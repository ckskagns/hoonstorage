package com.uridul.app

import android.app.Application
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.uridul.app.data.AppRelease
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Character
import com.uridul.app.data.Couple
import com.uridul.app.data.DeletedEvent
import com.uridul.app.data.DeletedEvents
import com.uridul.app.data.UndoItem
import com.uridul.app.data.WishPlace
import com.uridul.app.data.DateReview
import com.uridul.app.data.ReviewEntry
import com.uridul.app.domain.Memories
import com.uridul.app.domain.PhotoQuota
import com.uridul.app.data.PhotoMeta
import com.uridul.app.data.PhotoProcessor
import com.uridul.app.data.Backup
import com.uridul.app.data.BackupFiles
import com.uridul.app.data.ImportPreview
import com.uridul.app.data.toSeriesDates
import com.uridul.app.domain.Recurrence
import com.uridul.app.data.EventDraft
import com.uridul.app.data.Place
import com.uridul.app.data.toDraft
import com.uridul.app.data.NAME_MAX
import com.uridul.app.data.PartnerDefaults
import com.uridul.app.data.UserFacingException
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.InviteToken
import com.uridul.app.widget.CalendarWidget
import com.uridul.app.widget.MonthWidget
import com.uridul.app.notify.Reminders
import com.uridul.app.data.birthdays
import com.uridul.app.widget.Widgets
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 앱 업데이트 진행 상태 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** "출처를 알 수 없는 앱 설치" 허용이 필요함 */
    data class NeedPermission(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
}

/** 위젯에서 앱을 열 때 이동할 곳 */
data class OpenRequest(
    val tab: String,
    val date: LocalDate? = null,
    val addEvent: Boolean = false,
    /** 이 일정을 연 채로 그 날 하루 페이지 (알림을 눌러 열었을 때) */
    val focusEventId: String? = null,
)

sealed interface AppState {
    data object NotConfigured : AppState
    data object Loading : AppState
    data object SignedOut : AppState
    data class NoCouple(val myName: String) : AppState
    data class Ready(val couple: Couple, val myUid: String) : AppState
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as UridulApp).container

    private val _pendingInvite = MutableStateFlow<String?>(null)
    /** 딥링크로 받은 초대 토큰 (아직 사용 전). */
    val pendingInvite: StateFlow<String?> = _pendingInvite.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<AppState> = run {
        val auth = container.auth
        val couples = container.couples
        if (auth == null || couples == null) {
            MutableStateFlow(AppState.NotConfigured)
        } else {
            auth.authState().flatMapLatest { user ->
                if (user == null) {
                    flowOf(AppState.SignedOut)
                } else {
                    val name = user.displayName?.takeIf { it.isNotBlank() } ?: "나"
                    couples.observeCoupleId(user.uid).flatMapLatest { coupleId ->
                        if (coupleId == null) {
                            flowOf(AppState.NoCouple(name))
                        } else {
                            couples.observeCouple(coupleId).map { couple ->
                                if (couple == null || user.uid !in couple.members) AppState.NoCouple(name)
                                else AppState.Ready(couple, user.uid)
                            }
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)
        }
    }

    /** 연결된 커플 공간 ID (Ready 일 때만) */
    private val readyCoupleId: Flow<String?> =
        state.map { (it as? AppState.Ready)?.couple?.id }.distinctUntilChanged()

    // ── 캘린더 ──────────────────────────────────────────────

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    /** 캘린더 화면에 보이는 달 */
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    /**
     * 반복 일정 원본 목록: 커플 공간마다 한 번만 듣고 달력·위젯·알림이 함께 쓴다
     * (달을 넘길 때마다 다시 받지 않도록)
     */
    private val recurringEvents: StateFlow<List<CalendarEvent>> = run {
        val repo = container.events
        if (repo == null) {
            MutableStateFlow(emptyList())
        } else {
            readyCoupleId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeRecurring(id) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    /** 보이는 달(앞뒤 달 포함)의 일정 + 반복 일정(날짜별로 펼침) */
    val monthEvents: StateFlow<List<CalendarEvent>> = run {
        val repo = container.events
        if (repo == null) {
            MutableStateFlow(emptyList())
        } else {
            val ranged = combine(readyCoupleId, _visibleMonth) { id, month -> id to month }
                .flatMapLatest { (id, month) ->
                    val range = CalendarMath.queryRange(month)
                    if (id == null) flowOf(range to emptyList()) else repo.observeRange(id, range).map { range to it }
                }
            combine(ranged, recurringEvents) { (range, plain), recurring ->
                Recurrence.merge(plain, recurring, range.start, range.endInclusive)
            }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    /** 위젯을 눌러서 열린 경우 이동할 화면 */
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    fun onOpenRequest(request: OpenRequest) {
        _openRequest.value = request
        request.date?.let { _visibleMonth.value = YearMonth.from(it) }
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    fun showMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    fun saveEvent(input: EventDraft): Boolean {
        // 반복 일정의 어느 날을 고쳤으면 원래 첫 날 기준으로 저장 (반복 전체가 함께 바뀐다)
        val draft = input.toSeriesDates()
        CalendarMath.validate(draft)?.let {
            _message.value = it
            return false
        }
        val ready = state.value as? AppState.Ready ?: return false
        val repo = container.events ?: return false
        repo.save(ready.couple.id, ready.myUid, draft) { e -> _message.value = e.toUserMessage() ?: "저장하지 못했어요." }
        return true
    }

    /** 일정 색 등 화면 설정이 바뀌면 위젯을 다시 그린다 */
    fun refreshWidgets() {
        viewModelScope.launch { Widgets.updateAll(getApplication()) }
    }

    /** 일정 카드에서 찾은 장소를 그 일정(시간대)에 바로 저장 */
    fun attachPlace(eventId: String, slot: Int, place: Place) {
        val event = monthEvents.value.firstOrNull { it.id == eventId } ?: run {
            _message.value = "일정을 찾지 못했어요. 다시 시도해 주세요."
            return
        }
        val draft = event.toDraft()
        val updated = if (slot < 0 || draft.time == null) {
            draft.copy(place = place)
        } else {
            draft.copy(timeline = draft.timeline.mapIndexed { i, s -> if (i == slot) s.copy(place = place) else s })
        }
        if (saveEvent(updated)) _message.value = "📍 ${place.name} 을(를) 등록했어요"
    }

    fun deleteEvent(eventId: String) {
        val ready = state.value as? AppState.Ready ?: return
        // 되돌릴 수 있도록 지우기 전 내용을 기억해 둔다
        // 반복 일정은 원본(첫 날 기준)으로 보관
        val event = (monthEvents.value.firstOrNull { it.id == eventId } ?: searchCache.firstOrNull { it.id == eventId })?.asSeries()
        container.events?.delete(ready.couple.id, eventId) { e ->
            _undo.value = null
            _message.value = e.toUserMessage() ?: "삭제하지 못했어요."
        }
        if (event != null) {
            DeletedEvents.add(getApplication(), event)
            _undo.value = UndoItem(event)
        }
    }

    /** 반복 일정에서 이 날만 빼기 ([day] 는 그 날의 시작일) */
    fun skipOccurrence(eventId: String, day: LocalDate) {
        val ready = state.value as? AppState.Ready ?: return
        val repo = container.events ?: return
        val event = monthEvents.value.firstOrNull { it.id == eventId }?.asSeries() ?: return
        repo.setSkipped(ready.couple.id, ready.myUid, eventId, day, skipped = true) { e ->
            _undo.value = null
            _message.value = e.toUserMessage() ?: "빼지 못했어요."
        }
        _undo.value = UndoItem(event, day)
    }

    // ── 지운 일정 되돌리기 ─────────────────────────────────

    private val _undo = MutableStateFlow<UndoItem?>(null)
    /** 방금 지운 일정·뺀 날 ("되돌리기" 안내를 잠깐 보여준다) */
    val undo: StateFlow<UndoItem?> = _undo.asStateFlow()

    /** 최근 지운 일정 (이 폰에 30일 보관) */
    val deletedEvents: StateFlow<List<DeletedEvent>> = DeletedEvents.items

    fun undoDelete() {
        val item = _undo.value ?: return
        _undo.value = null
        val day = item.skippedDay
        if (day == null) {
            restoreDeleted(item.event)
            return
        }
        val ready = state.value as? AppState.Ready ?: return
        container.events?.setSkipped(ready.couple.id, ready.myUid, item.event.id, day, skipped = false) { e ->
            _message.value = e.toUserMessage() ?: "되돌리지 못했어요."
        }
        _message.value = "${day.monthValue}월 ${day.dayOfMonth}일 일정을 되돌렸어요"
    }

    fun dismissUndo() {
        _undo.value = null
    }

    fun restoreDeleted(event: CalendarEvent) {
        val ready = state.value as? AppState.Ready ?: return
        val repo = container.events ?: return
        repo.restore(ready.couple.id, ready.myUid, event) { e ->
            _message.value = e.toUserMessage() ?: "되돌리지 못했어요."
        }
        DeletedEvents.remove(getApplication(), event.id)
        if (_undo.value?.event?.id == event.id) _undo.value = null
        _message.value = "\"${event.title}\" 일정을 되돌렸어요 (${event.date.monthValue}월 ${event.date.dayOfMonth}일)"
    }

    // ── 가고 싶은 곳 ──────────────────────────────────────

    /** 가고 싶은 곳 목록 (최근에 담은 것부터) */
    val wishes: StateFlow<List<WishPlace>> = run {
        val repo = container.wishes
        if (repo == null) {
            MutableStateFlow(emptyList())
        } else {
            readyCoupleId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observe(id) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    fun addWish(place: Place) {
        val ready = state.value as? AppState.Ready ?: return
        val repo = container.wishes ?: return
        if (wishes.value.any { it.place.name == place.name && !it.visited }) {
            _message.value = "📍 ${place.name} 은(는) 이미 가고 싶은 곳에 있어요"
            return
        }
        if (wishes.value.size >= WishPlace.MAX_ITEMS) {
            _message.value = "가고 싶은 곳은 ${WishPlace.MAX_ITEMS}개까지 담을 수 있어요"
            return
        }
        repo.add(ready.couple.id, ready.myUid, place) { e -> _message.value = e.toUserMessage() ?: "담지 못했어요." }
        _message.value = "📍 ${place.name} 을(를) 가고 싶은 곳에 담았어요 💕"
    }

    fun setWishVisited(id: String, visited: Boolean) {
        val ready = state.value as? AppState.Ready ?: return
        container.wishes?.setVisited(ready.couple.id, ready.myUid, id, visited) { e ->
            _message.value = e.toUserMessage() ?: "바꾸지 못했어요."
        }
    }

    fun setWishMemo(id: String, memo: String) {
        val ready = state.value as? AppState.Ready ?: return
        container.wishes?.setMemo(ready.couple.id, ready.myUid, id, memo) { e ->
            _message.value = e.toUserMessage() ?: "메모를 저장하지 못했어요."
        }
    }

    fun deleteWish(id: String) {
        val ready = state.value as? AppState.Ready ?: return
        container.wishes?.delete(ready.couple.id, id) { e -> _message.value = e.toUserMessage() ?: "지우지 못했어요." }
    }

    // ── 기능별 관리 (사진첩·백업 파일·앱 업데이트는 FeatureControllers.kt) ──

    private val features = FeatureScope(
        app = app,
        scope = viewModelScope,
        container = container,
        readyState = { state.value as? AppState.Ready },
        say = { _message.value = it },
        errorText = { it.toUserMessage() },
        busyRunner = { block -> launchBusy(block) },
    )
    val photos = PhotoController(features)
    val backup = BackupController(features)
    val updates = UpdateController(features)

    // ── 일정 색 (두 사람 같은 색) ─────────────────────────

    /**
     * 일정 색 바꾸기: 이 폰 설정 + 서버(역할별) 에 함께 저장 → 상대 폰도 같은 색으로 보인다.
     * [mine] 은 내 일정 색, [partner] 는 상대 일정 색.
     */
    fun setEventColors(mine: com.uridul.app.data.EventColor, partner: com.uridul.app.data.EventColor) {
        com.uridul.app.data.AppSettings.update(getApplication()) { it.copy(mine = mine, partner = partner) }
        val ready = state.value as? AppState.Ready ?: return
        uploadColors(ready.couple, ready.myUid, mine, partner)
    }

    private fun uploadColors(couple: Couple, myUid: String, mine: com.uridul.app.data.EventColor, partner: com.uridul.app.data.EventColor) {
        val repo = container.couples ?: return
        val map = if (couple.isCreator(myUid)) mapOf("creator" to mine.key, "partner" to partner.key)
        else mapOf("creator" to partner.key, "partner" to mine.key)
        if (couple.colors == map) return
        viewModelScope.launch {
            runCatching { repo.updateColors(couple.id, map) }
                .onFailure { _message.value = (it as? Exception)?.toUserMessage() ?: "일정 색을 저장하지 못했어요." }
        }
    }

    /** 서버의 일정 색을 이 폰 설정에 맞춘다. 서버에 아직 없으면 이 폰에서 고른 색을 올린다 (처음 한 번) */
    private fun syncColors(couple: Couple, myUid: String) {
        val settings = com.uridul.app.data.AppSettings.state.value
        val (mineKey, partnerKey) = couple.colorKeys(myUid)
        if (mineKey == null && partnerKey == null) {
            uploadColors(couple, myUid, settings.mine, settings.partner)
            return
        }
        val mine = com.uridul.app.data.EventColor.from(mineKey, settings.mine)
        val partner = com.uridul.app.data.EventColor.from(partnerKey, settings.partner)
        if (mine != settings.mine || partner != settings.partner) {
            com.uridul.app.data.AppSettings.update(getApplication()) { it.copy(mine = mine, partner = partner) }
        }
    }

    // ── 앱 안 알림 (종 아이콘) ────────────────────────────

    /** 일정 알림 보관함 (최근 것부터) */
    val inbox: StateFlow<List<com.uridul.app.data.InboxItem>> = com.uridul.app.data.NotificationInbox.items

    fun markInboxRead(id: String) = com.uridul.app.data.NotificationInbox.markRead(getApplication(), id)

    fun markAllInboxRead() = com.uridul.app.data.NotificationInbox.markAllRead(getApplication())

    // ── 데이트 한 줄 후기 ─────────────────────────────────

    /** 전체 후기 (일정ID_날짜 → 후기) */
    val reviews: StateFlow<Map<String, DateReview>> = run {
        val repo = container.reviews
        if (repo == null) {
            MutableStateFlow(emptyMap())
        } else {
            readyCoupleId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observe(id) }
                .map { list -> list.associateBy { it.id } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        }
    }

    /** 내 후기 저장 ([event] 는 그 날 기준 일정) */
    fun saveReview(event: CalendarEvent, hearts: Int, text: String) {
        val ready = state.value as? AppState.Ready ?: return
        val repo = container.reviews ?: return
        if (text.length > ReviewEntry.TEXT_MAX || hearts !in ReviewEntry.HEARTS) return
        repo.save(ready.couple.id, ready.myUid, event, ReviewEntry(text, hearts)) { e ->
            _message.value = e.toUserMessage() ?: "후기를 저장하지 못했어요."
        }
        _message.value = "💗 후기를 남겼어요"
    }

    fun deleteMyReview(review: DateReview) {
        val ready = state.value as? AppState.Ready ?: return
        container.reviews?.deleteMine(ready.couple.id, ready.myUid, review) { e ->
            _message.value = e.toUserMessage() ?: "후기를 지우지 못했어요."
        }
    }

    // ── 추억 슬라이드 (작년 오늘 · 지난 한 달) ─────────────

    private val _memories = MutableStateFlow<List<Memories.Slide>>(emptyList())
    val memories: StateFlow<List<Memories.Slide>> = _memories.asStateFlow()
    private var memoriesLoadedFor: Pair<String, LocalDate>? = null

    /** 홈을 볼 때 하루에 한 번 받아온다 (몇 년 전 같은 날 + 지난 한 달) */
    fun loadMemories(today: LocalDate) {
        val ready = state.value as? AppState.Ready ?: return
        val repo = container.events ?: return
        val key = ready.couple.id to today
        if (memoriesLoadedFor == key) return
        memoriesLoadedFor = key
        viewModelScope.launch {
            val events = runCatching {
                Memories.queryRanges(today).flatMap { repo.fetchRange(ready.couple.id, it) }
            }.getOrElse {
                memoriesLoadedFor = null
                return@launch
            }
            _memories.value = Memories.slides(today, events)
            photos.setMemorySlides(_memories.value)
            photos.refreshWeekPhotoCount()
        }
    }

    // ── 일정 검색 ─────────────────────────────────────────

    /** 검색 창에서 받아 둔 전체 일정 (검색 결과에서 연 일정을 지울 때도 쓴다) */
    @Volatile
    private var searchCache: List<CalendarEvent> = emptyList()

    /** 검색용 전체 일정. 실패하면 null */
    suspend fun loadAllEvents(): List<CalendarEvent>? {
        val ready = state.value as? AppState.Ready ?: return null
        val repo = container.events ?: return null
        return try {
            repo.fetchAll(ready.couple.id).also { searchCache = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // ── 상대 일정 알림 ─────────────────────────────────────

    private val _partnerAlerts = MutableStateFlow(com.uridul.app.notify.PartnerAlerts.isEnabled(app))
    val partnerAlertsEnabled: StateFlow<Boolean> = _partnerAlerts.asStateFlow()

    fun setPartnerAlerts(enabled: Boolean) {
        com.uridul.app.notify.PartnerAlerts.setEnabled(getApplication(), enabled)
        _partnerAlerts.value = enabled
        _message.value = if (enabled) "상대가 일정·가고 싶은 곳·후기를 남기면 알려드릴게요 (약 15분마다 확인)" else "상대 소식 알림을 껐어요"
    }

    // ── 일정 알림 ─────────────────────────────────────────

    private val _notifyEnabled = MutableStateFlow(Reminders.isEnabled(app))
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled.asStateFlow()

    fun setNotifyEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        Reminders.setEnabled(context, enabled)
        _notifyEnabled.value = enabled
        _message.value = if (!enabled) {
            "일정 알림을 껐어요"
        } else if (!Reminders.hasPermission(context)) {
            "폰 설정에서 우리둘 알림을 허용해야 알림이 와요"
        } else {
            "일정 당일 아침 9시와 일정 전(기본 30분 전)에 알려드릴게요"
        }
    }

    init {
        DeletedEvents.load(app)
        com.uridul.app.data.NotificationInbox.load(app)
        // 앱을 열 때 새 버전이 있는지 한 번 확인한다
        updates.checkForUpdate(manual = false)
        // 커플 공간의 일정 색이 바뀌면(상대가 바꾼 것 포함) 이 폰에도 같은 색으로
        viewModelScope.launch {
            state.collect { s -> if (s is AppState.Ready) syncColors(s.couple, s.myUid) }
        }
        // 커플 정보가 바뀔 때마다 위젯용 데이터를 저장하고 위젯을 다시 그린다
        viewModelScope.launch {
            state
                .filter { it is AppState.Ready || it is AppState.SignedOut || it is AppState.NoCouple }
                .map { s ->
                    (s as? AppState.Ready)?.let {
                        WidgetSnapshot(
                            startDate = it.couple.startDate,
                            countFromOne = it.couple.countFromOne,
                            myName = it.couple.myName(it.myUid),
                            partnerName = it.couple.partnerName(it.myUid),
                            coupleId = it.couple.id,
                            myUid = it.myUid,
                            myCharacter = it.couple.myCharacter(it.myUid).key,
                            partnerCharacter = it.couple.partnerCharacter(it.myUid).key,
                            birthdays = it.couple.birthdays(),
                        )
                    }
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val context = getApplication<Application>()
                    if (snapshot != null) WidgetStore.save(context, snapshot) else WidgetStore.clear(context)
                    Widgets.updateAll(context)
                }
        }
        // 다가오는 일정이 바뀌면 일정 위젯을 다시 그린다 (상대가 추가한 일정 포함)
        container.events?.let { repo ->
            viewModelScope.launch {
                readyCoupleId
                    .flatMapLatest { id ->
                        if (id == null) {
                            flowOf(null)
                        } else {
                            // 다가오는 일정 + 반복 일정(앞으로 두 달, 날짜별로 펼침)
                            val today = LocalDate.now()
                            combine(repo.observeUpcoming(id, today), recurringEvents) { upcoming, recurring ->
                                CalendarMath.upcoming(Recurrence.merge(upcoming, recurring, today, today.plusDays(60)), today, 40)
                            }
                        }
                    }
                    .filterNotNull()
                    .distinctUntilChanged()
                    // 저장 직후(내 폰) → 서버 확인 순으로 연달아 오는 변경을 한 번으로 묶어 위젯을 덜 그린다
                    .debounce(400)
                    .collect { events ->
                        val context = getApplication<Application>()
                        WidgetStore.saveEvents(context, events)
                        CalendarWidget().updateAll(context)
                        MonthWidget.refreshAll(context)
                        // 시간 일정 알림(일정마다 고른 시간)을 다시 맞춘다 (추가·수정·삭제 반영)
                        Reminders.scheduleTimed(context, events)
                    }
            }
        }
        // 이미 연결된 상태에서 초대 링크를 열면 안내만 하고 버린다
        viewModelScope.launch {
            state.collect { s ->
                if (s is AppState.Ready && _pendingInvite.value != null && !joining) {
                    _pendingInvite.value = null
                    _message.value = "이미 커플 공간에 연결되어 있어요."
                }
            }
        }
    }

    fun onInviteReceived(token: String) {
        _pendingInvite.value = token
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun signIn(activityContext: Context) = launchBusy {
        container.auth?.signInWithGoogle(activityContext)
    }

    fun signOut(context: Context) = launchBusy {
        container.auth?.signOut(context)
    }

    fun createCouple(startDate: LocalDate) = launchBusy {
        val user = container.auth?.currentUser ?: return@launchBusy
        container.couples?.createCouple(user.uid, displayName(), startDate)
    }

    fun join(input: String) = launchBusy {
        val user = container.auth?.currentUser ?: return@launchBusy
        val token = InviteToken.parse(input)
            ?: throw UserFacingException("초대 링크 형식이 올바르지 않아요. 받은 링크를 그대로 붙여넣어 주세요.")
        joining = true
        try {
            container.couples?.join(token, user.uid, displayName())
        } finally {
            joining = false
        }
        _pendingInvite.value = null
        _message.value = "연결되었어요 💕"
    }

    /** 참여 처리 중에는 "이미 연결됨" 안내가 뜨지 않게 한다. */
    @Volatile
    private var joining = false

    /** 초대 링크를 만들어 onLink 로 넘긴다 (화면에서 공유 창을 띄움). */
    fun createInviteLink(onLink: (String) -> Unit) = launchBusy {
        val ready = state.value as? AppState.Ready ?: return@launchBusy
        if (ready.couple.isPaired) {
            _message.value = "이미 두 사람이 연결되어 있어요."
            return@launchBusy
        }
        val token = container.couples?.createInvite(ready.couple.id, ready.myUid, ready.couple.partnerDefaults)
            ?: return@launchBusy
        onLink(InviteToken.link(BuildConfig.INVITE_HOST, token))
    }

    /**
     * 내 이름·캐릭터 저장. 아직 상대가 연결되지 않았으면 상대 이름·캐릭터(초대 링크에 실림)도 함께 저장한다.
     */
    fun updateProfile(name: String, character: Character, partner: PartnerDefaults?) = launchBusy {
        val ready = state.value as? AppState.Ready ?: return@launchBusy
        val repo = container.couples ?: return@launchBusy
        val myName = name.trim()
        if (myName.isEmpty() || myName.length > NAME_MAX) {
            throw UserFacingException("이름은 1~${NAME_MAX}자로 입력해 주세요.")
        }
        repo.updateMyProfile(ready.couple.id, ready.myUid, myName, character)
        if (partner != null && !ready.couple.isPaired) {
            val partnerName = partner.name.trim()
            if (partnerName.isEmpty() || partnerName.length > NAME_MAX) {
                throw UserFacingException("상대 이름은 1~${NAME_MAX}자로 입력해 주세요.")
            }
            repo.updatePartnerDefaults(ready.couple.id, partner.copy(name = partnerName))
        }
        _message.value = "프로필을 저장했어요"
    }

    fun updateStartDate(date: LocalDate) = launchBusy {
        val ready = state.value as? AppState.Ready ?: return@launchBusy
        container.couples?.updateStartDate(ready.couple.id, date)
    }

    fun setCountFromOne(value: Boolean) = launchBusy {
        val ready = state.value as? AppState.Ready ?: return@launchBusy
        container.couples?.updateCountFromOne(ready.couple.id, value)
    }

    private fun displayName(): String =
        container.auth?.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "나"

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toUserMessage()?.let { _message.value = it }
        } finally {
            _busy.value = false
        }
    }

    private fun Exception.toUserMessage(): String? = when (this) {
        is GetCredentialCancellationException -> null
        is NoCredentialException -> "이 폰에 로그인할 수 있는 Google 계정이 없어요."
        is UserFacingException -> message
        is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요."
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "권한이 없어요. 링크가 만료되었거나 이미 사용되었을 수 있어요."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "인터넷 연결을 확인해 주세요."
            else -> "서버 오류가 발생했어요. (${code.name})"
        }
        else -> message ?: "문제가 발생했어요."
    }
}

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
import com.uridul.app.data.DateReview
import com.uridul.app.data.ReviewEntry
import com.uridul.app.data.normalized
import com.uridul.app.domain.Memories
import com.uridul.app.domain.MemoryMove
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
    /** 가계부 직접 기록 창 바로 열기 (앱 아이콘 바로가기) */
    val ledgerAdd: Boolean = false,
    /** 오늘의 질문 바로 열기 (앱 아이콘 바로가기) */
    val question: Boolean = false,
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

    /** 고치기 전 원본 (첫 날 기준). 저장하면 화면 목록이 바로 바뀌므로 저장 전에 찾는다 */
    private fun seriesOf(id: String?): CalendarEvent? =
        id?.let { (monthEvents.value.firstOrNull { e -> e.id == it } ?: searchCache.firstOrNull { e -> e.id == it })?.asSeries() }

    private val _occurrenceMove = MutableStateFlow<EventDraft?>(null)
    /** 반복 일정의 어느 날 날짜를 바꿔 "이 날만 / 반복 전체" 를 묻는 중인 입력값 */
    val occurrenceMove: StateFlow<EventDraft?> = _occurrenceMove.asStateFlow()
    /** 물을 때의 원본 (고르기 전에 달을 넘겨도 그대로 쓰도록) */
    private var occurrenceSeries: CalendarEvent? = null

    fun saveEvent(input: EventDraft): Boolean {
        // 반복 일정의 어느 날을 고쳤으면 원래 첫 날 기준으로 저장 (반복 전체가 함께 바뀐다)
        // 타임라인 정리: 하루 일정은 시간 순, 여러 날 일정은 날짜별로
        val draft = input.toSeriesDates().normalized()
        CalendarMath.validate(draft)?.let {
            _message.value = it
            return false
        }
        if (state.value !is AppState.Ready || container.events == null) return false
        // 반복 일정의 어느 날 날짜를 바꿨으면 그 날만 옮길지, 반복 전체를 옮길지 묻는다 (창은 닫고 물음 창을 띄운다)
        val series = seriesOf(input.id)
        if (MemoryMove.asksOccurrenceChoice(input, series)) {
            occurrenceSeries = series
            _occurrenceMove.value = input
            return true
        }
        return saveSeries(draft, series)
    }

    /** "이 날만" / "반복 전체" 를 고른 뒤 (null = 취소, 저장하지 않음) */
    fun resolveOccurrenceMove(onlyThisDay: Boolean?) {
        val input = _occurrenceMove.value ?: return
        val series = occurrenceSeries
        _occurrenceMove.value = null
        occurrenceSeries = null
        when (onlyThisDay) {
            null -> _message.value = "일정을 고치지 않았어요"
            true -> moveOneOccurrence(input, series)
            false -> saveSeries(input.toSeriesDates().normalized(), series)
        }
    }

    /** [old] 고치기 전 원본 (날짜가 바뀌면 그 일정의 사진·후기를 함께 옮긴다) */
    private fun saveSeries(draft: EventDraft, old: CalendarEvent?): Boolean {
        val ready = state.value as? AppState.Ready ?: return false
        val repo = container.events ?: return false
        repo.save(ready.couple.id, ready.myUid, draft) { e -> _message.value = e.toUserMessage() ?: "저장하지 못했어요." }
        if (old != null && MemoryMove.needed(old, draft.date)) {
            memoryMover.move(old.id, old.id, { d -> MemoryMove.target(old, draft.date, draft.repeat, d) }) { what ->
                if (old.repeat != null && draft.repeat != null) "📷 날마다 남긴 ${what}도 반복과 함께 옮겼어요"
                else "📷 ${what}도 ${draft.date.monthValue}월 ${draft.date.dayOfMonth}일로 함께 옮겼어요"
            }
        }
        return true
    }

    /**
     * 반복 일정의 하루만 다른 날로: 그 날은 반복에서 빼고,
     *  - 옮길 날이 이미 그 반복의 날이면 그 날에 합치고 (새 일정 없음)
     *  - 아니면 고친 내용으로 반복 없는 일정을 새로 만든다.
     * 그 날의 사진·후기도 함께 옮긴다.
     */
    private fun moveOneOccurrence(input: EventDraft, found: CalendarEvent?) {
        val ready = state.value as? AppState.Ready ?: return
        val repo = container.events ?: return
        val series = found ?: seriesOf(input.id) ?: run { _message.value = "일정을 찾지 못했어요. 다시 시도해 주세요."; return }
        val from = input.occurrence ?: return
        val to = input.date
        val merge = MemoryMove.mergesIntoSeries(series, to)
        val toId = if (merge) {
            series.id
        } else {
            repo.save(ready.couple.id, ready.myUid, MemoryMove.detached(input).normalized()) { e ->
                _message.value = e.toUserMessage() ?: "저장하지 못했어요."
            }
        }
        repo.setSkipped(ready.couple.id, ready.myUid, series.id, from, skipped = true) { e ->
            _message.value = e.toUserMessage() ?: "반복에서 이 날을 빼지 못했어요."
        }
        // 반복의 날에 합치면 그 날만 따로 고친 내용(시간·메모 등)은 남길 곳이 없다: 알려 준다
        val note = if (merge && MemoryMove.editsBesidesDate(input, series)) " · 바꾼 내용은 반복 전체에서 고쳐 주세요" else ""
        val day = "${from.monthValue}월 ${from.dayOfMonth}일 일정을 ${to.monthValue}월 ${to.dayOfMonth}일로 옮겼어요$note"
        _message.value = day
        memoryMover.move(series.id, toId, { d -> to.takeIf { d == from } }) { what -> "$day ($what 함께)" }
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

    // ── 기능별 관리 (사진첩·백업 파일·앱 업데이트는 FeatureControllers.kt) ──

    private val features = FeatureScope(
        app = app,
        scope = viewModelScope,
        container = container,
        coupleId = readyCoupleId,
        readyState = { state.value as? AppState.Ready },
        say = { _message.value = it },
        errorText = { it.toUserMessage() },
        busyRunner = { block -> launchBusy(block) },
    )
    val photos = PhotoController(features)
    val backup = BackupController(features)
    val updates = UpdateController(features)
    val wishList = WishController(features)
    val colors = ColorController(features)
    private val memoryMover = MemoryMover(features, photos)

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
        com.uridul.app.ui.Celebrate.fire()
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
            "일정이 있는 날 아침 9시에 그날 일정을 한 번 알려드릴게요"
        }
    }

    init {
        DeletedEvents.load(app)
        com.uridul.app.data.NotificationInbox.load(app)
        // 앱을 열 때 새 버전이 있는지 한 번 확인한다
        updates.checkForUpdate(manual = false)
        // 커플 공간의 일정 색이 바뀌면(상대가 바꾼 것 포함) 이 폰에도 같은 색으로
        viewModelScope.launch {
            state.collect { s -> if (s is AppState.Ready) colors.syncColors(s.couple, s.myUid) }
        }
        // 커플 정보가 바뀔 때마다 위젯용 데이터를 저장하고 위젯을 다시 그린다
        viewModelScope.launch {
            state
                .filter { it is AppState.Ready || it is AppState.SignedOut || it is AppState.NoCouple }
                .map { s ->
                    (s as? AppState.Ready)?.let {
                        // 가계부 알림 서비스가 결제한 사람을 나/상대로 맞출 때 쓴다
                        com.uridul.app.ledger.PaymentInbox.setCreator(getApplication(), it.couple.isCreator(it.myUid))
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

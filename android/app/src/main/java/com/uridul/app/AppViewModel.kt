package com.uridul.app

import android.app.Application
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Character
import com.uridul.app.data.Couple
import com.uridul.app.data.EventDraft
import com.uridul.app.data.NAME_MAX
import com.uridul.app.data.PartnerDefaults
import com.uridul.app.data.UserFacingException
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.InviteToken
import com.uridul.app.widget.CalendarWidget
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

/** 위젯에서 앱을 열 때 이동할 곳 */
data class OpenRequest(
    val tab: String,
    val date: LocalDate? = null,
    val addEvent: Boolean = false,
)

sealed interface AppState {
    data object NotConfigured : AppState
    data object Loading : AppState
    data object SignedOut : AppState
    data class NoCouple(val myName: String) : AppState
    data class Ready(val couple: Couple, val myUid: String) : AppState
}

@OptIn(ExperimentalCoroutinesApi::class)
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

    /** 보이는 달(앞뒤 달 포함)의 일정 */
    val monthEvents: StateFlow<List<CalendarEvent>> = run {
        val repo = container.events
        if (repo == null) {
            MutableStateFlow(emptyList())
        } else {
            combine(readyCoupleId, _visibleMonth) { id, month -> id to month }
                .flatMapLatest { (id, month) ->
                    if (id == null) flowOf(emptyList()) else repo.observeRange(id, CalendarMath.queryRange(month))
                }
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

    fun saveEvent(draft: EventDraft): Boolean {
        CalendarMath.validate(draft)?.let {
            _message.value = it
            return false
        }
        val ready = state.value as? AppState.Ready ?: return false
        val repo = container.events ?: return false
        repo.save(ready.couple.id, ready.myUid, draft) { e -> _message.value = e.toUserMessage() ?: "저장하지 못했어요." }
        return true
    }

    fun deleteEvent(eventId: String) {
        val ready = state.value as? AppState.Ready ?: return
        container.events?.delete(ready.couple.id, eventId) { e ->
            _message.value = e.toUserMessage() ?: "삭제하지 못했어요."
        }
    }

    init {
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
                        if (id == null) flowOf(null) else repo.observeUpcoming(id, LocalDate.now())
                    }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { events ->
                        val context = getApplication<Application>()
                        WidgetStore.saveEvents(context, events)
                        CalendarWidget().updateAll(context)
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

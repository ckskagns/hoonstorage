package com.uridul.app

import android.app.Application
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.uridul.app.data.Couple
import com.uridul.app.data.UserFacingException
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.InviteToken
import com.uridul.app.widget.DDayWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
                        )
                    }
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val context = getApplication<Application>()
                    if (snapshot != null) WidgetStore.save(context, snapshot) else WidgetStore.clear(context)
                    DDayWidget().updateAll(context)
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
        val token = container.couples?.createInvite(ready.couple.id, ready.myUid) ?: return@launchBusy
        onLink(InviteToken.link(BuildConfig.INVITE_HOST, token))
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

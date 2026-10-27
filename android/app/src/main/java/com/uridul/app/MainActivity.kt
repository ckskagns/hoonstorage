package com.uridul.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uridul.app.domain.InviteToken
import com.uridul.app.ui.LoadingScreen
import com.uridul.app.ui.LoginScreen
import com.uridul.app.ui.NotConfiguredScreen
import com.uridul.app.ui.ReadyScreen
import com.uridul.app.ui.SetupScreen
import com.uridul.app.ui.UpdateDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.uridul.app.ui.shareInviteLink
import com.uridul.app.ui.theme.UridulTheme
import com.uridul.app.widget.MainExtras
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            UridulTheme {
                UridulRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * - https://<host>/invite/<토큰> 또는 uridul://invite/<토큰> 으로 열린 경우: 초대 처리
     * - 위젯을 눌러 열린 경우: 해당 탭/날짜로 이동
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.dataString?.let { data ->
            InviteToken.parse(data)?.let(viewModel::onInviteReceived)
        }
        val tab = intent.getStringExtra(MainExtras.TAB) ?: return
        val date = intent.getStringExtra(MainExtras.DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val add = intent.getBooleanExtra(MainExtras.ADD_EVENT, false)
        viewModel.onOpenRequest(OpenRequest(tab = tab, date = date, addEvent = add))
    }
}

@Composable
private fun UridulRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingInvite.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val monthEvents by viewModel.monthEvents.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()

    // 설치 허용 화면에서 돌아오면 업데이트를 이어서 진행
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumeAfterPermission() }
    UpdateDialog(
        state = update,
        onUpdate = viewModel::startUpdate,
        onOpenPermission = viewModel::openInstallPermission,
        onDismiss = viewModel::dismissUpdate,
    )
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    when (val s = state) {
        AppState.NotConfigured -> NotConfiguredScreen()
        AppState.Loading -> LoadingScreen()
        AppState.SignedOut -> LoginScreen(
            hasPendingInvite = pendingInvite != null,
            busy = busy,
            snackbar = snackbar,
            onSignIn = { viewModel.signIn(context) },
        )
        is AppState.NoCouple -> SetupScreen(
            myName = s.myName,
            pendingInvite = pendingInvite,
            busy = busy,
            snackbar = snackbar,
            onCreate = viewModel::createCouple,
            onJoin = viewModel::join,
            onSignOut = { viewModel.signOut(context) },
        )
        is AppState.Ready -> ReadyScreen(
            couple = s.couple,
            myUid = s.myUid,
            busy = busy,
            snackbar = snackbar,
            month = month,
            monthEvents = monthEvents,
            openRequest = openRequest,
            onOpenRequestHandled = viewModel::consumeOpenRequest,
            onMonthChange = viewModel::showMonth,
            onSaveEvent = viewModel::saveEvent,
            onDeleteEvent = viewModel::deleteEvent,
            onInvite = { viewModel.createInviteLink { link -> shareInviteLink(context, link) } },
            onChangeStartDate = viewModel::updateStartDate,
            onSetCountFromOne = viewModel::setCountFromOne,
            onUpdateProfile = viewModel::updateProfile,
            onCheckUpdate = { viewModel.checkForUpdate(manual = true) },
            onSignOut = { viewModel.signOut(context) },
        )
    }
}

package com.uridul.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.uridul.app.notify.Reminders
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
            // 앱에서 고른 화면 모드(화이트/다크)에 맞춰 상태 표시줄 글자색도 바꾼다
            val dark = com.uridul.app.ui.theme.isAppInDarkTheme()
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = androidx.activity.SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
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
    val notifyEnabled by viewModel.notifyEnabled.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val deletedEvents by viewModel.deletedEvents.collectAsStateWithLifecycle()
    val wishes by viewModel.wishes.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()

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

    // 커플 공간에 들어오면 처음 한 번만 알림 허용을 묻는다 (거절하면 다시 묻지 않음, 메뉴에서 다시 켤 수 있음)
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state is AppState.Ready) {
        if (state is AppState.Ready && !Reminders.hasPermission(context) && !Reminders.wasPermissionAsked(context)) {
            Reminders.markPermissionAsked(context)
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
            notifyEnabled = notifyEnabled,
            onAttachPlace = viewModel::attachPlace,
            undo = undo,
            onUndo = viewModel::undoDelete,
            onDismissUndo = viewModel::dismissUndo,
            deletedEvents = deletedEvents,
            onRestoreDeleted = viewModel::restoreDeleted,
            onLoadAllEvents = viewModel::loadAllEvents,
            onSkipOccurrence = viewModel::skipOccurrence,
            wishes = wishes,
            onAddWish = viewModel::addWish,
            onWishVisited = viewModel::setWishVisited,
            onWishMemo = viewModel::setWishMemo,
            onDeleteWish = viewModel::deleteWish,
            onExportBackup = { viewModel.exportBackup(it) },
            onImportBackup = { viewModel.readBackup(it) },
            importPreview = importPreview,
            onConfirmImport = viewModel::confirmImport,
            onCancelImport = viewModel::cancelImport,
            onSetNotify = { enabled ->
                viewModel.setNotifyEnabled(enabled)
                if (enabled && !Reminders.hasPermission(context)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onSignOut = { viewModel.signOut(context) },
        )
    }
}

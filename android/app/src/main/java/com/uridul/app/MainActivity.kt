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
        // 앱 아이콘을 길게 눌러 고른 바로가기
        if (intent.action == SHORTCUT_ACTION) {
            shortcutRequest(intent.data?.lastPathSegment)?.let(viewModel::onOpenRequest)
            return
        }
        intent.dataString?.let { data ->
            InviteToken.parse(data)?.let(viewModel::onInviteReceived)
        }
        val tab = intent.getStringExtra(MainExtras.TAB) ?: return
        val date = intent.getStringExtra(MainExtras.DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val add = intent.getBooleanExtra(MainExtras.ADD_EVENT, false)
        // 폰 알림을 눌러 열었으면 앱 안 알림도 읽음으로
        intent.getStringExtra(MainExtras.INBOX_ID)?.let { com.uridul.app.data.NotificationInbox.markRead(applicationContext, it) }
        val focus = intent.getStringExtra(MainExtras.EVENT_ID)?.takeIf { it.isNotBlank() }
        viewModel.onOpenRequest(OpenRequest(tab = tab, date = date, addEvent = add, focusEventId = focus))
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
    val update by viewModel.updates.update.collectAsStateWithLifecycle()
    val notifyEnabled by viewModel.notifyEnabled.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val deletedEvents by viewModel.deletedEvents.collectAsStateWithLifecycle()
    val wishes by viewModel.wishList.wishes.collectAsStateWithLifecycle()
    val importPreview by viewModel.backup.importPreview.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val partnerAlerts by viewModel.partnerAlertsEnabled.collectAsStateWithLifecycle()
    val weekPhotos by viewModel.photos.weekPhotoCount.collectAsStateWithLifecycle()
    val photoUploading by viewModel.photos.photoUploading.collectAsStateWithLifecycle()
    val memoryPhotos by viewModel.photos.memoryPhotos.collectAsStateWithLifecycle()
    val dayPhotos by viewModel.photos.dayPhotos.collectAsStateWithLifecycle()
    val heroPhoto by viewModel.photos.hero.collectAsStateWithLifecycle()
    val partnerLatest by viewModel.photos.partnerLatest.collectAsStateWithLifecycle()
    // 반복 일정의 어느 날 날짜를 바꿨으면 "이 날만 / 반복 전체" 를 묻는다
    val occurrenceMove by viewModel.occurrenceMove.collectAsStateWithLifecycle()
    occurrenceMove?.let { com.uridul.app.ui.OccurrenceMoveDialog(it, viewModel::resolveOccurrenceMove) }
    // 상대가 일정 날짜를 옮겼으면 사진 달력도 새 날짜로
    androidx.compose.runtime.LaunchedEffect(monthEvents, dayPhotos) { viewModel.photos.recheckMoved(monthEvents) }
    // 디데이 사진이 바뀌면 그 사진의 씨앗 색 (메뉴 "디데이 사진 색 따라가기"를 켰을 때 앱 강조색)
    androidx.compose.runtime.LaunchedEffect(heroPhoto.photo?.id) {
        com.uridul.app.ui.theme.PhotoTint.setSeed(heroPhoto.photo?.let { com.uridul.app.ui.theme.PhotoSeed.of(it.bytes) })
    }

    // 설치 허용 화면에서 돌아오면 업데이트를 이어서 진행
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.updates.onResumeAfterPermission()
        // 앱으로 돌아오면 이번 주 사진 장수를 다시 센다 (상대가 올렸을 수 있음)
        viewModel.photos.refreshWeekPhotoCount()
    }
    UpdateDialog(
        state = update,
        onUpdate = viewModel.updates::startUpdate,
        onOpenPermission = viewModel.updates::openInstallPermission,
        onDismiss = viewModel.updates::dismissUpdate,
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
        is AppState.Ready -> androidx.compose.runtime.CompositionLocalProvider(
            com.uridul.app.ui.LocalReviews provides com.uridul.app.ui.ReviewHooks(reviews, viewModel::saveReview, viewModel::deleteMyReview),
            com.uridul.app.ui.LocalPhotos provides com.uridul.app.ui.PhotoHooks(
                album = viewModel.photos::albumFlow,
                weekUsed = weekPhotos,
                uploading = photoUploading,
                onUpload = viewModel.photos::uploadPhotos,
                onDelete = viewModel.photos::deletePhoto,
                loadFull = viewModel.photos::loadPhoto,
                memoryThumbs = memoryPhotos,
                dayPhotos = dayPhotos,
                loadMonthPhotos = { m -> viewModel.photos.loadMonthPhotos(m) },
                hero = heroPhoto,
                refreshHero = viewModel.photos::refreshHero,
                heroAction = viewModel.photos::heroAction,
                partnerPhotoAt = partnerLatest?.uploadedAtMillis,
                refreshPartner = { viewModel.photos.refreshPartnerLatest() },
                refreshWeek = viewModel.photos::refreshWeekPhotoCount,
            ),
        ) { androidx.compose.foundation.layout.Box { ReadyScreen(
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
            onCheckUpdate = { viewModel.updates.checkForUpdate(manual = true) },
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
            onAddWish = viewModel.wishList::addWish,
            onWishVisited = viewModel.wishList::setWishVisited,
            onWishMemo = viewModel.wishList::setWishMemo,
            onDeleteWish = viewModel.wishList::deleteWish,
            onExportBackup = { viewModel.backup.exportBackup(it) },
            onImportBackup = { viewModel.backup.readBackup(it) },
            importPreview = importPreview,
            onConfirmImport = viewModel.backup::confirmImport,
            onCancelImport = viewModel.backup::cancelImport,
            memories = memories,
            onLoadMemories = viewModel::loadMemories,
            inbox = inbox,
            onInboxRead = { viewModel.markInboxRead(it) },
            onInboxAllRead = { viewModel.markAllInboxRead() },
            onSaveColors = viewModel.colors::setEventColors,
            partnerAlertsEnabled = partnerAlerts,
            onSetPartnerAlerts = { enabled ->
                viewModel.setPartnerAlerts(enabled)
                if (enabled && !Reminders.hasPermission(context)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onSetNotify = { enabled ->
                viewModel.setNotifyEnabled(enabled)
                if (enabled && !Reminders.hasPermission(context)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onSignOut = { viewModel.signOut(context) },
        )
            // 후기 저장·사진 올리기를 마치면 화면 가운데서 하트가 터진다 (짧은 진동과 함께)
            com.uridul.app.ui.HeartBurstOverlay()
        } }
    }
}

/** 앱 아이콘 바로가기 (res/xml/shortcuts.xml) 의 intent action */
internal const val SHORTCUT_ACTION = "com.uridul.app.SHORTCUT"

/** 바로가기 이름 → 열 화면 */
internal fun shortcutRequest(name: String?): OpenRequest? = when (name) {
    "add_event" -> OpenRequest(tab = "calendar", date = LocalDate.now(), addEvent = true)
    "ledger_add" -> OpenRequest(tab = "ledger", ledgerAdd = true)
    "diary" -> OpenRequest(tab = "diary")
    "question" -> OpenRequest(tab = "home", question = true)
    else -> null
}

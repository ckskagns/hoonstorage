package com.uridul.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.uridul.app.data.AppSettings
import com.uridul.app.ui.theme.CoupleColors
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.navigationBarsPadding
import com.uridul.app.data.DeletedEvent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.uridul.app.data.birthdays
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.uridul.app.OpenRequest
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Character
import com.uridul.app.data.Couple
import com.uridul.app.data.EventDraft
import com.uridul.app.data.Place
import com.uridul.app.data.toDraft
import com.uridul.app.data.PartnerDefaults
import java.time.LocalDate
import java.time.YearMonth

private enum class Tab(val key: String, val label: String, val icon: ImageVector) {
    Home("home", "디데이", Icons.Filled.Favorite),
    Calendar("calendar", "캘린더", Icons.Filled.DateRange),
    Diary("diary", "일기장", Icons.Filled.Edit),
    Wish("wish", "가고 싶은 곳", Icons.Filled.Place),
}

/**
 * 연결된 뒤의 메인 화면. 탭 2개(디데이 / 캘린더).
 *  - 폭 600dp 미만: 아래 탭 바
 *  - 폭 600dp 이상(폴드 펼침): 왼쪽 세로 탭(내비게이션 레일)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadyScreen(
    couple: Couple,
    myUid: String,
    busy: Boolean,
    snackbar: SnackbarHostState,
    month: YearMonth,
    monthEvents: List<CalendarEvent>,
    openRequest: OpenRequest?,
    onOpenRequestHandled: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onSaveEvent: (EventDraft) -> Boolean,
    onDeleteEvent: (String) -> Unit,
    onInvite: () -> Unit,
    onChangeStartDate: (LocalDate) -> Unit,
    onSetCountFromOne: (Boolean) -> Unit,
    onUpdateProfile: (String, Character, PartnerDefaults?) -> Unit,
    onSignOut: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    notifyEnabled: Boolean = true,
    onAttachPlace: (eventId: String, slot: Int, place: Place) -> Unit = { _, _, _ -> },
    /** 일정 색을 바꾸면 위젯도 다시 그린다 */
    onColorsChanged: () -> Unit = {},
    onSetNotify: (Boolean) -> Unit = {},
    /** 방금 지운 일정 ("되돌리기" 안내) */
    undo: com.uridul.app.data.UndoItem? = null,
    onUndo: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
    /** 최근 지운 일정 (이 폰에 30일 보관) */
    deletedEvents: List<DeletedEvent> = emptyList(),
    onRestoreDeleted: (CalendarEvent) -> Unit = {},
    /** 검색용 전체 일정 불러오기 */
    onLoadAllEvents: suspend () -> List<CalendarEvent>? = { null },
    /** 반복 일정에서 이 날만 빼기 */
    onSkipOccurrence: (String, LocalDate) -> Unit = { _, _ -> },
    /** 가고 싶은 곳 */
    wishes: List<com.uridul.app.data.WishPlace> = emptyList(),
    onAddWish: (Place) -> Unit = {},
    onWishVisited: (String, Boolean) -> Unit = { _, _ -> },
    onWishMemo: (String, String) -> Unit = { _, _ -> },
    onDeleteWish: (String) -> Unit = {},
    /** 백업 파일 */
    onExportBackup: (android.net.Uri) -> Unit = {},
    onImportBackup: (android.net.Uri) -> Unit = {},
    importPreview: com.uridul.app.data.ImportPreview? = null,
    onConfirmImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    /** 추억 슬라이드 */
    memories: List<com.uridul.app.domain.Memories.Slide> = emptyList(),
    onLoadMemories: (LocalDate) -> Unit = {},
    /** 앱 안 알림 (종 아이콘) */
    inbox: List<com.uridul.app.data.InboxItem> = emptyList(),
    onInboxRead: (String) -> Unit = {},
    onInboxAllRead: () -> Unit = {},
    /** 일정 색 저장 (내 색, 상대 색) — 기본은 이 폰에만 */
    onSaveColors: ((com.uridul.app.data.EventColor, com.uridul.app.data.EventColor) -> Unit)? = null,
    /** 상대 일정 알림 (추가·수정) */
    partnerAlertsEnabled: Boolean = true,
    onSetPartnerAlerts: (Boolean) -> Unit = {},
) {
    // 공유받은 장소를 어디에 담을지 묻는 창
    var placeChoice by remember { mutableStateOf<Place?>(null) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExportBackup) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportBackup) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showingDeleted by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
    var pickingTheme by remember { mutableStateOf(false) }
    var pickingColors by remember { mutableStateOf(false) }
    val uiSettings by AppSettings.state.collectAsState()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var tabKey by rememberSaveable { mutableStateOf(Tab.Home.key) }
    val tab = Tab.entries.firstOrNull { it.key == tabKey } ?: Tab.Home
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedText)
    var editing by remember { mutableStateOf<EditorTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    /** 하루 페이지: "날짜|열어 둘 일정 ID|new" (폴드 접고 펴기에도 유지) */
    var dayPageText by rememberSaveable { mutableStateOf("") }
    val dayPage = remember(dayPageText) { DayPageTarget.parse(dayPageText) }
    fun openDay(date: LocalDate, focus: String? = null, new: Boolean = false) {
        tabKey = Tab.Calendar.key
        dayPageText = DayPageTarget(date, focus, new).encode()
    }

    fun select(day: LocalDate) {
        selectedText = day.toString()
        if (YearMonth.from(day) != month) onMonthChange(YearMonth.from(day))
    }

    // 지도 앱에서 "공유 → 우리둘" 로 장소를 받은 경우: 하루 페이지·입력 창이 열려 있으면 그쪽이 가져가고,
    // 아니면 카드에서 시작한 일정에 저장하거나 그 장소로 새 일정을 연다
    val sharedPlace by PlaceInbox.place.collectAsState()
    LaunchedEffect(sharedPlace) {
        val place = sharedPlace ?: return@LaunchedEffect
        if (editing != null || dayPage != null) return@LaunchedEffect
        val target = PlaceInbox.eventTarget
        when {
            // 가고 싶은 곳에서 [+ 장소 담기] 로 시작했으면 목록에 바로 담기
            PlaceInbox.wishTarget -> {
                tabKey = Tab.Wish.key
                onAddWish(place)
                PlaceInbox.consume()
            }
            // 일정 카드의 [장소 찾기] 에서 시작했으면 그 일정·시간대에 바로 저장
            target != null -> {
                tabKey = Tab.Calendar.key
                onAttachPlace(target.eventId, target.slot, place)
                PlaceInbox.consume()
            }
            // 지도 앱에서 그냥 공유했으면 어디에 담을지 묻는다
            else -> {
                placeChoice = place
                PlaceInbox.consume()
            }
        }
    }

    // 위젯에서 열린 경우: 해당 탭/날짜로 이동, 필요하면 그 날 하루 페이지에서 새 일정
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        tabKey = Tab.entries.firstOrNull { it.key == request.tab }?.key ?: Tab.Home.key
        request.date?.let { selectedText = it.toString() }
        if (request.addEvent) openDay(request.date ?: selected, new = true)
        // 알림을 눌러 열었으면 그 일정을 연 채로 그 날 하루 페이지
        request.focusEventId?.let { id -> openDay(request.date ?: selected, focus = id) }
        onOpenRequestHandled()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneMinWidth

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (tab) {
                                Tab.Home -> "우리둘"
                                Tab.Calendar -> "우리 캘린더"
                                Tab.Wish -> "가고 싶은 곳"
                                Tab.Diary -> "우리 일기장"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        if (tab == Tab.Calendar) {
                            IconButton(onClick = { searching = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "일정 검색")
                            }
                            TextButton(onClick = { select(LocalDate.now()) }) { Text("오늘") }
                        }
                        // 🔔 알림 목록 (작은 창). 누르면 그 일정으로 이동 + 읽음
                        NotificationBell(
                            items = inbox,
                            onOpen = { item ->
                                onInboxRead(item.id)
                                val focus = item.eventId
                                when {
                                    // 가고 싶은 곳 소식이면 그 탭
                                    item.tab == Tab.Wish.key -> tabKey = Tab.Wish.key
                                    focus != null -> { select(item.date); openDay(item.date, focus = focus) }
                                    else -> { select(item.date); tabKey = Tab.Calendar.key }
                                }
                            },
                            onMarkAllRead = onInboxAllRead,
                        )
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "메뉴")
                        }
                        MainMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            couple = couple,
                            myUid = myUid,
                            notifyEnabled = notifyEnabled,
                            partnerAlertsEnabled = partnerAlertsEnabled,
                            themeLabel = uiSettings.theme.label,
                            deletedCount = deletedEvents.size,
                            onAction = { action ->
                                when (action) {
                                    MenuAction.Profile -> editingProfile = true
                                    MenuAction.StartDate -> picking = true
                                    MenuAction.ToggleCountFromOne -> onSetCountFromOne(!couple.countFromOne)
                                    MenuAction.Invite -> onInvite()
                                    MenuAction.ToggleNotify -> onSetNotify(!notifyEnabled)
                                    MenuAction.TogglePartnerAlerts -> onSetPartnerAlerts(!partnerAlertsEnabled)
                                    MenuAction.Theme -> pickingTheme = true
                                    MenuAction.Colors -> pickingColors = true
                                    MenuAction.Deleted -> showingDeleted = true
                                    MenuAction.BackupSave -> exportLauncher.launch(com.uridul.app.data.Backup.fileName(LocalDate.now()))
                                    MenuAction.BackupLoad -> importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                    MenuAction.CheckUpdate -> onCheckUpdate()
                                    MenuAction.SignOut -> onSignOut()
                                }
                            },
                        )
                    },
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tabKey = t.key },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.Calendar) {
                    if (wide) {
                        ExtendedFloatingActionButton(
                            onClick = { openDay(selected, new = true) },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("일정 추가") },
                        )
                    } else {
                        // 좁은 화면(폰·폴드 커버)에서는 달력을 덜 가리도록 둥근 + 버튼만
                        FloatingActionButton(onClick = { openDay(selected, new = true) }) {
                            Icon(Icons.Filled.Add, contentDescription = "일정 추가")
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail(windowInsets = WindowInsets(0), modifier = Modifier.fillMaxHeight()) {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tabKey = t.key },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
                // 탭을 바꾸면 살짝 밀리며 부드럽게 바뀐다
                androidx.compose.animation.AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { w -> if (forward) w / 8 else -w / 8 }) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
                    },
                    label = "tab",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { shownTab ->
                val content = Modifier.fillMaxSize()
                when (shownTab) {
                    Tab.Home -> HomeContent(
                        couple, myUid, busy, onInvite, content,
                        memories = memories,
                        onLoadMemories = onLoadMemories,
                        // 추억 카드를 누르면 그 날 하루 페이지에서 그 일정
                        onOpenMemory = { e -> select(e.date); openDay(e.date, focus = e.id) },
                    )
                    Tab.Calendar -> CalendarContent(
                        couple = couple,
                        myUid = myUid,
                        month = month,
                        events = monthEvents,
                        selected = selected,
                        onSelect = ::select,
                        onMonthChange = onMonthChange,
                        // 날짜를 다시 누르거나 길게 누르면 / 일정 카드를 누르면 → 하루 페이지
                        onAdd = { openDay(it, new = true) },
                        onEdit = { e -> openDay(if (e.occursOn(selected)) selected else e.date, focus = e.id) },
                        modifier = content,
                    )
                    Tab.Diary -> DiaryContent(
                        couple = couple,
                        myUid = myUid,
                        // 일기를 누르면 그 날 하루 페이지에서 그 일정
                        onOpenDay = { r -> select(r.date); openDay(r.date, focus = r.eventId) },
                        modifier = content,
                    )
                    Tab.Wish -> WishlistContent(
                        wishes = wishes,
                        couple = couple,
                        myUid = myUid,
                        // 날짜를 고르면 그 날 하루 페이지에서 이 장소로 새 일정
                        onPlan = { place, date ->
                            select(date)
                            openDay(date, new = true)
                            PlaceInbox.expectEditor()
                            PlaceInbox.offer(place)
                        },
                        onVisited = { w, v -> onWishVisited(w.id, v) },
                        onMemo = { w, m -> onWishMemo(w.id, m) },
                        onDelete = { w -> onDeleteWish(w.id) },
                        modifier = content,
                    )
                }
                }
            }
        }
        // 방금 지운 일정 되돌리기 (하루 페이지·입력 창이 열려 있으면 그쪽에서 보여준다)
        UndoBar(
            event = if (dayPage == null && editing == null && !searching) undo else null,
            onUndo = onUndo,
            onDismiss = onDismissUndo,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (wide) 24.dp else 88.dp),
        )
    }

    if (searching) {
        SearchDialog(
            myUid = myUid,
            load = onLoadAllEvents,
            onOpen = { e ->
                searching = false
                val day = if (e.occursOn(LocalDate.now())) LocalDate.now() else e.date
                select(day)
                openDay(day, focus = e.id)
            },
            onDismiss = { searching = false },
        )
    }

    placeChoice?.let { place ->
        PlaceChoiceDialog(
            place = place,
            selected = selected,
            onWish = {
                placeChoice = null
                tabKey = Tab.Wish.key
                onAddWish(place)
            },
            onEvent = {
                placeChoice = null
                // 고른 날 하루 페이지에서 이 장소로 새 일정
                openDay(selected, new = true)
                PlaceInbox.expectEditor()
                PlaceInbox.offer(place)
            },
            onDismiss = { placeChoice = null },
        )
    }

    importPreview?.let { p -> ImportPreviewDialog(p, onConfirm = onConfirmImport, onCancel = onCancelImport) }

    if (showingDeleted) {
        DeletedEventsDialog(
            items = deletedEvents,
            onRestore = { e ->
                onRestoreDeleted(e)
                showingDeleted = false
                // 되돌린 일정이 있는 날을 보여준다
                tabKey = Tab.Calendar.key
                select(e.date)
            },
            onDismiss = { showingDeleted = false },
        )
    }

    // 하루 페이지 (하루 일정을 한 페이지에서 모두 관리)
    dayPage?.let { page ->
        val daySpecials = remember(couple, page.date) {
            com.uridul.app.domain.SpecialDays.on(couple.startDate, couple.countFromOne, couple.birthdays(), page.date)
        }
        androidx.compose.runtime.key(dayPageText) {
            DayTimelinePage(
                date = page.date,
                events = monthEvents,
                couple = couple,
                myUid = myUid,
                specials = daySpecials,
                focusEventId = page.focus,
                startNew = page.new,
                onClose = { dayPageText = "" },
                onSave = onSaveEvent,
                onDelete = onDeleteEvent,
                onOpenFullEditor = { draft -> editing = EditorTarget.FromDraft(draft) },
                incomingPlace = if (editing == null) sharedPlace else null,
                onIncomingPlaceConsumed = PlaceInbox::consume,
                onAttachPlace = onAttachPlace,
                undo = if (editing == null) undo else null,
                onUndo = onUndo,
                onDismissUndo = onDismissUndo,
                onSkip = onSkipOccurrence,
            )
        }
    }

    editing?.let { target ->
        val draft = when (target) {
            is EditorTarget.New -> EventDraft(title = "", date = target.date, endDate = target.date, time = null, memo = "")
            is EditorTarget.Existing -> target.event.toDraft()
            is EditorTarget.FromDraft -> target.draft
        }
        // 대상이 바뀌면 입력 상태를 새로 만든다
        androidx.compose.runtime.key(target) {
            EventEditorDialog(
                initial = draft,
                onDismiss = { editing = null },
                onSave = onSaveEvent,
                onDelete = when (target) {
                    is EditorTarget.Existing -> ({ onDeleteEvent(target.event.id) })
                    is EditorTarget.FromDraft -> target.draft.id?.let { id -> { onDeleteEvent(id) } }
                    else -> null
                },
                incomingPlace = sharedPlace,
                onIncomingPlaceConsumed = PlaceInbox::consume,
            )
        }
    }

    if (pickingTheme) {
        ThemeModeDialog(
            current = uiSettings.theme,
            onPick = { mode ->
                AppSettings.update(appContext) { it.copy(theme = mode) }
                // 위젯도 같은 화면 모드로 다시 그린다
                onColorsChanged()
            },
            onDismiss = { pickingTheme = false },
        )
    }
    if (pickingColors) {
        EventColorDialog(
            settings = uiSettings,
            myName = couple.myName(myUid),
            partnerName = couple.partnerName(myUid) ?: "상대",
            // 고른 색은 서버에도 저장해서 상대 폰도 같은 색으로 본다
            onPickMine = { c ->
                onSaveColors?.invoke(c, uiSettings.partner) ?: AppSettings.update(appContext) { it.copy(mine = c) }
                onColorsChanged()
            },
            onPickPartner = { c ->
                onSaveColors?.invoke(uiSettings.mine, c) ?: AppSettings.update(appContext) { it.copy(partner = c) }
                onColorsChanged()
            },
            onDismiss = { pickingColors = false },
        )
    }

    if (editingProfile) {
        ProfileDialog(
            myName = couple.myName(myUid),
            myCharacter = couple.myCharacter(myUid),
            partnerDefaults = couple.partnerDefaults,
            isPaired = couple.isPaired,
            onDismiss = { editingProfile = false },
            onSave = onUpdateProfile,
        )
    }

    if (picking) {
        StartDatePickerDialog(
            initial = couple.startDate,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onChangeStartDate(it)
            },
        )
    }
}

private sealed interface EditorTarget {
    data class New(val date: LocalDate) : EditorTarget
    data class Existing(val event: CalendarEvent) : EditorTarget
    /** 하루 페이지의 "날짜·메모 자세히" 에서 넘어온 입력값 */
    data class FromDraft(val draft: EventDraft) : EditorTarget
}

/** 하루 페이지 대상: 날짜 + 열어 둘 일정 + 새 일정으로 시작할지 */
private data class DayPageTarget(val date: LocalDate, val focus: String?, val new: Boolean) {
    fun encode() = "$date|${focus.orEmpty()}|$new"

    companion object {
        fun parse(text: String): DayPageTarget? {
            val parts = text.split("|")
            if (parts.size != 3) return null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            return DayPageTarget(date, parts[1].ifEmpty { null }, parts[2] == "true")
        }
    }
}

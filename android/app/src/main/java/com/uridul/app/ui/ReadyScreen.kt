package com.uridul.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
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
import com.uridul.app.data.PartnerDefaults
import java.time.LocalDate
import java.time.YearMonth

private enum class Tab(val key: String, val label: String, val icon: ImageVector) {
    Home("home", "디데이", Icons.Filled.Favorite),
    Calendar("calendar", "캘린더", Icons.Filled.DateRange),
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
    onSetNotify: (Boolean) -> Unit = {},
) {
    var editingProfile by remember { mutableStateOf(false) }
    var tabKey by rememberSaveable { mutableStateOf(Tab.Home.key) }
    val tab = Tab.entries.firstOrNull { it.key == tabKey } ?: Tab.Home
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedText)
    var editing by remember { mutableStateOf<EditorTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    fun select(day: LocalDate) {
        selectedText = day.toString()
        if (YearMonth.from(day) != month) onMonthChange(YearMonth.from(day))
    }

    // 지도 앱에서 "공유 → 우리둘" 로 장소를 받은 경우: 입력 창이 없으면 그 장소로 새 일정 입력 창을 연다
    val sharedPlace by PlaceInbox.place.collectAsState()
    LaunchedEffect(sharedPlace) {
        if (sharedPlace != null && editing == null) {
            tabKey = Tab.Calendar.key
            editing = EditorTarget.New(selected)
        }
    }

    // 위젯에서 열린 경우: 해당 탭/날짜로 이동, 필요하면 일정 추가 창 열기
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        tabKey = if (request.tab == Tab.Calendar.key) Tab.Calendar.key else Tab.Home.key
        request.date?.let { selectedText = it.toString() }
        if (request.addEvent) editing = EditorTarget.New(request.date ?: selected)
        onOpenRequestHandled()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneMinWidth

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (tab == Tab.Home) "우리둘" else "우리 캘린더", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        if (tab == Tab.Calendar) {
                            TextButton(onClick = { select(LocalDate.now()) }) { Text("오늘") }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "메뉴")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("내 프로필 (이름·캐릭터)") },
                                leadingIcon = {
                                    CharacterAvatar(couple.myCharacter(myUid), 24.dp)
                                },
                                onClick = { menuOpen = false; editingProfile = true },
                            )
                            DropdownMenuItem(
                                text = { Text("시작일 변경") },
                                onClick = { menuOpen = false; picking = true },
                            )
                            DropdownMenuItem(
                                text = { Text("사귄 날을 1일로 계산") },
                                trailingIcon = { Checkbox(checked = couple.countFromOne, onCheckedChange = null) },
                                onClick = { menuOpen = false; onSetCountFromOne(!couple.countFromOne) },
                            )
                            if (!couple.isPaired) {
                                DropdownMenuItem(
                                    text = { Text("초대 링크 보내기") },
                                    onClick = { menuOpen = false; onInvite() },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("일정 알림 (당일 아침·30분 전)") },
                                trailingIcon = { Checkbox(checked = notifyEnabled, onCheckedChange = null) },
                                onClick = { menuOpen = false; onSetNotify(!notifyEnabled) },
                            )
                            DropdownMenuItem(
                                text = { Text("업데이트 확인 (현재 ${com.uridul.app.BuildConfig.VERSION_NAME})") },
                                onClick = { menuOpen = false; onCheckUpdate() },
                            )
                            DropdownMenuItem(
                                text = { Text("로그아웃") },
                                onClick = { menuOpen = false; onSignOut() },
                            )
                        }
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
                            onClick = { editing = EditorTarget.New(selected) },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("일정 추가") },
                        )
                    } else {
                        // 좁은 화면(폰·폴드 커버)에서는 달력을 덜 가리도록 둥근 + 버튼만
                        FloatingActionButton(onClick = { editing = EditorTarget.New(selected) }) {
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
                val content = Modifier.weight(1f).fillMaxHeight()
                when (tab) {
                    Tab.Home -> HomeContent(couple, myUid, busy, onInvite, content)
                    Tab.Calendar -> CalendarContent(
                        couple = couple,
                        myUid = myUid,
                        month = month,
                        events = monthEvents,
                        selected = selected,
                        onSelect = ::select,
                        onMonthChange = onMonthChange,
                        onAdd = { editing = EditorTarget.New(it) },
                        onEdit = { editing = EditorTarget.Existing(it) },
                        modifier = content,
                    )
                }
            }
        }
    }

    editing?.let { target ->
        val draft = when (target) {
            is EditorTarget.New -> EventDraft(title = "", date = target.date, endDate = target.date, time = null, memo = "")
            is EditorTarget.Existing -> with(target.event) { EventDraft(id, title, date, endDate, time, memo, place) }
        }
        // 대상이 바뀌면 입력 상태를 새로 만든다
        androidx.compose.runtime.key(target) {
            EventEditorDialog(
                initial = draft,
                onDismiss = { editing = null },
                onSave = onSaveEvent,
                onDelete = (target as? EditorTarget.Existing)?.let { { onDeleteEvent(it.event.id) } },
                incomingPlace = sharedPlace,
                onIncomingPlaceConsumed = PlaceInbox::consume,
            )
        }
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
}

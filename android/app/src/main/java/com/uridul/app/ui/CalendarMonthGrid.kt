package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.domain.CalendarMath
import com.uridul.app.data.birthdays
import com.uridul.app.domain.SpecialDay
import com.uridul.app.domain.SpecialDays
import com.uridul.app.domain.SpecialKind
import com.uridul.app.ui.theme.CoupleColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// 공유 캘린더의 한 달 달력: 달 넘기기(슬라이드), 6주 칸, 여러 날 막대, 날짜 칸. CalendarContent 에서 쓴다.

/** 달 넘기기 페이지: 이번 달을 가운데에 두고 앞뒤로 100년씩 */
private const val PAGE_CENTER = 1200
private const val PAGE_COUNT = 2401

/**
 * 한 달 달력. 좌우로 밀면 손가락을 따라 다음/이전 달이 부드럽게 넘어온다 (화살표도 같은 애니메이션).
 */
@Composable
internal fun MonthCalendar(
    month: YearMonth,
    events: List<CalendarEvent>,
    specialsFor: (List<LocalDate>) -> Map<LocalDate, List<SpecialDay>>,
    selected: LocalDate,
    today: LocalDate,
    myUid: String,
    onSelect: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    fillHeight: Boolean,
    modifier: Modifier,
    /** 길게 누른 채 여러 날을 끌면 그 기간으로 새 일정 */
    onAddRange: (LocalDate, LocalDate) -> Unit = { s, _ -> onAdd(s) },
) {
    val base = remember { YearMonth.now() }
    var pickingMonth by remember { mutableStateOf(false) }
    fun pageOf(m: YearMonth) = PAGE_CENTER + ChronoUnit.MONTHS.between(base, m).toInt()
    fun monthOf(page: Int): YearMonth = base.plusMonths((page - PAGE_CENTER).toLong())
    val pagerState = rememberPagerState(initialPage = pageOf(month)) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val latestMonth by rememberUpdatedState(month)
    val latestOnMonthChange by rememberUpdatedState(onMonthChange)

    // 밖에서 달이 바뀌면(오늘 버튼·위젯·다른 달 날짜 누르기) 그 달로 넘긴다 (멀면 바로, 가까우면 슬라이드)
    LaunchedEffect(month) {
        val target = pageOf(month)
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            if (abs(pagerState.currentPage - target) > 3) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    // 넘기기가 끝나면 그 달 일정을 불러온다
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val m = monthOf(page)
            if (m != latestMonth) latestOnMonthChange(m)
        }
    }
    val shownMonth = monthOf(pagerState.currentPage)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            // 머리글: < 2026년 10월 >
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
                }
                AnimatedContent(
                    targetState = shownMonth,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally { w -> if (forward) w / 3 else -w / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { w -> if (forward) -w / 3 else w / 3 } + fadeOut())
                    },
                    label = "month-title",
                    modifier = Modifier.weight(1f),
                ) { m ->
                    // 기본 캘린더처럼 달 제목을 누르면 년·월을 바로 고른다
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "${m.year}년 ${m.monthValue}월 ▾",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClickLabel = "년·월 고르기") { pickingMonth = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
                }
            }
            // 요일
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (i) {
                            0 -> CoupleColors.sunday
                            6 -> CoupleColors.saturday
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
                modifier = if (fillHeight) Modifier.weight(1f) else Modifier,
            ) { page ->
                val pageMonth = monthOf(page)
                val grid = remember(pageMonth) { CalendarMath.monthGrid(pageMonth) }
                val specials = remember(grid, specialsFor) { specialsFor(grid) }
                MonthGrid(pageMonth, grid, events, specials, selected, today, myUid, onSelect, onAdd, onAddRange, fillHeight)
            }
        }
    }
    if (pickingMonth) {
        MonthPickerDialog(
            initial = shownMonth,
            today = today,
            onPick = { m ->
                pickingMonth = false
                val target = pageOf(m)
                scope.launch {
                    if (abs(pagerState.currentPage - target) > 3) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
                }
            },
            onDismiss = { pickingMonth = false },
        )
    }
}

/** 년·월 고르기: 위에서 해를 넘기고, 12달 중 하나를 누른다 */
@Composable
private fun MonthPickerDialog(initial: YearMonth, today: LocalDate, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(initial.year) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 해") }
                Text("${year}년", textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 해") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mo ->
                            val ym = YearMonth.of(year, mo)
                            val isShown = ym == initial
                            val isNow = ym == YearMonth.from(today)
                            val scheme = MaterialTheme.colorScheme
                            Text(
                                "${mo}월",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isShown || isNow) FontWeight.Bold else FontWeight.Normal,
                                color = if (isShown) scheme.onPrimary else if (isNow) scheme.primary else scheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isShown) scheme.primary else scheme.surfaceContainerHigh)
                                    .clickable { onPick(ym) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(YearMonth.from(today)) }) { Text("이번 달") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 길게 누르기 전 손짓 결과 */
private enum class EarlyEnd { TAP, CANCEL }

/**
 * 달력 칸 손짓 (안드로이드 기본 캘린더와 같게):
 *  - 한 번 누르기: 그 날 선택 (선택된 날을 다시 누르면 새 일정)
 *  - 길게 누르기: 그 날 새 일정
 *  - 길게 누른 채 옆 날짜로 끌기: 끈 기간으로 여러 날 새 일정
 * 길게 누르기 전에 손가락이 움직이면 손짓을 놓아서 달 넘기기·위아래 스크롤이 그대로 된다.
 */
private fun Modifier.dayGestures(
    grid: List<LocalDate>,
    rows: Int,
    cellWidthPx: Float,
    rowHeightPx: Float,
    onTap: () -> ((LocalDate) -> Unit),
    onLongPress: () -> ((LocalDate) -> Unit),
    onRange: () -> ((LocalDate, LocalDate) -> Unit),
    onDragging: (ClosedRange<LocalDate>?) -> Unit,
    onHaptic: () -> Unit,
): Modifier = pointerInput(grid, rows, cellWidthPx, rowHeightPx) {
    fun dayAt(p: androidx.compose.ui.geometry.Offset): LocalDate? {
        if (p.x < 0f || p.y < 0f) return null
        val row = (p.y / rowHeightPx).toInt()
        if (row >= rows) return null
        val col = (p.x / cellWidthPx).toInt().coerceIn(0, 6)
        return grid.getOrNull(row * 7 + col)
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = dayAt(down.position) ?: return@awaitEachGesture
        // null = 길게 누르기 시간이 지남
        val early: EarlyEnd? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: EarlyEnd? = null
            while (result == null) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                result = when {
                    ch == null -> EarlyEnd.CANCEL
                    ch.changedToUp() -> if (ch.isConsumed) EarlyEnd.CANCEL else EarlyEnd.TAP
                    ch.isConsumed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop -> EarlyEnd.CANCEL
                    else -> null
                }
            }
            result
        }
        if (early != null) {
            if (early == EarlyEnd.TAP) onTap()(start)
            return@awaitEachGesture
        }
        onHaptic()
        var end = start
        var finished = false
        onDragging(start..start)
        try {
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                if (!ch.pressed) {
                    finished = true
                    break
                }
                val d = dayAt(ch.position) ?: continue
                // 한 일정은 최대 31일
                val limited = when {
                    d.isAfter(start.plusDays(CalendarEvent.MAX_SPAN_DAYS - 1)) -> start.plusDays(CalendarEvent.MAX_SPAN_DAYS - 1)
                    d.isBefore(start.minusDays(CalendarEvent.MAX_SPAN_DAYS - 1)) -> start.minusDays(CalendarEvent.MAX_SPAN_DAYS - 1)
                    else -> d
                }
                if (limited != end) {
                    end = limited
                    onDragging(minOf(start, end)..maxOf(start, end))
                }
            }
        } finally {
            onDragging(null)
        }
        if (finished) {
            if (end == start) onLongPress()(start) else onRange()(minOf(start, end), maxOf(start, end))
        }
    }
}

/** 한 달 6주 칸 (여러 날 일정은 칸을 가로지르는 막대로 이어서 그린다) */
@Composable
private fun MonthGrid(
    month: YearMonth,
    grid: List<LocalDate>,
    events: List<CalendarEvent>,
    specials: Map<LocalDate, List<SpecialDay>>,
    selected: LocalDate,
    today: LocalDate,
    myUid: String,
    onSelect: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onAddRange: (LocalDate, LocalDate) -> Unit,
    fillHeight: Boolean,
) {
            val latestSelected by rememberUpdatedState(selected)
            val latestOnSelect by rememberUpdatedState(onSelect)
            val latestOnAdd by rememberUpdatedState(onAdd)
            val latestOnAddRange by rememberUpdatedState(onAddRange)
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            /** 길게 눌러 끄는 중인 기간 */
            var dragging by remember { mutableStateOf<ClosedRange<LocalDate>?>(null) }
            BoxWithConstraints(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                val cellWidth: Dp = maxWidth / 7
                // 칸이 좁으면(폰 세로) 일정 제목 대신 점·얇은 선으로 표시
                val showLabels = cellWidth >= 48.dp
                val rowHeight: Dp = if (fillHeight) maxHeight / 6 else (cellWidth * 1.25f).coerceIn(60.dp, 88.dp)
                // 날짜 숫자(약 28dp) 아래에 들어가는 줄 수 (한 줄 16dp)
                val maxLabels = ((rowHeight - CellTop) / LinePitch).toInt().coerceIn(1, 4)
                // 칸마다 전체 일정을 다시 거르지 않도록 한 달치를 한 번에 계산해 둔다
                val weeks = remember(grid) { grid.chunked(7) }
                val dayEvents = remember(events, grid) { grid.associateWith { CalendarMath.eventsOn(events, it) } }
                // 칸마다 그릴 하루 일정(여러 날 일정은 막대로 따로)도 한 번에 (날짜를 누를 때마다 다시 거르지 않게)
                val daySingles = remember(dayEvents) { dayEvents.mapValues { (_, list) -> list.filter { !it.isMultiDay } } }
                val layouts = remember(events, weeks) { weeks.map { CalendarMath.weekLayout(it, events) } }
                val density = LocalDensity.current
                Column(
                    Modifier.dayGestures(
                        grid = grid,
                        rows = weeks.size,
                        cellWidthPx = with(density) { cellWidth.toPx() },
                        rowHeightPx = with(density) { rowHeight.toPx() },
                        // 한 번 누르면 선택, 선택된 날을 다시 누르면 그 날짜로 새 일정
                        onTap = { { day -> if (day == latestSelected) latestOnAdd(day) else latestOnSelect(day) } },
                        onLongPress = { { day -> latestOnSelect(day); latestOnAdd(day) } },
                        onRange = { { s, e -> latestOnSelect(s); latestOnAddRange(s, e) } },
                        onDragging = { dragging = it },
                        onHaptic = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                    ),
                ) {
                    weeks.forEachIndexed { w, week ->
                        val layout = layouts[w]
                        Box(Modifier.fillMaxWidth().height(rowHeight)) {
                            Row(Modifier.fillMaxSize()) {
                                week.forEachIndexed { col, day ->
                                    DayCell(
                                        day = day,
                                        inMonth = YearMonth.from(day) == month,
                                        isToday = day == today,
                                        isSelected = day == selected,
                                        inRange = dragging?.contains(day) == true,
                                        special = specials[day]?.firstOrNull(),
                                        holiday = com.uridul.app.domain.Holidays.isHoliday(day),
                                        allCount = dayEvents[day]?.size ?: 0,
                                        singles = daySingles[day].orEmpty(),
                                        lanes = layout.lanesUsed[col],
                                        hiddenBars = layout.bars.count { col in it.startCol..it.endCol && it.lane >= maxLabels },
                                        myUid = myUid,
                                        showLabels = showLabels,
                                        maxLabels = maxLabels,
                                        // 손짓은 달력 전체에서 받는다 (dayGestures). 여기는 화면 읽기(TalkBack)용 동작
                                        onClick = { if (day == selected) onAdd(day) else onSelect(day) },
                                        onLongClick = { onSelect(day); onAdd(day) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                            layout.bars.forEach { bar ->
                                val visible = if (showLabels) bar.lane < maxLabels else bar.lane < 3
                                if (visible) {
                                    EventBar(
                                        bar = bar,
                                        cellWidth = cellWidth,
                                        showLabel = showLabels,
                                        myUid = myUid,
                                        inMonth = YearMonth.from(week[bar.startCol]) == month,
                                    )
                                }
                            }
                        }
                    }
                }
            }
}

/** 칸 위쪽 여백 + 날짜 숫자 높이 */
private val CellTop = 28.dp
/** 일정 한 줄 간격 (글자 칸) */
private val LinePitch = 16.dp
/** 좁은 칸에서 얇은 선 간격 */
private val ThinPitch = 6.dp

/** 여러 날 일정 막대: 이어지는 쪽은 모서리를 각지게 해서 다음 칸과 붙어 보이게 한다 */
@Composable
private fun EventBar(bar: CalendarMath.WeekBar, cellWidth: Dp, showLabel: Boolean, myUid: String, inMonth: Boolean) {
    // 막대는 파스텔 바탕 + 진한 글자 (일정마다 고른 색, 없으면 등록한 사람 색)
    val color = CoupleColors.strongOf(bar.event, myUid)
    val fill = CoupleColors.fillOf(bar.event, myUid)
    val alpha = if (inMonth) 1f else 0.45f
    val startInset = if (bar.continuesLeft) 0.dp else 3.dp
    val endInset = if (bar.continuesRight) 0.dp else 3.dp
    val radius = if (showLabel) 4.dp else 2.dp
    val shape = RoundedCornerShape(
        topStart = if (bar.continuesLeft) 0.dp else radius,
        bottomStart = if (bar.continuesLeft) 0.dp else radius,
        topEnd = if (bar.continuesRight) 0.dp else radius,
        bottomEnd = if (bar.continuesRight) 0.dp else radius,
    )
    val x = cellWidth * bar.startCol + startInset
    val width = cellWidth * bar.span - startInset - endInset
    if (showLabel) {
        val density = LocalDensity.current
        val style = MaterialTheme.typography.labelSmall.copy(
            fontSize = with(density) { 11.dp.toSp() },
            lineHeight = with(density) { 14.dp.toSp() },
        )
        Text(
            if (bar.continuesLeft) "… ${bar.event.title}" else bar.event.title,
            style = style,
            color = CoupleColors.onFill.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier
                .offset(x = x, y = CellTop + LinePitch * bar.lane + 1.dp)
                .width(width)
                .height(14.dp)
                .clip(shape)
                .background(fill.copy(alpha = alpha))
                .padding(horizontal = 4.dp),
        )
    } else {
        Box(
            Modifier
                .offset(x = x, y = CellTop + ThinPitch * bar.lane + 2.dp)
                .width(width)
                .height(4.dp)
                .background(color.copy(alpha = alpha), shape),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    /** 길게 눌러 끄는 기간 안 */
    inRange: Boolean,
    special: SpecialDay?,
    /** 공휴일·대체공휴일이면 일요일처럼 빨간 숫자 */
    holiday: Boolean,
    /** 이 날의 전체 일정 수 (여러 날 일정 포함) */
    allCount: Int,
    /** 하루 일정 (여러 날 일정은 막대로 따로 그림) */
    singles: List<CalendarEvent>,
    /** 이 날 위로 지나가는 막대 줄 수 */
    lanes: Int,
    /** 칸이 모자라 안 보이는 막대 수 */
    hiddenBars: Int,
    myUid: String,
    showLabels: Boolean,
    maxLabels: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 달력 칸은 좁아서 글꼴 크기 설정을 키워도 칸 안 글자는 일정한 크기로 유지한다
    // (아래 일정 목록은 글꼴 크기 설정을 그대로 따른다)
    val density = LocalDensity.current
    val dayStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = with(density) { 14.dp.toSp() },
        lineHeight = with(density) { 18.dp.toSp() },
    )
    val smallStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = with(density) { 11.dp.toSp() },
        lineHeight = with(density) { 14.dp.toSp() },
    )
    val dayColor = when {
        !inMonth && (holiday || day.dayOfWeek == DayOfWeek.SUNDAY) -> CoupleColors.sunday.copy(alpha = 0.4f)
        !inMonth -> scheme.onSurface.copy(alpha = 0.35f)
        holiday || day.dayOfWeek == DayOfWeek.SUNDAY -> CoupleColors.sunday
        day.dayOfWeek == DayOfWeek.SATURDAY -> CoupleColors.saturday
        else -> scheme.onSurface
    }
    val description = buildString {
        append("${day.monthValue}월 ${day.dayOfMonth}일")
        if (isToday) append(", 오늘")
        special?.let { append(", ${it.title}") }
        if (allCount > 0) append(", 일정 ${allCount}개")
        if (isSelected) append(", 한 번 더 누르면 일정 추가")
    }
    val rangeColor = scheme.primary.copy(alpha = 0.28f)

    // 선택한 날: 바탕색이 부드럽게 물들고 날짜 숫자가 통통 튄다
    val selectedBg by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            inRange -> rangeColor
            isSelected -> scheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "day-bg",
    )
    val numberScale = bounceScale(isSelected, selectedScale = 1.15f)
    Column(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(selectedBg)
            .semantics {
                contentDescription = description
                this.onClick(label = if (isSelected) "새 일정" else "선택") { onClick(); true }
                this.onLongClick(label = "새 일정") { onLongClick(); true }
            }
            .padding(top = 3.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = numberScale
                    scaleY = numberScale
                }
                .then(if (isToday) Modifier.background(scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = dayStyle,
                color = if (isToday) scheme.onPrimary else dayColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        // 위로 지나가는 막대 자리는 비워 둔다
        val barLines = if (showLabels) minOf(lanes, maxLabels) else minOf(lanes, 3)
        if (barLines > 0) {
            Spacer(Modifier.height(if (showLabels) LinePitch * barLines else ThinPitch * barLines + 2.dp))
        }

        var linesLeft = maxLabels - barLines
        if (special != null && (!showLabels || linesLeft > 0)) {
            // 우리 기념일·생일은 진하게, 연인 기념일(발렌타인데이 등)은 연하게
            val personal = special.kind.personal
            Text(
                when {
                    !showLabels -> special.emoji
                    special.kind == SpecialKind.MILESTONE -> "♥${special.title}"
                    // 공휴일은 빨간 글자만으로 알 수 있어서 그림 없이 (좁은 칸에서 이름이 잘리지 않게)
                    special.kind == SpecialKind.HOLIDAY -> special.shortTitle
                    else -> "${special.emoji}${special.shortTitle}"
                },
                style = smallStyle,
                color = when {
                    personal -> scheme.primary
                    special.kind == SpecialKind.HOLIDAY -> CoupleColors.sunday.copy(alpha = if (inMonth) 1f else 0.5f)
                    else -> scheme.onSurfaceVariant.copy(alpha = if (inMonth) 1f else 0.5f)
                },
                fontWeight = if (personal) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
            linesLeft--
        }
        if (singles.isEmpty() && hiddenBars == 0) return@Column
        val alpha = if (inMonth) 1f else 0.45f
        if (showLabels) {
            // 칸이 모자라면 마지막 줄을 "+N" 으로 바꾼다 (안 보이는 막대 수도 포함)
            val room = linesLeft.coerceAtLeast(0)
            val fitsAll = hiddenBars == 0 && singles.size <= room
            val shown = if (fitsAll) singles else singles.take((room - 1).coerceAtLeast(0))
            shown.forEach { e ->
                val fill = CoupleColors.fillOf(e, myUid)
                Text(
                    e.title,
                    style = smallStyle,
                    color = CoupleColors.onFill.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    // 띄어쓰기에서 줄바꿈되어 "Fold test2" 가 "Fold" 로만 보이던 문제 방지
                    softWrap = false,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(fill.copy(alpha = alpha))
                        .padding(horizontal = 2.dp),
                )
            }
            val hidden = singles.size - shown.size + hiddenBars
            if (hidden > 0) {
                Text(
                    "+$hidden",
                    style = smallStyle,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        } else {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                singles.take(3).forEach { e ->
                    val color = CoupleColors.strongOf(e, myUid)
                    Box(Modifier.size(5.dp).background(color.copy(alpha = alpha), CircleShape))
                }
            }
        }
    }
}

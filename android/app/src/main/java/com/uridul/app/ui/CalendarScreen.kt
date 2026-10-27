package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * 공유 캘린더.
 *  - 폰 / 폴드 커버(600dp 미만): 위 달력, 아래 선택한 날 일정
 *  - 폴드 펼침 / 가로(600dp 이상): 왼쪽 달력, 오른쪽 선택한 날 일정
 */
@Composable
fun CalendarContent(
    couple: Couple,
    myUid: String,
    month: YearMonth,
    events: List<CalendarEvent>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = rememberToday()
    val grid = remember(month) { CalendarMath.monthGrid(month) }
    val birthdays = remember(couple) { couple.birthdays() }
    val specials = remember(couple.startDate, couple.countFromOne, birthdays, grid) {
        SpecialDays.between(couple.startDate, couple.countFromOne, birthdays, grid.first(), grid.last())
    }
    val selectedEvents = remember(events, selected) { CalendarMath.eventsOn(events, selected) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneMinWidth
        if (twoPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MonthCalendar(
                    month, grid, events, specials, selected, today, myUid, onSelect, onAdd, onMonthChange,
                    fillHeight = true,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                )
                DayPanel(
                    selected, selectedEvents, specials[selected].orEmpty(), couple, myUid, onAdd, onEdit,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val m = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                MonthCalendar(
                    month, grid, events, specials, selected, today, myUid, onSelect, onAdd, onMonthChange,
                    fillHeight = false,
                    modifier = m,
                )
                DayPanel(selected, selectedEvents, specials[selected].orEmpty(), couple, myUid, onAdd, onEdit, m)
                // 아래 버튼(일정 추가)에 마지막 일정이 가려지지 않게
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    grid: List<LocalDate>,
    events: List<CalendarEvent>,
    specials: Map<LocalDate, List<SpecialDay>>,
    selected: LocalDate,
    today: LocalDate,
    myUid: String,
    onSelect: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    fillHeight: Boolean,
    modifier: Modifier,
) {
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    Card(
        modifier = modifier.pointerInput(month) {
            // 좌우로 밀어서 달 넘기기
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (abs(total) > swipeThreshold) onMonthChange(if (total < 0) month.plusMonths(1) else month.minusMonths(1))
                },
                onHorizontalDrag = { _, amount -> total += amount },
            )
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            // 머리글: < 2026년 10월 >
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
                }
                Text(
                    "${month.year}년 ${month.monthValue}월",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
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
            // 6주 (여러 날 일정은 칸을 가로지르는 막대로 이어서 그린다)
            BoxWithConstraints(if (fillHeight) Modifier.weight(1f) else Modifier) {
                val cellWidth: Dp = maxWidth / 7
                // 칸이 좁으면(폰 세로) 일정 제목 대신 점·얇은 선으로 표시
                val showLabels = cellWidth >= 48.dp
                val rowHeight: Dp = if (fillHeight) maxHeight / 6 else (cellWidth * 1.25f).coerceIn(60.dp, 88.dp)
                // 날짜 숫자(약 28dp) 아래에 들어가는 줄 수 (한 줄 16dp)
                val maxLabels = ((rowHeight - CellTop) / LinePitch).toInt().coerceIn(1, 4)
                Column {
                    grid.chunked(7).forEach { week ->
                        val layout = remember(week, events) { CalendarMath.weekLayout(week, events) }
                        Box(Modifier.fillMaxWidth().height(rowHeight)) {
                            Row(Modifier.fillMaxSize()) {
                                week.forEachIndexed { col, day ->
                                    DayCell(
                                        day = day,
                                        inMonth = YearMonth.from(day) == month,
                                        isToday = day == today,
                                        isSelected = day == selected,
                                        special = specials[day]?.firstOrNull(),
                                        allCount = CalendarMath.eventsOn(events, day).size,
                                        singles = CalendarMath.singleDayEventsOn(events, day),
                                        lanes = layout.lanesUsed[col],
                                        hiddenBars = layout.bars.count { col in it.startCol..it.endCol && it.lane >= maxLabels },
                                        myUid = myUid,
                                        showLabels = showLabels,
                                        maxLabels = maxLabels,
                                        // 한 번 누르면 선택, 선택된 날을 다시 누르거나 길게 누르면 그 날짜로 일정 추가
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
                                        mine = bar.event.createdBy == myUid,
                                        inMonth = YearMonth.from(week[bar.startCol]) == month,
                                    )
                                }
                            }
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
private fun EventBar(bar: CalendarMath.WeekBar, cellWidth: Dp, showLabel: Boolean, mine: Boolean, inMonth: Boolean) {
    val color = if (mine) CoupleColors.mine else CoupleColors.partner
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier
                .offset(x = x, y = CellTop + LinePitch * bar.lane + 1.dp)
                .width(width)
                .height(14.dp)
                .clip(shape)
                .background(color.copy(alpha = 0.32f * alpha))
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
    special: SpecialDay?,
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
        !inMonth -> scheme.onSurface.copy(alpha = 0.35f)
        day.dayOfWeek == DayOfWeek.SUNDAY -> CoupleColors.sunday
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

    Column(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) scheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description }
            .padding(top = 3.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(24.dp)
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
                    else -> "${special.emoji}${special.title}"
                },
                style = smallStyle,
                color = if (personal) scheme.primary else scheme.onSurfaceVariant.copy(alpha = if (inMonth) 1f else 0.5f),
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
                val color = if (e.createdBy == myUid) CoupleColors.mine else CoupleColors.partner
                Text(
                    e.title,
                    style = smallStyle,
                    color = scheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    // 띄어쓰기에서 줄바꿈되어 "Fold test2" 가 "Fold" 로만 보이던 문제 방지
                    softWrap = false,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.22f * alpha))
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
                    val color = if (e.createdBy == myUid) CoupleColors.mine else CoupleColors.partner
                    Box(Modifier.size(5.dp).background(color.copy(alpha = alpha), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun DayPanel(
    day: LocalDate,
    events: List<CalendarEvent>,
    specials: List<SpecialDay>,
    couple: Couple,
    myUid: String,
    onAdd: (LocalDate) -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            day.format(KoreanMonthDay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        specials.forEach { SpecialDayCard(it) }
        if (events.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CharacterPair(couple.myCharacter(myUid), couple.partnerCharacter(myUid), size = 48.dp)
                    Text(
                        "등록된 일정이 없어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onAdd(day) }) { Text("이 날에 일정 추가") }
                }
            }
        } else {
            events.forEach { EventCard(it, couple, myUid, onEdit) }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, couple: Couple, myUid: String, onEdit: (CalendarEvent) -> Unit) {
    val mine = event.createdBy == myUid
    val color = if (mine) CoupleColors.mine else CoupleColors.partner
    val who = if (mine) couple.myName(myUid) else (couple.names[event.createdBy] ?: "상대")
    val whenText = buildString {
        append(event.time?.format(KoreanTime) ?: "하루 종일")
        if (event.isMultiDay) append(" · ${event.date.format(KoreanMonthDay)} ~ ${event.endDate.format(KoreanMonthDay)}")
    }

    Card(
        onClick = { onEdit(event) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.memo.isNotBlank()) {
                    Text(
                        event.memo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 누가 등록했는지: 캐릭터 얼굴 + 이름
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CharacterAvatar(couple.characterOf(event.createdBy), 30.dp, contentDescription = "$who 등록")
                Text(
                    who,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** 선택한 날의 기념일 한 줄: 우리 기념일·생일은 분홍 바탕, 연인 기념일은 연한 바탕 */
@Composable
private fun SpecialDayCard(special: SpecialDay) {
    val personal = special.kind.personal
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (personal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(special.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    special.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (personal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (special.note.isNotBlank()) {
                    Text(
                        special.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

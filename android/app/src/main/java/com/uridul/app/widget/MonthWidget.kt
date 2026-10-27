package com.uridul.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.uridul.app.MainActivity
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.SpecialDay
import com.uridul.app.domain.SpecialDays
import com.uridul.app.domain.SpecialKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 한 달 달력 위젯 (기본 4×4).
 * 한 달 날짜가 모두 보이고, 각 날짜에 공유 일정(내 일정 분홍 / 상대 일정 파랑)과 기념일·생일·연인 기념일을 표시한다.
 * < > 로 달을 넘기고, 날짜를 누르면 앱의 그 날짜로 바로 간다.
 */
class MonthWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    private data class Loaded(
        val month: YearMonth,
        val refresh: Long,
        val snapshot: WidgetSnapshot?,
        val events: List<CalendarEvent>,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initialMonth = YearMonth.now().plusMonths((prefs[KeyOffset] ?: 0).toLong())
        val initialRefresh = prefs[KeyRefresh] ?: 0L
        val snapshot = WidgetStore.load(context)
        val initialEvents = Widgets.fetchMonthEvents(context, snapshot, initialMonth)
            ?: WidgetStore.loadMonthEvents(context, initialMonth)

        provideContent {
            val state = currentState<Preferences>()
            val offset = state[KeyOffset] ?: 0
            val refresh = state[KeyRefresh] ?: 0L
            val today = LocalDate.now()
            val month = YearMonth.from(today).plusMonths(offset.toLong())
            var loaded by remember { mutableStateOf(Loaded(initialMonth, initialRefresh, snapshot, initialEvents)) }

            // 달을 넘기거나 앱에서 일정이 바뀌면 다시 받아온다 (먼저 저장해 둔 것을 보여주고 서버 응답으로 교체)
            LaunchedEffect(month, refresh) {
                if (loaded.month == month && loaded.refresh == refresh) return@LaunchedEffect
                val snap = WidgetStore.load(context)
                loaded = Loaded(month, refresh, snap, WidgetStore.loadMonthEvents(context, month))
                Widgets.fetchMonthEvents(context, snap, month)?.let { loaded = Loaded(month, refresh, snap, it) }
            }

            val shown = if (loaded.month == month) loaded.events else emptyList()
            MonthContent(loaded.snapshot, month, offset, shown, today)
        }
    }

    companion object {
        /** 이번 달에서 몇 달 넘겼는지 (위젯마다 따로 저장) */
        val KeyOffset = intPreferencesKey("month_offset")
        /** 앱에서 일정이 바뀌었을 때 다시 받아오라는 신호 */
        val KeyRefresh = longPreferencesKey("month_refresh")
        val KeyDelta = ActionParameters.Key<Int>("delta")

        /** 놓여 있는 한 달 달력 위젯을 모두 새로 받아오게 한다 */
        suspend fun refreshAll(context: Context) {
            val ids = runCatching { GlanceAppWidgetManager(context).getGlanceIds(MonthWidget::class.java) }
                .getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                updateAppWidgetState(context, id) { it[KeyRefresh] = now }
                MonthWidget().update(context, id)
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightScheduler.schedule(context)
    }
}

/** < > 달 넘기기, 0 이면 이번 달로 돌아가기 */
class MonthShiftAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[MonthWidget.KeyDelta] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val next = if (delta == 0) 0 else (prefs[MonthWidget.KeyOffset] ?: 0) + delta
            prefs[MonthWidget.KeyOffset] = next.coerceIn(-24, 24)
        }
        MonthWidget().update(context, glanceId)
    }
}

private val HeaderHeight = 30.dp
private val WeekdayHeight = 16.dp
private val Pad = 8.dp

@Composable
private fun MonthContent(
    snapshot: WidgetSnapshot?,
    month: YearMonth,
    offset: Int,
    events: List<CalendarEvent>,
    today: LocalDate,
) {
    val size = LocalSize.current
    val grid = CalendarMath.monthGrid(month)
    // 이번 달 날짜가 하나도 없는 마지막 주는 빼서 칸을 크게 쓴다 (4~6주)
    val weeks = grid.chunked(7).filter { week -> week.any { YearMonth.from(it) == month } }
    val specials = if (snapshot == null) {
        emptyMap()
    } else {
        SpecialDays.between(snapshot.startDate, snapshot.countFromOne, snapshot.birthdays, grid.first(), grid.last())
    }

    val cellWidth: Dp = (size.width - Pad * 2) / 7
    val cellHeight: Dp = (size.height - Pad * 2 - HeaderHeight - WeekdayHeight) / weeks.size
    // 날짜 숫자(약 19dp) 아래에 들어가는 줄 수 (한 줄 약 12dp)
    val lines = ((cellHeight.value - 19f) / 12f).toInt().coerceIn(0, 4)
    val showTitles = cellWidth >= 34.dp && lines >= 1

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Surface)
            .cornerRadius(20.dp)
            .padding(Pad),
    ) {
        Header(snapshot, month, offset, today)
        Row(GlanceModifier.fillMaxWidth().height(WeekdayHeight), verticalAlignment = Alignment.CenterVertically) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, label ->
                Text(
                    label,
                    style = TextStyle(
                        color = when (i) {
                            0 -> WidgetColors.Sunday
                            6 -> WidgetColors.Saturday
                            else -> WidgetColors.SubText
                        },
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        weeks.forEach { week ->
            val layout = CalendarMath.weekLayout(week, events)
            val maxLanes = if (showTitles) lines else 3
            Box(GlanceModifier.fillMaxWidth().height(cellHeight)) {
            Row(GlanceModifier.fillMaxSize()) {
                week.forEachIndexed { col, day ->
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        isToday = day == today,
                        special = specials[day]?.firstOrNull(),
                        events = CalendarMath.singleDayEventsOn(events, day),
                        lanes = minOf(layout.lanesUsed[col], maxLanes),
                        hiddenBars = layout.bars.count { col in it.startCol..it.endCol && it.lane >= maxLanes },
                        myUid = snapshot?.myUid,
                        lines = lines,
                        showTitles = showTitles,
                        wide = cellWidth >= 46.dp,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    )
                }
            }
            BarLanes(layout, maxLanes, cellWidth, showTitles, snapshot?.myUid, month, week)
            }
        }
    }
}

@Composable
private fun Header(snapshot: WidgetSnapshot?, month: YearMonth, offset: Int, today: LocalDate) {
    val openMonth = actionStartActivity<MainActivity>(
        actionParametersOf(
            Widgets.KeyTab to "calendar",
            Widgets.KeyDate to (if (offset == 0) today else month.atDay(1)).toString(),
        ),
    )
    Row(GlanceModifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        RoundButton("‹", actionRunCallback<MonthShiftAction>(actionParametersOf(MonthWidget.KeyDelta to -1)))
        Text(
            "${month.year}년 ${month.monthValue}월",
            style = TextStyle(
                color = WidgetColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(openMonth),
        )
        if (offset != 0) {
            Box(
                GlanceModifier
                    .height(24.dp)
                    .cornerRadius(12.dp)
                    .background(WidgetColors.Background)
                    .padding(horizontal = 8.dp)
                    .clickable(actionRunCallback<MonthShiftAction>(actionParametersOf(MonthWidget.KeyDelta to 0))),
                contentAlignment = Alignment.Center,
            ) {
                Text("오늘", style = TextStyle(color = WidgetColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.width(4.dp))
        }
        RoundButton("›", actionRunCallback<MonthShiftAction>(actionParametersOf(MonthWidget.KeyDelta to 1)))
        if (snapshot != null) {
            Spacer(GlanceModifier.width(4.dp))
            RoundButton(
                "+",
                actionStartActivity<MainActivity>(
                    actionParametersOf(
                        Widgets.KeyTab to "calendar",
                        Widgets.KeyAdd to true,
                        Widgets.KeyDate to (if (offset == 0) today else month.atDay(1)).toString(),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun RoundButton(text: String, action: androidx.glance.action.Action) {
    Box(
        GlanceModifier
            .size(28.dp)
            .cornerRadius(14.dp)
            .background(WidgetColors.Background)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = WidgetColors.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    special: SpecialDay?,
    events: List<CalendarEvent>,
    lanes: Int,
    hiddenBars: Int,
    myUid: String?,
    lines: Int,
    showTitles: Boolean,
    wide: Boolean,
    modifier: GlanceModifier,
) {
    val open = actionStartActivity<MainActivity>(
        actionParametersOf(Widgets.KeyTab to "calendar", Widgets.KeyDate to day.toString()),
    )
    val dayColor = when {
        isToday -> WidgetColors.OnPrimary
        !inMonth -> WidgetColors.Faded
        day.dayOfWeek == DayOfWeek.SUNDAY -> WidgetColors.Sunday
        day.dayOfWeek == DayOfWeek.SATURDAY -> WidgetColors.Saturday
        else -> WidgetColors.OnSurface
    }

    Column(
        modifier.padding(horizontal = 1.dp).clickable(open),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            GlanceModifier
                .size(19.dp)
                .then(if (isToday) GlanceModifier.cornerRadius(10.dp).background(WidgetColors.Primary) else GlanceModifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = TextStyle(
                    color = dayColor,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }

        // 위로 지나가는 여러 날 일정 막대 자리
        if (lanes > 0) Spacer(GlanceModifier.height(if (showTitles) BarPitch * lanes else ThinBarPitch * lanes + 2.dp))
        var used = if (showTitles) lanes else 0
        if (special != null && lines - used >= 1) {
            val personal = special.kind.personal
            Text(
                when {
                    !wide -> special.emoji
                    special.kind == SpecialKind.MILESTONE -> "♥${special.title}"
                    else -> "${special.emoji}${special.title}"
                },
                style = TextStyle(
                    color = if (!inMonth) WidgetColors.Faded else if (personal) WidgetColors.Primary else WidgetColors.SubText,
                    fontSize = 9.sp,
                    fontWeight = if (personal) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
            )
            used++
        }
        if (events.isEmpty() && hiddenBars == 0) return@Column

        val room = lines - used
        if (showTitles && room >= 1) {
            // 칸이 모자라면 마지막 줄을 "+N" 으로 (안 보이는 막대 포함)
            val shownEvents = if (hiddenBars == 0 && events.size <= room) events else events.take((room - 1).coerceAtLeast(0))
            shownEvents.forEach { e ->
                val mine = myUid != null && e.createdBy == myUid
                Text(
                    e.title,
                    style = TextStyle(
                        color = if (inMonth) WidgetColors.OnFill else WidgetColors.OnFillFaded,
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .cornerRadius(3.dp)
                        .background(if (mine) WidgetColors.MineSoft else WidgetColors.PartnerSoft)
                        .padding(horizontal = 2.dp),
                )
            }
            if (events.size - shownEvents.size + hiddenBars > 0) {
                Text(
                    "+${events.size - shownEvents.size + hiddenBars}",
                    style = TextStyle(color = WidgetColors.SubText, fontSize = 9.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }
        } else {
            // 좁은 칸: 누가 등록했는지 색 점만 (최대 3개)
            Row(GlanceModifier.padding(top = 2.dp)) {
                events.take(3).forEachIndexed { i, e ->
                    if (i > 0) Spacer(GlanceModifier.width(2.dp))
                    val mine = myUid != null && e.createdBy == myUid
                    Box(
                        GlanceModifier
                            .size(5.dp)
                            .cornerRadius(3.dp)
                            .background(if (mine) WidgetColors.Mine else WidgetColors.Partner),
                    ) {}
                }
            }
        }
    }
}

/** 막대 한 줄 간격 (글자 칸 일정 한 줄과 같은 높이) */
private val BarPitch = 12.dp
/** 좁은 칸의 얇은 선 간격 */
private val ThinBarPitch = 6.dp
/** 날짜 숫자 칸 높이 */
private val DayNumberHeight = 19.dp

/**
 * 여러 날 일정 막대: 날짜 칸 위에 줄(lane)마다 가로 막대를 겹쳐 그린다.
 * 위젯은 위치를 자유롭게 지정할 수 없어서, 줄마다 "빈칸 → 막대 → 빈칸" 을 칸 너비만큼 이어 붙인다.
 */
@Composable
private fun BarLanes(
    layout: CalendarMath.WeekLayout,
    maxLanes: Int,
    cellWidth: Dp,
    showTitles: Boolean,
    myUid: String?,
    month: YearMonth,
    week: List<LocalDate>,
) {
    val visible = layout.bars.filter { it.lane < maxLanes }
    if (visible.isEmpty()) return
    val laneCount = visible.maxOf { it.lane } + 1
    Column(GlanceModifier.fillMaxWidth().padding(top = DayNumberHeight + if (showTitles) 0.dp else 2.dp)) {
        for (lane in 0 until laneCount) {
            val pitch = if (showTitles) BarPitch else ThinBarPitch
            Row(GlanceModifier.fillMaxWidth().height(pitch)) {
                var col = 0
                visible.filter { it.lane == lane }.sortedBy { it.startCol }.forEach { bar ->
                    if (bar.startCol > col) Spacer(GlanceModifier.width(cellWidth * (bar.startCol - col)))
                    val startInset = if (bar.continuesLeft) 0.dp else 2.dp
                    val endInset = if (bar.continuesRight) 0.dp else 2.dp
                    val mine = myUid != null && bar.event.createdBy == myUid
                    val inMonth = YearMonth.from(week[bar.startCol]) == month
                    Row(GlanceModifier.width(cellWidth * bar.span).height(pitch)) {
                        if (startInset > 0.dp) Spacer(GlanceModifier.width(startInset))
                        if (showTitles) {
                            Text(
                                if (bar.continuesLeft) "… ${bar.event.title}" else bar.event.title,
                                style = TextStyle(
                                    color = if (inMonth) WidgetColors.OnFill else WidgetColors.OnFillFaded,
                                    fontSize = 9.sp,
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .height(pitch - 1.dp)
                                    .cornerRadius(3.dp)
                                    .background(if (mine) WidgetColors.MineBar else WidgetColors.PartnerBar)
                                    .padding(horizontal = 3.dp),
                            )
                        } else {
                            Box(
                                GlanceModifier
                                    .defaultWeight()
                                    .height(4.dp)
                                    .cornerRadius(2.dp)
                                    .background(if (mine) WidgetColors.Mine else WidgetColors.Partner),
                            ) {}
                        }
                        if (endInset > 0.dp) Spacer(GlanceModifier.width(endInset))
                    }
                    col = bar.endCol + 1
                }
            }
        }
    }
}

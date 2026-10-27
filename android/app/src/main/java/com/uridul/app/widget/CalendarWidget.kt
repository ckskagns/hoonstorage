package com.uridul.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.uridul.app.MainActivity
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.CalendarMath
import com.uridul.app.ui.KoreanTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 공유 캘린더 홈 화면 위젯: 두 사람이 등록한 다가오는 일정 목록.
 * 내 일정은 분홍, 상대 일정은 파랑 막대로 구분한다.
 */
class CalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.load(context)
        val events = Widgets.fetchUpcomingEvents(context, snapshot) ?: WidgetStore.loadEvents(context)
        val today = LocalDate.now()
        val upcoming = CalendarMath.upcoming(events, today, limit = 20)
        provideContent { CalendarContent(snapshot, upcoming, today) }
    }

    companion object {
        val SMALL = DpSize(120.dp, 120.dp)
        val WIDE = DpSize(250.dp, 120.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightScheduler.schedule(context)
    }
}

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN)

private fun dayLabel(event: CalendarEvent, today: LocalDate): String {
    val day = maxOf(event.date, today)
    return when (day) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> day.format(DayFormat)
    }
}

@Composable
private fun CalendarContent(snapshot: WidgetSnapshot?, events: List<CalendarEvent>, today: LocalDate) {
    val size = LocalSize.current
    val narrow = size.width < 200.dp
    val openCalendar = actionStartActivity<MainActivity>(actionParametersOf(Widgets.KeyTab to "calendar"))

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Surface)
            .cornerRadius(20.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // 머리글: 제목 + 일정 추가
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (narrow) "우리 일정" else "💕 우리 일정",
                style = TextStyle(color = WidgetColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(openCalendar),
            )
            if (snapshot != null) {
                Box(
                    GlanceModifier
                        .width(32.dp)
                        .height(28.dp)
                        .cornerRadius(14.dp)
                        .background(WidgetColors.Background)
                        .clickable(
                            actionStartActivity<MainActivity>(
                                actionParametersOf(Widgets.KeyTab to "calendar", Widgets.KeyAdd to true),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(color = WidgetColors.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))

        when {
            snapshot == null -> EmptyText("앱을 열어 연결해 주세요", openCalendar)
            events.isEmpty() -> EmptyText("다가오는 일정이 없어요\n+ 를 눌러 추가해 보세요", openCalendar)
            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                items(events, itemId = { it.id.hashCode().toLong() }) { event ->
                    EventRow(event, today, snapshot.myUid, narrow)
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String, onClick: Action) {
    Box(GlanceModifier.fillMaxSize().clickable(onClick), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = WidgetColors.SubText, fontSize = 12.sp))
    }
}

@Composable
private fun EventRow(event: CalendarEvent, today: LocalDate, myUid: String?, narrow: Boolean) {
    val mine = myUid != null && event.createdBy == myUid
    val day = maxOf(event.date, today)
    val open = actionStartActivity<MainActivity>(
        actionParametersOf(Widgets.KeyTab to "calendar", Widgets.KeyDate to day.toString()),
    )
    val timeText = event.time?.format(KoreanTime) ?: "종일"

    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(open)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 누가 등록했는지 색 막대
            Box(
                GlanceModifier
                    .width(4.dp)
                    .height(if (narrow) 30.dp else 22.dp)
                    .cornerRadius(2.dp)
                    .background(if (mine) WidgetColors.Mine else WidgetColors.Partner),
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            if (narrow) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        "${dayLabel(event, today)} · $timeText",
                        style = TextStyle(color = WidgetColors.SubText, fontSize = 11.sp),
                        maxLines = 1,
                    )
                    Text(
                        event.title,
                        style = TextStyle(color = WidgetColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    dayLabel(event, today),
                    style = TextStyle(color = WidgetColors.SubText, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.width(64.dp),
                )
                Text(
                    timeText,
                    style = TextStyle(color = WidgetColors.SubText, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.width(60.dp),
                )
                Text(
                    event.title,
                    style = TextStyle(color = WidgetColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

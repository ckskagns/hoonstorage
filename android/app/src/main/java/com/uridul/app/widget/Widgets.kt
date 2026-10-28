package com.uridul.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.updateAll
import androidx.compose.runtime.collectAsState
import androidx.glance.color.ColorProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventRepository
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import kotlinx.coroutines.withTimeoutOrNull
import com.uridul.app.domain.CalendarMath
import java.time.LocalDate
import java.time.YearMonth

/** 위젯 공통: 색상, 앱 열기 파라미터, 전체 갱신 */
object Widgets {
    /** 위젯을 눌렀을 때 열 탭 ("home" / "calendar") */
    val KeyTab = ActionParameters.Key<String>(MainExtras.TAB)
    /** 캘린더에서 바로 보여줄 날짜 (yyyy-MM-dd) */
    val KeyDate = ActionParameters.Key<String>(MainExtras.DATE)
    /** true 면 일정 추가 창을 바로 연다 */
    val KeyAdd = ActionParameters.Key<Boolean>(MainExtras.ADD_EVENT)

    suspend fun updateAll(context: Context) {
        DDayWidget().updateAll(context)
        HeartWidget().updateAll(context)
        CalendarWidget().updateAll(context)
        MonthWidget.refreshAll(context)
        PhotoWidget().updateAll(context)
    }

    /**
     * 앱을 열지 않아도 상대가 추가한 일정이 보이도록, 위젯이 갱신될 때 서버에서 한 번 받아온다.
     * 로그인 정보가 없거나 네트워크가 안 되면 null (저장해 둔 목록을 그대로 쓴다).
     */
    suspend fun fetchUpcomingEvents(context: Context, snapshot: WidgetSnapshot?): List<CalendarEvent>? {
        val coupleId = snapshot?.coupleId ?: return null
        if (FirebaseApp.getApps(context).isEmpty()) return null
        if (FirebaseAuth.getInstance().currentUser == null) return null
        val events = withTimeoutOrNull(6_000) {
            runCatching {
                val repo = EventRepository(FirebaseFirestore.getInstance())
                val today = LocalDate.now()
                // 반복 일정은 앞으로 두 달 동안 날짜별로 펼쳐서 함께
                val merged = com.uridul.app.domain.Recurrence.merge(
                    repo.fetchUpcoming(coupleId, today), repo.fetchRecurring(coupleId), today, today.plusDays(60),
                )
                CalendarMath.upcoming(merged, today, 40)
            }.getOrNull()
        } ?: return null
        WidgetStore.saveEvents(context, events)
        return events
    }

    /**
     * 한 달 달력 위젯: 그 달(앞뒤 달 포함) 일정을 서버에서 받아와 저장한다. 실패하면 null.
     */
    suspend fun fetchMonthEvents(context: Context, snapshot: WidgetSnapshot?, month: YearMonth): List<CalendarEvent>? {
        val coupleId = snapshot?.coupleId ?: return null
        if (FirebaseApp.getApps(context).isEmpty()) return null
        if (FirebaseAuth.getInstance().currentUser == null) return null
        val events = withTimeoutOrNull(6_000) {
            runCatching {
                val repo = EventRepository(FirebaseFirestore.getInstance())
                val range = CalendarMath.queryRange(month)
                com.uridul.app.domain.Recurrence.merge(
                    repo.fetchRange(coupleId, range), repo.fetchRecurring(coupleId), range.start, range.endInclusive,
                )
            }.getOrNull()
        } ?: return null
        WidgetStore.saveMonthEvents(context, month, events)
        return events
    }
}

/** MainActivity 가 위젯에서 받는 intent extra 이름 */
object MainExtras {
    const val TAB = "open_tab"
    const val DATE = "open_date"
    const val ADD_EVENT = "add_event"
    /** 지도 앱에서 장소를 공유받아 돌아온 경우 */
    const val PLACE_SHARED = "place_shared"
}

/**
 * 위젯 색: 앱 메뉴의 "화면 모드"와 "일정 색"을 그대로 따른다.
 *  - 폰 설정 따라가기: 폰이 다크면 다크 색, 아니면 화이트 색 (홈 화면이 알아서 고른다)
 *  - 화이트 모드 / 다크 모드: 폰 설정과 상관없이 그 색으로 고정
 */
internal object WidgetColors {
    private val ui get() = com.uridul.app.data.AppSettings.state.value

    /** 화면 모드에 맞는 색 (화이트 색, 다크 색) */
    private fun themed(day: Color, night: Color): androidx.glance.unit.ColorProvider = when (ui.theme) {
        com.uridul.app.data.ThemeMode.SYSTEM -> ColorProvider(day = day, night = night)
        com.uridul.app.data.ThemeMode.LIGHT -> androidx.glance.unit.ColorProvider(day)
        com.uridul.app.data.ThemeMode.DARK -> androidx.glance.unit.ColorProvider(night)
    }

    /** 어느 모드에서나 같은 색 */
    private fun fixed(c: Color): androidx.glance.unit.ColorProvider = androidx.glance.unit.ColorProvider(c)

    val Background get() = themed(Color(0xFFFFD9E2), Color(0xFF4A1F30))
    val Surface get() = themed(Color(0xFFFFF8F8), Color(0xFF261D20))
    val Primary get() = themed(Color(0xFFC03A62), Color(0xFFFFB1C5))
    val OnPrimary get() = themed(Color(0xFFFFFFFF), Color(0xFF5E1133))
    val OnBg get() = themed(Color(0xFF3E001D), Color(0xFFFFD9E2))
    val OnSurface get() = themed(Color(0xFF22191C), Color(0xFFF0DEE2))
    val SubText get() = themed(Color(0xFF74565F), Color(0xFFE3BDC6))
    val Sunday get() = themed(Color(0xFFD32F2F), Color(0xFFFFB4AB))
    val Saturday get() = themed(Color(0xFF1E62C8), Color(0xFFA8C8FF))
    val Faded get() = themed(Color(0x6622191C), Color(0x66F0DEE2))

    // 일정 색: 앱 메뉴 "일정 색" 에서 고른 색 (기본 내 일정 연핑크 / 상대 일정 하늘) — 달력 막대와 똑같이
    val Mine get() = fixed(Color(ui.mine.strong))
    val Partner get() = fixed(Color(ui.partner.strong))
    val MineSoft get() = fixed(Color(ui.mine.fill))
    val PartnerSoft get() = fixed(Color(ui.partner.fill))
    val MineBar get() = MineSoft
    val PartnerBar get() = PartnerSoft
    /** 파스텔 막대 위 글자 (화이트·다크 모두 진한 색) */
    val OnFill get() = fixed(Color(com.uridul.app.data.ON_EVENT_FILL))
    /** 다른 달 날짜의 막대 글자 (반투명 진한 색) */
    val OnFillFaded get() = fixed(Color(0x993A2A30))
}

/**
 * 위젯 내용 감싸기: 앱에서 화면 모드·일정 색을 바꾸면 떠 있는 위젯도 처음부터 다시 그린다.
 * (색은 설정에서 바로 읽으므로, 설정이 바뀔 때 화면 전체를 새로 그려야 바뀐 색이 보인다)
 */
@androidx.compose.runtime.Composable
internal fun WidgetSettingsScope(content: @androidx.compose.runtime.Composable () -> Unit) {
    val ui = com.uridul.app.data.AppSettings.state.collectAsState().value
    androidx.compose.runtime.key(ui) { content() }
}

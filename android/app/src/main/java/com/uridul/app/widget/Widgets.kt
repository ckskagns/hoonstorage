package com.uridul.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventRepository
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

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
                EventRepository(FirebaseFirestore.getInstance()).fetchUpcoming(coupleId, LocalDate.now())
            }.getOrNull()
        } ?: return null
        WidgetStore.saveEvents(context, events)
        return events
    }
}

/** MainActivity 가 위젯에서 받는 intent extra 이름 */
object MainExtras {
    const val TAB = "open_tab"
    const val DATE = "open_date"
    const val ADD_EVENT = "add_event"
}

internal object WidgetColors {
    val Background = ColorProvider(day = Color(0xFFFFD9E2), night = Color(0xFF4A1F30))
    val Surface = ColorProvider(day = Color(0xFFFFF8F8), night = Color(0xFF261D20))
    val Primary = ColorProvider(day = Color(0xFFC03A62), night = Color(0xFFFFB1C5))
    val OnPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF5E1133))
    val OnBg = ColorProvider(day = Color(0xFF3E001D), night = Color(0xFFFFD9E2))
    val OnSurface = ColorProvider(day = Color(0xFF22191C), night = Color(0xFFF0DEE2))
    val SubText = ColorProvider(day = Color(0xFF74565F), night = Color(0xFFE3BDC6))
    val Mine = ColorProvider(day = Color(0xFFD14D72), night = Color(0xFFFFB1C5))
    val Partner = ColorProvider(day = Color(0xFF4F6FC4), night = Color(0xFFB0C6FF))
}

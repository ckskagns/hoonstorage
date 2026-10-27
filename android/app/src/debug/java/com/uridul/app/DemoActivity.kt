package com.uridul.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.uridul.app.data.CalendarEvent
import androidx.lifecycle.lifecycleScope
import com.uridul.app.data.Couple
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.widget.Widgets
import com.uridul.app.notify.Reminders
import com.uridul.app.data.birthdays
import kotlinx.coroutines.launch
import com.uridul.app.domain.CalendarMath
import com.uridul.app.ui.ReadyScreen
import com.uridul.app.ui.theme.UridulTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 개발용 화면 점검 (디버그 빌드에만 포함).
 * 로그인·서버 없이 가짜 데이터로 메인 화면을 띄워 폴드/울트라 화면 배치를 확인한다.
 *   adb shell am start -n com.uridul.app/.DemoActivity --es tab calendar
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 알림 점검: 저장된 실제 커플 정보로 아침 알림을 지금 바로 보낸다
        //   adb shell am start -n com.uridul.app/.DemoActivity --ez reminderNow true
        if (intent.getBooleanExtra("reminderNow", false)) {
            lifecycleScope.launch {
                Reminders.runDaily(applicationContext, force = true)
                finish()
            }
            return
        }
        val startTab = intent.getStringExtra("tab") ?: "home"
        val today = LocalDate.now()
        val me = "me"
        val partner = "partner"
        val couple = Couple(
            id = "demo",
            members = listOf(me, partner),
            names = mapOf(me to "후이", partner to "나나"),
            characters = mapOf(me to "dragon", partner to "bunny"),
            startDate = LocalDate.of(2026, 7, 17),
            countFromOne = true,
        )
        fun ev(id: String, offset: Long, title: String, time: String?, by: String, days: Long = 0, memo: String = "") =
            CalendarEvent(id, title, today.plusDays(offset), today.plusDays(offset + days), time?.let(LocalTime::parse), memo, by)
        val seed = listOf(
            ev("1", 0, "저녁 데이트", "19:00", me, memo = "성수동 파스타집 예약함"),
            ev("2", 0, "토끼 회식", "18:30", partner),
            ev("3", 1, "영화 보기 (아주 긴 제목이 들어가도 잘리는지 확인하는 일정)", "20:10", partner),
            ev("4", 3, "제주도 여행", null, me, days = 2),
            ev("5", 3, "비행기", "09:05", partner),
            ev("6", 3, "렌터카", "11:00", me),
            ev("7", 9, "엄마 생신", null, partner),
            ev("8", -4, "카페 투어", "14:00", me),
        )

        // 에뮬레이터에서 위젯 모양을 점검할 때만: --ez seedWidgets true
        if (intent.getBooleanExtra("seedWidgets", false)) {
            lifecycleScope.launch {
                WidgetStore.save(
                    this@DemoActivity,
                    WidgetSnapshot(
                        couple.startDate, true, "후이", "나나", coupleId = null, myUid = me,
                        myCharacter = "dragon", partnerCharacter = "bunny",
                        birthdays = couple.birthdays(),
                    ),
                )
                WidgetStore.saveEvents(this@DemoActivity, seed)
                Widgets.updateAll(this@DemoActivity)
            }
        }

        setContent {
            UridulTheme {
                val events = remember { mutableStateListOf<CalendarEvent>().apply { addAll(seed) } }
                var month by remember { mutableStateOf(YearMonth.now()) }
                var request by remember { mutableStateOf<OpenRequest?>(OpenRequest(tab = startTab)) }
                val snackbar = remember { SnackbarHostState() }
                ReadyScreen(
                    couple = couple,
                    myUid = me,
                    busy = false,
                    snackbar = snackbar,
                    month = month,
                    monthEvents = events.toList(),
                    openRequest = request,
                    onOpenRequestHandled = { request = null },
                    onMonthChange = { month = it },
                    onSaveEvent = { draft ->
                        if (CalendarMath.validate(draft) != null) {
                            false
                        } else {
                            val id = draft.id ?: "new${events.size + 1}"
                            events.removeAll { it.id == id }
                            events += CalendarEvent(id, draft.title.trim(), draft.date, draft.endDate, draft.time, draft.memo, me)
                            true
                        }
                    },
                    onDeleteEvent = { id -> events.removeAll { it.id == id } },
                    onInvite = {},
                    onChangeStartDate = {},
                    onSetCountFromOne = {},
                    onUpdateProfile = { _, _, _ -> },
                    onSignOut = {},
                )
            }
        }
    }
}

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
import com.uridul.app.data.toSeriesDates
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
        // 백업 점검: 실제 서버 데이터로 백업 파일을 앱 전용 폴더에 저장 → 다시 읽어 불러오기 요약 계산 (서버에 쓰지 않음)
        //   adb shell am start -n com.uridul.app/.DemoActivity --ez backupProbe true
        if (intent.getBooleanExtra("backupProbe", false)) {
            lifecycleScope.launch {
                val log = StringBuilder()
                runCatching {
                    val snap = WidgetStore.load(applicationContext) ?: error("연결 정보 없음")
                    val coupleId = snap.coupleId ?: error("커플 ID 없음")
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val events = com.uridul.app.data.EventRepository(db).fetchAll(coupleId)
                    val wishes = com.uridul.app.data.WishRepository(db).fetchAll(coupleId)
                    val text = com.uridul.app.data.Backup.encode(events, wishes, snap.startDate, snap.countFromOne, emptyMap(), java.time.LocalDateTime.now().withNano(0).toString())
                    val file = java.io.File(getExternalFilesDir("backup"), "probe.json")
                    file.writeText(text)
                    val back = com.uridul.app.data.Backup.decode(file.readText())
                    val preview = com.uridul.app.data.Backup.preview(back, events.map { it.id }.toSet(), wishes.map { it.id }.toSet())
                    log.append("events=${events.size} wishes=${wishes.size} bytes=${file.length()} decodedEvents=${back.events.size} decodedWishes=${back.wishes.size} new=${preview.newEvents.size}/${preview.newWishes.size} recurring=${events.count { it.repeat != null }}")
                }.onFailure { log.append("FAIL ${it.message}") }
                android.util.Log.i("UridulProbe", log.toString())
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
            ev("4", 3, "제주도 여행", null, me, days = 2, memo = "jeju 렌터카 예약"),
            ev("5", 3, "비행기", "09:05", partner),
            ev("6", 3, "렌터카", "11:00", me),
            ev("7", 9, "엄마 생신", null, partner),
            ev("8", -4, "카페 투어", "14:00", me),
            // 반복 일정: 매주 같은 요일 운동 (열흘 전부터)
            ev("9", -10, "같이 운동", "07:30", partner).copy(repeat = com.uridul.app.data.Repeat.WEEKLY),
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
                var undo by remember { mutableStateOf<com.uridul.app.data.UndoItem?>(null) }
                val deleted = remember { mutableStateListOf<com.uridul.app.data.DeletedEvent>() }
                val wishes = remember {
                    mutableStateListOf(
                        com.uridul.app.data.WishPlace("w1", com.uridul.app.data.Place("선릉감자탕", null, com.uridul.app.data.MapApp.NAVER), "웨이팅 길대요", me, false),
                        com.uridul.app.data.WishPlace("w2", com.uridul.app.data.Place("성수 카페거리", null, com.uridul.app.data.MapApp.KAKAO), "", partner, false),
                        com.uridul.app.data.WishPlace("w3", com.uridul.app.data.Place("남산타워", null, com.uridul.app.data.MapApp.GOOGLE), "", me, true),
                    )
                }
                fun restore(e: CalendarEvent) {
                    events.removeAll { it.id == e.id }
                    events += e
                    deleted.removeAll { it.event.id == e.id }
                    undo = null
                }
                fun setSkip(id: String, day: LocalDate, skipped: Boolean) {
                    val i = events.indexOfFirst { it.id == id }
                    if (i >= 0) events[i] = events[i].copy(skip = if (skipped) events[i].skip + day else events[i].skip - day)
                }
                val range = CalendarMath.queryRange(month)
                ReadyScreen(
                    undo = undo,
                    onUndo = {
                        undo?.let { u -> if (u.skippedDay != null) setSkip(u.event.id, u.skippedDay!!, false) else restore(u.event) }
                        undo = null
                    },
                    onDismissUndo = { undo = null },
                    deletedEvents = deleted.toList(),
                    onRestoreDeleted = ::restore,
                    onLoadAllEvents = { events.toList() },
                    onSkipOccurrence = { id, day ->
                        events.firstOrNull { it.id == id }?.let { undo = com.uridul.app.data.UndoItem(it, day) }
                        setSkip(id, day, true)
                    },
                    wishes = wishes.toList(),
                    onAddWish = { p -> wishes.add(0, com.uridul.app.data.WishPlace("w${wishes.size + 10}", p, "", me, false)) },
                    onWishVisited = { id, v -> wishes.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { wishes[it] = wishes[it].copy(visited = v) } },
                    onWishMemo = { id, m -> wishes.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { wishes[it] = wishes[it].copy(memo = m) } },
                    onDeleteWish = { id -> wishes.removeAll { it.id == id } },
                    couple = couple,
                    myUid = me,
                    busy = false,
                    snackbar = snackbar,
                    month = month,
                    monthEvents = com.uridul.app.domain.Recurrence.merge(events.toList(), events.toList(), range.start, range.endInclusive),
                    openRequest = request,
                    onOpenRequestHandled = { request = null },
                    onMonthChange = { month = it },
                    onSaveEvent = { input ->
                        val draft = input.toSeriesDates()
                        if (CalendarMath.validate(draft) != null) {
                            false
                        } else {
                            val id = draft.id ?: "new${events.size + 1}"
                            val old = events.firstOrNull { it.id == id }
                            events.removeAll { it.id == id }
                            events += CalendarEvent(
                                id, draft.title.trim(), draft.date, draft.endDate, draft.time, draft.memo, old?.createdBy ?: me,
                                place = draft.place, timeline = draft.timeline, remind = draft.remind,
                                repeat = draft.repeat, skip = old?.skip.orEmpty(),
                            )
                            true
                        }
                    },
                    onDeleteEvent = { id ->
                        events.firstOrNull { it.id == id }?.let { e ->
                            undo = com.uridul.app.data.UndoItem(e)
                            deleted.add(0, com.uridul.app.data.DeletedEvent(e, System.currentTimeMillis()))
                        }
                        events.removeAll { it.id == id }
                    },
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

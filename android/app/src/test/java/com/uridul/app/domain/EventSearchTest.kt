package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.DeletedEvent
import com.uridul.app.data.DeletedEvents
import com.uridul.app.data.Place
import com.uridul.app.data.Remind
import com.uridul.app.data.TimeSlot
import com.uridul.app.notify.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EventSearchTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun event(
        id: String,
        title: String,
        date: LocalDate,
        time: LocalTime? = LocalTime.of(18, 30),
        memo: String = "",
        timeline: List<TimeSlot> = emptyList(),
        remind: Int? = null,
    ) = CalendarEvent(id, title, date, date, time, memo, "u1", timeline = timeline, remind = remind)

    private val dinner = event(
        "a", "저녁 데이트", LocalDate.of(2026, 10, 2),
        timeline = listOf(TimeSlot(LocalTime.of(18, 30), LocalTime.of(20, 0), "저녁식사", Place("선릉감자탕", null, null))),
    )
    private val movie = event("b", "영화", LocalDate.of(2026, 11, 3), memo = "팝콘 큰 거")
    private val trip = event("c", "제주 여행", LocalDate.of(2026, 9, 1), time = null)

    @Test
    fun findsByTitlePlaceSlotAndMemo() {
        assertTrue(EventSearch.matches(dinner, "데이트"))
        assertTrue(EventSearch.matches(dinner, "감자탕"))
        assertTrue(EventSearch.matches(dinner, "선릉 감자탕")) // 띄어쓰기 무시
        assertTrue(EventSearch.matches(dinner, "저녁식사"))
        assertTrue(EventSearch.matches(movie, "팝콘"))
        assertFalse(EventSearch.matches(movie, "감자탕"))
        assertFalse(EventSearch.matches(movie, "  "))
    }

    @Test
    fun upcomingFirstThenRecentPast() {
        val r = EventSearch.search(listOf(trip, movie, dinner), "", today)
        assertTrue(r.isEmpty)
        val all = listOf(trip, movie, dinner, event("d", "영화 또", LocalDate.of(2026, 10, 30)))
        val movies = EventSearch.search(all, "영화", today)
        assertEquals(listOf("b", "d").sorted(), movies.upcoming.map { it.id }.sorted())
        assertEquals("d", movies.upcoming.first().id) // 가까운 날 먼저
        // 지난 일정은 최근 것부터 (10/2 저녁 데이트 → 9/1 제주 여행)
        val past = EventSearch.search(listOf(trip, dinner, event("e", "여행 계획 데이트", LocalDate.of(2026, 9, 1))), "데이트", today)
        assertEquals(listOf("a", "e"), past.past.map { it.id })
        assertTrue(past.upcoming.isEmpty())
    }

    @Test
    fun remindDefaultsAndOptions() {
        assertEquals(30, Remind.effective(null, allDay = false))
        assertEquals(0, Remind.effective(null, allDay = true))
        assertEquals(60, Remind.effective(60, allDay = false))
        // 하루 종일에는 없는 값이면 기본값 (당일 아침)
        assertEquals(0, Remind.effective(60, allDay = true))
        assertEquals(Remind.DAY_BEFORE, Remind.effective(Remind.DAY_BEFORE, allDay = true))
        assertEquals(30, Remind.effective(999, allDay = false))
        assertEquals("30분 뒤 일정이 있어요", Remind.leadText(30))
        assertEquals("1시간 뒤 일정이 있어요", Remind.leadText(60))
        assertEquals("내일 이 시간에 일정이 있어요", Remind.leadText(Remind.DAY_BEFORE))
    }

    @Test
    fun alarmsUseEachEventsChoice() {
        val now = LocalDateTime.of(2026, 10, 28, 9, 0)
        val events = listOf(
            event("t1", "점심", today, LocalTime.of(12, 0)), // 기본 30분 전 → 11:30
            event("t2", "운동", today, LocalTime.of(19, 0), remind = 60), // 18:00
            event("t3", "꺼둠", today, LocalTime.of(20, 0), remind = Remind.OFF),
            event("t4", "내일 아침", today.plusDays(1), LocalTime.of(8, 0), remind = Remind.DAY_BEFORE), // 오늘 08:00 → 이미 지남
            event("t5", "모레 저녁", today.plusDays(2), LocalTime.of(19, 0), remind = Remind.DAY_BEFORE), // 내일 19:00
            event("t6", "하루 종일", today, null),
            event("t7", "멀리", today.plusDays(5), LocalTime.of(19, 0)),
        )
        val alarms = Reminders.alarmsFor(events, now).associate { it.event.id to it.at }
        assertEquals(LocalDateTime.of(2026, 10, 28, 11, 30), alarms["t1"])
        assertEquals(LocalDateTime.of(2026, 10, 28, 18, 0), alarms["t2"])
        assertEquals(LocalDateTime.of(2026, 10, 29, 19, 0), alarms["t5"])
        assertEquals(setOf("t1", "t2", "t5"), alarms.keys)
    }

    @Test
    fun deletedListKeeps30DaysNewestFirst() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val list = listOf(
            DeletedEvent(movie, now - 31 * day),
            DeletedEvent(dinner, now - 2 * day),
            DeletedEvent(trip, now - 1 * day),
        )
        assertEquals(listOf("c", "a"), DeletedEvents.prune(list, now).map { it.event.id })
    }
}

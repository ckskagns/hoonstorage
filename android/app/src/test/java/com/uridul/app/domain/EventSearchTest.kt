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
    fun remindIsOnDayOrOff() {
        // 받기(당일 아침) 아니면 끄기. 예전 버전 값(30분 전·하루 전)도 받기로 본다
        assertEquals(Remind.ON_DAY, Remind.effective(null, allDay = false))
        assertEquals(Remind.ON_DAY, Remind.effective(null, allDay = true))
        assertEquals(Remind.ON_DAY, Remind.effective(30, allDay = false))
        assertEquals(Remind.ON_DAY, Remind.effective(Remind.DAY_BEFORE, allDay = true))
        assertEquals(Remind.OFF, Remind.effective(Remind.OFF, allDay = false))
        assertEquals("당일 아침 9시", Remind.label(60, allDay = false))
        assertEquals("알림 끄기", Remind.label(Remind.OFF, allDay = true))
        assertTrue(30 in Remind.allowed) // 서버에 이미 있는 예전 값도 읽는다
    }

    @Test
    fun dailyTitleJoinsAndCuts() {
        assertEquals("영화", Reminders.joinTitles(listOf("영화")))
        assertEquals("영화, 저녁 약속", Reminders.joinTitles(listOf("영화", "저녁 약속")))
        // 길면 잘라서 "…"
        val long = Reminders.joinTitles(listOf("가나다라마바사", "아자차카타파하", "가나다라마바사아자"), max = 20)
        assertEquals("가나다라마바사, 아자차카타파하, 가…", long)
        assertTrue(long.length <= 20)
        // 쉼표·빈칸 바로 뒤에서 잘리면 지운다
        assertEquals("가나다라…", Reminders.joinTitles(listOf("가나다라", "마바사아"), max = 7))
        // 이모지 가운데서 자르지 않는다
        assertEquals("💕…", Reminders.joinTitles(listOf("💕💕💕"), max = 4))
        // 기념일 먼저, 빈 제목·같은 제목은 한 번만
        assertEquals(listOf("🎂 나나 생일", "영화"), Reminders.dailyTitles(listOf("🎂 나나 생일"), listOf("영화", " ", "영화")))
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

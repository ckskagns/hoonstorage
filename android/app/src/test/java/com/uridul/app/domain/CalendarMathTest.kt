package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalendarMathTest {

    private fun event(
        id: String,
        date: String,
        end: String = date,
        time: String? = null,
        title: String = id,
    ) = CalendarEvent(id, title, LocalDate.parse(date), LocalDate.parse(end), time?.let(LocalTime::parse), "", "u1")

    @Test
    fun monthGridStartsOnSundayAndHas42Days() {
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 10))
        assertEquals(42, grid.size)
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
        // 2026-10-01 은 목요일 → 첫 칸은 9월 27일(일)
        assertEquals(LocalDate.of(2026, 9, 27), grid.first())
        assertTrue(LocalDate.of(2026, 10, 31) in grid)
    }

    @Test
    fun monthStartingOnSundayStartsThatDay() {
        // 2026-02-01 은 일요일
        assertEquals(LocalDate.of(2026, 2, 1), CalendarMath.monthGrid(YearMonth.of(2026, 2)).first())
    }

    @Test
    fun queryRangeCoversVisibleGridAndLongEvents() {
        val month = YearMonth.of(2026, 10)
        val range = CalendarMath.queryRange(month)
        val grid = CalendarMath.monthGrid(month)
        assertTrue(range.start <= grid.first().minusDays(CalendarEvent.MAX_SPAN_DAYS))
        assertTrue(range.endInclusive >= grid.last())
    }

    @Test
    fun eventsOnIncludesMultiDayAndSortsAllDayFirst() {
        val events = listOf(
            event("dinner", "2026-10-28", time = "19:00"),
            event("lunch", "2026-10-28", time = "12:00"),
            event("trip", "2026-10-27", end = "2026-10-29"),
            event("other", "2026-10-30"),
        )
        val ids = CalendarMath.eventsOn(events, LocalDate.of(2026, 10, 28)).map { it.id }
        assertEquals(listOf("trip", "lunch", "dinner"), ids)
    }

    @Test
    fun upcomingKeepsOngoingEventsAndDropsPastOnes() {
        val today = LocalDate.of(2026, 10, 28)
        val events = listOf(
            event("past", "2026-10-20"),
            event("ongoing", "2026-10-26", end = "2026-10-29"),
            event("tomorrow", "2026-10-29"),
            event("today-evening", "2026-10-28", time = "20:00"),
        )
        val ids = CalendarMath.upcoming(events, today, limit = 10).map { it.id }
        assertEquals(listOf("ongoing", "today-evening", "tomorrow"), ids)
    }

    @Test
    fun validateRejectsBadInput() {
        val day = LocalDate.of(2026, 10, 28)
        fun draft(title: String = "데이트", end: LocalDate = day, memo: String = "") =
            EventDraft(title = title, date = day, endDate = end, time = null, memo = memo)

        assertNull(CalendarMath.validate(draft()))
        assertNotNull(CalendarMath.validate(draft(title = "   ")))
        assertNotNull(CalendarMath.validate(draft(title = "가".repeat(CalendarEvent.TITLE_MAX + 1))))
        assertNotNull(CalendarMath.validate(draft(memo = "가".repeat(CalendarEvent.MEMO_MAX + 1))))
        assertNotNull(CalendarMath.validate(draft(end = day.minusDays(1))))
        assertNull(CalendarMath.validate(draft(end = day.plusDays(CalendarEvent.MAX_SPAN_DAYS - 1))))
        assertNotNull(CalendarMath.validate(draft(end = day.plusDays(CalendarEvent.MAX_SPAN_DAYS))))
    }
}

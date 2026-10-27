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

    @Test
    fun weekLayoutStacksOverlappingMultiDayEvents() {
        // 2026-10-25(일) ~ 10-31(토)
        val week = CalendarMath.monthGrid(java.time.YearMonth.of(2026, 10)).chunked(7)[4]
        assertEquals(LocalDate.of(2026, 10, 25), week.first())
        val events = listOf(
            event("trip", "2026-10-27", end = "2026-10-29"),
            event("long", "2026-10-20", end = "2026-10-26"), // 지난주부터 이어짐
            event("overlap", "2026-10-28", end = "2026-11-02"), // 다음 주로 이어짐
            event("single", "2026-10-27"),
        )
        val layout = CalendarMath.weekLayout(week, events)
        val byId = layout.bars.associateBy { it.event.id }
        assertEquals(3, layout.bars.size)
        assertEquals(0 to 1, byId.getValue("long").let { it.startCol to it.endCol })
        assertEquals(true, byId.getValue("long").continuesLeft)
        assertEquals(2 to 4, byId.getValue("trip").let { it.startCol to it.endCol })
        // long(0~1) 과 trip(2~4) 은 같은 줄, overlap(3~6) 은 trip 과 겹쳐 다음 줄
        assertEquals(0, byId.getValue("long").lane)
        assertEquals(0, byId.getValue("trip").lane)
        assertEquals(1, byId.getValue("overlap").lane)
        assertEquals(true, byId.getValue("overlap").continuesRight)
        assertEquals(listOf(1, 1, 1, 2, 2, 2, 2), layout.lanesUsed)
        assertEquals(listOf("single"), CalendarMath.singleDayEventsOn(events, LocalDate.of(2026, 10, 27)).map { it.id })
    }

    @Test
    fun validateTimeline() {
        val day = LocalDate.of(2026, 10, 27)
        fun slot(a: String, b: String?) = com.uridul.app.data.TimeSlot(LocalTime.parse(a), b?.let(LocalTime::parse))
        fun draft(end: LocalDate, vararg slots: com.uridul.app.data.TimeSlot) =
            EventDraft(title = "데이트", date = day, endDate = end, time = slots.firstOrNull()?.start, memo = "", timeline = slots.toList())

        assertNull(CalendarMath.validate(draft(day, slot("18:30", "20:00"), slot("20:30", "22:00"))))
        // 끝이 시작보다 빠르면 안 됨
        assertNotNull(CalendarMath.validate(draft(day, slot("18:30", "18:00"))))
        // 여러 날 일정은 시간대 하나만 (끝 시각은 마지막 날이라 시작보다 빨라도 됨)
        assertNull(CalendarMath.validate(draft(day.plusDays(2), slot("16:00", "09:00"))))
        assertNotNull(CalendarMath.validate(draft(day.plusDays(1), slot("16:00", "17:00"), slot("18:00", "19:00"))))
    }
}

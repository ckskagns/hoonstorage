package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import com.uridul.app.data.normalized
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

    /** 4박5일처럼 여러 날 일정은 날마다 타임라인 (시간대마다 며칠째) */
    @Test
    fun perDayTimeline() {
        val day = LocalDate.of(2026, 11, 15)
        fun slot(d: Int, a: String, b: String?) = com.uridul.app.data.TimeSlot(LocalTime.parse(a), b?.let(LocalTime::parse), "할 일$d", day = d)
        fun draft(end: LocalDate, vararg slots: com.uridul.app.data.TimeSlot) =
            EventDraft(title = "test0 여행", date = day, endDate = end, time = slots.firstOrNull()?.start, memo = "", timeline = slots.toList())
        val trip = day.plusDays(4)

        assertNull(CalendarMath.validate(draft(trip, slot(0, "10:00", "12:00"), slot(0, "13:00", "14:00"), slot(2, "09:00", "10:00"), slot(4, "15:00", null))))
        // 날짜별 시간대는 끝이 시작보다 늦어야 한다
        assertNotNull(CalendarMath.validate(draft(trip, slot(1, "16:00", "09:00"))))
        // 기간 밖의 날
        assertNotNull(CalendarMath.validate(draft(trip, slot(5, "10:00", "11:00"))))
        // 하루 12개, 모두 60개까지
        assertNotNull(CalendarMath.validate(draft(trip, *Array(13) { slot(0, "10:00", "11:00") })))
        assertNull(CalendarMath.validate(draft(trip, *Array(60) { slot(it % 5, "10:00", "11:00") })))
        assertNotNull(CalendarMath.validate(draft(day.plusDays(9), *Array(61) { slot(it % 10, "10:00", "11:00") })))
    }

    @Test
    fun normalizeTimeline() {
        val day = LocalDate.of(2026, 11, 15)
        fun s(a: String, b: String?, d: Int? = null) = com.uridul.app.data.TimeSlot(LocalTime.parse(a), b?.let(LocalTime::parse), day = d)
        fun draft(end: LocalDate, vararg slots: com.uridul.app.data.TimeSlot) =
            EventDraft(title = "t", date = day, endDate = end, time = slots.firstOrNull()?.start, memo = "", timeline = slots.toList())

        // 예전 여러 날 일정 한 줄(첫날 16시 ~ 마지막 날 9시) → 첫날 시간대, 끝은 비움
        val legacy = draft(day.plusDays(2), s("16:00", "09:00")).normalized()
        assertEquals(listOf(s("16:00", null, 0)), legacy.timeline)
        assertNull(CalendarMath.validate(legacy))
        // 날짜·시간 순, 기간 밖은 버림
        val trip = draft(day.plusDays(1), s("15:00", "16:00", 1), s("09:00", "10:00", 1), s("20:00", "21:00", 0), s("10:00", "11:00", 3)).normalized()
        assertEquals(listOf(0 to "20:00", 1 to "09:00", 1 to "15:00"), trip.timeline.map { it.dayIndex to it.start.toString() })
        assertEquals(LocalTime.of(20, 0), trip.time)
        // 하루 일정이 되면 첫날 것만, 며칠째 없이
        val single = draft(day, s("15:00", "16:00", 1), s("09:00", "10:00", 0)).normalized()
        assertEquals(listOf(s("09:00", "10:00")), single.timeline)
        // 하루 종일이면 타임라인 없음
        assertEquals(emptyList<Any>(), draft(day.plusDays(1)).copy(time = null, timeline = listOf(s("09:00", null, 0))).normalized().timeline)
    }

    @Test
    fun nextSlotAndDayHelpers() {
        val day = LocalDate.of(2026, 11, 23)
        fun s(a: String, b: String?, d: Int? = null) = com.uridul.app.data.TimeSlot(LocalTime.parse(a), b?.let(LocalTime::parse), day = d)
        // 하루 일정: 첫 시간대는 저녁 7시, 다음은 마지막 끝부터 1시간
        assertEquals(s("19:00", "20:00"), com.uridul.app.data.TimeSlot.next(emptyList(), null))
        assertEquals(s("20:30", "21:30"), com.uridul.app.data.TimeSlot.next(listOf(s("19:00", "20:30")), null))
        // 여러 날 일정: 그 날 첫 시간대는 오전 10시, 다른 날 시간대는 보지 않음
        assertEquals(s("10:00", "11:00", 2), com.uridul.app.data.TimeSlot.next(listOf(s("19:00", "20:00", 0)), 2))
        assertEquals(s("16:00", "17:00", 0), com.uridul.app.data.TimeSlot.next(listOf(s("15:00", "16:00", 0), s("09:00", "10:00", 1)), 0))
        // 끝이 없으면 시작 1시간 뒤부터, 자정을 넘으면 밤 11:59 까지
        assertEquals(s("21:00", "22:00"), com.uridul.app.data.TimeSlot.next(listOf(s("20:00", null)), null))
        assertEquals(s("23:30", "23:59"), com.uridul.app.data.TimeSlot.next(listOf(s("22:00", "23:30")), null))
        assertEquals(4, com.uridul.app.data.daysBetween(day, day.plusDays(4)))
        assertEquals("2일차 · 11월 24일 (화)", com.uridul.app.ui.tripDayLabel(day, 1))
    }
}

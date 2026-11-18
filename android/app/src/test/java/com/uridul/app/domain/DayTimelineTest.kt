package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayTimelineTest {
    private val day = LocalDate.of(2026, 10, 27)

    private fun t(s: String) = LocalTime.parse(s)

    private fun event(id: String, vararg slots: TimeSlot, end: LocalDate = day) =
        CalendarEvent(id, id, day, end, slots.firstOrNull()?.start, "", "u", timeline = slots.toList())

    @Test
    fun blocksAndAllDaySplit() {
        val events = listOf(
            event("dinner", TimeSlot(t("18:30"), t("20:00"), "저녁"), TimeSlot(t("20:30"), null, "카페")),
            event("trip", TimeSlot(t("09:00"), t("10:00")), end = day.plusDays(1)), // 여러 날 → 하루 종일 칸
            CalendarEvent("allday", "종일", day, day, null, "", "u"),
        )
        val blocks = DayTimeline.blocks(events, day)
        assertEquals(listOf("dinner#0", "dinner#1"), blocks.map { it.key })
        assertEquals(18 * 60 + 30, blocks[0].startMin)
        // 끝이 없으면 1시간
        assertEquals(21 * 60 + 30, blocks[1].endMin)
        assertEquals("dinner · 저녁", blocks[0].title)
        assertEquals(listOf("trip", "allday").sorted(), DayTimeline.allDayEvents(events, day).map { it.id }.sorted())
    }

    /** 날짜별로 짠 여러 날 일정은 그 날 시간대가 시간표에 (첫날 시간대는 둘째 날에 안 보임) */
    @Test
    fun perDayTripBlocks() {
        val trip = event(
            "trip",
            TimeSlot(t("10:00"), t("12:00"), "체크인", day = 0),
            TimeSlot(t("09:00"), t("10:00"), "아침", day = 1),
            TimeSlot(t("14:00"), t("15:00"), "카페", day = 1),
            end = day.plusDays(2),
        )
        assertEquals(listOf("trip#0"), DayTimeline.blocks(listOf(trip), day).map { it.key })
        assertEquals(listOf("trip#1", "trip#2"), DayTimeline.blocks(listOf(trip), day.plusDays(1)).map { it.key })
        assertEquals(emptyList<String>(), DayTimeline.blocks(listOf(trip), day.plusDays(2)).map { it.key })
        assertEquals("trip · 아침", DayTimeline.blocks(listOf(trip), day.plusDays(1))[0].title)
        // 여행 전체는 위쪽 하루 종일 칸에도 그대로
        assertEquals(listOf("trip"), DayTimeline.allDayEvents(listOf(trip), day.plusDays(1)).map { it.id })
        assertEquals(listOf(1, 2), trip.slotsOn(day.plusDays(1)).map { it.index })
    }

    @Test
    fun overlappingBlocksGoSideBySide() {
        val events = listOf(
            event("a", TimeSlot(t("10:00"), t("12:00"))),
            event("b", TimeSlot(t("11:00"), t("13:00"))),
            event("c", TimeSlot(t("12:00"), t("12:30"))),
            event("d", TimeSlot(t("15:00"), t("16:00"))),
        )
        val placed = DayTimeline.layout(DayTimeline.blocks(events, day)).associateBy { it.block.key }
        assertEquals(0, placed.getValue("a#0").column)
        assertEquals(1, placed.getValue("b#0").column)
        // c 는 a 가 끝난 12:00 에 시작 → a 의 열을 다시 쓴다
        assertEquals(0, placed.getValue("c#0").column)
        assertEquals(2, placed.getValue("a#0").columns)
        // d 는 따로 떨어져 있어서 혼자 한 열
        assertEquals(1, placed.getValue("d#0").columns)
    }
}

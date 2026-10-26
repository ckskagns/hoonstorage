package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DDayTest {

    private val start = LocalDate.of(2025, 1, 1)

    @Test
    fun `첫날은 한국식 1일, 0일 시작 옵션이면 0일`() {
        assertEquals(1, DDay.dayNumber(start, start, countFromOne = true))
        assertEquals(0, DDay.dayNumber(start, start, countFromOne = false))
    }

    @Test
    fun `한국식으로 4월 10일이 100일`() {
        val day100 = LocalDate.of(2025, 4, 10)
        assertEquals(100, DDay.dayNumber(start, day100, countFromOne = true))
        assertEquals("100일", DDay.heroText(start, day100, countFromOne = true))
        assertEquals("D+100", DDay.shortText(start, day100, countFromOne = true))
    }

    @Test
    fun `시작일이 미래면 D-마이너스로 표시`() {
        val today = LocalDate.of(2024, 12, 20)
        assertEquals(-12, DDay.dayNumber(start, today, countFromOne = true))
        assertEquals("D-12", DDay.heroText(start, today, countFromOne = true))
        assertEquals("D-12", DDay.shortText(start, today, countFromOne = true))
    }

    @Test
    fun `100일 당일에는 해당 기념일이 D-Day로 포함된다`() {
        val day100 = LocalDate.of(2025, 4, 10)
        val first = DDay.upcoming(start, day100, countFromOne = true).first()
        assertEquals("100일", first.label)
        assertEquals(day100, first.date)
        assertEquals(0, first.daysLeft)
        assertEquals("D-Day", DDay.daysLeftText(first.daysLeft))
    }

    @Test
    fun `0일 시작 옵션이면 100일이 하루 늦다`() {
        val first = DDay.upcoming(start, start, countFromOne = false).first()
        assertEquals("100일", first.label)
        assertEquals(LocalDate.of(2025, 4, 11), first.date)
    }

    @Test
    fun `100일 단위와 주년이 날짜순으로 섞인다`() {
        val today = LocalDate.of(2025, 12, 1) // 335일째
        val list = DDay.upcoming(start, today, countFromOne = true, limit = 4)
        assertEquals(listOf("1주년", "400일", "500일", "600일"), list.map { it.label })
        assertEquals(LocalDate.of(2026, 1, 1), list[0].date)
        assertEquals(LocalDate.of(2026, 2, 4), list[1].date)
        assertTrue(list.zipWithNext().all { (a, b) -> !a.date.isAfter(b.date) })
    }

    @Test
    fun `2월 29일 시작이면 평년 주년은 2월 28일`() {
        val leapStart = LocalDate.of(2024, 2, 29)
        val today = LocalDate.of(2025, 2, 1)
        val anniversary = DDay.upcoming(leapStart, today, countFromOne = true, limit = 5)
            .first { it.label == "1주년" }
        assertEquals(LocalDate.of(2025, 2, 28), anniversary.date)
    }

    @Test
    fun `지난 기념일은 포함하지 않는다`() {
        val today = LocalDate.of(2025, 4, 11)
        val list = DDay.upcoming(start, today, countFromOne = true, limit = 3)
        assertTrue(list.none { it.date.isBefore(today) })
        assertEquals("200일", list.first().label)
    }
}

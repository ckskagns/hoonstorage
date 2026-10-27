package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class SpecialDaysTest {
    private val start = LocalDate.of(2026, 7, 17)
    private val birthdays = listOf(
        Birthday("후이", MonthDay.of(5, 13)),
        Birthday("나나", MonthDay.of(10, 18)),
    )

    private fun on(day: LocalDate) = SpecialDays.on(start, true, birthdays, day)

    @Test
    fun birthdaysEveryYear() {
        assertEquals(listOf("나나 생일"), on(LocalDate.of(2026, 10, 18)).map { it.title })
        assertEquals(listOf("후이 생일"), on(LocalDate.of(2027, 5, 13)).map { it.title })
        assertEquals(SpecialKind.BIRTHDAY, on(LocalDate.of(2027, 5, 13)).single().kind)
    }

    @Test
    fun coupleDays() {
        assertEquals("발렌타인데이", on(LocalDate.of(2027, 2, 14)).single().title)
        assertEquals("화이트데이", on(LocalDate.of(2027, 3, 14)).single().title)
        assertEquals("빼빼로데이", on(LocalDate.of(2026, 11, 11)).single().title)
        assertEquals(
            listOf("크리스마스 이브", "크리스마스"),
            listOf(on(LocalDate.of(2026, 12, 24)), on(LocalDate.of(2026, 12, 25))).map { it.single().title },
        )
        assertEquals("칠석", on(LocalDate.of(2027, 8, 8)).single().title)
    }

    @Test
    fun personalComesBeforeCoupleDay() {
        // 2026-10-14 는 와인데이. 같은 날 생일이 있으면 생일이 먼저
        val list = SpecialDays.on(start, true, listOf(Birthday("나나", MonthDay.of(10, 14))), LocalDate.of(2026, 10, 14))
        assertEquals(listOf("나나 생일", "와인데이"), list.map { it.title })
    }

    @Test
    fun milestonesIncluded() {
        // 7/17 시작, 1일부터 세면 100일 = 10/24
        val day = on(LocalDate.of(2026, 10, 24)).single()
        assertEquals("100일", day.title)
        assertEquals(SpecialKind.MILESTONE, day.kind)
    }

    @Test
    fun upcomingKeepsPersonalDaysAndFewCoupleDays() {
        val today = LocalDate.of(2026, 10, 27)
        val list = SpecialDays.upcoming(start, true, birthdays, today, personalLimit = 4, coupleDayLimit = 2)
        assertEquals(4, list.count { it.kind.personal })
        assertEquals(listOf("빼빼로데이", "무비데이"), list.filter { !it.kind.personal }.map { it.title })
        assertTrue(list.zipWithNext().all { (a, b) -> !a.date.isAfter(b.date) })
        assertEquals("200일", list.first { it.kind.personal }.title)
    }
}

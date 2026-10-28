package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {

    private fun days(year: Int) = Holidays.ofYear(year).map { it.date.toString().substring(5) }
    private fun substitutes(year: Int) = Holidays.ofYear(year).filter { it.substitute }.map { it.date.toString() }

    @Test
    fun year2026MatchesOfficialCalendar() {
        // 정부 발표 2026 공휴일 (노동절·제헌절 포함, 지방선거 6/3)
        assertEquals(
            listOf(
                "01-01", "02-16", "02-17", "02-18", "03-01", "03-02", "05-01", "05-05", "05-24", "05-25",
                "06-03", "06-06", "07-17", "08-15", "08-17", "09-24", "09-25", "09-26", "10-03", "10-05",
                "10-09", "12-25",
            ),
            days(2026),
        )
    }

    @Test
    fun year2025Substitutes() {
        // 2025: 삼일절(토) → 3/3, 어린이날·부처님오신날 겹침 → 5/6, 추석 연휴 일요일 → 10/8
        assertEquals(listOf("2025-03-03", "2025-05-06", "2025-10-08"), substitutes(2025))
        // 2025 에는 노동절·제헌절이 공휴일이 아니었다
        assertFalse(Holidays.isHoliday(LocalDate.of(2025, 5, 1)))
        assertFalse(Holidays.isHoliday(LocalDate.of(2025, 7, 17)))
    }

    @Test
    fun year2027Substitutes() {
        // 설날 연휴(2/6~2/8) 일요일 → 2/9, 노동절(토) → 5/3, 제헌절(토) → 7/19,
        // 광복절(일) → 8/16, 개천절(일) → 10/4, 한글날(토) → 10/11, 성탄절(토) → 12/27
        assertEquals(
            listOf("2027-02-09", "2027-05-03", "2027-07-19", "2027-08-16", "2027-10-04", "2027-10-11", "2027-12-27"),
            substitutes(2027),
        )
    }

    @Test
    fun chuseokOverlappingGaecheonjeol() {
        // 2028 추석 연휴 10/2~10/4 가 개천절(10/3)과 겹침 → 10/5 하루만
        assertEquals(listOf("2028-10-05"), substitutes(2028))
        assertTrue(Holidays.isHoliday(LocalDate.of(2028, 4, 12))) // 국회의원 선거일
    }

    @Test
    fun laterYears() {
        // 2029: 어린이날(토) → 5/7, 부처님오신날(일) → 5/21, 추석 연휴 일요일 → 9/24
        assertEquals(listOf("2029-05-07", "2029-05-21", "2029-09-24"), substitutes(2029))
        // 2030: 설날 연휴 일요일 → 2/5, 어린이날(일) → 5/6
        assertEquals(listOf("2030-02-05", "2030-05-06"), substitutes(2030))
    }

    @Test
    fun ordinaryDaysAreNotHolidays() {
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 10, 28)))
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 6, 4)))
        assertEquals("추석", Holidays.on(LocalDate.of(2026, 9, 25)).single().name)
        assertEquals("추석 연휴", Holidays.on(LocalDate.of(2026, 9, 26)).single().name)
    }
}

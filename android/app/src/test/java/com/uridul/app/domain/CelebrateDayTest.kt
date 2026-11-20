package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class CelebrateDayTest {

    private val start = LocalDate.of(2026, 7, 17)
    private val birthdays = listOf(Birthday("나나", MonthDay.of(10, 18)))

    @Test
    fun hundredDays() {
        // 1일부터 세면 100일째 = 시작일 + 99일
        val d = CelebrateDay.today(start, true, birthdays, start.plusDays(99))!!
        assertEquals("오늘 우리 100일이에요 🎉", CelebrateDay.headline(d))
    }

    @Test
    fun birthday() {
        val d = CelebrateDay.today(start, true, birthdays, LocalDate.of(2026, 10, 18))!!
        assertEquals("오늘은 나나 생일이에요 🎂", CelebrateDay.headline(d))
    }

    @Test
    fun coupleDaysAndHolidaysAreNotCelebrated() {
        // 크리스마스(연인 기념일·공휴일)는 축하 창 없음
        assertNull(CelebrateDay.today(start, true, birthdays, LocalDate.of(2026, 12, 25)))
        assertNull(CelebrateDay.today(start, true, birthdays, LocalDate.of(2026, 11, 20)))
    }
}

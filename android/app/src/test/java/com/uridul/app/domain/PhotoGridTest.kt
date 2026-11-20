package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PhotoGridTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun sectionsNewestFirstWithHeader() {
        // 실제 서버 사진 수 (10~12월)
        val counts = mapOf(
            d("2026-10-28") to 1, d("2026-10-29") to 3, d("2026-10-30") to 4, d("2026-10-31") to 3,
            d("2026-11-07") to 5, d("2026-11-08") to 6, d("2026-11-11") to 3, d("2026-11-13") to 2, d("2026-11-14") to 5, d("2026-11-15") to 2,
            d("2026-12-02") to 3, d("2026-09-01") to 0,
        )
        val s = PhotoGrid.sections(counts)
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2026, 11), YearMonth.of(2026, 10)), s.map { it.month })
        assertEquals(d("2026-11-15"), s[1].days.first())
        assertEquals(d("2026-11-07"), s[1].days.last())
        assertEquals("2026년 11월 · 사진 23장 · 6일", s[1].header)
        assertEquals("2026년 10월 · 사진 11장 · 4일", s[2].header)
    }

    @Test
    fun monthsToLoadFromStartToNextMonth() {
        val m = PhotoGrid.monthsToLoad(d("2026-07-17"), d("2026-11-20"))
        assertEquals((7..12).map { YearMonth.of(2026, it) }.reversed(), m)
        // 아주 오래된 시작일은 5년까지만
        assertEquals(YearMonth.of(2021, 11), PhotoGrid.monthsToLoad(d("2010-01-01"), d("2026-11-20")).last())
    }
}

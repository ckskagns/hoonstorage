package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.DayPhoto
import com.uridul.app.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MemoryMoveTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun event(date: String, repeat: Repeat? = null, end: String = date, id: String = "e1") =
        CalendarEvent(id, "데이트", d(date), d(end), null, "", "u1", repeat = repeat)

    @Test
    fun plainEventMovesItsDay() {
        val old = event("2026-12-02")
        assertTrue(MemoryMove.needed(old, d("2026-11-25")))
        assertEquals(d("2026-11-25"), MemoryMove.target(old, d("2026-11-25"), null, d("2026-12-02")))
        // 그 일정의 날이 아닌 것(예전 반복 등)은 그대로
        assertNull(MemoryMove.target(old, d("2026-11-25"), null, d("2026-12-09")))
    }

    @Test
    fun sameStartMovesNothing() {
        val old = event("2026-12-02", end = "2026-12-03")
        assertFalse(MemoryMove.needed(old, d("2026-12-02")))
        assertNull(MemoryMove.target(old, d("2026-12-02"), null, d("2026-12-02")))
    }

    @Test
    fun weeklyKeepsOccurrenceNumber() {
        val old = event("2026-11-02", Repeat.WEEKLY)
        // 세 번째 반복(11/16) → 새 시작 11/04 기준 세 번째(11/18)
        assertEquals(d("2026-11-18"), MemoryMove.target(old, d("2026-11-04"), Repeat.WEEKLY, d("2026-11-16")))
        // 요일이 안 맞는 날은 그 일정 것이 아니다
        assertNull(MemoryMove.target(old, d("2026-11-04"), Repeat.WEEKLY, d("2026-11-17")))
    }

    @Test
    fun monthlyAndYearly() {
        val monthly = event("2026-01-10", Repeat.MONTHLY)
        assertEquals(d("2026-04-15"), MemoryMove.target(monthly, d("2026-01-15"), Repeat.MONTHLY, d("2026-04-10")))
        // 새 날짜가 31일이면 30일까지인 달에는 반복이 없다 → 옮기지 않는다
        assertNull(MemoryMove.target(monthly, d("2026-01-31"), Repeat.MONTHLY, d("2026-04-10")))
        val yearly = event("2024-05-01", Repeat.YEARLY)
        assertEquals(d("2026-05-03"), MemoryMove.target(yearly, d("2024-05-03"), Repeat.YEARLY, d("2026-05-01")))
    }

    @Test
    fun repeatChangedMovesOnlyFirstDay() {
        val old = event("2026-11-02", Repeat.WEEKLY)
        assertEquals(d("2026-11-05"), MemoryMove.target(old, d("2026-11-05"), null, d("2026-11-02")))
        assertNull(MemoryMove.target(old, d("2026-11-05"), null, d("2026-11-09")))
    }

    @Test
    fun staleMonthsFindsPhotosLeftOnOldDay() {
        val photos = mapOf(
            d("2026-11-02") to DayPhoto("p1", ByteArray(0), 2, "e1"),
            d("2026-11-10") to DayPhoto("p2", ByteArray(0), 1, "e2"),
            d("2026-11-20") to DayPhoto("p3", ByteArray(0), 1, "gone"),
        )
        // e1 은 11/25 로 옮겨졌고, e2 는 그대로, gone 은 지운 일정(목록에 없음)
        val events = listOf(event("2026-11-25", id = "e1"), event("2026-11-10", id = "e2"))
        assertEquals(setOf(YearMonth.of(2026, 11)), MemoryMove.staleMonths(photos, events))
        assertTrue(MemoryMove.staleMonths(photos, listOf(event("2026-11-02", id = "e1"), event("2026-11-10", id = "e2"))).isEmpty())
    }
}

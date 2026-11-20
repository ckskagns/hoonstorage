package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Place
import com.uridul.app.data.WishPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeTilesTest {
    private val today = LocalDate.parse("2026-11-20")
    private fun ev(date: String, end: String = date, time: LocalTime? = null) =
        CalendarEvent("e", "t", LocalDate.parse(date), LocalDate.parse(end), time, "", "u")

    @Test
    fun whenText() {
        assertEquals("오늘 오후 3:30", HomeTiles.whenText(ev("2026-11-20", time = LocalTime.of(15, 30)), today))
        assertEquals("오늘", HomeTiles.whenText(ev("2026-11-20"), today))
        assertEquals("내일 오후 7:00", HomeTiles.whenText(ev("2026-11-21", time = LocalTime.of(19, 0)), today))
        assertEquals("모레", HomeTiles.whenText(ev("2026-11-22"), today))
        assertEquals("D-5 · 11월 25일 (수)", HomeTiles.whenText(ev("2026-11-25"), today))
        // 이미 시작한 여러 날 일정
        assertEquals("진행 중 · 11월 22일까지", HomeTiles.whenText(ev("2026-11-18", "2026-11-22"), today))
    }

    @Test
    fun nextSkipsEndedAndShowsOngoing() {
        fun timed(id: String, start: Int, end: Int) = CalendarEvent(
            id, id, today, today, LocalTime.of(start, 30), "", "u",
            timeline = listOf(com.uridul.app.data.TimeSlot(LocalTime.of(start, 30), LocalTime.of(end, 30))),
        )
        // 실제 11/20: 집 재계약 15:30~16:30, 금요일 19:00~20:00 (+ 시험 18:30~19:30)
        val list = listOf(timed("집 재계약", 15, 16), timed("test0", 18, 19), timed("금요일", 19, 20))
        val at1550 = today.atTime(15, 50)
        assertEquals("집 재계약", HomeTiles.nextEvent(list, at1550)!!.id)
        assertEquals("지금 · 오후 4:30까지", HomeTiles.whenText(list[0], today, at1550))
        // 끝난 뒤에는 다음 것
        val at1700 = today.atTime(17, 0)
        assertEquals("test0", HomeTiles.nextEvent(list, at1700)!!.id)
        assertEquals("오늘 오후 6:30", HomeTiles.whenText(list[1], today, at1700))
        // 다 끝났으면 없음, 하루 종일 일정은 하루 동안 그대로
        assertNull(HomeTiles.nextEvent(list, today.atTime(21, 0)))
        assertEquals("allday", HomeTiles.nextEvent(list + ev("2026-11-20").copy(id = "allday"), today.atTime(21, 0))!!.id)
    }

    @Test
    fun wishOfDayRotatesOverUnvisited() {
        fun w(id: String, visited: Boolean = false) = WishPlace(id, Place("곳$id"), "", "u", visited)
        val wishes = listOf(w("a"), w("b", visited = true), w("c"), w("d"))
        val seen = (0L until 3).map { HomeTiles.wishOfDay(wishes, today.plusDays(it))!!.id }.toSet()
        assertEquals(setOf("a", "c", "d"), seen)
        // 같은 날은 같은 곳 (목록 순서와 상관없이)
        assertEquals(HomeTiles.wishOfDay(wishes, today), HomeTiles.wishOfDay(wishes.reversed(), today))
        assertNull(HomeTiles.wishOfDay(listOf(w("x", visited = true)), today))
    }
}

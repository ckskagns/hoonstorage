package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HeroPickTest {

    private val today = LocalDate.parse("2026-11-20")
    private fun c(date: String, id: String) = HeroPick.Candidate(LocalDate.parse(date), id, "e_$id")

    private val photos = listOf(
        c("2026-10-28", "a"), c("2026-11-07", "b"), c("2026-11-08", "c"),
        c("2026-11-14", "d"), c("2026-11-15", "e"),
    )

    @Test
    fun yearsAgoTodayComesFirst() {
        val list = photos + c("2025-11-20", "y1") + c("2024-11-20", "y2") + c("2025-11-19", "near")
        val order = HeroPick.order(list, today)
        assertEquals(listOf("y1", "y2"), order.take(2).map { it.id })
        assertEquals("y1", HeroPick.pick(list, today, 0)!!.id)
        assertEquals("y2", HeroPick.pick(list, today, 1)!!.id)
    }

    @Test
    fun futurePhotosAreSkipped() {
        // 12월 2일 일정에 미리 올린 사진은 추억이 아니다
        val order = HeroPick.order(photos + c("2026-12-02", "future"), today)
        assertTrue(order.none { it.id == "future" })
        assertNull(HeroPick.pick(listOf(c("2026-12-02", "future")), today, 0))
        assertNull(HeroPick.pick(emptyList(), today, 0))
    }

    @Test
    fun rotatesDailyThroughEveryPhotoBeforeRepeating() {
        val seen = (0L until photos.size).map { HeroPick.pick(photos, today.plusDays(it), 0)!!.id }
        assertEquals(photos.size, seen.toSet().size)
        // 다음 날은 다른 사진
        assertNotEquals(HeroPick.pick(photos, today, 0)!!.id, HeroPick.pick(photos, today.plusDays(1), 0)!!.id)
        // 같은 날은 몇 번을 다시 골라도 같은 사진 (홈을 열 때마다 바뀌지 않게)
        assertEquals(HeroPick.pick(photos, today, 0), HeroPick.pick(photos.reversed(), today, 0))
    }

    @Test
    fun nextWalksTheSameLineAndWraps() {
        val order = HeroPick.order(photos, today)
        assertEquals(order[1], HeroPick.pick(photos, today, 1))
        assertEquals(order[0], HeroPick.pick(photos, today, photos.size))
        assertEquals(order[photos.size - 1], HeroPick.pick(photos, today, -1))
    }

    @Test
    fun labels() {
        assertEquals("1년 전 오늘", HeroPick.label(LocalDate.parse("2025-11-20"), today))
        assertEquals("2년 전 오늘", HeroPick.label(LocalDate.parse("2024-11-20"), today))
        assertEquals("오늘", HeroPick.label(today, today))
        assertEquals("어제", HeroPick.label(today.minusDays(1), today))
        assertEquals("6일 전", HeroPick.label(today.minusDays(6), today))
        assertEquals("11월 7일", HeroPick.label(LocalDate.parse("2026-11-07"), today))
        assertEquals("2025년 3월 2일", HeroPick.label(LocalDate.parse("2025-03-02"), today))
    }
}

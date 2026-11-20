package com.uridul.app.domain

import com.uridul.app.data.PhotoMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PhotoDaysTest {

    private fun photo(id: String, date: String, at: Long, event: String = "e1") = PhotoMeta(
        id = id, eventId = event, date = LocalDate.parse(date), week = "", thumb = byteArrayOf(1),
        width = 480, height = 360, uploadedBy = "u", uploadedAtMillis = at,
    )

    @Test
    fun coverIsFirstUploadedPhotoOfTheDay() {
        val covers = PhotoDays.covers(
            listOf(
                photo("b", "2026-11-15", 200),
                photo("a", "2026-11-15", 100, event = "e2"), // 다른 일정이어도 같은 날이면 한 칸
                photo("c", "2026-11-15", 300),
                photo("d", "2026-11-16", 50),
            ),
        )
        assertEquals(2, covers.size)
        assertEquals("a", covers.getValue(LocalDate.parse("2026-11-15")).photo.id)
        assertEquals(3, covers.getValue(LocalDate.parse("2026-11-15")).count)
        assertEquals(1, covers.getValue(LocalDate.parse("2026-11-16")).count)
    }

    @Test
    fun sameUploadTimeIsStable() {
        val c = PhotoDays.covers(listOf(photo("z", "2026-11-01", 5), photo("y", "2026-11-01", 5)))
        assertEquals("y", c.values.single().photo.id)
    }

    @Test
    fun upcomingMonthsAreAskedUpToAYear() {
        val today = LocalDate.parse("2026-11-20")
        assertTrue(PhotoDays.mayHavePhotos(YearMonth.of(2026, 11), today))
        assertTrue(PhotoDays.mayHavePhotos(YearMonth.of(2025, 3), today))
        // 12월 2일 일정에 미리 올린 사진 (실제 서버에 있음)
        assertTrue(PhotoDays.mayHavePhotos(YearMonth.of(2026, 12), today))
        assertTrue(PhotoDays.mayHavePhotos(YearMonth.of(2027, 11), today))
        assertFalse(PhotoDays.mayHavePhotos(YearMonth.of(2027, 12), today))
    }

    @Test
    fun cacheUsedOnlyWhenCountMatchesAndFresh() {
        val now = 10L * PhotoDays.MAX_AGE_MS
        assertTrue(PhotoDays.cacheValid(4, now - 1000, 4, now))
        // 처음 (저장한 적 없음)
        assertFalse(PhotoDays.cacheValid(null, 0, 0, now))
        // 상대가 올리거나 지움
        assertFalse(PhotoDays.cacheValid(4, now - 1000, 5, now))
        assertFalse(PhotoDays.cacheValid(4, now - 1000, 3, now))
        // 너무 오래됨 (지우고 같은 수만큼 올린 경우 대비)
        assertFalse(PhotoDays.cacheValid(4, now - PhotoDays.MAX_AGE_MS, 4, now))
        // 폰 시계가 뒤로 감
        assertFalse(PhotoDays.cacheValid(4, now + 1000, 4, now))
        // 사진 없는 달도 저장해 두면 다시 받지 않는다
        assertTrue(PhotoDays.cacheValid(0, now, 0, now))
    }

    @Test
    fun moreBadge() {
        assertNull(PhotoDays.moreText(1))
        assertEquals("+1", PhotoDays.moreText(2))
        assertEquals("+9", PhotoDays.moreText(10))
    }
}

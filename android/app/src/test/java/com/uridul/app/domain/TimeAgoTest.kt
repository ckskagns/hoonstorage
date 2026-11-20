package com.uridul.app.domain

import com.uridul.app.data.DateReview
import com.uridul.app.data.ReviewEntry
import com.uridul.app.ui.dateStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 위젯 "3시간 전", 일기장 새 소식 점, 날짜 도장 */
class TimeAgoTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun ms(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private val now = ms("2026-11-20T14:30:00")

    @Test
    fun agoText() {
        assertEquals("방금", TimeAgo.text(now - 20_000, now))
        assertEquals("12분 전", TimeAgo.text(now - 12 * 60_000, now))
        assertEquals("3시간 전", TimeAgo.text(ms("2026-11-20T11:20:00"), now))
        assertEquals("어제", TimeAgo.text(ms("2026-11-19T23:50:00"), now))
        assertEquals("4일 전", TimeAgo.text(ms("2026-11-16T10:00:00"), now))
        assertEquals("11월 2일", TimeAgo.text(ms("2026-11-02T10:00:00"), now))
        assertEquals("2025년 3월 2일", TimeAgo.text(ms("2025-03-02T10:00:00"), now))
        // 폰 시계가 조금 늦어도 "방금"
        assertEquals("방금", TimeAgo.text(now + 5_000, now))
    }

    @Test
    fun diaryNewsDot() {
        fun r(by: String, at: Long, empty: Boolean = false) = DateReview(
            "x", "e", LocalDate.parse("2026-11-14"), "t", null,
            if (empty) emptyMap() else mapOf(by to ReviewEntry("hi", 5)), updatedBy = by, updatedAtMillis = at,
        )
        val seen = ms("2026-11-20T12:00:00")
        assertTrue(DiaryNews.hasNew(listOf(r("partner", seen + 1)), "me", seen, null))
        assertFalse(DiaryNews.hasNew(listOf(r("me", seen + 1)), "me", seen, null)) // 내가 쓴 것
        assertFalse(DiaryNews.hasNew(listOf(r("partner", seen - 1)), "me", seen, null)) // 이미 본 것
        assertFalse(DiaryNews.hasNew(listOf(r("partner", seen + 1, empty = true)), "me", seen, null)) // 지운 후기
        assertTrue(DiaryNews.hasNew(emptyList(), "me", seen, seen + 60_000)) // 상대 새 사진
        assertFalse(DiaryNews.hasNew(emptyList(), "me", seen, seen - 60_000))
    }

    @Test
    fun stamp() {
        assertEquals("'26 11 14", dateStamp(LocalDate.parse("2026-11-14")))
        assertEquals("'27 1 3", dateStamp(LocalDate.parse("2027-01-03")))
    }
}

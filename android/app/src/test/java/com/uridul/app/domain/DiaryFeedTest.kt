package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.ui.FeedPost
import com.uridul.app.ui.feedDateText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 일기장 피드: 게시물 저장(화면 회전·접기)과 "언제" 표시 */
class DiaryFeedTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun postSurvivesEncoding() {
        val p = FeedPost("e1", today, "저녁 데이트", Place("선릉감자탕", "https://naver.me/x", MapApp.NAVER))
        assertEquals(p, FeedPost.decode(p.encode()))
        val noPlace = p.copy(place = null)
        assertEquals(noPlace, FeedPost.decode(noPlace.encode()))
        assertNull(FeedPost.decode(""))
        assertNull(FeedPost.decode("망가진 글"))
        // 사진첩·후기는 "일정 ID_날짜" 로 찾는다
        assertEquals("e1_2026-11-02", p.reviewId)
    }

    @Test
    fun postFromEvent() {
        val e = CalendarEvent("e2", "영화", today.minusDays(3), today.minusDays(3), null, "", "u1", place = Place("CGV", null, null))
        val p = FeedPost.of(e)
        assertEquals("e2_2026-10-30", p.reviewId)
        assertEquals("CGV", p.place?.name)
    }

    @Test
    fun whenText() {
        assertEquals("오늘", feedDateText(today, today))
        assertEquals("어제", feedDateText(today.minusDays(1), today))
        assertEquals("3일 전", feedDateText(today.minusDays(3), today))
        assertEquals("10월 20일", feedDateText(LocalDate.of(2026, 10, 20), today))
        assertEquals("2025년 12월 24일", feedDateText(LocalDate.of(2025, 12, 24), today))
    }
}

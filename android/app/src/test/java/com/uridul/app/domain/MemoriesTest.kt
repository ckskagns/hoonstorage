package com.uridul.app.domain

import com.uridul.app.data.Backup
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.DateReview
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.data.Repeat
import com.uridul.app.data.ReviewEntry
import com.uridul.app.ui.lovedPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MemoriesTest {
    private val today = LocalDate.of(2026, 10, 28)
    private fun ev(id: String, title: String, date: LocalDate, repeat: Repeat? = null, place: Place? = null) =
        CalendarEvent(id, title, date, date, null, "", "u1", place = place, repeat = repeat)

    @Test
    fun lastMonthNewestFirstWithoutRecurringOrFuture() {
        val events = listOf(
            ev("a", "영화 데이트", today.minusDays(2)),
            ev("b", "선릉감자탕 저녁", today.minusDays(9)),
            ev("c", "운동", today.minusDays(3), repeat = Repeat.WEEKLY), // 반복은 빼기
            ev("d", "오늘 일정", today), // 오늘은 아직 추억이 아님
            ev("e", "두 달 전", today.minusMonths(2)), // 한 달보다 오래됨
            ev("f", "어제 카페", today.minusDays(1)),
        )
        val slides = Memories.slides(today, events)
        assertEquals(listOf("f", "a", "b"), slides.map { it.event.id })
        assertEquals(listOf("어제", "2일 전", "9일 전"), slides.map { it.label })
    }

    @Test
    fun yearAgoComesFirst() {
        val events = listOf(
            ev("y", "첫 여행", today.minusYears(1)),
            ev("a", "영화", today.minusDays(2)),
        )
        val slides = Memories.slides(today, events)
        assertEquals("1년 전 오늘", slides.first().label)
        assertEquals(listOf("y", "a"), slides.map { it.event.id })
        // 받아올 범위: 5년치 같은 날 + 지난 한 달
        assertEquals(6, Memories.queryRanges(today).size)
    }

    @Test
    fun emojiByTitleAndPlace() {
        assertEquals("🎬", Memories.emojiFor(ev("1", "영화 데이트", today)))
        assertEquals("🍽️", Memories.emojiFor(ev("2", "저녁", today)))
        assertEquals("☕", Memories.emojiFor(ev("3", "만남", today, place = Place("성수 카페", null, null))))
        assertEquals("🏃", Memories.emojiFor(ev("4", "한강 산책", today)))
        assertEquals("💗", Memories.emojiFor(ev("5", "우리둘만의!!", today)))
    }

    @Test
    fun lovedPlacesAverageAndThreshold() {
        val gamja = Place("선릉감자탕", null, MapApp.NAVER)
        val reviews = listOf(
            DateReview("r1", "e1", today.minusDays(9), "저녁", gamja, mapOf("u1" to ReviewEntry("최고", 5), "u2" to ReviewEntry("", 4))),
            DateReview("r2", "e2", today.minusDays(2), "저녁 또", gamja, mapOf("u1" to ReviewEntry("또 오자", 5))),
            DateReview("r3", "e3", today.minusDays(5), "카페", Place("별로 카페", null, null), mapOf("u1" to ReviewEntry("", 2))),
            DateReview("r4", "e4", today.minusDays(4), "산책", null, mapOf("u1" to ReviewEntry("", 5))),
        )
        val loved = lovedPlaces(reviews)
        assertEquals(listOf("선릉감자탕"), loved.map { it.place.name })
        assertEquals(2, loved.single().visits)
        assertEquals(14.0 / 3, loved.single().averageHearts, 0.001)
        assertEquals("또 오자", loved.single().lastText)
    }

    @Test
    fun reviewsInBackup() {
        val r = DateReview("e1_2026-10-19", "e1", LocalDate.of(2026, 10, 19), "저녁", Place("선릉감자탕", "https://naver.me/x", MapApp.NAVER),
            mapOf("u1" to ReviewEntry("최고", 5), "u2" to ReviewEntry("맛있다", 4)))
        val text = Backup.encode(emptyList(), emptyList(), null, null, emptyMap(), "2026-10-28T10:00", reviews = listOf(r))
        val back = Backup.decode(text)
        assertEquals(listOf(r), back.reviews)
        assertTrue(Backup.preview(back, emptySet(), emptySet(), setOf("e1_2026-10-19")).newReviews.isEmpty())
        // 예전 백업(후기 없음)도 읽힌다
        assertTrue(Backup.decode("""{"format":"uridul-backup","version":1,"events":[],"wishes":[]}""").reviews.isEmpty())
    }
}

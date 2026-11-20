package com.uridul.app.domain

import com.uridul.app.data.DateReview
import com.uridul.app.data.Place
import com.uridul.app.data.ReviewEntry
import com.uridul.app.ledger.EntryStatus
import com.uridul.app.ledger.EntryType
import com.uridul.app.ledger.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MonthRecapTest {
    private val nov = YearMonth.of(2026, 11)
    private fun d(s: String) = LocalDate.parse(s)
    private fun review(date: String, title: String, place: String?, vararg hearts: Int) = DateReview(
        id = "e_$date", eventId = "e", date = d(date), title = title, place = place?.let { Place(it) },
        entries = hearts.mapIndexed { i, h -> "u$i" to ReviewEntry("후기 $i", h) }.toMap(),
    )
    private fun at(s: String) = LocalDateTime.parse(s).atZone(LedgerEntry.ZONE).toInstant().toEpochMilli()
    private fun pay(id: String, time: String, amount: Long, merchant: String, category: String, cancel: Boolean = false) =
        LedgerEntry(id = id, type = EntryType.EXPENSE, amount = amount, cancel = cancel, at = at(time), merchant = merchant, category = category, assetId = "date_card")

    private val reviews = listOf(
        review("2026-11-07", "양양 데이트", "양양", 5, 5),
        review("2026-11-08", "양양 데이트", "양양", 4),
        review("2026-11-14", "아울렛데이트", "롯데아울렛", 5, 5),
        review("2026-11-15", "집돌이", null, 3, 4),
        review("2026-10-30", "가평데이트", "가평", 4),
        // 후기가 지워진 문서
        DateReview("x", "x", d("2026-11-20"), "빈", null, emptyMap()),
    )
    private val photos = mapOf(d("2026-11-07") to 5, d("2026-11-08") to 6, d("2026-11-11") to 3, d("2026-11-14") to 5, d("2026-11-15") to 2, d("2026-10-30") to 4)
    private val ledger = listOf(
        pay("a", "2026-11-17T22:16:19", 398_147, "Agoda", "date.travel"),
        pay("c", "2026-11-19T22:40:27", 398_147, "Agoda", "date.travel", cancel = true),
        pay("b", "2026-11-17T22:21:59", 65_400, "Agoda", "date.travel"),
        pay("k", "2026-11-10T16:26:00", 73_800, "Klook", "date.travel"),
        pay("f", "2026-11-12T12:00:00", 30_000, "식당", "food.meal"),
        pay("p", "2026-11-13T12:00:00", 99_000, "확인필요", "food.meal").copy(status = EntryStatus.PENDING),
    )

    @Test
    fun countsOnlyThisMonth() {
        val r = MonthRecap.build(nov, reviews, photos, ledger)
        assertEquals(4, r.posts)
        assertEquals(5, r.days) // 7·8·14·15 후기 + 11일 사진만
        assertEquals(21, r.photos)
        assertEquals((5 + 5 + 4 + 5 + 5 + 3 + 4) / 7.0, r.hearts!!, 1e-9)
        assertEquals(2, r.places) // 양양(2번)·롯데아울렛
        assertEquals("양양" to 2, r.topPlace)
        assertFalse(r.isEmpty)
    }

    @Test
    fun bestDayTiesGoToMorePhotosThenLater() {
        // 7일·14일 모두 하트 5.0, 사진도 5장씩 → 늦은 14일
        assertEquals(d("2026-11-14"), MonthRecap.build(nov, reviews, photos, ledger).best!!.date)
        val more = photos + (d("2026-11-07") to 9)
        assertEquals(d("2026-11-07"), MonthRecap.build(nov, reviews, more, ledger).best!!.date)
    }

    @Test
    fun moneyLeavesOutCancelledAndPending() {
        val r = MonthRecap.build(nov, reviews, photos, ledger)
        assertEquals(65_400L + 73_800 + 30_000, r.expense)
        assertEquals("date.travel", r.topCategory!!.first.key)
        assertEquals(139_200L, r.topCategory!!.second)
        assertEquals("k", r.biggest!!.id)
    }

    @Test
    fun collagePrefersHeartsThenPhotosAndIsDateOrdered() {
        val r = MonthRecap.build(nov, reviews, photos, ledger)
        // 하트 5.0 인 7·14일, 그다음 4.0 인 8일, 3.5 인 15일 (사진만 있는 11일은 빠짐)
        assertEquals(listOf(d("2026-11-07"), d("2026-11-08"), d("2026-11-14"), d("2026-11-15")), r.collage)
    }

    @Test
    fun emptyMonth() {
        val r = MonthRecap.build(YearMonth.of(2026, 9), reviews, photos, emptyList())
        assertTrue(r.isEmpty)
        assertNull(r.best)
        assertNull(r.hearts)
        assertNull(r.topPlace)
    }

    @Test
    fun monthsAndHomeWindow() {
        val today = d("2026-11-20")
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), MonthRecap.months(reviews, photos.keys, today))
        // 12월 2일 일정에 미리 올린 사진만 있는 12월은 아직 안 나온다 (실제 서버에 있음)
        val withFuture = photos.keys + d("2026-12-02")
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), MonthRecap.months(reviews, withFuture, today))
        assertEquals(YearMonth.of(2026, 12), MonthRecap.months(reviews, withFuture, d("2026-12-01")).first())
        assertEquals(YearMonth.of(2026, 10), MonthRecap.homeMonth(d("2026-11-01")))
        assertEquals(YearMonth.of(2026, 10), MonthRecap.homeMonth(d("2026-11-07")))
        assertNull(MonthRecap.homeMonth(d("2026-11-08")))
        assertEquals(YearMonth.of(2026, 12), MonthRecap.homeMonth(d("2027-01-03")))
    }
}

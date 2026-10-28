package com.uridul.app.domain

import com.uridul.app.data.Backup
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.data.Repeat
import com.uridul.app.data.TimeSlot
import com.uridul.app.data.UserFacingException
import com.uridul.app.data.WishPlace
import com.uridul.app.data.toDraft
import com.uridul.app.data.toSeriesDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun series(start: String, repeat: Repeat, end: String = start, skip: Set<LocalDate> = emptySet()) =
        CalendarEvent("s1", "운동", d(start), d(end), LocalTime.of(7, 30), "", "u1", repeat = repeat, skip = skip,
            timeline = listOf(TimeSlot(LocalTime.of(7, 30), LocalTime.of(8, 30))))

    @Test
    fun weeklyInRange() {
        // 2026-10-07 (수) 부터 매주
        val e = series("2026-10-07", Repeat.WEEKLY)
        assertEquals(
            listOf("2026-10-28", "2026-11-04", "2026-11-11"),
            Recurrence.occurrenceStarts(e, d("2026-10-26"), d("2026-11-14")).map { it.toString() },
        )
        // 시작 전 기간에는 없다
        assertTrue(Recurrence.occurrenceStarts(e, d("2026-09-01"), d("2026-10-06")).isEmpty())
    }

    @Test
    fun skippedDaysAreLeftOut() {
        val e = series("2026-10-07", Repeat.WEEKLY, skip = setOf(d("2026-11-04")))
        assertEquals(
            listOf("2026-10-28", "2026-11-11"),
            Recurrence.occurrenceStarts(e, d("2026-10-26"), d("2026-11-14")).map { it.toString() },
        )
    }

    @Test
    fun monthlySkipsMissingDays() {
        // 1월 31일부터 매달: 2월·4월에는 31일이 없어서 건너뛴다
        val e = series("2027-01-31", Repeat.MONTHLY)
        assertEquals(
            listOf("2027-01-31", "2027-03-31", "2027-05-31"),
            Recurrence.occurrenceStarts(e, d("2027-01-01"), d("2027-05-31")).map { it.toString() },
        )
        val fourteenth = series("2026-07-14", Repeat.MONTHLY)
        assertEquals(listOf("2026-10-14"), Recurrence.occurrenceStarts(fourteenth, d("2026-10-01"), d("2026-10-31")).map { it.toString() })
    }

    @Test
    fun yearlyAndLeapDay() {
        val e = series("2026-10-18", Repeat.YEARLY)
        assertEquals(listOf("2027-10-18", "2028-10-18"), Recurrence.occurrenceStarts(e, d("2027-01-01"), d("2028-12-31")).map { it.toString() })
        val leap = series("2028-02-29", Repeat.YEARLY)
        assertEquals(listOf("2029-02-28"), Recurrence.occurrenceStarts(leap, d("2029-01-01"), d("2029-12-31")).map { it.toString() })
    }

    @Test
    fun multiDayOccurrenceOverlappingRangeStart() {
        // 금~일 2박 여행을 매달: 범위 시작 전에 시작해도 걸치면 포함
        val e = series("2026-10-30", Repeat.MONTHLY, end = "2026-11-01")
        val occ = Recurrence.expand(listOf(e), d("2026-11-01"), d("2026-11-05"))
        assertEquals(listOf(d("2026-10-30")), occ.map { it.date })
        assertEquals(d("2026-11-01"), occ.single().endDate)
        assertEquals(d("2026-10-30"), occ.single().seriesStart)
    }

    @Test
    fun mergeDropsRawSeriesFromRangeQuery() {
        val e = series("2026-10-07", Repeat.WEEKLY)
        val plain = CalendarEvent("p1", "데이트", d("2026-10-28"), d("2026-10-28"), null, "", "u1")
        val merged = Recurrence.merge(listOf(plain, e), listOf(e), d("2026-10-25"), d("2026-10-31"))
        assertEquals(listOf("p1", "s1"), merged.map { it.id })
        assertEquals(d("2026-10-28"), merged.last().date)
    }

    @Test
    fun editingOneDayMovesWholeSeriesBySameShift() {
        val e = series("2026-10-07", Repeat.WEEKLY)
        val occ = Recurrence.occurrence(e, d("2026-10-28"))
        val draft = occ.toDraft()
        assertEquals(d("2026-10-07"), draft.seriesStart)
        assertEquals(d("2026-10-28"), draft.occurrence)
        // 제목만 바꾸면 날짜는 원래 첫 날 그대로
        assertEquals(d("2026-10-07"), draft.copy(title = "아침 운동").toSeriesDates().date)
        // 그 날에서 하루 뒤로 옮기면 반복 전체가 하루 뒤로 (첫 날 10/8)
        val moved = draft.copy(date = d("2026-10-29"), endDate = d("2026-10-29")).toSeriesDates()
        assertEquals(d("2026-10-08"), moved.date)
        assertNull(moved.seriesStart)
        // 펼친 일정을 원본으로
        assertEquals(d("2026-10-07"), occ.asSeries().date)
    }

    @Test
    fun nextOccurrence() {
        val e = series("2026-10-07", Repeat.WEEKLY)
        assertEquals(d("2026-10-28"), Recurrence.next(e, d("2026-10-28"))?.date)
        assertEquals(d("2026-11-04"), Recurrence.next(e, d("2026-10-29"))?.date)
        val once = CalendarEvent("o", "x", d("2026-10-01"), d("2026-10-01"), null, "", "u1")
        assertNull(Recurrence.next(once, d("2026-10-28")))
    }

    @Test
    fun repeatSpanIsValidated() {
        val tooLong = CalendarEvent("t", "여행", d("2026-10-01"), d("2026-10-08"), null, "", "u1", repeat = Repeat.WEEKLY).toDraft()
        assertNotNull(CalendarMath.validate(tooLong))
        val ok = CalendarEvent("t", "여행", d("2026-10-01"), d("2026-10-03"), null, "", "u1", repeat = Repeat.WEEKLY).toDraft()
        assertNull(CalendarMath.validate(ok.toSeriesDates()))
    }

    @Test
    fun backupRoundTrip() {
        val e = series("2026-10-07", Repeat.WEEKLY, skip = setOf(d("2026-11-04"))).copy(memo = "헬스장", remind = 60)
        val occ = Recurrence.occurrence(e, d("2026-10-28"))
        val wish = WishPlace("w1", Place("선릉감자탕", "https://naver.me/abc", MapApp.NAVER), "웨이팅 길대요", "u1", false)
        val text = Backup.encode(listOf(occ, e), listOf(wish), d("2026-07-17"), true, mapOf("u1" to "후이"), "2026-10-28T10:00")
        val data = Backup.decode(text)
        // 펼친 날짜가 아니라 원본 하나로 저장된다
        assertEquals(1, data.events.size)
        val back = data.events.single()
        assertEquals(d("2026-10-07"), back.date)
        assertEquals(Repeat.WEEKLY, back.repeat)
        assertEquals(setOf(d("2026-11-04")), back.skip)
        assertEquals("헬스장", back.memo)
        assertEquals(60, back.remind)
        assertEquals(LocalTime.of(8, 30), back.timeline.single().end)
        assertEquals(wish, data.wishes.single())
        assertEquals(d("2026-07-17"), data.startDate)

        val preview = Backup.preview(data, existingEventIds = setOf("s1"), existingWishIds = emptySet())
        assertTrue(preview.newEvents.isEmpty())
        assertEquals(1, preview.newWishes.size)
    }

    @Test
    fun backupRejectsOtherFiles() {
        for (bad in listOf("hello", "{\"format\":\"other\"}", "{\"format\":\"uridul-backup\",\"version\":99}")) {
            try {
                Backup.decode(bad)
                fail("읽으면 안 되는 파일: $bad")
            } catch (e: UserFacingException) {
                // 기대한 대로
            }
        }
        // 위험한 ID 는 건너뛴다
        val text = """{"format":"uridul-backup","version":1,"events":[{"id":"../x","title":"a","date":"2026-10-01","endDate":"2026-10-01","time":null}],"wishes":[]}"""
        assertTrue(Backup.decode(text).events.isEmpty())
    }
}

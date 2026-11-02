package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.WidgetStore
import com.uridul.app.ui.latestRegistered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 일정 있는 날을 누르면 그 날 "가장 최근에 등록한" 일정부터 보여준다 */
class CalendarTapTest {
    private val d = LocalDate.of(2026, 11, 2)
    private fun e(id: String, created: Long, day: LocalDate = d, end: LocalDate = day) =
        CalendarEvent(id, id, day, end, null, "", "u1", createdAtMillis = created)

    @Test
    fun picksLatestRegistered() {
        val list = listOf(e("old", 100), e("new", 300), e("mid", 200), e("other day", 999, d.plusDays(1)))
        assertEquals("new", latestRegistered(list, d)?.id)
        // 여러 날 일정도 그 사이 날에 포함
        val trip = e("trip", 500, d.minusDays(1), d.plusDays(1))
        assertEquals("trip", latestRegistered(list + trip, d)?.id)
        assertNull(latestRegistered(list, d.plusDays(5)))
    }

    @Test
    fun unknownTimeFallsBackToLast() {
        // 등록 시각을 모르면(예전 데이터 0) 목록에서 나중 것
        assertEquals("b", latestRegistered(listOf(e("a", 0), e("b", 0)), d)?.id)
    }

    @Test
    fun widgetStoreKeepsCreatedAt() {
        val back = WidgetStore.decode(WidgetStore.encode(listOf(e("a", 1_730_000_000_000))))
        assertEquals(1_730_000_000_000, back.single().createdAtMillis)
    }
}

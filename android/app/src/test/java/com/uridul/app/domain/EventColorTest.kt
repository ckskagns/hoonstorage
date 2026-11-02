package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventColor
import com.uridul.app.data.Repeat
import com.uridul.app.data.WidgetStore
import com.uridul.app.data.toDraft
import com.uridul.app.data.toSeriesDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 일정마다 고르는 색: 저장·불러오기·반복 일정에서 색이 그대로 남는지 */
class EventColorTest {
    private val d = LocalDate.of(2026, 11, 2)
    private fun event(color: EventColor?) = CalendarEvent("e1", "저녁 데이트", d, d, null, "", "me", color = color)

    @Test
    fun colorKeys() {
        assertEquals(EventColor.MINT, EventColor.fromOrNull("mint"))
        assertNull(EventColor.fromOrNull(null))
        assertNull(EventColor.fromOrNull("rainbow"))
    }

    @Test
    fun widgetStoreKeepsColor() {
        val list = listOf(event(EventColor.LAVENDER), event(null).copy(id = "e2"))
        val back = WidgetStore.decode(WidgetStore.encode(list))
        assertEquals(EventColor.LAVENDER, back[0].color)
        assertNull(back[1].color)
        // 예전 버전이 저장한 내용(색 칸 없음)도 읽힌다
        val old = WidgetStore.encode(list).replace(",\"color\":\"lavender\"", "").replace(",\"color\":null", "")
        assertNull(WidgetStore.decode(old)[0].color)
        assertEquals(2, WidgetStore.decode(old).size)
    }

    @Test
    fun draftKeepsColor() {
        assertEquals(EventColor.CORAL, event(EventColor.CORAL).toDraft().color)
        // 반복 일정의 어느 날에서 색을 바꿔도 원래 첫 날 기준으로 저장된다
        val series = event(null).copy(repeat = Repeat.WEEKLY)
        val occurrence = series.copy(date = d.plusWeeks(2), endDate = d.plusWeeks(2), seriesStart = d)
        val draft = occurrence.toDraft().copy(color = EventColor.SKY).toSeriesDates()
        assertEquals(d, draft.date)
        assertEquals(EventColor.SKY, draft.color)
    }
}

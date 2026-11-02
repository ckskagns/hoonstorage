package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.EventCategory
import com.uridul.app.data.PartnerDefaults
import com.uridul.app.data.WidgetStore
import com.uridul.app.data.categoryLabel
import com.uridul.app.data.Character
import com.uridul.app.data.toDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 일정 분류: 우리둘(기본) / 후이 일정 / 나나 일정 */
class EventCategoryTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun event(id: String, daysAgo: Long, c: EventCategory = EventCategory.US) =
        CalendarEvent(id, "일정 $id", today.minusDays(daysAgo), today.minusDays(daysAgo), null, "", "u1", category = c)

    @Test
    fun oldDataIsUs() {
        // 서버에 분류 칸이 없거나 모르는 값이면 우리둘
        assertEquals(EventCategory.US, EventCategory.from(null))
        assertEquals(EventCategory.US, EventCategory.from("weird"))
        assertEquals(EventCategory.PARTNER, EventCategory.from("partner"))
        assertEquals(EventCategory.US, CalendarEvent("a", "t", today, today, null, "", "u1").category)
        // 예전 버전이 위젯·백업에 저장한 내용(분류 칸 없음)도 우리둘
        val old = WidgetStore.encode(listOf(event("a", 1))).replace(",\"category\":\"us\"", "")
        assertEquals(EventCategory.US, WidgetStore.decode(old).single().category)
    }

    @Test
    fun keepsCategory() {
        val e = event("a", 1, EventCategory.CREATOR)
        assertEquals(EventCategory.CREATOR, WidgetStore.decode(WidgetStore.encode(listOf(e))).single().category)
        assertEquals(EventCategory.CREATOR, e.toDraft().category)
    }

    @Test
    fun labelsUseNames() {
        // 나나가 아직 들어오기 전: 미리 정해 둔 이름으로
        val waiting = Couple("c", listOf("u1"), mapOf("u1" to "후이"), today, true, partnerDefaults = PartnerDefaults("나나", Character.BUNNY))
        assertEquals("우리둘", waiting.categoryLabel(EventCategory.US))
        assertEquals("후이 일정", waiting.categoryLabel(EventCategory.CREATOR))
        assertEquals("나나 일정", waiting.categoryLabel(EventCategory.PARTNER))
        // 들어온 뒤 이름을 바꾸면 바뀐 이름으로
        val paired = waiting.copy(members = listOf("u1", "u2"), names = mapOf("u1" to "후이", "u2" to "나나짱"))
        assertEquals("나나짱 일정", paired.categoryLabel(EventCategory.PARTNER))
    }

    @Test
    fun onlyTogetherEventsInMemories() {
        assertTrue(EventCategory.US.together)
        assertFalse(EventCategory.CREATOR.together)
        val slides = Memories.slides(today, listOf(event("us", 3), event("hui", 4, EventCategory.CREATOR), event("nana", 5, EventCategory.PARTNER)))
        assertEquals(listOf("us"), slides.map { it.event.id })
    }
}

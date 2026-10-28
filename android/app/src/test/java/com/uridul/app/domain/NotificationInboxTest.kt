package com.uridul.app.domain

import com.uridul.app.data.InboxItem
import com.uridul.app.data.NotificationInbox
import com.uridul.app.ui.timeAgo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NotificationInboxTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun item(id: String, at: Long, read: Boolean = false, eventId: String? = "e1") =
        InboxItem(id, "30분 뒤 일정이 있어요 ⏰", "오후 7:00  저녁 데이트\n📍선릉감자탕", LocalDate.of(2026, 10, 28), eventId, at, read)

    @Test
    fun keepsRecent30DaysNewestFirst() {
        val list = listOf(item("old", now - 31 * day), item("a", now - 2 * day), item("b", now - 1000))
        assertEquals(listOf("b", "a"), NotificationInbox.prune(list, now).map { it.id })
    }

    @Test
    fun encodeDecodeKeepsEverything() {
        val list = listOf(item("a", now, read = true), item("b", now - 5, eventId = null))
        assertEquals(list, NotificationInbox.decode(NotificationInbox.encode(list)))
        assertEquals(emptyList<InboxItem>(), NotificationInbox.decode("broken"))
        assertEquals(emptyList<InboxItem>(), NotificationInbox.decode(null))
    }

    @Test
    fun timeAgoLabels() {
        assertEquals("방금", timeAgo(now - 10_000, now))
        assertEquals("12분 전", timeAgo(now - 12 * 60_000, now))
        assertEquals("3시간 전", timeAgo(now - 3 * 3_600_000L, now))
        assertEquals("어제", timeAgo(now - 30 * 3_600_000L, now))
    }
}

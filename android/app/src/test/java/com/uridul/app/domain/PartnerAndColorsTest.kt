package com.uridul.app.domain

import com.uridul.app.data.Couple
import com.uridul.app.notify.PartnerAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PartnerAndColorsTest {
    private val d = LocalDate.of(2026, 11, 2)

    @Test
    fun alertWording() {
        val one = listOf(PartnerAlerts.Change("e1", "저녁 데이트", d, created = true, atMillis = 1))
        val (t1, l1) = PartnerAlerts.describe("나나", one)
        assertEquals("나나가 일정을 추가했어요 💕", t1)
        assertTrue(l1.single().startsWith("새 일정 · 11월 2일"))
        assertTrue(l1.single().endsWith("저녁 데이트"))

        val edited = listOf(PartnerAlerts.Change("e1", "저녁 데이트", d, created = false, atMillis = 1))
        assertEquals("나나가 일정을 고쳤어요 ✏️", PartnerAlerts.describe("나나", edited).first)

        val mixed = one + edited.map { it.copy(id = "e2") }
        assertEquals("나나가 일정 2개를 바꿨어요 ✏️", PartnerAlerts.describe("나나", mixed).first)
        val added = one + one.map { it.copy(id = "e2") }
        assertEquals("나나가 일정 2개를 추가했어요 💕", PartnerAlerts.describe("나나", added).first)
    }

    @Test
    fun colorsAreSharedByRole() {
        // 만든 사람(후이) = creator 색, 들어온 사람(나나) = partner 색 → 두 폰이 같은 색으로 본다
        val couple = Couple("c", listOf("hui", "nana"), emptyMap(), d, true, colors = mapOf("creator" to "pink", "partner" to "sky"))
        assertEquals("pink" to "sky", couple.colorKeys("hui")) // 후이 폰: 내 일정 분홍, 상대 하늘
        assertEquals("sky" to "pink", couple.colorKeys("nana")) // 나나 폰: 내 일정 하늘, 상대 분홍
        assertTrue(couple.isCreator("hui"))
        // 아직 서버에 없으면 null (이 폰 색을 올림)
        val empty = couple.copy(colors = emptyMap())
        assertEquals(null to null, empty.colorKeys("hui"))
    }
}

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
        assertTrue(l1.single().startsWith("📅 새 일정 · 11월 2일"))
        assertTrue(l1.single().endsWith("저녁 데이트"))

        val edited = listOf(PartnerAlerts.Change("e1", "저녁 데이트", d, created = false, atMillis = 1))
        assertEquals("나나가 일정을 고쳤어요 ✏️", PartnerAlerts.describe("나나", edited).first)

        val mixed = one + edited.map { it.copy(id = "e2") }
        assertEquals("나나가 일정 2개를 바꿨어요 ✏️", PartnerAlerts.describe("나나", mixed).first)
        val added = one + one.map { it.copy(id = "e2") }
        assertEquals("나나가 일정 2개를 추가했어요 💕", PartnerAlerts.describe("나나", added).first)
    }

    @Test
    fun wishAndReviewAlerts() {
        val wish = PartnerAlerts.Change("w1", "선릉감자탕", d, created = true, atMillis = 1, kind = PartnerAlerts.Kind.WISH)
        val (tw, lw) = PartnerAlerts.describe("나나", listOf(wish))
        assertEquals("나나가 가고 싶은 곳을 담았어요 📍", tw)
        assertEquals("📍 가고 싶은 곳 · 선릉감자탕", lw.single())
        val visited = wish.copy(created = false)
        assertEquals("나나가 다녀온 곳을 표시했어요 ✅", PartnerAlerts.describe("나나", listOf(visited)).first)

        val review = PartnerAlerts.Change("e1", "저녁 데이트", d, created = true, atMillis = 2, kind = PartnerAlerts.Kind.REVIEW, detail = "♥♥♥♥♡ “맛있었다”")
        val (tr, lr) = PartnerAlerts.describe("나나", listOf(review))
        assertEquals("나나가 후기를 남겼어요 💗", tr)
        assertTrue(lr.single().startsWith("💗 후기 · 11월 2일"))
        assertTrue(lr.single().endsWith("♥♥♥♥♡ “맛있었다”"))

        val event = PartnerAlerts.Change("e2", "영화", d.plusDays(1), created = true, atMillis = 3)
        assertEquals("나나 소식 3개 💕", PartnerAlerts.describe("나나", listOf(wish, review, event)).first)

        // 누르면 갈 곳: 가고 싶은 곳만이면 그 탭, 후기 하나면 그 날 그 일정, 섞이면 캘린더의 가장 늦은 날
        assertEquals("wish", PartnerAlerts.targetOf(listOf(wish)).tab)
        assertEquals(PartnerAlerts.Target("calendar", d, "e1"), PartnerAlerts.targetOf(listOf(review)))
        assertEquals(PartnerAlerts.Target("calendar", d.plusDays(1), null), PartnerAlerts.targetOf(listOf(wish, review, event)))
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

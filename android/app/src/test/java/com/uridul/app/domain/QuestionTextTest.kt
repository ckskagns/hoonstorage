package com.uridul.app.domain

import com.uridul.app.data.PartnerAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTextTest {

    @Test
    fun notReadyBeforeRulesDeployed() {
        assertEquals("곧 열려요", QuestionText.cardStatus(false, true, null, PartnerAnswer.Unknown, "나나"))
    }

    @Test
    fun lockedWhenPartnerAnsweredFirst() {
        assertEquals(
            "🔒 나나님은 답했어요 · 내가 답하면 열려요",
            QuestionText.cardStatus(true, true, null, PartnerAnswer.Locked, "나나"),
        )
        assertEquals("눌러서 답하기", QuestionText.cardStatus(true, true, null, PartnerAnswer.None, "나나"))
    }

    @Test
    fun afterMyAnswer() {
        assertEquals("답했어요 · 나나님 답을 기다려요", QuestionText.cardStatus(true, true, "응", PartnerAnswer.None, "나나"))
        assertEquals("💞 둘 다 답했어요 · 눌러서 보기", QuestionText.cardStatus(true, true, "응", PartnerAnswer.Open("나도"), "나나"))
    }

    @Test
    fun partnerTextOnlyWhenOpen() {
        val (text, dim) = QuestionText.partnerLine(true, PartnerAnswer.Open("고마워"), loading = false)
        assertEquals("고마워", text)
        assertFalse(dim)
        assertTrue(QuestionText.partnerLine(true, PartnerAnswer.Locked, loading = false).second)
        assertEquals("불러오는 중…", QuestionText.partnerLine(true, PartnerAnswer.Unknown, loading = true).first)
    }
}

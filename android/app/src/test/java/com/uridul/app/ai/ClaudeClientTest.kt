package com.uridul.app.ai

import com.uridul.app.data.Couple
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Claude 오류 말·키 모양·AI 켜짐·관리자 */
class ClaudeClientTest {

    @Test fun errorsInPlainWords() {
        assertTrue(ClaudeClient.errorText(401).startsWith("키가 올바르지 않아요"))
        assertTrue(ClaudeClient.errorText(402).startsWith("충전 잔액이 없어요"))
        assertTrue(ClaudeClient.errorText(429).startsWith("사용량 한도"))
        assertTrue(ClaudeClient.errorText(529).contains("붐벼요"))
        assertTrue(ClaudeClient.errorText(503).startsWith("Claude 서버가"))
        assertTrue(ClaudeClient.errorText(418).contains("(418)"))
    }

    @Test fun reasonHidesKey() {
        val r = ClaudeClient.reasonOf("invalid x-api-key: sk-ant-api03-test0abcdefghij")
        assertFalse(r.contains("test0abcdefghij"))
        assertTrue(r.contains("(키)"))
        assertEquals("", ClaudeClient.reasonOf(null))
    }

    @Test fun keyShapeAndMask() {
        val key = "sk-ant-api03-test0000000000000000000000000000abcd"
        assertTrue(AiConfig.looksLikeKey(key))
        assertFalse(AiConfig.looksLikeKey("짧음"))
        assertFalse(AiConfig.looksLikeKey("sk-ant api03 test0000000000000000"))
        assertEquals("sk-ant-••••••abcd", AiConfig.masked(key))
    }

    @Test fun usableOnlyWhenOnWithKey() {
        assertFalse(AiConfig.OFF.usable) // 문서 없음·못 읽음 = 꺼짐
        assertFalse(AiConfig(enabled = false, key = "sk-ant-test0key000000000000").usable) // 꺼지면 키가 있어도 안 부름
        assertFalse(AiConfig(enabled = true, key = null).usable)
        assertTrue(AiConfig(enabled = true, key = "sk-ant-test0key000000000000").usable)
    }

    @Test fun adminIsCoupleCreator() {
        val couple = Couple("c", listOf("hui", "nana"), emptyMap(), LocalDate.of(2026, 7, 17), true)
        assertTrue(couple.isAdmin("hui"))
        assertFalse(couple.isAdmin("nana"))
        assertFalse(Couple("c", emptyList(), emptyMap(), LocalDate.of(2026, 7, 17), true).isAdmin("hui"))
    }
}

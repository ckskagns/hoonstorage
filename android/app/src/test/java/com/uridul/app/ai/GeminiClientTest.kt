package com.uridul.app.ai

import com.uridul.app.data.Couple
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 제미나이 API 요청·답 읽기·오류 말, 키 모양, AI 켜짐, 관리자 */
class GeminiClientTest {

    @Test fun requestHasUserPrompt() {
        val body = JSONObject(GeminiClient.requestBody("안녕"))
        val part = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0)
        assertEquals("안녕", part.getString("text"))
        assertEquals("user", body.getJSONArray("contents").getJSONObject(0).getString("role"))
    }

    @Test fun readsTextParts() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"안녕하세요, "},{"text":"우리둘!"}],"role":"model"}}]}"""
        assertEquals("안녕하세요, 우리둘!", GeminiClient.parseText(json))
        assertNull(GeminiClient.parseText("""{"candidates":[]}"""))
        assertNull(GeminiClient.parseText("깨진 글"))
    }

    @Test fun errorsInPlainWords() {
        val invalid = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""
        assertTrue(GeminiClient.errorMessage(400, invalid).startsWith("키가 올바르지 않아요"))
        assertTrue(GeminiClient.errorMessage(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}""").contains("429 RESOURCE_EXHAUSTED"))
        assertTrue(GeminiClient.errorMessage(403, "").startsWith("이 키로는"))
        assertTrue(GeminiClient.errorMessage(503, "").startsWith("제미나이 서버가"))
    }

    @Test fun keyShapeAndMask() {
        assertTrue(AiConfig.looksLikeKey("AIzaSyD-test0000000000000000000000000"))
        assertFalse(AiConfig.looksLikeKey("짧음"))
        assertFalse(AiConfig.looksLikeKey("AIza test0000000000000000000000000"))
        assertEquals("AIza••••••0000", AiConfig.masked("AIzaSyD-test0000000000000000000000000"))
    }

    @Test fun usableOnlyWhenOnWithKey() {
        assertFalse(AiConfig.OFF.usable) // 문서 없음·못 읽음 = 꺼짐
        assertFalse(AiConfig(enabled = false, key = "test0key0000000000000000").usable) // 꺼지면 키가 있어도 안 부름
        assertFalse(AiConfig(enabled = true, key = null).usable)
        assertTrue(AiConfig(enabled = true, key = "test0key0000000000000000").usable)
    }

    @Test fun adminIsCoupleCreator() {
        val couple = Couple("c", listOf("hui", "nana"), emptyMap(), LocalDate.of(2026, 7, 17), true)
        assertTrue(couple.isAdmin("hui"))
        assertFalse(couple.isAdmin("nana"))
        assertFalse(Couple("c", emptyList(), emptyMap(), LocalDate.of(2026, 7, 17), true).isAdmin("hui"))
    }
}

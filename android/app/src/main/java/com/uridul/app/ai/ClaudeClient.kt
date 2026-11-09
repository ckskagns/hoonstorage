package com.uridul.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** AI 답 (성공이면 글, 아니면 알아듣기 쉬운 오류) */
sealed interface AiReply {
    data class Text(val text: String) : AiReply
    data class Error(val message: String, val code: Int) : AiReply
}

/**
 * Claude API 부르기 (직접 통신: 공식 SDK 는 앱 크기를 약 40MB 늘려서 쓰지 않는다).
 *
 * 요금은 Claude 콘솔에 충전한 금액에서 나간다. 자동 충전을 끄면 충전한 금액 이상은 쓰이지 않는다.
 * 키는 앱(공개 APK)에 넣지 않고 우리 두 사람만 읽을 수 있는 서버 칸에서 받아 쓴다 ([AiConfigStore]).
 * 키는 기록(로그)에 남기지 않는다.
 */
object ClaudeClient {
    /** 기본 모델. 비용을 줄이려면 "claude-sonnet-5-5"(검색·비서) 또는 "claude-haiku-5-5"(간단한 요약)로 바꿀 수 있다 */
    const val MODEL = "claude-opus-5-5"

    /** 한 번에 받을 답의 최대 길이 (토큰, 생각하는 몫 포함). 한 번 부를 때 나올 수 있는 요금을 제한한다 */
    const val MAX_OUTPUT_TOKENS = 4096

    private const val URL_MESSAGES = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2026-10-21"

    /** 안전 검사로 답을 거절하면 다른 모델이 대신 답하게 하는 서버 기능 */
    private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

    suspend fun ask(apiKey: String, prompt: String): AiReply = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(URL_MESSAGES).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey.trim())
                setRequestProperty("anthropic-version", API_VERSION)
                setRequestProperty("anthropic-beta", FALLBACK_BETA)
            }
            try {
                conn.outputStream.use { it.write(requestBody(prompt).toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) parseReply(body) else AiReply.Error(errorText(code) + reasonOf(errorMessage(body)), code)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { AiReply.Error("인터넷 연결을 확인해 주세요", -1) }
    }

    internal fun requestBody(prompt: String): String = JSONObject()
        .put("model", MODEL)
        .put("max_tokens", MAX_OUTPUT_TOKENS)
        .put("fallbacks", "default")
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        .toString()

    /** 답 읽기: 거절이면 오류, 아니면 글 조각(type == "text")을 이어 붙인다 */
    internal fun parseReply(json: String): AiReply = runCatching {
        val o = JSONObject(json)
        if (o.optString("stop_reason") == "refusal") return AiReply.Error("Claude가 이 요청에는 답하지 않았어요", 0)
        val content = o.getJSONArray("content")
        val text = (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
            .trim()
        if (text.isEmpty()) AiReply.Error("Claude 답이 비어 있어요", 0) else AiReply.Text(text)
    }.getOrElse { AiReply.Error("Claude 답을 읽지 못했어요", 0) }

    /** 오류 본문의 설명 ({"type":"error","error":{"type":"billing_error","message":"…"}}) */
    internal fun errorMessage(body: String): String? =
        runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull()

    /** 상태 번호를 알아듣기 쉬운 말로 */
    internal fun errorText(code: Int): String = when (code) {
        400 -> "요청 형식이 맞지 않아요 (400)"
        401 -> "키가 올바르지 않아요. 키를 다시 붙여 넣어 주세요 (401)"
        402 -> "충전 잔액이 없어요. Claude 콘솔에서 충전해 주세요 (402)"
        403 -> "이 키로는 Claude 를 쓸 수 없어요 (403)"
        404 -> "Claude 모델을 찾지 못했어요 (404)"
        429 -> "사용량 한도에 걸렸어요. 잠시 뒤에 다시 해 주세요 (429)"
        529 -> "Claude 가 잠시 붐벼요. 잠시 뒤에 다시 해 주세요 (529)"
        in 500..599 -> "Claude 서버가 잠시 바빠요. 잠시 뒤에 다시 해 주세요 ($code)"
        else -> "Claude 오류가 났어요 ($code)"
    }

    /** 오류 설명 앞부분 (키처럼 생긴 글자는 가림) */
    internal fun reasonOf(message: String?): String {
        val m = message?.replace(Regex("""sk-ant-[0-9A-Za-z_\-]{6,}"""), "(키)")?.trim()?.take(160)
        return if (m.isNullOrEmpty()) "" else "\n$m"
    }
}

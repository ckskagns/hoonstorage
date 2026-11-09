package com.uridul.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 제미나이 API (클라우드) 부르기.
 *
 * 요금은 제미나이 Pro 구독의 월 크레딧이 연결된 구글 클라우드 프로젝트(Uridul)로 나가고,
 * 그 프로젝트에는 월 지출 한도가 걸려 있다 (docs/AI비서_준비_가이드.md).
 * 키는 앱(공개 APK)에 넣지 않고 우리 두 사람만 읽을 수 있는 서버 칸에서 받아 쓴다 ([AiKeyStore]).
 * 키는 기록(로그)에 남기지 않는다.
 */
object GeminiClient {
    /** 항상 최신 Flash 모델을 가리키는 이름 */
    const val MODEL = "gemini-flash-latest"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    sealed interface Reply {
        data class Text(val text: String) : Reply
        data class Error(val message: String, val code: Int) : Reply
    }

    suspend fun generate(apiKey: String, prompt: String, model: String = MODEL): Reply = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$BASE$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            try {
                conn.outputStream.use { it.write(requestBody(prompt).toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) {
                    parseText(body)?.let { Reply.Text(it) } ?: Reply.Error("제미나이 답이 비어 있어요", code)
                } else {
                    Reply.Error(errorMessage(code, body), code)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { Reply.Error("인터넷 연결을 확인해 주세요", -1) }
    }

    /** 한 번에 받을 답의 최대 길이 (토큰). 한 번 부를 때 나올 수 있는 요금을 제한한다 */
    const val MAX_OUTPUT_TOKENS = 4096

    internal fun requestBody(prompt: String): String = JSONObject()
        .put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))),
            ),
        )
        .put("generationConfig", JSONObject().put("maxOutputTokens", MAX_OUTPUT_TOKENS))
        .toString()

    /** 답 글자만 꺼내기 (여러 조각이면 이어 붙임) */
    internal fun parseText(json: String): String? = runCatching {
        val parts = JSONObject(json).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        (0 until parts.length())
            .mapNotNull { parts.getJSONObject(it).optString("text").takeIf { t -> t.isNotEmpty() } }
            .joinToString("")
            .trim()
            .ifEmpty { null }
    }.getOrNull()

    /** 오류를 알아듣기 쉬운 말로 (뒤에 상태 이름) */
    internal fun errorMessage(code: Int, body: String): String {
        val status = runCatching { JSONObject(body).getJSONObject("error").optString("status") }.getOrDefault("")
        val text = when {
            body.contains("API_KEY_INVALID") || body.contains("API key not valid", ignoreCase = true) ->
                "키가 올바르지 않아요. 키를 다시 붙여 넣어 주세요"
            code == 429 -> "사용량 한도(이번 달 지출 한도 또는 분당 횟수)에 걸렸어요. 잠시 뒤에 다시 해 주세요"
            code == 401 || code == 403 -> "이 키로는 제미나이를 쓸 수 없어요 (권한·결제 연결 확인)"
            code == 404 -> "제미나이 모델을 찾지 못했어요"
            code >= 500 -> "제미나이 서버가 잠시 바빠요. 잠시 뒤에 다시 해 주세요"
            else -> "제미나이 오류가 났어요"
        }
        return if (status.isNotEmpty()) "$text ($code $status)" else "$text ($code)"
    }
}

package com.uridul.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** AI 답 (성공이면 글, 아니면 알아듣기 쉬운 오류) */
sealed interface AiReply {
    data class Text(val text: String) : AiReply
    data class Error(val message: String, val code: Int) : AiReply
}

/**
 * Claude API 부르기 (공식 Java SDK).
 *
 * 요금은 Claude 콘솔에 충전한 금액에서 나간다. 자동 충전을 끄면 충전한 금액 이상은 쓰이지 않는다.
 * 키는 앱(공개 APK)에 넣지 않고 우리 두 사람만 읽을 수 있는 서버 칸에서 받아 쓴다 ([AiConfigStore]).
 * 키는 기록(로그)에 남기지 않는다.
 */
object ClaudeClient {
    /** 기본 모델. 비용을 줄이려면 "claude-sonnet-5-5"(검색·비서) 또는 "claude-haiku-5-5"(간단한 요약)로 바꿀 수 있다 */
    const val MODEL = "claude-opus-5-5"

    /** 한 번에 받을 답의 최대 길이 (토큰, 생각하는 몫 포함). 한 번 부를 때 나올 수 있는 요금을 제한한다 */
    const val MAX_OUTPUT_TOKENS = 4096L

    /** 안전 검사로 답을 거절하면 다른 모델이 대신 답하게 하는 서버 기능 */
    private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

    @Volatile private var cached: Pair<String, AnthropicClient>? = null

    private fun client(apiKey: String): AnthropicClient {
        cached?.let { (k, c) -> if (k == apiKey) return c }
        val c = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        cached?.second?.close()
        cached = apiKey to c
        return c
    }

    suspend fun ask(apiKey: String, prompt: String): AiReply = withContext(Dispatchers.IO) {
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(prompt)
                .build()
            val message = client(apiKey.trim()).beta().messages().create(params)
            val stop = message.stopReason().map { it.toString() }.orElse("")
            if (stop == "refusal") return@withContext AiReply.Error("Claude가 이 요청에는 답하지 않았어요", 0)
            val text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
                .joinToString("").trim()
            if (text.isEmpty()) AiReply.Error("Claude 답이 비어 있어요", 0) else AiReply.Text(text)
        } catch (e: UnauthorizedException) {
            AiReply.Error(errorText(401), 401)
        } catch (e: PermissionDeniedException) {
            AiReply.Error(errorText(403), 403)
        } catch (e: NotFoundException) {
            AiReply.Error(errorText(404), 404)
        } catch (e: RateLimitException) {
            AiReply.Error(errorText(429), 429)
        } catch (e: InternalServerException) {
            AiReply.Error(errorText(e.statusCode()), e.statusCode())
        } catch (e: AnthropicServiceException) {
            AiReply.Error(errorText(e.statusCode()) + reasonOf(e.message), e.statusCode())
        } catch (e: AnthropicIoException) {
            AiReply.Error("인터넷 연결을 확인해 주세요", -1)
        } catch (e: Exception) {
            AiReply.Error("Claude 를 부르지 못했어요" + reasonOf(e.message), -1)
        }
    }

    /** 상태 번호를 알아듣기 쉬운 말로 */
    internal fun errorText(code: Int): String = when (code) {
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

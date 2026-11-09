package com.uridul.app.ai

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** AI 비서 설정 (couples/{coupleId}/aiConfig/assistant { enabled, apiKey }) — 지금은 Claude API 키 */
data class AiConfig(
    /** AI 기능 켜짐. 꺼져 있으면 앱이 Claude 를 부르지 않고, 상대 폰은 키를 받을 수도 없다(서버 규칙) */
    val enabled: Boolean = false,
    val key: String? = null,
) {
    val hasKey: Boolean get() = !key.isNullOrBlank()

    /** Claude 를 불러도 되는 상태 */
    val usable: Boolean get() = enabled && hasKey

    companion object {
        val OFF = AiConfig()

        /** 붙여 넣은 글이 키처럼 생겼는지 (빈칸 없이 20~300자) */
        fun looksLikeKey(text: String): Boolean {
            val k = text.trim()
            return k.length in 20..300 && k.none { it.isWhitespace() }
        }

        /** 화면에 보일 때 가린 키: 앞 7자 · 뒤 4자만 ("sk-ant-••••••abcd") */
        fun masked(key: String): String = if (key.length <= 14) "••••" else key.take(7) + "••••••" + key.takeLast(4)
    }
}

/**
 * AI 설정 저장소. 관리자만 쓸 수 있고, 관리자는 언제나·상대는 AI 가 켜져 있을 때만 읽을 수 있다(서버 규칙).
 * 읽지 못하면(문서 없음·꺼짐·인터넷 없음) 꺼진 것으로 본다.
 */
class AiConfigStore(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun config(coupleId: String) = db.collection("couples").document(coupleId).collection("aiConfig")
    private fun doc(coupleId: String) = config(coupleId).document(DOC)

    suspend fun load(coupleId: String): AiConfig = runCatching {
        val snap = doc(coupleId).get().await()
        if (!snap.exists()) AiConfig.OFF
        else AiConfig(enabled = snap.getBoolean("enabled") == true, key = snap.getString("apiKey")?.takeIf { it.isNotBlank() })
    }.getOrDefault(AiConfig.OFF)

    suspend fun setEnabled(coupleId: String, uid: String, enabled: Boolean) {
        doc(coupleId).set(
            mapOf("enabled" to enabled, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    /** 키 넣기·바꾸기 (켜짐 여부는 그대로, 처음이면 꺼진 채로) */
    suspend fun saveKey(coupleId: String, uid: String, key: String, enabled: Boolean) {
        doc(coupleId).set(
            mapOf("enabled" to enabled, "apiKey" to key.trim(), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    /** 키 지우기 (AI 도 함께 끈다) */
    suspend fun clearKey(coupleId: String, uid: String) {
        doc(coupleId).set(
            mapOf("enabled" to false, "apiKey" to FieldValue.delete(), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    /** 예전 제미나이 설정(키 포함) 지우기 — 관리자가 설정을 열 때 한 번. 없으면 아무 일도 없다 */
    suspend fun removeOldGemini(coupleId: String) {
        runCatching { config(coupleId).document("gemini").delete().await() }
    }

    private companion object {
        const val DOC = "assistant"
    }
}

/**
 * AI 비서의 유일한 입구: 앱의 모든 AI 기능은 여기를 거친다.
 * 부를 때마다 서버에서 켜짐 여부를 새로 확인하고, 꺼져 있으면 Claude 를 부르지 않는다 (요금이 생기지 않음).
 */
object AiService {
    private val store by lazy { AiConfigStore() }

    suspend fun ask(coupleId: String, prompt: String): AiReply {
        val config = store.load(coupleId)
        return when {
            !config.enabled -> AiReply.Error("AI 기능이 꺼져 있어요", 0)
            !config.hasKey -> AiReply.Error("AI 키가 아직 없어요", 0)
            else -> ClaudeClient.ask(config.key!!, prompt)
        }
    }
}

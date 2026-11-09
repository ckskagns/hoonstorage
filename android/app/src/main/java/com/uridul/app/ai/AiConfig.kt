package com.uridul.app.ai

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.uridul.app.data.Couple
import kotlinx.coroutines.tasks.await

/**
 * 관리자 = 커플 공간을 만든 사람(멤버 목록의 첫 번째, 후이).
 * 관리자 기능(AI 켜기/끄기, 키 넣기·보기·지우기 등)은 관리자 폰에만 보이고, 서버 규칙도 관리자만 바꿀 수 있게 막는다.
 */
fun Couple.isAdmin(uid: String): Boolean = members.firstOrNull() == uid

/** AI 비서 설정 (couples/{coupleId}/aiConfig/gemini) */
data class AiConfig(
    /** AI 기능 켜짐. 꺼져 있으면 앱이 제미나이를 부르지 않고, 상대 폰은 키를 받을 수도 없다(서버 규칙) */
    val enabled: Boolean = false,
    val key: String? = null,
) {
    val hasKey: Boolean get() = !key.isNullOrBlank()

    /** 제미나이를 불러도 되는 상태 */
    val usable: Boolean get() = enabled && hasKey

    companion object {
        val OFF = AiConfig()

        /** 붙여 넣은 글이 키처럼 생겼는지 (빈칸 없이 20~200자) */
        fun looksLikeKey(text: String): Boolean {
            val k = text.trim()
            return k.length in 20..200 && k.none { it.isWhitespace() }
        }

        /** 화면에 보일 때 가린 키: 앞 4자 · 뒤 4자만 */
        fun masked(key: String): String = if (key.length <= 10) "••••" else key.take(4) + "••••••" + key.takeLast(4)
    }
}

/**
 * AI 설정 저장소. 관리자만 쓸 수 있고, 관리자는 언제나·상대는 AI 가 켜져 있을 때만 읽을 수 있다(서버 규칙).
 * 읽지 못하면(문서 없음·꺼짐·인터넷 없음) 꺼진 것으로 본다.
 */
class AiConfigStore(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun doc(coupleId: String) =
        db.collection("couples").document(coupleId).collection("aiConfig").document("gemini")

    suspend fun load(coupleId: String): AiConfig = runCatching {
        val snap = doc(coupleId).get().await()
        if (!snap.exists()) AiConfig.OFF
        else AiConfig(enabled = snap.getBoolean("enabled") == true, key = snap.getString("geminiKey")?.takeIf { it.isNotBlank() })
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
            mapOf("enabled" to enabled, "geminiKey" to key.trim(), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    /** 키 지우기 (AI 도 함께 끈다) */
    suspend fun clearKey(coupleId: String, uid: String) {
        doc(coupleId).set(
            mapOf("enabled" to false, "geminiKey" to FieldValue.delete(), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }
}

/**
 * AI 비서의 유일한 입구: 앱의 모든 AI 기능은 여기를 거친다.
 * 부를 때마다 서버에서 켜짐 여부를 새로 확인하고, 꺼져 있으면 제미나이를 부르지 않는다 (요금이 생기지 않음).
 */
object AiService {
    private val store by lazy { AiConfigStore() }

    suspend fun ask(coupleId: String, prompt: String): GeminiClient.Reply {
        val config = store.load(coupleId)
        return when {
            !config.enabled -> GeminiClient.Reply.Error("AI 기능이 꺼져 있어요", 0)
            !config.hasKey -> GeminiClient.Reply.Error("AI 키가 아직 없어요", 0)
            else -> GeminiClient.generate(config.key!!, prompt)
        }
    }
}

package com.uridul.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** 상대 답의 상태 */
sealed interface PartnerAnswer {
    /** 상대가 아직 답하지 않음 */
    data object None : PartnerAnswer
    /** 상대는 답했지만 내가 아직 안 해서 잠김 (보안 규칙이 막아 줌) */
    data object Locked : PartnerAnswer
    /** 둘 다 답해서 열림 */
    data class Open(val text: String) : PartnerAnswer
    /** 인터넷이 끊겨 모름 */
    data object Unknown : PartnerAnswer
}

data class QuestionDay(
    val date: LocalDate,
    val mine: String?,
    val partner: PartnerAnswer,
)

/**
 * 오늘의 질문 답: couples/{coupleId}/answers/{yyyy-MM-dd}_{uid}
 *   { date, uid, qid, text, at }
 * 보안 규칙(firestore.rules): 내 답은 언제나 읽을 수 있고, 상대 답은 같은 날 내 답이 있을 때만 읽힌다.
 * 그래서 상대 답을 읽다가 "권한 없음"이 나오면 = 상대는 답했고 아직 잠김.
 */
class QuestionRepository(private val db: FirebaseFirestore) {

    private fun answers(coupleId: String) =
        db.collection("couples").document(coupleId).collection("answers")

    private fun docId(date: LocalDate, uid: String) = "${date}_$uid"

    /** 보안 규칙이 아직 배포되지 않았으면 내 답 읽기부터 권한 없음 → [NotReady] */
    class NotReady : Exception("answers rules not deployed")

    suspend fun load(coupleId: String, date: LocalDate, myUid: String, partnerUid: String?): QuestionDay {
        val mine = try {
            answers(coupleId).document(docId(date, myUid)).get().await().getString("text")
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw NotReady()
            throw e
        }
        val partner = when {
            partnerUid == null -> PartnerAnswer.None
            else -> try {
                val text = answers(coupleId).document(docId(date, partnerUid)).get().await().getString("text")
                when {
                    text == null -> PartnerAnswer.None
                    // 내가 답하기 전인데 읽혔다면 폰에 남은 기록일 뿐이므로 보여 주지 않는다
                    mine == null -> PartnerAnswer.Locked
                    else -> PartnerAnswer.Open(text)
                }
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PartnerAnswer.Locked else PartnerAnswer.Unknown
            }
        }
        return QuestionDay(date, mine, partner)
    }

    /** 답 저장 (고치기도 같은 함수). 끊겨 있으면 폰에 먼저 적혔다가 연결되면 올라간다 */
    fun answer(
        coupleId: String, date: LocalDate, myUid: String, qid: Int, text: String,
        onSaved: () -> Unit, onError: (Exception) -> Unit,
    ) {
        answers(coupleId).document(docId(date, myUid)).set(
            mapOf(
                "date" to date.toString(),
                "uid" to myUid,
                "qid" to qid,
                "text" to text,
                "at" to FieldValue.serverTimestamp(),
            ),
        ).addOnSuccessListener { onSaved() }.addOnFailureListener(onError)
    }
}

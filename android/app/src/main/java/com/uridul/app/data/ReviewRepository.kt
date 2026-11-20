package com.uridul.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** 한 사람의 후기: 한 줄 + 하트 1~5개 */
data class ReviewEntry(val text: String, val hearts: Int) {
    companion object {
        const val TEXT_MAX = 100
        val HEARTS = 1..5
    }
}

/**
 * 지난 일정 하루에 대한 후기 (두 사람 각자 하나씩).
 * 반복 일정도 날짜마다 따로 남길 수 있도록 "일정 ID + 날짜" 로 구분한다.
 * 일정 제목·장소를 함께 적어 두어 일정이 지워져도 추억은 남는다.
 */
data class DateReview(
    val id: String,
    val eventId: String,
    val date: LocalDate,
    val title: String,
    val place: Place?,
    /** 사람(uid)별 후기 */
    val entries: Map<String, ReviewEntry>,
    /** 마지막으로 고친 사람·시각 (일기장 탭의 새 소식 점) */
    val updatedBy: String = "",
    val updatedAtMillis: Long = 0,
) {
    val averageHearts: Double get() = if (entries.isEmpty()) 0.0 else entries.values.map { it.hearts }.average()

    companion object {
        fun idOf(eventId: String, date: LocalDate) = "${eventId}_$date"
    }
}

/**
 * couples/{coupleId}/reviews/{일정ID_날짜}
 *   { eventId, date, title, placeName, placeUrl, placeApp, reviews: { uid: { text, hearts } }, updatedBy, updatedAt }
 * 자기 후기만 쓰고 지울 수 있다 (firestore.rules). 무료 요금제로 동작.
 */
class ReviewRepository(private val db: FirebaseFirestore) {

    private fun reviews(coupleId: String) =
        db.collection("couples").document(coupleId).collection("reviews")

    /** 전체 후기 (두 사람이 남긴 것이라 많아야 수백 개) */
    fun observe(coupleId: String): Flow<List<DateReview>> = callbackFlow {
        val reg = reviews(coupleId).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().mapNotNull { it.toReview() })
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    suspend fun fetchAll(coupleId: String): List<DateReview> =
        reviews(coupleId).get().await().documents.mapNotNull { it.toReview() }

    /** 내 후기 저장 (그 날 일정의 제목·장소도 함께 적어 둔다). [event] 는 그 날 기준(펼친 반복 일정 포함) */
    fun save(coupleId: String, uid: String, event: CalendarEvent, entry: ReviewEntry, onError: (Exception) -> Unit) {
        val place = event.place ?: event.slots.firstOrNull { it.place != null }?.place
        val data = mapOf(
            "eventId" to event.id,
            "date" to event.date.toString(),
            "title" to event.title.take(CalendarEvent.TITLE_MAX),
            "placeName" to place?.name?.take(Place.NAME_MAX),
            "placeUrl" to place?.url?.takeIf { it.length <= Place.URL_MAX && it.startsWith("http") },
            "placeApp" to place?.app?.key,
            "reviews" to mapOf(uid to mapOf("text" to entry.text.trim().take(ReviewEntry.TEXT_MAX), "hearts" to entry.hearts.coerceIn(ReviewEntry.HEARTS))),
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        // merge: 상대 후기는 그대로 두고 내 것만 넣는다
        reviews(coupleId).document(DateReview.idOf(event.id, event.date)).set(data, SetOptions.merge()).addOnFailureListener(onError)
    }

    /** 내 후기 지우기. 상대 후기도 없으면 문서째 지운다 */
    fun deleteMine(coupleId: String, uid: String, review: DateReview, onError: (Exception) -> Unit) {
        val doc = reviews(coupleId).document(review.id)
        val task = if (review.entries.keys == setOf(uid)) {
            doc.delete()
        } else {
            doc.update(mapOf("reviews.$uid" to FieldValue.delete(), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
        }
        task.addOnFailureListener(onError)
    }

    /**
     * 일정 날짜를 고쳤을 때 그 일정의 후기를 새 날짜로 옮긴다.
     * 문서 ID 에 날짜가 들어 있어서 새 ID 로 만들고(두 사람 후기·고친 시각 그대로) 예전 것을 지운다.
     * 새 날짜에 이미 후기가 있으면 섞지 않고 그대로 둔다.
     * @param target 원래 날짜 → 새 날짜 (옮기지 않으면 null)
     * @return 옮긴 후기 수
     */
    suspend fun moveDates(coupleId: String, uid: String, eventId: String, target: (LocalDate) -> LocalDate?): Int {
        val moves = reviews(coupleId).whereEqualTo("eventId", eventId).get().await().documents.mapNotNull { d ->
            val from = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val to = target(from)?.takeIf { it != from } ?: return@mapNotNull null
            Triple(d, from, to)
        }
        if (moves.isEmpty()) return 0
        // 반복 일정을 앞으로 옮기면 새 날짜가 다음 반복의 원래 날짜일 수 있다: 먼 날부터 옮겨서 자리를 비운다
        val forward = moves.first().let { it.third.isAfter(it.second) }
        var moved = 0
        for ((doc, _, to) in if (forward) moves.sortedByDescending { it.second } else moves.sortedBy { it.second }) {
            val dest = reviews(coupleId).document(DateReview.idOf(eventId, to))
            if (dest.get().await().exists()) continue
            val data = doc.data.orEmpty().toMutableMap()
            data["date"] = to.toString()
            // 규칙상 마지막으로 고친 사람은 나. 고친 시각은 그대로 두어 상대에게 "새 후기" 점이 뜨지 않게 한다
            data["updatedBy"] = uid
            db.batch().set(dest, data).delete(doc.reference).commit().await()
            moved++
        }
        return moved
    }

    /** 백업 불러오기: 같은 ID 로 다시 만든다 */
    fun restore(coupleId: String, uid: String, review: DateReview, onError: (Exception) -> Unit) {
        val data = mapOf(
            "eventId" to review.eventId,
            "date" to review.date.toString(),
            "title" to review.title,
            "placeName" to review.place?.name,
            "placeUrl" to review.place?.url,
            "placeApp" to review.place?.app?.key,
            "reviews" to review.entries.mapValues { (_, e) -> mapOf("text" to e.text, "hearts" to e.hearts) },
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        reviews(coupleId).document(review.id).set(data).addOnFailureListener(onError)
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toReview(): DateReview? {
        val eventId = getString("eventId") ?: return null
        val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val entries = (get("reviews") as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val uid = k as? String ?: return@mapNotNull null
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val hearts = (m["hearts"] as? Number)?.toInt()?.takeIf { it in ReviewEntry.HEARTS } ?: return@mapNotNull null
            uid to ReviewEntry((m["text"] as? String).orEmpty(), hearts)
        }.toMap()
        val placeName = getString("placeName")?.takeIf { it.isNotBlank() }
        return DateReview(
            id = id,
            eventId = eventId,
            date = date,
            title = getString("title").orEmpty(),
            place = placeName?.let { Place(it, getString("placeUrl"), MapApp.from(getString("placeApp"))) },
            entries = entries,
            updatedBy = getString("updatedBy").orEmpty(),
            updatedAtMillis = getTimestamp("updatedAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0,
        )
    }
}

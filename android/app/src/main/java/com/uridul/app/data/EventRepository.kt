package com.uridul.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * couples/{coupleId}/events/{eventId}
 *   { title, date "yyyy-MM-dd", endDate "yyyy-MM-dd", allDay, time "HH:mm"|null, memo,
 *     createdBy, updatedBy, createdAt, updatedAt }
 * 커플 멤버 두 사람만 읽고 쓸 수 있다 (firestore.rules).
 */
class EventRepository(private val db: FirebaseFirestore) {

    private fun events(coupleId: String) =
        db.collection("couples").document(coupleId).collection("events")

    /** 시작일이 범위 안에 있는 일정. 저장 직후에도 바로 보이도록 로컬 변경을 그대로 반영한다. */
    fun observeRange(coupleId: String, range: ClosedRange<LocalDate>): Flow<List<CalendarEvent>> =
        listen(
            events(coupleId)
                .whereGreaterThanOrEqualTo("date", range.start.toString())
                .whereLessThanOrEqualTo("date", range.endInclusive.toString())
                .orderBy("date"),
        )

    /** 오늘 이후 끝나는 일정 (위젯용). */
    fun observeUpcoming(coupleId: String, today: LocalDate, limit: Long = 40): Flow<List<CalendarEvent>> =
        listen(upcomingQuery(coupleId, today, limit))

    /** 위젯이 앱 밖에서 한 번 새로 받아올 때 쓴다. */
    suspend fun fetchUpcoming(coupleId: String, today: LocalDate, limit: Long = 40): List<CalendarEvent> =
        upcomingQuery(coupleId, today, limit).get().await().documents.mapNotNull { it.toEvent() }

    /** 달력 범위의 일정을 한 번 받아온다 (한 달 달력 위젯·알림용). */
    suspend fun fetchRange(coupleId: String, range: ClosedRange<LocalDate>): List<CalendarEvent> =
        events(coupleId)
            .whereGreaterThanOrEqualTo("date", range.start.toString())
            .whereLessThanOrEqualTo("date", range.endInclusive.toString())
            .orderBy("date")
            .get().await().documents.mapNotNull { it.toEvent() }

    private fun upcomingQuery(coupleId: String, today: LocalDate, limit: Long) =
        events(coupleId)
            .whereGreaterThanOrEqualTo("endDate", today.toString())
            .orderBy("endDate")
            .limit(limit)

    /**
     * 저장. 서버 응답을 기다리지 않는다(오프라인이어도 화면에는 바로 보이고, 연결되면 올라간다).
     * 실패하면 onError 로 알린다.
     */
    fun save(coupleId: String, uid: String, draft: EventDraft, onError: (Exception) -> Unit) {
        val data = dataOf(draft, uid)
        val task = if (draft.id == null) {
            data["createdBy"] = uid
            data["createdAt"] = FieldValue.serverTimestamp()
            events(coupleId).document().set(data)
        } else {
            events(coupleId).document(draft.id).update(data)
        }
        task.addOnFailureListener(onError)
    }

    /** 일정 전체 목록 (검색용). 오프라인이면 폰에 남아 있는 것만. */
    suspend fun fetchAll(coupleId: String): List<CalendarEvent> =
        events(coupleId).orderBy("date").get().await().documents.mapNotNull { it.toEvent() }

    /**
     * 지운 일정 되돌리기: 같은 문서 ID 로 다시 만든다 (만든 사람은 원래 그대로).
     * 상대가 만든 일정도 되돌릴 수 있도록 규칙에서 createdBy 가 커플 멤버이면 허용한다.
     */
    fun restore(coupleId: String, uid: String, event: CalendarEvent, onError: (Exception) -> Unit) {
        val data = dataOf(event.toDraft(), uid)
        data["createdBy"] = event.createdBy.ifEmpty { uid }
        data["createdAt"] = FieldValue.serverTimestamp()
        events(coupleId).document(event.id).set(data).addOnFailureListener(onError)
    }

    private fun dataOf(draft: EventDraft, uid: String): MutableMap<String, Any?> {
        return mutableMapOf<String, Any?>(
            "title" to draft.title.trim(),
            "date" to draft.date.toString(),
            "endDate" to draft.endDate.toString(),
            "allDay" to (draft.time == null),
            "time" to draft.time?.format(TimeFormat),
            "memo" to draft.memo.trim(),
            // 하루 종일 일정은 일정 하나에 장소 하나, 시간 일정은 시간대마다 장소
            "place" to if (draft.time == null) placeMap(draft.place) else null,
            "timeline" to if (draft.time == null) null else draft.timeline.take(TimeSlot.MAX_SLOTS).map { s ->
                mapOf(
                    "start" to s.start.format(TimeFormat),
                    "end" to s.end?.format(TimeFormat),
                    "title" to s.title.trim().take(TimeSlot.TITLE_MAX),
                    "place" to placeMap(s.place),
                )
            },
            // 알림 시간 (몇 분 전, 끄기 -1). 기본값이면 비워 둔다
            "remind" to draft.remind?.takeIf { it in Remind.allowed },
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }

    fun delete(coupleId: String, eventId: String, onError: (Exception) -> Unit) {
        events(coupleId).document(eventId).delete().addOnFailureListener(onError)
    }

    private fun listen(query: Query): Flow<List<CalendarEvent>> = callbackFlow<List<CalendarEvent>> {
        val reg = query.addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            trySend(snap.documents.mapNotNull { it.toEvent() })
        }
        awaitClose { reg.remove() }
    }
        .retryWhen { cause, attempt ->
            val retryable = cause is FirebaseFirestoreException &&
                (cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    cause.code == FirebaseFirestoreException.Code.UNAVAILABLE)
            if (retryable && attempt < 3) {
                delay(1_000L * (attempt + 1))
                true
            } else {
                false
            }
        }
        .catch { emit(emptyList()) }

    private fun DocumentSnapshot.toEvent(): CalendarEvent? {
        val title = getString("title") ?: return null
        val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val end = getString("endDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: date
        val time = getString("time")?.let { runCatching { LocalTime.parse(it, TimeFormat) }.getOrNull() }
        return CalendarEvent(
            id = id,
            title = title,
            date = date,
            endDate = maxOf(end, date),
            time = if (getBoolean("allDay") == true) null else time,
            memo = getString("memo").orEmpty(),
            createdBy = getString("createdBy").orEmpty(),
            place = parsePlace(get("place")),
            timeline = (get("timeline") as? List<*>).orEmpty().mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val start = (m["start"] as? String)?.let { runCatching { LocalTime.parse(it, TimeFormat) }.getOrNull() }
                    ?: return@mapNotNull null
                TimeSlot(
                    start = start,
                    end = (m["end"] as? String)?.let { runCatching { LocalTime.parse(it, TimeFormat) }.getOrNull() },
                    title = (m["title"] as? String).orEmpty(),
                    place = parsePlace(m["place"]),
                )
            },
            remind = (get("remind") as? Number)?.toInt()?.takeIf { it in Remind.allowed },
        )
    }

    private fun placeMap(p: Place?): Map<String, Any?>? =
        p?.let { mapOf("name" to it.name.trim().take(Place.NAME_MAX), "url" to it.url, "app" to it.app?.key) }

    private fun parsePlace(v: Any?): Place? {
        val m = v as? Map<*, *> ?: return null
        val name = (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return Place(name, m["url"] as? String, MapApp.from(m["app"] as? String))
    }

    companion object {
        val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

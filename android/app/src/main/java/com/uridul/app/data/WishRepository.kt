package com.uridul.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/** 가고 싶은 곳 하나 (지도 앱에서 공유받은 장소 + 한 줄 메모) */
data class WishPlace(
    val id: String,
    val place: Place,
    val memo: String,
    val createdBy: String,
    val visited: Boolean,
    /** 담은 시각 (정렬용, 저장 직후 서버 시각이 오기 전에는 0) */
    val createdAtMillis: Long = 0,
) {
    companion object {
        const val MEMO_MAX = 200
        const val MAX_ITEMS = 300
    }
}

/**
 * couples/{coupleId}/wishes/{id}
 *   { name, url, app, memo, visited, createdBy, createdAt, updatedAt }
 * 커플 멤버 두 사람만 읽고 쓴다 (firestore.rules). 서버 함수 없이 무료 요금제로 동작.
 */
class WishRepository(private val db: FirebaseFirestore) {

    private fun wishes(coupleId: String) =
        db.collection("couples").document(coupleId).collection("wishes")

    fun observe(coupleId: String): Flow<List<WishPlace>> = callbackFlow {
        val reg = wishes(coupleId).orderBy("createdAt", Query.Direction.DESCENDING).limit(WishPlace.MAX_ITEMS.toLong())
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().mapNotNull { it.toWish() })
            }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    suspend fun fetchAll(coupleId: String): List<WishPlace> =
        wishes(coupleId).get().await().documents.mapNotNull { it.toWish() }

    fun add(coupleId: String, uid: String, place: Place, memo: String = "", onError: (Exception) -> Unit) {
        wishes(coupleId).document().set(dataOf(place, memo, false, uid).apply {
            put("createdBy", uid)
            put("createdAt", FieldValue.serverTimestamp())
        }).addOnFailureListener(onError)
    }

    /** 백업 불러오기·되돌리기: 같은 ID 로 다시 만든다 */
    fun restore(coupleId: String, uid: String, wish: WishPlace, createdBy: String, onError: (Exception) -> Unit) {
        wishes(coupleId).document(wish.id).set(dataOf(wish.place, wish.memo, wish.visited, uid).apply {
            put("createdBy", createdBy)
            put("createdAt", FieldValue.serverTimestamp())
        }).addOnFailureListener(onError)
    }

    fun setVisited(coupleId: String, uid: String, id: String, visited: Boolean, onError: (Exception) -> Unit) {
        wishes(coupleId).document(id)
            .update(mapOf("visited" to visited, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnFailureListener(onError)
    }

    fun setMemo(coupleId: String, uid: String, id: String, memo: String, onError: (Exception) -> Unit) {
        wishes(coupleId).document(id)
            .update(mapOf("memo" to memo.trim().take(WishPlace.MEMO_MAX), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnFailureListener(onError)
    }

    fun delete(coupleId: String, id: String, onError: (Exception) -> Unit) {
        wishes(coupleId).document(id).delete().addOnFailureListener(onError)
    }

    private fun dataOf(place: Place, memo: String, visited: Boolean, uid: String): MutableMap<String, Any?> = mutableMapOf(
        "name" to place.name.trim().take(Place.NAME_MAX),
        "url" to place.url?.takeIf { it.length <= Place.URL_MAX && (it.startsWith("http://") || it.startsWith("https://")) },
        "app" to place.app?.key,
        "memo" to memo.trim().take(WishPlace.MEMO_MAX),
        "visited" to visited,
        "updatedBy" to uid,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toWish(): WishPlace? {
        val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
        return WishPlace(
            id = id,
            place = Place(name, getString("url"), MapApp.from(getString("app"))),
            memo = getString("memo").orEmpty(),
            createdBy = getString("createdBy").orEmpty(),
            visited = getBoolean("visited") == true,
            createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0,
        )
    }
}

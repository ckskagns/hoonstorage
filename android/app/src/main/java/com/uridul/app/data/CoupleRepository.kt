package com.uridul.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.uridul.app.domain.InviteToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Firestore 구조
 * users/{uid}            { coupleId, displayName }
 * couples/{coupleId}     { members[], names{uid:name}, startDate "yyyy-MM-dd", countFromOne, createdAt }
 * invites/{token}        { coupleId, createdBy, createdAt, expiresAt }   ← 1회용, 24시간
 */
class CoupleRepository(private val db: FirebaseFirestore) {

    private val users get() = db.collection("users")
    private val couples get() = db.collection("couples")
    private val invites get() = db.collection("invites")

    /** 내가 속한 커플 공간 ID. 연결 전이면 null. */
    fun observeCoupleId(uid: String): Flow<String?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            // 새 기기에서 캐시가 비어 있을 때 "연결 안 됨"으로 잠깐 보이는 것을 막는다
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            trySend(snap.getString("coupleId"))
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    fun observeCouple(coupleId: String): Flow<Couple?> = callbackFlow {
        val reg = couples.document(coupleId).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            trySend(snap.toCouple())
        }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    suspend fun createCouple(uid: String, name: String, startDate: LocalDate): String {
        val ref = couples.document()
        db.batch()
            .set(
                ref,
                mapOf(
                    "members" to listOf(uid),
                    "names" to mapOf(uid to name),
                    "startDate" to startDate.toString(),
                    "countFromOne" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .set(users.document(uid), mapOf("coupleId" to ref.id, "displayName" to name), SetOptions.merge())
            .commit()
            .await()
        return ref.id
    }

    /** 1회용 초대 토큰을 만든다. 24시간 뒤 만료. */
    suspend fun createInvite(coupleId: String, uid: String): String {
        val token = InviteToken.generate()
        val expires = Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24))
        invites.document(token).set(
            mapOf(
                "coupleId" to coupleId,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(expires),
            ),
        ).await()
        return token
    }

    /** 초대 토큰으로 상대의 커플 공간에 참여한다. 토큰은 같은 배치에서 삭제되어 재사용할 수 없다. */
    suspend fun join(token: String, uid: String, name: String) {
        val inviteRef = invites.document(token)
        val invite = inviteRef.get(Source.SERVER).await()
        if (!invite.exists()) {
            throw UserFacingException("만료되었거나 이미 사용된 초대 링크예요. 상대에게 새 링크를 요청해 주세요.")
        }
        if (invite.getString("createdBy") == uid) {
            throw UserFacingException("내가 만든 초대 링크예요. 상대에게 보내 주세요.")
        }
        val expiresAt = invite.getTimestamp("expiresAt")
        if (expiresAt == null || expiresAt.toDate().before(Date())) {
            throw UserFacingException("초대 링크가 만료되었어요. 상대에게 새 링크를 요청해 주세요.")
        }
        val coupleId = invite.getString("coupleId")
            ?: throw UserFacingException("초대 정보가 올바르지 않아요.")

        try {
            db.batch()
                .update(
                    couples.document(coupleId),
                    FieldPath.of("members"), FieldValue.arrayUnion(uid),
                    FieldPath.of("names", uid), name,
                    FieldPath.of("joinToken"), token,
                )
                .delete(inviteRef)
                .set(users.document(uid), mapOf("coupleId" to coupleId, "displayName" to name), SetOptions.merge())
                .commit()
                .await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw UserFacingException("이미 두 사람이 연결된 공간이거나 사용할 수 없는 링크예요.")
            }
            throw e
        }
    }

    suspend fun updateStartDate(coupleId: String, date: LocalDate) {
        couples.document(coupleId).update("startDate", date.toString()).await()
    }

    suspend fun updateCountFromOne(coupleId: String, value: Boolean) {
        couples.document(coupleId).update("countFromOne", value).await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toCouple(): Couple? {
        if (!exists()) return null
        val start = getString("startDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return Couple(
            id = id,
            members = (get("members") as? List<String>).orEmpty(),
            names = (get("names") as? Map<String, String>).orEmpty(),
            startDate = start,
            countFromOne = getBoolean("countFromOne") ?: true,
        )
    }
}

package com.uridul.app.ledger

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.time.YearMonth

/**
 * 가계부 서버 저장소.
 *   couples/{coupleId}/ledgerEntries/{id}  거래
 *   couples/{coupleId}/ledgerAssets/{id}   자산·부채
 *   couples/{coupleId}/ledgerMerchants/{정리한 가맹점 이름}  우리 둘 학습표 (고친 분류 기억)
 * 두 사람만 읽고 쓸 수 있다 (firestore.rules).
 */
class LedgerRepository(private val db: FirebaseFirestore) {

    private fun couple(c: String) = db.collection("couples").document(c)
    private fun entries(c: String) = couple(c).collection("ledgerEntries")
    private fun assets(c: String) = couple(c).collection("ledgerAssets")
    private fun merchants(c: String) = couple(c).collection("ledgerMerchants")

    /** 우리 둘 학습표: 정리한 가맹점 이름 → 분류 */
    fun observeLearned(coupleId: String): Flow<Map<String, String>> = callbackFlow {
        val reg = merchants(coupleId).addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { d -> d.getString("category")?.let { d.id to it } }?.toMap().orEmpty())
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyMap()) }

    /**
     * 학습표에서 이 가맹점의 분류 (없거나 못 읽으면 null).
     * [cache] 를 주면 한 번 읽은 가맹점은 다시 읽지 않는다 (쌓인 결제를 한꺼번에 올릴 때).
     */
    suspend fun learnedCategory(coupleId: String, merchant: String, cache: MutableMap<String, String?>? = null): String? {
        val key = LedgerCategories.normalize(merchant).ifEmpty { return null }
        if (cache != null && cache.containsKey(key)) return cache[key]
        val doc = runCatching { merchants(coupleId).document(key).get().await() }.getOrNull() ?: return null
        val category = doc.getString("category")?.takeIf(LedgerCategories::isExpense)
        cache?.put(key, category)
        return category
    }

    /** 이 가맹점은 다음부터 이 분류로 (두 사람이 같은 표를 쓴다) */
    fun learnCategory(coupleId: String, uid: String, merchant: String, category: String, onError: (Exception) -> Unit = {}) {
        val key = LedgerCategories.normalize(merchant).ifEmpty { return }
        if (!LedgerCategories.isExpense(category)) return
        merchants(coupleId).document(key).set(
            mapOf(
                "merchant" to merchant.trim().take(LedgerEntry.MERCHANT_MAX),
                "category" to category,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).addOnFailureListener(onError)
    }

    /** 같은 가맹점(글자 그대로)의 다른 지출 중 분류가 [category] 가 아닌 것 */
    suspend fun sameMerchantOthers(coupleId: String, merchant: String, category: String, exceptId: String): List<LedgerEntry> {
        val m = merchant.trim().take(LedgerEntry.MERCHANT_MAX).ifEmpty { return emptyList() }
        return runCatching { entries(coupleId).whereEqualTo("merchant", m).get().await().documents }.getOrDefault(emptyList())
            .mapNotNull { it.toEntry() }
            .filter { !it.deleted && it.id != exceptId && it.type == EntryType.EXPENSE && it.category != category }
    }

    /** 여러 거래의 분류만 한꺼번에 바꾸기 */
    fun recategorize(coupleId: String, uid: String, ids: List<String>, category: String, onError: (Exception) -> Unit = {}) {
        ids.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.update(entries(coupleId).document(id), mapOf("category" to category, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().addOnFailureListener(onError)
        }
    }

    /** 한 달 거래 (지운 것 빼고, 최신 먼저) */
    fun observeMonth(coupleId: String, month: YearMonth): Flow<List<LedgerEntry>> =
        listen(
            entries(coupleId)
                .whereGreaterThanOrEqualTo("date", month.atDay(1).toString())
                .whereLessThanOrEqualTo("date", month.atEndOfMonth().toString()),
        ) { list -> list.filter { !it.deleted }.sortedByDescending { it.at } }

    /** 확인 필요 거래 */
    fun observePending(coupleId: String): Flow<List<LedgerEntry>> =
        listen(entries(coupleId).whereEqualTo("status", EntryStatus.PENDING.key)) { list ->
            list.filter { !it.deleted }.sortedByDescending { it.at }
        }

    /** 자산들의 맞추기 말에 정해 둔 결제한 사람 이름 → 커플 역할 */
    suspend fun payerNames(coupleId: String): Map<String, String> =
        assets(coupleId).get().await().documents.mapNotNull { it.toAsset() }.flatMap { PayerNames.parse(it.matchers).toList() }.toMap()

    fun observeAssets(coupleId: String): Flow<List<LedgerAsset>> = callbackFlow {
        val reg = assets(coupleId).addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toAsset() }?.filter { !it.archived }?.sortedBy { it.order }.orEmpty())
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    /**
     * 기본 자산(데이트 통장·모임카드·현금)이 없으면 만든다. 이 폰에서 한 번 끝냈으면 서버에 묻지 않는다.
     * @return 모두 있음 (확인하지 못했으면 false → 다음에 다시)
     */
    suspend fun ensureDefaultAssets(context: android.content.Context, coupleId: String, uid: String): Boolean {
        if (PaymentInbox.assetsReady(context, coupleId)) return true
        for (a in LedgerAsset.defaults) {
            val ref = assets(coupleId).document(a.id)
            val exists = runCatching { ref.get().await().exists() }.getOrNull() ?: return false
            if (!exists) runCatching { ref.set(assetData(a, uid) + mapOf("createdBy" to uid, "createdAt" to FieldValue.serverTimestamp())).await() }.getOrNull() ?: return false
        }
        PaymentInbox.markAssetsReady(context, coupleId)
        return true
    }

    /**
     * 알림에서 모은 결제 올리기. 이미 있으면(두 폰이 같은 알림을 받음, 같은 3분 칸이나 바로 앞 칸) 올리지 않는다.
     * 계좌 출금 알림은 같은 금액의 카드 결제가 5분 안에 있으면 같은 돈으로 보고 건너뛴다.
     * @return 처리 끝 (올렸거나 이미 있음)
     */
    suspend fun uploadCapture(
        coupleId: String,
        uid: String,
        capture: PaymentCapture,
        paidBy: String?,
        paidByName: String?,
        learnedCache: MutableMap<String, String?>? = null,
    ): Boolean {
        val p = capture.parsed
        if (p.kind == PaymentKind.WITHDRAWAL && hasNearbyExpense(coupleId, capture)) return true
        val bucket = capture.postedAt / PaymentCapture.BUCKET_MS
        val id = PaymentCapture.keyForBucket(p, bucket)
        val prevId = PaymentCapture.keyForBucket(p, bucket - 1)
        val entry = capture.toEntry(uid, paidBy)
        // 우리 둘 학습표에 있는 가맹점이면 그 분류 (폰에서 짐작한 기본 사전보다 먼저)
        val learned = if (entry.type == EntryType.EXPENSE) learnedCategory(coupleId, entry.merchant, learnedCache) else null
        val data = entryData(entry.copy(paidByName = paidByName, category = learned ?: entry.category), uid).apply {
            put("createdBy", uid)
            put("createdAt", FieldValue.serverTimestamp())
        }
        db.runTransaction { tx ->
            val here = tx.get(entries(coupleId).document(id))
            val before = tx.get(entries(coupleId).document(prevId))
            if (!here.exists() && !before.exists()) tx.set(entries(coupleId).document(id), data)
            null
        }.await()
        // 알림에 잔액이 있으면 그 자산의 최신 잔액으로.
        // 체크카드 결제 알림의 잔액은 카드가 빠져나가는 통장의 잔액이라, 연결된 통장이 있으면 그쪽에 적는다
        val balance = p.balance
        val assetId = capture.assetId
        if (balance != null && assetId != null) {
            runCatching {
                val target = assets(coupleId).document(assetId).get().await().getString("linkedAssetId")
                    ?: LedgerAsset.defaults.firstOrNull { it.id == assetId }?.linkedAssetId
                    ?: assetId
                assets(coupleId).document(target).update(
                    mapOf("lastBalance" to balance, "lastBalanceAt" to capture.postedAt, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
                ).await()
            }
        }
        return true
    }

    private suspend fun hasNearbyExpense(coupleId: String, capture: PaymentCapture): Boolean {
        val amount = capture.parsed.amount ?: return false
        val day = LedgerEntry.dateOf(capture.postedAt).toString()
        val docs = entries(coupleId).whereEqualTo("date", day).whereEqualTo("amount", amount).get().await().documents
        return docs.mapNotNull { it.toEntry() }.any { e ->
            !e.deleted && e.type == EntryType.EXPENSE && !e.cancel && kotlin.math.abs(e.at - capture.postedAt) <= 5 * 60_000L
        }
    }

    /**
     * 저장: id 가 비어 있으면 직접 기록(새 거래), 있으면 고치기(확인 필요였으면 확정으로).
     * 고칠 때 알림 원문·출처·만든 사람은 그대로 둔다.
     */
    fun save(coupleId: String, uid: String, entry: LedgerEntry, onError: (Exception) -> Unit = {}) {
        if (entry.id.isEmpty()) {
            val data = entryData(entry.copy(status = EntryStatus.CONFIRMED), uid).apply {
                put("createdBy", uid)
                put("createdAt", FieldValue.serverTimestamp())
            }
            entries(coupleId).document().set(data).addOnFailureListener(onError)
        } else {
            entries(coupleId).document(entry.id).update(
                mapOf(
                    "status" to EntryStatus.CONFIRMED.key,
                    "type" to entry.type.key,
                    "amount" to entry.amount,
                    "cancel" to entry.cancel,
                    "at" to entry.at,
                    "date" to entry.date.toString(),
                    "category" to entry.category,
                    "merchant" to entry.merchant.trim().take(LedgerEntry.MERCHANT_MAX),
                    "memo" to entry.memo.trim().take(LedgerEntry.MEMO_MAX),
                    "assetId" to entry.assetId,
                    "paidBy" to entry.paidBy,
                    "paidByName" to entry.paidByName?.take(20),
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).addOnFailureListener(onError)
        }
    }

    /** 휴지통으로 (바로 지우지 않는다: 나중에 되살리기) */
    fun moveToTrash(coupleId: String, uid: String, id: String, onError: (Exception) -> Unit = {}) {
        entries(coupleId).document(id).update(
            mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()),
        ).addOnFailureListener(onError)
    }

    private fun listen(query: Query, transform: (List<LedgerEntry>) -> List<LedgerEntry>): Flow<List<LedgerEntry>> = callbackFlow {
        val reg = query.addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            if (snap != null) trySend(transform(snap.documents.mapNotNull { it.toEntry() }))
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }
}

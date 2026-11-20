package com.uridul.app.ledger

/**
 * 결제 취소를 원래 결제와 짝짓는다.
 *
 * 토스뱅크 알림은 취소를 "취소한 날"에 따로 보낸다. 그대로 그날 합계에서 빼면
 * 17일 아고다 결제를 19일에 취소했을 때 17일엔 쓰지 않은 돈이 지출로 남고, 19일 합계는 마이너스가 된다.
 * 그래서 취소를 원래 결제와 짝지어 둘 다 합계에서 뺀다 (처음부터 쓰지 않은 돈).
 * 짝을 못 찾은 취소(원래 결제가 기록되지 않았거나 부분 취소)만 취소한 날 합계에서 뺀다.
 *
 * 짝은 서버에 적어 둔다 (거래의 cancelPair·cancelPairAt, [LedgerRepository.linkCancels]).
 * 그래서 다른 달에 취소된 결제도 그 달 거래만 읽고 알 수 있다.
 * 아직 이어지지 않은 취소(막 올라왔거나 서버에 못 적음)는 읽은 거래 안에서 같은 조건으로 짝을 찾는다.
 *
 * 짝 조건: 같은 자산, 같은 금액, 같은 가맹점(띄어쓰기·대소문자 무시), 취소보다 앞선 결제, [WINDOW_DAYS] 안.
 * 후보가 여럿이면 취소에 가장 가까운(늦은) 결제와 짓는다. 취소는 시각 순서대로 짝을 찾는다.
 */
class CancelPairs private constructor(
    private val cancelToPayment: Map<String, String>,
    private val partnerAt: Map<String, Long>,
) {
    private val payments: Set<String> = cancelToPayment.values.toSet()

    /** 짝이 지어져 합계에서 빠지는 거래인지 (결제든 취소든) */
    fun isPaired(id: String): Boolean = id in cancelToPayment || id in payments

    /** 짝 거래의 시각 (모르면 null) */
    fun partnerAt(id: String): Long? = partnerAt[id]

    val size: Int get() = cancelToPayment.size

    companion object {
        /** 결제와 취소 사이 최대 날짜 */
        const val WINDOW_DAYS = 45L
        private const val WINDOW_MS = WINDOW_DAYS * 24 * 60 * 60 * 1000

        val NONE = CancelPairs(emptyMap(), emptyMap())

        private fun LedgerEntry.live(): Boolean = !deleted && status == EntryStatus.CONFIRMED && type == EntryType.EXPENSE

        private fun key(e: LedgerEntry): Triple<String?, Long, String> =
            Triple(e.assetId, e.amount, e.merchant.filterNot(Char::isWhitespace).lowercase())

        /** 이 취소의 원래 결제를 후보 중에서 고른다 (서버에 짝을 적을 때도 같은 조건) */
        fun pick(cancel: LedgerEntry, candidates: List<LedgerEntry>): LedgerEntry? {
            if (!cancel.cancel || !cancel.live()) return null
            val k = key(cancel)
            return candidates
                .filter { it.live() && !it.cancel && it.cancelPair == null && key(it) == k && it.at <= cancel.at && cancel.at - it.at <= WINDOW_MS }
                .maxByOrNull { it.at }
        }

        fun of(entries: List<LedgerEntry>): CancelPairs {
            val live = entries.filter { it.live() }
            val pairs = LinkedHashMap<String, String>()
            val at = HashMap<String, Long>()
            // 1) 서버에 적어 둔 짝 (상대 거래가 다른 달이라 없어도 된다)
            for (e in live) {
                val other = e.cancelPair ?: continue
                if (e.cancel) pairs[e.id] = other else pairs[other] = e.id
                e.cancelPairAt?.let { at[e.id] = it }
            }
            // 2) 아직 이어지지 않은 취소는 읽은 거래 안에서 찾는다
            val open = live.filter { !it.cancel && it.cancelPair == null && it.id !in pairs.values }.toMutableList()
            for (c in live.filter { it.cancel && it.cancelPair == null }.sortedBy { it.at }) {
                val p = pick(c, open) ?: continue
                open.remove(p)
                pairs[c.id] = p.id
                at[c.id] = p.at
                at[p.id] = c.at
            }
            return if (pairs.isEmpty()) NONE else CancelPairs(pairs, at)
        }
    }
}

/** 합계 계산 (취소 짝은 빼고) */
object LedgerTotals {
    private fun LedgerEntry.counts(): Boolean = !deleted && status == EntryStatus.CONFIRMED

    /** 지출 합계: 짝이 된 결제·취소는 빼고, 짝 없는 취소는 뺀다. 환불이 더 많으면 음수 */
    fun expense(entries: List<LedgerEntry>, pairs: CancelPairs): Long =
        entries.filter { it.counts() && it.type == EntryType.EXPENSE && !pairs.isPaired(it.id) }.sumOf { it.signedAmount }

    fun income(entries: List<LedgerEntry>): Long =
        entries.filter { it.counts() && it.type == EntryType.INCOME }.sumOf { it.signedAmount }
}

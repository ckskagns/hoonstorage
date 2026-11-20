package com.uridul.app.ledger

import java.time.LocalDate

/**
 * 결제 취소를 원래 결제와 짝짓는다.
 *
 * 토스뱅크 알림은 취소를 "취소한 날"에 따로 보낸다. 그대로 그날 합계에서 빼면
 * 17일 아고다 결제를 19일에 취소했을 때 17일엔 쓰지 않은 돈이 지출로 남고, 19일 합계는 마이너스가 된다.
 * 그래서 취소를 원래 결제와 짝지어 둘 다 합계에서 뺀다 (처음부터 쓰지 않은 돈).
 * 짝을 못 찾은 취소(원래 결제가 기록되지 않았거나 부분 취소)만 취소한 날 합계에서 뺀다.
 *
 * 짝 조건: 같은 자산, 같은 금액, 같은 가맹점(띄어쓰기·대소문자 무시), 취소보다 앞선 결제, [WINDOW_DAYS] 안.
 * 후보가 여럿이면 취소에 가장 가까운(늦은) 결제와 짓는다. 취소는 시각 순서대로 짝을 찾는다.
 */
class CancelPairs private constructor(private val cancelToPayment: Map<String, String>) {
    private val paymentToCancel: Map<String, String> = cancelToPayment.entries.associate { (c, p) -> p to c }

    /** 짝이 지어져 합계에서 빠지는 거래인지 (결제든 취소든) */
    fun isPaired(id: String): Boolean = id in cancelToPayment || id in paymentToCancel

    /** 이 결제를 취소한 거래 id */
    fun cancelOf(paymentId: String): String? = paymentToCancel[paymentId]

    /** 이 취소가 무른 결제 id */
    fun paymentOf(cancelId: String): String? = cancelToPayment[cancelId]

    val size: Int get() = cancelToPayment.size

    companion object {
        /** 결제와 취소 사이 최대 날짜 (화면은 앞뒤로 이만큼 더 읽어 다른 달 짝도 찾는다) */
        const val WINDOW_DAYS = 45L
        private const val WINDOW_MS = WINDOW_DAYS * 24 * 60 * 60 * 1000

        val NONE = CancelPairs(emptyMap())

        fun of(entries: List<LedgerEntry>): CancelPairs {
            val live = entries.filter { !it.deleted && it.status == EntryStatus.CONFIRMED && it.type == EntryType.EXPENSE }
            val cancels = live.filter { it.cancel }.sortedBy { it.at }
            if (cancels.isEmpty()) return NONE
            val open = live.filter { !it.cancel }.groupBy { key(it) }.mapValues { (_, v) -> v.toMutableList() }
            val pairs = LinkedHashMap<String, String>()
            for (c in cancels) {
                val candidates = open[key(c)] ?: continue
                val payment = candidates.filter { it.at <= c.at && c.at - it.at <= WINDOW_MS }.maxByOrNull { it.at } ?: continue
                candidates.remove(payment)
                pairs[c.id] = payment.id
            }
            return CancelPairs(pairs)
        }

        private fun key(e: LedgerEntry): Triple<String?, Long, String> =
            Triple(e.assetId, e.amount, e.merchant.filterNot(Char::isWhitespace).lowercase())

        /** 화면이 짝을 찾으려고 읽을 날짜 범위 (그 달 앞뒤로 [WINDOW_DAYS]) */
        fun readRange(first: LocalDate, last: LocalDate): ClosedRange<LocalDate> =
            first.minusDays(WINDOW_DAYS)..last.plusDays(WINDOW_DAYS)
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

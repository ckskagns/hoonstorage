package com.uridul.app.ledger

/**
 * 잔액으로 찾는 입금.
 *
 * 카카오 알림톡으로는 통장에 들어온 돈(입금) 알림이 오지 않는다. 대신 결제 알림마다 결제 뒤 잔액이 온다.
 * 지난 잔액에서 이번 결제를 빼서 나와야 할 잔액보다 실제 잔액이 많으면, 그 사이에 그만큼 들어온 것이다.
 *   예) 지난 잔액 50,000원 → 12,000원 결제 → 잔액 88,000원
 *       나와야 할 잔액 38,000원, 실제 88,000원 → 50,000원 입금
 * 이 차이를 그 결제가 있던 날의 수입으로 적는다 (결제 바로 앞 시각).
 * 실제 잔액이 더 적은 경우(알림을 못 받은 결제 등)는 수입이 아니라서 적지 않는다.
 */
object BalanceGap {
    const val PARSER = "balance-gap@1"
    const val CATEGORY = "income.deposit"
    const val MERCHANT = "입금 (잔액으로 계산)"
    const val MEMO = "입금 알림이 없어 잔액 변동으로 계산했어요"
    const val TAG = "잔액역산"
    /** 이미 어떤 잔액 차이에 반영한 직접 기록 입금 (다음 차이에서 또 빼지 않게) */
    const val USED_TAG = "잔액계산반영"

    /** 결제 바로 앞 시각에 적는다 (목록에서 결제 아래에 보이도록) */
    const val BEFORE_MS = 1_000L

    /**
     * 이번 알림 전에 들어온 돈. 없으면 null.
     * @param previous 지난 알림의 잔액
     * @param kind 이번 알림 종류
     * @param amount 이번 알림 금액
     * @param balance 이번 알림 뒤 잔액
     */
    fun missingIncome(previous: Long?, kind: PaymentKind, amount: Long?, balance: Long?): Long? {
        if (previous == null || balance == null || amount == null || amount < 0) return null
        val expected = when (kind) {
            PaymentKind.PAYMENT, PaymentKind.WITHDRAWAL -> previous - amount
            PaymentKind.CANCEL, PaymentKind.DEPOSIT -> previous + amount
            PaymentKind.IGNORE -> return null
        }
        val gap = balance - expected
        return gap.takeIf { it in 1..LedgerEntry.AMOUNT_MAX }
    }

    /**
     * 직접 기록한 입금이 이 잔액 차이를 만든 돈인지: 같은 통장, 직접 기록, 지난 잔액 알림([from]) 뒤 ~ 이번 결제([paymentAt]) 사이.
     * 직접 기록은 시각을 대충 넣기 쉬워 결제한 날과 같은 날이면 결제보다 늦어도 같은 돈으로 본다.
     * 이미 다른 차이에 반영한 것([USED_TAG])은 빼고 (같은 날 결제가 또 있으면 두 번 빼게 되므로).
     */
    fun explains(e: LedgerEntry, account: String, from: Long, paymentAt: Long): Boolean =
        !e.deleted && e.type == EntryType.INCOME && e.source == EntrySource.MANUAL && e.parser != PARSER && USED_TAG !in e.tags &&
            e.assetId == account && e.at > from &&
            (e.at <= paymentAt || e.date == LedgerEntry.dateOf(paymentAt))

    /**
     * 잔액 차이에서 이미 직접 기록한 입금을 뺀 나머지 (없으면 null).
     *   예) 차이 600,000원, 그 사이 직접 기록한 입금 600,000원 → 적지 않는다 (두 번 세지 않게)
     */
    fun remaining(gap: Long, account: String, from: Long, paymentAt: Long, recorded: List<LedgerEntry>): Long? {
        val known = explaining(account, from, paymentAt, recorded).sumOf { it.amount }
        return (gap - known).takeIf { it > 0 }
    }

    /** 이 차이에 반영할 직접 기록 입금들 (반영한 뒤 [USED_TAG]를 단다) */
    fun explaining(account: String, from: Long, paymentAt: Long, recorded: List<LedgerEntry>): List<LedgerEntry> =
        recorded.filter { explains(it, account, from, paymentAt) }

    /**
     * 잔액으로 계산한 입금이 이미 적힌 뒤에 같은 돈을 직접 기록했을 때: 그 계산 입금을 줄이거나(0이면 휴지통) 한다.
     * @return 고칠 계산 입금과 새 금액 (0 = 휴지통으로), 해당 없으면 null
     */
    fun absorb(manual: LedgerEntry, gaps: List<LedgerEntry>): Pair<LedgerEntry, Long>? {
        val g = gaps
            .filter { it.parser == PARSER && !it.deleted && it.gapFrom != null && it.assetId != null }
            .sortedBy { it.at }
            .firstOrNull { explains(manual, it.assetId!!, it.gapFrom!!, it.at + BEFORE_MS) }
            ?: return null
        return g to (g.amount - manual.amount).coerceAtLeast(0)
    }

    /** 직접 기록만큼 줄인 계산 입금의 메모 */
    fun absorbedMemo(manualAmount: Long): String = "$MEMO (직접 기록한 ${Won.text(manualAmount)} 뺌)".take(LedgerEntry.MEMO_MAX)

    /** 두 폰이 같은 결제를 올려도 한 번만 적히도록: 통장 + 이번 결제 거래 ID 로 정한다 */
    fun entryId(paymentEntryId: String): String = "g_" + paymentEntryId.removePrefix("n_")

    /** 적을 수입 거래 */
    fun entry(gap: Long, payment: LedgerEntry, accountId: String, uid: String, from: Long? = null): LedgerEntry = LedgerEntry(
        id = "",
        type = EntryType.INCOME,
        amount = gap,
        at = payment.at - BEFORE_MS,
        merchant = MERCHANT,
        memo = MEMO,
        category = CATEGORY,
        assetId = accountId,
        status = EntryStatus.CONFIRMED,
        source = EntrySource.NOTIFICATION,
        sourceApp = payment.sourceApp,
        parser = PARSER,
        balanceAfter = null,
        tags = listOf(TAG),
        createdBy = uid,
        gapFrom = from,
    )
}

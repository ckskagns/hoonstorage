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

    /** 두 폰이 같은 결제를 올려도 한 번만 적히도록: 통장 + 이번 결제 거래 ID 로 정한다 */
    fun entryId(paymentEntryId: String): String = "g_" + paymentEntryId.removePrefix("n_")

    /** 적을 수입 거래 */
    fun entry(gap: Long, payment: LedgerEntry, accountId: String, uid: String): LedgerEntry = LedgerEntry(
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
    )
}

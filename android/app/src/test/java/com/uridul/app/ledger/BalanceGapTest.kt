package com.uridul.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 입금 알림이 없을 때 결제 알림의 잔액으로 들어온 돈 계산 */
class BalanceGapTest {
    @Test
    fun paymentAfterDeposit() {
        // 지난 잔액 50,000 → 12,000원 결제 → 잔액 88,000 : 그 사이 50,000원 입금
        assertEquals(50_000L, BalanceGap.missingIncome(50_000, PaymentKind.PAYMENT, 12_000, 88_000))
    }

    @Test
    fun noGapWhenBalanceMatches() {
        assertNull(BalanceGap.missingIncome(50_000, PaymentKind.PAYMENT, 12_000, 38_000))
        assertNull(BalanceGap.missingIncome(38_000, PaymentKind.CANCEL, 12_000, 50_000))
    }

    @Test
    fun lessThanExpectedIsNotIncome() {
        // 알림을 못 받은 결제가 있으면 잔액이 더 적다: 수입이 아니다
        assertNull(BalanceGap.missingIncome(50_000, PaymentKind.PAYMENT, 12_000, 30_000))
    }

    @Test
    fun cancelAndWithdrawal() {
        assertEquals(10_000L, BalanceGap.missingIncome(38_000, PaymentKind.CANCEL, 12_000, 60_000))
        assertEquals(5_000L, BalanceGap.missingIncome(20_000, PaymentKind.WITHDRAWAL, 10_000, 15_000))
    }

    @Test
    fun unknownValues() {
        assertNull(BalanceGap.missingIncome(null, PaymentKind.PAYMENT, 12_000, 88_000))
        assertNull(BalanceGap.missingIncome(50_000, PaymentKind.PAYMENT, null, 88_000))
        assertNull(BalanceGap.missingIncome(50_000, PaymentKind.PAYMENT, 12_000, null))
        assertNull(BalanceGap.missingIncome(50_000, PaymentKind.IGNORE, 12_000, 88_000))
    }

    @Test
    fun incomeEntryOnPaymentDay() {
        val payment = LedgerEntry(id = "n_abc", type = EntryType.EXPENSE, amount = 12_000, at = 1_763_200_000_000L, sourceApp = "com.kakao.talk")
        val income = BalanceGap.entry(50_000, payment, LedgerAsset.DATE_ACCOUNT, "u1")
        assertEquals(EntryType.INCOME, income.type)
        assertEquals(50_000L, income.amount)
        assertEquals(payment.date, income.date)
        assertEquals(payment.at - BalanceGap.BEFORE_MS, income.at)
        assertEquals(LedgerAsset.DATE_ACCOUNT, income.assetId)
        assertEquals(EntryStatus.CONFIRMED, income.status)
        assertEquals("g_abc", BalanceGap.entryId("n_abc"))
    }
}

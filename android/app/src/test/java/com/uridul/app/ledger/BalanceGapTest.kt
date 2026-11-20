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

    private fun t(s: String) = java.time.LocalDateTime.parse(s).atZone(LedgerEntry.ZONE).toInstant().toEpochMilli()
    private fun manual(amount: Long, time: String, asset: String = "date_account") =
        LedgerEntry(id = "m", type = EntryType.INCOME, amount = amount, at = t(time), source = EntrySource.MANUAL, assetId = asset)

    @Test
    fun manualIncomeBeforePaymentIsNotCountedTwice() {
        // 실제 11월 17일: 지난 잔액 알림 10일, 22:06 직접 600,000원 입금 기록, 22:16 결제 → 잔액 차이 600,000원
        val from = t("2026-11-10T16:26:15")
        val pay = t("2026-11-17T22:16:19")
        assertNull(BalanceGap.remaining(600_000, "date_account", from, pay, listOf(manual(600_000, "2026-11-17T22:06:00"))))
        // 일부만 직접 기록했으면 나머지만
        assertEquals(100_000L, BalanceGap.remaining(600_000, "date_account", from, pay, listOf(manual(500_000, "2026-11-15T09:00:00"))))
        // 다른 통장·지난 알림 전·잔액 계산 입금은 빼지 않는다
        val others = listOf(
            manual(600_000, "2026-11-17T22:06:00", asset = "cash"),
            manual(600_000, "2026-11-09T10:00:00"),
            manual(600_000, "2026-11-17T22:06:00").copy(parser = BalanceGap.PARSER),
            manual(600_000, "2026-11-17T22:06:00").copy(source = EntrySource.NOTIFICATION),
        )
        assertEquals(600_000L, BalanceGap.remaining(600_000, "date_account", from, pay, others))
        // 같은 날이면 결제보다 늦게 적은 시각이어도 같은 돈
        assertNull(BalanceGap.remaining(600_000, "date_account", from, pay, listOf(manual(600_000, "2026-11-17T23:30:00"))))
        // 다음 날 기록은 이 차이와 상관없음
        assertEquals(600_000L, BalanceGap.remaining(600_000, "date_account", from, pay, listOf(manual(600_000, "2026-11-18T09:00:00"))))
    }

    @Test
    fun manualIncomeAfterGapShrinksIt() {
        val gap = LedgerEntry(
            id = "g_1", type = EntryType.INCOME, amount = 600_000, at = t("2026-11-17T22:16:18"), assetId = "date_account",
            parser = BalanceGap.PARSER, source = EntrySource.NOTIFICATION, gapFrom = t("2026-11-10T16:26:15"),
        )
        assertEquals(gap to 0L, BalanceGap.absorb(manual(600_000, "2026-11-17T12:00:00"), listOf(gap)))
        assertEquals(gap to 400_000L, BalanceGap.absorb(manual(200_000, "2026-11-12T12:00:00"), listOf(gap)))
        assertNull(BalanceGap.absorb(manual(600_000, "2026-11-19T12:00:00"), listOf(gap)))
        assertNull(BalanceGap.absorb(manual(600_000, "2026-11-17T12:00:00", asset = "cash"), listOf(gap)))
        // 지난 알림 시각을 모르는 예전 계산 입금은 건드리지 않는다
        assertNull(BalanceGap.absorb(manual(600_000, "2026-11-17T12:00:00"), listOf(gap.copy(gapFrom = null))))
    }

    @Test
    fun usedManualIncomeIsNotSubtractedAgain() {
        // 11시에 들어온 돈을 13시에 직접 기록 → 12시 결제의 차이에 반영됨(표시) → 15시 결제의 새 차이에서는 빼지 않는다
        val m = manual(30_000, "2026-11-20T13:00:00")
        val first = BalanceGap.explaining("date_account", t("2026-11-20T09:00:00"), t("2026-11-20T12:00:00"), listOf(m))
        assertEquals(listOf(m), first)
        val used = m.copy(tags = listOf(BalanceGap.USED_TAG))
        assertEquals(20_000L, BalanceGap.remaining(20_000, "date_account", t("2026-11-20T12:00:00"), t("2026-11-20T15:00:00"), listOf(used)))
        // 계산 입금을 줄이는 쪽도 이미 반영한 직접 기록은 쓰지 않는다
        val gap = LedgerEntry(
            id = "g", type = EntryType.INCOME, amount = 20_000, at = t("2026-11-20T14:59:59"), assetId = "date_account",
            parser = BalanceGap.PARSER, gapFrom = t("2026-11-20T12:00:00"),
        )
        assertNull(BalanceGap.absorb(used, listOf(gap)))
    }

    @Test
    fun absorbedMemoAndWon() {
        assertEquals("${BalanceGap.MEMO} (직접 기록한 30,000원 뺌)", BalanceGap.absorbedMemo(30_000))
        assertEquals("-51,435원", Won.signed(-51_435))
        assertEquals("+346,712원", Won.signed(346_712))
        assertEquals("0원", Won.text(0))
    }
}

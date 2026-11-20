package com.uridul.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 결제 취소를 원래 결제와 짝지어 합계에서 빼기 */
class CancelPairsTest {
    private fun at(s: String) = LocalDateTime.parse(s).atZone(LedgerEntry.ZONE).toInstant().toEpochMilli()

    private fun pay(id: String, time: String, amount: Long, merchant: String, cancel: Boolean = false, asset: String = "date_card") =
        LedgerEntry(id = id, type = EntryType.EXPENSE, amount = amount, cancel = cancel, at = at(time), merchant = merchant, assetId = asset)

    /** 실제 11월 17~19일 모임카드 기록 */
    private val november = listOf(
        LedgerEntry(id = "in", type = EntryType.INCOME, amount = 600_000, at = at("2026-11-17T22:06:00"), assetId = "date_account"),
        pay("a17", "2026-11-17T22:16:19", 398_147, "Agoda"),
        pay("b17", "2026-11-17T22:21:59", 65_400, "Agoda"),
        pay("c19", "2026-11-19T22:40:27", 398_147, "Agoda", cancel = true),
        pay("d19", "2026-11-19T22:41:32", 342_420, "Agoda"),
        pay("e19", "2026-11-19T22:49:15", 342_420, "Agoda", cancel = true),
        pay("f19", "2026-11-19T23:00:15", 346_712, "트립닷컴"),
    )

    @Test
    fun cancelPairsWithEarlierPayment() {
        val p = CancelPairs.of(november)
        assertEquals(at("2026-11-17T22:16:19"), p.partnerAt("c19"))
        assertEquals(at("2026-11-19T22:40:27"), p.partnerAt("a17"))
        assertEquals(at("2026-11-19T22:41:32"), p.partnerAt("e19"))
        assertFalse(p.isPaired("b17"))
        assertFalse(p.isPaired("f19"))
    }

    @Test
    fun dayTotalsNoLongerNegative() {
        val p = CancelPairs.of(november)
        val day19 = november.filter { it.date.dayOfMonth == 19 }
        val day17 = november.filter { it.date.dayOfMonth == 17 }
        // 전에는 19일 -51,435원(마이너스 두 번)으로 보였다
        assertEquals(346_712L, LedgerTotals.expense(day19, p))
        assertEquals(65_400L, LedgerTotals.expense(day17, p))
        // 달 합계는 짝을 지어도 같다
        val s = MonthSummary.of(november, p)
        assertEquals(65_400L + 346_712L, s.expense)
        assertEquals(600_000L, s.income)
        assertEquals(7, s.count)
    }

    @Test
    fun unmatchedRefundStaysOnItsDay() {
        // 원래 결제가 기록에 없으면 취소한 날 합계에서 뺀다 (환불이 더 많으면 음수)
        val list = listOf(pay("x", "2026-11-19T10:00:00", 10_000, "가게", cancel = true), pay("y", "2026-11-19T11:00:00", 3_000, "카페"))
        assertEquals(-7_000L, LedgerTotals.expense(list, CancelPairs.of(list)))
    }

    @Test
    fun pairNeedsSameAssetAmountMerchantAndOrder() {
        val cancel = pay("c", "2026-11-19T10:00:00", 10_000, "Agoda", cancel = true)
        assertNull(pickOf(listOf(pay("p", "2026-11-18T10:00:00", 10_000, "Agoda", asset = "cash"), cancel)))
        assertNull(pickOf(listOf(pay("p", "2026-11-18T10:00:00", 9_000, "Agoda"), cancel)))
        assertNull(pickOf(listOf(pay("p", "2026-11-18T10:00:00", 10_000, "Klook"), cancel)))
        // 취소보다 늦은 결제와는 짝짓지 않는다
        assertNull(pickOf(listOf(pay("p", "2026-11-19T11:00:00", 10_000, "Agoda"), cancel)))
        // 기간을 넘으면 짝짓지 않는다
        assertNull(pickOf(listOf(pay("p", "2026-09-01T10:00:00", 10_000, "Agoda"), cancel)))
        // 띄어쓰기·대소문자는 무시
        assertEquals("p", pickOf(listOf(pay("p", "2026-11-18T10:00:00", 10_000, " AGO DA"), cancel)))
    }

    @Test
    fun oneCancelPerPaymentClosestFirst() {
        val list = listOf(
            pay("p1", "2026-11-10T10:00:00", 5_000, "카페"),
            pay("p2", "2026-11-12T10:00:00", 5_000, "카페"),
            pay("c1", "2026-11-13T10:00:00", 5_000, "카페", cancel = true),
            pay("c2", "2026-11-14T10:00:00", 5_000, "카페", cancel = true),
            pay("c3", "2026-11-15T10:00:00", 5_000, "카페", cancel = true),
        )
        val p = CancelPairs.of(list)
        assertEquals(at("2026-11-12T10:00:00"), p.partnerAt("c1"))
        assertEquals(at("2026-11-10T10:00:00"), p.partnerAt("c2"))
        assertFalse(p.isPaired("c3"))
        assertTrue(p.isPaired("p1"))
        assertEquals(-5_000L, LedgerTotals.expense(list, p))
    }

    @Test
    fun storedPairWorksWithoutTheOtherMonth() {
        // 10월 결제를 11월에 취소: 서버에 적힌 짝만으로 두 달 모두 합계에서 빠진다 (상대 달 거래는 읽지 않음)
        val oct = pay("oct", "2026-10-28T10:00:00", 20_000, "Agoda").copy(cancelPair = "nov", cancelPairAt = at("2026-11-03T10:00:00"))
        val nov = pay("nov", "2026-11-03T10:00:00", 20_000, "Agoda", cancel = true).copy(cancelPair = "oct", cancelPairAt = oct.at)
        val octPairs = CancelPairs.of(listOf(oct))
        val novPairs = CancelPairs.of(listOf(nov))
        assertEquals(0L, MonthSummary.of(listOf(oct), octPairs).expense)
        assertEquals(0L, MonthSummary.of(listOf(nov), novPairs).expense)
        assertEquals(nov.at, octPairs.partnerAt("oct"))
    }

    @Test
    fun storedPairWinsOverGuess() {
        // 서버 짝이 p1 이면 더 가까운 p2 가 있어도 p2 는 그대로 지출
        val p1 = pay("p1", "2026-11-10T10:00:00", 5_000, "카페").copy(cancelPair = "c")
        val p2 = pay("p2", "2026-11-12T10:00:00", 5_000, "카페")
        val c = pay("c", "2026-11-13T10:00:00", 5_000, "카페", cancel = true).copy(cancelPair = "p1")
        val p = CancelPairs.of(listOf(p1, p2, c))
        assertTrue(p.isPaired("p1"))
        assertFalse(p.isPaired("p2"))
        assertEquals(5_000L, LedgerTotals.expense(listOf(p1, p2, c), p))
    }

    @Test
    fun pickSkipsAlreadyLinkedPayment() {
        val linked = pay("p", "2026-11-18T10:00:00", 10_000, "Agoda").copy(cancelPair = "other")
        val cancel = pay("c", "2026-11-19T10:00:00", 10_000, "Agoda", cancel = true)
        assertNull(CancelPairs.pick(cancel, listOf(linked)))
    }

    @Test
    fun noteText() {
        val p = CancelPairs.of(november)
        assertEquals("19일 취소됨", p.note(november.first { it.id == "a17" }))
        assertEquals("17일 결제 취소", p.note(november.first { it.id == "c19" }))
        assertNull(p.note(november.first { it.id == "f19" }))
        // 다른 달 짝은 달까지
        val oct = pay("oct", "2026-10-28T10:00:00", 20_000, "Agoda").copy(cancelPair = "nov", cancelPairAt = at("2026-11-03T10:00:00"))
        assertEquals("11월 3일 취소됨", CancelPairs.of(listOf(oct)).note(oct))
        // 시각을 모르는 짝
        val bare = pay("x", "2026-11-03T10:00:00", 20_000, "Agoda", cancel = true).copy(cancelPair = "y")
        assertEquals("결제 취소", CancelPairs.of(listOf(bare)).note(bare))
    }

    private fun pickOf(list: List<LedgerEntry>): String? = CancelPairs.pick(list.last { it.cancel }, list)?.id

    /** 일기장 "이날 데이트 비용": 가계부 날짜 머리글과 같은 계산 (취소 짝 빼고, 쓴 돈이 있는 날만) */
    @Test
    fun expenseByDayMatchesLedgerHeaders() {
        val byDay = LedgerTotals.expenseByDay(november)
        // 17일: 아고다 398,147 은 19일에 취소되어 빠지고 65,400 만
        assertEquals(65_400L, byDay[java.time.LocalDate.parse("2026-11-17")])
        // 19일: 같은 날 결제·취소(342,420)는 빠지고 트립닷컴 346,712 만
        assertEquals(346_712L, byDay[java.time.LocalDate.parse("2026-11-19")])
        // 입금만 있는 날·쓴 돈 없는 날은 없음
        assertEquals(2, byDay.size)
        // 확인 필요(PENDING)·지운 거래는 세지 않는다
        val more = november + pay("g20", "2026-11-20T12:00:00", 9_000, "카페").copy(status = EntryStatus.PENDING) +
            pay("h20", "2026-11-20T13:00:00", 5_000, "편의점").copy(deleted = true)
        assertNull(LedgerTotals.expenseByDay(more)[java.time.LocalDate.parse("2026-11-20")])
    }
}

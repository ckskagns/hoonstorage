package com.uridul.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCategoriesTest {

    private fun guess(m: String?, learned: Map<String, String> = emptyMap()) = LedgerCategories.guess(m, learned)

    @Test
    fun `알림에 오는 영문·회사 이름도 맞춘다`() {
        assertEquals("date.travel", guess("Agoda"))
        assertEquals("date.travel", guess("AGODA.COM"))
        assertEquals("date.travel", guess("Booking.com"))
        assertEquals("date.travel", guess("AIRBNB * HM123"))
        assertEquals("food.delivery", guess("(주)우아한형제들"))
        assertEquals("food.delivery", guess("우아한 형제들"))
        assertEquals("life.subscription", guess("NETFLIX.COM"))
        assertEquals("food.cafe", guess("STARBUCKS 선릉점"))
        assertEquals("shopping.online", guess("쿠팡(주)"))
    }

    @Test
    fun `여러 말이 맞으면 긴 말이 이긴다`() {
        assertEquals("life.subscription", guess("쿠팡플레이"))
        assertEquals("food.delivery", guess("쿠팡이츠"))
        assertEquals("shopping.online", guess("쿠팡"))
        assertEquals("shopping.fashion", guess("스파오 강남점"))
        assertEquals("food.meal", guess("꽃게탕집"))
        assertEquals("date.gift", guess("꽃집"))
        assertEquals("date.travel", guess("고속버스 터미널"))
    }

    @Test
    fun `짧은 영문은 다른 말 속에 들어 있으면 안 맞는다`() {
        assertEquals("shopping.living", guess("CU 선릉점"))
        assertEquals("shopping.living", guess("CU선릉점"))
        assertEquals("food.drink", guess("WINE BAR 101"))
        assertEquals("date.travel", guess("KTX 예매"))
        assertEquals("life.subscription", guess("SKT 요금"))
        // CURRY 의 CU, BARBER 의 BAR 는 아님
        assertEquals(LedgerCategories.DEFAULT_EXPENSE, guess("CURRY HOUSE"))
        assertEquals(LedgerCategories.DEFAULT_EXPENSE, guess("BARBER SHOP"))
    }

    @Test
    fun `모르는 가게·빈 이름은 기타`() {
        assertEquals(LedgerCategories.DEFAULT_EXPENSE, guess("test0시험상점"))
        assertEquals(LedgerCategories.DEFAULT_EXPENSE, guess(""))
        assertEquals(LedgerCategories.DEFAULT_EXPENSE, guess(null))
    }

    @Test
    fun `학습표가 기본 사전보다 먼저`() {
        val learned = mapOf(LedgerCategories.normalize("test0 우리동네 단골집") to "food.drink", LedgerCategories.normalize("Agoda") to "date.play")
        assertEquals("food.drink", guess("test0 우리동네 단골집", learned))
        // 띄어쓰기·대소문자가 달라도 같은 가게
        assertEquals("food.drink", guess("TEST0우리동네단골집", learned))
        assertEquals("date.play", guess("Agoda", learned))
        // 학습표에 없는 가게는 사전
        assertEquals("food.cafe", guess("스타벅스", learned))
    }

    @Test
    fun `학습표에 이상한 분류가 있으면 쓰지 않는다`() {
        val learned = mapOf(LedgerCategories.normalize("스타벅스") to "income.deposit", LedgerCategories.normalize("test0가게") to "nope.nope")
        assertEquals("food.cafe", guess("스타벅스", learned))
        assertEquals(LedgerCategories.DEFAULT_EXPENSE, guess("test0가게", learned))
    }

    @Test
    fun `가맹점 이름 정리`() {
        assertEquals("우아한형제들", LedgerCategories.normalize("(주)우아한 형제들"))
        assertEquals("우아한형제들", LedgerCategories.normalize("주식회사 우아한형제들"))
        assertEquals("BOOKINGCOM", LedgerCategories.normalize("Booking.com"))
        assertEquals("", LedgerCategories.normalize("  "))
        assertTrue(LedgerCategories.normalize("가".repeat(200)).length <= LedgerCategories.KEY_MAX)
        // 문서 이름에 쓸 수 없는 글자가 남지 않는다
        assertFalse(LedgerCategories.normalize("a/b.c__d").any { it == '/' || it == '.' || it == '_' })
    }

    private fun entry(merchant: String, category: String, type: EntryType = EntryType.EXPENSE) =
        LedgerEntry(id = "test0", type = type, amount = 1000, at = 0, merchant = merchant, category = category, createdBy = "test0a")

    @Test
    fun `고친 거래는 분류가 바뀌었을 때만 기억한다`() {
        val before = entry("test0단골집", "etc.etc")
        assertTrue(LedgerCategories.shouldLearn(before, before.copy(category = "food.drink"), emptyMap()))
        // 금액만 고침
        assertFalse(LedgerCategories.shouldLearn(before, before.copy(amount = 2000), emptyMap()))
        // 가맹점이 없으면 기억할 데가 없음
        assertFalse(LedgerCategories.shouldLearn(before.copy(merchant = ""), before.copy(merchant = " ", category = "food.drink"), emptyMap()))
        // 수입으로 바꾼 거래는 지출 학습표에 넣지 않음
        assertFalse(LedgerCategories.shouldLearn(before, before.copy(type = EntryType.INCOME, category = "income.deposit"), emptyMap()))
    }

    @Test
    fun `새 거래는 짐작과 다르게 골랐을 때만 기억한다`() {
        // 사전 짐작(카페) 그대로면 기억하지 않음
        assertFalse(LedgerCategories.shouldLearn(null, entry("스타벅스", "food.cafe"), emptyMap()))
        // 짐작과 다르게 고름
        assertTrue(LedgerCategories.shouldLearn(null, entry("스타벅스", "date.gift"), emptyMap()))
        // 이미 학습표대로면 다시 쓰지 않음
        val learned = mapOf(LedgerCategories.normalize("스타벅스") to "date.gift")
        assertFalse(LedgerCategories.shouldLearn(null, entry("스타벅스", "date.gift"), learned))
    }

    @Test
    fun `수입 분류는 지출 학습에 쓰지 않는다`() {
        assertTrue(LedgerCategories.isExpense("food.cafe"))
        assertFalse(LedgerCategories.isExpense("income.deposit"))
        assertFalse(LedgerCategories.isExpense("없는.분류"))
        assertFalse(LedgerCategories.isExpense(null))
    }
}

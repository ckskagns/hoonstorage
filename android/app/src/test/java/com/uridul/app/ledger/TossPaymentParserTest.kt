package com.uridul.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 토스 결제 알림 글 풀기. 공개된 실제 알림 형식(카드 알림·토스페이·취소·포인트·광고)과
 * 데이트 통장(토스뱅크 chch모임통장) 경우.
 */
class TossPaymentParserTest {
    private fun n(title: String?, text: String?, sub: String? = null, big: String? = null) =
        NotificationText(PaymentSources.TOSS, title, text, sub, big)
    private fun parse(title: String?, text: String?, sub: String? = null, big: String? = null) = TossPaymentParser.parse(n(title, text, sub, big))
    private val matchers = PaymentInbox.defaultMatchers()

    @Test
    fun cardAlert() {
        val p = parse("카드 알림 받기", "26,400원 결제", "하나체크 | Adobe(일시불)")
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(26_400L, p.amount)
        assertEquals("하나체크", p.method)
        assertEquals("Adobe", p.merchant)
        assertEquals("일시불", p.payType)
        assertTrue(p.confident)
    }

    @Test
    fun cardAlertInBigTextAndInstallment() {
        val p = parse("카드 알림 받기", null, big = "12,000원 결제\n국민카드 | 김밥천국(일시불)")
        assertEquals(12_000L, p.amount)
        assertEquals("김밥천국", p.merchant)
        val i = parse("카드 알림 받기", "1,200,000원 결제", "신한카드 | 하이마트(할부 3개월)")
        assertEquals("하이마트", i.merchant)
        assertEquals(3, i.installmentMonths)
        val j = parse("카드 알림 받기", "300,000원 결제", "신한카드|하이마트(3개월)")
        assertEquals(3, j.installmentMonths)
        // 가맹점 이름 안 괄호는 남긴다
        assertEquals("스타벅스(강남역점)", parse("카드 알림 받기", "5,600원 결제", "하나체크 | 스타벅스(강남역점)(일시불)").merchant)
    }

    @Test
    fun tossPay() {
        val p = parse("17,000원 결제 완료", "토스페이머니 · 구글페이먼트코리아 유한회사", big = "토스페이머니 · 구글페이먼트코리아 유한회사\n보너스 포인트 적립 가능")
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(17_000L, p.amount)
        assertEquals("토스페이머니", p.method)
        assertEquals("구글페이먼트코리아 유한회사", p.merchant)
    }

    @Test
    fun cancels() {
        val a = parse("17,000원 결제 취소", "토스페이머니 · 구글페이먼트코리아 유한회사")
        assertEquals(PaymentKind.CANCEL, a.kind)
        assertEquals(17_000L, a.amount)
        val b = parse("카드 알림 받기", "26,400원 결제 취소", "하나체크 | Adobe(일시불)")
        assertEquals(PaymentKind.CANCEL, b.kind)
        assertEquals("Adobe", b.merchant)
    }

    @Test
    fun ignoresPointsAdsAndOthers() {
        assertEquals(PaymentKind.IGNORE, parse("포인트 적립", "토스페이 결제 혜택으로 14원 받았어요").kind)
        assertEquals(PaymentKind.IGNORE, parse("토스", "오늘의 혜택을 확인하세요", "광고").kind)
        assertEquals(PaymentKind.IGNORE, parse("만보기", "6 걸음").kind)
        assertEquals(PaymentKind.IGNORE, parse("(광고) 이번 주 쿠폰", "최대 5,000원 할인 쿠폰이 도착했어요").kind)
        assertFalse(parse("포인트 적립", "토스페이 결제 혜택으로 14원 받았어요").recordable)
    }

    @Test
    fun meetingAccountPayment() {
        // 데이트 통장(chch모임통장)의 모임카드 결제: 결제 수단 자리에 통장 이름이 오는 경우
        val text = n("카드 알림 받기", "38,000원 결제", "chch모임통장 | 선릉감자탕(일시불)")
        val c = PaymentCapture.from(text, TossPaymentParser, 1_000L, matchers)!!
        assertEquals(LedgerAsset.DATE_ACCOUNT, c.assetId)
        val e = c.toEntry("me", null)
        assertEquals(EntryType.EXPENSE, e.type)
        assertEquals(38_000L, e.amount)
        assertEquals("선릉감자탕", e.merchant)
        assertEquals("food.meal", e.category)
        assertEquals(EntryStatus.CONFIRMED, e.status)
        // 모임카드라고 오는 경우
        val card = PaymentCapture.from(n("카드 알림 받기", "6,500원 결제", "토스뱅크 모임카드 | 스타벅스(일시불)"), TossPaymentParser, 1_000L, matchers)!!
        assertEquals(LedgerAsset.DATE_CARD, card.assetId)
        assertEquals("food.cafe", card.toEntry("me", null).category)
    }

    @Test
    fun personalCardStaysOnPhone() {
        // 개인 카드 결제는 커플 자산과 맞지 않아 서버에 올리지 않는다
        val c = PaymentCapture.from(n("카드 알림 받기", "26,400원 결제", "하나체크 | Adobe(일시불)"), TossPaymentParser, 1_000L, matchers)!!
        assertNull(c.assetId)
    }

    @Test
    fun meetingAccountDepositAndWithdrawal() {
        // 토스뱅크 입출금은 공개 예시가 없어 넓게 읽고 확인 필요로 둔다
        val d = TossPaymentParser.parse(n("chch모임통장", "50,000원 입금 | 나나", big = "잔액 320,000원"))
        assertEquals(PaymentKind.DEPOSIT, d.kind)
        assertEquals(50_000L, d.amount)
        assertEquals(320_000L, d.balance)
        assertFalse(d.confident)
        val dc = PaymentCapture.from(n("chch모임통장", "50,000원 입금 | 나나", big = "잔액 320,000원"), TossPaymentParser, 1_000L, matchers)!!
        assertEquals(LedgerAsset.DATE_ACCOUNT, dc.assetId)
        assertEquals(EntryType.INCOME, dc.toEntry("me", null).type)
        assertEquals(EntryStatus.PENDING, dc.toEntry("me", null).status)

        val w = TossPaymentParser.parse(n("출금 30,000원", "chch모임통장에서 꽃집으로"))
        assertEquals(PaymentKind.WITHDRAWAL, w.kind)
        assertEquals(30_000L, w.amount)
        assertEquals("chch모임통장", w.method)
    }

    @Test
    fun payerName() {
        val p = parse("나나님이 모임카드로 결제했어요", "12,000원 결제", "chch모임통장 | 김밥천국(일시불)")
        assertEquals("나나", p.payerName)
        assertNull(parse("카드 알림 받기", "12,000원 결제", "하나체크 | 김밥천국").payerName)
    }

    @Test
    fun dedupeKeys() {
        val p = parse("카드 알림 받기", "38,000원 결제", "chch모임통장 | 선릉감자탕(일시불)")
        // 같은 3분 칸이면 같은 키 (두 폰이 함께 받아도 한 건)
        assertEquals(PaymentCapture.dedupeKey(p, 60_000L), PaymentCapture.dedupeKey(p, 170_000L))
        assertNotEquals(PaymentCapture.dedupeKey(p, 60_000L), PaymentCapture.dedupeKey(p.copy(amount = 38_001), 60_000L))
        assertNotEquals(PaymentCapture.dedupeKey(p, 60_000L), PaymentCapture.dedupeKey(p.copy(kind = PaymentKind.CANCEL), 60_000L))
    }

    @Test
    fun captureSurvivesStorage() {
        val c = PaymentCapture.from(n("카드 알림 받기", "38,000원 결제", "chch모임통장 | 선릉감자탕(할부 2개월)"), TossPaymentParser, 123L, matchers)!!
        val back = PaymentInbox.readCaptures(PaymentInbox.writeCaptures(listOf(c))).single()
        assertEquals(c, back)
    }

    @Test
    fun monthSummary() {
        val e = { amount: Long, cancel: Boolean, type: EntryType, status: EntryStatus ->
            LedgerEntry("x", type, amount, cancel, 0L, status = status)
        }
        val s = MonthSummary.of(listOf(
            e(10_000, false, EntryType.EXPENSE, EntryStatus.CONFIRMED),
            e(3_000, true, EntryType.EXPENSE, EntryStatus.CONFIRMED),
            e(50_000, false, EntryType.INCOME, EntryStatus.CONFIRMED),
            e(99_000, false, EntryType.EXPENSE, EntryStatus.PENDING),
        ))
        assertEquals(7_000L, s.expense)
        assertEquals(50_000L, s.income)
    }
}

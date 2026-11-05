package com.uridul.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 카카오톡 "토스뱅크" 알림톡 글 풀기 (숫자·가맹점은 예시) */
class KakaoTossBankParserTest {
    private fun kakao(body: String, title: String = "토스뱅크") =
        NotificationText(PaymentSources.KAKAO, title = title, text = body.lineSequence().first(), bigText = body)

    private val payment = """
        [토스뱅크] 체크카드 국내 결제
        최*○님의 CHCH 카드
        12,300원 결제 | 테스트상점
        잔액 45,600원
    """.trimIndent()

    @Test
    fun cardPayment() {
        val p = KakaoTossBankParser.parse(kakao(payment))
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(12_300L, p.amount)
        assertEquals("테스트상점", p.merchant)
        assertEquals("CHCH 카드", p.method)
        assertEquals("최*○", p.payerName)
        assertEquals(45_600L, p.balance)
        assertTrue(p.confident)
        assertTrue(p.recordable)
    }

    @Test
    fun cancel() {
        val p = KakaoTossBankParser.parse(kakao(payment.replace("결제 | 테스트상점", "결제 취소 | 테스트상점").replace("국내 결제", "결제 취소")))
        assertEquals(PaymentKind.CANCEL, p.kind)
        assertEquals(12_300L, p.amount)
        assertEquals("테스트상점", p.merchant)
    }

    @Test
    fun noticeWithoutAmountIsIgnored() {
        val p = KakaoTossBankParser.parse(kakao("안전한 서비스 이용을 위해, 고객님의 기기에 본인 확인용 정보가 등록되었어요."))
        assertFalse(p.recordable)
    }

    @Test
    fun onlyTossBankChannelIsRead() {
        val kakao = PaymentSources.sourceFor(PaymentSources.KAKAO)!!
        assertTrue(kakao.acceptsSender("토스뱅크", null))
        assertTrue(kakao.acceptsSender(null, "토스뱅크"))
        // 개인 대화·단체방·다른 채널은 본문을 열어 보지 않는다
        assertFalse(kakao.acceptsSender("친구", null))
        assertFalse(kakao.acceptsSender("토스뱅크 친구들", "단체방"))
        assertFalse(kakao.acceptsSender(null, null))
        // 토스 앱은 그대로 모두 읽는다
        assertTrue(PaymentSources.sourceFor(PaymentSources.TOSS)!!.acceptsSender(null, null))
        assertNull(PaymentSources.sourceFor("com.example.chat"))
        // 이름만 같은 대화에서 온 글이라도 [토스뱅크] 본문이 아니면 기록하지 않는다
        assertFalse(KakaoTossBankParser.parse(kakao("내일 12,000원 결제할게")).recordable)
    }

    @Test
    fun onlyChchCard() {
        assertTrue(PaymentFilter.accepts(kakao(payment)))
        assertTrue(PaymentFilter.accepts(kakao(payment.replace("CHCH 카드", "chch카드"))))
        assertTrue(PaymentFilter.accepts(NotificationText(PaymentSources.TOSS, title = "카드 알림 받기", text = "12,300원 결제", subText = "CHCH 모임카드 | 테스트상점(일시불)")))
        // 개인 체크카드·다른 카드·계좌 알림은 기록하지 않는다
        assertFalse(PaymentFilter.accepts(kakao(payment.replace("CHCH 카드", "체크카드"))))
        assertFalse(PaymentFilter.accepts(NotificationText(PaymentSources.TOSS, title = "카드 알림 받기", text = "12,300원 결제", subText = "하나체크 | 테스트상점(일시불)")))
        assertFalse(PaymentFilter.accepts(NotificationText(PaymentSources.TOSS, title = "12,300원 입금", text = "생활비통장")))
        // 데이트 통장(CHCH 모임통장) 입출금은 기록한다
        assertTrue(PaymentFilter.accepts(NotificationText(PaymentSources.TOSS, title = "12,300원 입금", text = "chch모임통장")))
        assertTrue(PaymentFilter.accepts(kakao("[토스뱅크] 입금\n차*○님의 CHCH 모임통장\n50,000원 입금 | 홍길동\n잔액 95,600원")))
    }

    @Test
    fun accountTransfers() {
        val dep = KakaoTossBankParser.parse(kakao("[토스뱅크] 입금\n차*○님의 CHCH 모임통장\n50,000원 입금 | 홍길동\n잔액 95,600원"))
        assertEquals(PaymentKind.DEPOSIT, dep.kind)
        assertEquals(50_000L, dep.amount)
        assertEquals("홍길동", dep.merchant)
        assertEquals("CHCH 모임통장", dep.method)
        assertEquals(95_600L, dep.balance)
        assertFalse(dep.confident)
        val out = KakaoTossBankParser.parse(kakao("[토스뱅크] 출금\n최*○님의 CHCH 모임통장\n20,000원 출금 | 김철수\n잔액 75,600원"))
        assertEquals(PaymentKind.WITHDRAWAL, out.kind)
        assertEquals(20_000L, out.amount)
        // 통장 알림은 데이트 통장으로
        val c = PaymentCapture.from(kakao("[토스뱅크] 입금\n차*○님의 CHCH 모임통장\n50,000원 입금 | 홍길동"), KakaoTossBankParser, 1_000_000L, PaymentInbox.defaultMatchers())!!
        assertEquals(LedgerAsset.DATE_ACCOUNT, c.assetId)
    }

    @Test
    fun goesToDateCard() {
        val c = PaymentCapture.from(kakao(payment), KakaoTossBankParser, 1_000_000L, PaymentInbox.defaultMatchers())!!
        assertEquals(LedgerAsset.DATE_CARD, c.assetId)
        assertEquals(PaymentSources.KAKAO, c.packageName)
    }
}

/** 결제한 사람 이름 연결 (이름은 예시) */
class PayerNamesTest {
    @Test
    fun parseTags() {
        val m = listOf("모임카드", "chch카드", PayerNames.tag(PayerNames.CREATOR, "김*수"), "@partner=이*영", "@other=박*", "@creator=")
        assertEquals(mapOf("김*수" to "creator", "이*영" to "partner"), PayerNames.parse(m))
        assertTrue(PayerNames.isTag("@partner=이*영"))
        assertFalse(PayerNames.isTag("모임카드"))
    }

    @Test
    fun phoneRoleDependsOnWhoIAm() {
        // 만든 사람 폰: creator = 나, partner = 상대 / 들어온 사람 폰: 반대
        assertEquals(PaymentInbox.PAYER_ME, PayerNames.phoneRole(PayerNames.CREATOR, iAmCreator = true))
        assertEquals(PaymentInbox.PAYER_PARTNER, PayerNames.phoneRole(PayerNames.PARTNER, iAmCreator = true))
        assertEquals(PaymentInbox.PAYER_ME, PayerNames.phoneRole(PayerNames.PARTNER, iAmCreator = false))
        assertEquals(PaymentInbox.PAYER_PARTNER, PayerNames.phoneRole(PayerNames.CREATOR, iAmCreator = false))
    }
}

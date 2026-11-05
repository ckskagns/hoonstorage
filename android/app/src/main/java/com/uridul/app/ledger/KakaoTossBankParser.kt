package com.uridul.app.ledger

/**
 * 카카오톡 "토스뱅크" 알림톡 글 풀기. 실제 형식(2026-11, 숫자는 예시):
 *   [토스뱅크] 체크카드 국내 결제
 *   최*○님의 CHCH 카드
 *   12,300원 결제 | 가맹점
 *   잔액 45,600원
 *  - "○○님의 (모임) 카드" → 결제한 사람(이름 일부가 가려져 옴)·결제 수단
 *  - 취소면 "결제 취소"
 *  - 통장 입출금 "N원 입금/출금 | 상대" 는 실제 문구를 아직 못 봐서 확인 필요로 둔다 (결제한 사람 이름은 붙이지 않음)
 *  - 그 밖의 알림은 토스 글 풀기로 넓게 읽고 확인 필요로 둔다
 *  - 금액 없는 알림(본인 확인 안내 등)은 기록하지 않는다
 */
object KakaoTossBankParser : PaymentNotificationParser {
    override val id = "kakao-tossbank@1"

    private val PREFIX = "[토스뱅크]"
    private val PAYMENT = Regex("""(\d[\d,]*)\s*원\s*(?:결제|승인)(\s*취소)?\s*(?:[|ㅣ｜]\s*(.+))?$""")
    private val TRANSFER = Regex("""(\d[\d,]*)\s*원\s*(입금|출금|이체|송금)\s*(?:[|ㅣ｜]\s*(.+))?$""")
    private val CARD_OWNER = Regex("""^(.{1,12}?)님의\s+(.+?)\s*$""")
    private val BALANCE = Regex("""잔액\s*:?\s*(\d[\d,]*)\s*원""")

    override fun parse(n: NotificationText): ParsedPayment {
        val lines = n.lines
        // 토스뱅크 알림톡 본문이 아니면 (대화방 이름만 같은 경우 등) 읽지 않는다
        if (lines.none { it.startsWith(PREFIX) }) return ParsedPayment(PaymentKind.IGNORE, parser = id)
        val head = lines.first { it.startsWith(PREFIX) }
        val body = lines.filter { it != n.title?.trim() }
        val payLine = body.firstNotNullOfOrNull { l -> PAYMENT.find(l) }
        val owner = body.firstNotNullOfOrNull { l -> CARD_OWNER.find(l) }
        val balance = body.firstNotNullOfOrNull { l -> BALANCE.find(l)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
        val transfer = if (payLine == null) body.firstNotNullOfOrNull { l -> TRANSFER.find(l) } else null
        if (transfer != null) {
            // 통장 입출금: "N원 입금 | 보낸 사람" / "N원 출금 | 받는 사람". 아직 실제 문구를 못 봐서 확인 필요로 둔다
            val deposit = transfer.groupValues[2] == "입금"
            return ParsedPayment(
                kind = if (deposit) PaymentKind.DEPOSIT else PaymentKind.WITHDRAWAL,
                amount = transfer.groupValues[1].replace(",", "").toLongOrNull(),
                merchant = transfer.groupValues[3].trim().ifEmpty { null }?.take(LedgerEntry.MERCHANT_MAX),
                method = owner?.groupValues?.get(2),
                // "○○님의 통장"은 통장 주인이라 돈을 넣은·보낸 사람과 다를 수 있어 이름을 붙이지 않는다
                payerName = null,
                balance = balance,
                confident = false,
                parser = id,
            )
        }
        if (payLine == null) {
            // 그 밖의 알림은 토스 글 풀기로 넓게 읽고 확인 필요로
            val stripped = n.copy(title = null, text = body.joinToString("\n") { it.removePrefix(PREFIX).trim() }, subText = null, bigText = null, textLines = null, messages = null)
            val loose = TossPaymentParser.parse(stripped)
            return loose.copy(confident = false, payerName = owner?.groupValues?.get(1) ?: loose.payerName, balance = balance ?: loose.balance, parser = id)
        }
        val cancel = payLine.groupValues[2].isNotBlank() || "취소" in head
        val amount = payLine.groupValues[1].replace(",", "").toLongOrNull()
        val merchant = payLine.groupValues[3].trim().ifEmpty { null }
        return ParsedPayment(
            kind = if (cancel) PaymentKind.CANCEL else PaymentKind.PAYMENT,
            amount = amount,
            merchant = merchant?.take(LedgerEntry.MERCHANT_MAX),
            method = owner?.groupValues?.get(2),
            payerName = owner?.groupValues?.get(1),
            balance = balance,
            confident = amount != null && merchant != null,
            parser = id,
        )
    }
}

package com.uridul.app.ledger

// 결제 알림 글 풀기. 앱마다 글 풀기를 하나씩 두고 PaymentSources 에 등록한다 (카드사 앱·문자도 같은 틀).
// 글 풀기를 고치면 버전(id)을 올린다. 거래마다 원문을 보관하므로 나중에 다시 풀 수 있다.

/** 알림 하나의 글 (앱·OS 마다 어느 칸에 무엇이 오는지 달라서 모든 칸을 모은다) */
data class NotificationText(
    val packageName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val textLines: String? = null,
    /** 대화형 알림(카카오톡 등)의 마지막 메시지 글 */
    val messages: String? = null,
) {
    /** 모든 칸의 줄 (앞뒤 공백 정리, 빈 줄·겹치는 줄 빼고) */
    val lines: List<String>
        get() = listOfNotNull(title, text, subText, bigText, textLines, messages)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** 보관용 원문 */
    fun raw(): RawText = RawText(title.orEmpty(), lines.drop(if (title.isNullOrBlank()) 0 else 1).joinToString("\n"))
}

/** 알림 종류 */
enum class PaymentKind {
    /** 카드·간편결제 결제 */
    PAYMENT,
    /** 결제 취소·환불 */
    CANCEL,
    /** 계좌로 들어온 돈 */
    DEPOSIT,
    /** 계좌에서 나간 돈 (이체·출금. 카드 결제 출금과 겹칠 수 있어 확인 필요로 둔다) */
    WITHDRAWAL,
    /** 포인트·캐시백·혜택·광고 등 기록하지 않는 알림 */
    IGNORE,
}

/** 알림에서 뽑은 내용 */
data class ParsedPayment(
    val kind: PaymentKind,
    val amount: Long? = null,
    /** 가맹점 (입출금이면 보낸·받은 사람) */
    val merchant: String? = null,
    /** 결제 수단·계좌 이름 ("하나체크", "토스페이머니", "chch모임통장") */
    val method: String? = null,
    /** "일시불", "할부 3개월" */
    val payType: String? = null,
    val installmentMonths: Int = 0,
    /** "나나님이 결제" 처럼 쓴 사람 이름이 있으면 */
    val payerName: String? = null,
    /** 결제·입출금 뒤 잔액 */
    val balance: Long? = null,
    /** 형식이 확실하면 true (아니면 확인 필요로) */
    val confident: Boolean = false,
    /** 어느 글 풀기가 풀었는지 ("toss@1") */
    val parser: String = "",
) {
    val recordable: Boolean get() = kind != PaymentKind.IGNORE && amount != null && amount > 0 && amount <= LedgerEntry.AMOUNT_MAX
}

interface PaymentNotificationParser {
    /** "toss@1" 처럼 이름@버전 */
    val id: String
    fun parse(n: NotificationText): ParsedPayment
}

/**
 * 토스 알림 글 풀기. 공개된 실제 형식(2026년 기준):
 *  - 카드 알림:   title="카드 알림 받기"  text="26,400원 결제"  subText="하나체크 | Adobe(일시불)"
 *                 (취소면 "26,400원 결제 취소", 할부면 "(할부 3개월)" 또는 "(3개월)")
 *  - 토스페이:    title="17,000원 결제 완료"  text="토스페이머니 · 구글페이먼트코리아 유한회사" (+ "보너스 포인트 적립 가능")
 *                 취소면 title="17,000원 결제 취소"
 *  - 포인트:      title="포인트 적립"  text="토스페이 결제 혜택으로 14원 받았어요"  → 기록 안 함
 *  - 광고·혜택:   subText="광고" 등 → 기록 안 함
 *  - 토스뱅크 입출금: 공개 예시가 없어 "N원 입금/출금" 과 계좌 이름(○○통장)으로 넓게 읽고 확인 필요로 둔다
 */
object TossPaymentParser : PaymentNotificationParser {
    override val id = "toss@1"

    private val AMOUNT = Regex("""(\d[\d,]*)\s*원""")
    private val PAYMENT_LINE = Regex("""(\d[\d,]*)\s*원\s*(결제|승인|사용)""")
    private val DEPOSIT_LINE = Regex("""(\d[\d,]*)\s*원\s*(입금|들어왔)|입금\s*:?\s*(\d[\d,]*)\s*원""")
    private val WITHDRAW_LINE = Regex("""(\d[\d,]*)\s*원\s*(출금|이체|송금|보냄|보냈|빠져)|(출금|이체|송금)\s*:?\s*(\d[\d,]*)\s*원""")
    private val BALANCE = Regex("""잔액\s*:?\s*(\d[\d,]*)\s*원""")
    private val CANCEL_WORDS = listOf("취소", "환불")
    private val POINT_WORDS = listOf("적립", "캐시백", "받았어요", "혜택으로", "포인트")
    private val AD_WORDS = listOf("광고", "(광고)", "이벤트", "쿠폰", "혜택을 확인", "추천", "당첨")
    private val SEPARATOR = Regex("""\s*[|·•ㆍ]\s*""")
    private val PAY_TYPE_SUFFIX = Regex("""\(\s*(일시불|할부[^()]*|\d+\s*개월[^()]*)\s*\)\s*$""")
    private val MONTHS = Regex("""(\d+)\s*개월""")
    private val PAYER = Regex("""([가-힣A-Za-z0-9]{1,12})님""")
    private val ACCOUNT = Regex("""([가-힣A-Za-z0-9]{1,20}(통장|계좌))""")

    override fun parse(n: NotificationText): ParsedPayment {
        val lines = n.lines
        if (lines.isEmpty()) return ParsedPayment(PaymentKind.IGNORE, parser = id)
        val balance = lines.firstNotNullOfOrNull { BALANCE.find(it)?.groupValues?.get(1)?.toAmount() }
        val paymentLine = lines.firstOrNull { PAYMENT_LINE.containsMatchIn(it) }
        val hasAmount = lines.any { AMOUNT.containsMatchIn(it) && !BALANCE.containsMatchIn(it) }
        val isCancel = hasAmount && lines.any { l -> CANCEL_WORDS.any { it in l } }
        val isAd = n.subText?.trim() == "광고" || lines.any { l -> AD_WORDS.any { it in l } } && paymentLine == null && !isCancel
        val depositLine = lines.firstOrNull { DEPOSIT_LINE.containsMatchIn(it) }
        val withdrawLine = lines.firstOrNull { WITHDRAW_LINE.containsMatchIn(it) }
        val kind = when {
            isAd -> PaymentKind.IGNORE
            isCancel && (paymentLine != null || lines.any { "결제" in it }) -> PaymentKind.CANCEL
            paymentLine != null -> PaymentKind.PAYMENT
            lines.any { l -> POINT_WORDS.any { it in l } } -> PaymentKind.IGNORE
            depositLine != null -> PaymentKind.DEPOSIT
            withdrawLine != null -> PaymentKind.WITHDRAWAL
            else -> PaymentKind.IGNORE
        }
        if (kind == PaymentKind.IGNORE) return ParsedPayment(kind, parser = id)

        val amountLine = when (kind) {
            PaymentKind.PAYMENT, PaymentKind.CANCEL -> paymentLine ?: lines.firstOrNull { l -> AMOUNT.containsMatchIn(l) && !BALANCE.containsMatchIn(l) }
            PaymentKind.DEPOSIT -> depositLine
            PaymentKind.WITHDRAWAL -> withdrawLine
            PaymentKind.IGNORE -> null
        }
        val amount = amountLine?.let(::amountIn)
        // 결제 수단 | 가맹점(결제 방식) 줄: 금액이 없는 줄에서 찾는다
        val split = lines.filter { !AMOUNT.containsMatchIn(it) }.firstNotNullOfOrNull(::splitMethodMerchant)
        val payType = split?.payType
        val months = payType?.let { MONTHS.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        val payer = lines.firstNotNullOfOrNull { l -> PAYER.find(l)?.groupValues?.get(1) }?.takeIf { it != "고객" && it != "회원" }
        val method = split?.method ?: lines.firstNotNullOfOrNull { ACCOUNT.find(it)?.groupValues?.get(1) }
        val merchant = split?.merchant ?: if (kind == PaymentKind.DEPOSIT || kind == PaymentKind.WITHDRAWAL) counterpart(lines, amountLine, method) else null
        return ParsedPayment(
            kind = kind,
            amount = amount,
            merchant = merchant?.take(LedgerEntry.MERCHANT_MAX),
            method = method,
            payType = payType,
            installmentMonths = months,
            payerName = payer,
            balance = balance,
            // 카드·토스페이 결제/취소는 형식이 확인됨. 입출금은 공개 예시가 없어 사용자가 확인하게 한다
            confident = (kind == PaymentKind.PAYMENT || kind == PaymentKind.CANCEL) && amount != null && merchant != null,
            parser = id,
        )
    }

    private fun String.toAmount(): Long? = replace(",", "").toLongOrNull()

    internal fun amountIn(line: String): Long? {
        // "잔액 N원" 은 금액이 아니다
        val cleaned = BALANCE.replace(line, "")
        return AMOUNT.find(cleaned)?.groupValues?.get(1)?.toAmount()
    }

    internal data class MethodMerchant(val method: String?, val merchant: String?, val payType: String?)

    /** "하나체크 | 스타벅스(강남역점)(일시불)" → (하나체크, 스타벅스(강남역점), 일시불) */
    internal fun splitMethodMerchant(line: String): MethodMerchant? {
        val parts = line.split(SEPARATOR, limit = 2)
        if (parts.size < 2) return null
        var rest = parts[1].trim()
        var payType: String? = null
        PAY_TYPE_SUFFIX.find(rest)?.let { m ->
            payType = m.groupValues[1].trim()
            rest = rest.substring(0, m.range.first).trim()
        }
        val method = parts[0].trim().ifEmpty { null }
        val merchant = rest.ifEmpty { null }
        if (method == null && merchant == null) return null
        return MethodMerchant(method, merchant, payType)
    }

    /** 입출금 알림에서 보낸·받은 사람: 금액·계좌 이름·안내 말을 뺀 남은 짧은 줄 */
    private fun counterpart(lines: List<String>, amountLine: String?, method: String?): String? =
        lines.asSequence()
            .filter { it != amountLine && !AMOUNT.containsMatchIn(it) }
            .map { l -> if (method != null) l.replace(method, "").trim() else l }
            .map { it.trim('|', '·', ' ', ':', '-') }
            .firstOrNull { it.isNotEmpty() && it.length <= 30 && it !in setOf("토스", "토스뱅크", "입금", "출금") }
}

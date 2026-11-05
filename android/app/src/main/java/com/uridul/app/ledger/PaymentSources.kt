package com.uridul.app.ledger

// 결제 알림을 읽을 앱(토스·카카오톡 토스뱅크 알림톡)과 기록할 알림 고르기.

/**
 * 알림을 읽을 앱 하나.
 * [senders] 가 있으면 여러 대화가 섞인 앱(카카오톡)이라, 보낸 쪽(알림 제목)이 이 이름일 때만 본문을 읽는다.
 */
class PaymentSource(
    val packageName: String,
    val label: String,
    val parser: PaymentNotificationParser,
    val senders: Set<String>? = null,
) {
    /** 이 알림의 본문을 읽어도 되는지 (제목·대화방 이름만 보고 정한다. 인증 표시·띄어쓰기는 무시) */
    fun acceptsSender(title: String?, conversationTitle: String?): Boolean {
        val allowed = senders ?: return true
        fun clean(s: String?) = s?.filter { it.isLetterOrDigit() }
        return clean(title) in allowed || clean(conversationTitle) in allowed
    }
}

/** 알림을 읽을 앱 → 글 풀기. 여기 없는 앱 알림은 열어 보지 않는다 */
object PaymentSources {
    const val TOSS = "viva.republica.toss"
    const val KAKAO = "com.kakao.talk"
    /** 토스뱅크 카카오톡 알림톡의 보낸 쪽 이름 */
    const val TOSSBANK_CHANNEL = "토스뱅크"

    private val sources: Map<String, PaymentSource> = listOf(
        PaymentSource(TOSS, "토스", TossPaymentParser),
        // 카카오톡은 "토스뱅크" 알림톡만. 개인 대화·단체방·다른 채널 알림은 본문을 열어 보지 않는다
        PaymentSource(KAKAO, "토스뱅크 알림톡", KakaoTossBankParser, senders = setOf(TOSSBANK_CHANNEL)),
    ).associateBy { it.packageName }

    val packages: Set<String> get() = sources.keys
    fun sourceFor(packageName: String): PaymentSource? = sources[packageName]
    fun parserFor(packageName: String): PaymentNotificationParser? = sources[packageName]?.parser
    fun label(packageName: String?): String = sources[packageName]?.label ?: packageName.orEmpty()
}

/**
 * 기록할 알림 고르기: 데이트 카드(CHCH 카드) 결제와 데이트 통장(CHCH 모임통장) 입출금만 기록한다.
 * 개인 카드·다른 계좌 알림은 이 폰에도 남기지 않고 버린다. 대소문자·띄어쓰기는 가리지 않는다
 * ("CHCH 카드", "chch카드", "CHCH 모임카드", "chch모임통장" 모두 통과).
 */
object PaymentFilter {
    private val CHCH = Regex("""chch.{0,4}(카드|통장)""")

    fun accepts(n: NotificationText): Boolean = CHCH.containsMatchIn(n.lines.joinToString(" ").forMatch())
}

/** 알림 한 건을 어떻게 처리했는지 (점검용으로 개수만 센다. 알림 내용은 남기지 않는다) */
enum class IntakeResult {
    /** CHCH 카드·통장 알림이 아님 (버림) */
    OTHER_ACCOUNT,
    /** 금액이 없거나 결제·입출금 알림이 아님 (안내 문구 등) */
    NOT_PAYMENT,
    /** 이미 받은 알림 */
    DUPLICATE,
    /** 보관함에 넣음 (서버로 올림) */
    RECORDED,
}

/** 알림 글 맞추기용: 소문자로, 띄어쓰기 없이 ("CHCH 카드" = "chch카드") */
internal fun String.forMatch(): String = lowercase().filterNot { it.isWhitespace() }

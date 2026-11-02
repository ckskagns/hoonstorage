package com.uridul.app.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// 가계부 데이터 모델. 설계: docs/가계부_설계.md
// 금액은 원 단위 정수, 문서마다 구조 버전(v)을 둬서 칸을 더해도 예전 앱이 깨지지 않게 한다.

/** 거래 종류 */
enum class EntryType(val key: String, val label: String) {
    EXPENSE("expense", "지출"),
    INCOME("income", "수입"),
    TRANSFER("transfer", "이체");

    companion object {
        fun from(key: String?): EntryType = entries.firstOrNull { it.key == key } ?: EXPENSE
    }
}

/** 확정 / 확인 필요 (알림을 애매하게 읽었을 때) */
enum class EntryStatus(val key: String) {
    CONFIRMED("confirmed"),
    PENDING("pending");

    companion object {
        fun from(key: String?): EntryStatus = entries.firstOrNull { it.key == key } ?: CONFIRMED
    }
}

/** 어디서 왔는지 */
enum class EntrySource(val key: String, val label: String) {
    NOTIFICATION("notification", "결제 알림"),
    SMS("sms", "문자"),
    MANUAL("manual", "직접 입력"),
    IMPORT("import", "가져오기"),
    RECURRING("recurring", "반복");

    companion object {
        fun from(key: String?): EntrySource = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/** 자산 종류. liability 면 부채(순자산에서 뺌) */
enum class AssetKind(val key: String, val label: String, val liability: Boolean = false) {
    BANK("bank", "통장"),
    CARD("card", "카드"),
    CASH("cash", "현금"),
    SAVINGS("savings", "적금·저축"),
    INVESTMENT("investment", "투자"),
    LOAN("loan", "대출", liability = true),
    OTHER("other", "기타");

    companion object {
        fun from(key: String?): AssetKind = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** 알림 원문 (글 풀기를 고치면 다시 풀 수 있게 보관) */
data class RawText(val title: String, val text: String)

/** 거래 한 건 */
data class LedgerEntry(
    val id: String,
    val type: EntryType,
    /** 원 단위, 0 이상 */
    val amount: Long,
    /** 결제 취소·환불 (합계에서 뺀다) */
    val cancel: Boolean = false,
    val at: Long,
    val merchant: String = "",
    val memo: String = "",
    /** "대분류.소분류" */
    val category: String = LedgerCategories.DEFAULT_EXPENSE,
    val assetId: String? = null,
    val toAssetId: String? = null,
    val paidBy: String? = null,
    /** 쓴 사람 이름 (상대가 아직 가입 전이거나 알림에 이름만 있을 때) */
    val paidByName: String? = null,
    val status: EntryStatus = EntryStatus.CONFIRMED,
    val source: EntrySource = EntrySource.MANUAL,
    val sourceApp: String? = null,
    val raw: RawText? = null,
    val parser: String? = null,
    /** 알림에 잔액이 있었으면 결제 뒤 잔액 */
    val balanceAfter: Long? = null,
    /** 할부 개월 (0 = 일시불) */
    val installment: Int = 0,
    val recurringId: String? = null,
    val eventId: String? = null,
    val tags: List<String> = emptyList(),
    val deleted: Boolean = false,
    val createdBy: String = "",
) {
    val date: LocalDate get() = dateOf(at)

    /** 합계에 더할 값: 지출은 +, 취소는 − (수입·이체는 따로 센다) */
    val signedAmount: Long get() = if (cancel) -amount else amount

    companion object {
        const val SCHEMA = 1
        const val MERCHANT_MAX = 100
        const val MEMO_MAX = 500
        /** 한 건 최대 금액 (잘못 읽은 숫자를 막는 울타리): 100억 */
        const val AMOUNT_MAX = 10_000_000_000L
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate()
    }
}

/** 자산·부채 하나 */
data class LedgerAsset(
    val id: String,
    val name: String,
    val kind: AssetKind,
    val institution: String = "",
    /** 카드가 빠져나가는 통장 */
    val linkedAssetId: String? = null,
    val openingBalance: Long = 0,
    val openingDate: LocalDate? = null,
    /** 알림에서 읽은 최신 잔액 */
    val lastBalance: Long? = null,
    val lastBalanceAt: Long? = null,
    /** 알림 글에 이 말이 있으면 이 자산으로 */
    val matchers: List<String> = emptyList(),
    val archived: Boolean = false,
    val order: Int = 0,
) {
    companion object {
        const val SCHEMA = 1
        /** 처음 쓸 때 만들어 두는 기본 자산 (데이트 통장 = 토스뱅크 모임통장, 그 모임카드) */
        const val DATE_ACCOUNT = "date_account"
        const val DATE_CARD = "date_card"
        const val CASH = "cash"
        val defaults = listOf(
            LedgerAsset(DATE_ACCOUNT, "데이트 통장", AssetKind.BANK, "토스뱅크 chch모임통장", matchers = listOf("chch모임통장", "모임통장"), order = 0),
            LedgerAsset(DATE_CARD, "모임카드", AssetKind.CARD, "토스뱅크", linkedAssetId = DATE_ACCOUNT, matchers = listOf("모임카드"), order = 1),
            LedgerAsset(CASH, "현금", AssetKind.CASH, order = 2),
        )
    }
}

/** 한 달 합계 */
data class MonthSummary(val expense: Long, val income: Long, val count: Int) {
    companion object {
        fun of(entries: List<LedgerEntry>): MonthSummary {
            val live = entries.filter { !it.deleted && it.status == EntryStatus.CONFIRMED }
            return MonthSummary(
                expense = live.filter { it.type == EntryType.EXPENSE }.sumOf { it.signedAmount },
                income = live.filter { it.type == EntryType.INCOME }.sumOf { it.signedAmount },
                count = live.size,
            )
        }
    }
}

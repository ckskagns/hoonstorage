package com.uridul.app.ledger

/**
 * 가계부 분류: "대분류.소분류" 키. 기본 분류와 가맹점 이름으로 분류를 짐작하는 규칙.
 * 짐작 순서: 우리 둘 학습표(고친 분류를 기억, 서버 ledgerMerchants) → 기본 사전 → 기타.
 */
object LedgerCategories {
    data class Category(val key: String, val label: String, val emoji: String)
    data class Group(val key: String, val label: String, val emoji: String, val children: List<Category>, val income: Boolean = false)

    const val DEFAULT_EXPENSE = "etc.etc"
    const val DEFAULT_INCOME = "income.etc"

    /** 정리한 가맹점 이름 최대 길이 (학습표 문서 이름) */
    const val KEY_MAX = 60

    /** 가맹점 이름 비교 때 빼는 회사 꼴 말 (사전보다 먼저 만들어 둬야 한다) */
    private val CORP_WORDS = listOf("(주)", "㈜", "주식회사", "(유)", "유한회사")

    val groups: List<Group> = listOf(
        Group("food", "식비", "🍽️", listOf(
            Category("food.meal", "식사", "🍚"), Category("food.cafe", "카페·간식", "☕"),
            Category("food.delivery", "배달", "🛵"), Category("food.drink", "술", "🍻"),
        )),
        Group("date", "데이트·여가", "💕", listOf(
            Category("date.culture", "영화·공연", "🎬"), Category("date.play", "놀거리", "🎡"),
            Category("date.travel", "여행·숙박", "✈️"), Category("date.gift", "선물", "🎁"),
        )),
        Group("transport", "교통", "🚇", listOf(
            Category("transport.transit", "대중교통", "🚌"), Category("transport.taxi", "택시", "🚕"),
            Category("transport.car", "주유·주차", "⛽"),
        )),
        Group("shopping", "쇼핑", "🛍️", listOf(
            Category("shopping.living", "생활용품", "🧴"), Category("shopping.fashion", "옷·꾸미기", "👗"),
            Category("shopping.online", "온라인", "📦"),
        )),
        Group("life", "생활", "🏠", listOf(
            Category("life.subscription", "통신·구독", "📱"), Category("life.bill", "공과금", "🧾"),
            Category("life.health", "병원·약국", "💊"),
        )),
        Group("etc", "기타", "📌", listOf(Category("etc.etc", "기타", "📌"))),
        Group("income", "수입", "💰", listOf(
            Category("income.deposit", "모임비 입금", "🐷"), Category("income.interest", "이자", "🏦"),
            Category("income.refund", "환급", "↩️"), Category("income.etc", "기타 수입", "💰"),
        ), income = true),
    )

    private val byKey: Map<String, Category> = groups.flatMap { it.children }.associateBy { it.key }

    fun of(key: String?): Category = byKey[key] ?: byKey.getValue(DEFAULT_EXPENSE)
    fun groupOf(key: String?): Group = groups.firstOrNull { g -> g.children.any { it.key == key } } ?: groups.first { it.key == "etc" }

    fun isExpense(key: String?): Boolean = key != null && byKey.containsKey(key) && !groupOf(key).income

    /**
     * 저장한 거래로 학습표에 기억할지: 가맹점이 있는 지출이고,
     * 고친 거래([before] 있음)는 분류가 바뀌었을 때, 새 거래는 짐작과 다르게 골랐을 때.
     */
    fun shouldLearn(before: LedgerEntry?, after: LedgerEntry, learned: Map<String, String>): Boolean {
        if (after.type != EntryType.EXPENSE || after.merchant.isBlank() || !isExpense(after.category)) return false
        return if (before == null) after.category != guess(after.merchant, learned) else after.category != before.category
    }

    /** 짧은 영문(3자 이하)은 다른 말 속에 들어가 있으면 안 맞게 한다 (CU ≠ CURRY, KT ≠ KTX, BAR ≠ BARBER) */
    private class Word(val category: String, word: String, val order: Int) {
        val key = normalize(word)
        private val wholeWord = word.all { it.code < 128 } && key.length <= 3
        private val regex = if (wholeWord) Regex("(?<![A-Z0-9])" + Regex.escape(word.uppercase()) + "(?![A-Z])") else null
        fun matches(upper: String, normalized: String): Boolean = regex?.containsMatchIn(upper) ?: normalized.contains(key)
    }

    private val words: List<Word> = CategoryDictionary.entries.flatMapIndexed { i, (category, list) -> list.map { Word(category, it, i) } }

    /**
     * 가맹점 이름 비교용으로 정리: 대문자로, (주)·주식회사는 빼고, 글자·숫자만 남긴다.
     * "(주)우아한형제들" = "우아한 형제들", "Agoda" = "AGODA". 학습표의 문서 이름으로도 쓴다.
     */
    fun normalize(merchant: String?): String {
        if (merchant.isNullOrBlank()) return ""
        var s = merchant.uppercase()
        for (w in CORP_WORDS) s = s.replace(w, "")
        return s.filter { it.isLetterOrDigit() }.take(KEY_MAX)
    }

    /** 기본 사전으로만 짐작 */
    fun guessFromDictionary(merchant: String?): String {
        val normalized = normalize(merchant)
        if (normalized.isEmpty()) return DEFAULT_EXPENSE
        val upper = merchant!!.uppercase()
        return words.filter { it.matches(upper, normalized) }
            .maxWithOrNull(compareBy<Word> { it.key.length }.thenByDescending { it.order })
            ?.category ?: DEFAULT_EXPENSE
    }

    /**
     * 가맹점 이름 → 분류. [learned] (우리 둘 학습표: 정리한 가맹점 이름 → 분류)에 있으면 그것,
     * 없으면 기본 사전, 그래도 없으면 기타.
     */
    fun guess(merchant: String?, learned: Map<String, String> = emptyMap()): String {
        val normalized = normalize(merchant)
        if (normalized.isEmpty()) return DEFAULT_EXPENSE
        learned[normalized]?.takeIf(::isExpense)?.let { return it }
        return guessFromDictionary(merchant)
    }
}

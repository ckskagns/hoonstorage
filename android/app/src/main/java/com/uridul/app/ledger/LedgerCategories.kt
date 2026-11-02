package com.uridul.app.ledger

/**
 * 가계부 분류: "대분류.소분류" 키. 기본 분류와 가맹점 이름으로 분류를 짐작하는 규칙.
 * 사용자 분류·학습표는 나중에 ledgerSettings 에 더한다 (키 체계는 그대로).
 */
object LedgerCategories {
    data class Category(val key: String, val label: String, val emoji: String)
    data class Group(val key: String, val label: String, val emoji: String, val children: List<Category>, val income: Boolean = false)

    const val DEFAULT_EXPENSE = "etc.etc"
    const val DEFAULT_INCOME = "income.etc"

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

    /** 가맹점 이름 → 분류 짐작 (위에서부터 먼저 맞는 것). 사용자가 고친 분류는 나중에 학습표가 먼저 본다 */
    private val rules: List<Pair<String, List<String>>> = listOf(
        "food.cafe" to listOf("스타벅스", "투썸", "이디야", "메가커피", "메가엠지씨", "빽다방", "컴포즈", "폴바셋", "블루보틀", "카페", "커피", "베이커리", "파리바게뜨", "뚜레쥬르", "던킨", "배스킨", "설빙", "디저트", "케이크"),
        "food.delivery" to listOf("배달의민족", "배민", "요기요", "쿠팡이츠", "땡겨요"),
        "food.drink" to listOf("호프", "포차", "주점", "이자카야", "와인", "펍", "BAR", "칵테일", "맥주"),
        "date.culture" to listOf("CGV", "씨지브이", "롯데시네마", "메가박스", "영화", "공연", "인터파크티켓", "티켓링크", "예스24티켓", "뮤지컬", "전시", "미술관", "박물관"),
        "date.play" to listOf("노래", "코인", "볼링", "방탈출", "롯데월드", "에버랜드", "아쿠아리움", "PC방", "피씨방", "오락", "보드게임", "놀이"),
        "date.travel" to listOf("호텔", "모텔", "펜션", "리조트", "에어비앤비", "야놀자", "여기어때", "아고다", "숙박", "항공", "대한항공", "아시아나", "제주항공", "티웨이", "진에어", "KTX", "코레일", "SRT"),
        "date.gift" to listOf("꽃", "플라워", "선물"),
        "transport.taxi" to listOf("택시", "카카오T", "카카오모빌리티", "우티", "타다"),
        "transport.transit" to listOf("지하철", "버스", "교통", "티머니", "캐시비"),
        "transport.car" to listOf("주유", "SK에너지", "GS칼텍스", "S-OIL", "에쓰오일", "현대오일뱅크", "주차", "하이패스"),
        "shopping.online" to listOf("쿠팡", "11번가", "G마켓", "지마켓", "옥션", "네이버페이", "무신사", "29CM", "에이블리", "지그재그", "SSG", "알리익스프레스", "테무"),
        "shopping.living" to listOf("다이소", "이마트", "홈플러스", "롯데마트", "코스트코", "GS25", "CU", "세븐일레븐", "이마트24", "편의점", "올리브영", "마트"),
        "shopping.fashion" to listOf("유니클로", "자라", "ZARA", "H&M", "탑텐", "스파오", "미용실", "헤어", "네일"),
        "life.subscription" to listOf("넷플릭스", "NETFLIX", "유튜브", "YOUTUBE", "디즈니", "티빙", "웨이브", "쿠팡플레이", "멜론", "스포티파이", "SKT", "KT", "LG유플러스", "구글", "GOOGLE", "애플", "APPLE", "Adobe"),
        "life.health" to listOf("병원", "의원", "약국", "치과", "한의원"),
        "food.meal" to listOf("식당", "김밥", "국밥", "치킨", "피자", "버거", "맥도날드", "롯데리아", "버거킹", "맘스터치", "서브웨이", "분식", "고기", "갈비", "삼겹", "감자탕", "닭", "초밥", "스시", "라멘", "파스타", "쌀국수", "마라", "식육", "푸드", "키친", "반점", "곱창", "족발", "보쌈"),
    )

    fun guess(merchant: String?): String {
        if (merchant.isNullOrBlank()) return DEFAULT_EXPENSE
        val m = merchant.uppercase()
        return rules.firstOrNull { (_, words) -> words.any { m.contains(it.uppercase()) } }?.first ?: DEFAULT_EXPENSE
    }
}

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
     * 기본 사전: 가맹점 이름에 이 말이 들어 있으면 그 분류.
     * 결제 알림에는 가맹점이 영문·회사 이름·서비스 이름으로 오는 일이 많아 함께 적어 둔다 (Agoda, (주)우아한형제들 …).
     * 여러 말이 맞으면 가장 긴 말 (쿠팡플레이 > 쿠팡), 길이가 같으면 위에 있는 것.
     */
    private val dictionary: List<Pair<String, List<String>>> = listOf(
        "food.cafe" to listOf(
            "스타벅스", "STARBUCKS", "투썸", "TWOSOME", "이디야", "EDIYA", "메가커피", "메가엠지씨", "빽다방", "컴포즈", "폴바셋", "PAULBASSETT",
            "블루보틀", "BLUEBOTTLE", "할리스", "HOLLYS", "커피빈", "COFFEEBEAN", "탐앤탐스", "엔제리너스", "공차", "더벤티", "매머드", "텐퍼센트",
            "바나프레소", "커피에반하다", "카페", "CAFE", "커피", "COFFEE", "베이커리", "BAKERY", "제과", "파리바게뜨", "PARISBAGUETTE",
            "뚜레쥬르", "성심당", "던킨", "DUNKIN", "크리스피크림", "노티드", "배스킨", "BASKIN", "설빙", "디저트", "DESSERT", "케이크",
            "도넛", "와플", "아이스크림", "젤라또", "요거트", "탕후루",
        ),
        "food.delivery" to listOf("배달의민족", "배민", "우아한형제들", "요기요", "위대한상상", "쿠팡이츠", "땡겨요", "배달"),
        "food.drink" to listOf(
            "호프", "포차", "주점", "술집", "이자카야", "와인", "WINE", "펍", "PUB", "BAR", "칵테일", "하이볼", "맥주", "BEER", "비어",
            "막걸리", "전통주", "바틀샵",
        ),
        "date.culture" to listOf(
            "CGV", "씨지브이", "롯데시네마", "LOTTECINEMA", "메가박스", "MEGABOX", "씨네Q", "영화", "공연", "콘서트", "연극", "뮤지컬",
            "인터파크티켓", "티켓링크", "TICKETLINK", "예스24티켓", "멜론티켓", "놀티켓", "전시", "갤러리", "GALLERY", "미술관", "박물관",
            "예술의전당", "세종문화회관", "아트센터",
        ),
        "date.play" to listOf(
            "노래", "코인", "볼링", "방탈출", "롯데월드", "LOTTEWORLD", "에버랜드", "EVERLAND", "서울랜드", "레고랜드", "아쿠아리움", "AQUARIUM",
            "아쿠아플라넷", "PC방", "피씨방", "오락", "보드게임", "놀이", "당구", "스크린골프", "골프존", "GOLFZON", "클라이밍", "워터파크",
            "찜질", "스파", "사우나", "인생네컷", "포토이즘", "하루필름", "포토그레이", "포토부스",
        ),
        "date.travel" to listOf(
            "호텔", "HOTEL", "모텔", "펜션", "리조트", "RESORT", "게스트하우스", "글램핑", "캠핑", "숙박",
            "에어비앤비", "AIRBNB", "야놀자", "YANOLJA", "놀유니버스", "여기어때", "아고다", "AGODA", "부킹닷컴", "BOOKING.COM",
            "익스피디아", "EXPEDIA", "호텔스닷컴", "HOTELS.COM", "트립닷컴", "TRIP.COM", "트립비토즈", "마이리얼트립", "MYREALTRIP",
            "클룩", "KLOOK", "KKDAY", "하나투어", "모두투어", "노랑풍선", "인터파크투어", "스카이스캐너", "SKYSCANNER",
            "항공", "AIRLINE", "AIRWAYS", "대한항공", "KOREANAIR", "아시아나", "ASIANA", "제주항공", "JEJUAIR", "티웨이", "TWAY",
            "진에어", "JINAIR", "에어부산", "에어서울", "이스타항공", "에어프레미아",
            "KTX", "코레일", "KORAIL", "SRT", "에스알", "고속버스", "시외버스", "렌터카", "렌트카", "쏘카", "SOCAR", "그린카",
            "면세점", "공항",
        ),
        "date.gift" to listOf("꽃", "꽃집", "플라워", "FLOWER", "선물", "기프트", "GIFT"),
        "transport.taxi" to listOf("택시", "TAXI", "카카오T", "카카오모빌리티", "우티", "타다", "아이엠택시", "티맵택시"),
        "transport.transit" to listOf("지하철", "버스", "교통", "티머니", "TMONEY", "캐시비", "CASHBEE", "레일플러스", "따릉이", "공공자전거", "킥보드"),
        "transport.car" to listOf(
            "주유", "SK에너지", "GS칼텍스", "S-OIL", "에쓰오일", "현대오일뱅크", "알뜰주유", "충전소", "전기차충전",
            "주차", "파킹", "PARKING", "모두의주차장", "하이패스", "한국도로공사", "통행료", "세차",
        ),
        "shopping.online" to listOf(
            "쿠팡", "COUPANG", "11번가", "G마켓", "지마켓", "GMARKET", "옥션", "AUCTION", "네이버페이", "스마트스토어", "무신사", "MUSINSA",
            "29CM", "에이블리", "지그재그", "카카오스타일", "SSG", "쓱닷컴", "알리익스프레스", "ALIEXPRESS", "테무", "TEMU", "아마존", "AMAZON",
            "컬리", "KURLY", "오늘의집", "위메프", "티몬", "롯데온", "오아시스마켓", "번개장터", "KREAM",
        ),
        "shopping.living" to listOf(
            "다이소", "DAISO", "이마트", "EMART", "홈플러스", "HOMEPLUS", "롯데마트", "코스트코", "COSTCO", "트레이더스", "노브랜드",
            "GS25", "GS리테일", "CU", "BGF리테일", "세븐일레븐", "코리아세븐", "7-ELEVEN", "이마트24", "미니스톱", "편의점",
            "올리브영", "OLIVEYOUNG", "마트", "슈퍼", "하나로마트", "이케아", "IKEA", "모던하우스", "아트박스", "무인양품", "생활용품",
        ),
        "shopping.fashion" to listOf(
            "유니클로", "UNIQLO", "자라", "ZARA", "H&M", "탑텐", "스파오", "에잇세컨즈", "나이키", "NIKE", "아디다스", "ADIDAS", "뉴발란스",
            "백화점", "아울렛", "OUTLET", "미용실", "헤어", "HAIR", "네일", "NAIL", "뷰티", "BEAUTY", "왁싱", "시코르", "세포라", "안경",
        ),
        "life.subscription" to listOf(
            "넷플릭스", "NETFLIX", "유튜브", "YOUTUBE", "디즈니", "DISNEY", "티빙", "TVING", "웨이브", "WAVVE", "쿠팡플레이", "왓챠", "WATCHA",
            "멜론", "MELON", "지니뮤직", "스포티파이", "SPOTIFY", "SKT", "SK텔레콤", "KT", "LG유플러스", "LGU+", "알뜰폰",
            "구글", "GOOGLE", "애플", "APPLE", "마이크로소프트", "MICROSOFT", "ADOBE", "OPENAI", "ANTHROPIC", "네이버플러스",
        ),
        "life.bill" to listOf("공과금", "한국전력", "한전", "전기요금", "도시가스", "가스공사", "수도요금", "상수도", "관리비", "지방세", "국세", "지로"),
        "life.health" to listOf(
            "병원", "의원", "약국", "치과", "한의원", "메디컬", "클리닉", "CLINIC", "피부과", "안과", "내과", "정형외과", "이비인후과",
            "산부인과", "검진",
        ),
        "food.meal" to listOf(
            "식당", "RESTAURANT", "레스토랑", "다이닝", "비스트로", "한식", "중식", "일식", "양식", "김밥", "국밥", "백반", "도시락", "한솥",
            "치킨", "CHICKEN", "교촌", "BHC", "BBQ", "굽네", "처갓집", "호식이", "피자", "PIZZA", "도미노", "피자헛", "파파존스",
            "버거", "BURGER", "맥도날드", "MCDONALD", "롯데리아", "LOTTERIA", "버거킹", "맘스터치", "KFC", "쉐이크쉑", "파이브가이즈",
            "서브웨이", "써브웨이", "SUBWAY", "이삭토스트", "아웃백", "빕스", "VIPS", "애슐리",
            "분식", "떡볶이", "순대", "고기", "갈비", "삼겹", "한우", "정육식당", "곱창", "족발", "보쌈", "감자탕", "해장국", "닭", "오리구이", "오리고기",
            "초밥", "스시", "SUSHI", "라멘", "우동", "돈까스", "돈가스", "파스타", "스파게티", "쌀국수", "마라", "냉면", "칼국수", "국수", "샤브",
            "쭈꾸미", "낙지", "횟집", "회센터", "수산", "꽃게", "대게", "장어", "식육", "푸드", "FOOD", "키친", "KITCHEN", "반점", "명륜진사갈비",
        ),
    )

    /** 짧은 영문(3자 이하)은 다른 말 속에 들어가 있으면 안 맞게 한다 (CU ≠ CURRY, KT ≠ KTX, BAR ≠ BARBER) */
    private class Word(val category: String, word: String, val order: Int) {
        val key = normalize(word)
        private val wholeWord = word.all { it.code < 128 } && key.length <= 3
        private val regex = if (wholeWord) Regex("(?<![A-Z0-9])" + Regex.escape(word.uppercase()) + "(?![A-Z])") else null
        fun matches(upper: String, normalized: String): Boolean = regex?.containsMatchIn(upper) ?: normalized.contains(key)
    }

    private val words: List<Word> = dictionary.flatMapIndexed { i, (category, list) -> list.map { Word(category, it, i) } }

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

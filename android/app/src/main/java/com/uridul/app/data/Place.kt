package com.uridul.app.data

/** 장소를 찾고 여는 지도 앱 (API 없이 각 앱의 연동 주소만 사용) */
enum class MapApp(val key: String, val label: String, val packageName: String) {
    NAVER("naver", "네이버 지도", "com.nhn.android.nmap"),
    KAKAO("kakao", "카카오맵", "net.daum.android.map"),
    GOOGLE("google", "구글 지도", "com.google.android.apps.maps");

    companion object {
        fun from(key: String?): MapApp? = entries.firstOrNull { it.key == key }
    }
}

/** 일정 장소: 이름 + (있으면) 지도 앱 장소 링크 + 어느 지도 앱에서 왔는지 */
data class Place(
    val name: String,
    val url: String? = null,
    val app: MapApp? = null,
) {
    companion object {
        const val NAME_MAX = 100
        const val URL_MAX = 500
    }
}

/**
 * 지도 앱의 "공유하기" 로 받은 글에서 장소 이름과 링크를 뽑는다.
 *
 * 예) 네이버 지도: "[네이버 지도]\n선릉감자탕\n서울 강남구 …\nhttps://naver.me/xxxx"
 *     카카오맵:   "[카카오맵] 선릉감자탕\n서울 강남구 …\nhttps://kko.to/xxxx"
 *     구글 지도:  "선릉감자탕\nhttps://maps.app.goo.gl/xxxx"
 * 형식이 조금 달라도 링크는 주소 모양으로, 이름은 링크·주소가 아닌 첫 줄로 찾는다.
 */
object PlaceShare {
    private val urlRegex = Regex("""https?://[^\s<>"']+""")
    private val bracketPrefix = Regex("""^\s*[\[【(]\s*(네이버\s*지도|네이버지도|NAVER\s*Map|카카오\s*맵|카카오맵|Kakao\s*Map|Google\s*Maps?|구글\s*지도)\s*[\]】)]\s*""", RegexOption.IGNORE_CASE)
    // 주소처럼 보이는 줄 (시·도, 구·군, 로·길 + 번지)
    private val addressLike = Regex("""^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)\S*\s.*(구|군|시|로|길|동|읍|면)\b.*""")

    fun parse(text: String?, subject: String? = null): Place? {
        val body = text?.trim().orEmpty()
        if (body.isEmpty() && subject.isNullOrBlank()) return null
        val url = urlRegex.find(body)?.value?.trimEnd('.', ',', ')', ']')?.take(Place.URL_MAX)
        val app = url?.let(::appOf)

        val lines = body.lines()
            .map { it.replace(urlRegex, "").trim() }
            .map { it.replace(bracketPrefix, "").trim() }
            .filter { it.isNotEmpty() }
        val name = lines.firstOrNull { !addressLike.matches(it) }
            ?: subject?.replace(bracketPrefix, "")?.trim()?.takeIf { it.isNotEmpty() }
            ?: lines.firstOrNull()
            ?: if (url != null) "공유한 장소" else return null
        return Place(name.take(Place.NAME_MAX), url, app)
    }

    /** 링크 주소로 어느 지도 앱인지 */
    fun appOf(url: String): MapApp? {
        val host = Regex("""^https?://([^/?#]+)""").find(url)?.groupValues?.get(1)?.lowercase() ?: return null
        val path = url.lowercase()
        return when {
            host == "naver.me" || host.endsWith("map.naver.com") || host.endsWith("place.naver.com") -> MapApp.NAVER
            host == "kko.to" || host.endsWith("map.kakao.com") || host.endsWith("place.map.kakao.com") -> MapApp.KAKAO
            host == "maps.app.goo.gl" || (host == "goo.gl" && "/maps" in path) ||
                host.startsWith("maps.google.") || (host.contains("google.") && "/maps" in path) -> MapApp.GOOGLE
            else -> null
        }
    }
}

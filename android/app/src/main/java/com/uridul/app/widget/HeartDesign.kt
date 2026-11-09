package com.uridul.app.widget

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.uridul.app.domain.DDay
import java.time.LocalDate

/**
 * 하트 위젯 디자인. 앱 메뉴 "하트 위젯 디자인" 이나 위젯을 길게 눌러 "설정" 에서 고른다.
 * key 는 저장 값이라 바꾸지 않는다.
 */
enum class HeartDesign(val key: String, val number: Int, val title: String) {
    CLASSIC("classic", 0, "지금 하트"),
    PILL("pill", 1, "글래스 알약"),
    INSIDE("inside", 2, "하트 속 숫자"),
    ECG("ecg", 3, "심전도"),
    RISE("rise", 4, "떠오르는 하트"),
    MINIMAL("minimal", 5, "미니멀 D+"),
    NAMES("names", 6, "두 하트 만나기"),
    FILL("fill", 7, "채워지는 하트"),
    CARD("card", 8, "핑크 카드 반짝이"),
    NEON("neon", 9, "네온사인"),
    ORBIT("orbit", 10, "궤도 하트"),
    BUBBLE("bubble", 11, "말풍선"),
    PIXEL("pixel", 12, "픽셀 하트"),
    SERIF("serif", 13, "영문 타이포"),
    CALENDAR("calendar", 14, "일력 한 장");

    val label: String get() = if (number == 0) title else "$number $title"

    companion object {
        fun from(key: String?): HeartDesign = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** 디자인마다 다른 글자 (사귄 날·오늘에서 만든다) */
internal data class HeartTexts(
    val days: CharSequence,
    val since: String = "",
    val extra: String = "",
    val extra2: String = "",
    /** 채워지는 하트: 다음 100일까지 온 만큼 (0~100) */
    val progress: Int = 0,
)

/**
 * 채워지는 하트의 진행 막대 값: 하트 그림은 칸의 아래 11% ~ 위 87.5% 에만 있어서,
 * 0~100 을 그 사이로 옮겨야 13% 가 "하트의 13%" 로 보인다.
 */
internal fun fillLevel(progress: Int): Int = (11 + progress.coerceIn(0, 100) * 0.765f).toInt()

/** 다음 100일 단위 기념일까지: (다음 기념일, 남은 날, 채운 정도 0~100). 시작 전이면 (100, 남은 날+100, 0) */
internal fun nextHundred(dayNumber: Long): Triple<Long, Long, Int> {
    if (dayNumber <= 0) return Triple(100L, 100L - dayNumber, 0)
    val next = (dayNumber / 100 + 1) * 100
    return Triple(next, next - dayNumber, (dayNumber % 100).toInt())
}


/** 디자인별 글자 만들기 (연결 전이면 "우리둘" + 안내) */
internal fun heartTexts(design: HeartDesign, face: HeartFace, today: LocalDate): HeartTexts {
    val start = face.start ?: return when (design) {
        HeartDesign.INSIDE, HeartDesign.ORBIT, HeartDesign.SERIF, HeartDesign.CALENDAR -> HeartTexts("♥", "앱에서 연결해 주세요")
        HeartDesign.NAMES -> HeartTexts("우리둘", extra = face.myName ?: "나", extra2 = face.partnerName ?: "너")
        else -> HeartTexts("우리둘", "앱에서 연결해 주세요")
    }
    val n = DDay.dayNumber(start, today, face.countFromOne)
    val hero = DDay.heroText(start, today, face.countFromOne) // "113일" / "D-3"
    val num = if (n < 0) "D$n" else "$n" // 숫자만
    // 사귄 날짜(26.07.17 등)는 보이지 않는다: 하트 그림과 디데이만. 숫자만 크게 쓰는 디자인은 "일째" 같은 단위만 남긴다
    return when (design) {
        HeartDesign.CLASSIC -> HeartTexts(hero)
        HeartDesign.PILL -> HeartTexts(hero)
        HeartDesign.INSIDE -> HeartTexts(num, if (n < 0) "사귀기 전" else "일째 사랑 중")
        HeartDesign.ECG -> HeartTexts(hero)
        HeartDesign.RISE -> HeartTexts(hero)
        HeartDesign.MINIMAL -> HeartTexts(DDay.shortText(start, today, face.countFromOne))
        HeartDesign.NAMES -> HeartTexts(hero, extra = face.myName ?: "나", extra2 = face.partnerName ?: "너")
        HeartDesign.FILL -> {
            val (next, left, progress) = nextHundred(n)
            HeartTexts(hero, if (left == 0L) "오늘 ${next}일!" else "${next}일까지 ${left}일", progress = progress)
        }
        HeartDesign.CARD -> HeartTexts(hero)
        HeartDesign.NEON -> HeartTexts(hero)
        HeartDesign.ORBIT -> HeartTexts(num, if (n < 0) "" else "일")
        HeartDesign.BUBBLE -> HeartTexts(bubbleText(hero, n >= 0))
        HeartDesign.PIXEL -> HeartTexts(hero)
        HeartDesign.SERIF -> HeartTexts(num, extra = if (n < 0) "" else if (n == 1L) "day" else "days")
        HeartDesign.CALENDAR -> HeartTexts(num, if (n < 0) "" else "일째")
    }
}

/** 말풍선: "오늘로 113일째" (숫자만 크게, 분홍) */
private fun bubbleText(hero: String, started: Boolean): CharSequence {
    if (!started) return SpannableString(hero).apply { setSpan(ForegroundColorSpan(0xFFE63B74.toInt()), 0, hero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    val text = "오늘로 ${hero}째"
    val s = text.indexOf(hero)
    return SpannableString(text).apply {
        setSpan(ForegroundColorSpan(0xFFE63B74.toInt()), s, s + hero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(RelativeSizeSpan(1.3f), s, s + hero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), s, s + hero.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}


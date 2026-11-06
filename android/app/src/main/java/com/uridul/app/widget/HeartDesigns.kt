package com.uridul.app.widget

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import com.uridul.app.R
import com.uridul.app.domain.DDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

private val Short: DateTimeFormatter = DateTimeFormatter.ofPattern("yy.MM.dd")
private val Dotted: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val Spaced: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd")
private val English: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd.yyyy")
private val MonthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

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
    return when (design) {
        HeartDesign.CLASSIC -> HeartTexts(hero, start.format(Short))
        HeartDesign.PILL -> HeartTexts(hero, "${start.format(Dotted)} ~")
        HeartDesign.INSIDE -> HeartTexts(num, if (n < 0) "사귀기 전 · ${start.format(Short)}" else "일째 사랑 중 · ${start.format(Short)}")
        HeartDesign.ECG -> HeartTexts(hero, "${start.format(Short)}부터")
        HeartDesign.RISE -> HeartTexts(hero, "우리 사귄 날 ${start.format(Short)}")
        HeartDesign.MINIMAL -> HeartTexts(DDay.shortText(start, today, face.countFromOne), "since ${start.format(Spaced)}")
        HeartDesign.NAMES -> HeartTexts(hero, extra = face.myName ?: "나", extra2 = face.partnerName ?: "너")
        HeartDesign.FILL -> {
            val (next, left, progress) = nextHundred(n)
            HeartTexts(hero, if (left == 0L) "오늘 ${next}일!" else "${next}일까지 ${left}일", progress = progress)
        }
        HeartDesign.CARD -> HeartTexts(hero)
        HeartDesign.NEON -> HeartTexts(hero, start.format(Short))
        HeartDesign.ORBIT -> HeartTexts(num, if (n < 0) start.format(MonthDay) else "일 · ${start.format(MonthDay)}")
        HeartDesign.BUBBLE -> HeartTexts(bubbleText(hero, n >= 0))
        HeartDesign.PIXEL -> HeartTexts(hero, if (n < 0) start.format(Short) else "LV.$n ♥ ${start.format(Short)}")
        HeartDesign.SERIF -> HeartTexts(num, "together since ${start.format(English)}", extra = if (n < 0) "" else if (n == 1L) "day" else "days")
        HeartDesign.CALENDAR -> HeartTexts(num, if (n < 0) start.format(Short) else "일째 · ${start.format(Short)}")
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

/**
 * 디자인 그리기 (지금 하트 CLASSIC 은 HeartWidget 의 heartRemoteViews 가 그린다).
 * 레이아웃은 기준 크기로 만들어져 있고, 위젯 칸(size)에 맞춰 글자·그림·여백을 같은 비율로 키우고 줄인다.
 */
internal object HeartDesigns {

    fun layoutOf(design: HeartDesign): Int = when (design) {
        HeartDesign.CLASSIC -> R.layout.widget_heart
        HeartDesign.PILL -> R.layout.widget_hd_pill
        HeartDesign.INSIDE -> R.layout.widget_hd_inside
        HeartDesign.ECG -> R.layout.widget_hd_ecg
        HeartDesign.RISE -> R.layout.widget_hd_rise
        HeartDesign.MINIMAL -> R.layout.widget_hd_minimal
        HeartDesign.NAMES -> R.layout.widget_hd_names
        HeartDesign.FILL -> R.layout.widget_hd_fill
        HeartDesign.CARD -> R.layout.widget_hd_card
        HeartDesign.NEON -> R.layout.widget_hd_neon
        HeartDesign.ORBIT -> R.layout.widget_hd_orbit
        HeartDesign.BUBBLE -> R.layout.widget_hd_bubble
        HeartDesign.PIXEL -> R.layout.widget_hd_pixel
        HeartDesign.SERIF -> R.layout.widget_hd_serif
        HeartDesign.CALENDAR -> R.layout.widget_hd_calendar
    }

    /** 글자 폭 (dp): 굵은 글꼴 기준 대략 */
    private fun tw(text: CharSequence, sizeDp: Float, spacing: Float = 1f) = textWidthEm(text.toString()) * sizeDp * spacing

    /** 기준 크기에서 이 디자인 내용이 차지하는 가로·세로 (dp) */
    fun baseSize(design: HeartDesign, t: HeartTexts): Pair<Float, Float> = when (design) {
        HeartDesign.CLASSIC -> 0f to 0f
        HeartDesign.PILL -> (12 + 26 + 8 + maxOf(tw(t.days, 20f), tw(t.since, 11f)) + 16) to 49f
        HeartDesign.INSIDE -> maxOf(78f, tw(t.since, 11f)) to 90f
        HeartDesign.ECG -> (74 + 4 + maxOf(tw(t.days, 24f), tw(t.since, 11f))) to 38f
        HeartDesign.RISE -> maxOf(100f, tw(t.days, 32f), tw(t.since, 11f)) to 60f
        HeartDesign.MINIMAL -> maxOf(tw(t.days, 34f, 0.85f) + 15, tw(t.since, 11f, 1.3f)) to 50f
        HeartDesign.NAMES -> maxOf(tw(t.extra, 13f) + 60 + tw(t.extra2, 13f), tw(t.days, 26f)) to 62f
        HeartDesign.FILL -> (44 + 8 + maxOf(tw(t.days, 24f), tw(t.since, 11f))) to 44f
        HeartDesign.CARD -> (16 + 22 + 8 + maxOf(tw("우리 사귄 지", 11f), tw(t.days, 22f)) + 18) to 53f
        HeartDesign.NEON -> (34 + 6 + maxOf(tw(t.days, 26f), tw(t.since, 11f))) to 40f
        HeartDesign.ORBIT -> 96f to 96f
        HeartDesign.BUBBLE -> (14 + tw(t.days, 15f) + 5 + 16 + 12) to 46f
        HeartDesign.PIXEL -> (36 + 10 + maxOf(tw(t.days, 24f, 1.15f), tw(t.since, 11f, 1.1f))) to 38f
        HeartDesign.SERIF -> maxOf(tw(t.days, 38f) + 5 + tw(t.extra, 14f) + 18, tw(t.since, 11f, 1.15f)) to 53f
        HeartDesign.CALENDAR -> maxOf(80f, tw(t.since, 11f) + 18) to 66f
    }

    /** 위젯 칸에 맞춘 배율 (바깥 여백 4dp 씩 빼고, 조금 남게) */
    fun scaleFor(design: HeartDesign, t: HeartTexts, size: DpSize): Float {
        val (bw, bh) = baseSize(design, t)
        val w = (size.width.value - 8f) * 0.94f
        val h = (size.height.value - 8f) * 0.92f
        return minOf(w / bw, h / bh).coerceIn(0.35f, 2.4f)
    }

    /** CLASSIC 이 아닌 디자인 그리기 */
    fun render(context: Context, design: HeartDesign, face: HeartFace, today: LocalDate, size: DpSize): RemoteViews {
        val t = heartTexts(design, face, today)
        val k = scaleFor(design, t, size)
        val px = context.resources.displayMetrics.density * k
        val v = RemoteViews(context.packageName, layoutOf(design))
        fun text(id: Int, value: CharSequence, sp: Float) {
            v.setTextViewText(id, value)
            v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, sp * k)
        }
        fun box(id: Int, wDp: Float, hDp: Float) {
            v.setViewLayoutWidth(id, wDp * k, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(id, hDp * k, TypedValue.COMPLEX_UNIT_DIP)
        }
        fun pad(id: Int, l: Int, tp: Int, rt: Int, b: Int) =
            v.setViewPadding(id, (l * px).toInt(), (tp * px).toInt(), (rt * px).toInt(), (b * px).toInt())
        fun margin(id: Int, dp: Float) = v.setViewLayoutMargin(id, RemoteViews.MARGIN_START, dp * k, TypedValue.COMPLEX_UNIT_DIP)

        when (design) {
            HeartDesign.CLASSIC -> error("CLASSIC 은 heartRemoteViews 로 그린다")
            HeartDesign.PILL -> {
                box(R.id.heart_anim, 26f, 26f); text(R.id.heart_days, t.days, 20f); text(R.id.heart_since, t.since, 11f)
                pad(R.id.heart_box, 12, 8, 16, 8)
            }
            HeartDesign.INSIDE -> {
                box(R.id.heart_anim, 78f, 78f); text(R.id.heart_days, t.days, if (t.days.length > 4) 16f else 22f); text(R.id.heart_since, t.since, 11f)
            }
            HeartDesign.ECG -> { box(R.id.heart_anim, 74f, 30f); text(R.id.heart_days, t.days, 24f); text(R.id.heart_since, t.since, 11f) }
            HeartDesign.RISE -> {
                val (bw, _) = baseSize(design, t)
                box(R.id.heart_fx, bw, 60f); text(R.id.heart_days, t.days, 32f); text(R.id.heart_since, t.since, 11f)
            }
            HeartDesign.MINIMAL -> { text(R.id.heart_days, t.days, 34f); box(R.id.heart_anim, 13f, 13f); text(R.id.heart_since, t.since, 11f) }
            HeartDesign.NAMES -> {
                text(R.id.heart_me, t.extra, 13f); text(R.id.heart_partner, t.extra2, 13f); box(R.id.heart_anim, 52f, 34f)
                text(R.id.heart_days, t.days, 26f)
            }
            HeartDesign.FILL -> {
                box(R.id.heart_fill, 44f, 44f); box(R.id.heart_anim, 14f, 14f)
                v.setProgressBar(R.id.heart_fill, 100, fillLevel(t.progress), false)
                text(R.id.heart_days, t.days, 24f); text(R.id.heart_since, t.since, 11f)
            }
            HeartDesign.CARD -> {
                // 반짝이 칸은 레이아웃에서 카드 크기를 그대로 따른다
                box(R.id.heart_anim, 22f, 22f); text(R.id.heart_label, "우리 사귄 지", 11f); text(R.id.heart_days, t.days, 22f)
                pad(R.id.heart_box, 16, 10, 18, 10)
            }
            HeartDesign.NEON -> { box(R.id.heart_anim, 34f, 34f); text(R.id.heart_days, t.days, 26f); text(R.id.heart_since, t.since, 11f) }
            HeartDesign.ORBIT -> { box(R.id.heart_fx, 96f, 96f); text(R.id.heart_days, t.days, if (t.days.length > 4) 18f else 24f); text(R.id.heart_since, t.since, 11f) }
            HeartDesign.BUBBLE -> {
                text(R.id.heart_days, t.days, 14f); box(R.id.heart_anim, 16f, 16f); box(R.id.heart_tail, 12f, 8f)
                pad(R.id.heart_box, 14, 9, 12, 9); margin(R.id.heart_tail, 18f)
            }
            HeartDesign.PIXEL -> { box(R.id.heart_anim, 36f, 32f); text(R.id.heart_days, t.days, 24f); text(R.id.heart_since, t.since, 11f) }
            HeartDesign.SERIF -> {
                text(R.id.heart_days, t.days, 38f); text(R.id.heart_unit, t.extra, 14f); box(R.id.heart_anim, 14f, 14f)
                text(R.id.heart_since, t.since, 11f)
            }
            HeartDesign.CALENDAR -> {
                v.setViewLayoutWidth(R.id.heart_box, baseSize(design, t).first * k, TypedValue.COMPLEX_UNIT_DIP)
                text(R.id.heart_label, "사귄 지", 11f); box(R.id.heart_anim, 11f, 11f)
                text(R.id.heart_days, t.days, 28f); text(R.id.heart_since, t.since, 11f)
            }
        }
        val desc = if (face.start == null) "우리둘: 앱을 열어 연결해 주세요" else "사귄 지 ${heartTexts(HeartDesign.CLASSIC, face, today).days}, ${face.start.format(Short)}부터"
        v.setContentDescription(R.id.heart_root, desc)
        return v
    }
}

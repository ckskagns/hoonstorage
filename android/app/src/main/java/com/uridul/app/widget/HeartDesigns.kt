package com.uridul.app.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.uridul.app.R
import java.time.LocalDate

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

    /** 한 줄 높이 (글자 크기보다 약 20% 크다: 폴드8·에뮬레이터에서 글자 크기만으로 재면 낮은 칸에서 아래가 잘렸다) */
    private fun lh(sizeDp: Float) = sizeDp * LINE_HEIGHT
    private const val LINE_HEIGHT = 1.22f

    /** 기준 크기에서 이 디자인 내용이 차지하는 가로·세로 (dp) */
    fun baseSize(design: HeartDesign, t: HeartTexts): Pair<Float, Float> = when (design) {
        HeartDesign.CLASSIC -> 0f to 0f
        HeartDesign.PILL -> (12 + 26 + 8 + maxOf(tw(t.days, 20f), tw(t.since, 11f)) + 16) to (16 + maxOf(26f, lh(20f) + 2 + lh(11f)))
        HeartDesign.INSIDE -> maxOf(78f, tw(t.since, 11f)) to (78 + lh(11f))
        HeartDesign.ECG -> (74 + 4 + maxOf(tw(t.days, 24f), tw(t.since, 11f))) to maxOf(30f, lh(24f) + 2 + lh(11f))
        HeartDesign.RISE -> maxOf(100f, tw(t.days, 32f), tw(t.since, 11f)) to maxOf(60f, lh(32f) + 3 + lh(11f))
        HeartDesign.MINIMAL -> maxOf(tw(t.days, 34f, 0.85f) + 15, tw(t.since, 11f, 1.3f)) to (lh(34f) + 4 + lh(11f))
        HeartDesign.NAMES -> maxOf(tw(t.extra, 13f) + 60 + tw(t.extra2, 13f), tw(t.days, 26f)) to (34 + 2 + lh(26f))
        HeartDesign.FILL -> (44 + 8 + maxOf(tw(t.days, 24f), tw(t.since, 11f))) to maxOf(44f, lh(24f) + 2 + lh(11f))
        HeartDesign.CARD -> (16 + 22 + 8 + maxOf(tw("우리 사귄 지", 11f), tw(t.days, 22f)) + 18) to (20 + lh(11f) + lh(22f))
        HeartDesign.NEON -> (34 + 6 + maxOf(tw(t.days, 26f), tw(t.since, 11f))) to (lh(26f) + 2 + lh(11f) + 6)
        HeartDesign.ORBIT -> 96f to 96f
        HeartDesign.BUBBLE -> (14 + tw(t.days, 15f) + 5 + 16 + 12) to (18 + lh(14f * 1.3f) + 8)
        HeartDesign.PIXEL -> (36 + 10 + maxOf(tw(t.days, 24f, 1.15f), tw(t.since, 11f, 1.1f))) to maxOf(32f, lh(24f) + 2 + lh(11f))
        HeartDesign.SERIF -> maxOf(tw(t.days, 38f) + 5 + tw(t.extra, 14f) + 18, tw(t.since, 11f, 1.15f)) to (lh(38f) + 3 + lh(11f))
        HeartDesign.CALENDAR -> maxOf(80f, tw(t.since, 11f) + 18) to (6 + lh(11f) + 3 + lh(28f) + lh(11f) + 5)
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
        val desc = if (face.start == null) "우리둘: 앱을 열어 연결해 주세요" else "사귄 지 ${face.daysText(today)}, ${face.start.format(Short)}부터"
        v.setContentDescription(R.id.heart_root, desc)
        return v
    }
}

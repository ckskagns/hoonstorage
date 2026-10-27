package com.uridul.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import com.uridul.app.MainActivity
import com.uridul.app.R
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.DDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * "사귄 날" 하트 위젯. 홈 화면 + 잠금 화면(굿락 LockStar).
 *
 *   [♥♥] 103일
 *        26.07.17
 *
 * - 겹친 빨간 미니 하트 두 개가 번갈아 콩닥이고, 뛰는 하트가 앞으로 나온다
 *   (위젯 호스트가 애니메이션을 막으면 멈춘 하트로 보인다).
 * - 하트 높이는 디데이 숫자 글자 크기와 같고, 숫자 왼쪽에 가로로 나란히 선다.
 * - 배경은 투명(하트와 글자만). 흰 글자 + 진한 그림자라 밝은 배경화면에서도 읽힌다.
 *
 * 하트 애니메이션은 Glance 로 만들 수 없어서 일반 위젯 레이아웃(RemoteViews)을 그대로 넣는다.
 */
class HeartWidget : GlanceAppWidget() {

    // 칸 크기에 맞춰 글자·하트 크기를 정하려고 실제 크기를 받는다
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.load(context)
        val today = LocalDate.now()
        provideContent {
            val views = heartRemoteViews(context, snapshot, today, LocalSize.current)
            AndroidRemoteViews(views, GlanceModifier.fillMaxSize())
        }
    }
}

class HeartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeartWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightScheduler.schedule(context)
    }
}

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yy.MM.dd")

/** 겹친 하트 두 개(가로:세로 = 30:24) 너비 비율 */
private const val HEART_ASPECT = 30f / 24f

/**
 * 하트 그림 높이 = 글자 크기 × 이 값.
 * 그림 안의 하트는 여백 때문에 칸의 약 60%만 차지해서, 숫자 높이와 비슷해지도록 키운다.
 */
private const val HEART_SCALE = 1.3f

/** 하트 그림 너비 (글자 크기 1em 대비) */
private const val HEART_WIDTH_EM = HEART_ASPECT * HEART_SCALE

/** 가로 여백: 바깥 패딩 4×2 + 하트와 글자 사이 6 */
private const val ROW_PADDING_DP = 14f

/** 세로 여백: 바깥 패딩 4×2 */
private const val COLUMN_PADDING_DP = 8f

/** 위젯 크기에 맞춘 글자·하트 크기 (dp) */
internal data class HeartLayout(val textDp: Float, val showSince: Boolean)

/** 굵은 글꼴 기준 대략적인 글자 폭 (글자 크기 1em 대비) */
internal fun textWidthEm(text: String): Float = text.sumOf { c ->
    when {
        c.isDigit() -> 0.58
        c == '-' -> 0.42
        c == '+' -> 0.62
        c in 'A'..'Z' -> 0.72
        c.code in 0xAC00..0xD7A3 -> 1.0 // 한글
        else -> 0.6
    }
}.toFloat()

/** 하트 + 간격 + 글자 + 여백을 합친 가로 폭(dp) */
internal fun heartRowWidthDp(text: String, textDp: Float): Float =
    ROW_PADDING_DP + textDp * (textWidthEm(text) + HEART_WIDTH_EM)

internal fun heartLayout(widthDp: Float, heightDp: Float, daysText: String): HeartLayout {
    val byWidth = (widthDp * 0.94f - ROW_PADDING_DP) / (textWidthEm(daysText) + HEART_WIDTH_EM)
    val byHeight = (heightDp - COLUMN_PADDING_DP) / HEART_SCALE * 0.9f
    val text = minOf(byWidth, byHeight).coerceIn(12f, 64f)
    val showSince = heightDp - COLUMN_PADDING_DP >= text * HEART_SCALE + 16f && text >= 14f
    return HeartLayout(text, showSince)
}

private fun heartRemoteViews(
    context: Context,
    snapshot: WidgetSnapshot?,
    today: LocalDate,
    size: DpSize,
): RemoteViews {
    val days = snapshot?.let { DDay.heroText(it.startDate, today, it.countFromOne) } ?: "우리둘"
    val since = snapshot?.startDate?.format(ShortDate) ?: "앱에서 연결해 주세요"
    val layout = heartLayout(size.width.value, size.height.value, days)

    return RemoteViews(context.packageName, R.layout.widget_heart).apply {
        setTextViewText(R.id.heart_days, days)
        // 글꼴 크기 설정과 상관없이 칸에 맞도록 dp 단위로 지정
        setTextViewTextSize(R.id.heart_days, TypedValue.COMPLEX_UNIT_DIP, layout.textDp)
        setTextViewText(R.id.heart_since, since)
        setTextViewTextSize(R.id.heart_since, TypedValue.COMPLEX_UNIT_DIP, (layout.textDp * 0.36f).coerceIn(10f, 16f))
        setViewVisibility(R.id.heart_since, if (layout.showSince) View.VISIBLE else View.GONE)

        // 보이는 하트 크기 ≈ 숫자 글자 높이
        setViewLayoutHeight(R.id.heart_anim, layout.textDp * HEART_SCALE, TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutWidth(R.id.heart_anim, layout.textDp * HEART_WIDTH_EM, TypedValue.COMPLEX_UNIT_DIP)
        // 날짜를 숫자 시작점(하트 폭 + 간격 6dp)에 맞춘다
        setViewLayoutMargin(
            R.id.heart_since,
            RemoteViews.MARGIN_START,
            layout.textDp * HEART_WIDTH_EM + 6f,
            TypedValue.COMPLEX_UNIT_DIP,
        )

        setContentDescription(
            R.id.heart_root,
            if (snapshot == null) "우리둘: 앱을 열어 연결해 주세요" else "사귄 지 $days, ${since}부터",
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainExtras.TAB, "home")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        setOnClickPendingIntent(
            R.id.heart_root,
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_HOME,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }
}

private const val REQUEST_OPEN_HOME = 2001

package com.uridul.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.uridul.app.MainActivity
import com.uridul.app.R
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.DDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 하트 모양 2×2 "사귄 날" 위젯. 홈 화면과 잠금 화면(지원하는 기기) 모두에 둘 수 있다.
 * 배경 없이 하트만 보이게 해서 잠금 화면 배경화면 위에서도 자연스럽게 보인다.
 */
class HeartWidget : GlanceAppWidget() {

    // 하트 크기를 칸 크기에 정확히 맞추려고 실제 크기를 받는다
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.load(context)
        val today = LocalDate.now()
        provideContent { HeartContent(snapshot, today) }
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

@Composable
private fun HeartContent(snapshot: WidgetSnapshot?, today: LocalDate) {
    val size = LocalSize.current
    val side: Dp = minOf(size.width, size.height)
    val compact = side < 90.dp

    val big = snapshot?.let { DDay.heroText(it.startDate, today, it.countFromOne) } ?: "♥"
    val since = snapshot?.startDate?.format(ShortDate)
    val description = if (snapshot == null) "우리둘: 앱을 열어 연결해 주세요" else "사귄 지 $big, ${since}부터"

    Box(
        GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(Widgets.KeyTab to "home")))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_heart),
            contentDescription = null,
            colorFilter = ColorFilter.tint(WidgetColors.Primary),
            modifier = GlanceModifier.size(side),
        )
        // 하트의 볼록한 윗부분 때문에 글자를 살짝 위쪽에 둔다
        Column(
            GlanceModifier.size(side * 0.78f, side * 0.62f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact && snapshot != null) {
                Text(
                    "우리 사귄 지",
                    style = TextStyle(color = WidgetColors.OnPrimary, fontSize = (side.value * 0.085f).sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
            Text(
                big,
                style = TextStyle(
                    color = WidgetColors.OnPrimary,
                    fontSize = (side.value * if (big.length > 5) 0.15f else 0.19f).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            if (!compact && since != null) {
                Text(
                    since,
                    style = TextStyle(color = WidgetColors.OnPrimary, fontSize = (side.value * 0.08f).sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
            if (snapshot == null && !compact) {
                Text(
                    "앱에서 연결",
                    style = TextStyle(color = WidgetColors.OnPrimary, fontSize = (side.value * 0.08f).sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

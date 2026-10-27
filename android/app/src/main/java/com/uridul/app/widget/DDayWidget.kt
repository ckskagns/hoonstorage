package com.uridul.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import com.uridul.app.MainActivity
import com.uridul.app.R
import com.uridul.app.data.Character
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.DDay
import java.time.LocalDate

/**
 * 디데이 홈 화면 위젯.
 * 칸 크기에 따라 3가지 모양으로 바뀐다 (폴드 커버/메인 홈의 칸 크기가 서로 달라서 반응형이 필수).
 *  - SMALL  (4×1, 2×1): 한 줄  "D+523 · 600일 D-12"
 *  - SQUARE (2×2):      가운데 큰 숫자
 *  - WIDE   (4×2 이상): 왼쪽 큰 숫자 + 오른쪽 다가오는 기념일 2개
 */
class DDayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, SQUARE, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.load(context)
        val today = LocalDate.now()
        provideContent { WidgetContent(snapshot, today) }
    }

    companion object {
        val SMALL = DpSize(110.dp, 48.dp)
        val SQUARE = DpSize(120.dp, 120.dp)
        val WIDE = DpSize(250.dp, 120.dp)
    }
}

class DDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DDayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightScheduler.schedule(context)
    }
}

private val Background = WidgetColors.Background
private val Primary = WidgetColors.Primary
private val OnBg = WidgetColors.OnBg
private val SubText = WidgetColors.SubText

/** [내 캐릭터] 후이 ♥ 나나 [상대 캐릭터] */
@Composable
private fun CharacterNames(snapshot: WidgetSnapshot, avatarDp: Int) {
    val mine = Character.from(snapshot.myCharacter) ?: Character.DRAGON
    val partner = Character.from(snapshot.partnerCharacter) ?: mine.other
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(widgetDrawable(mine)),
            contentDescription = null,
            modifier = GlanceModifier.size(avatarDp.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            "${snapshot.myName} ♥ ${snapshot.partnerName ?: "?"}",
            style = TextStyle(color = OnBg, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(4.dp))
        Image(
            ImageProvider(widgetDrawable(partner)),
            contentDescription = null,
            modifier = GlanceModifier.size(avatarDp.dp),
        )
    }
}

internal fun widgetDrawable(c: Character): Int = when (c) {
    Character.DRAGON -> R.drawable.char_dragon
    Character.BUNNY -> R.drawable.char_bunny
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot?, today: LocalDate) {
    val size = LocalSize.current
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(Background)
        .cornerRadius(24.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .clickable(actionStartActivity<MainActivity>())

    if (snapshot == null) {
        Column(modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💕 우리둘", style = TextStyle(color = OnBg, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            if (size.height >= 100.dp) {
                Spacer(GlanceModifier.height(4.dp))
                Text("앱을 열어 연결해 주세요", style = TextStyle(color = SubText, fontSize = 12.sp))
            }
        }
        return
    }

    val dday = DDay.shortText(snapshot.startDate, today, snapshot.countFromOne)
    val upcoming = DDay.upcoming(snapshot.startDate, today, snapshot.countFromOne, limit = 2)
    val names = "${snapshot.myName} ♥ ${snapshot.partnerName ?: "?"}"

    when {
        size.height < 100.dp -> {
            // 한 줄형
            Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dday, style = TextStyle(color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                upcoming.firstOrNull()?.takeIf { size.width >= 200.dp }?.let {
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        "${it.label} ${DDay.daysLeftText(it.daysLeft)}",
                        style = TextStyle(color = SubText, fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
            }
        }

        size.width >= 250.dp -> {
            // 가로 넓은형
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CharacterNames(snapshot, 26)
                    Text(dday, style = TextStyle(color = Primary, fontSize = 36.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    upcoming.forEach {
                        Text(it.label, style = TextStyle(color = OnBg, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(DDay.daysLeftText(it.daysLeft), style = TextStyle(color = SubText, fontSize = 12.sp), maxLines = 1)
                        Spacer(GlanceModifier.height(4.dp))
                    }
                }
            }
        }

        else -> {
            // 정사각형
            Column(modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                if (size.height >= 130.dp) CharacterNames(snapshot, 24) else Text(names, style = TextStyle(color = OnBg, fontSize = 12.sp), maxLines = 1)
                Text(dday, style = TextStyle(color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                upcoming.firstOrNull()?.let {
                    Text(
                        "${it.label} ${DDay.daysLeftText(it.daysLeft)}",
                        style = TextStyle(color = SubText, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

package com.uridul.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.uridul.app.MainActivity
import com.uridul.app.PhotoWidgetConfigActivity
import com.uridul.app.R
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.DDay
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 사진 디데이 위젯: 각자 고른 사진 위에 "후이 ♥ 나나 · 103일".
 * 사진은 이 폰 안(앱 전용 폴더)에만 저장되고 서버에 올라가지 않는다. 위젯마다 다른 사진을 쓸 수 있다.
 * 위젯을 길게 눌러 "설정"(위젯 재설정)으로 사진을 바꾼다.
 */
class PhotoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)
        val snapshot = WidgetStore.load(context)
        val photo = if (appWidgetId != -1) PhotoStore.loadForWidget(context, appWidgetId) else null
        provideContent { WidgetSettingsScope { PhotoContent(context, appWidgetId, snapshot, photo, LocalDate.now()) } }
    }
}

class PhotoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PhotoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightScheduler.schedule(context)
    }

    /** 위젯을 지우면 그 위젯 사진 파일도 지운다 */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { PhotoStore.delete(context, it) }
    }
}

/** 위젯 사진 파일 관리 (앱 전용 폴더: 다른 앱이 볼 수 없고, 앱을 지우면 함께 지워진다) */
object PhotoStore {
    /** 저장할 때 긴 쪽 최대 크기 (위젯 크기를 바꿔도 선명하도록 조금 넉넉히) */
    const val SAVE_MAX_SIDE = 1080
    /** 위젯에 넘길 때 긴 쪽 최대 크기 (위젯은 한 번에 넘길 수 있는 사진 크기에 제한이 있다) */
    private const val WIDGET_MAX_SIDE = 640

    private fun dir(context: Context) = File(context.filesDir, "widget_photos").apply { mkdirs() }

    fun file(context: Context, appWidgetId: Int) = File(dir(context), "$appWidgetId.jpg")

    fun has(context: Context, appWidgetId: Int) = file(context, appWidgetId).exists()

    fun save(context: Context, appWidgetId: Int, bitmap: Bitmap) {
        val target = file(context, appWidgetId)
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(target)
    }

    fun delete(context: Context, appWidgetId: Int) {
        file(context, appWidgetId).delete()
    }

    /** 위젯용으로 작게 줄여 읽는다 (알파 없는 RGB_565: 메모리 절반) */
    fun loadForWidget(context: Context, appWidgetId: Int): Bitmap? {
        val f = file(context, appWidgetId)
        if (!f.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WIDGET_MAX_SIDE) sample *= 2
            val decoded = BitmapFactory.decodeFile(
                f.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            ) ?: return null
            val long = maxOf(decoded.width, decoded.height)
            if (long <= WIDGET_MAX_SIDE) {
                decoded
            } else {
                val ratio = WIDGET_MAX_SIDE.toFloat() / long
                Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt(), (decoded.height * ratio).toInt(), true)
            }
        }.getOrNull()
    }
}

private val SinceFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val White = ColorProvider(day = Color.White, night = Color.White)
private val WhiteSoft = ColorProvider(day = Color(0xE6FFFFFF), night = Color(0xE6FFFFFF))

@Composable
private fun PhotoContent(
    context: Context,
    appWidgetId: Int,
    snapshot: WidgetSnapshot?,
    photo: Bitmap?,
    today: LocalDate,
) {
    val size = LocalSize.current
    val small = minOf(size.width, size.height) < 140.dp
    // 사진이 없으면 눌러서 바로 사진 고르기, 있으면 앱 열기
    val action = if (photo == null && appWidgetId != -1) {
        actionStartActivity(
            Intent(context, PhotoWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // 위젯마다 다른 PendingIntent 가 되도록
                .setData(Uri.parse("uridul-widget://photo/$appWidgetId")),
        )
    } else {
        actionStartActivity<MainActivity>(actionParametersOf(Widgets.KeyTab to "home"))
    }

    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(WidgetColors.Background)
            .clickable(action),
    ) {
        if (photo != null) {
            Image(
                provider = ImageProvider(photo),
                contentDescription = "우리 사진",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
            // 글자가 어떤 사진에서도 잘 읽히도록 아래쪽을 어둡게
            Image(
                provider = ImageProvider(R.drawable.photo_scrim),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }

        when {
            photo == null -> Column(
                GlanceModifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_add_photo),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(WidgetColors.Primary),
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
                Text(
                    "눌러서 사진 고르기",
                    style = TextStyle(color = WidgetColors.OnBg, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
                if (snapshot != null && !small) {
                    Text(
                        DDay.heroText(snapshot.startDate, today, snapshot.countFromOne),
                        style = TextStyle(color = WidgetColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            snapshot == null -> Column(
                GlanceModifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("앱을 열어 연결해 주세요", style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
            else -> Column(
                GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val names = listOfNotNull(snapshot.myName, snapshot.partnerName).joinToString(" ♥ ")
                Text(
                    names,
                    style = TextStyle(color = WhiteSoft, fontSize = if (small) 11.sp else 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    DDay.heroText(snapshot.startDate, today, snapshot.countFromOne),
                    style = TextStyle(color = White, fontSize = if (small) 24.sp else 34.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (!small) {
                    Text(
                        "${snapshot.startDate.format(SinceFormat)}부터",
                        style = TextStyle(color = WhiteSoft, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

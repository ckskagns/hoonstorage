package com.uridul.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.updateAll
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.MainActivity
import com.uridul.app.R
import com.uridul.app.data.PhotoMeta
import com.uridul.app.data.PhotoRepository
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.TimeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * "우리둘 상대 사진" 위젯: 상대가 일기에 가장 최근 올린 사진 + "나나가 올렸어요 · 3시간 전".
 * 사진은 서버의 작은 사진(긴 변 480px)을 이 폰에 저장해 두고 쓴다.
 * 새로 고침: 앱을 열 때, 위젯이 한 시간마다 스스로 (무료 요금제라 서버가 폰을 깨울 수 없어서 바로 뜨지는 않는다 → 시각을 함께 보여준다).
 */
class PartnerPhotoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PartnerPhotoStore.refreshFromServer(context)
        val saved = PartnerPhotoStore.load(context)
        provideContent { WidgetSettingsScope { PartnerContent(saved) } }
    }
}

class PartnerPhotoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PartnerPhotoWidget()
}

/** 상대 최근 사진 (폰에 저장해 둔 것) */
object PartnerPhotoStore {
    private const val PREFS = "partner_photo"
    private const val CHECK_EVERY_MS = 30 * 60 * 1000L

    class Saved(val bitmap: Bitmap, val uploadedAt: Long, val partnerName: String)

    private fun file(context: Context) = File(context.filesDir, "partner_photo.jpg")

    /** 새 사진이면 저장하고 위젯을 다시 그린다 (같은 사진이면 아무것도 안 함) */
    suspend fun update(context: Context, latest: PhotoMeta?, partnerName: String?) {
        if (latest == null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("id", null) == latest.id && file(context).exists()) return
        withContext(Dispatchers.IO) {
            file(context).writeBytes(latest.thumb)
            p.edit().putString("id", latest.id).putLong("at", latest.uploadedAtMillis).putString("name", partnerName.orEmpty()).apply()
        }
        runCatching { PartnerPhotoWidget().updateAll(context) }
    }

    /** 위젯이 그려질 때: 로그인돼 있으면 서버에 최근 사진을 물어 새 것이면 저장 (6초 안에 못 받으면 저장해 둔 것) */
    suspend fun refreshFromServer(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty() || FirebaseAuth.getInstance().currentUser == null) return
        // 다른 위젯과 함께 자주 다시 그려지므로 서버에는 30분에 한 번만 묻는다 (앱을 열면 앱이 따로 알려 준다)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checkedAt", 0L) in 0 until CHECK_EVERY_MS) return
        prefs.edit().putLong("checkedAt", now).apply()
        val snap = WidgetStore.load(context) ?: return
        val coupleId = snap.coupleId ?: return
        val myUid = snap.myUid ?: return
        val latest = withTimeoutOrNull(6_000) {
            runCatching { PhotoRepository(FirebaseFirestore.getInstance()).fetchLatest(coupleId, 5) }.getOrNull()
        }?.firstOrNull { it.uploadedBy != myUid } ?: return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("id", null) == latest.id && file(context).exists()) return
        withContext(Dispatchers.IO) {
            file(context).writeBytes(latest.thumb)
            p.edit().putString("id", latest.id).putLong("at", latest.uploadedAtMillis).putString("name", snap.partnerName.orEmpty()).apply()
        }
    }

    fun load(context: Context): Saved? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val f = file(context)
        if (!f.exists()) return null
        val bmp = runCatching {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        }.getOrNull() ?: return null
        return Saved(bmp, p.getLong("at", 0L), p.getString("name", null).orEmpty())
    }
}

private val White = ColorProvider(day = Color.White, night = Color.White)
private val WhiteSoft = ColorProvider(day = Color(0xE6FFFFFF), night = Color(0xE6FFFFFF))

@Composable
private fun PartnerContent(saved: PartnerPhotoStore.Saved?) {
    val small = minOf(LocalSize.current.width, LocalSize.current.height) < 140.dp
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(WidgetColors.Background)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(Widgets.KeyTab to "diary"))),
    ) {
        if (saved == null) {
            Column(
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
                    "상대가 일기에 사진을 올리면 여기에 보여요",
                    style = TextStyle(color = WidgetColors.OnBg, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
            return@Box
        }
        Image(
            provider = ImageProvider(saved.bitmap),
            contentDescription = "${saved.partnerName.ifBlank { "상대" }}가 올린 사진",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(R.drawable.photo_scrim),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                "${saved.partnerName.ifBlank { "상대" }}가 올렸어요",
                style = TextStyle(color = White, fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (saved.uploadedAt > 0) Text(
                TimeAgo.text(saved.uploadedAt, System.currentTimeMillis()),
                style = TextStyle(color = WhiteSoft, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

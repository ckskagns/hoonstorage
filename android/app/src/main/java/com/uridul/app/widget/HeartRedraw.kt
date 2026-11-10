package com.uridul.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.uridul.app.data.WidgetStore
import java.time.LocalDate

/**
 * 하트 위젯은 보이는 내용(디데이 숫자·날짜·이름)·디자인·앱 버전이 바뀌거나 폰을 다시 켰을 때만 다시 그린다.
 *
 * 꺼진 화면 시계(AOD)에서 위젯을 다시 그리면 하트 애니메이션이 멈춘다 (폴드8 측정).
 * 상대 일정 확인(15분마다)·앱 열기·6시간 정기 갱신 때마다 새로 그리던 것을 하트 위젯은 건너뛴다.
 *
 * 위젯마다 "마지막으로 그린 내용"(서명)을 이 폰에 적어 두고, 지금 그릴 내용과 같으면 그리지 않는다.
 * 다시 그릴 때는 바뀐 값을 위젯 상태에 함께 넣는다: Glance 는 그리기 세션이 살아 있는 동안
 * (마지막으로 그린 뒤 약 45초) 다시 그리라고 하면 처음 읽은 값을 그대로 쓰기 때문.
 */
internal object HeartRedraw {
    private const val PREFS = "heart_widget_drawn"
    private const val KEY_LAST_START = "last_start"
    private const val KEY_LAST_COUNT1 = "last_count_from_one"
    private const val KEY_LAST_ME = "last_me"
    private const val KEY_LAST_PARTNER = "last_partner"

    /**
     * 그릴 내용의 서명: 그림 판(앱 설치 시각/부팅 횟수) | 디자인 | 디데이 글자 | 사귄 날 | 이름.
     * 폰을 다시 켜면 홈·잠금 화면이 위젯 그림을 잃어버리므로 부팅 횟수가 바뀌면 다시 그린다
     * (에뮬레이터 재부팅으로 확인: 넣기 전에는 하트 위젯만 "불러오는 중"에 멈춤).
     */
    fun signatureOf(stamp: String, face: HeartFace, today: LocalDate, design: HeartDesign = HeartDesign.CLASSIC): String =
        "$stamp|${design.key}|${face.daysText(today) ?: "-"}|${face.start}|${face.myName}|${face.partnerName}"

    fun signature(context: Context, face: HeartFace, today: LocalDate, design: HeartDesign): String =
        signatureOf(stamp(context), face, today, design)

    /** 그림 판 (위젯 여러 개를 볼 때는 한 번만 구해 signatureOf 에 넘긴다: 앱 설치 정보 읽기를 줄이려고) */
    fun stamp(context: Context): String {
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        val boots = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        return "$installed/$boots"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDrawn(context: Context, appWidgetId: Int, sig: String): Boolean = prefs(context).getString(appWidgetId.toString(), null) == sig

    /** 그렸음: 위젯별 서명 + 마지막으로 그린 내용 (정기 갱신 때 저장소를 읽지 않고 비교하려고) */
    fun remember(context: Context, appWidgetId: Int, sig: String, face: HeartFace) {
        prefs(context).edit {
            putString(appWidgetId.toString(), sig)
            putString(KEY_LAST_START, face.start?.toString())
            putBoolean(KEY_LAST_COUNT1, face.countFromOne)
            putString(KEY_LAST_ME, face.myName)
            putString(KEY_LAST_PARTNER, face.partnerName)
        }
    }

    /** 마지막으로 그린 내용 (없으면 연결 전) */
    fun lastFace(context: Context): HeartFace {
        val p = prefs(context)
        val start = p.getString(KEY_LAST_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return HeartFace(start, p.getBoolean(KEY_LAST_COUNT1, true), p.getString(KEY_LAST_ME, null), p.getString(KEY_LAST_PARTNER, null))
    }

    /**
     * 정기 갱신(6시간)·재부팅: 그대로인 위젯은 건너뛰고, 그린 적 있는 위젯은 바로 다시 그린다.
     * Glance 로 처음 그려야 하는 위젯(새로 놓은 것, 칸 크기를 아직 모르는 것)만 돌려준다.
     */
    fun refreshStale(context: Context, appWidgetIds: IntArray): IntArray {
        val face = lastFace(context)
        val today = LocalDate.now()
        val stamp = stamp(context)
        return appWidgetIds.filter { id ->
            val design = HeartDesignStore.forWidget(context, id)
            val sig = signatureOf(stamp, face, today, design)
            when {
                isDrawn(context, id, sig) -> false
                wasDrawn(context, id) && drawNow(context, id, face, today, design) -> {
                    remember(context, id, sig, face)
                    false
                }
                else -> true
            }
        }.toIntArray()
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit { appWidgetIds.forEach { remove(it.toString()) } }
    }

    /**
     * 내용·디자인이 바뀐 하트 위젯만 다시 그린다 (바뀐 값을 위젯 상태에 함께 넣어서).
     *
     * 디데이 숫자만 바뀐 경우(자정)는 Glance 를 거치지 않고 자정 알람 안에서 바로 그려 넣는다 ([drawNow]).
     * Glance 는 그리기를 알람 처리가 끝난 뒤의 WorkManager 작업으로 돌리고 위젯마다 따로 그려서,
     * 삼성처럼 백그라운드 앱을 바로 얼리는 폰에서는 위젯마다 바뀌는 시각이 어긋날 수 있다.
     * (2026-11-10 "116일/117일 오락가락" 신고 때 폴드8 은 자정 6초 뒤 두 위젯 모두 제때 그렸음: 이것은 대비책)
     */
    suspend fun updateChanged(context: Context) {
        val face = HeartFace.of(WidgetStore.load(context))
        val today = LocalDate.now()
        val manager = GlanceAppWidgetManager(context)
        val widget = HeartWidget()
        val ids = manager.getGlanceIds(HeartWidget::class.java)
        if (ids.isEmpty()) return
        val stamp = stamp(context)
        // 사귄 날·이름·첫날 세는 법이 그대로면 날짜만 바뀐 것: 바로 그리기로 충분하다
        val sameFace = face == lastFace(context)
        for (id in ids) {
            val appWidgetId = manager.getAppWidgetId(id)
            val design = HeartDesignStore.forWidget(context, appWidgetId)
            val sig = signatureOf(stamp, face, today, design)
            if (isDrawn(context, appWidgetId, sig)) continue
            updateAppWidgetState(context, id) {
                face.writeTo(it)
                it[HeartFace.KEY_DESIGN] = design.key
            }
            val drawn = drawNow(context, appWidgetId, face, today, design)
            if (drawn) remember(context, appWidgetId, sig, face)
            // 내용이 바뀌었으면 Glance 도 새 값으로 다시 그린다 (살아 있는 그리기 세션이 옛 값을 들고 있을 수 있어서)
            if (!drawn || !sameFace) widget.update(context, id)
        }
    }

    /**
     * 위젯 칸 크기(세로·가로 화면 모두)에 맞춰 지금 바로 그려 넣는다. 위젯 칸 크기를 아직 모르면 false (Glance 로 그린다).
     * 하트 위젯은 원래 일반 위젯 레이아웃(RemoteViews)이라 Glance 가 그린 것과 같은 그림이다.
     */
    fun drawNow(context: Context, appWidgetId: Int, face: HeartFace, today: LocalDate, design: HeartDesign): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val sizes = runCatching { sizesOf(manager.getAppWidgetOptions(appWidgetId)) }.getOrDefault(emptyList())
        if (sizes.isEmpty()) return false
        fun draw(s: SizeF) = heartViews(context, design, face, today, DpSize(s.width.dp, s.height.dp), clickable = true)
        return runCatching {
            val views = if (sizes.size == 1) draw(sizes[0]) else RemoteViews(sizes.associateWith { draw(it) })
            manager.updateAppWidget(appWidgetId, views)
        }.isSuccess
    }

    /** 이 위젯을 한 번이라도 그린 적이 있는지 (새로 놓은 위젯은 Glance 로 처음 그린다) */
    private fun wasDrawn(context: Context, appWidgetId: Int): Boolean = prefs(context).contains(appWidgetId.toString())

    /** 위젯 칸 크기들 (Glance 의 SizeMode.Exact 와 같은 방법: 홈이 알려준 크기, 없으면 세로·가로 화면 크기) */
    internal fun sizesOf(options: Bundle?): List<SizeF> {
        if (options == null) return emptyList()
        BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.filter { it.width > 0f && it.height > 0f }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return listOf(SizeF(minW.toFloat(), maxH.toFloat()), SizeF(maxW.toFloat(), minH.toFloat()))
            .filter { it.width > 0f && it.height > 0f }
            .distinct()
    }
}

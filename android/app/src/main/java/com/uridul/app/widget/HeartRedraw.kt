package com.uridul.app.widget

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.DDay
import java.time.LocalDate

/** 하트 위젯에 보이는 내용을 정하는 값: 사귄 날(없으면 연결 전), 첫날을 1일로 셀지, 두 사람 이름 */
internal data class HeartFace(
    val start: LocalDate?,
    val countFromOne: Boolean = true,
    val myName: String? = null,
    val partnerName: String? = null,
) {

    /** "113일" / "D-3", 연결 전이면 null */
    fun daysText(today: LocalDate): String? = start?.let { DDay.heroText(it, today, countFromOne) }

    /** 위젯 상태(다시 그리기 신호와 함께 넣은 값)에 적기 */
    fun writeTo(p: MutablePreferences) {
        p[KEY_SET] = true
        if (start != null) p[KEY_START] = start.toString() else p.remove(KEY_START)
        p[KEY_COUNT1] = countFromOne
        if (myName != null) p[KEY_ME] = myName else p.remove(KEY_ME)
        if (partnerName != null) p[KEY_PARTNER] = partnerName else p.remove(KEY_PARTNER)
    }

    companion object {
        private val KEY_SET = booleanPreferencesKey("heart_face_set")
        private val KEY_START = stringPreferencesKey("heart_start")
        private val KEY_COUNT1 = booleanPreferencesKey("heart_count_from_one")
        private val KEY_ME = stringPreferencesKey("heart_me")
        private val KEY_PARTNER = stringPreferencesKey("heart_partner")
        /** 디자인을 바꾸면 위젯 상태도 바뀌어 다시 그려지게 */
        val KEY_DESIGN = stringPreferencesKey("heart_design")

        fun of(snapshot: WidgetSnapshot?) = HeartFace(snapshot?.startDate, snapshot?.countFromOne ?: true, snapshot?.myName, snapshot?.partnerName)

        /** 위젯 상태에 적힌 값 (한 번도 적지 않았으면 null) */
        fun from(p: Preferences): HeartFace? {
            if (p[KEY_SET] != true) return null
            return HeartFace(p[KEY_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, p[KEY_COUNT1] ?: true, p[KEY_ME], p[KEY_PARTNER])
        }
    }
}

/**
 * 하트 위젯 디자인 고르기 저장 (이 폰에만). 위젯마다 따로 고른 값이 먼저, 없으면 "모든 하트 위젯" 값, 없으면 지금 하트.
 */
internal object HeartDesignStore {
    private const val PREFS = "heart_widget_design"
    private const val KEY_ALL = "all"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun widgetKey(appWidgetId: Int) = "w$appWidgetId"

    fun forAll(context: Context): HeartDesign = HeartDesign.from(prefs(context).getString(KEY_ALL, null))

    fun forWidget(context: Context, appWidgetId: Int?): HeartDesign {
        val p = prefs(context)
        val own = appWidgetId?.let { p.getString(widgetKey(it), null) }
        return HeartDesign.from(own ?: p.getString(KEY_ALL, null))
    }

    /** 모든 하트 위젯 (따로 고른 위젯도 이 디자인으로 맞춘다) */
    fun setAll(context: Context, design: HeartDesign) {
        val p = prefs(context)
        val own = p.all.keys.filter { it.startsWith("w") }
        p.edit {
            own.forEach { remove(it) }
            putString(KEY_ALL, design.key)
        }
    }

    fun setWidget(context: Context, appWidgetId: Int, design: HeartDesign) {
        prefs(context).edit { putString(widgetKey(appWidgetId), design.key) }
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit { appWidgetIds.forEach { remove(widgetKey(it)) } }
    }
}

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

    private fun stamp(context: Context): String {
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

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit { appWidgetIds.forEach { remove(it.toString()) } }
    }

    /** 내용·디자인이 바뀐 하트 위젯만 다시 그린다 (바뀐 값을 위젯 상태에 함께 넣어서) */
    suspend fun updateChanged(context: Context) {
        val face = HeartFace.of(WidgetStore.load(context))
        val today = LocalDate.now()
        val manager = GlanceAppWidgetManager(context)
        val widget = HeartWidget()
        for (id in manager.getGlanceIds(HeartWidget::class.java)) {
            val appWidgetId = manager.getAppWidgetId(id)
            val design = HeartDesignStore.forWidget(context, appWidgetId)
            if (isDrawn(context, appWidgetId, signature(context, face, today, design))) continue
            updateAppWidgetState(context, id) {
                face.writeTo(it)
                it[HeartFace.KEY_DESIGN] = design.key
            }
            widget.update(context, id)
        }
    }
}

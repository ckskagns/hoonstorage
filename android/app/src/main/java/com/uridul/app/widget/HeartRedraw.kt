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

/** 하트 위젯에 보이는 내용을 정하는 값: 사귄 날(없으면 연결 전)과 첫날을 1일로 셀지 */
internal data class HeartFace(val start: LocalDate?, val countFromOne: Boolean = true) {

    /** "113일" / "D-3", 연결 전이면 null */
    fun daysText(today: LocalDate): String? = start?.let { DDay.heroText(it, today, countFromOne) }

    /** 위젯 상태(다시 그리기 신호와 함께 넣은 값)에 적기 */
    fun writeTo(p: MutablePreferences) {
        p[KEY_SET] = true
        if (start != null) p[KEY_START] = start.toString() else p.remove(KEY_START)
        p[KEY_COUNT1] = countFromOne
    }

    companion object {
        private val KEY_SET = booleanPreferencesKey("heart_face_set")
        private val KEY_START = stringPreferencesKey("heart_start")
        private val KEY_COUNT1 = booleanPreferencesKey("heart_count_from_one")

        fun of(snapshot: WidgetSnapshot?) = HeartFace(snapshot?.startDate, snapshot?.countFromOne ?: true)

        /** 위젯 상태에 적힌 값 (한 번도 적지 않았으면 null) */
        fun from(p: Preferences): HeartFace? {
            if (p[KEY_SET] != true) return null
            return HeartFace(p[KEY_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, p[KEY_COUNT1] ?: true)
        }
    }
}

/**
 * 하트 위젯은 보이는 내용(디데이 숫자·날짜)·앱 버전이 바뀌거나 폰을 다시 켰을 때만 다시 그린다.
 *
 * 꺼진 화면 시계(AOD)에서 위젯을 다시 그리면 하트 애니메이션이 멈춘다 (폴드8 측정).
 * 상대 일정 확인(15분마다)·앱 열기·6시간 정기 갱신 때마다 새로 그리던 것을 하트 위젯은 건너뛴다.
 *
 * 위젯마다 "마지막으로 그린 내용"(서명)을 이 폰에 적어 두고, 지금 그릴 내용과 같으면 그리지 않는다.
 * 다시 그릴 때는 바뀐 사귄 날을 위젯 상태에 함께 넣는다: Glance 는 그리기 세션이 살아 있는 동안
 * (마지막으로 그린 뒤 약 45초) 다시 그리라고 하면 처음 읽은 값을 그대로 쓰기 때문.
 */
internal object HeartRedraw {
    private const val PREFS = "heart_widget_drawn"
    private const val KEY_LAST_START = "last_start"
    private const val KEY_LAST_COUNT1 = "last_count_from_one"

    /**
     * 그릴 내용의 서명: 그림 판(앱 설치 시각/부팅 횟수) | 디데이 글자 | 사귄 날.
     * 폰을 다시 켜면 홈·잠금 화면이 위젯 그림을 잃어버리므로 부팅 횟수가 바뀌면 다시 그린다
     * (에뮬레이터 재부팅으로 확인: 넣기 전에는 하트 위젯만 "불러오는 중"에 멈춤).
     */
    fun signatureOf(stamp: String, face: HeartFace, today: LocalDate): String =
        "$stamp|${face.daysText(today) ?: "-"}|${face.start}"

    fun signature(context: Context, face: HeartFace, today: LocalDate): String = signatureOf(stamp(context), face, today)

    private fun stamp(context: Context): String {
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        val boots = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        return "$installed/$boots"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDrawn(context: Context, appWidgetId: Int, sig: String): Boolean = prefs(context).getString(appWidgetId.toString(), null) == sig

    /** 그렸음: 위젯별 서명 + 마지막으로 그린 사귄 날 (정기 갱신 때 서버·저장소를 읽지 않고 비교하려고) */
    fun remember(context: Context, appWidgetId: Int, sig: String, face: HeartFace) {
        prefs(context).edit {
            putString(appWidgetId.toString(), sig)
            putString(KEY_LAST_START, face.start?.toString())
            putBoolean(KEY_LAST_COUNT1, face.countFromOne)
        }
    }

    /** 마지막으로 그린 사귄 날 (없으면 연결 전) */
    fun lastFace(context: Context): HeartFace {
        val p = prefs(context)
        val start = p.getString(KEY_LAST_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return HeartFace(start, p.getBoolean(KEY_LAST_COUNT1, true))
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit { appWidgetIds.forEach { remove(it.toString()) } }
    }

    /** 내용이 바뀐 하트 위젯만 다시 그린다 (바뀐 사귄 날을 위젯 상태에 함께 넣어서) */
    suspend fun updateChanged(context: Context) {
        val face = HeartFace.of(WidgetStore.load(context))
        val sig = signature(context, face, LocalDate.now())
        val manager = GlanceAppWidgetManager(context)
        val widget = HeartWidget()
        for (id in manager.getGlanceIds(HeartWidget::class.java)) {
            if (isDrawn(context, manager.getAppWidgetId(id), sig)) continue
            updateAppWidgetState(context, id) { face.writeTo(it) }
            widget.update(context, id)
        }
    }
}

package com.uridul.app.widget

import android.content.Context
import androidx.compose.runtime.key
import androidx.core.content.edit

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

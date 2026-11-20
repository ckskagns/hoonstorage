package com.uridul.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 이 폰에서 일기장을 마지막으로 본 시각 (커플마다). 처음에는 지금으로 두어 점이 바로 뜨지 않게 */
object DiarySeen {
    private const val PREFS = "diary_seen"
    private val _seenAt = MutableStateFlow(Long.MAX_VALUE)
    val seenAt: StateFlow<Long> = _seenAt.asStateFlow()

    fun load(context: Context, coupleId: String) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getLong(coupleId, 0L)
        _seenAt.value = if (saved > 0) saved else System.currentTimeMillis().also { p.edit().putLong(coupleId, it).apply() }
    }

    fun mark(context: Context, coupleId: String) {
        val now = System.currentTimeMillis()
        _seenAt.value = now
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(coupleId, now).apply()
    }
}

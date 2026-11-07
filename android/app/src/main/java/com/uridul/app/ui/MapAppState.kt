package com.uridul.app.ui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.uridul.app.data.MapApp

/**
 * 지도 앱이 지금 켜져 있는지 (최근 앱에 화면이 남아 있는지).
 *
 * 안드로이드는 다른 앱이 켜져 있는지 그냥은 알려 주지 않는다.
 *  1) 폰 설정에서 우리둘에 "사용 정보 접근" 을 허용했으면: 그 앱 화면들의 마지막 상태(열림·멈춤·닫힘)로 정확히 판단
 *  2) 허용하지 않았으면: 우리둘이 그 앱을 열었거나 그 앱에서 장소를 공유받은 지 [RECENT_MILLIS] 안이면 켜져 있다고 본다
 */
internal object MapAppState {
    private const val PREFS = "map_apps"
    private const val RECENT_MILLIS = 3L * 60 * 60 * 1000
    private const val LOOKBACK_MILLIS = 24L * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 우리둘이 그 앱을 열었음 / 그 앱에서 공유받았음 */
    fun markSeen(context: Context, app: MapApp, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong("seen_${app.key}", now).apply()
    }

    fun isRunning(context: Context, app: MapApp): Boolean {
        val now = System.currentTimeMillis()
        usageRunning(context, app.packageName, now)?.let { return it }
        return isRecent(prefs(context).getLong("seen_${app.key}", 0L), now)
    }

    internal fun isRecent(seenAt: Long, now: Long): Boolean = seenAt > 0 && now - seenAt in 0 until RECENT_MILLIS

    /** "사용 정보 접근" 을 허용했는지 */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** 허용했으면 켜져 있는지, 허용하지 않았으면 null */
    private fun usageRunning(context: Context, pkg: String, now: Long): Boolean? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { usm.queryEvents(now - LOOKBACK_MILLIS, now) }.getOrNull() ?: return null
        val last = mutableMapOf<String, Int>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == pkg && e.eventType in TRACKED) last[e.className ?: ""] = e.eventType
        }
        return isAlive(last.values)
    }

    /** 화면이 닫힘 (안드로이드가 공개하지 않은 값이지만 사용 기록에는 24로 들어온다) */
    internal const val ACTIVITY_DESTROYED = 24

    private val TRACKED = setOf(
        UsageEvents.Event.ACTIVITY_RESUMED,
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED,
        ACTIVITY_DESTROYED,
    )

    /** 화면마다 마지막 상태: 하나라도 닫히지 않았으면 켜져 있음 */
    internal fun isAlive(lastStates: Collection<Int>): Boolean = lastStates.any { it != ACTIVITY_DESTROYED }
}

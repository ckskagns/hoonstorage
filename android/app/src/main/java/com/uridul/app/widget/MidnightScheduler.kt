package com.uridul.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 매일 자정 직후 위젯 숫자를 바꾼다.
 * 위젯 기본 갱신 주기(최소 30분)로는 날짜가 바뀌는 순간을 맞출 수 없어서 알람을 따로 건다.
 */
object MidnightScheduler {
    const val ACTION_MIDNIGHT = "com.uridul.app.action.MIDNIGHT_REFRESH"

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .plusSeconds(5)
            .toInstant()
            .toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}

/** 자정 알람, 재부팅, 시간/시간대 변경, 앱 업데이트 시 위젯을 다시 그리고 다음 자정 알람을 건다. */
class WidgetRefreshReceiver : BroadcastReceiver() {

    private val allowedActions = setOf(
        MidnightScheduler.ACTION_MIDNIGHT,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in allowedActions) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Widgets.updateAll(appContext)
            } finally {
                MidnightScheduler.schedule(appContext)
                pending.finish()
            }
        }
    }
}

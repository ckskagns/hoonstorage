package com.uridul.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uridul.app.MainActivity
import com.uridul.app.R
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.TimeSlot
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.SpecialDays
import com.uridul.app.ui.KoreanTime
import com.uridul.app.widget.MainExtras
import com.uridul.app.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 일정 알림 (폰 안에서 예약하는 알림이라 서버·유료 요금제가 필요 없다).
 *
 *  - 매일 아침 9시: 오늘 일정 + 오늘 기념일·생일·연인 기념일 + 내일 우리 기념일·생일 미리 알림
 *    (알림 시각에 서버에서 일정을 새로 받아오므로, 앱을 열지 않아도 상대가 추가한 일정까지 알려준다)
 *  - 시간이 정해진 오늘 일정: 30분 전에 한 번 더
 *
 * 두 사람 폰에서 각각 예약되므로 둘 다 같은 알림을 받는다.
 */
object Reminders {
    const val ACTION_DAILY = "com.uridul.app.action.DAILY_REMINDER"
    const val ACTION_EVENT = "com.uridul.app.action.EVENT_REMINDER"

    private const val CHANNEL_ID = "schedule"
    private const val DAILY_NOTIFICATION_ID = 1001
    private val DAILY_TIME: LocalTime = LocalTime.of(9, 0)
    const val LEAD_MINUTES = 30L

    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SCHEDULED = "scheduled_event_ids"
    private const val KEY_ASKED = "asked_permission"

    private const val EXTRA_ID = "event_id"
    private const val EXTRA_TITLE = "event_title"
    private const val EXTRA_TIME = "event_time"
    private const val EXTRA_DATE = "event_date"
    private const val EXTRA_PLACE = "event_place"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleDaily(context)
        } else {
            cancel(context, dailyIntent(context))
            cancelTimed(context)
        }
    }

    /** 알림 권한을 이미 한 번 물어봤는지 (거절했으면 다시 조르지 않는다) */
    fun wasPermissionAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "일정 당일 아침 9시와 일정 30분 전에 알려줘요"
            },
        )
    }

    /** 다음 아침 9시 알림 예약 (이미 지났으면 내일 9시) */
    fun scheduleDaily(context: Context) {
        if (!isEnabled(context)) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(DAILY_TIME)
        if (!at.isAfter(now)) at = at.plusDays(1)
        setAlarm(context, at, dailyIntent(context))
    }

    /**
     * 아침 알림: 오늘 일정과 기념일을 모아 한 번 알리고, 오늘 시간 일정의 30분 전 알림을 예약한다.
     * [force] 는 개발용 점검에서 알림 설정과 상관없이 보여줄 때만 쓴다.
     */
    suspend fun runDaily(context: Context, force: Boolean = false) {
        if (!isEnabled(context) && !force) return
        val snapshot = WidgetStore.load(context) ?: return
        val today = LocalDate.now()
        val events = Widgets.fetchUpcomingEvents(context, snapshot) ?: WidgetStore.loadEvents(context)
        val todayEvents = CalendarMath.eventsOn(events, today)
        val specials = SpecialDays.on(snapshot.startDate, snapshot.countFromOne, snapshot.birthdays, today)
        val tomorrow = SpecialDays.on(snapshot.startDate, snapshot.countFromOne, snapshot.birthdays, today.plusDays(1))
            .filter { it.kind.personal }

        val lines = buildList {
            specials.forEach { add("${it.emoji} 오늘은 ${it.title}" + if (it.kind.personal) "이에요" else "") }
            todayEvents.forEach { e ->
                if (e.allDay) {
                    add("종일  ${e.title}" + (e.place?.let { p -> "  📍${p.name}" } ?: ""))
                } else {
                    e.slots.forEach { s ->
                        val what = s.title.takeIf { it.isNotBlank() }?.let { "${e.title} · $it" } ?: e.title
                        add("${s.start.format(KoreanTime)}  $what" + (s.place?.let { p -> "  📍${p.name}" } ?: ""))
                    }
                }
            }
            tomorrow.forEach { add("${it.emoji} 내일은 ${it.title}이에요") }
        }
        if (lines.isNotEmpty()) {
            val title = when {
                todayEvents.isNotEmpty() -> "오늘 일정 ${todayEvents.size}개 💕"
                specials.isNotEmpty() -> "오늘은 ${specials.first().title} ${specials.first().emoji}"
                else -> "내일은 ${tomorrow.first().title} ${tomorrow.first().emoji}"
            }
            notify(context, DAILY_NOTIFICATION_ID, title, lines, today)
        }
        scheduleTimed(context, todayEvents)
    }

    /** 오늘 시간이 정해진 일정마다 30분 전 알림 예약 (앱에서 일정이 바뀔 때도 다시 부른다) */
    fun scheduleTimed(context: Context, todayEvents: List<CalendarEvent>) {
        cancelTimed(context)
        if (!isEnabled(context)) return
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val ids = mutableSetOf<String>()
        // 시간대마다 30분 전 (여러 날 일정은 첫날 시작 시각만)
        todayEvents.filter { it.time != null && it.date == today }.forEach { e ->
            e.slots.forEachIndexed { i, slot ->
                val at = today.atTime(slot.start).minusMinutes(LEAD_MINUTES)
                if (!at.isAfter(now)) return@forEachIndexed
                val key = "${e.id}#$i"
                setAlarm(context, at, eventIntent(context, key, e, slot))
                ids += key
            }
        }
        prefs(context).edit().putStringSet(KEY_SCHEDULED, ids).apply()
    }

    private fun cancelTimed(context: Context) {
        prefs(context).getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { id ->
            cancel(context, eventIntent(context, id))
        }
        prefs(context).edit().remove(KEY_SCHEDULED).apply()
    }

    fun showEventReminder(context: Context, intent: Intent) {
        if (!isEnabled(context)) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        notify(context, id.hashCode(), "${LEAD_MINUTES}분 뒤 일정이 있어요 ⏰", listOf("$time  $title" + (intent.getStringExtra(EXTRA_PLACE)?.let { "  📍$it" } ?: "")), date)
    }

    private fun timeText(e: CalendarEvent): String = e.time?.format(KoreanTime) ?: "종일"

    private fun notify(context: Context, id: Int, title: String, lines: List<String>, date: LocalDate) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainExtras.TAB, "calendar")
                .putExtra(MainExtras.DATE, date.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) }
        if (lines.size > 6) style.setSummaryText("외 ${lines.size - 6}개")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC03A62.toInt())
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun dailyIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        DAILY_NOTIFICATION_ID,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_DAILY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun eventIntent(context: Context, id: String, event: CalendarEvent? = null, slot: TimeSlot? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_EVENT)
            // 일정마다 다른 알람이 되도록 data 로 구분한다
            .setData(android.net.Uri.parse("uridul-reminder://event/$id"))
        if (event != null) {
            val title = slot?.title?.takeIf { it.isNotBlank() }?.let { "${event.title} · $it" } ?: event.title
            intent.putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TIME, (slot?.start ?: event.time)?.format(KoreanTime).orEmpty())
                .putExtra(EXTRA_DATE, event.date.toString())
                .putExtra(EXTRA_PLACE, slot?.place?.name ?: event.place?.name)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }


    private fun setAlarm(context: Context, at: LocalDateTime, pending: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun cancel(context: Context, pending: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
    }
}

/** 아침 9시 알림 / 일정 30분 전 알림 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            Reminders.ACTION_EVENT -> Reminders.showEventReminder(appContext, intent)
            Reminders.ACTION_DAILY -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        Reminders.runDaily(appContext)
                    } finally {
                        Reminders.scheduleDaily(appContext)
                        pending.finish()
                    }
                }
            }
        }
    }
}

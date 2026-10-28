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
import com.uridul.app.data.Remind
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
 *  - 시간이 정해진 일정: 일정마다 고른 시간(기본 30분 전 · 시작할 때 · 1시간 전 · 하루 전 등)에 한 번 더
 *  - 하루 종일 일정은 아침 알림에 들어가고, "전날·당일" 로 고르면 전날 아침 알림에도 들어간다
 *  - "알림 끄기" 로 고른 일정은 알리지 않는다
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
    private const val EXTRA_LEAD = "event_lead"

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

    /** 알림을 보낼 수 있는지 (안드로이드 13 미만은 따로 묻는 권한이 없어서 알림 설정만 본다) */
    fun hasPermission(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }

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
        // 알림을 끈 일정은 아침 알림에서도 뺀다
        val todayEvents = CalendarMath.eventsOn(events, today).filter { it.remindValue != Remind.OFF }
        // 하루 종일 일정 중 "전날·당일" 로 고른 내일 일정
        val tomorrowEvents = CalendarMath.eventsOn(events, today.plusDays(1))
            .filter { it.allDay && it.date == today.plusDays(1) && it.remindValue == Remind.DAY_BEFORE }
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
            tomorrowEvents.forEach { e -> add("내일  ${e.title}" + (e.place?.let { p -> "  📍${p.name}" } ?: "")) }
            tomorrow.forEach { add("${it.emoji} 내일은 ${it.title}이에요") }
        }
        if (lines.isNotEmpty()) {
            val title = when {
                todayEvents.isNotEmpty() -> "오늘 일정 ${todayEvents.size}개 💕"
                specials.isNotEmpty() -> "오늘은 ${specials.first().title} ${specials.first().emoji}"
                tomorrowEvents.isNotEmpty() -> "내일 일정이 있어요 💕"
                else -> "내일은 ${tomorrow.first().title} ${tomorrow.first().emoji}"
            }
            notify(context, DAILY_NOTIFICATION_ID, title, lines, today)
        }
        scheduleTimed(context, events)
    }

    /**
     * 시간 일정마다 고른 시간(기본 30분 전, 하루 전 등)에 알림 예약. 앞으로 이틀 안의 알림만 맞춘다
     * (그 뒤의 알림은 다음 아침 9시 알림이나 앱을 열 때 다시 맞춘다). 앱에서 일정이 바뀔 때도 다시 부른다.
     */
    fun scheduleTimed(context: Context, events: List<CalendarEvent>) {
        cancelTimed(context)
        if (!isEnabled(context)) return
        val now = LocalDateTime.now()
        val ids = mutableSetOf<String>()
        alarmsFor(events, now).forEach { a ->
            setAlarm(context, a.at, eventIntent(context, a.key, a.event, a.slot, a.lead))
            ids += a.key
        }
        prefs(context).edit().putStringSet(KEY_SCHEDULED, ids).apply()
    }

    /** 예약할 알림 하나 */
    internal data class Alarm(val key: String, val at: LocalDateTime, val event: CalendarEvent, val slot: TimeSlot, val lead: Int)

    /** 지금부터 [HORIZON_HOURS] 안에 울릴 시간 일정 알림 (여러 날 일정은 첫날 시작 시각만) */
    internal fun alarmsFor(events: List<CalendarEvent>, now: LocalDateTime): List<Alarm> {
        val until = now.plusHours(HORIZON_HOURS)
        return events.filter { it.time != null && it.remindValue != Remind.OFF }.flatMap { e ->
            val lead = e.remindValue
            e.slots.mapIndexedNotNull { i, slot ->
                val at = e.date.atTime(slot.start).minusMinutes(lead.toLong())
                if (at.isAfter(now) && !at.isAfter(until)) Alarm("${e.id}@${e.date}#$i", at, e, slot, lead) else null
            }
        }
    }

    private const val HORIZON_HOURS = 48L

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
        val lead = intent.getIntExtra(EXTRA_LEAD, LEAD_MINUTES.toInt())
        notify(context, id.hashCode(), "${Remind.leadText(lead)} ⏰", listOf("$time  $title" + (intent.getStringExtra(EXTRA_PLACE)?.let { "  📍$it" } ?: "")), date)
    }

    private fun timeText(e: CalendarEvent): String = e.time?.format(KoreanTime) ?: "종일"

    @android.annotation.SuppressLint("MissingPermission") // 바로 위 hasPermission 으로 확인한 뒤에만 보낸다
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

    private fun eventIntent(context: Context, id: String, event: CalendarEvent? = null, slot: TimeSlot? = null, lead: Int = LEAD_MINUTES.toInt()): PendingIntent {
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
                .putExtra(EXTRA_LEAD, lead)
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

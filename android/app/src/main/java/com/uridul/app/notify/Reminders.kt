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
import com.uridul.app.data.InboxItem
import com.uridul.app.data.NotificationInbox
import com.uridul.app.data.Remind
import com.uridul.app.data.WidgetStore
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.SpecialDays
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
 * 일정이 있는 날 아침 9시에 하루 한 번만 알린다. 알림 제목은 그날 일정 제목을 이어 붙인 것
 * ("영화 데이트, 저녁 약속, 카페…": 길면 잘라서). 일정 안의 세부 일정(시간대)·몇 분 전 알림은 보내지 않는다.
 *  - 오늘 기념일·생일도 같은 알림에 제목으로 들어간다
 *  - "알림 끄기" 로 고른 일정은 빠진다
 *  - 알림 시각에 서버에서 일정을 새로 받아오므로, 앱을 열지 않아도 상대가 추가한 일정까지 들어간다
 *
 * 두 사람 폰에서 각각 예약되므로 둘 다 같은 알림을 받는다.
 */
object Reminders {
    const val ACTION_DAILY = "com.uridul.app.action.DAILY_REMINDER"
    /** 예전 버전이 예약해 둔 "일정 몇 분 전" 알림 (울려도 무시하고, 앱이 켜질 때 지운다) */
    const val ACTION_EVENT = "com.uridul.app.action.EVENT_REMINDER"

    private const val CHANNEL_ID = "schedule"
    private const val DAILY_NOTIFICATION_ID = 1001
    private val DAILY_TIME: LocalTime = LocalTime.of(9, 0)

    /** 알림 제목 최대 글자 수 (넘으면 잘라서 "…") */
    const val TITLE_MAX = 24

    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SCHEDULED = "scheduled_event_ids"
    private const val KEY_ASKED = "asked_permission"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleDaily(context) else cancel(context, dailyIntent(context))
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

    /** 알림 채널 (이미 있으면 설명만 새로 고친다) */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "일정이 있는 날 아침 9시에 그날 일정 제목을 한 번 알려줘요"
            },
        )
    }

    /** 다음 아침 9시 알림 예약 (이미 지났으면 내일 9시) */
    fun scheduleDaily(context: Context) {
        cancelOldEventAlarms(context)
        if (!isEnabled(context)) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(DAILY_TIME)
        if (!at.isAfter(now)) at = at.plusDays(1)
        setAlarm(context, at, dailyIntent(context))
    }

    /**
     * 아침 알림: 오늘 기념일·일정 제목을 모아 한 번 알린다 (없으면 알리지 않는다).
     * [force] 는 개발용 점검에서 알림 설정과 상관없이 보여줄 때만 쓴다.
     */
    suspend fun runDaily(context: Context, force: Boolean = false) {
        if (!isEnabled(context) && !force) return
        val snapshot = WidgetStore.load(context) ?: return
        val today = LocalDate.now()
        val events = Widgets.fetchUpcomingEvents(context, snapshot) ?: WidgetStore.loadEvents(context)
        // 알림을 끈 일정은 뺀다. 일정 안의 세부 일정(시간대)은 알리지 않고 일정 제목만
        val todayEvents = CalendarMath.eventsOn(events, today).filter { it.remindValue != Remind.OFF }
        val specials = SpecialDays.on(snapshot.startDate, snapshot.countFromOne, snapshot.birthdays, today)
        val titles = dailyTitles(specials.map { "${it.emoji} ${it.title}" }, todayEvents.map { it.title })
        // 오늘 일정이 하나뿐이면 누를 때 그 일정을 바로 연다
        postDaily(context, titles, today, todayEvents.singleOrNull()?.takeIf { specials.isEmpty() }?.id)
    }

    /** 아침 알림 하나 보내기: 제목 = 이어 붙인 일정 제목 (없으면 보내지 않는다) */
    internal fun postDaily(context: Context, titles: List<String>, today: LocalDate, eventId: String?) {
        if (titles.isEmpty()) return
        notify(
            context,
            title = joinTitles(titles),
            text = if (titles.size > 1) "오늘 일정 ${titles.size}개 💕" else "오늘 일정 💕",
            fullText = titles.joinToString("\n"),
            date = today,
            eventId = eventId,
            inboxId = "daily-$today",
        )
    }

    /** 오늘 알릴 제목들: 기념일 먼저, 그다음 일정 (빈 제목·같은 제목은 한 번만) */
    internal fun dailyTitles(specials: List<String>, events: List<String>): List<String> =
        (specials + events).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** "가나다, 라마바, 사아…": 제목을 이어 붙이고 [max] 글자를 넘으면 잘라서 "…" */
    internal fun joinTitles(titles: List<String>, max: Int = TITLE_MAX): String {
        val joined = titles.joinToString(", ")
        if (joined.length <= max) return joined
        var cut = joined.take(max - 1)
        // 이모지(두 글자짜리) 가운데서 자르지 않게
        if (cut.lastOrNull()?.isHighSurrogate() == true) cut = cut.dropLast(1)
        return cut.trimEnd(' ', ',') + "…"
    }

    /** 예전 버전이 예약해 둔 "일정 몇 분 전" 알림을 지운다 (한 번 지우면 목록이 비어서 아무것도 안 한다) */
    private fun cancelOldEventAlarms(context: Context) {
        val ids = prefs(context).getStringSet(KEY_SCHEDULED, null) ?: return
        ids.forEach { id -> cancel(context, oldEventIntent(context, id)) }
        prefs(context).edit().remove(KEY_SCHEDULED).apply()
    }

    @android.annotation.SuppressLint("MissingPermission") // 바로 위 hasPermission 으로 확인한 뒤에만 보낸다
    private fun notify(
        context: Context,
        title: String,
        text: String,
        fullText: String,
        date: LocalDate,
        eventId: String?,
        inboxId: String,
    ) {
        // 앱 안 알림 보관함에는 제목 전체를 적어 둔다 (폰 알림을 꺼 두었어도 종 아이콘에서 볼 수 있게)
        NotificationInbox.add(context, InboxItem(inboxId, title, fullText, date, eventId, System.currentTimeMillis()))
        if (!hasPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            DAILY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainExtras.TAB, "calendar")
                .putExtra(MainExtras.DATE, date.toString())
                .putExtra(MainExtras.EVENT_ID, eventId)
                .putExtra(MainExtras.INBOX_ID, inboxId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC03A62.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(DAILY_NOTIFICATION_ID, notification) }
    }

    private fun dailyIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        DAILY_NOTIFICATION_ID,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_DAILY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 예전 버전의 일정 알림과 같은 모양 (지우려면 같은 모양이어야 한다) */
    private fun oldEventIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_EVENT)
            .setData(android.net.Uri.parse("uridul-reminder://event/$id")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

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

/** 아침 9시 알림 (예전 버전의 일정 몇 분 전 알림은 울려도 무시) */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_DAILY) return
        val appContext = context.applicationContext
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

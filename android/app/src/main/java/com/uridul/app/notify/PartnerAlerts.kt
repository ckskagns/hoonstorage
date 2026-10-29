package com.uridul.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.uridul.app.MainActivity
import com.uridul.app.R
import com.uridul.app.data.InboxItem
import com.uridul.app.data.NotificationInbox
import com.uridul.app.data.WidgetStore
import com.uridul.app.ui.KoreanMonthDay
import com.uridul.app.widget.MainExtras
import com.uridul.app.widget.Widgets
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 상대 일정 알림: 상대가 일정을 새로 만들거나 고치면 내 폰에 알림 + 🔔 목록에.
 *
 * 무료 요금제에서는 상대 폰으로 바로 보내는 푸시(서버 함수)를 쓸 수 없어서,
 * 내 폰이 약 15분마다(안드로이드가 정하는 대로, 인터넷 있을 때) 서버를 확인한다.
 * 마지막으로 확인한 뒤에 상대(updatedBy ≠ 나)가 바꾼 일정만 알린다. 지운 일정은 알 수 없다.
 */
object PartnerAlerts {
    private const val WORK_NAME = "partner-activity"
    private const val CHANNEL_ID = "partner"
    private const val PREFS = "partner_alerts"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST = "last_check_millis"
    private const val NOTIFICATION_ID = 2001

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 15분마다 확인 예약 (이미 있으면 그대로) */
    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val request = PeriodicWorkRequestBuilder<PartnerActivityWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "상대 일정 소식", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "상대가 일정을 새로 만들거나 고치면 알려줘요 (약 15분마다 확인)"
            },
        )
    }

    /** 바뀐 일정 하나 */
    internal data class Change(val id: String, val title: String, val date: LocalDate, val created: Boolean, val atMillis: Long)

    /**
     * 확인하기. 처음 한 번은 기준 시각만 적고 알리지 않는다 (예전 일정이 한꺼번에 오지 않게).
     * [force] 는 개발용 점검에서 알림 설정과 상관없이 확인할 때만.
     * @return 알린 일정 수
     */
    suspend fun check(context: Context, force: Boolean = false): Int {
        if (!isEnabled(context) && !force) return 0
        if (FirebaseApp.getApps(context).isEmpty() || FirebaseAuth.getInstance().currentUser == null) return 0
        val snapshot = WidgetStore.load(context) ?: return 0
        val coupleId = snapshot.coupleId ?: return 0
        val myUid = snapshot.myUid ?: return 0
        val now = System.currentTimeMillis()
        val last = prefs(context).getLong(KEY_LAST, 0L)
        if (last == 0L) {
            prefs(context).edit().putLong(KEY_LAST, now).apply()
            return 0
        }
        val docs = withTimeoutOrNull(15_000) {
            FirebaseFirestore.getInstance().collection("couples").document(coupleId).collection("events")
                .whereGreaterThan("updatedAt", Timestamp(last / 1000, ((last % 1000) * 1_000_000).toInt()))
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .limit(50)
                .get().await().documents
        } ?: return 0
        val changes = docs.mapNotNull { d ->
            val by = d.getString("updatedBy") ?: return@mapNotNull null
            if (by == myUid) return@mapNotNull null
            val title = d.getString("title") ?: return@mapNotNull null
            val date = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val updated = d.getTimestamp("updatedAt")?.toDate()?.time ?: return@mapNotNull null
            val created = d.getTimestamp("createdAt")?.toDate()?.time ?: updated
            Change(d.id, title, date, created > last, updated)
        }
        // 다음에는 이번에 본 가장 늦은 시각 이후만
        val newest = docs.mapNotNull { it.getTimestamp("updatedAt")?.toDate()?.time }.maxOrNull()
        prefs(context).edit().putLong(KEY_LAST, maxOf(last, newest ?: last)).apply()
        if (changes.isEmpty()) return 0
        val who = snapshot.partnerName ?: "상대"
        val (title, lines) = describe(who, changes)
        notify(context, title, lines, changes)
        // 홈 화면 위젯도 바로 새로
        runCatching { Widgets.updateAll(context) }
        return changes.size
    }

    /** 알림 제목·줄: "나나가 일정을 추가했어요 💕" / "11월 2일 (월) · 저녁 데이트" */
    internal fun describe(who: String, changes: List<Change>): Pair<String, List<String>> {
        val title = when {
            changes.size == 1 && changes[0].created -> "${who}가 일정을 추가했어요 💕"
            changes.size == 1 -> "${who}가 일정을 고쳤어요 ✏️"
            changes.all { it.created } -> "${who}가 일정 ${changes.size}개를 추가했어요 💕"
            else -> "${who}가 일정 ${changes.size}개를 바꿨어요 ✏️"
        }
        val lines = changes.map { c -> "${if (c.created) "새 일정" else "고침"} · ${c.date.format(KoreanMonthDay)} · ${c.title}" }
        return title to lines
    }

    @android.annotation.SuppressLint("MissingPermission") // hasPermission 으로 확인한 뒤에만 보낸다
    private fun notify(context: Context, title: String, lines: List<String>, changes: List<Change>) {
        val single = changes.singleOrNull()
        val date = single?.date ?: changes.maxOf { it.date }
        val inboxId = "partner-${changes.maxOf { it.atMillis }}"
        // 🔔 앱 안 알림에도 (폰 알림을 꺼 두었어도 볼 수 있게)
        NotificationInbox.add(context, InboxItem(inboxId, title, lines.joinToString("\n"), date, single?.id, System.currentTimeMillis()))
        if (!Reminders.hasPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainExtras.TAB, "calendar")
                .putExtra(MainExtras.DATE, date.toString())
                .putExtra(MainExtras.EVENT_ID, single?.id)
                .putExtra(MainExtras.INBOX_ID, inboxId),
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
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

/** 15분마다 깨어나서 상대 일정 변경을 확인 */
class PartnerActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { PartnerAlerts.check(applicationContext) }
        return Result.success()
    }
}

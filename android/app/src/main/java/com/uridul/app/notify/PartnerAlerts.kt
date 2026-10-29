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
import com.google.firebase.firestore.DocumentSnapshot
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
 * 상대 소식 알림: 상대가 일정을 만들거나 고치고, 가고 싶은 곳을 담거나 다녀왔어요를 누르고, 후기를 남기면 내 폰에 알림 + 🔔 목록에.
 *
 * 무료 요금제에서는 상대 폰으로 바로 보내는 푸시(서버 함수)를 쓸 수 없어서,
 * 내 폰이 약 15분마다(안드로이드가 정하는 대로, 인터넷 있을 때) 서버를 확인한다.
 * 마지막으로 확인한 뒤에 상대(updatedBy ≠ 나)가 바꾼 것만 알린다. 지운 것·메모만 고친 것은 알리지 않는다.
 */
object PartnerAlerts {
    private const val WORK_NAME = "partner-activity"
    private const val CHANNEL_ID = "partner"
    private const val PREFS = "partner_alerts"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST = "last_check_millis"
    private const val KEY_VISITED = "visited_notified"
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
            NotificationChannel(CHANNEL_ID, "상대 소식", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "상대가 일정·가고 싶은 곳·후기를 남기면 알려줘요 (약 15분마다 확인)"
            },
        )
    }

    /** 상대 소식 종류 */
    enum class Kind { EVENT, WISH, REVIEW }

    /**
     * 상대가 바꾼 것 하나.
     *  - 일정: created = 새로 만듦 / 아니면 고침
     *  - 가고 싶은 곳: created = 새로 담음 / 아니면 다녀왔어요 표시
     *  - 후기: 남기거나 고침 ([detail] = 하트·한 줄)
     */
    internal data class Change(
        val id: String,
        val title: String,
        val date: LocalDate,
        val created: Boolean,
        val atMillis: Long,
        val kind: Kind = Kind.EVENT,
        val detail: String = "",
    )

    /**
     * 확인하기 (일정 · 가고 싶은 곳 · 후기). 처음 한 번은 기준 시각만 적고 알리지 않는다 (예전 것이 한꺼번에 오지 않게).
     * [force] 는 개발용 점검에서 알림 설정과 상관없이 확인할 때만.
     * @return 알린 소식 수
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
        val couple = FirebaseFirestore.getInstance().collection("couples").document(coupleId)
        val since = Timestamp(last / 1000, ((last % 1000) * 1_000_000).toInt())
        suspend fun changed(collection: String): List<DocumentSnapshot> = withTimeoutOrNull(15_000) {
            couple.collection(collection)
                .whereGreaterThan("updatedAt", since)
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .limit(50)
                .get().await().documents
        }.orEmpty()
        val events = changed("events")
        val wishes = changed("wishes")
        val reviews = changed("reviews")
        // "다녀왔어요" 는 한 번만 알린다 (그 뒤 메모만 고쳐도 다시 알리지 않게): 알린 장소를 기억
        val visitedSeen = prefs(context).getStringSet(KEY_VISITED, emptySet()).orEmpty().toMutableSet()
        val wishChanges = wishes.mapNotNull { d ->
            val visited = d.getBoolean("visited") == true
            if (!visited) visitedSeen.remove(d.id)
            wishChange(d, myUid, last)?.takeIf { c -> c.created || visitedSeen.add(c.id) }
                ?.also { c -> if (visited) visitedSeen.add(c.id) }
        }
        prefs(context).edit().putStringSet(KEY_VISITED, visitedSeen.toSet()).apply()
        val changes = (
            events.mapNotNull { eventChange(it, myUid, last) } +
                wishChanges +
                reviews.mapNotNull { reviewChange(it, myUid) }
            ).sortedBy { it.atMillis }
        // 다음에는 이번에 본 가장 늦은 시각 이후만
        val newest = (events + wishes + reviews).mapNotNull { it.getTimestamp("updatedAt")?.toDate()?.time }.maxOrNull()
        prefs(context).edit().putLong(KEY_LAST, maxOf(last, newest ?: last)).apply()
        if (changes.isEmpty()) return 0
        val who = snapshot.partnerName ?: "상대"
        val (title, lines) = describe(who, changes)
        notify(context, title, lines, changes)
        // 일정이 바뀌었으면 홈 화면 위젯도 바로 새로
        if (changes.any { it.kind == Kind.EVENT }) runCatching { Widgets.updateAll(context) }
        return changes.size
    }

    private fun DocumentSnapshot.updatedMillis(): Long? = getTimestamp("updatedAt")?.toDate()?.time

    private fun eventChange(d: DocumentSnapshot, myUid: String, last: Long): Change? {
        val by = d.getString("updatedBy") ?: return null
        if (by == myUid) return null
        val title = d.getString("title") ?: return null
        val date = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val updated = d.updatedMillis() ?: return null
        val created = d.getTimestamp("createdAt")?.toDate()?.time ?: updated
        return Change(d.id, title, date, created > last, updated, Kind.EVENT)
    }

    private fun wishChange(d: DocumentSnapshot, myUid: String, last: Long): Change? {
        val by = d.getString("updatedBy") ?: return null
        if (by == myUid) return null
        val name = d.getString("name") ?: return null
        val updated = d.updatedMillis() ?: return null
        val created = (d.getTimestamp("createdAt")?.toDate()?.time ?: updated) > last
        // 메모만 고친 것은 알리지 않는다 (새로 담음 · 다녀왔어요만)
        if (!created && d.getBoolean("visited") != true) return null
        return Change(d.id, name, LocalDate.now(), created, updated, Kind.WISH)
    }

    private fun reviewChange(d: DocumentSnapshot, myUid: String): Change? {
        val by = d.getString("updatedBy") ?: return null
        if (by == myUid) return null
        // 상대 후기가 남아 있을 때만 (지운 것은 알리지 않음)
        val entry = (d.get("reviews") as? Map<*, *>)?.get(by) as? Map<*, *> ?: return null
        val hearts = (entry["hearts"] as? Number)?.toInt()?.coerceIn(1, 5) ?: return null
        val text = (entry["text"] as? String).orEmpty()
        val date = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val eventId = d.getString("eventId") ?: return null
        val updated = d.updatedMillis() ?: return null
        val heartsText = "♥".repeat(hearts) + "♡".repeat(5 - hearts)
        val quote = if (text.isNotBlank()) " “$text”" else ""
        return Change(eventId, d.getString("title").orEmpty(), date, true, updated, Kind.REVIEW, heartsText + quote)
    }

    /** 알림 제목·줄: "나나가 일정을 추가했어요 💕" / "📅 새 일정 · 11월 2일 (월) · 저녁 데이트" */
    internal fun describe(who: String, changes: List<Change>): Pair<String, List<String>> {
        val kinds = changes.map { it.kind }.toSet()
        val single = changes.singleOrNull()
        val title = when {
            single != null -> when (single.kind) {
                Kind.EVENT -> if (single.created) "${who}가 일정을 추가했어요 💕" else "${who}가 일정을 고쳤어요 ✏️"
                Kind.WISH -> if (single.created) "${who}가 가고 싶은 곳을 담았어요 📍" else "${who}가 다녀온 곳을 표시했어요 ✅"
                Kind.REVIEW -> "${who}가 후기를 남겼어요 💗"
            }
            kinds == setOf(Kind.EVENT) && changes.all { it.created } -> "${who}가 일정 ${changes.size}개를 추가했어요 💕"
            kinds == setOf(Kind.EVENT) -> "${who}가 일정 ${changes.size}개를 바꿨어요 ✏️"
            kinds == setOf(Kind.WISH) -> "${who}가 가고 싶은 곳 ${changes.size}곳을 바꿨어요 📍"
            kinds == setOf(Kind.REVIEW) -> "${who}가 후기 ${changes.size}개를 남겼어요 💗"
            else -> "$who 소식 ${changes.size}개 💕"
        }
        val lines = changes.map { c ->
            when (c.kind) {
                Kind.EVENT -> "📅 ${if (c.created) "새 일정" else "고친 일정"} · ${c.date.format(KoreanMonthDay)} · ${c.title}"
                Kind.WISH -> "📍 ${if (c.created) "가고 싶은 곳" else "다녀왔어요"} · ${c.title}"
                Kind.REVIEW -> "💗 후기 · ${c.date.format(KoreanMonthDay)} ${c.title} ${c.detail}".trimEnd()
            }
        }
        return title to lines
    }

    /** 알림을 누르면 갈 곳: 가고 싶은 곳만 바뀌었으면 그 탭, 아니면 캘린더의 그 날(하나면 그 일정) */
    internal data class Target(val tab: String, val date: LocalDate, val eventId: String?)

    internal fun targetOf(changes: List<Change>): Target {
        if (changes.all { it.kind == Kind.WISH }) return Target("wish", LocalDate.now(), null)
        val dated = changes.filter { it.kind != Kind.WISH }
        val single = dated.singleOrNull()
        return Target("calendar", single?.date ?: dated.maxOf { it.date }, single?.id)
    }

    @android.annotation.SuppressLint("MissingPermission") // hasPermission 으로 확인한 뒤에만 보낸다
    private fun notify(context: Context, title: String, lines: List<String>, changes: List<Change>) {
        val target = targetOf(changes)
        val inboxId = "partner-${changes.maxOf { it.atMillis }}"
        // 🔔 앱 안 알림에도 (폰 알림을 꺼 두었어도 볼 수 있게)
        NotificationInbox.add(
            context,
            InboxItem(inboxId, title, lines.joinToString("\n"), target.date, target.eventId, System.currentTimeMillis(), tab = target.tab),
        )
        if (!Reminders.hasPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainExtras.TAB, target.tab)
                .putExtra(MainExtras.DATE, target.date.toString())
                .putExtra(MainExtras.EVENT_ID, target.eventId)
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

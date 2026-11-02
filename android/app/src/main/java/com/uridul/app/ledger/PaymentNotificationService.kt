package com.uridul.app.ledger

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.data.WidgetStore

/**
 * 결제 알림 받기. 앱 안 "자동 기록"이 켜져 있고 폰 설정에서 알림 접근을 허용해야 동작한다.
 * 허용된 금융 앱(PaymentSources: 지금은 토스) 알림만 읽고, 다른 앱 알림은 열어 보지도 저장하지도 않는다.
 */
class PaymentNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val parser = PaymentSources.parserFor(sbn.packageName) ?: return
        if (!PaymentInbox.isEnabled(this)) return
        if (sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val text = NotificationText(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"),
        )
        // 알림이 갱신되면 postTime 은 바뀌지만 when(결제 시각)은 그대로라 두 번 기록되지 않는다
        val at = n.`when`.takeIf { it > 0 && it <= sbn.postTime + 60_000L } ?: sbn.postTime
        val capture = PaymentCapture.from(text, parser, at, PaymentInbox.matchers(this)) ?: return
        if (PaymentInbox.add(this, capture) && capture.assetId != null) LedgerUploadWorker.enqueue(this)
    }

    companion object {
        /** 폰 설정에서 이 앱의 알림 접근이 허용돼 있는지 */
        fun isAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, PaymentNotificationService::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

/** 폰 보관함의 결제를 서버에 올린다 (인터넷이 될 때, 실패하면 나중에 다시) */
class LedgerUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val pending = PaymentInbox.pendingUploads(ctx)
        if (pending.isEmpty()) return Result.success()
        if (FirebaseApp.getApps(ctx).isEmpty() || FirebaseAuth.getInstance().currentUser == null) return Result.retry()
        val snapshot = WidgetStore.load(ctx) ?: return Result.retry()
        val coupleId = snapshot.coupleId ?: return Result.retry()
        val myUid = snapshot.myUid ?: return Result.retry()
        val repo = LedgerRepository(FirebaseFirestore.getInstance())
        repo.ensureDefaultAssets(ctx, coupleId, myUid)
        val done = mutableSetOf<String>()
        for (c in pending) {
            val name = c.parsed.payerName
            // "○○님이 결제" 의 이름이 내 이름이면 나, 아니면 이름만 적어 둔다
            val paidBy = if (name != null && name == snapshot.myName) myUid else null
            val ok = runCatching { repo.uploadCapture(coupleId, myUid, c, paidBy, name?.takeIf { paidBy == null }) }.getOrDefault(false)
            if (ok) done += c.key
        }
        PaymentInbox.removeUploaded(ctx, done)
        return if (done.size == pending.size) Result.success() else Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<LedgerUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("ledger-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

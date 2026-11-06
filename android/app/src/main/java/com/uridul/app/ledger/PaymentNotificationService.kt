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
 * 허용된 앱(PaymentSources: 토스, 카카오톡 "토스뱅크" 알림톡)만 읽고, 다른 앱·대화 알림은 열어 보지도 저장하지도 않는다.
 */
class PaymentNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(this, sbn)

    companion object {
        /** 알림 한 건 처리 (개발 빌드의 시험 알림도 이 길로 들어온다) */
        fun handle(context: Context, sbn: StatusBarNotification) {
            val source = PaymentSources.sourceFor(sbn.packageName) ?: return
            if (!PaymentInbox.isEnabled(context)) return
            if (sbn.isOngoing) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val extras = n.extras ?: return
            // 카카오톡처럼 여러 대화가 섞인 앱은 보낸 쪽(제목)만 먼저 보고, 토스뱅크가 아니면 본문을 읽지 않고 끝낸다
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            if (!source.acceptsSender(title, conversation)) return
            val text = NotificationText(
                packageName = sbn.packageName,
                title = title,
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"),
                messages = lastMessage(extras),
            )
            // 알림이 갱신되면 postTime 은 바뀌지만 when(결제 시각)은 그대로라 두 번 기록되지 않는다
            val at = n.`when`.takeIf { it > 0 && it <= sbn.postTime + 60_000L } ?: sbn.postTime
            val result = intake(context, text, source.parser, at)
            PaymentInbox.noteIntake(context, source.packageName, result)
        }

        /** CHCH 카드·통장 알림만 골라 글을 풀고 보관함에 넣는다 */
        private fun intake(context: Context, text: NotificationText, parser: PaymentNotificationParser, at: Long): IntakeResult {
            if (!PaymentFilter.accepts(text)) return IntakeResult.OTHER_ACCOUNT
            val capture = PaymentCapture.from(text, parser, at, PaymentInbox.matchers(context)) ?: return IntakeResult.NOT_PAYMENT
            if (!PaymentInbox.add(context, capture)) return IntakeResult.DUPLICATE
            if (capture.assetId != null) LedgerUploadWorker.enqueue(context)
            return IntakeResult.RECORDED
        }

        /** 대화형 알림의 마지막 메시지 글 (카카오톡은 본문이 여기에만 다 들어 있을 수 있다) */
        @Suppress("DEPRECATION")
        private fun lastMessage(extras: android.os.Bundle): String? =
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.lastOrNull()
                ?.let { it as? android.os.Bundle }
                ?.getCharSequence("text")
                ?.toString()

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
        // 결제한 사람 이름(가려진 이름) 연결을 서버에서 새로 받아 둔다
        if (PaymentInbox.payerSyncDue(ctx)) runCatching { PaymentInbox.syncPayerNames(ctx, repo.payerNames(coupleId)) }
        val done = mutableSetOf<String>()
        // 같은 가맹점 결제가 여러 건이면 학습표는 한 번만 읽는다
        val learnedCache = mutableMapOf<String, String?>()
        for (c in pending) {
            val name = c.parsed.payerName
            // 알림 속 이름: 내 이름이거나, 전에 "나"로 고른 (가려진) 이름이면 나 / "상대"로 고른 이름이면 상대 이름 / 모르면 그 이름 그대로
            val role = name?.let { PaymentInbox.payerRole(ctx, it) }
            val paidBy = if (name != null && (name == snapshot.myName || role == PaymentInbox.PAYER_ME)) myUid else null
            val paidByName = when {
                paidBy != null -> null
                role == PaymentInbox.PAYER_PARTNER -> snapshot.partnerName ?: name
                else -> name
            }
            val ok = runCatching { repo.uploadCapture(coupleId, myUid, c, paidBy, paidByName, learnedCache) }.getOrDefault(false)
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

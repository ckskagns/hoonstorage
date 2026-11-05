package com.uridul.app

import android.app.Notification
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import com.uridul.app.ledger.PaymentNotificationService
import com.uridul.app.ledger.PaymentSources

/**
 * 개발용: 카카오톡 "토스뱅크" 알림톡 모양의 시험 알림을 가계부 알림 처리에 그대로 넣어 본다.
 * 실제 알림은 띄우지 않고, 금액·가맹점은 예시(가맹점 이름 test0…)라 시험 뒤 지우기 쉽다.
 * adb 만 부를 수 있다 (DUMP 권한으로 막음).
 *
 *   adb shell am broadcast -n com.uridul.app/.DebugPaymentReceiver --es sample pay
 *   sample: pay(카드 결제) · pay_msg(대화형 알림) · cancel · deposit · personal(개인 카드) · chat(다른 대화방)
 */
class DebugPaymentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 에뮬레이터처럼 앱에서 자동 기록을 켜지 않은 곳에서 시험할 때: --ez enable true
        if (intent.getBooleanExtra("enable", false)) com.uridul.app.ledger.PaymentInbox.setEnabled(context, true)
        val sample = intent.getStringExtra("sample") ?: "pay"
        val (title, body) = when (sample) {
            "cancel" -> "토스뱅크" to "[토스뱅크] 체크카드 결제 취소\n테스트님의 CHCH 카드\n1,230원 결제 취소 | test0시험상점\n잔액 10,000원"
            "deposit" -> "토스뱅크" to "[토스뱅크] 입금\n테스트님의 CHCH 모임통장\n2,000원 입금 | test0보낸사람\n잔액 12,000원"
            "personal" -> "토스뱅크" to "[토스뱅크] 체크카드 국내 결제\n테스트님의 체크카드\n3,400원 결제 | test0개인상점\n잔액 5,000원"
            // 다른 대화방: 본문에 CHCH 카드가 있어도 보낸 쪽이 토스뱅크가 아니라 읽지 않아야 한다
            "chat" -> "친구" to "[토스뱅크] 체크카드 국내 결제\n테스트님의 CHCH 카드\n4,560원 결제 | test0대화방\n잔액 1원"
            else -> "토스뱅크" to "[토스뱅크] 체크카드 국내 결제\n테스트님의 CHCH 카드\n1,230원 결제 | test0시험상점\n잔액 11,230원"
        }
        val now = System.currentTimeMillis()
        val builder = Notification.Builder(context, "debug").setSmallIcon(android.R.drawable.ic_dialog_info).setWhen(now)
        if (sample == "pay_msg") {
            // 카카오톡처럼 본문이 메시지 칸에만 다 들어 있는 경우
            val sender = Person.Builder().setName(title).build()
            builder.setContentTitle(title).setContentText(body.lineSequence().first())
                .setStyle(Notification.MessagingStyle(sender).addMessage(body, now, sender))
        } else {
            builder.setContentTitle(title).setContentText(body.lineSequence().first()).setStyle(Notification.BigTextStyle().bigText(body))
        }
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            PaymentSources.KAKAO, PaymentSources.KAKAO, sample.hashCode(), null, Process.myUid(), 0, 0,
            builder.build(), Process.myUserHandle(), now,
        )
        PaymentNotificationService.handle(context, sbn)
    }
}

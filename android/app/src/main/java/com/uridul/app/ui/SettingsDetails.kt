package com.uridul.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

// 설정 상세 페이지에 들어가는 큰 묶음: 알림 점검(알림), 위젯 놓기(위젯), 라이선스(앱 정보)

/**
 * 알림 점검: 폰 설정 세 가지(알림 허용·정확한 알람·배터리 절전 예외)를 ✅/⚠️ 로.
 * 설정에서 돌아오면 다시 확인한다. 우리는 설정 화면을 열어 줄 뿐 직접 바꾸지 않는다.
 */
@Composable
internal fun NotifyCheck() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val checks = remember(tick) { NotifyChecks.of(context) }
    Group {
        checks.forEach { c ->
            ActionRow(
                (if (c.ok) "✅ " else "⚠️ ") + c.title,
                if (c.ok) c.okText else c.fixText,
            ) { runCatching { context.startActivity(c.intent(context)) } }
        }
    }
}

/** 알림 점검 항목 (폰 설정 읽기만) */
internal object NotifyChecks {
    class Check(val title: String, val ok: Boolean, val okText: String, val fixText: String, val intent: (Context) -> Intent)

    fun of(context: Context): List<Check> {
        val pkg = context.packageName
        val alarms = context.getSystemService(android.app.AlarmManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return listOf(
            Check(
                "알림 허용", NotificationManagerCompat.from(context).areNotificationsEnabled(),
                "폰 설정에서 우리둘 알림이 켜져 있어요", "우리둘 알림이 꺼져 있어요 · 눌러서 켜 주세요",
            ) { Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg) },
            Check(
                "정확한 시각 알람", alarms?.canScheduleExactAlarms() ?: true,
                "아침 9시 알림이 제때 울려요", "아침 알림이 늦게 올 수 있어요 · 눌러서 허용해 주세요",
            ) { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$pkg".toUri()) },
            Check(
                "배터리 절전 예외", power?.isIgnoringBatteryOptimizations(pkg) ?: true,
                "폰이 쉬는 동안에도 상대 소식을 확인해요", "삼성 절전 때문에 상대 소식이 늦을 수 있어요 · 눌러서 우리둘을 '제한 없음'으로",
            ) { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
        )
    }
}

/** 홈 화면에 놓을 수 있는 위젯들 (누르면 안드로이드 "위젯 놓기" 요청) */
@Composable
internal fun WidgetPinList() {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val supported = remember { manager.isRequestPinAppWidgetSupported }
    val widgets = remember {
        listOf(
            Triple("💗 우리둘 하트", "잠금 화면에도 놓을 수 있는 하트 디데이", com.uridul.app.widget.HeartWidgetReceiver::class.java),
            Triple("📆 우리둘 디데이", "두 캐릭터와 함께한 날", com.uridul.app.widget.DDayWidgetReceiver::class.java),
            Triple("🖼 우리둘 사진", "내가 고른 사진 위에 디데이", com.uridul.app.widget.PhotoWidgetReceiver::class.java),
            Triple("💌 우리둘 상대 사진", "상대가 일기에 올린 최근 사진", com.uridul.app.widget.PartnerPhotoWidgetReceiver::class.java),
            Triple("🗓 우리둘 일정", "다가오는 일정 목록", com.uridul.app.widget.CalendarWidgetReceiver::class.java),
            Triple("📅 우리둘 달력", "한 달 달력", com.uridul.app.widget.MonthWidgetReceiver::class.java),
        )
    }
    Group {
        widgets.forEach { (title, sub, cls) ->
            ActionRow(title, if (supported) sub else "$sub · 이 홈 화면은 바로 놓기를 지원하지 않아요 (홈 화면을 길게 눌러 위젯에서 골라 주세요)") {
                if (supported) runCatching { manager.requestPinAppWidget(ComponentName(context, cls), null, null) }
            }
        }
    }
}

/** 오픈소스·글꼴 라이선스 (주아체 OFL 전문은 앱에 들어 있는 파일 그대로) */
@Composable
internal fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val jua = remember { runCatching { context.assets.open("licenses/Jua-OFL.txt").bufferedReader().readText() }.getOrDefault("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오픈소스·글꼴 라이선스") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("• 주아체 (배달의민족, Woowahan Brothers) — SIL Open Font License 1.1", style = MaterialTheme.typography.bodyMedium)
                Text("• AndroidX · Jetpack Compose · Material 아이콘 · Palette — Apache License 2.0", style = MaterialTheme.typography.bodyMedium)
                Text("• Firebase SDK — Apache License 2.0", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text(jua, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

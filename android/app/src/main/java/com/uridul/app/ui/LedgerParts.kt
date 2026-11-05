package com.uridul.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.uridul.app.data.Couple
import com.uridul.app.ledger.EntryStatus
import com.uridul.app.ledger.EntryType
import com.uridul.app.ledger.LedgerAsset
import com.uridul.app.ledger.LedgerCategories
import com.uridul.app.ledger.LedgerEntry
import com.uridul.app.ledger.LedgerRepository
import com.uridul.app.ledger.LedgerUploadWorker
import com.uridul.app.ledger.MonthSummary
import com.uridul.app.ledger.PaymentCapture
import com.uridul.app.ledger.PaymentInbox
import com.uridul.app.ledger.PaymentKind
import com.uridul.app.ledger.PaymentNotificationService
import kotlinx.coroutines.flow.flowOf
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

// 가계부 탭의 부품: 안내 카드, 이번 달 요약·자산, 거래 줄, 이 폰에만 온 결제 줄. LedgerContent 에서 쓴다.

internal fun Modifier.clip8() = this.then(Modifier.clip(RoundedCornerShape(8.dp)))

@Composable
internal fun SectionTitle(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 자동 기록 켜기: 앱 안 스위치 + 폰 설정의 알림 접근 허용 (사용자가 직접) */
@Composable
internal fun SetupCard(enabled: Boolean, granted: Boolean, onEnable: (Boolean) -> Unit, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("💳 데이트 통장 결제를 자동으로 기록해요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "토스뱅크 CHCH 카드 결제 알림을 읽어 가계부에 바로 적어요. " +
                    "토스 앱 결제 알림과 카카오톡 '토스뱅크' 알림톡만 읽어요. 카카오톡의 다른 대화나 다른 앱 알림은 열어 보지도 저장하지도 않아요. CHCH 카드 결제만 기록하고, 다른 카드·계좌 알림은 남기지 않아요.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("① 자동 기록", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onEnable)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (granted) "② 알림 접근 허용됨 ✅" else "② 폰 설정에서 '우리둘 가계부 자동 기록' 알림 접근 허용",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!granted) OutlinedButton(onClick = onOpenSettings) { Text("설정 열기") }
            }
            Text(
                "끄려면 같은 설정 화면에서 허용을 끄거나 위 스위치를 끄면 돼요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
internal fun SummaryCard(month: YearMonth, s: MonthSummary, assets: List<LedgerAsset>, onMonth: (YearMonth) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달") }
                Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                SummaryCell("지출", wonText(s.expense), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryCell("수입", wonText(s.income), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SummaryCell("건수", "${s.count}건", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            if (assets.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                assets.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(when { a.kind.liability -> "🧾"; a.kind.key == "card" -> "💳"; a.kind.key == "cash" -> "💵"; else -> "🏦" }, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium)
                            if (a.institution.isNotBlank()) Text(a.institution, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            a.lastBalance?.let { "잔액 ${wonText(it)}" } ?: when (a.kind.key) { "card" -> "연결: 데이트 통장"; "cash" -> "직접 기록"; else -> "잔액 알림 대기" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
internal fun EntryRow(e: LedgerEntry, assets: List<LedgerAsset>, onClick: () -> Unit) {
    val cat = LedgerCategories.of(e.category)
    val asset = assets.firstOrNull { it.id == e.assetId }
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cat.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).padding(4.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.merchant.ifBlank { cat.label }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                add(e.localDateTime.toLocalTime().format(KoreanTime))
                add(cat.label)
                asset?.let { add(it.name) }
                if (e.installment > 0) add("${e.installment}개월 할부")
                if (e.cancel) add("취소")
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val sign = when {
            e.type == EntryType.INCOME -> "+"
            e.cancel -> "+"
            else -> "-"
        }
        Text(
            "$sign${wonText(e.amount)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (sign == "+") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun LocalCaptureRow(c: PaymentCapture, onRecord: () -> Unit, onRemove: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val p = c.parsed
            Text(
                "${if (p.kind == PaymentKind.CANCEL) "취소 " else ""}${p.amount?.let(::wonText).orEmpty()} · ${p.merchant ?: "가맹점 모름"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${p.method ?: "결제 수단 모름"} · ${java.time.Instant.ofEpochMilli(c.postedAt).atZone(LedgerEntry.ZONE).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRecord) { Text("데이트 통장 결제로 기록") }
                TextButton(onClick = onRemove) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}


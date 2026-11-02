package com.uridul.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

// 가계부 탭 (1차): 자동 기록 켜기 안내, 확인 필요, 이 폰에만 온 다른 결제, 이번 달 합계·자산·내역.
// 자산·내역·통계·예산 네 칸 화면은 docs/가계부_설계.md 의 제안안대로 2차에 같은 데이터 위에 올린다.

private val won = NumberFormat.getNumberInstance(Locale.KOREA)
internal fun wonText(amount: Long): String = "${won.format(amount)}원"

@Composable
fun LedgerContent(couple: Couple, myUid: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val firebaseReady = remember { FirebaseApp.getApps(context).isNotEmpty() }
    val repo = remember { if (firebaseReady) LedgerRepository(FirebaseFirestore.getInstance()) else null }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val entries by remember(month, repo) { repo?.observeMonth(couple.id, month) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val pending by remember(repo) { repo?.observePending(couple.id) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val assets by remember(repo) { repo?.observeAssets(couple.id) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val locals by PaymentInbox.local.collectAsState()
    var enabled by remember { mutableStateOf(PaymentInbox.isEnabled(context)) }
    var granted by remember { mutableStateOf(PaymentNotificationService.isAccessGranted(context)) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var showLocals by rememberSaveable { mutableStateOf(false) }

    // 설정에서 돌아오면 허용 상태를 다시 본다
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = PaymentNotificationService.isAccessGranted(context)
        PaymentInbox.loadLocal(context)
    }
    LaunchedEffect(repo) {
        if (repo == null) return@LaunchedEffect
        runCatching { repo.ensureDefaultAssets(couple.id, myUid) }
        // 앱이 꺼져 있을 때 모인 결제가 있으면 올린다
        if (PaymentInbox.pendingUploads(context).isNotEmpty()) LedgerUploadWorker.enqueue(context)
    }
    // 서버 자산의 맞추기 말을 이 폰 알림 서비스도 쓰게 (두 사람이 같은 말로)
    LaunchedEffect(assets) {
        if (assets.isEmpty()) return@LaunchedEffect
        val map = PaymentInbox.defaultMatchers() + assets.flatMap { a -> a.matchers.map { it to a.id } }
        PaymentInbox.setMatchers(context, map)
    }

    val summary = remember(entries) { MonthSummary.of(entries) }
    val confirmed = remember(entries) { entries.filter { it.status == EntryStatus.CONFIRMED } }
    val byDay = remember(confirmed) { confirmed.groupBy { it.date }.toSortedMap(compareByDescending { it }) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!enabled || !granted) {
            item(key = "setup") {
                SetupCard(
                    enabled = enabled,
                    granted = granted,
                    onEnable = { on -> PaymentInbox.setEnabled(context, on); enabled = on },
                    onOpenSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
        }
        item(key = "summary") {
            SummaryCard(month, summary, assets, onMonth = { monthText = it.toString() })
        }
        if (pending.isNotEmpty()) {
            item(key = "pendingHead") { SectionTitle("🔎 확인 필요 ${pending.size}건", "결제 알림을 애매하게 읽었어요. 눌러서 고치고 확정해 주세요") }
            items(pending, key = { "p" + it.id }) { e -> EntryRow(e, assets, onClick = { editing = e }) }
        }
        if (locals.isNotEmpty()) {
            item(key = "localHead") {
                Text(
                    "${if (showLocals) "▾" else "▸"} 이 폰에만 온 다른 토스 결제 ${locals.size}건",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).clip8().clickable { showLocals = !showLocals }.padding(vertical = 6.dp),
                )
            }
            if (showLocals) {
                items(locals, key = { "l" + it.key }) { c ->
                    LocalCaptureRow(
                        c,
                        onRecord = { PaymentInbox.moveToUpload(context, c.key, LedgerAsset.DATE_ACCOUNT); LedgerUploadWorker.enqueue(context) },
                        onRemove = { PaymentInbox.removeLocal(context, c.key) },
                    )
                }
            }
        }
        item(key = "listHead") { SectionTitle("📒 ${month.monthValue}월 내역", if (confirmed.isEmpty()) "아직 기록이 없어요" else "${confirmed.size}건") }
        byDay.forEach { (day, list) ->
            item(key = "d$day") {
                Row(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.format(KoreanMonthDay), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val dayExpense = list.filter { it.type == EntryType.EXPENSE }.sumOf { it.signedAmount }
                    if (dayExpense != 0L) Text("-${wonText(dayExpense)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { e -> EntryRow(e, assets, onClick = { editing = e }) }
        }
        item(key = "next") {
            Text(
                "자산 · 통계 · 예산 화면과 직접 추가는 다음 단계에서 열려요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    editing?.let { e ->
        EntryEditDialog(
            entry = e,
            onSave = { updated -> repo?.confirm(couple.id, myUid, updated); editing = null },
            onTrash = { repo?.moveToTrash(couple.id, myUid, e.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private fun Modifier.clip8() = this.then(Modifier.clip(RoundedCornerShape(8.dp)))

@Composable
private fun SectionTitle(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 자동 기록 켜기: 앱 안 스위치 + 폰 설정의 알림 접근 허용 (사용자가 직접) */
@Composable
private fun SetupCard(enabled: Boolean, granted: Boolean, onEnable: (Boolean) -> Unit, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("💳 데이트 통장 결제를 자동으로 기록해요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "토스뱅크 chch모임통장·모임카드 결제 알림을 읽어 가계부에 바로 적어요. " +
                    "토스 알림만 읽고, 다른 앱 알림은 열어 보지도 저장하지도 않아요. 개인 카드 결제는 이 폰에만 잠깐 보여 드리고 공유하지 않아요.",
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
private fun SummaryCard(month: YearMonth, s: MonthSummary, assets: List<LedgerAsset>, onMonth: (YearMonth) -> Unit) {
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
                        Text(if (a.kind.liability) "🧾" else if (a.kind.key == "card") "💳" else "🏦", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium)
                            if (a.institution.isNotBlank()) Text(a.institution, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            a.lastBalance?.let { "잔액 ${wonText(it)}" } ?: if (a.kind.key == "card") "연결: 데이트 통장" else "잔액 알림 대기",
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
private fun SummaryCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
private fun EntryRow(e: LedgerEntry, assets: List<LedgerAsset>, onClick: () -> Unit) {
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
                add(java.time.Instant.ofEpochMilli(e.at).atZone(LedgerEntry.ZONE).toLocalTime().format(KoreanTime))
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
private fun LocalCaptureRow(c: PaymentCapture, onRecord: () -> Unit, onRemove: () -> Unit) {
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

/** 거래 고치기·확정: 분류, 가맹점, 메모 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryEditDialog(entry: LedgerEntry, onSave: (LedgerEntry) -> Unit, onTrash: () -> Unit, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf(entry.category) }
    var merchant by remember { mutableStateOf(entry.merchant) }
    var memo by remember { mutableStateOf(entry.memo) }
    var type by remember { mutableStateOf(entry.type) }
    val groups = LedgerCategories.groups.filter { it.income == (type == EntryType.INCOME) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${if (entry.cancel) "취소 " else ""}${wonText(entry.amount)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { t ->
                        FilterChip(selected = type == t, onClick = {
                            type = t
                            category = if (t == EntryType.INCOME) LedgerCategories.DEFAULT_INCOME else LedgerCategories.guess(merchant)
                        }, label = { Text(t.label) })
                    }
                }
                OutlinedTextField(value = merchant, onValueChange = { merchant = it.take(LedgerEntry.MERCHANT_MAX) }, label = { Text("가맹점·내용") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                groups.forEach { g ->
                    Text("${g.emoji} ${g.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.children.forEach { c -> FilterChip(selected = category == c.key, onClick = { category = c.key }, label = { Text("${c.emoji} ${c.label}") }) }
                    }
                }
                OutlinedTextField(value = memo, onValueChange = { memo = it.take(LedgerEntry.MEMO_MAX) }, label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
                entry.raw?.let { r ->
                    Text("알림 원문: ${r.title} ${r.text}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(entry.copy(category = category, merchant = merchant, memo = memo, type = type)) }) {
                Text(if (entry.status == EntryStatus.PENDING) "확정" else "저장")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onTrash) { Text("휴지통", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

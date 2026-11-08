package com.uridul.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.uridul.app.data.Couple
import com.uridul.app.ledger.EntryStatus
import com.uridul.app.ledger.EntryType
import com.uridul.app.ledger.LedgerAsset
import com.uridul.app.ledger.LedgerCategories
import com.uridul.app.ledger.LedgerEntry
import com.uridul.app.ledger.LedgerUploadWorker
import com.uridul.app.ledger.MonthSummary
import com.uridul.app.ledger.PaymentInbox
import com.uridul.app.ledger.PayerNames
import com.uridul.app.ledger.PaymentNotificationService
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    val repo = remember { (context.applicationContext as? com.uridul.app.UridulApp)?.container?.ledger }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val entries by remember(month, repo) { repo?.observeMonth(couple.id, month) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val pending by remember(repo) { repo?.observePending(couple.id) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val assets by remember(repo) { repo?.observeAssets(couple.id) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val learned by remember(repo) { repo?.observeLearned(couple.id) ?: flowOf(emptyMap()) }.collectAsState(initial = emptyMap())
    /** 분류를 고친 뒤 "같은 가맹점 지난 기록도 바꿀까요?" */
    var relabel by remember { mutableStateOf<Relabel?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val locals by PaymentInbox.local.collectAsState()
    var enabled by remember { mutableStateOf(PaymentInbox.isEnabled(context)) }
    var granted by remember { mutableStateOf(PaymentNotificationService.isAccessGranted(context)) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    /** 직접 기록 창 */
    var creating by rememberSaveable { mutableStateOf(false) }
    var showLocals by rememberSaveable { mutableStateOf(false) }

    // 설정에서 돌아오면 허용 상태를 다시 본다
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = PaymentNotificationService.isAccessGranted(context)
        PaymentInbox.loadLocal(context)
    }
    LaunchedEffect(repo) {
        if (repo == null) return@LaunchedEffect
        repo.ensureDefaultAssets(context, couple.id, myUid)
        // 앱이 꺼져 있을 때 모인 결제가 있으면 올린다
        if (PaymentInbox.pendingUploads(context).isNotEmpty()) LedgerUploadWorker.enqueue(context)
    }
    // 서버 자산의 맞추기 말을 이 폰 알림 서비스도 쓰게 (두 사람이 같은 말로)
    LaunchedEffect(assets) {
        if (assets.isEmpty()) return@LaunchedEffect
        val map = PaymentInbox.defaultMatchers() + assets.flatMap { a -> a.matchers.filterNot(PayerNames::isTag).map { it to a.id } }
        PaymentInbox.setMatchers(context, map)
        // 결제한 사람 이름 연결 (서버에 정해 둔 것 → 이 폰 기준 나/상대)
        PaymentInbox.setCreator(context, couple.isCreator(myUid))
        PaymentInbox.syncPayerNames(context, assets.flatMap { PayerNames.parse(it.matchers).toList() }.toMap())
    }

    /**
     * 저장 뒤 분류 학습: 고친 거래는 분류가 바뀌었을 때, 새 거래는 짐작과 다르게 골랐을 때
     * 그 가맹점을 학습표에 기억하고, 고친 거래면 같은 가맹점의 지난 기록도 바꿀지 묻는다.
     */
    fun learnAfterSave(before: LedgerEntry?, after: LedgerEntry) {
        val r = repo ?: return
        if (!LedgerCategories.shouldLearn(before, after, learned)) return
        r.learnCategory(couple.id, myUid, after.merchant, after.category)
        if (before == null) return
        scope.launch {
            val others = r.sameMerchantOthers(couple.id, after.merchant, after.category, after.id)
            if (others.isNotEmpty()) relabel = Relabel(after.merchant.trim(), after.category, others.map { it.id })
        }
    }

    val summary = remember(entries) { MonthSummary.of(entries) }
    val confirmed = remember(entries) { entries.filter { it.status == EntryStatus.CONFIRMED } }
    val byDay = remember(confirmed) { confirmed.groupBy { it.date }.toSortedMap(compareByDescending { it }) }

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
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
                "자산 · 통계 · 예산 화면은 다음 단계에서 열려요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    // 직접 기록 (현금 결제·알림을 놓친 결제·모임비 입금 등)
    ExtendedFloatingActionButton(
        onClick = { creating = true },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("직접 기록") },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    )
    }

    if (creating) {
        EntryFormDialog(
            initial = null,
            couple = couple,
            myUid = myUid,
            assets = assets,
            defaultDate = if (month == YearMonth.now()) java.time.LocalDate.now() else month.atDay(1),
            learned = learned,
            onSave = { e -> repo?.save(couple.id, myUid, e); learnAfterSave(null, e); creating = false },
            onTrash = null,
            onDismiss = { creating = false },
        )
    }
    editing?.let { e ->
        EntryFormDialog(
            initial = e,
            couple = couple,
            myUid = myUid,
            assets = assets,
            defaultDate = e.date,
            learned = learned,
            onSave = { updated -> repo?.save(couple.id, myUid, updated); learnAfterSave(e, updated); editing = null },
            onTrash = { repo?.moveToTrash(couple.id, myUid, e.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    relabel?.let { r ->
        val cat = LedgerCategories.of(r.category)
        AlertDialog(
            onDismissRequest = { relabel = null },
            title = { Text("같은 가맹점도 바꿀까요?") },
            text = {
                Text(
                    "'${r.merchant}'의 다른 기록 ${r.ids.size}건도 ${cat.emoji} ${cat.label}(으)로 바꿀까요?\n" +
                        "앞으로 들어오는 결제는 이 분류로 자동으로 들어가요.",
                )
            },
            confirmButton = { Button(onClick = { repo?.recategorize(couple.id, myUid, r.ids, r.category); relabel = null }) { Text("모두 바꾸기") } },
            dismissButton = { TextButton(onClick = { relabel = null }) { Text("이번 것만") } },
        )
    }
}

/** 분류를 고친 가맹점의 다른 기록들 */
private data class Relabel(val merchant: String, val category: String, val ids: List<String>)

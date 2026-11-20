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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import com.uridul.app.ledger.CancelPairs
import com.uridul.app.ledger.EntryStatus
import com.uridul.app.ledger.EntryType
import com.uridul.app.ledger.LedgerTotals
import com.uridul.app.ledger.LedgerAsset
import com.uridul.app.ledger.LedgerCategories
import com.uridul.app.ledger.LedgerEntry
import com.uridul.app.ledger.LedgerUploadWorker
import com.uridul.app.ledger.MonthSummary
import com.uridul.app.ledger.PaymentInbox
import com.uridul.app.ledger.PayerNames
import com.uridul.app.ledger.PaymentNotificationService
import com.uridul.app.ledger.Won
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.YearMonth

// 가계부 탭 (1차): 자동 기록 켜기 안내, 확인 필요, 이 폰에만 온 다른 결제, 이번 달 합계·자산·내역.
// 자산·내역·통계·예산 네 칸 화면은 docs/가계부_설계.md 의 제안안대로 2차에 같은 데이터 위에 올린다.

internal fun wonText(amount: Long): String = Won.text(amount)

@Composable
fun LedgerContent(
    couple: Couple,
    myUid: String,
    modifier: Modifier = Modifier,
    /** 날짜 머리글의 사진을 누르면 그날 일기 (크게 보기) */
    onOpenDay: (com.uridul.app.data.CalendarEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { appContainer(context)?.ledger }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    // null = 아직 받는 중 (빈 달 캐릭터 화면이 열자마자 번쩍 보이지 않게)
    val loadedEntries by remember(month, repo) { repo?.observeMonth(couple.id, month) ?: flowOf(emptyList()) }.collectAsState(initial = null)
    val entries = loadedEntries.orEmpty()
    // 취소 짝: 서버에 적어 둔 짝 + 아직 안 이어진 것은 이 달 안에서 찾기
    val pairs = remember(entries) { CancelPairs.of(entries) }
    // 예전 기록(이 폰에서 처음 한 번)이나 다른 폰이 올린, 아직 짝이 안 적힌 취소를 원래 결제와 잇는다.
    // 다 이어진 뒤에는 이 달에 짝 없는 취소가 보일 때만 서버를 읽는다
    val unlinkedCancels = remember(entries) { entries.count { it.cancel && it.cancelPair == null && it.status == EntryStatus.CONFIRMED } }
    LaunchedEffect(repo, unlinkedCancels) {
        val r = repo ?: return@LaunchedEffect
        val done = PaymentInbox.cancelLinksDone(context, couple.id)
        if (done && unlinkedCancels == 0) return@LaunchedEffect
        runCatching { r.linkCancels(couple.id, myUid) }.onSuccess { if (!done) PaymentInbox.markCancelLinksDone(context, couple.id) }
    }
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

    val summary = remember(entries, pairs) { MonthSummary.of(entries, pairs) }
    val confirmed = remember(entries) { entries.filter { it.status == EntryStatus.CONFIRMED } }
    val byDay = remember(confirmed) { confirmed.groupBy { it.date }.toSortedMap(compareByDescending { it }) }
    // 날짜 머리글에 그날 일기 사진 (사진 달력이 저장해 둔 대표 사진, 이 달 것을 불러 둔다)
    val photoHooks = LocalPhotos.current
    val reviewHooks = LocalReviews.current
    LaunchedEffect(month) { photoHooks.loadMonthPhotos(month) }

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
        if (loadedEntries != null && confirmed.isEmpty()) item(key = "emptyMonth") {
            CharacterEmpty(
                couple, myUid,
                title = "${month.monthValue}월 데이트 기록이 아직 없어요",
                body = "같이 쓰는 카드로 결제하면 알림을 읽어 자동으로 적혀요.\n현금처럼 알림이 없는 건 직접 적어 주세요.",
                action = "✏️ 직접 기록",
                onAction = { creating = true },
            )
        }
        byDay.forEach { (day, list) ->
            item(key = "d$day") {
                Row(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    photoHooks.dayPhotos[day]?.let { p ->
                        val title = reviewHooks.reviews[com.uridul.app.data.DateReview.idOf(p.eventId, day)]?.title.orEmpty()
                        DayPhotoDot(p, title) { onOpenDay(FeedPost(p.eventId, day, title, null).asEvent()) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(day.format(KoreanMonthDay), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    // 그날 쓴 돈 (취소된 결제는 빼고). 짝 없는 환불이 더 많으면 +로
                    val dayExpense = LedgerTotals.expense(list, pairs)
                    if (dayExpense != 0L) Text(
                        Won.signed(-dayExpense),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dayExpense < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { e -> EntryRow(e, assets, pairs.note(e), onClick = { editing = e }) }
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
            onSave = { e ->
                val id = repo?.save(couple.id, myUid, e)
                learnAfterSave(null, e)
                creating = false
                // 잔액으로 계산해 이미 적힌 입금과 같은 돈이면 그쪽을 줄인다 (두 번 세지 않게)
                if (repo != null && id != null && e.type == EntryType.INCOME) scope.launch { runCatching { repo.absorbManualIncome(couple.id, myUid, e.copy(id = id)) } }
            },
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
            onTrash = { repo?.moveToTrash(couple.id, myUid, e); editing = null },
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

package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.ledger.EntrySource
import com.uridul.app.ledger.EntryType
import com.uridul.app.ledger.LedgerAsset
import com.uridul.app.ledger.LedgerCategories
import com.uridul.app.ledger.LedgerEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 쓴 사람 선택: 나 / 상대 / 같이 */
private enum class Payer { ME, PARTNER, TOGETHER }

/**
 * 거래 직접 기록·고치기 (자동 기록으로 들어온 거래도 같은 창에서 금액·날짜까지 고친다).
 * 지출/수입, 금액, 날짜·시간, 내용(가맹점), 분류, 결제 수단, 쓴 사람, 메모.
 * 내용을 쓰면 분류를 자동으로 짐작한다 (직접 고르면 그대로 둔다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryFormDialog(
    /** null 이면 새로 직접 기록 */
    initial: LedgerEntry?,
    couple: Couple,
    myUid: String,
    assets: List<LedgerAsset>,
    /** 새로 기록할 때 처음 날짜 (지금 보는 달) */
    defaultDate: LocalDate,
    onSave: (LedgerEntry) -> Unit,
    onTrash: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val now = remember0 { LocalDateTime.now(LedgerEntry.ZONE) }
    val start = initial?.let { Instant.ofEpochMilli(it.at).atZone(LedgerEntry.ZONE).toLocalDateTime() }
        ?: LocalDateTime.of(defaultDate, if (defaultDate == now.toLocalDate()) now.toLocalTime().withSecond(0).withNano(0) else LocalTime.of(12, 0))
    val partnerUid = couple.partnerUid(myUid)
    val partnerName = couple.partnerName(myUid) ?: "상대"

    var typeKey by rememberSaveable { mutableStateOf((initial?.type ?: EntryType.EXPENSE).key) }
    val type = EntryType.from(typeKey)
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf(start.toLocalDate().toString()) }
    var timeText by rememberSaveable { mutableStateOf(start.toLocalTime().toString()) }
    var merchant by rememberSaveable { mutableStateOf(initial?.merchant.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: LedgerCategories.DEFAULT_EXPENSE) }
    var categoryTouched by rememberSaveable { mutableStateOf(initial != null) }
    val assetChoices = assets.ifEmpty { LedgerAsset.defaults }
    var assetId by rememberSaveable { mutableStateOf(initial?.assetId ?: LedgerAsset.DATE_CARD) }
    var payerKey by rememberSaveable {
        mutableStateOf(
            when {
                initial == null -> Payer.ME.name
                initial.paidBy == myUid -> Payer.ME.name
                initial.paidBy != null || initial.paidByName != null -> Payer.PARTNER.name
                else -> Payer.TOGETHER.name
            },
        )
    }
    var memo by rememberSaveable { mutableStateOf(initial?.memo.orEmpty()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val date = LocalDate.parse(dateText)
    val time = LocalTime.parse(timeText)
    val amount = amountText.toLongOrNull()

    fun save() {
        if (amount == null || amount <= 0) { error = "금액을 입력해 주세요"; return }
        if (amount > LedgerEntry.AMOUNT_MAX) { error = "금액이 너무 커요"; return }
        val payer = Payer.valueOf(payerKey)
        val at = LocalDateTime.of(date, time).atZone(LedgerEntry.ZONE).toInstant().toEpochMilli()
        val base = initial ?: LedgerEntry(id = "", type = type, amount = amount, at = at, source = EntrySource.MANUAL, createdBy = myUid)
        onSave(
            base.copy(
                type = type,
                amount = amount,
                at = at,
                merchant = merchant.trim(),
                category = category,
                assetId = assetId,
                paidBy = when (payer) {
                    Payer.ME -> myUid
                    Payer.PARTNER -> partnerUid
                    Payer.TOGETHER -> null
                },
                paidByName = if (payer == Payer.PARTNER && partnerUid == null) partnerName else null,
                memo = memo.trim(),
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "✏️ 직접 기록" else if (initial.source == EntrySource.NOTIFICATION) "💳 알림으로 들어온 거래" else "거래 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                typeKey = t.key
                                category = if (t == EntryType.INCOME) LedgerCategories.DEFAULT_INCOME else LedgerCategories.guess(merchant)
                                categoryTouched = false
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() }.take(11); error = null },
                    label = { Text("금액 (원)") },
                    placeholder = { Text("38000") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: amount?.let { wonText(it) } ?: "숫자만 입력") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "date" }, modifier = Modifier.weight(1f)) { Text(date.format(KoreanDate)) }
                    OutlinedButton(onClick = { picking = "time" }) { Text(time.format(KoreanTime)) }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { v ->
                        merchant = v.take(LedgerEntry.MERCHANT_MAX)
                        if (!categoryTouched && type == EntryType.EXPENSE) category = LedgerCategories.guess(merchant)
                    },
                    label = { Text(if (type == EntryType.INCOME) "내용" else "어디서 (가맹점·내용)") },
                    placeholder = { Text(if (type == EntryType.INCOME) "모임비 입금" else "선릉감자탕") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LedgerCategories.groups.filter { it.income == (type == EntryType.INCOME) }.forEach { g ->
                    Text("${g.emoji} ${g.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.children.forEach { c ->
                            FilterChip(selected = category == c.key, onClick = { category = c.key; categoryTouched = true }, label = { Text("${c.emoji} ${c.label}") })
                        }
                    }
                }
                Text(if (type == EntryType.INCOME) "어디로" else "결제 수단", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    assetChoices.forEach { a -> FilterChip(selected = assetId == a.id, onClick = { assetId = a.id }, label = { Text(a.name) }) }
                }
                Text(if (type == EntryType.INCOME) "누가 넣었나요" else "누가 썼나요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = payerKey == Payer.ME.name, onClick = { payerKey = Payer.ME.name }, label = { Text(couple.myName(myUid)) })
                    FilterChip(selected = payerKey == Payer.PARTNER.name, onClick = { payerKey = Payer.PARTNER.name }, label = { Text(partnerName) })
                    FilterChip(selected = payerKey == Payer.TOGETHER.name, onClick = { payerKey = Payer.TOGETHER.name }, label = { Text("같이") })
                }
                OutlinedTextField(value = memo, onValueChange = { memo = it.take(LedgerEntry.MEMO_MAX) }, label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
                initial?.raw?.let { r ->
                    Text("알림 원문: ${r.title} ${r.text}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = ::save) { Text(if (initial == null) "기록" else "저장") } },
        dismissButton = {
            Row {
                if (onTrash != null) TextButton(onClick = onTrash) { Text("휴지통", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )

    when (picking) {
        "date" -> StartDatePickerDialog(
            initial = date,
            title = "날짜",
            onDismiss = { picking = null },
            onConfirm = { dateText = it.toString(); picking = null },
        )
        "time" -> TimePickerDialog(
            initial = time,
            title = "시간",
            onDismiss = { picking = null },
            onConfirm = { timeText = it.toString(); picking = null },
        )
    }
}

/** 처음 한 번만 계산 (창을 다시 그려도 "지금" 이 바뀌지 않게) */
@Composable
private fun <T> remember0(calc: () -> T): T = androidx.compose.runtime.remember { calc() }

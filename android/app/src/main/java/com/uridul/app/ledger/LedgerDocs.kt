package com.uridul.app.ledger

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.time.LocalDate

// 가계부 서버 문서 ↔ 앱 모델 바꾸기 (저장소는 읽기·쓰기만 맡는다)

/** 알림 원문 최대 길이 */
internal const val RAW_MAX = 1000

internal fun entryData(e: LedgerEntry, uid: String): MutableMap<String, Any?> = mutableMapOf(
    "v" to LedgerEntry.SCHEMA,
    "type" to e.type.key,
    "amount" to e.amount,
    "cancel" to e.cancel,
    "date" to e.date.toString(),
    "at" to e.at,
    "merchant" to e.merchant.trim().take(LedgerEntry.MERCHANT_MAX),
    "memo" to e.memo.trim().take(LedgerEntry.MEMO_MAX),
    "category" to e.category,
    "assetId" to e.assetId,
    "toAssetId" to e.toAssetId,
    "paidBy" to e.paidBy,
    "paidByName" to e.paidByName?.take(20),
    "status" to e.status.key,
    "source" to e.source.key,
    "sourceApp" to e.sourceApp,
    "raw" to e.raw?.let { mapOf("title" to it.title.take(RAW_MAX), "text" to it.text.take(RAW_MAX)) },
    "parser" to e.parser,
    "balanceAfter" to e.balanceAfter,
    "installment" to e.installment,
    "recurringId" to e.recurringId,
    "eventId" to e.eventId,
    "tags" to e.tags.take(10),
    "deleted" to e.deleted,
    "updatedBy" to uid,
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun assetData(a: LedgerAsset, uid: String): Map<String, Any?> = mapOf(
    "v" to LedgerAsset.SCHEMA,
    "name" to a.name,
    "kind" to a.kind.key,
    "institution" to a.institution,
    "linkedAssetId" to a.linkedAssetId,
    "openingBalance" to a.openingBalance,
    "openingDate" to a.openingDate?.toString(),
    "matchers" to a.matchers,
    "archived" to a.archived,
    "order" to a.order,
    "updatedBy" to uid,
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun DocumentSnapshot.toEntry(): LedgerEntry? {
    val amount = getLong("amount") ?: return null
    val at = getLong("at") ?: return null
    val raw = (get("raw") as? Map<*, *>)?.let { RawText((it["title"] as? String).orEmpty(), (it["text"] as? String).orEmpty()) }
    return LedgerEntry(
        id = id,
        type = EntryType.from(getString("type")),
        amount = amount,
        cancel = getBoolean("cancel") == true,
        at = at,
        merchant = getString("merchant").orEmpty(),
        memo = getString("memo").orEmpty(),
        category = getString("category") ?: LedgerCategories.DEFAULT_EXPENSE,
        assetId = getString("assetId"),
        toAssetId = getString("toAssetId"),
        paidBy = getString("paidBy"),
        paidByName = getString("paidByName"),
        status = EntryStatus.from(getString("status")),
        source = EntrySource.from(getString("source")),
        sourceApp = getString("sourceApp"),
        raw = raw,
        parser = getString("parser"),
        balanceAfter = getLong("balanceAfter"),
        installment = getLong("installment")?.toInt() ?: 0,
        recurringId = getString("recurringId"),
        eventId = getString("eventId"),
        tags = (get("tags") as? List<*>).orEmpty().filterIsInstance<String>(),
        deleted = getBoolean("deleted") == true,
        createdBy = getString("createdBy").orEmpty(),
    )
}

internal fun DocumentSnapshot.toAsset(): LedgerAsset? {
    val name = getString("name") ?: return null
    return LedgerAsset(
        id = id,
        name = name,
        kind = AssetKind.from(getString("kind")),
        institution = getString("institution").orEmpty(),
        linkedAssetId = getString("linkedAssetId"),
        openingBalance = getLong("openingBalance") ?: 0,
        openingDate = getString("openingDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        lastBalance = getLong("lastBalance"),
        lastBalanceAt = getLong("lastBalanceAt"),
        matchers = (get("matchers") as? List<*>).orEmpty().filterIsInstance<String>(),
        archived = getBoolean("archived") == true,
        order = getLong("order")?.toInt() ?: 0,
    )
}

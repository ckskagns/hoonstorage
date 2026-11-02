package com.uridul.app.ledger

import org.json.JSONObject

/**
 * 알림 한 건을 가계부에 넣을 모양으로 정리한 것 (폰 보관함에 이 모양으로 쌓았다가 서버에 올린다).
 * [assetId] 가 null 이면 커플 자산과 맞지 않은 결제(개인 카드 등): 서버에 올리지 않고 이 폰에만 잠깐 둔다.
 */
data class PaymentCapture(
    val key: String,
    val packageName: String,
    val postedAt: Long,
    val parsed: ParsedPayment,
    val raw: RawText,
    val assetId: String?,
) {
    /** 서버에 올릴 거래 (id 는 저장소가 정한다) */
    fun toEntry(myUid: String, paidBy: String?): LedgerEntry {
        val type = when (parsed.kind) {
            PaymentKind.DEPOSIT -> EntryType.INCOME
            else -> EntryType.EXPENSE
        }
        return LedgerEntry(
            id = "",
            type = type,
            amount = parsed.amount ?: 0,
            cancel = parsed.kind == PaymentKind.CANCEL,
            at = postedAt,
            merchant = parsed.merchant.orEmpty(),
            category = if (type == EntryType.INCOME) "income.deposit" else LedgerCategories.guess(parsed.merchant),
            assetId = assetId,
            paidBy = paidBy,
            status = if (parsed.confident) EntryStatus.CONFIRMED else EntryStatus.PENDING,
            source = EntrySource.NOTIFICATION,
            sourceApp = packageName,
            raw = raw,
            parser = parsed.parser,
            balanceAfter = parsed.balance,
            installment = parsed.installmentMonths,
            createdBy = myUid,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("pkg", packageName).put("at", postedAt).put("asset", assetId ?: JSONObject.NULL)
        .put("rawTitle", raw.title).put("rawText", raw.text)
        .put("kind", parsed.kind.name).put("amount", parsed.amount ?: JSONObject.NULL)
        .put("merchant", parsed.merchant ?: JSONObject.NULL).put("method", parsed.method ?: JSONObject.NULL)
        .put("payType", parsed.payType ?: JSONObject.NULL).put("months", parsed.installmentMonths)
        .put("payer", parsed.payerName ?: JSONObject.NULL).put("balance", parsed.balance ?: JSONObject.NULL)
        .put("confident", parsed.confident).put("parser", parsed.parser)

    companion object {
        private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
        private fun JSONObject.long(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)

        fun fromJson(o: JSONObject): PaymentCapture? = runCatching {
            PaymentCapture(
                key = o.getString("key"),
                packageName = o.getString("pkg"),
                postedAt = o.getLong("at"),
                parsed = ParsedPayment(
                    kind = PaymentKind.valueOf(o.getString("kind")),
                    amount = o.long("amount"),
                    merchant = o.str("merchant"),
                    method = o.str("method"),
                    payType = o.str("payType"),
                    installmentMonths = o.optInt("months", 0),
                    payerName = o.str("payer"),
                    balance = o.long("balance"),
                    confident = o.optBoolean("confident", false),
                    parser = o.optString("parser"),
                ),
                raw = RawText(o.optString("rawTitle"), o.optString("rawText")),
                assetId = o.str("asset"),
            )
        }.getOrNull()

        /**
         * 알림 → 정리. 기록할 알림이 아니면 null.
         * [matchers] = 알림 글에 있으면 그 자산으로 볼 말 → 자산 ID ("chch모임통장" → 데이트 통장).
         * 결제 수단 이름에서 먼저 찾고, 없으면 알림 전체 글에서 찾는다.
         */
        fun from(n: NotificationText, parser: PaymentNotificationParser, postedAt: Long, matchers: Map<String, String>): PaymentCapture? {
            val parsed = parser.parse(n)
            if (!parsed.recordable) return null
            val all = n.lines.joinToString("\n")
            val ordered = matchers.entries.sortedByDescending { it.key.length }
            val asset = ordered.firstOrNull { (word, _) -> parsed.method?.contains(word) == true }?.value
                ?: ordered.firstOrNull { (word, _) -> all.contains(word) }?.value
            return PaymentCapture(
                key = dedupeKey(parsed, postedAt),
                packageName = n.packageName,
                postedAt = postedAt,
                parsed = parsed,
                raw = n.raw(),
                assetId = asset,
            )
        }

        /** 같은 알림이 다시 올라오거나(알림 갱신) 두 폰에서 함께 받아도 같은 값 (3분 단위) */
        fun dedupeKey(p: ParsedPayment, at: Long): String = keyForBucket(p, at / BUCKET_MS)

        fun keyForBucket(p: ParsedPayment, bucket: Long): String {
            val base = "${p.kind}|${p.amount}|${p.merchant.orEmpty().replace(" ", "")}|$bucket"
            val md = java.security.MessageDigest.getInstance("SHA-1").digest(base.toByteArray())
            return "n_" + md.take(10).joinToString("") { "%02x".format(it) }
        }

        const val BUCKET_MS = 3 * 60 * 1000L
    }
}

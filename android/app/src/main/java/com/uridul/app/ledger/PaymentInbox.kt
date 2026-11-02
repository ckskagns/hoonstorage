package com.uridul.app.ledger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 폰 보관함 (SharedPreferences).
 *  - upload: 커플 자산과 맞는 결제. 인터넷이 되면 서버에 올리고 지운다 (앱이 꺼져 있어도 잃지 않게 먼저 여기에)
 *  - local: 자산과 맞지 않은 토스 결제(개인 카드일 수 있음). 서버에 올리지 않고 이 폰에서만 최근 30건을 보여준다
 *  - seen: 같은 알림이 다시 올라와도 한 번만 받도록 최근 키 100개
 * 설정(자동 기록 켜짐, 자산 맞추기 말)도 여기 둔다: 알림 서비스가 앱 없이 읽어야 해서.
 */
object PaymentInbox {
    private const val PREFS = "ledger_inbox"
    private const val UPLOAD = "upload"
    private const val LOCAL = "local"
    private const val SEEN = "seen"
    private const val ENABLED = "enabled"
    private const val MATCHERS = "matchers"
    private const val LOCAL_MAX = 30
    private const val SEEN_MAX = 100

    private val _local = MutableStateFlow<List<PaymentCapture>>(emptyList())
    /** 이 폰에만 있는 맞지 않은 결제 (가계부 탭이 보여준다) */
    val local: StateFlow<List<PaymentCapture>> = _local.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(ENABLED, false)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(ENABLED, on).apply()

    /** 알림 글의 말 → 자산 ID (기본: chch모임통장·모임통장 → 데이트 통장, 모임카드 → 모임카드) */
    fun matchers(c: Context): Map<String, String> {
        val raw = prefs(c).getString(MATCHERS, null) ?: return defaultMatchers()
        return runCatching {
            val o = org.json.JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(defaultMatchers())
    }

    fun setMatchers(c: Context, map: Map<String, String>) {
        prefs(c).edit().putString(MATCHERS, org.json.JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun defaultMatchers(): Map<String, String> =
        LedgerAsset.defaults.flatMap { a -> a.matchers.map { it to a.id } }.toMap()

    @Synchronized
    fun add(c: Context, capture: PaymentCapture): Boolean {
        val p = prefs(c)
        val seen = read(p.getString(SEEN, null)).toMutableList()
        if (capture.key in seen) return false
        seen += capture.key
        val edit = p.edit().putString(SEEN, JSONArray(seen.takeLast(SEEN_MAX)).toString())
        if (capture.assetId != null) {
            val list = readCaptures(p.getString(UPLOAD, null)) + capture
            edit.putString(UPLOAD, writeCaptures(list))
        } else {
            val list = (listOf(capture) + readCaptures(p.getString(LOCAL, null))).take(LOCAL_MAX)
            edit.putString(LOCAL, writeCaptures(list))
            _local.value = list
        }
        edit.apply()
        return true
    }

    fun pendingUploads(c: Context): List<PaymentCapture> = readCaptures(prefs(c).getString(UPLOAD, null))

    @Synchronized
    fun removeUploaded(c: Context, keys: Set<String>) {
        val p = prefs(c)
        p.edit().putString(UPLOAD, writeCaptures(readCaptures(p.getString(UPLOAD, null)).filter { it.key !in keys })).apply()
    }

    fun loadLocal(c: Context) {
        _local.value = readCaptures(prefs(c).getString(LOCAL, null))
    }

    /** 맞지 않은 결제를 사용자가 커플 자산으로 옮김 (올리기 줄로) */
    @Synchronized
    fun moveToUpload(c: Context, key: String, assetId: String) {
        val p = prefs(c)
        val locals = readCaptures(p.getString(LOCAL, null))
        val item = locals.firstOrNull { it.key == key } ?: return
        val rest = locals.filter { it.key != key }
        p.edit()
            .putString(LOCAL, writeCaptures(rest))
            .putString(UPLOAD, writeCaptures(readCaptures(p.getString(UPLOAD, null)) + item.copy(assetId = assetId)))
            .apply()
        _local.value = rest
    }

    @Synchronized
    fun removeLocal(c: Context, key: String) {
        val p = prefs(c)
        val rest = readCaptures(p.getString(LOCAL, null)).filter { it.key != key }
        p.edit().putString(LOCAL, writeCaptures(rest)).apply()
        _local.value = rest
    }

    private fun read(raw: String?): List<String> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    internal fun readCaptures(raw: String?): List<PaymentCapture> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { PaymentCapture.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    internal fun writeCaptures(list: List<PaymentCapture>): String = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()
}

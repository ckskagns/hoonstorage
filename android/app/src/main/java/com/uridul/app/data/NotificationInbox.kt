package com.uridul.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 앱 안 알림 하나 (폰 알림으로 보낸 것과 같은 내용) */
data class InboxItem(
    val id: String,
    val title: String,
    val text: String,
    /** 누르면 이동할 날짜 */
    val date: LocalDate,
    /** 누르면 열 일정 (아침 요약처럼 여러 일정이면 null → 그 날짜만) */
    val eventId: String?,
    val postedAtMillis: Long,
    val read: Boolean = false,
    /** 누르면 열 탭 ("wish" 등, 없으면 캘린더) */
    val tab: String? = null,
)

/**
 * 앱 안 알림 보관함: 일정 알림(아침 9시·일정 전)을 보낼 때마다 함께 적어 둔다.
 * 폰 알림을 꺼 두었어도 앱 안 종 아이콘에서 볼 수 있다. 이 폰에만 저장, 최근 30일·100개까지.
 */
object NotificationInbox {
    private const val PREFS = "notification_inbox"
    private const val KEY = "items"
    private const val MAX_ITEMS = 100
    private const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000

    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    /** 최근 것부터 */
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _items.value = prune(read(context), System.currentTimeMillis())
    }

    /** 새 알림 적기 (같은 ID 가 있으면 새 내용으로 바꾸고 다시 안 읽음으로) */
    @Synchronized
    fun add(context: Context, item: InboxItem) {
        load(context)
        write(context, prune(listOf(item) + _items.value.filter { it.id != item.id }, item.postedAtMillis))
    }

    @Synchronized
    fun markRead(context: Context, id: String) {
        load(context)
        if (_items.value.none { it.id == id && !it.read }) return
        write(context, _items.value.map { if (it.id == id) it.copy(read = true) else it })
    }

    @Synchronized
    fun markAllRead(context: Context) {
        load(context)
        if (_items.value.none { !it.read }) return
        write(context, _items.value.map { it.copy(read = true) })
    }

    /** 30일이 지난 것·100개를 넘는 것은 정리 (최근 것부터) */
    internal fun prune(list: List<InboxItem>, now: Long): List<InboxItem> =
        list.filter { now - it.postedAtMillis < KEEP_MILLIS }
            .sortedByDescending { it.postedAtMillis }
            .take(MAX_ITEMS)

    private fun write(context: Context, list: List<InboxItem>) {
        _items.value = list
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(list)).apply()
    }

    private fun read(context: Context): List<InboxItem> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    internal fun encode(list: List<InboxItem>): String = JSONArray().also { arr ->
        list.forEach { i ->
            arr.put(
                JSONObject()
                    .put("id", i.id).put("title", i.title).put("text", i.text)
                    .put("date", i.date.toString()).put("eventId", i.eventId ?: JSONObject.NULL)
                    .put("at", i.postedAtMillis).put("read", i.read).put("tab", i.tab ?: JSONObject.NULL),
            )
        }
    }.toString()

    internal fun decode(raw: String?): List<InboxItem> {
        if (raw == null) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { k ->
                val o = arr.getJSONObject(k)
                runCatching {
                    InboxItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        text = o.optString("text"),
                        date = LocalDate.parse(o.getString("date")),
                        eventId = if (o.isNull("eventId")) null else o.getString("eventId"),
                        postedAtMillis = o.getLong("at"),
                        read = o.optBoolean("read", false),
                        tab = if (!o.has("tab") || o.isNull("tab")) null else o.getString("tab"),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}

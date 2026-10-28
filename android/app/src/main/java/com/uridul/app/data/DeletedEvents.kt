package com.uridul.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 되돌릴 수 있는 일: 일정 지우기 / 반복 일정에서 하루 빼기 */
data class UndoItem(val event: CalendarEvent, val skippedDay: java.time.LocalDate? = null) {
    val message: String
        get() = if (skippedDay != null) "\"${event.title}\" ${skippedDay.monthValue}월 ${skippedDay.dayOfMonth}일만 뺐어요"
        else "\"${event.title}\" 일정을 지웠어요"
}

/** 지운 일정 하나 + 지운 시각 */
data class DeletedEvent(val event: CalendarEvent, val deletedAtMillis: Long)

/**
 * 최근 지운 일정: 지운 폰에 30일 동안 보관해서 메뉴에서 되돌릴 수 있게 한다.
 * (서버에서는 바로 지워진다. 상대 폰에는 남지 않는다)
 */
object DeletedEvents {
    private const val PREFS = "deleted_events"
    private const val KEY = "items"
    const val KEEP_DAYS = 30L
    private const val MAX_ITEMS = 50
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    private val _items = MutableStateFlow<List<DeletedEvent>>(emptyList())
    val items: StateFlow<List<DeletedEvent>> = _items.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _items.value = prune(read(context), System.currentTimeMillis())
    }

    fun add(context: Context, event: CalendarEvent, now: Long = System.currentTimeMillis()) {
        load(context)
        val list = listOf(DeletedEvent(event, now)) + _items.value.filter { it.event.id != event.id }
        write(context, prune(list, now))
    }

    fun remove(context: Context, eventId: String) {
        load(context)
        write(context, _items.value.filter { it.event.id != eventId })
    }

    /** 30일이 지난 것과 50개를 넘는 것은 정리 */
    internal fun prune(list: List<DeletedEvent>, now: Long): List<DeletedEvent> =
        list.filter { now - it.deletedAtMillis < KEEP_DAYS * DAY_MILLIS }
            .sortedByDescending { it.deletedAtMillis }
            .take(MAX_ITEMS)

    private fun write(context: Context, list: List<DeletedEvent>) {
        _items.value = list
        val json = JSONArray()
        list.forEach { d ->
            json.put(JSONObject().put("at", d.deletedAtMillis).put("event", WidgetStore.encode(listOf(d.event))))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
    }

    private fun read(context: Context): List<DeletedEvent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i ->
                val o = json.getJSONObject(i)
                val event = WidgetStore.decode(o.getString("event")).firstOrNull() ?: return@mapNotNull null
                DeletedEvent(event, o.getLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}

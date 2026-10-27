package com.uridul.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import com.uridul.app.domain.Birthday

/** 위젯이 네트워크 없이 바로 그릴 수 있도록 폰에 저장해 두는 최소 정보. */
data class WidgetSnapshot(
    val startDate: LocalDate,
    val countFromOne: Boolean,
    val myName: String,
    val partnerName: String?,
    val coupleId: String? = null,
    val myUid: String? = null,
    val myCharacter: String? = null,
    val partnerCharacter: String? = null,
    val birthdays: List<Birthday> = emptyList(),
)

private val Context.widgetDataStore by preferencesDataStore(name = "widget_state")

object WidgetStore {
    private val START = stringPreferencesKey("start_date")
    private val COUNT_FROM_ONE = booleanPreferencesKey("count_from_one")
    private val MY_NAME = stringPreferencesKey("my_name")
    private val PARTNER_NAME = stringPreferencesKey("partner_name")
    private val COUPLE_ID = stringPreferencesKey("couple_id")
    private val MY_UID = stringPreferencesKey("my_uid")
    private val MY_CHAR = stringPreferencesKey("my_character")
    private val PARTNER_CHAR = stringPreferencesKey("partner_character")
    private val EVENTS = stringPreferencesKey("upcoming_events")
    private val BIRTHDAYS = stringPreferencesKey("birthdays")
    private val MONTH = stringPreferencesKey("month_events_month")
    private val MONTH_EVENTS = stringPreferencesKey("month_events")

    suspend fun save(context: Context, snapshot: WidgetSnapshot) {
        context.widgetDataStore.edit {
            it[START] = snapshot.startDate.toString()
            it[COUNT_FROM_ONE] = snapshot.countFromOne
            it[MY_NAME] = snapshot.myName
            if (snapshot.partnerName != null) it[PARTNER_NAME] = snapshot.partnerName else it.remove(PARTNER_NAME)
            if (snapshot.coupleId != null) it[COUPLE_ID] = snapshot.coupleId else it.remove(COUPLE_ID)
            if (snapshot.myUid != null) it[MY_UID] = snapshot.myUid else it.remove(MY_UID)
            if (snapshot.myCharacter != null) it[MY_CHAR] = snapshot.myCharacter else it.remove(MY_CHAR)
            if (snapshot.partnerCharacter != null) it[PARTNER_CHAR] = snapshot.partnerCharacter else it.remove(PARTNER_CHAR)
            it[BIRTHDAYS] = snapshot.birthdays.joinToString("\n") { b -> "${b.date}|${b.name}" }
        }
    }

    suspend fun load(context: Context): WidgetSnapshot? {
        val prefs = context.widgetDataStore.data.first()
        val start = prefs[START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return WidgetSnapshot(
            startDate = start,
            countFromOne = prefs[COUNT_FROM_ONE] ?: true,
            myName = prefs[MY_NAME] ?: "나",
            partnerName = prefs[PARTNER_NAME],
            coupleId = prefs[COUPLE_ID],
            myUid = prefs[MY_UID],
            myCharacter = prefs[MY_CHAR],
            partnerCharacter = prefs[PARTNER_CHAR],
            birthdays = prefs[BIRTHDAYS].orEmpty().lines().mapNotNull { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                runCatching { Birthday(parts[1], MonthDay.parse(parts[0])) }.getOrNull()
            },
        )
    }

    /** 다가오는 일정 (일정 목록 위젯·알림용) */
    suspend fun saveEvents(context: Context, events: List<CalendarEvent>) {
        context.widgetDataStore.edit { it[EVENTS] = encode(events) }
    }

    suspend fun loadEvents(context: Context): List<CalendarEvent> =
        decode(context.widgetDataStore.data.first()[EVENTS])

    /** 한 달 달력 위젯이 마지막으로 받아 온 달의 일정 (네트워크가 안 될 때 대신 보여준다) */
    suspend fun saveMonthEvents(context: Context, month: YearMonth, events: List<CalendarEvent>) {
        context.widgetDataStore.edit {
            it[MONTH] = month.toString()
            it[MONTH_EVENTS] = encode(events)
        }
    }

    /** 저장해 둔 달이 다르면 다가오는 일정 목록으로 대신한다 */
    suspend fun loadMonthEvents(context: Context, month: YearMonth): List<CalendarEvent> {
        val prefs = context.widgetDataStore.data.first()
        return if (prefs[MONTH] == month.toString()) decode(prefs[MONTH_EVENTS]) else decode(prefs[EVENTS])
    }

    private fun encode(events: List<CalendarEvent>): String {
        val json = JSONArray()
        events.forEach { e ->
            json.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("date", e.date.toString())
                    .put("endDate", e.endDate.toString())
                    .put("time", e.time?.toString() ?: JSONObject.NULL)
                    .put("createdBy", e.createdBy),
            )
        }
        return json.toString()
    }

    private fun decode(raw: String?): List<CalendarEvent> {
        if (raw == null) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i ->
                val o = json.getJSONObject(i)
                runCatching {
                    CalendarEvent(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        date = LocalDate.parse(o.getString("date")),
                        endDate = LocalDate.parse(o.getString("endDate")),
                        time = if (o.isNull("time")) null else LocalTime.parse(o.getString("time")),
                        memo = "",
                        createdBy = o.optString("createdBy"),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun clear(context: Context) {
        context.widgetDataStore.edit { it.clear() }
    }
}

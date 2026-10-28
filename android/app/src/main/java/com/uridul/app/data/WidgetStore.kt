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

    internal fun encode(events: List<CalendarEvent>): String {
        val json = JSONArray()
        events.forEach { e ->
            json.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("date", e.date.toString())
                    .put("endDate", e.endDate.toString())
                    .put("time", e.time?.toString() ?: JSONObject.NULL)
                    .put("createdBy", e.createdBy)
                    .put("memo", e.memo)
                    .put("remind", e.remind ?: JSONObject.NULL)
                    .put("repeat", e.repeat?.key ?: JSONObject.NULL)
                    .put("skip", JSONArray(e.skip.sorted().map { it.toString() }))
                    .put("seriesStart", e.seriesStart?.toString() ?: JSONObject.NULL)
                    .put("placeName", e.place?.name ?: JSONObject.NULL)
                    .put("placeUrl", e.place?.url ?: JSONObject.NULL)
                    .put("placeApp", e.place?.app?.key ?: JSONObject.NULL)
                    .put("timeline", JSONArray().also { arr ->
                        e.timeline.forEach { s ->
                            arr.put(
                                JSONObject()
                                    .put("start", s.start.toString())
                                    .put("end", s.end?.toString() ?: JSONObject.NULL)
                                    .put("title", s.title)
                                    .put("placeName", s.place?.name ?: JSONObject.NULL)
                                    .put("placeUrl", s.place?.url ?: JSONObject.NULL)
                                    .put("placeApp", s.place?.app?.key ?: JSONObject.NULL),
                            )
                        }
                    }),
            )
        }
        return json.toString()
    }

    internal fun decode(raw: String?): List<CalendarEvent> {
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
                        memo = o.optString("memo"),
                        remind = if (o.has("remind") && !o.isNull("remind")) o.getInt("remind") else null,
                        repeat = Repeat.from(if (o.isNull("repeat")) null else o.optString("repeat")),
                        skip = o.optJSONArray("skip")?.let { arr ->
                            (0 until arr.length()).mapNotNull { k -> runCatching { LocalDate.parse(arr.getString(k)) }.getOrNull() }.toSet()
                        }.orEmpty(),
                        seriesStart = if (!o.has("seriesStart") || o.isNull("seriesStart")) null
                        else runCatching { LocalDate.parse(o.getString("seriesStart")) }.getOrNull(),
                        createdBy = o.optString("createdBy"),
                        place = if (o.isNull("placeName") || !o.has("placeName")) null else Place(
                            o.getString("placeName"),
                            if (o.isNull("placeUrl")) null else o.optString("placeUrl"),
                            MapApp.from(if (o.isNull("placeApp")) null else o.optString("placeApp")),
                        ),
                        timeline = o.optJSONArray("timeline")?.let { arr ->
                            (0 until arr.length()).mapNotNull { j ->
                                val s = arr.getJSONObject(j)
                                runCatching {
                                    TimeSlot(
                                        start = LocalTime.parse(s.getString("start")),
                                        end = if (s.isNull("end")) null else LocalTime.parse(s.getString("end")),
                                        title = s.optString("title"),
                                        place = if (s.isNull("placeName")) null else Place(
                                            s.getString("placeName"),
                                            if (s.isNull("placeUrl")) null else s.optString("placeUrl"),
                                            MapApp.from(if (s.isNull("placeApp")) null else s.optString("placeApp")),
                                        ),
                                    )
                                }.getOrNull()
                            }
                        }.orEmpty(),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun clear(context: Context) {
        context.widgetDataStore.edit { it.clear() }
    }
}

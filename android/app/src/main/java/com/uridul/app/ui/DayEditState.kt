package com.uridul.app.ui

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.data.Repeat
import com.uridul.app.data.TimeSlot
import com.uridul.app.data.toDraft
import com.uridul.app.data.normalized
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

// 하루 페이지 편집 상태: 입력값 + 고치는 시간대 + 저장용 JSON. 접고 펴도 남도록 글자로 저장한다.

/** 아직 저장 전인 새 일정의 임시 ID */
internal const val NEW_ID = "__new__"

/** 편집 중: 일정 입력값 + 고치는 시간대 번호(-1: 하루 종일) + 새 일정인지 + 원래 값(바뀌었는지 비교) */
internal data class SlotEdit(val draft: EventDraft, val slot: Int, val isNew: Boolean, val original: EventDraft?) {

    fun finalDraft(): EventDraft = if (slot < 0) {
        draft.copy(time = null, timeline = emptyList())
    } else {
        // 저장할 때는 날짜·시간 순으로 (대표 시작 시각·알림·정렬이 맞도록)
        draft.copy(time = draft.timeline.firstOrNull()?.start, place = null).normalized()
    }

    fun updateSlot(change: (TimeSlot) -> TimeSlot): SlotEdit {
        val list = draft.timeline.toMutableList()
        if (slot !in list.indices) return this
        list[slot] = change(list[slot])
        return copy(draft = draft.copy(timeline = list))
    }

    /** 시작을 옮기면 끝도 같은 길이만큼 따라간다 */
    fun moveStart(t: LocalTime): SlotEdit = updateSlot { s ->
        val length = s.end?.let { java.time.Duration.between(s.start, it).toMinutes() }?.takeIf { it > 0 } ?: 60
        val end = clampTime(t.toSecondOfDay() / 60 + length.toInt())
        s.copy(start = t, end = if (end.isAfter(t)) end else LocalTime.of(23, 59))
    }

    fun setEnd(t: LocalTime): SlotEdit = updateSlot { s ->
        // 끝이 시작보다 늦어야 한다 (최소 15분). 예전 여러 날 일정의 한 줄만 마지막 날 시각이라 그대로
        val legacySpan = draft.endDate.isAfter(draft.date) && s.day == null
        s.copy(end = if (legacySpan || t.isAfter(s.start)) t else clampTime(s.start.toSecondOfDay() / 60 + 15))
    }

    /** 시간대 하나 더. [day] = 여러 날 일정에서 며칠째 (하루 일정은 null) */
    fun addSlot(day: Int? = null): SlotEdit {
        val list = draft.timeline + TimeSlot.next(draft.timeline, day)
        return copy(draft = draft.copy(timeline = list), slot = list.lastIndex)
    }

    fun removeSlot(): SlotEdit {
        if (draft.timeline.size <= 1) return this
        val list = draft.timeline.toMutableList().also { it.removeAt(slot) }
        return copy(draft = draft.copy(timeline = list), slot = (slot - 1).coerceAtLeast(0))
    }

    fun toggleAllDay(allDay: Boolean): SlotEdit = if (allDay) {
        copy(draft = draft.copy(place = draft.place ?: draft.timeline.getOrNull(slot)?.place), slot = -1)
    } else {
        val multiDay = draft.endDate.isAfter(draft.date)
        val list = draft.timeline.ifEmpty { listOf(TimeSlot(LocalTime.of(19, 0), LocalTime.of(20, 0), "", draft.place, day = if (multiDay) 0 else null)) }
        copy(draft = draft.copy(timeline = list), slot = 0)
    }

    /** 장소 넣기. 새 일정이고 제목이 비어 있으면 장소 이름을 제목으로 */
    fun withPlace(p: Place): SlotEdit {
        val titled = if (isNew && draft.title.isBlank()) copy(draft = draft.copy(title = p.name.take(CalendarEvent.TITLE_MAX))) else this
        return if (slot < 0) titled.copy(draft = titled.draft.copy(place = p)) else titled.updateSlot { it.copy(place = p) }
    }
}

internal fun editOf(event: CalendarEvent, slot: Int = 0): SlotEdit {
    // 예전 여러 날 일정(시작~끝 한 줄)은 첫날 시간대로
    val d = event.toDraft().normalized()
    return SlotEdit(d, if (event.allDay) -1 else slot.coerceIn(0, (d.timeline.size - 1).coerceAtLeast(0)), false, d)
}

internal fun newEdit(date: LocalDate, today: LocalDate, at: Int? = null, allDay: Boolean = false): SlotEdit {
    val startMin = at ?: if (date == today) ((LocalTime.now().hour + 1).coerceAtMost(23)) * 60 else 19 * 60
    val start = clampTime(startMin)
    val end = clampTime(startMin + 60).let { if (it.isAfter(start)) it else LocalTime.of(23, 59) }
    val d = EventDraft(
        title = "", date = date, endDate = date,
        time = if (allDay) null else start, memo = "",
        timeline = if (allDay) emptyList() else listOf(TimeSlot(start, end)),
    )
    return SlotEdit(d, if (allDay) -1 else 0, true, d)
}

internal fun clampTime(minute: Int): LocalTime = LocalTime.of(0, 0).plusMinutes(minute.coerceIn(0, 23 * 60 + 59).toLong())

/** 미리보기용: 입력값을 일정처럼 */
internal fun EventDraft.toPreview(createdBy: String): CalendarEvent = CalendarEvent(
    id = id ?: NEW_ID,
    title = title.ifBlank { "새 일정" },
    date = date,
    endDate = endDate,
    time = if (timeline.isEmpty()) null else timeline.first().start,
    memo = memo,
    createdBy = createdBy,
    place = place,
    timeline = timeline,
    remind = remind,
    repeat = repeat,
    color = color,
    category = category,
)

/** 편집 창 상태 저장용: 시간대·장소 ↔ JSON (하루 페이지 편집 칸과 일정 편집 창이 함께 쓴다) */
internal object SlotJson {
    fun place(p: Place?): Any = p?.let {
        JSONObject().put("name", it.name).put("url", it.url ?: JSONObject.NULL).put("app", it.app?.key ?: JSONObject.NULL)
    } ?: JSONObject.NULL

    fun parsePlace(o: JSONObject, key: String): Place? = if (!o.has(key) || o.isNull(key)) null else o.getJSONObject(key).let {
        Place(it.getString("name"), if (it.isNull("url")) null else it.getString("url"), MapApp.from(if (it.isNull("app")) null else it.getString("app")))
    }

    fun slots(list: List<TimeSlot>): JSONArray = JSONArray().also { arr ->
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("start", s.start.toString())
                    .put("end", s.end?.toString() ?: JSONObject.NULL)
                    .put("title", s.title)
                    .put("place", place(s.place))
                    .put("day", s.day ?: JSONObject.NULL),
            )
        }
    }

    fun parseSlots(arr: JSONArray): List<TimeSlot> = (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        TimeSlot(
            start = LocalTime.parse(s.getString("start")),
            end = if (s.isNull("end")) null else LocalTime.parse(s.getString("end")),
            title = s.optString("title"),
            place = parsePlace(s, "place"),
            day = if (!s.has("day") || s.isNull("day")) null else s.getInt("day"),
        )
    }
}

/** 편집 상태 저장용 JSON */
internal object EditCodec {
    fun encode(e: SlotEdit): String = JSONObject()
        .put("draft", draftJson(e.draft))
        .put("slot", e.slot)
        .put("new", e.isNew)
        .put("orig", e.original?.let(::draftJson) ?: JSONObject.NULL)
        .toString()

    fun decode(raw: String): SlotEdit? = if (raw.isEmpty()) null else runCatching {
        val o = JSONObject(raw)
        SlotEdit(
            draft = parseDraft(o.getJSONObject("draft")),
            slot = o.getInt("slot"),
            isNew = o.getBoolean("new"),
            original = if (o.isNull("orig")) null else parseDraft(o.getJSONObject("orig")),
        )
    }.getOrNull()

    private fun draftJson(d: EventDraft): JSONObject = JSONObject()
        .put("id", d.id ?: JSONObject.NULL)
        .put("title", d.title)
        .put("date", d.date.toString())
        .put("endDate", d.endDate.toString())
        .put("time", d.time?.toString() ?: JSONObject.NULL)
        .put("memo", d.memo)
        .put("place", SlotJson.place(d.place))
        .put("remind", d.remind ?: JSONObject.NULL)
        .put("repeat", d.repeat?.key ?: JSONObject.NULL)
        .put("color", d.color?.key ?: JSONObject.NULL)
        .put("category", d.category.key)
        .put("seriesStart", d.seriesStart?.toString() ?: JSONObject.NULL)
        .put("occurrence", d.occurrence?.toString() ?: JSONObject.NULL)
        .put("timeline", SlotJson.slots(d.timeline))

    private fun parseDraft(o: JSONObject): EventDraft {
        val arr = o.getJSONArray("timeline")
        return EventDraft(
            id = if (o.isNull("id")) null else o.getString("id"),
            title = o.getString("title"),
            date = LocalDate.parse(o.getString("date")),
            endDate = LocalDate.parse(o.getString("endDate")),
            time = if (o.isNull("time")) null else LocalTime.parse(o.getString("time")),
            memo = o.getString("memo"),
            place = SlotJson.parsePlace(o, "place"),
            remind = if (o.has("remind") && !o.isNull("remind")) o.getInt("remind") else null,
            repeat = Repeat.from(if (!o.has("repeat") || o.isNull("repeat")) null else o.getString("repeat")),
            color = com.uridul.app.data.EventColor.fromOrNull(if (!o.has("color") || o.isNull("color")) null else o.getString("color")),
            category = com.uridul.app.data.EventCategory.from(if (!o.has("category") || o.isNull("category")) null else o.getString("category")),
            seriesStart = if (!o.has("seriesStart") || o.isNull("seriesStart")) null else LocalDate.parse(o.getString("seriesStart")),
            occurrence = if (!o.has("occurrence") || o.isNull("occurrence")) null else LocalDate.parse(o.getString("occurrence")),
            timeline = SlotJson.parseSlots(arr),
        )
    }
}

package com.uridul.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.EventDraft
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.data.Remind
import com.uridul.app.data.Repeat
import com.uridul.app.data.TimeSlot
import com.uridul.app.data.toDraft
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.DayTimeline
import com.uridul.app.domain.SpecialDay
import com.uridul.app.ui.theme.CoupleColors
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
        // 하루 종일에 없는 알림 값(예: 1시간 전)은 기본값으로
        val remind = draft.remind?.takeIf { r -> Remind.allDayOptions.any { it.minutes == r } }
        draft.copy(time = null, timeline = emptyList(), remind = remind)
    } else {
        // 저장할 때는 시간 순으로 (대표 시작 시각·알림·정렬이 맞도록)
        val sorted = draft.timeline.sortedBy { it.start }
        draft.copy(time = sorted.firstOrNull()?.start, place = null, timeline = sorted)
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
        val multiDay = draft.endDate.isAfter(draft.date)
        // 하루 일정은 끝이 시작보다 늦어야 한다 (최소 15분)
        s.copy(end = if (multiDay || t.isAfter(s.start)) t else clampTime(s.start.toSecondOfDay() / 60 + 15))
    }

    fun addSlot(): SlotEdit {
        val last = draft.timeline.maxByOrNull { it.start }
        val from = last?.end ?: last?.start?.plusHours(1) ?: LocalTime.of(19, 0)
        val to = clampTime(from.toSecondOfDay() / 60 + 60)
        val list = draft.timeline + TimeSlot(from, if (to.isAfter(from)) to else LocalTime.of(23, 59))
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
        val list = draft.timeline.ifEmpty { listOf(TimeSlot(LocalTime.of(19, 0), LocalTime.of(20, 0), "", draft.place)) }
        copy(draft = draft.copy(timeline = list), slot = 0)
    }

    /** 장소 넣기. 새 일정이고 제목이 비어 있으면 장소 이름을 제목으로 */
    fun withPlace(p: Place): SlotEdit {
        val titled = if (isNew && draft.title.isBlank()) copy(draft = draft.copy(title = p.name.take(CalendarEvent.TITLE_MAX))) else this
        return if (slot < 0) titled.copy(draft = titled.draft.copy(place = p)) else titled.updateSlot { it.copy(place = p) }
    }
}

internal fun editOf(event: CalendarEvent, slot: Int = 0): SlotEdit {
    val d = event.toDraft()
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
)

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

    private fun placeJson(p: Place?): Any = p?.let {
        JSONObject().put("name", it.name).put("url", it.url ?: JSONObject.NULL).put("app", it.app?.key ?: JSONObject.NULL)
    } ?: JSONObject.NULL

    private fun parsePlace(o: JSONObject, key: String): Place? = if (o.isNull(key)) null else o.getJSONObject(key).let {
        Place(it.getString("name"), if (it.isNull("url")) null else it.getString("url"), MapApp.from(if (it.isNull("app")) null else it.getString("app")))
    }

    private fun draftJson(d: EventDraft): JSONObject = JSONObject()
        .put("id", d.id ?: JSONObject.NULL)
        .put("title", d.title)
        .put("date", d.date.toString())
        .put("endDate", d.endDate.toString())
        .put("time", d.time?.toString() ?: JSONObject.NULL)
        .put("memo", d.memo)
        .put("place", placeJson(d.place))
        .put("remind", d.remind ?: JSONObject.NULL)
        .put("repeat", d.repeat?.key ?: JSONObject.NULL)
        .put("seriesStart", d.seriesStart?.toString() ?: JSONObject.NULL)
        .put("occurrence", d.occurrence?.toString() ?: JSONObject.NULL)
        .put("timeline", JSONArray().also { arr ->
            d.timeline.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("start", s.start.toString())
                        .put("end", s.end?.toString() ?: JSONObject.NULL)
                        .put("title", s.title)
                        .put("place", placeJson(s.place)),
                )
            }
        })

    private fun parseDraft(o: JSONObject): EventDraft {
        val arr = o.getJSONArray("timeline")
        return EventDraft(
            id = if (o.isNull("id")) null else o.getString("id"),
            title = o.getString("title"),
            date = LocalDate.parse(o.getString("date")),
            endDate = LocalDate.parse(o.getString("endDate")),
            time = if (o.isNull("time")) null else LocalTime.parse(o.getString("time")),
            memo = o.getString("memo"),
            place = parsePlace(o, "place"),
            remind = if (o.has("remind") && !o.isNull("remind")) o.getInt("remind") else null,
            repeat = Repeat.from(if (!o.has("repeat") || o.isNull("repeat")) null else o.getString("repeat")),
            seriesStart = if (!o.has("seriesStart") || o.isNull("seriesStart")) null else LocalDate.parse(o.getString("seriesStart")),
            occurrence = if (!o.has("occurrence") || o.isNull("occurrence")) null else LocalDate.parse(o.getString("occurrence")),
            timeline = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                TimeSlot(
                    start = LocalTime.parse(s.getString("start")),
                    end = if (s.isNull("end")) null else LocalTime.parse(s.getString("end")),
                    title = s.getString("title"),
                    place = parsePlace(s, "place"),
                )
            },
        )
    }
}

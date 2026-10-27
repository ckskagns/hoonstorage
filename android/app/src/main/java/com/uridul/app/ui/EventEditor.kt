package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place
import com.uridul.app.data.TimeSlot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * 일정 추가·수정 창. 폰에서는 거의 전체 폭, 폴드 펼침에서는 가운데 560dp.
 *  - 하루 종일: 한 줄 + 오른쪽 장소
 *  - 시간 일정: 타임라인 (시작~끝, 내용, 오른쪽 장소) 여러 줄
 * 저장에 성공하면 onSave 가 true 를 돌려주고 창이 닫힌다.
 */
@Composable
fun EventEditorDialog(
    initial: EventDraft,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Boolean,
    onDelete: (() -> Unit)?,
    /** 지도 앱에서 "공유 → 우리둘" 로 받은 장소 (받으면 장소를 찾던 줄에 넣는다) */
    incomingPlace: Place? = null,
    onIncomingPlaceConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var date by rememberSaveable { mutableStateOf(initial.date.toString()) }
    var endDate by rememberSaveable { mutableStateOf(initial.endDate.toString()) }
    var allDay by rememberSaveable { mutableStateOf(initial.time == null) }
    // 타임라인·하루 종일 장소는 화면 회전에도 남도록 글자로 저장해 둔다
    var slotsJson by rememberSaveable { mutableStateOf(SlotCodec.encode(initial.timeline)) }
    var dayPlaceJson by rememberSaveable { mutableStateOf(SlotCodec.encodePlace(initial.place)) }
    var memo by rememberSaveable { mutableStateOf(initial.memo) }
    /** 장소를 찾고 있는 줄 (-1: 하루 종일 줄) */
    var placeTarget by rememberSaveable { mutableStateOf(-1) }
    var choosingMap by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) } // "start" | "end" | "slot:3:start" | "slot:3:end"
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val slots = SlotCodec.decode(slotsJson)
    val dayPlace = SlotCodec.decodePlace(dayPlaceJson)
    val start = LocalDate.parse(date)
    val end = LocalDate.parse(endDate)
    val multiDay = end.isAfter(start)
    fun setSlots(list: List<TimeSlot>) {
        slotsJson = SlotCodec.encode(list)
    }

    // 지도 앱에서 공유받은 장소가 오면 장소를 찾던 줄에 넣는다
    LaunchedEffect(incomingPlace) {
        val p = incomingPlace ?: return@LaunchedEffect
        val current = SlotCodec.decode(slotsJson)
        if (allDay || current.isEmpty()) {
            dayPlaceJson = SlotCodec.encodePlace(p)
        } else {
            val i = placeTarget.takeIf { it in current.indices } ?: 0
            setSlots(current.toMutableList().also { it[i] = it[i].copy(place = p) })
        }
        onIncomingPlaceConsumed()
    }

    fun buildDraft(): EventDraft {
        val timeline = if (allDay) emptyList() else slots
        return EventDraft(
            id = initial.id,
            title = title,
            date = start,
            endDate = end,
            time = timeline.firstOrNull()?.start,
            memo = memo,
            place = if (allDay) dayPlace else null,
            timeline = timeline,
        )
    }

    val save: () -> Unit = {
        if (onSave(buildDraft())) onDismiss()
    }
    // 뒤로 가기·취소: 바꾼 내용이 있으면 한 번 더 묻는다 (키보드 내리려다 내용이 날아가는 것 방지)
    val requestClose: () -> Unit = {
        if (buildDraft() != initial) confirmDiscard = true else onDismiss()
    }
    val findPlace: (Int) -> Unit = { index ->
        placeTarget = index
        choosingMap = true
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 창이 뿅 하고 말랑하게 커지며 나타난다
            val appear = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
            androidx.compose.animation.AnimatedVisibility(
                visibleState = appear,
                enter = androidx.compose.animation.scaleIn(
                    initialScale = 0.9f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f),
                ) + androidx.compose.animation.fadeIn(),
            ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
            ) {
                Column {
                    // 머리글: 키보드가 올라와도 항상 보이도록 저장 버튼을 위에 둔다
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (initial.id == null) "일정 추가" else "일정 수정",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = save) { Text("저장") }
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(CalendarEvent.TITLE_MAX) },
                            label = { Text("제목") },
                            placeholder = { Text("예) 저녁 데이트") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        LabeledButton("시작", start.format(KoreanDate)) { picker = "start" }
                        LabeledButton("끝", end.format(KoreanDate)) { picker = "end" }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("하루 종일", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(
                                checked = allDay,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        // 시간대 장소를 하루 종일 장소로 옮긴다
                                        if (dayPlace == null) dayPlaceJson = SlotCodec.encodePlace(slots.firstOrNull()?.place)
                                    } else if (slots.isEmpty()) {
                                        setSlots(listOf(TimeSlot(LocalTime.of(19, 0), LocalTime.of(20, 0), "", dayPlace)))
                                    }
                                    allDay = checked
                                },
                            )
                        }

                        if (allDay) {
                            // 하루 종일: 한 줄 + 오른쪽 장소
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("하루 종일", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    PlaceTag(
                                        place = dayPlace,
                                        onFind = { findPlace(-1) },
                                        onOpen = { MapLinks.open(context, it) },
                                        onRemove = { dayPlaceJson = "" },
                                    )
                                }
                            }
                        } else {
                            Text(
                                if (multiDay) "시간 (첫날 시작 ~ 마지막 날 끝)" else "타임라인",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            slots.forEachIndexed { i, slot ->
                                SlotRow(
                                    slot = slot,
                                    removable = slots.size > 1,
                                    onStart = { picker = "slot:$i:start" },
                                    onEnd = { picker = "slot:$i:end" },
                                    onTitle = { text -> setSlots(slots.toMutableList().also { it[i] = slot.copy(title = text.take(TimeSlot.TITLE_MAX)) }) },
                                    onFindPlace = { findPlace(i) },
                                    onOpenPlace = { MapLinks.open(context, it) },
                                    onRemovePlace = { setSlots(slots.toMutableList().also { it[i] = slot.copy(place = null) }) },
                                    onRemove = { setSlots(slots.toMutableList().also { it.removeAt(i) }) },
                                )
                            }
                            // 하루 일정만 시간대를 여러 개 (여러 날 일정은 시작~끝 한 줄)
                            if (!multiDay && slots.size < TimeSlot.MAX_SLOTS) {
                                OutlinedButton(
                                    onClick = {
                                        val last = slots.lastOrNull()
                                        val from = (last?.end ?: last?.start?.plusHours(1) ?: LocalTime.of(19, 0))
                                        val to = if (from.hour >= 23) LocalTime.of(23, 59) else from.plusHours(1)
                                        setSlots(slots + TimeSlot(from, to))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("+ 시간대 추가") }
                            }
                        }

                        OutlinedTextField(
                            value = memo,
                            onValueChange = { memo = it.take(CalendarEvent.MEMO_MAX) },
                            label = { Text("메모 (선택)") },
                            minLines = 2,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onDelete != null) {
                                TextButton(onClick = { confirmDelete = true }) {
                                    Text("삭제", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = requestClose) { Text("취소") }
                        }
                    }
                }
            }
            }
        }
    }

    if (choosingMap) {
        MapChooserDialog(
            onDismiss = { choosingMap = false },
            onChoose = { app ->
                choosingMap = false
                val slotTitle = slots.getOrNull(placeTarget)?.title.orEmpty()
                PlaceInbox.expectEditor()
                MapLinks.startSearch(context, app, slotTitle.ifBlank { title }.trim())
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("작성 중인 내용을 버릴까요?") },
            text = { Text("저장하지 않은 내용은 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("버리기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("계속 쓰기") } },
        )
    }

    val p = picker
    when {
        p == "start" -> StartDatePickerDialog(
            initial = start,
            title = "시작하는 날",
            onDismiss = { picker = null },
            onConfirm = {
                // 끝나는 날은 같은 길이만큼 함께 옮긴다
                val length = java.time.temporal.ChronoUnit.DAYS.between(start, end)
                date = it.toString()
                endDate = it.plusDays(length).toString()
                picker = null
            },
        )
        p == "end" -> StartDatePickerDialog(
            initial = end,
            title = "끝나는 날",
            onDismiss = { picker = null },
            onConfirm = {
                val newEnd = maxOf(it, start)
                endDate = newEnd.toString()
                // 여러 날 일정이 되면 시간대는 한 줄만 남긴다
                if (newEnd.isAfter(start) && slots.size > 1) setSlots(slots.take(1))
                picker = null
            },
        )
        p != null && p.startsWith("slot:") -> {
            val (_, index, which) = p.split(":")
            val i = index.toInt()
            val slot = slots.getOrNull(i)
            if (slot == null) {
                picker = null
            } else {
                TimePickerDialog(
                    initial = if (which == "start") slot.start else slot.end ?: slot.start.plusHours(1),
                    title = if (which == "start") "시작 시간" else "끝 시간",
                    onDismiss = { picker = null },
                    onConfirm = { t ->
                        val updated = if (which == "start") {
                            // 시작을 옮기면 끝도 같은 길이만큼 따라간다
                            val length = slot.end?.let { java.time.Duration.between(slot.start, it) }
                            val newEnd = length?.takeIf { !it.isNegative }?.let { d ->
                                t.plus(d).takeIf { it.isAfter(t) } ?: LocalTime.of(23, 59)
                            } ?: slot.end
                            slot.copy(start = t, end = newEnd)
                        } else {
                            slot.copy(end = t)
                        }
                        setSlots(slots.toMutableList().also { it[i] = updated })
                        picker = null
                    },
                )
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("일정을 삭제할까요?") },
            text = { Text("상대 폰에서도 함께 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    onDismiss()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

/** 타임라인 한 줄: [시작] ~ [끝]   ✕ / [내용]  [장소] */
@Composable
private fun SlotRow(
    slot: TimeSlot,
    removable: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onTitle: (String) -> Unit,
    onFindPlace: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onRemovePlace: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onStart) { Text(slot.start.format(KoreanTime), fontWeight = FontWeight.Bold) }
                Text("~", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onEnd) { Text(slot.end?.format(KoreanTime) ?: "끝 시간", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                if (removable) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "시간대 지우기") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = slot.title,
                    onValueChange = onTitle,
                    placeholder = { Text("내용 (예: 저녁식사)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                PlaceTag(place = slot.place, onFind = onFindPlace, onOpen = onOpenPlace, onRemove = onRemovePlace)
            }
        }
    }
}

@Composable
private fun LabeledButton(label: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 입력 창 상태 저장용: 타임라인·장소를 글자(JSON)로 */
private object SlotCodec {
    fun encode(slots: List<TimeSlot>): String = JSONArray().also { arr ->
        slots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("start", s.start.toString())
                    .put("end", s.end?.toString() ?: JSONObject.NULL)
                    .put("title", s.title)
                    .put("place", encodePlace(s.place).ifEmpty { JSONObject.NULL }),
            )
        }
    }.toString()

    fun decode(raw: String): List<TimeSlot> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TimeSlot(
                start = LocalTime.parse(o.getString("start")),
                end = if (o.isNull("end")) null else LocalTime.parse(o.getString("end")),
                title = o.optString("title"),
                place = if (o.isNull("place")) null else decodePlace(o.getString("place")),
            )
        }
    }.getOrDefault(emptyList())

    fun encodePlace(p: Place?): String = p?.let {
        JSONObject()
            .put("name", it.name)
            .put("url", it.url ?: JSONObject.NULL)
            .put("app", it.app?.key ?: JSONObject.NULL)
            .toString()
    }.orEmpty()

    fun decodePlace(raw: String): Place? = if (raw.isEmpty()) null else runCatching {
        val o = JSONObject(raw)
        Place(o.getString("name"), if (o.isNull("url")) null else o.getString("url"), MapApp.from(if (o.isNull("app")) null else o.getString("app")))
    }.getOrNull()
}

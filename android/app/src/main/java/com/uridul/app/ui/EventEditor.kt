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
import com.uridul.app.data.Place
import com.uridul.app.data.TimeSlot
import com.uridul.app.data.normalized
import com.uridul.app.data.daysBetween
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
    /** 색을 고르지 않았을 때의 색 (등록한 사람 색) */
    defaultColor: com.uridul.app.data.EventColor = com.uridul.app.ui.theme.LocalUiSettings.current.mine,
    /** 분류 이름 ("우리둘", "후이 일정", "나나 일정") */
    categoryLabel: (com.uridul.app.data.EventCategory) -> String = { it.key },
) {
    val context = LocalContext.current
    var colorKey by rememberSaveable { mutableStateOf(initial.color?.key) }
    var categoryKey by rememberSaveable { mutableStateOf(initial.category.key) }
    val category = com.uridul.app.data.EventCategory.from(categoryKey)
    val color = com.uridul.app.data.EventColor.fromOrNull(colorKey)
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var date by rememberSaveable { mutableStateOf(initial.date.toString()) }
    var endDate by rememberSaveable { mutableStateOf(initial.endDate.toString()) }
    var allDay by rememberSaveable { mutableStateOf(initial.time == null) }
    // 타임라인·하루 종일 장소는 화면 회전에도 남도록 글자로 저장해 둔다
    // 예전 여러 날 일정(시작~끝 한 줄)은 첫날 시간대로 바꿔서 보여준다
    val startingDraft = remember(initial) { initial.normalized() }
    var slotsJson by rememberSaveable { mutableStateOf(SlotCodec.encode(startingDraft.timeline)) }
    var dayPlaceJson by rememberSaveable { mutableStateOf(SlotCodec.encodePlace(initial.place)) }
    var memo by rememberSaveable { mutableStateOf(initial.memo) }
    /** 장소를 찾고 있는 줄 (-1: 하루 종일 줄) */
    var placeTarget by rememberSaveable { mutableStateOf(-1) }
    var choosingMap by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) } // "start" | "end" | "slot:3:start" | "slot:3:end"
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    // 글자 → 시간대: 바뀔 때만 다시 푼다 (입력할 때마다 다시 그려져도 매번 풀지 않게)
    val slots = remember(slotsJson) { SlotCodec.decode(slotsJson) }
    val dayPlace = remember(dayPlaceJson) { SlotCodec.decodePlace(dayPlaceJson) }
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
            // 알림 시간은 하루 페이지에서 고른 값을 그대로 (하루 종일에 없는 값이면 기본값)
            // 반복 정보는 하루 페이지에서 고른 값을 그대로 (반복 일정의 어느 날인지도)
            repeat = initial.repeat,
            seriesStart = initial.seriesStart,
            occurrence = initial.occurrence,
            color = color,
            category = category,
            remind = initial.remind,
        )
    }

    val save: () -> Unit = {
        // 저장할 때는 시간대를 날짜·시간 순으로 (대표 시작 시각·알림이 맞도록)
        if (onSave(buildDraft().normalized())) onDismiss()
    }
    // 뒤로 가기·취소: 바꾼 내용이 있으면 한 번 더 묻는다 (키보드 내리려다 내용이 날아가는 것 방지)
    val requestClose: () -> Unit = {
        if (buildDraft().normalized() != startingDraft) confirmDiscard = true else onDismiss()
    }
    /** 이 날(며칠째)에 시간대 하나 더 */
    fun addSlot(day: Int?) = setSlots(slots + TimeSlot.next(slots, day))
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

                        EventCategoryPicker(
                            selected = category,
                            label = categoryLabel,
                            onPick = { categoryKey = it.key },
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
                                        setSlots(listOf(TimeSlot(LocalTime.of(19, 0), LocalTime.of(20, 0), "", dayPlace, day = if (multiDay) 0 else null)))
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
                            val slotRow: @Composable (Int, TimeSlot) -> Unit = { i, slot ->
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
                            if (multiDay) {
                                // 여러 날 일정(여행 등): 날마다 따로 타임라인과 장소
                                val span = daysBetween(start, end)
                                Text("날짜별 타임라인", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                for (day in 0..span) {
                                    val daySlots = slots.withIndex().filter { it.value.dayIndex == day }.sortedBy { it.value.start }
                                    Text(
                                        tripDayLabel(start, day),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                    daySlots.forEach { (i, slot) -> slotRow(i, slot) }
                                    if (daySlots.size < TimeSlot.MAX_SLOTS && slots.size < TimeSlot.MAX_TOTAL_SLOTS) {
                                        OutlinedButton(onClick = { addSlot(day) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("+ ${day + 1}일차 시간대 추가")
                                        }
                                    }
                                }
                            } else {
                                Text("타임라인", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                slots.forEachIndexed { i, slot -> slotRow(i, slot) }
                                if (slots.size < TimeSlot.MAX_SLOTS) {
                                    OutlinedButton(onClick = { addSlot(null) }, modifier = Modifier.fillMaxWidth()) { Text("+ 시간대 추가") }
                                }
                            }
                        }

                        EventColorPicker(
                            selected = color,
                            defaultColor = defaultColor,
                            onPick = { colorKey = it?.key },
                        )

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
                PlaceInbox.expectEditor()
                MapLinks.startSearch(context, app)
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
                // 기간이 바뀌면 시간대를 그 기간에 맞춘다 (여러 날이 되면 지금 시간대는 첫날로, 줄면 빠진 날의 시간대는 버린다)
                val span = daysBetween(start, newEnd)
                setSlots(
                    if (span == 0) {
                        slots.filter { it.dayIndex == 0 }.map { it.copy(day = null) }
                    } else {
                        slots.map { s -> s.copy(day = s.dayIndex) }.filter { s -> s.dayIndex <= span }
                    }.ifEmpty { slots.take(1).map { it.copy(day = if (span == 0) null else 0) } },
                )
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
            title = { Text(if (initial.repeat != null) "반복 일정 전체를 삭제할까요?" else "일정을 삭제할까요?") },
            text = {
                Text(
                    (if (initial.repeat != null) "반복되는 모든 날이 함께 지워져요. 이 날만 빼려면 하루 페이지의 [일정 삭제] → 이 날만 빼기 를 눌러 주세요.\n" else "") +
                        "상대 폰에서도 함께 사라져요.\n지운 뒤 바로 [되돌리기]를 누르거나, 메뉴의 '최근 지운 일정'에서 30일 안에 되살릴 수 있어요.",
                )
            },
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
internal fun TimePickerDialog(
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

/** 입력 창 상태 저장용: 타임라인·장소를 글자(JSON)로 (모양은 하루 페이지 편집 칸과 같은 [SlotJson]) */
private object SlotCodec {
    fun encode(slots: List<TimeSlot>): String = SlotJson.slots(slots).toString()

    fun decode(raw: String): List<TimeSlot> = runCatching { SlotJson.parseSlots(JSONArray(raw)) }.getOrDefault(emptyList())

    fun encodePlace(p: Place?): String = if (p == null) "" else SlotJson.place(p).toString()

    fun decodePlace(raw: String): Place? = if (raw.isEmpty()) null else runCatching {
        SlotJson.parsePlace(JSONObject().put("p", JSONObject(raw)), "p")
    }.getOrNull()
}

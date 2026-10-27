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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import com.uridul.app.data.MapApp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import com.uridul.app.data.Place
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * 일정 추가·수정 창. 폰에서는 거의 전체 폭, 폴드 펼침에서는 가운데 560dp.
 * 저장에 성공하면 onSave 가 true 를 돌려주고 창이 닫힌다.
 */
@Composable
fun EventEditorDialog(
    initial: EventDraft,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Boolean,
    onDelete: (() -> Unit)?,
    /** 지도 앱에서 "공유 → 우리둘" 로 받은 장소 (받으면 장소 칸을 채운다) */
    incomingPlace: Place? = null,
    onIncomingPlaceConsumed: () -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var date by rememberSaveable { mutableStateOf(initial.date.toString()) }
    var endDate by rememberSaveable { mutableStateOf(initial.endDate.toString()) }
    var time by rememberSaveable { mutableStateOf(initial.time?.toString()) }
    var memo by rememberSaveable { mutableStateOf(initial.memo) }
    var placeName by rememberSaveable { mutableStateOf(initial.place?.name.orEmpty()) }
    var placeUrl by rememberSaveable { mutableStateOf(initial.place?.url) }
    var placeApp by rememberSaveable { mutableStateOf(initial.place?.app?.key) }
    var choosingMap by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val place: Place? = placeName.trim().takeIf { it.isNotEmpty() }?.let { Place(it, placeUrl, MapApp.from(placeApp)) }
    // 지도 앱에서 공유받은 장소가 오면 장소 칸을 채운다
    LaunchedEffect(incomingPlace) {
        val p = incomingPlace ?: return@LaunchedEffect
        placeName = p.name
        placeUrl = p.url
        placeApp = p.app?.key
        onIncomingPlaceConsumed()
    }
    var picker by rememberSaveable { mutableStateOf<String?>(null) } // "start" | "end" | "time"
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val start = LocalDate.parse(date)
    val end = LocalDate.parse(endDate)
    val localTime = time?.let(LocalTime::parse)

    val save: () -> Unit = {
        val draft = EventDraft(initial.id, title, start, end, localTime, memo, place)
        if (onSave(draft)) onDismiss()
    }
    // 뒤로 가기·취소: 바꾼 내용이 있으면 한 번 더 묻는다 (키보드 내리려다 내용이 날아가는 것 방지)
    val requestClose: () -> Unit = {
        val changed = title != initial.title || date != initial.date.toString() ||
            endDate != initial.endDate.toString() || time != initial.time?.toString() || memo != initial.memo ||
            place != initial.place
        if (changed) {
            confirmDiscard = true
        } else {
            onDismiss()
        }
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("하루 종일", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = time == null,
                            onCheckedChange = { allDay -> time = if (allDay) null else "19:00" },
                        )
                    }

                    LabeledButton("시작", start.format(KoreanDate)) { picker = "start" }
                    LabeledButton("끝", end.format(KoreanDate)) { picker = "end" }
                    if (localTime != null) {
                        LabeledButton("시간", localTime.format(KoreanTime)) { picker = "time" }
                    }

                    // 장소 (선택): 직접 쓰거나, 지도 앱에서 찾아 "공유 → 우리둘" 로 채운다
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = {
                            placeName = it.take(Place.NAME_MAX)
                            if (it.isBlank()) {
                                placeUrl = null
                                placeApp = null
                            }
                        },
                        label = { Text("장소 (선택)") },
                        placeholder = { Text("예) 선릉감자탕") },
                        leadingIcon = { Text("📍") },
                        trailingIcon = if (placeName.isNotEmpty()) {
                            {
                                IconButton(onClick = { placeName = ""; placeUrl = null; placeApp = null }) {
                                    Icon(Icons.Filled.Close, contentDescription = "장소 지우기")
                                }
                            }
                        } else {
                            null
                        },
                        supportingText = placeUrl?.let { { Text("${MapApp.from(placeApp)?.label ?: "지도"} 장소 링크가 연결됐어요") } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { choosingMap = true }) { Text("지도에서 찾기") }
                        if (place != null) {
                            TextButton(onClick = { MapLinks.open(context, place) }) {
                                Text("${(place.app ?: MapApp.NAVER).label}에서 보기")
                            }
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

    if (choosingMap) {
        MapChooserDialog(
            onDismiss = { choosingMap = false },
            onChoose = { app ->
                choosingMap = false
                MapLinks.startSearch(context, app, placeName.ifBlank { title }.trim())
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

    when (picker) {
        "start" -> StartDatePickerDialog(
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
        "end" -> StartDatePickerDialog(
            initial = end,
            title = "끝나는 날",
            onDismiss = { picker = null },
            onConfirm = {
                endDate = maxOf(it, start).toString()
                picker = null
            },
        )
        "time" -> TimePickerDialog(
            initial = localTime ?: LocalTime.of(19, 0),
            onDismiss = { picker = null },
            onConfirm = {
                time = it.toString()
                picker = null
            },
        )
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
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시간") },
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

/** 어느 지도 앱에서 장소를 찾을지 (기본값: 네이버 지도) */
@Composable
private fun MapChooserDialog(onDismiss: () -> Unit, onChoose: (MapApp) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(MapApp.NAVER.key) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어느 지도에서 찾을까요?") },
        text = {
            Column {
                MapApp.entries.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == app.key, onClick = { selected = app.key })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == app.key, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(if (app == MapApp.NAVER) "${app.label} (기본)" else app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "지도 앱에서 장소를 고른 뒤 공유 → 우리둘 을 누르면 장소가 채워져요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(MapApp.from(selected) ?: MapApp.NAVER) }) { Text("찾기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

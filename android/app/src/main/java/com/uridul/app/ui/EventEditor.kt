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
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var date by rememberSaveable { mutableStateOf(initial.date.toString()) }
    var endDate by rememberSaveable { mutableStateOf(initial.endDate.toString()) }
    var time by rememberSaveable { mutableStateOf(initial.time?.toString()) }
    var memo by rememberSaveable { mutableStateOf(initial.memo) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) } // "start" | "end" | "time"
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val start = LocalDate.parse(date)
    val end = LocalDate.parse(endDate)
    val localTime = time?.let(LocalTime::parse)

    Dialog(
        onDismissRequest = onDismiss,
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
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        if (initial.id == null) "일정 추가" else "일정 수정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
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
                        TextButton(onClick = onDismiss) { Text("취소") }
                        Button(
                            onClick = {
                                val draft = EventDraft(initial.id, title, start, end, localTime, memo)
                                if (onSave(draft)) onDismiss()
                            },
                        ) { Text("저장") }
                    }
                }
            }
        }
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

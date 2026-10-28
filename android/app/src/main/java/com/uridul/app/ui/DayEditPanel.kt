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

// 하루 페이지 아래 편집 칸 (하루 종일 / 시간 표 / 반복 / 알림). DayTimelinePage 에서 쓴다.


/**
 * 아래 편집 칸: 하루 종일 / 타임라인 별로 관리
 *  - 하루 종일 ON: 시간은 쓰지 않고 장소 하나만 고른다
 *  - 하루 종일 OFF: [+ 일정 추가] 를 누를 때마다 시간 표가 하나씩 생긴다
 *      ┌──────────────┬──────────┐
 *      │ 시작 [시간 ▾] │          │
 *      ├──────────────┤  장소    │  ← 두 줄을 합친 칸, 가운데, 하나만
 *      │ 끝   [시간 ▾] │          │
 *      └──────────────┴──────────┘
 *    표 밑에 [+ 일정 추가] 버튼이 다시 생긴다
 */
@Composable
internal fun EditPanel(
    edit: SlotEdit,
    error: String?,
    onChange: (SlotEdit) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onFindPlace: (slot: Int) -> Unit,
    onOpenPlace: (Place) -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
    maxHeight: Dp,
) {
    val d = edit.draft
    val allDay = edit.slot < 0
    val multiDay = d.endDate.isAfter(d.date)
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 손잡이 + 머리글
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (edit.isNew) "새 일정 ✨" else "일정 고치기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
                Button(onClick = onSave) { Text("저장") }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = d.title,
                onValueChange = { onChange(edit.copy(draft = d.copy(title = it.take(CalendarEvent.TITLE_MAX)))) },
                label = { Text("일정 제목") },
                placeholder = { Text("예) 저녁 데이트") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하루 종일", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { onChange(edit.toggleAllDay(it)) })
            }

            if (allDay) {
                // 하루 종일: 시간 없이 장소 하나
                SlotTable(
                    startLabel = "시작", endLabel = "끝",
                    start = null, end = null, enabled = false,
                    place = d.place,
                    onStart = {}, onEnd = {},
                    onFindPlace = { onFindPlace(-1) },
                    onOpenPlace = onOpenPlace,
                    onRemovePlace = { onChange(edit.copy(draft = d.copy(place = null))) },
                    onRemoveTable = null,
                )
            } else {
                d.timeline.forEachIndexed { i, slot ->
                    SlotTable(
                        startLabel = if (multiDay) "시작(첫날)" else "시작",
                        endLabel = if (multiDay) "끝(마지막 날)" else "끝",
                        start = slot.start,
                        end = slot.end ?: slot.start.plusMinutes(60),
                        enabled = true,
                        place = slot.place,
                        onStart = { t -> onChange(edit.copy(slot = i).moveStart(t)) },
                        onEnd = { t -> onChange(edit.copy(slot = i).setEnd(t)) },
                        onFindPlace = { onFindPlace(i) },
                        onOpenPlace = onOpenPlace,
                        onRemovePlace = { onChange(edit.copy(slot = i).updateSlot { it.copy(place = null) }) },
                        onRemoveTable = if (d.timeline.size > 1) ({ onChange(edit.copy(slot = i).removeSlot()) }) else null,
                    )
                }
                // 표 밑에 다시 [+ 일정 추가] (여러 날 일정은 시작~끝 하나만)
                if (!multiDay && d.timeline.size < TimeSlot.MAX_SLOTS) {
                    OutlinedButton(onClick = { onChange(edit.addSlot()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ 일정 추가")
                    }
                }
            }

            RepeatRow(
                repeat = d.repeat,
                date = d.date,
                seriesStart = d.seriesStart,
                onPick = { onChange(edit.copy(draft = d.copy(repeat = it))) },
            )
            if (edit.original?.repeat != null) {
                Text(
                    "🔁 반복 일정이에요. 여기서 고치면 모든 날에 함께 바뀌어요 (이 날만 없애려면 [일정 삭제] → 이 날만 빼기).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RemindRow(
                remind = d.remind,
                allDay = allDay,
                onPick = { onChange(edit.copy(draft = d.copy(remind = it))) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!edit.isNew) {
                    TextButton(onClick = onDelete) { Text("일정 삭제", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onMore) { Text("날짜·메모 자세히") }
            }
        }
    }
}

/**
 * 시간 표 하나 (테두리 선만): 왼쪽 두 줄 = 시작·끝 시간 고르기(같은 너비), 오른쪽 = 두 줄을 합친 장소 칸(가운데, 하나만)
 */
@Composable
private fun SlotTable(
    startLabel: String,
    endLabel: String,
    start: LocalTime?,
    end: LocalTime?,
    enabled: Boolean,
    place: Place?,
    onStart: (LocalTime) -> Unit,
    onEnd: (LocalTime) -> Unit,
    onFindPlace: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onRemovePlace: () -> Unit,
    onRemoveTable: (() -> Unit)?,
) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val shape = RoundedCornerShape(14.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .border(1.dp, line, shape)
                .clip(shape),
        ) {
            // 시작 / 끝 (같은 너비의 시간 고르기 칸)
            Column(Modifier.weight(1.15f)) {
                TimeCell(startLabel, start, enabled, onStart)
                Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                TimeCell(endLabel, end, enabled, onEnd)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(line))
            // 장소: 두 줄을 합친 칸, 가운데 정렬, 하나만
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("장소", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    PlaceTag(place = place, onFind = onFindPlace, onOpen = onOpenPlace, onRemove = onRemovePlace)
                }
            }
        }
        if (onRemoveTable != null) {
            // 표 지우기 (오른쪽 위 작은 ✕)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, line, CircleShape)
                    .clickable(onClick = onRemoveTable)
                    .semantics { contentDescription = "이 시간 표 지우기" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 시간 고르기 칸: [라벨  오후 7:00 ▾] → 누르면 30분 단위 목록(선택 상자) */
@Composable
private fun TimeCell(label: String, time: LocalTime?, enabled: Boolean, onPick: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = remember(time) { timeOptions(time) }
    val alpha = if (enabled) 1f else 0.38f
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = "$label ${time?.format(KoreanTime) ?: "없음"}, 눌러서 바꾸기" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.width(56.dp),
                maxLines = 1,
            )
            Text(
                time?.format(KoreanTime) ?: "--:--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        val itemHeight = with(LocalDensity.current) { 48.dp.toPx() }
        val selectedIndex = time?.let { options.indexOf(it) }?.coerceAtLeast(0) ?: 0
        val menuScroll = rememberScrollState((selectedIndex * itemHeight - itemHeight * 2).toInt().coerceAtLeast(0))
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            scrollState = menuScroll,
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            options.forEach { t ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            t.format(KoreanTime),
                            fontWeight = if (t == time) FontWeight.Bold else FontWeight.Normal,
                            color = if (t == time) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onPick(t)
                    },
                )
            }
        }
    }
}

/** 반복 고르기: [🔁 반복   매주 수요일 ▾] */
@Composable
private fun RepeatRow(repeat: Repeat?, date: LocalDate, seriesStart: LocalDate?, onPick: (Repeat?) -> Unit) {
    // 반복은 첫 날 기준으로 저장되므로 이름도 첫 날 기준 (11/1 에서 고쳐도 첫 날이 10/18 이면 "매달 18일")
    val anchor = seriesStart ?: date
    val noneLabel = if (seriesStart != null && seriesStart != date) "반복 안 함 (첫 날 ${seriesStart.monthValue}월 ${seriesStart.dayOfMonth}일만 남아요)" else "반복 안 함"
    ChoiceRow(
        icon = "🔁",
        label = "반복",
        valueText = repeat?.label(anchor) ?: "반복 안 함",
        active = repeat != null,
        options = (listOf<Repeat?>(null) + Repeat.entries).map { it to (it?.label(anchor) ?: noneLabel) },
        selected = repeat,
        onPick = onPick,
    )
}

/** 알림 시간 고르기: [🔔 알림   30분 전 ▾] */
@Composable
private fun RemindRow(remind: Int?, allDay: Boolean, onPick: (Int) -> Unit) {
    val current = Remind.effective(remind, allDay)
    ChoiceRow(
        icon = if (current == Remind.OFF) "🔕" else "🔔",
        label = "알림",
        valueText = Remind.label(remind, allDay),
        active = current != Remind.OFF,
        options = (if (allDay) Remind.allDayOptions else Remind.timedOptions).map { it.minutes to it.label },
        selected = current,
        onPick = onPick,
    )
}

/** 고르기 칸 하나 (테두리만): [아이콘 라벨        고른 값 ▾] → 누르면 목록 */
@Composable
private fun <T> ChoiceRow(
    icon: String,
    label: String,
    valueText: String,
    active: Boolean,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val shape = RoundedCornerShape(14.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, line, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = "$label $valueText, 눌러서 바꾸기" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("  ▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onPick(value)
                    },
                )
            }
        }
    }
}

/** 선택 상자 목록: 0시~23시 30분 단위 + 23:59, 지금 값이 목록에 없으면(예: 20:20) 끼워 넣는다 */
private fun timeOptions(current: LocalTime?): List<LocalTime> {
    val base = (0 until 48).map { LocalTime.of(it / 2, (it % 2) * 30) } + LocalTime.of(23, 59)
    return if (current == null || current in base) base else (base + current).sorted()
}

package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import java.time.LocalDate
import java.time.YearMonth

// 한 달 달력의 손짓(누르기·길게 누르기·끌어서 기간 고르기)과 년·월 고르기 창. MonthCalendar 에서 쓴다.

/** 년·월 고르기: 위에서 해를 넘기고, 12달 중 하나를 누른다 */
@Composable
internal fun MonthPickerDialog(initial: YearMonth, today: LocalDate, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(initial.year) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 달력이 넘길 수 있는 범위(앞뒤 99년) 안에서만
                IconButton(onClick = { year-- }, enabled = year > today.year - 99) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 해") }
                Text("${year}년", textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { year++ }, enabled = year < today.year + 99) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 해") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mo ->
                            val ym = YearMonth.of(year, mo)
                            val isShown = ym == initial
                            val isNow = ym == YearMonth.from(today)
                            val scheme = MaterialTheme.colorScheme
                            Text(
                                "${mo}월",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isShown || isNow) FontWeight.Bold else FontWeight.Normal,
                                color = if (isShown) scheme.onPrimary else if (isNow) scheme.primary else scheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isShown) scheme.primary else scheme.surfaceContainerHigh)
                                    .clickable { onPick(ym) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(YearMonth.from(today)) }) { Text("이번 달") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 길게 누르기 전 손짓 결과 */
private enum class EarlyEnd { TAP, CANCEL }

/**
 * 달력 칸 손짓 (안드로이드 기본 캘린더와 같게):
 *  - 한 번 누르기: 그 날 선택 (선택된 날을 다시 누르면 새 일정)
 *  - 길게 누르기: 그 날 새 일정
 *  - 길게 누른 채 옆 날짜로 끌기: 끈 기간으로 여러 날 새 일정
 * 길게 누르기 전에 손가락이 움직이면 손짓을 놓아서 달 넘기기·위아래 스크롤이 그대로 된다.
 */
internal fun Modifier.dayGestures(
    grid: List<LocalDate>,
    rows: Int,
    cellWidthPx: Float,
    rowHeightPx: Float,
    onTap: () -> ((LocalDate) -> Unit),
    onLongPress: () -> ((LocalDate) -> Unit),
    onRange: () -> ((LocalDate, LocalDate) -> Unit),
    onDragging: (ClosedRange<LocalDate>?) -> Unit,
    onHaptic: () -> Unit,
): Modifier = pointerInput(grid, rows, cellWidthPx, rowHeightPx) {
    fun dayAt(p: androidx.compose.ui.geometry.Offset): LocalDate? {
        if (p.x < 0f || p.y < 0f) return null
        val row = (p.y / rowHeightPx).toInt()
        if (row >= rows) return null
        val col = (p.x / cellWidthPx).toInt().coerceIn(0, 6)
        return grid.getOrNull(row * 7 + col)
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = dayAt(down.position) ?: return@awaitEachGesture
        // null = 길게 누르기 시간이 지남
        val early: EarlyEnd? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: EarlyEnd? = null
            while (result == null) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                result = when {
                    ch == null -> EarlyEnd.CANCEL
                    ch.changedToUp() -> if (ch.isConsumed) EarlyEnd.CANCEL else EarlyEnd.TAP
                    ch.isConsumed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop -> EarlyEnd.CANCEL
                    else -> null
                }
            }
            result
        }
        if (early != null) {
            if (early == EarlyEnd.TAP) onTap()(start)
            return@awaitEachGesture
        }
        onHaptic()
        var end = start
        var finished = false
        onDragging(start..start)
        try {
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                if (!ch.pressed) {
                    finished = true
                    break
                }
                val d = dayAt(ch.position) ?: continue
                // 한 일정은 최대 31일
                val limited = when {
                    d.isAfter(start.plusDays(CalendarEvent.MAX_SPAN_DAYS - 1)) -> start.plusDays(CalendarEvent.MAX_SPAN_DAYS - 1)
                    d.isBefore(start.minusDays(CalendarEvent.MAX_SPAN_DAYS - 1)) -> start.minusDays(CalendarEvent.MAX_SPAN_DAYS - 1)
                    else -> d
                }
                if (limited != end) {
                    end = limited
                    onDragging(minOf(start, end)..maxOf(start, end))
                }
            }
        } finally {
            onDragging(null)
        }
        if (finished) {
            if (end == start) onLongPress()(start) else onRange()(minOf(start, end), maxOf(start, end))
        }
    }
}

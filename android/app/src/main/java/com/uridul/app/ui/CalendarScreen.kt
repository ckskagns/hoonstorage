package com.uridul.app.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.domain.CalendarMath
import com.uridul.app.data.birthdays
import com.uridul.app.domain.SpecialDay
import com.uridul.app.domain.SpecialDays
import java.time.LocalDate
import java.time.YearMonth

/** 그 날 일정 중 가장 최근에 등록한 것 (등록 시각이 같거나 모르면 나중 일정) */
internal fun latestRegistered(events: List<CalendarEvent>, day: LocalDate): CalendarEvent? =
    CalendarMath.eventsOn(events, day).withIndex()
        .maxWithOrNull(compareBy<IndexedValue<CalendarEvent>> { it.value.createdAtMillis }.thenBy { it.index })
        ?.value

/**
 * 공유 캘린더.
 *  - 폰 / 폴드 커버(600dp 미만): 위 달력, 아래 선택한 날 일정
 *  - 폴드 펼침 / 가로(600dp 이상): 왼쪽 달력, 오른쪽 선택한 날 일정
 */
@Composable
fun CalendarContent(
    couple: Couple,
    myUid: String,
    month: YearMonth,
    events: List<CalendarEvent>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** 길게 누른 채 여러 날을 끌었을 때: 그 기간으로 새 일정 */
    onAddRange: (LocalDate, LocalDate) -> Unit = { s, _ -> onAdd(s) },
    /** 상세 보기에서 색 바꾸기 */
    onSave: (com.uridul.app.data.EventDraft) -> Boolean = { false },
    onDelete: (String) -> Unit = {},
    onSkip: (String, LocalDate) -> Unit = { _, _ -> },
) {
    // 상세 보기 중인 일정 (ID). 일정이 바뀌면(색 등) 새 내용으로 다시 보여준다
    var detailId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val today = rememberToday()
    val grid = remember(month) { CalendarMath.monthGrid(month) }
    val birthdays = remember(couple) { couple.birthdays() }
    val specialsFor: (List<LocalDate>) -> Map<LocalDate, List<SpecialDay>> = remember(couple.startDate, couple.countFromOne, birthdays) {
        { g -> SpecialDays.between(couple.startDate, couple.countFromOne, birthdays, g.first(), g.last()) }
    }
    val specials = remember(specialsFor, grid) { specialsFor(grid) }
    val selectedEvents = remember(events, selected) { CalendarMath.eventsOn(events, selected) }
    // 일정을 누르면 기본 캘린더처럼 먼저 상세 보기
    val openDetail: (CalendarEvent) -> Unit = { detailId = it.id }
    // 날짜 누르기: 일정이 있는 날이면 그 날 가장 최근에 등록한 일정의 상세 보기부터 (바로 수정할 수 있게).
    // 일정이 없는 날은 선택만, 선택된 빈 날을 다시 누르면 새 일정
    val tapDay: (LocalDate) -> Unit = { day ->
        val latest = latestRegistered(events, day)
        when {
            latest != null -> { onSelect(day); detailId = latest.id }
            day == selected -> onAdd(day)
            else -> onSelect(day)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneMinWidth
        if (twoPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MonthCalendar(
                    month, events, specialsFor, selected, today, myUid, onSelect, onAdd, onMonthChange,
                    fillHeight = true,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                    onAddRange = onAddRange,
                    onTapDay = tapDay,
                )
                DayPanel(
                    selected, selectedEvents, specials[selected].orEmpty(), couple, myUid, onAdd, openDetail,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val m = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                MonthCalendar(
                    month, events, specialsFor, selected, today, myUid, onSelect, onAdd, onMonthChange,
                    fillHeight = false,
                    modifier = m,
                    onAddRange = onAddRange,
                    onTapDay = tapDay,
                )
                DayPanel(selected, selectedEvents, specials[selected].orEmpty(), couple, myUid, onAdd, openDetail, m)
                // 아래 버튼(일정 추가)에 마지막 일정이 가려지지 않게
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    // 상세 보기: 고른 날의 그 일정 (지워지거나 다른 날로 옮겨지면 닫힌다)
    detailId?.let { id ->
        val event = selectedEvents.firstOrNull { it.id == id }
        if (event == null) {
            LaunchedEffect(id) { detailId = null }
        } else {
            EventDetailSheet(
                event = event,
                couple = couple,
                myUid = myUid,
                onDismiss = { detailId = null },
                onEdit = { e -> detailId = null; onEdit(e) },
                onSave = onSave,
                onDelete = onDelete,
                onSkip = onSkip,
            )
        }
    }
}


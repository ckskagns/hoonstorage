package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.domain.CalendarMath
import com.uridul.app.data.birthdays
import com.uridul.app.domain.SpecialDay
import com.uridul.app.domain.SpecialDays
import com.uridul.app.domain.SpecialKind
import com.uridul.app.ui.theme.CoupleColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

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
) {
    val today = rememberToday()
    val grid = remember(month) { CalendarMath.monthGrid(month) }
    val birthdays = remember(couple) { couple.birthdays() }
    val specialsFor: (List<LocalDate>) -> Map<LocalDate, List<SpecialDay>> = remember(couple.startDate, couple.countFromOne, birthdays) {
        { g -> SpecialDays.between(couple.startDate, couple.countFromOne, birthdays, g.first(), g.last()) }
    }
    val specials = remember(specialsFor, grid) { specialsFor(grid) }
    val selectedEvents = remember(events, selected) { CalendarMath.eventsOn(events, selected) }

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
                )
                DayPanel(
                    selected, selectedEvents, specials[selected].orEmpty(), couple, myUid, onAdd, onEdit,
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
                )
                DayPanel(selected, selectedEvents, specials[selected].orEmpty(), couple, myUid, onAdd, onEdit, m)
                // 아래 버튼(일정 추가)에 마지막 일정이 가려지지 않게
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}


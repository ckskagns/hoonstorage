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

// 공유 캘린더 아래(펼치면 오른쪽) 선택한 날 목록: 기념일 카드, 일정 카드, 타임라인 줄. CalendarContent 에서 쓴다.


@Composable
internal fun DayPanel(
    day: LocalDate,
    events: List<CalendarEvent>,
    specials: List<SpecialDay>,
    couple: Couple,
    myUid: String,
    onAdd: (LocalDate) -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            day.format(KoreanMonthDay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        specials.forEach { SpecialDayCard(it) }
        if (events.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CharacterPair(couple.myCharacter(myUid), couple.partnerCharacter(myUid), size = 48.dp)
                    Text(
                        "등록된 일정이 없어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onAdd(day) }) { Text("이 날에 일정 추가") }
                }
            }
        } else {
            events.forEach { EventCard(it, couple, myUid, onEdit) }
        }
    }
}

/**
 * 선택한 날의 일정 카드.
 *  - 위: 제목, 날짜(여러 날이면 기간), 누가 등록했는지
 *  - 아래: 하루 종일 한 줄 또는 타임라인(시작~끝·내용) 여러 줄, 각 줄 오른쪽에 장소 태그 / [장소 찾기]
 * 카드를 누르면 수정, 장소 태그를 누르면 그 지도 앱에서 바로 열림.
 */
@Composable
private fun EventCard(event: CalendarEvent, couple: Couple, myUid: String, onEdit: (CalendarEvent) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mine = event.createdBy == myUid
    val color = if (mine) CoupleColors.mine else CoupleColors.partner
    val who = if (mine) couple.myName(myUid) else (couple.names[event.createdBy] ?: "상대")
    /** 장소를 찾을 줄 (-1: 하루 종일 줄), null 이면 지도 고르기 창 닫힘 */
    var findingSlot by remember { mutableStateOf<Int?>(null) }
    val press = remember { MutableInteractionSource() }

    Card(
        onClick = { onEdit(event) },
        interactionSource = press,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(press),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (event.isMultiDay) {
                        Text(
                            "${event.date.format(KoreanMonthDay)} ~ ${event.endDate.format(KoreanMonthDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    event.repeat?.let { r ->
                        Text(
                            "🔁 ${r.label(event.seriesStart ?: event.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // 누가 등록했는지: 캐릭터 얼굴 + 이름
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CharacterAvatar(couple.characterOf(event.createdBy), 30.dp, contentDescription = "$who 등록")
                    Text(
                        who,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        maxLines = 1,
                        modifier = Modifier
                            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            // 타임라인 (하루 종일이면 한 줄)
            Column(
                Modifier.padding(start = 16.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (event.allDay) {
                    TimelineLine(
                        timeText = "하루 종일",
                        title = "",
                        place = event.place,
                        onFind = { findingSlot = -1 },
                        onOpen = { MapLinks.open(context, it) },
                    )
                } else {
                    event.slots.forEachIndexed { i, slot ->
                        TimelineLine(
                            timeText = slotTimeText(event, slot),
                            title = slot.title,
                            place = slot.place,
                            onFind = { findingSlot = i },
                            onOpen = { MapLinks.open(context, it) },
                        )
                    }
                }
            }

            if (event.memo.isNotBlank()) {
                Text(
                    event.memo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
        }
    }

    findingSlot?.let { slot ->
        MapChooserDialog(
            onDismiss = { findingSlot = null },
            onChoose = { app ->
                findingSlot = null
                // 지도 앱에서 공유하면 이 일정·이 줄에 바로 저장된다
                PlaceInbox.expectEvent(event.id, slot)
                val query = event.slots.getOrNull(slot)?.title?.takeIf { it.isNotBlank() } ?: event.title
                MapLinks.startSearch(context, app, query)
            },
        )
    }
}

/** 타임라인 한 줄: 시간(+내용) 왼쪽, 장소 태그 / [장소 찾기] 오른쪽 */
@Composable
private fun TimelineLine(
    timeText: String,
    title: String,
    place: com.uridul.app.data.Place?,
    onFind: () -> Unit,
    onOpen: (com.uridul.app.data.Place) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 타임라인 점 대신 작은 하트
        Text("♥", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                timeText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        PlaceTag(place = place, onFind = onFind, onOpen = onOpen)
    }
}

/** "오후 6:30 ~ 오후 8:00", 여러 날 일정이면 "8.7 오후 4:00 ~ 8.14 오후 5:00" */
private fun slotTimeText(event: CalendarEvent, slot: com.uridul.app.data.TimeSlot): String {
    val startText = slot.start.format(KoreanTime)
    val endText = slot.end?.format(KoreanTime)
    return if (event.isMultiDay) {
        val from = "${event.date.monthValue}.${event.date.dayOfMonth} $startText"
        if (endText == null) from else "$from ~ ${event.endDate.monthValue}.${event.endDate.dayOfMonth} $endText"
    } else {
        if (endText == null) startText else "$startText ~ $endText"
    }
}


/** 선택한 날의 기념일 한 줄: 우리 기념일·생일은 분홍 바탕, 연인 기념일은 연한 바탕 */
@Composable
private fun SpecialDayCard(special: SpecialDay) {
    val personal = special.kind.personal
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (personal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(special.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    special.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        personal -> MaterialTheme.colorScheme.onPrimaryContainer
                        special.kind == SpecialKind.HOLIDAY -> CoupleColors.sunday
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (special.note.isNotBlank()) {
                    Text(
                        special.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

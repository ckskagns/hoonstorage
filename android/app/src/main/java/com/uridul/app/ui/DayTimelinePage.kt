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

/**
 * 하루 페이지: 하루 일정을 한 페이지에서 모두 관리한다.
 *  - 위: 날짜·기념일, 하루 종일 / 여러 날 일정
 *  - 가운데: 0시~24시 타임라인 표 (세로 스크롤). 시간대마다 블록, 겹치면 나란히, 오늘이면 지금 시각 선
 *  - 블록을 누르면 아래 편집 칸이 열린다 (페이지 이동 없이 시간 ±, 내용, 장소, 시간대 추가·삭제)
 *  - 빈 시간을 누르면 그 시각에 새 일정
 */
@Composable
fun DayTimelinePage(
    date: LocalDate,
    events: List<CalendarEvent>,
    couple: Couple,
    myUid: String,
    specials: List<SpecialDay>,
    /** 이 일정을 열어 둔 채로 시작 */
    focusEventId: String?,
    /** 새 일정 입력 칸을 연 채로 시작 */
    startNew: Boolean,
    onClose: () -> Unit,
    onSave: (EventDraft) -> Boolean,
    onDelete: (String) -> Unit,
    /** 날짜·메모 등 자세히 고치기 (기존 입력 창) */
    onOpenFullEditor: (EventDraft) -> Unit,
    incomingPlace: Place?,
    onIncomingPlaceConsumed: () -> Unit,
    onAttachPlace: (eventId: String, slot: Int, place: Place) -> Unit,
) {
    val context = LocalContext.current
    val today = rememberToday()
    val dayEvents = remember(events, date) { events.filter { it.occursOn(date) } }

    // 편집 중인 내용 (폴드 접고 펴기·화면 회전에도 남도록 글자로 저장)
    var editJson by rememberSaveable {
        mutableStateOf(
            when {
                startNew -> EditCodec.encode(newEdit(date, today))
                focusEventId != null -> dayEvents.firstOrNull { it.id == focusEventId }?.let { EditCodec.encode(editOf(it)) }.orEmpty()
                else -> ""
            },
        )
    }
    val edit = remember(editJson) { EditCodec.decode(editJson) }
    fun setEdit(e: SlotEdit?) {
        editJson = e?.let(EditCodec::encode).orEmpty()
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingMap by remember { mutableStateOf(false) }

    /** 바꾼 내용이 있으면 한 번 묻고 진행 */
    fun guard(action: () -> Unit) {
        val e = edit
        if (e != null && e.draft != e.original) confirmDiscard = action else action()
    }

    val closePage: () -> Unit = { guard { setEdit(null); onClose() } }

    // 지도 앱에서 공유받은 장소: 편집 중이면 그 줄에, 아니면 카드에서 시작한 일정에 바로 저장
    LaunchedEffect(incomingPlace) {
        val p = incomingPlace ?: return@LaunchedEffect
        val e = edit
        if (e != null) {
            setEdit(e.withPlace(p))
        } else {
            PlaceInbox.eventTarget?.let { onAttachPlace(it.eventId, it.slot, p) }
        }
        onIncomingPlaceConsumed()
    }

    // 편집 중인 일정은 저장 전에도 표에 바로 보이게 (미리보기)
    val previewEvents = remember(dayEvents, edit) {
        val e = edit ?: return@remember dayEvents
        val preview = e.draft.toPreview(e.original?.let { o -> dayEvents.firstOrNull { it.id == o.id }?.createdBy } ?: myUid)
        dayEvents.filter { it.id != preview.id } + preview
    }
    val allDay = remember(previewEvents, date) { DayTimeline.allDayEvents(previewEvents, date) }
    val placed = remember(previewEvents, date) { DayTimeline.layout(DayTimeline.blocks(previewEvents, date)) }
    val editingKey = edit?.let { "${it.draft.id ?: NEW_ID}#${it.slot}" }

    Dialog(
        onDismissRequest = { if (edit != null) guard { setEdit(null) } else closePage() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 3 } + fadeIn(),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    // 머리글: ← 10월 27일 (화)  · 기념일
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = closePage) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "달력으로") }
                        Column(Modifier.weight(1f)) {
                            Text(date.format(KoreanMonthDay), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            val sub = buildList {
                                if (date == today) add("오늘")
                                specials.forEach { add("${it.emoji} ${it.title}") }
                            }.joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        FilledTonalButton(onClick = { guard { error = null; setEdit(newEdit(date, today, allDay = true)) } }) { Text("+ 하루 종일") }
                    }

                    // 하루 종일 / 여러 날 일정
                    if (allDay.isNotEmpty()) {
                        AllDayRow(allDay, myUid, edit?.draft?.id ?: if (edit?.isNew == true && edit.slot < 0) NEW_ID else null) { e ->
                            guard { error = null; setEdit(editOf(e)) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp))

                    // 타임라인 표 (세로 스크롤)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        TimelineTable(
                            date = date,
                            today = today,
                            placed = placed,
                            myUid = myUid,
                            editingKey = editingKey,
                            scrollToMinute = edit?.let { e -> e.draft.timeline.getOrNull(e.slot)?.start?.toSecondOfDay()?.div(60) }
                                ?: placed.minOfOrNull { it.block.startMin }
                                ?: if (date == today) java.time.LocalTime.now().toSecondOfDay() / 60 else 9 * 60,
                            onBlock = { b -> guard { error = null; setEdit(editOf(b.event, b.slotIndex)) } },
                            onEmpty = { minute -> guard { error = null; setEdit(newEdit(date, today, at = minute)) } },
                            onOpenPlace = { MapLinks.open(context, it) },
                        )
                        if (placed.isEmpty() && edit == null) {
                            Text(
                                "빈 시간을 누르면 그 시간에 일정이 생겨요 💕",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // 아래 편집 칸 (페이지 이동 없이 여기서 모두 고친다)
                    AnimatedVisibility(
                        visible = edit != null,
                        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        val e = edit
                        if (e != null) {
                            EditPanel(
                                edit = e,
                                error = error,
                                onChange = { error = null; setEdit(it) },
                                onClose = { guard { error = null; setEdit(null) } },
                                onSave = {
                                    val draft = e.finalDraft()
                                    val problem = CalendarMath.validate(draft)
                                    if (problem != null) {
                                        error = problem
                                    } else if (onSave(draft)) {
                                        error = null
                                        setEdit(null)
                                    }
                                },
                                onFindPlace = { choosingMap = true },
                                onOpenPlace = { MapLinks.open(context, it) },
                                onDelete = { confirmDelete = true },
                                onMore = { onOpenFullEditor(e.finalDraft()); setEdit(null) },
                            )
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
                val e = edit ?: return@MapChooserDialog
                PlaceInbox.expectEditor()
                val q = e.draft.timeline.getOrNull(e.slot)?.title?.takeIf { it.isNotBlank() } ?: e.draft.title
                MapLinks.startSearch(context, app, q.trim())
            },
        )
    }
    confirmDiscard?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("저장하지 않은 내용이 있어요") },
            text = { Text("고친 내용을 버리고 넘어갈까요?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = null; action() }) { Text("버리기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("계속 고치기") } },
        )
    }
    if (confirmDelete) {
        val e = edit
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("일정을 삭제할까요?") },
            text = { Text("\"${e?.draft?.title.orEmpty()}\" 일정 전체가 상대 폰에서도 함께 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    e?.draft?.id?.let(onDelete)
                    setEdit(null)
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

private const val NEW_ID = "__new__"
private val HourHeight = 60.dp
private val LabelWidth = 52.dp

/** 하루 종일·여러 날 일정 줄 (칩) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllDayRow(events: List<CalendarEvent>, myUid: String, editingId: String?, onClick: (CalendarEvent) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        events.forEach { e ->
            val mine = e.createdBy == myUid
            val fill = if (mine) CoupleColors.mineFill else CoupleColors.partnerFill
            val strong = if (mine) CoupleColors.mine else CoupleColors.partner
            val label = buildString {
                append(e.title)
                if (e.isMultiDay) append(" (${e.date.monthValue}.${e.date.dayOfMonth}~${e.endDate.monthValue}.${e.endDate.dayOfMonth})")
                (e.place ?: e.slots.firstOrNull()?.place)?.let { append("  📍${it.name}") }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = CoupleColors.onFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .then(if (e.id == editingId) Modifier.border(2.dp, strong, RoundedCornerShape(50)) else Modifier)
                    .clickable { onClick(e) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 0시~24시 표: 시간 눈금 + 블록 + 지금 시각 선 */
@Composable
private fun TimelineTable(
    date: LocalDate,
    today: LocalDate,
    placed: List<DayTimeline.Placed>,
    myUid: String,
    editingKey: String?,
    scrollToMinute: Int,
    onBlock: (DayTimeline.Block) -> Unit,
    onEmpty: (minute: Int) -> Unit,
    onOpenPlace: (Place) -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pxPerMinute = with(density) { HourHeight.toPx() } / 60f
    // 처음 열 때(그리고 편집할 줄이 바뀔 때) 그 시각 조금 위로 스크롤
    LaunchedEffect(scrollToMinute) {
        scroll.animateScrollTo(((scrollToMinute - 60).coerceAtLeast(0) * pxPerMinute).toInt())
    }
    val dpPerMinute = HourHeight / 60

    Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
        // 시간 눈금
        Column(Modifier.width(LabelWidth)) {
            for (h in 0 until 24) {
                Box(Modifier.height(HourHeight).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    Text(
                        hourLabel(h),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp, top = 2.dp),
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).height(HourHeight * 24).padding(end = 10.dp)) {
            val width = maxWidth
            // 빈 시간: 30분 칸마다 누르면 그 시각에 새 일정
            Column(Modifier.fillMaxSize()) {
                for (half in 0 until 48) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(HourHeight / 2)
                            .clickable { onEmpty(half * 30) }
                            .semantics { contentDescription = "${hourLabel(half / 2)} ${if (half % 2 == 1) "30분" else ""} 빈 시간, 눌러서 일정 추가" },
                    ) {
                        if (half % 2 == 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
            // 블록
            placed.forEach { p ->
                val b = p.block
                val colWidth = width / p.columns
                TimelineBlock(
                    block = b,
                    mine = b.event.createdBy == myUid,
                    selected = b.key == editingKey,
                    modifier = Modifier
                        .offset(x = colWidth * p.column, y = dpPerMinute * b.startMin)
                        .width(colWidth - 2.dp)
                        .height((dpPerMinute * (b.endMin - b.startMin)) - 2.dp),
                    onClick = { onBlock(b) },
                    onOpenPlace = onOpenPlace,
                )
            }
            // 오늘이면 지금 시각 선 (작은 하트 + 선)
            if (date == today) {
                val now = java.time.LocalTime.now().toSecondOfDay() / 60
                Row(Modifier.offset(y = dpPerMinute * now - 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("♥", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

@Composable
private fun TimelineBlock(
    block: DayTimeline.Block,
    mine: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onOpenPlace: (Place) -> Unit,
) {
    val fill = if (mine) CoupleColors.mineFill else CoupleColors.partnerFill
    val strong = if (mine) CoupleColors.mine else CoupleColors.partner
    val press = remember { MutableInteractionSource() }
    val scale = bounceScale(selected, 1.02f)
    val lengthMin = block.endMin - block.startMin
    Row(
        modifier
            .pressBounce(press)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .then(if (selected) Modifier.border(2.dp, strong, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .semantics { contentDescription = "${block.title}, ${minuteText(block.startMin)}부터 ${minuteText(block.endMin)}까지" },
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(strong))
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(
                "${minuteText(block.startMin)} ~ ${minuteText(block.endMin)}",
                style = MaterialTheme.typography.labelSmall,
                color = CoupleColors.onFill.copy(alpha = 0.75f),
                maxLines = 1,
            )
            if (lengthMin >= 40) {
                Text(
                    block.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CoupleColors.onFill,
                    maxLines = if (lengthMin >= 90) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val place = block.place
            if (place != null && lengthMin >= 55) {
                Text(
                    "📍 ${place.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = strong,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenPlace(place) }
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}

/** 아래 편집 칸 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPanel(
    edit: SlotEdit,
    error: String?,
    onChange: (SlotEdit) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onFindPlace: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    val d = edit.draft
    val multiDay = d.endDate.isAfter(d.date)
    val slot = d.timeline.getOrNull(edit.slot)
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
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
                Switch(checked = edit.slot < 0, onCheckedChange = { onChange(edit.toggleAllDay(it)) })
            }

            if (slot == null) {
                // 하루 종일: 장소 하나
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("장소", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    PlaceTag(d.place, onFind = onFindPlace, onOpen = onOpenPlace, onRemove = { onChange(edit.copy(draft = d.copy(place = null))) })
                }
            } else {
                // 이 일정의 시간대들 (여러 개면 골라서 고친다)
                if (d.timeline.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.timeline.forEachIndexed { i, s ->
                            FilterChip(
                                selected = i == edit.slot,
                                onClick = { onChange(edit.copy(slot = i)) },
                                label = { Text(s.start.format(KoreanTime) + (s.title.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""), maxLines = 1) },
                            )
                        }
                    }
                }
                TimeStepper(if (multiDay) "시작 (첫날)" else "시작", slot.start) { onChange(edit.moveStart(it)) }
                TimeStepper(if (multiDay) "끝 (마지막 날)" else "끝", slot.end ?: slot.start.plusMinutes(60)) { onChange(edit.setEnd(it)) }
                // 길이 빠르게 고르기
                if (!multiDay) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30 to "30분", 60 to "1시간", 90 to "1시간 30분", 120 to "2시간", 180 to "3시간").forEach { (m, label) ->
                            val current = slot.end?.let { java.time.Duration.between(slot.start, it).toMinutes().toInt() }
                            FilterChip(
                                selected = current == m,
                                onClick = { onChange(edit.setEnd(clampTime(slot.start.toSecondOfDay() / 60 + m))) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = slot.title,
                    onValueChange = { text -> onChange(edit.updateSlot { it.copy(title = text.take(TimeSlot.TITLE_MAX)) }) },
                    label = { Text("이 시간에 할 일 (선택)") },
                    placeholder = { Text("예) 저녁식사") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("장소", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    PlaceTag(slot.place, onFind = onFindPlace, onOpen = onOpenPlace, onRemove = { onChange(edit.updateSlot { it.copy(place = null) }) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!multiDay && d.timeline.size < TimeSlot.MAX_SLOTS) {
                        OutlinedButton(onClick = { onChange(edit.addSlot()) }) { Text("+ 이 일정에 시간대 추가") }
                    }
                    if (d.timeline.size > 1) {
                        TextButton(onClick = { onChange(edit.removeSlot()) }) { Text("이 시간대 지우기", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

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

/** 시간 고르기: [-1시간] [-15분] 오후 6:30 [+15분] [+1시간] (창 없이 이 자리에서) */
@Composable
private fun TimeStepper(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    val minute = time.toSecondOfDay() / 60
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
        StepButton("−1시간") { onChange(clampTime(minute - 60)) }
        StepButton("−15") { onChange(clampTime(minute - 15)) }
        Text(
            time.format(KoreanTime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+15") { onChange(clampTime(minute + 15)) }
        StepButton("+1시간") { onChange(clampTime(minute + 60)) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ───────── 편집 상태 ─────────

/** 편집 중: 일정 입력값 + 고치는 시간대 번호(-1: 하루 종일) + 새 일정인지 + 원래 값(바뀌었는지 비교) */
private data class SlotEdit(val draft: EventDraft, val slot: Int, val isNew: Boolean, val original: EventDraft?) {

    fun finalDraft(): EventDraft = if (slot < 0) {
        draft.copy(time = null, timeline = emptyList())
    } else {
        val sorted = draft.timeline
        draft.copy(time = sorted.firstOrNull()?.start, place = null)
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

    fun withPlace(p: Place): SlotEdit = if (slot < 0) copy(draft = draft.copy(place = p)) else updateSlot { it.copy(place = p) }
}

private fun editOf(event: CalendarEvent, slot: Int = 0): SlotEdit {
    val d = event.toDraft()
    return SlotEdit(d, if (event.allDay) -1 else slot.coerceIn(0, (d.timeline.size - 1).coerceAtLeast(0)), false, d)
}

private fun newEdit(date: LocalDate, today: LocalDate, at: Int? = null, allDay: Boolean = false): SlotEdit {
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

private fun clampTime(minute: Int): LocalTime = LocalTime.of(0, 0).plusMinutes(minute.coerceIn(0, 23 * 60 + 59).toLong())

private fun hourLabel(h: Int): String = when {
    h == 0 -> "오전 12시"
    h < 12 -> "오전 ${h}시"
    h == 12 -> "오후 12시"
    else -> "오후 ${h - 12}시"
}

private fun minuteText(minute: Int): String = clampTime(minute).format(KoreanTime)

/** 미리보기용: 입력값을 일정처럼 */
private fun EventDraft.toPreview(createdBy: String): CalendarEvent = CalendarEvent(
    id = id ?: NEW_ID,
    title = title.ifBlank { "새 일정" },
    date = date,
    endDate = endDate,
    time = if (timeline.isEmpty()) null else timeline.first().start,
    memo = memo,
    createdBy = createdBy,
    place = place,
    timeline = timeline,
)

/** 편집 상태 저장용 JSON */
private object EditCodec {
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

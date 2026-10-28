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
    /** 방금 지운 일정 (아래에 "되돌리기" 안내) */
    undo: com.uridul.app.data.UndoItem? = null,
    /** 반복 일정에서 이 날만 빼기 (일정 ID, 그 날의 시작일) */
    onSkip: (String, LocalDate) -> Unit = { _, _ -> },
    onUndo: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
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
    // 검색 결과에서 다른 달 일정을 연 경우: 그 달 일정이 불러와지면 그때 그 일정을 연다
    var focusPending by rememberSaveable { mutableStateOf(focusEventId != null && editJson.isEmpty()) }
    LaunchedEffect(dayEvents) {
        if (!focusPending) return@LaunchedEffect
        val target = dayEvents.firstOrNull { it.id == focusEventId } ?: return@LaunchedEffect
        focusPending = false
        if (editJson.isEmpty()) setEdit(editOf(target))
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
        val target = PlaceInbox.eventTarget
        when {
            e != null -> setEdit(e.withPlace(p))
            target != null -> onAttachPlace(target.eventId, target.slot, p)
            // 편집 칸이 닫혀 있으면 그 장소로 새 일정을 연다 (버리지 않는다)
            else -> setEdit(newEdit(date, today).withPlace(p))
        }
        onIncomingPlaceConsumed()
    }

    // 편집 중인 일정은 저장 전에도 표에 바로 보이게 (미리보기)
    val previewEvents = remember(dayEvents, edit) {
        val e = edit ?: return@remember dayEvents
        // 하루 종일로 바꾸면 시간표에서 빠지고 위 칸으로 (저장할 모양 그대로 미리보기)
        val preview = e.finalDraft().toPreview(e.original?.let { o -> dayEvents.firstOrNull { it.id == o.id }?.createdBy } ?: myUid)
        dayEvents.filter { it.id != preview.id } + preview
    }
    val allDay = remember(previewEvents, date) { DayTimeline.allDayEvents(previewEvents, date) }
    val placed = remember(previewEvents, date) { DayTimeline.layout(DayTimeline.blocks(previewEvents, date)) }
    val editingKey = edit?.let { "${it.draft.id ?: NEW_ID}#${it.slot}" }

    Dialog(
        onDismissRequest = { if (edit != null) guard { setEdit(null) } else closePage() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FullScreenDialogBars()
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 3 } + fadeIn(),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
              Box(Modifier.fillMaxSize()) {
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
                                // 장소를 찾을 표를 기억해 두고 지도 고르기 (공유받은 장소가 그 표에 들어간다)
                                onFindPlace = { slot -> setEdit(e.copy(slot = slot)); choosingMap = true },
                                onOpenPlace = { MapLinks.open(context, it) },
                                onDelete = { confirmDelete = true },
                                onMore = { onOpenFullEditor(e.finalDraft()); setEdit(null) },
                                // 편집 칸은 화면의 절반 남짓까지만 (위의 시간표가 보이도록)
                                maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.55f).dp,
                            )
                        }
                    }
                }
                // 방금 지운 일정 되돌리기 (편집 칸이 닫혀 있을 때)
                UndoBar(
                    event = if (edit == null) undo else null,
                    onUndo = onUndo,
                    onDismiss = onDismissUndo,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 16.dp),
                )
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
        // 반복 일정이면 "이 날만 빼기" / "반복 전체 삭제" 중에서 고른다
        val occurrence = e?.original?.takeIf { it.repeat != null }?.let { it.occurrence ?: it.date }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (occurrence != null) "반복 일정을 삭제할까요?" else "일정을 삭제할까요?") },
            text = {
                Text(
                    if (occurrence != null) {
                        "\"${e.draft.title}\" 은(는) ${e.original.repeat?.label(e.original.seriesStart ?: e.original.date)} 반복 일정이에요.\n" +
                            "이 날(${occurrence.monthValue}월 ${occurrence.dayOfMonth}일)만 뺄 수도, 반복 전체를 지울 수도 있어요. 상대 폰에서도 똑같이 바뀌어요."
                    } else {
                        "\"${e?.draft?.title.orEmpty()}\" 일정 전체가 상대 폰에서도 함께 사라져요.\n지운 뒤 바로 [되돌리기]를 누르거나, 메뉴의 '최근 지운 일정'에서 30일 안에 되살릴 수 있어요."
                    },
                )
            },
            confirmButton = {
                Row {
                    if (occurrence != null) {
                        TextButton(onClick = {
                            confirmDelete = false
                            e.draft.id?.let { onSkip(it, occurrence) }
                            setEdit(null)
                        }) { Text("이 날만 빼기") }
                    }
                    TextButton(onClick = {
                        confirmDelete = false
                        e?.draft?.id?.let(onDelete)
                        setEdit(null)
                    }) { Text(if (occurrence != null) "반복 전체 삭제" else "삭제", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

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
                if (e.repeat != null) append("🔁 ")
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
                    (if (block.event.repeat != null) "🔁 " else "") + block.title,
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

private fun hourLabel(h: Int): String = when {
    h == 0 -> "오전 12시"
    h < 12 -> "오전 ${h}시"
    h == 12 -> "오후 12시"
    else -> "오후 ${h - 12}시"
}

private fun minuteText(minute: Int): String = clampTime(minute).format(KoreanTime)

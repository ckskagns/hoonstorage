package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.EventColor
import com.uridul.app.data.Remind
import com.uridul.app.data.toDraft
import com.uridul.app.data.categoryLabel
import com.uridul.app.ui.theme.CoupleColors

/**
 * 일정 상세 보기 (안드로이드 기본 캘린더처럼 일정을 누르면 먼저 내용을 본다).
 *  - 위: 색 막대 + 제목, 날짜·시간, 반복·알림
 *  - 타임라인(시간·내용·장소), 메모 전체, 누가 등록했는지
 *  - 색 바꾸기 (바로 저장), 지난 일정이면 후기·사진첩
 *  - 아래: [삭제] [수정]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: CalendarEvent,
    couple: Couple,
    myUid: String,
    onDismiss: () -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    onSave: (com.uridul.app.data.EventDraft) -> Boolean,
    onDelete: (String) -> Unit,
    onSkip: (String, java.time.LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    val mine = event.createdBy == myUid
    val defaultColor: EventColor = CoupleColors.defaultFor(event.createdBy, myUid)
    val strong = CoupleColors.strongOf(event, myUid)
    val who = if (mine) couple.myName(myUid) else (couple.names[event.createdBy] ?: "상대")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 제목 + 날짜
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).heightIn(min = 48.dp).background(strong, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(dateText(event), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InfoLine("📂", couple.categoryLabel(event.category))
            event.repeat?.let { r ->
                InfoLine("🔁", r.label(event.seriesStart ?: event.date))
            }
            InfoLine(if (event.remindValue == Remind.OFF) "🔕" else "🔔", Remind.label(event.remind, event.allDay))

            // 타임라인 (하루 종일이면 장소 한 줄)
            if (event.allDay) {
                event.place?.let { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        PlaceTag(place = p, onFind = {}, onOpen = { MapLinks.open(context, it) })
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.slots.forEach { slot ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("♥", style = MaterialTheme.typography.labelMedium, color = strong)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(slotTimeText(event, slot), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (slot.title.isNotBlank()) {
                                    Text(slot.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            slot.place?.let { p ->
                                Spacer(Modifier.width(8.dp))
                                PlaceTag(place = p, onFind = {}, onOpen = { MapLinks.open(context, it) })
                            }
                        }
                    }
                }
            }
            if (event.memo.isNotBlank()) {
                InfoLine("📝", event.memo)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CharacterAvatar(couple.characterOf(event.createdBy), 28.dp, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("${who}가 등록했어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            // 색은 여기서 바로 바꿔서 저장한다 (두 사람 폰 모두 같은 색)
            EventColorPicker(
                selected = event.color,
                defaultColor = defaultColor,
                onPick = { c -> if (c != event.color) onSave(event.toDraft().copy(color = c)) },
            )

            // 지난 일정(오늘 포함)이면 후기·사진첩 (후기는 우리둘 일정만)
            if (!event.date.isAfter(rememberToday())) {
                HorizontalDivider()
                if (event.category.together) ReviewStrip(event = event, couple = couple, myUid = myUid)
                AlbumStrip(event = event, couple = couple, myUid = myUid)
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { onEdit(event) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("수정")
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteEventDialog(
            title = event.title,
            repeatLabel = event.repeat?.label(event.seriesStart ?: event.date),
            occurrence = event.date.takeIf { event.repeat != null },
            onSkip = {
                confirmDelete = false
                onSkip(event.id, event.date)
                onDismiss()
            },
            onDelete = {
                confirmDelete = false
                onDelete(event.id)
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun InfoLine(icon: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(icon, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** "11월 2일 (월) · 하루 종일", "11월 2일 (월) ~ 11월 4일 (수)", "11월 2일 (월)" */
private fun dateText(e: CalendarEvent): String = buildString {
    append(e.date.format(KoreanMonthDay))
    if (e.isMultiDay) append(" ~ ${e.endDate.format(KoreanMonthDay)}")
    if (e.allDay) append(" · 하루 종일")
}

package com.uridul.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.DateReview
import com.uridul.app.data.ReviewEntry

/**
 * 데이트 한 줄 후기를 쓰고 보는 곳들(일정 카드·가고 싶은 곳·추억 슬라이드)이 함께 쓰는 값.
 * 메인 화면에서 한 번 넣어 주고, 필요한 곳에서 꺼내 쓴다.
 */
class ReviewHooks(
    /** 일정ID_날짜 → 후기 */
    val reviews: Map<String, DateReview> = emptyMap(),
    val onSave: (event: CalendarEvent, hearts: Int, text: String) -> Unit = { _, _, _ -> },
    val onDelete: (DateReview) -> Unit = {},
) {
    fun of(event: CalendarEvent): DateReview? = reviews[DateReview.idOf(event.id, event.date)]
}

val LocalReviews = compositionLocalOf { ReviewHooks() }

/** 하트 1~5개 (onPick 이 있으면 눌러서 고르기) */
@Composable
fun HeartsRow(hearts: Int, size: TextUnit = 16.sp, onPick: ((Int) -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (onPick != null) 6.dp else 1.dp),
        modifier = Modifier.semantics { contentDescription = "하트 ${hearts}개" },
    ) {
        ReviewEntry.HEARTS.forEach { i ->
            val on = i <= hearts
            val scale = bounceScale(onPick != null && i == hearts, 1.25f)
            Text(
                if (on) "♥" else "♡",
                fontSize = size,
                color = if (on) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(
                        if (onPick != null) {
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onPick(i) }
                                .padding(4.dp)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** 일정 카드 아래 한 줄 후기: 두 사람 후기 + [후기 남기기 / 내 후기 고치기] */
@Composable
fun ReviewStrip(event: CalendarEvent, couple: Couple, myUid: String) {
    val hooks = LocalReviews.current
    val review = hooks.of(event)
    var editing by remember { mutableStateOf(false) }
    val mine = review?.entries?.get(myUid)
    Column(Modifier.padding(start = 16.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        review?.entries?.entries?.sortedBy { if (it.key == myUid) 0 else 1 }?.forEach { (uid, e) ->
            val who = if (uid == myUid) couple.myName(myUid) else (couple.names[uid] ?: "상대")
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeartsRow(e.hearts, 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "$who · ${e.text.ifBlank { "(한 줄 없이 하트만)" }}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (mine == null) "💗 한 줄 후기 남기기" else "내 후기 고치기",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { editing = true }
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
    if (editing) {
        ReviewDialog(
            title = event.title,
            existing = mine,
            onSave = { hearts, text ->
                editing = false
                hooks.onSave(event, hearts, text)
            },
            onDelete = review?.takeIf { mine != null }?.let { r -> { editing = false; hooks.onDelete(r) } },
            onDismiss = { editing = false },
        )
    }
}

/** 후기 쓰기: 하트 1~5 + 한 줄 (100자) */
@Composable
fun ReviewDialog(
    title: String,
    existing: ReviewEntry?,
    onSave: (hearts: Int, text: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var hearts by rememberSaveable { mutableIntStateOf(existing?.hearts ?: 5) }
    var text by rememberSaveable { mutableStateOf(existing?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💗 $title", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("어땠어요?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HeartsRow(hearts, 30.sp) { hearts = it }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace('\n', ' ').take(ReviewEntry.TEXT_MAX) },
                    label = { Text("한 줄 후기") },
                    placeholder = { Text("예) 감자탕 최고, 또 오자!") },
                    singleLine = true,
                    supportingText = { Text("${text.length}/${ReviewEntry.TEXT_MAX}") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hearts, text.trim()) }) { Text("저장") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("지우기", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

/** 가고 싶은 곳 탭: 후기 하트가 높은 장소 (장소 이름별 평균, 4개 이상) */
data class LovedPlace(
    val place: com.uridul.app.data.Place,
    val averageHearts: Double,
    val visits: Int,
    val lastText: String,
    val lastDate: java.time.LocalDate,
)

fun lovedPlaces(reviews: Collection<DateReview>, minHearts: Double = 4.0): List<LovedPlace> =
    reviews.filter { it.place != null && it.entries.isNotEmpty() }
        .groupBy { it.place!!.name }
        .map { (_, list) ->
            val latest = list.maxBy { it.date }
            LovedPlace(
                place = latest.place!!,
                averageHearts = list.flatMap { r -> r.entries.values.map { it.hearts } }.average(),
                visits = list.size,
                lastText = latest.entries.values.firstOrNull { it.text.isNotBlank() }?.text.orEmpty(),
                lastDate = latest.date,
            )
        }
        .filter { it.averageHearts >= minHearts }
        .sortedWith(compareByDescending<LovedPlace> { it.averageHearts }.thenByDescending { it.lastDate })


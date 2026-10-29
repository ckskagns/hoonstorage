package com.uridul.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.DateReview

/**
 * 일기장: 두 사람이 남긴 한 줄 후기를 최신 날짜부터 모아 본다.
 * 하루마다 일정 제목·장소, 두 사람의 하트와 한 줄, 그 날 사진첩 사진.
 * 사진을 누르면 크게 보기, 나머지를 누르면 그 날 하루 페이지.
 */
@Composable
fun DiaryContent(
    couple: Couple,
    myUid: String,
    onOpenDay: (DateReview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reviews = LocalReviews.current.reviews
    // 최신 날짜부터 (같은 날은 제목순), 후기가 남아 있는 날만
    val entries = remember(reviews) {
        reviews.values.filter { it.entries.isNotEmpty() }.sortedWith(compareByDescending<DateReview> { it.date }.thenBy { it.title })
    }
    // 달마다 머리글
    val byMonth = remember(entries) { entries.groupBy { it.date.year to it.date.monthValue } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📖 우리 일기장", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (entries.isEmpty()) "지난 일정에 한 줄 후기를 남기면 여기에 차곡차곡 모여요" else "후기 ${entries.size}개 · 최근 날짜부터",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "아직 쓴 후기가 없어요 🌷\n캘린더에서 지난 일정을 누르고\n💗 한 줄 후기 남기기 를 눌러 보세요.\n홈의 추억 슬라이드에서도 바로 쓸 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
        byMonth.forEach { (ym, list) ->
            item(key = "m${ym.first}-${ym.second}") {
                Text(
                    "${ym.first}년 ${ym.second}월",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 8.dp, start = 4.dp),
                )
            }
            items(list, key = { it.id }) { r -> DiaryEntry(r, couple, myUid) { onOpenDay(r) } }
        }
    }
}

/** 일기 하루: 날짜·제목·장소 / 두 사람 후기 / 그 날 사진 */
@Composable
private fun DiaryEntry(review: DateReview, couple: Couple, myUid: String, onOpen: () -> Unit) {
    val photosHooks = LocalPhotos.current
    // 사진첩은 "일정 ID + 날짜" 로 찾는다
    val dayEvent = remember(review.id) {
        CalendarEvent(review.eventId, review.title, review.date, review.date, null, "", "")
    }
    val photos by remember(review.id) { photosHooks.album(dayEvent) }.collectAsState(initial = emptyList())
    var viewing by remember { mutableStateOf<Int?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
    ) {
        Column(Modifier.clickable(onClick = onOpen).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(review.date.format(KoreanMonthDay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(review.title.ifBlank { "(제목 없음)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            review.place?.let {
                Text("📍 ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 두 사람 후기 (내 것 먼저)
            review.entries.entries.sortedBy { if (it.key == myUid) 0 else 1 }.forEach { (uid, e) ->
                val who = if (uid == myUid) couple.myName(myUid) else (couple.names[uid] ?: "상대")
                Row(verticalAlignment = Alignment.Top) {
                    CharacterAvatar(couple.characterOf(uid), 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(who, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            HeartsRow(e.hearts, 13.sp)
                        }
                        if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
                        val bmp = rememberPhotoBitmap(p.id, p.thumb)
                        Box(
                            Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { viewing = i }
                                .semantics { contentDescription = "사진 ${i + 1}, 눌러서 크게 보기" },
                        ) {
                            bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { start ->
        PhotoViewerDialog(
            photos = photos,
            start = start.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
            couple = couple,
            myUid = myUid,
            onDelete = { p -> photosHooks.onDelete(p) },
            onDismiss = { viewing = null },
        )
    }
}

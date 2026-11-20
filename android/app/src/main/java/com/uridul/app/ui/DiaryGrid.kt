package com.uridul.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.data.DateReview
import com.uridul.app.data.DayPhoto
import com.uridul.app.domain.PhotoDays
import com.uridul.app.domain.PhotoGrid
import java.time.LocalDate

// 일기장 "모아보기": 사진 있는 날을 달마다 격자로. DiaryContent 에서 쓴다.

/** 일기장 위 "📰 피드 / 🔲 모아보기" */
@Composable
internal fun DiaryModeChips(grid: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !grid, onClick = { onChange(false) }, label = { Text("📰 피드") })
        FilterChip(selected = grid, onClick = { onChange(true) }, label = { Text("🔲 모아보기") })
    }
}

/** 그날 사진의 게시물 (후기 제목·장소가 있으면 함께) */
internal fun postFor(date: LocalDate, photo: DayPhoto, reviews: Map<String, DateReview>): FeedPost {
    val r = reviews[DateReview.idOf(photo.eventId, date)]
    return FeedPost(photo.eventId, date, r?.title.orEmpty(), r?.place)
}

/**
 * 사진 격자: 맨 위 [header](전환·달 단추), 달마다 머리글 줄 + 칸들.
 * @param selected 2단 화면에서 오른쪽에 보이는 날 (테두리)
 */
@Composable
internal fun DiaryPhotoGrid(
    sections: List<PhotoGrid.Section>,
    photos: Map<LocalDate, DayPhoto>,
    selected: LocalDate?,
    onPick: (LocalDate, DayPhoto) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** 사진 있는 날이 하나도 없을 때 */
    empty: @Composable () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) { header() }
        if (sections.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                empty()
            }
        }
        sections.forEach { s ->
            item(key = "m${s.month}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    s.header,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp),
                )
            }
            items(s.days, key = { "d$it" }) { day ->
                photos[day]?.let { p -> GridTile(day, p, day == selected) { onPick(day, p) } }
            }
        }
    }
}

@Composable
private fun GridTile(day: LocalDate, photo: DayPhoto, selected: Boolean, onClick: () -> Unit) {
    val bmp = rememberPhotoBitmap("cal:${photo.id}", photo.thumb)
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "이날 사진 보기", onClick = onClick)
            .semantics { contentDescription = "${day.monthValue}월 ${day.dayOfMonth}일 사진 ${photo.count}장" },
    ) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        GridBadge("${day.dayOfMonth}일", Modifier.align(Alignment.TopStart))
        PhotoDays.moreText(photo.count)?.let { GridBadge(it, Modifier.align(Alignment.BottomEnd)) }
        if (selected) Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary, shape))
    }
}

@Composable
private fun GridBadge(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

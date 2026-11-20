package com.uridul.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.data.DayPhoto
import com.uridul.app.domain.PhotoDays
import java.time.LocalDate

// 사진 달력: 머리글의 📷 단추, 사진이 있는 날의 달력 칸. CalendarMonthGrid 에서 쓴다.

/** 머리글 사진 단추 크기 (왼쪽에도 같은 만큼 비워 제목을 가운데에 둔다) */
internal val PhotoToggleSize = 40.dp

/** 📷 사진 달력 켜기/끄기 (켜면 동그란 바탕이 깔린다) */
@Composable
internal fun PhotoModeToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(PhotoToggleSize)
            .clip(CircleShape)
            .background(if (on) scheme.primaryContainer else Color.Transparent)
            .clickable(
                role = Role.Switch,
                onClickLabel = if (on) "사진 달력 끄기" else "사진 달력 켜기",
            ) { onChange(!on) }
            .semantics { contentDescription = if (on) "사진 달력 켜짐" else "사진 달력 꺼짐" },
        contentAlignment = Alignment.Center,
    ) {
        Text("📷", style = MaterialTheme.typography.titleMedium, modifier = Modifier.graphicsLayer { alpha = if (on) 1f else 0.45f })
    }
}

/**
 * 사진이 있는 날의 칸: 사진을 칸 가득 깔고, 위쪽을 살짝 어둡게 해서 날짜 숫자를 흰 글씨로.
 * 사진이 여러 장이면 오른쪽 아래 "+N". 고른 날은 테두리 (바탕을 칠하면 사진이 가려지므로).
 * 하루 일정 글자는 사진을 가리지 않게 빼고, 여러 날 막대는 달력이 그대로 위에 그린다.
 */
@Composable
internal fun PhotoDayCell(
    day: LocalDate,
    photo: DayPhoto,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    inRange: Boolean,
    numberScale: Float,
    dayStyle: TextStyle,
    smallStyle: TextStyle,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val bitmap = rememberPhotoBitmap("cal:${photo.id}", photo.thumb)
    val fade = if (inMonth) 1f else 0.4f
    Box(
        modifier
            .padding(1.dp)
            .clip(shape)
            .background(scheme.surfaceVariant)
            .semantics {
                contentDescription = description
                this.onClick(label = if (isSelected) "새 일정" else "선택") { onClick(); true }
                this.onLongClick(label = "새 일정") { onLongClick(); true }
            },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = fade,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 날짜 숫자가 어떤 사진 위에서도 읽히게 위쪽만 어둡게
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f * fade), 0.5f to Color.Transparent)),
        )
        if (inRange) Box(Modifier.fillMaxSize().background(scheme.primary.copy(alpha = 0.35f)))
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 3.dp)
                .size(24.dp)
                .graphicsLayer {
                    scaleX = numberScale
                    scaleY = numberScale
                }
                .then(if (isToday) Modifier.background(scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = dayStyle,
                color = if (isToday) scheme.onPrimary else Color.White.copy(alpha = if (inMonth) 1f else 0.7f),
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
        PhotoDays.moreText(photo.count)?.let { more ->
            Text(
                more,
                style = smallStyle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 3.dp),
            )
        }
        if (isSelected) Box(Modifier.fillMaxSize().border(2.5.dp, scheme.primary, shape))
    }
}

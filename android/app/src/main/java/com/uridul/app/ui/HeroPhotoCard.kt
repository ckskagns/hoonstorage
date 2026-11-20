package com.uridul.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.DateReview
import com.uridul.app.data.HeroAction
import com.uridul.app.data.HeroPhoto
import com.uridul.app.data.HeroPhotoStore
import com.uridul.app.domain.DDay
import com.uridul.app.domain.HeroPick
import java.time.LocalDate

// 디데이 카드에 오늘의 사진 깔기 (홈). 사진은 PhotoController.refreshHero 가 고른다.

/**
 * 사진 디데이 카드: 사진을 가득 깔고 위·아래만 어둡게 해서 글자를 흰색으로.
 *  - 왼쪽 위: "1년 전 오늘 · 아울렛데이트" (후기 제목이 있으면)
 *  - 오른쪽 위 ⋯: 다른 사진 / 이 사진 고정·매일 바뀌게 / 사진 끄기
 *  - 아래: 두 캐릭터 + 이름, 함께한 지 N일, 시작일
 *  - 사진을 누르면 그날 일기(크게 보기)
 */
@Composable
internal fun PhotoHeroCard(
    couple: Couple,
    myUid: String,
    today: LocalDate,
    photo: HeroPhoto,
    mode: HeroPhotoStore.Mode,
    onAction: (HeroAction) -> Unit,
    onOpen: (CalendarEvent) -> Unit,
    modifier: Modifier,
) {
    val review = LocalReviews.current.reviews[DateReview.idOf(photo.eventId, photo.date)]
    val label = HeroPick.label(photo.date, today)
    val bitmap = rememberPhotoBitmap("hero:${photo.id}:${photo.sharp}", photo.bytes)
    val heroSize = with(LocalDensity.current) { 56.dp.toSp() }
    // 다크 모드에서는 사진 위 글자 뒤를 조금 더 어둡게 (사진 자체는 그대로)
    val dark = com.uridul.app.ui.theme.LocalDarkTheme.current
    val partner = couple.partnerName(myUid)
    var menu by remember { mutableStateOf(false) }
    Card(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    onClickLabel = "이날 일기 보기",
                    enabled = photo.eventId.isNotEmpty(),
                ) { onOpen(FeedPost(photo.eventId, photo.date, review?.title.orEmpty(), review?.place).asEvent()) },
        ) {
            bitmap?.let {
                Image(it, contentDescription = "${label} 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // 위(작은 글자)·아래(디데이) 글자가 어떤 사진에서도 읽히게
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = if (dark) 0.50f else 0.40f),
                            0.22f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = if (dark) 0.82f else 0.70f),
                        ),
                    ),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 14.dp, end = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "📸 " + label + (review?.title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mode == HeroPhotoStore.Mode.FIXED) {
                    Text(" 📌", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.30f))
                        .clickable(role = Role.Button, onClickLabel = "사진 바꾸기") { menu = true }
                        .semantics { contentDescription = "디데이 사진 메뉴" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋯", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("🔀 다른 사진") }, onClick = { menu = false; onAction(HeroAction.NEXT) })
                    if (mode == HeroPhotoStore.Mode.FIXED) {
                        DropdownMenuItem(text = { Text("📅 매일 바뀌게") }, onClick = { menu = false; onAction(HeroAction.DAILY) })
                    } else {
                        DropdownMenuItem(text = { Text("📌 이 사진 고정") }, onClick = { menu = false; onAction(HeroAction.PIN) })
                    }
                    DropdownMenuItem(text = { Text("🙈 사진 끄기") }, onClick = { menu = false; onAction(HeroAction.OFF) })
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CharacterAvatar(couple.myCharacter(myUid), 36.dp, bob = true, phase = 0)
                    CharacterAvatar(
                        couple.partnerCharacter(myUid), 36.dp, bob = true, phase = 1,
                        modifier = Modifier.graphicsLayer { alpha = if (partner == null) 0.45f else 1f },
                    )
                    Text(
                        "${couple.myName(myUid)} ♥ ${partner ?: "?"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("함께한 지", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Text(
                    DDay.heroText(couple.startDate, today, couple.countFromOne),
                    fontSize = heroSize,
                    // 디데이 숫자는 둥근 주아 글꼴
                    fontFamily = com.uridul.app.ui.theme.JuaFamily,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    "${couple.startDate.format(KoreanLongDate)}부터",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** 사진을 꺼 둔 디데이 카드 오른쪽 위 📷: 다시 켜기 */
@Composable
internal fun HeroPhotoOnButton(onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "디데이 사진 켜기", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("📷", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "디데이 사진 켜기" })
    }
}

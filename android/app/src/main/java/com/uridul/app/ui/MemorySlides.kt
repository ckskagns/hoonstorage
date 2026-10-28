package com.uridul.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.domain.Memories
import com.uridul.app.ui.theme.CoupleColors
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** 가만히 두면 다음 장으로 넘어가는 시간 */
private const val AUTO_ADVANCE_MILLIS = 4_000L

/**
 * 홈 "추억 모음": 지난 한 달(그리고 몇 년 전 같은 날) 일정을 그림 카드로 넘겨 본다.
 *  - 손으로 좌우로 넘길 수 있고, 가만히 두면 4초마다 다음 장으로 (끝나면 처음으로)
 *  - 카드를 누르면 그 날 하루 페이지
 */
@Composable
fun MemorySlides(
    slides: List<Memories.Slide>,
    couple: Couple,
    myUid: String,
    onOpen: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) return
    val pager = rememberPagerState { slides.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    // 손대지 않으면 자동으로 다음 장 (넘기는 중이면 멈춘다)
    LaunchedEffect(pager.settledPage, dragged, slides.size) {
        if (dragged || slides.size < 2) return@LaunchedEffect
        delay(AUTO_ADVANCE_MILLIS)
        pager.animateScrollToPage((pager.settledPage + 1) % slides.size)
    }
    val yearly = slides.first().label.contains("년 전")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (yearly) "📸 그때 오늘" else "📸 지난 한 달 추억",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${pager.currentPage + 1} / ${slides.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalPager(
                state = pager,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                pageSpacing = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                // 옆 장은 살짝 작고 흐리게 (넘기는 느낌)
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                MemoryCard(
                    slide = slides[page],
                    couple = couple,
                    myUid = myUid,
                    onClick = { onOpen(slides[page].event) },
                    modifier = Modifier.graphicsLayer {
                        val s = 1f - 0.08f * offset
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.35f * offset
                    },
                )
            }
            if (slides.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    // 장 수가 많으면 점은 10개까지만
                    val shown = minOf(slides.size, 10)
                    repeat(shown) { i ->
                        val active = i == pager.currentPage.coerceAtMost(shown - 1)
                        val color by animateColorAsState(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            label = "dot",
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (active) 8.dp else 6.dp)
                                .background(color, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** 그림 카드 한 장: 큰 그림 + 언제 + 제목 + 장소 + 후기 */
@Composable
private fun MemoryCard(slide: Memories.Slide, couple: Couple, myUid: String, onClick: () -> Unit, modifier: Modifier) {
    val e = slide.event
    val mine = e.createdBy == myUid
    val fill = if (mine) CoupleColors.mineFill else CoupleColors.partnerFill
    val other = if (mine) CoupleColors.partnerFill else CoupleColors.mineFill
    val review = LocalReviews.current.of(e)
    val place = e.place ?: e.slots.firstOrNull { it.place != null }?.place
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(fill, other.copy(alpha = 0.75f))))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${slide.label} ${e.title}, 눌러서 그 날 보기" },
    ) {
        // 배경의 큰 그림 (오른쪽 아래, 살짝 투명)
        Text(
            slide.emoji,
            fontSize = 96.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 4.dp)
                .graphicsLayer { alpha = 0.9f; rotationZ = -8f },
        )
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slide.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    e.date.format(KoreanMonthDay),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoupleColors.onFill.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                CharacterAvatar(couple.characterOf(e.createdBy), 28.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                e.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CoupleColors.onFill,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
            place?.let {
                Text(
                    "📍 ${it.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoupleColors.onFill.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.72f),
                )
            }
            Spacer(Modifier.weight(1f))
            if (review != null && review.entries.isNotEmpty()) {
                val best = review.entries[myUid] ?: review.entries.values.first()
                HeartsRow(best.hearts, 14.sp)
                if (best.text.isNotBlank()) {
                    Text(
                        "“${best.text}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoupleColors.onFill,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.72f),
                    )
                }
            } else {
                Text(
                    "💗 한 줄 후기를 남겨 보세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoupleColors.onFill.copy(alpha = 0.7f),
                )
            }
        }
    }
}

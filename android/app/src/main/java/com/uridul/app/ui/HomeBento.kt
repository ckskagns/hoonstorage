package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.WishPlace
import com.uridul.app.domain.CalendarMath
import com.uridul.app.domain.HomeTiles
import com.uridul.app.domain.PhotoQuota
import com.uridul.app.ledger.LedgerTotals
import com.uridul.app.ledger.Won
import java.time.LocalDate
import java.time.YearMonth

// 홈 벤토: 디데이 카드 옆(아래)에 작은 칸 네 개 — 다음 일정 · 이번 달 데이트 비용 · 가고 싶은 곳 · 이번 주 사진.
// 칸을 누르면 그 탭으로.

/** 벤토 칸이 여는 탭 (ReadyScreen 의 탭 키) */
object HomeTabs {
    const val CALENDAR = "calendar"
    const val LEDGER = "ledger"
    const val WISH = "wish"
    const val DIARY = "diary"

    /** 탭 키가 아니라 "캘린더 탭에서 오늘 새 일정 바로 쓰기" 요청 */
    const val ADD_EVENT = "add_event"
}

/**
 * 벤토 칸 네 개 (2×2).
 * @param eventsKey 일정이 바뀌면 다음 일정을 다시 읽도록 (캘린더 탭의 이번 달 일정)
 */
@Composable
fun HomeBento(couple: Couple, today: LocalDate, wishes: List<WishPlace>, eventsKey: Any?, onOpenTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = appContainer(context)
    // 다음 일정: 위젯과 같은 계산 (반복 일정은 두 달 앞까지 펼쳐서)
    // 탭을 오갈 때마다 서버를 다시 읽지 않게 5분 동안 기억 (일정이 바뀌면 키가 달라져 바로 다시 읽는다)
    val nextKey = listOf("next", couple.id, today, eventsKey?.hashCode())
    val upcoming by produceState(BentoCache.peek<List<CalendarEvent>>(nextKey), nextKey) {
        val repo = container?.events ?: return@produceState
        value = BentoCache.load(nextKey) {
            runCatching {
                val merged = com.uridul.app.domain.Recurrence.merge(
                    repo.fetchUpcoming(couple.id, today), repo.fetchRecurring(couple.id), today, today.plusDays(60),
                )
                // 오늘 이미 끝난 시간 일정은 고를 때 건너뛰므로 몇 개 더 받아 둔다
                CalendarMath.upcoming(merged, today, 6)
            }.getOrNull()
        }
    }
    // 이번 달 데이트 비용 (가계부와 같은 계산: 확정, 취소 짝 빼고)
    val month = YearMonth.from(today)
    val spentKey = listOf("spent", couple.id, month)
    val spent by produceState(BentoCache.peek<Pair<Long, Int>>(spentKey), spentKey) {
        val repo = container?.ledger ?: return@produceState
        value = BentoCache.load(spentKey) {
            runCatching {
                val entries = repo.fetchMonth(couple.id, month)
                val confirmed = entries.filter { it.status == com.uridul.app.ledger.EntryStatus.CONFIRMED }
                LedgerTotals.expense(entries, com.uridul.app.ledger.CancelPairs.of(entries)).coerceAtLeast(0) to confirmed.size
            }.getOrNull()
        }
    }
    val now = java.time.LocalDateTime.now()
    val next = upcoming?.let { HomeTiles.nextEvent(it, now) }
    val wish = HomeTiles.wishOfDay(wishes, today)
    val openWishes = wishes.count { !it.visited }
    val weekUsed = LocalPhotos.current.weekUsed

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoTile(
                "📅 다음 일정",
                // 못 읽었거나 읽는 중이면(upcoming == null) "없어요"라고 단정하지 않는다
                next?.title ?: if (upcoming == null) "우리 캘린더" else "다가오는 일정이 없어요",
                next?.let { HomeTiles.whenText(it, today, now) } ?: if (upcoming == null) "눌러서 캘린더 보기" else "눌러서 일정 추가",
                MaterialTheme.colorScheme.secondaryContainer,
                Modifier.weight(1f),
                art = Emotion.DATE.takeIf { upcoming != null && next == null },
            ) {
                // 다가오는 일정이 없다고 확인된 때만 바로 새 일정 (읽는 중이면 캘린더로)
                onOpenTab(if (upcoming != null && next == null) HomeTabs.ADD_EVENT else HomeTabs.CALENDAR)
            }
            BentoTile(
                "💳 ${month.monthValue}월 데이트 비용",
                spent?.let { Won.text(it.first) } ?: "-",
                spent?.let { "기록 ${it.second}건" } ?: "데이트 가계부",
                MaterialTheme.colorScheme.tertiaryContainer,
                Modifier.weight(1f),
                art = Emotion.SAVE.takeIf { spent != null && spent?.second == 0 },
            ) { onOpenTab(HomeTabs.LEDGER) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoTile(
                "📍 가고 싶은 곳",
                wish?.place?.name ?: "가고 싶은 곳을 담아 보세요",
                if (wish != null) "아직 못 간 ${openWishes}곳 중 오늘의 한 곳" else "지도 앱에서 공유하면 담겨요",
                MaterialTheme.colorScheme.surfaceContainerHigh,
                Modifier.weight(1f),
                art = Emotion.TRIP.takeIf { wish == null },
            ) { onOpenTab(HomeTabs.WISH) }
            BentoTile(
                "📷 이번 주 사진",
                "$weekUsed / ${PhotoQuota.WEEKLY_LIMIT}장",
                "일기장에서 사진 올리기",
                MaterialTheme.colorScheme.primaryContainer,
                Modifier.weight(1f),
                art = Emotion.PHOTO.takeIf { weekUsed == 0 },
            ) { onOpenTab(HomeTabs.DIARY) }
        }
    }
}

@Composable
private fun BentoTile(
    label: String, value: String, sub: String, color: Color, modifier: Modifier,
    /** 비어 있는 칸에만 오른쪽 아래 작은 캐릭터 (그림이 들어 있는 앱에서만) */
    art: Emotion? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 104.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
      androidx.compose.foundation.layout.Box {
        val res = art?.let { rememberEmotionRes(it) } ?: 0
        if (art != null) SmallArt(art, 64.dp, Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).padding(end = if (res != 0) 40.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
}

/** 홈 벤토 값 기억 (앱이 켜져 있는 동안, 키마다 5분) */
private object BentoCache {
    private const val TTL_MS = 5 * 60 * 1000L
    private val map = HashMap<Any, Pair<Long, Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> peek(key: Any): T? = map[key]?.second as T?

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> load(key: Any, block: suspend () -> T?): T? {
        val now = System.currentTimeMillis()
        map[key]?.let { (at, v) -> if (now - at < TTL_MS) return v as T? }
        return block().also { if (it != null) map[key] = now to it }
    }
}

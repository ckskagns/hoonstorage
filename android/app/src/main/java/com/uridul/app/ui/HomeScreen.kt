package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.data.birthdays
import com.uridul.app.domain.DDay
import com.uridul.app.domain.SpecialDay
import com.uridul.app.domain.SpecialDays
import java.time.LocalDate

/** 홈 탭: 디데이 + 다가오는 기념일 */
@Composable
fun HomeContent(
    couple: Couple,
    myUid: String,
    busy: Boolean,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
    /** 추억 슬라이드 (지난 한 달·몇 년 전 같은 날) */
    memories: List<com.uridul.app.domain.Memories.Slide> = emptyList(),
    onLoadMemories: (java.time.LocalDate) -> Unit = {},
    onOpenMemory: (com.uridul.app.data.CalendarEvent) -> Unit = {},
    /** 벤토 칸: 가고 싶은 곳 목록, 일정이 바뀐 것을 알리는 값, 칸을 누르면 열 탭 */
    wishes: List<com.uridul.app.data.WishPlace> = emptyList(),
    eventsKey: Any? = null,
    onOpenTab: (String) -> Unit = {},
) {
    val today = rememberToday()
    val bento: @Composable (Modifier) -> Unit = { m -> HomeBento(couple, today, wishes, eventsKey, onOpenTab, m) }
    // 날이 바뀌면 추억을 다시 받아온다 (하루에 한 번)
    val photos = LocalPhotos.current
    androidx.compose.runtime.LaunchedEffect(today) {
        onLoadMemories(today)
        // 디데이 카드에 깔 오늘의 사진 (일기 사진 중에서, 하루에 하나)
        photos.refreshHero(today)
    }
    // 매달 1~7일: "지난달의 우리" 카드 (지난달에 후기·사진이 있고, 닫지 않았으면)
    val context = androidx.compose.ui.platform.LocalContext.current
    val reviews = LocalReviews.current.reviews
    val recapMonth = com.uridul.app.domain.MonthRecap.homeMonth(today)
    var recapClosed by androidx.compose.runtime.remember(recapMonth) {
        androidx.compose.runtime.mutableStateOf(recapMonth == null || RecapFlags.closed(context, recapMonth))
    }
    val showRecap = recapMonth != null && !recapClosed &&
        recapMonth in com.uridul.app.domain.MonthRecap.months(reviews.values, photos.dayPhotos.keys, today)
    var recapOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (recapOpen && recapMonth != null) MonthRecapDialog(recapMonth, couple, myUid) { recapOpen = false }
    val recapBanner: @Composable (Modifier) -> Unit = { m ->
        if (showRecap && recapMonth != null) RecapHomeBanner(
            recapMonth,
            onOpen = { recapOpen = true },
            onClose = { RecapFlags.close(context, recapMonth); recapClosed = true },
            modifier = m,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // 폭에 따라 1단/2단
        run {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val milestones = remember(couple, today, twoPane) {
                SpecialDays.upcoming(
                    couple.startDate, couple.countFromOne, couple.birthdays(), today,
                    personalLimit = if (twoPane) 5 else 4,
                    coupleDayLimit = 2,
                )
            }

            if (twoPane) {
                // 폴드 펼침 / 가로 화면: 왼쪽 디데이, 오른쪽 기념일
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    HeroCard(
                        couple, myUid, today,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onOpenMemory,
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!couple.isPaired) InviteBanner(busy, onInvite)
                        bento(Modifier.fillMaxWidth())
                        recapBanner(Modifier.fillMaxWidth())
                        MemorySlides(memories, couple, myUid, onOpenMemory)
                        MilestoneCard(milestones, today, Modifier.fillMaxWidth())
                    }
                }
            } else {
                // 폰 / 폴드 커버: 1단
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val m = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                    HeroCard(couple, myUid, today, m, onOpenMemory, photoHeight = HeroPhotoHeight)
                    if (!couple.isPaired) Column(m) { InviteBanner(busy, onInvite) }
                    bento(m)
                    recapBanner(m)
                    MemorySlides(memories, couple, myUid, onOpenMemory, m)
                    MilestoneCard(milestones, today, m)
                }
            }
        }
    }
}

/** 1단 화면(폰·폴드 커버)에서 사진 디데이 카드 높이 (2단은 화면 높이를 다 쓴다) */
private val HeroPhotoHeight = 360.dp

/**
 * 디데이 카드: 오늘의 사진이 있으면 사진을 깐 카드, 없으면(사진이 없거나 꺼 둠) 캐릭터 카드.
 * @param photoHeight 사진 카드 높이 (null 이면 [modifier] 높이를 따른다)
 */
@Composable
private fun HeroCard(
    couple: Couple,
    myUid: String,
    today: LocalDate,
    modifier: Modifier,
    onOpenDay: (com.uridul.app.data.CalendarEvent) -> Unit,
    photoHeight: androidx.compose.ui.unit.Dp? = null,
) {
    val photos = LocalPhotos.current
    val hero = photos.hero
    hero.photo?.let { p ->
        PhotoHeroCard(
            couple, myUid, today, p, hero.mode, photos.heroAction, onOpenDay,
            if (photoHeight != null) modifier.height(photoHeight) else modifier,
        )
        return
    }
    val partner = couple.partnerName(myUid)
    // 큰 숫자는 글꼴 크기 설정을 따라 무한히 커지지 않도록 dp 기준으로 고정한다
    val heroSize = with(LocalDensity.current) { 64.dp.toSp() }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
      androidx.compose.foundation.layout.Box {
        // 사진을 꺼 뒀고 고를 사진이 있으면 다시 켜는 📷
        if (hero.mode == com.uridul.app.data.HeroPhotoStore.Mode.OFF && hero.available) {
            HeroPhotoOnButton(
                onClick = { photos.heroAction(com.uridul.app.data.HeroAction.ON) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            // 두 캐릭터가 살랑살랑 움직이고, 누르면 콩 뛴다
            CoupleHeader(
                myCharacter = couple.myCharacter(myUid),
                partnerCharacter = couple.partnerCharacter(myUid),
                myName = couple.myName(myUid),
                partnerName = partner,
                avatarSize = 72.dp,
            )
            Text(
                "함께한 지",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Text(
                DDay.heroText(couple.startDate, today, couple.countFromOne),
                fontSize = heroSize,
                // 디데이 숫자는 둥근 주아 글꼴
                fontFamily = com.uridul.app.ui.theme.JuaFamily,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                "${couple.startDate.format(KoreanLongDate)}부터",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
      }
    }
}

@Composable
private fun InviteBanner(busy: Boolean, onInvite: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "아직 상대가 연결되지 않았어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                "초대 링크를 보내면 상대가 눌러서 바로 들어올 수 있어요. 링크는 24시간 동안 한 번만 쓸 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Button(onClick = onInvite, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("초대 링크 보내기")
            }
        }
    }
}

@Composable
private fun MilestoneCard(milestones: List<SpecialDay>, today: LocalDate, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                "다가오는 기념일",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            milestones.forEachIndexed { index, m ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (m.kind.personal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            m.date.format(KoreanDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val left = SpecialDays.daysLeft(today, m)
                    Text(
                        if (left == 0L) "오늘 🎉" else DDay.daysLeftText(left),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (m.kind.personal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

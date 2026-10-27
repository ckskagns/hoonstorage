package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
) {
    val today = rememberToday()

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
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!couple.isPaired) InviteBanner(busy, onInvite)
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
                    HeroCard(couple, myUid, today, m)
                    if (!couple.isPaired) Column(m) { InviteBanner(busy, onInvite) }
                    MilestoneCard(milestones, today, m)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(couple: Couple, myUid: String, today: LocalDate, modifier: Modifier) {
    val partner = couple.partnerName(myUid)
    // 큰 숫자는 글꼴 크기 설정을 따라 무한히 커지지 않도록 dp 기준으로 고정한다
    val heroSize = with(LocalDensity.current) { 64.dp.toSp() }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
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
                fontWeight = FontWeight.Bold,
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

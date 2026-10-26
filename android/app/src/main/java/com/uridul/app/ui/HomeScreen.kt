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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.domain.DDay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    couple: Couple,
    myUid: String,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onInvite: () -> Unit,
    onChangeStartDate: (LocalDate) -> Unit,
    onSetCountFromOne: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val today = rememberToday()
    var menuOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("우리둘", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "메뉴")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("시작일 변경") },
                            onClick = { menuOpen = false; picking = true },
                        )
                        DropdownMenuItem(
                            text = { Text("사귄 날을 1일로 계산") },
                            trailingIcon = { Checkbox(checked = couple.countFromOne, onCheckedChange = null) },
                            onClick = { menuOpen = false; onSetCountFromOne(!couple.countFromOne) },
                        )
                        if (!couple.isPaired) {
                            DropdownMenuItem(
                                text = { Text("초대 링크 보내기") },
                                onClick = { menuOpen = false; onInvite() },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("로그아웃") },
                            onClick = { menuOpen = false; onSignOut() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val milestones = remember(couple, today, twoPane) {
                DDay.upcoming(couple.startDate, today, couple.countFromOne, limit = if (twoPane) 6 else 4)
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
                        MilestoneCard(milestones, Modifier.fillMaxWidth())
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
                    MilestoneCard(milestones, m)
                }
            }
        }
    }

    if (picking) {
        StartDatePickerDialog(
            initial = couple.startDate,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onChangeStartDate(it)
            },
        )
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                "${couple.myName(myUid)}  ♥  ${partner ?: "?"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
private fun MilestoneCard(milestones: List<DDay.Milestone>, modifier: Modifier) {
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
                    Column(Modifier.weight(1f)) {
                        Text(m.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            m.date.format(KoreanDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (m.daysLeft == 0L) "오늘 🎉" else DDay.daysLeftText(m.daysLeft),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

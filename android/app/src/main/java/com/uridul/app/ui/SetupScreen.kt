package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.BuildConfig
import com.uridul.app.domain.InviteToken
import java.time.LocalDate

/** 로그인 후, 아직 커플 공간이 없을 때: 새로 만들기 / 초대 링크로 참여하기 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    myName: String,
    pendingInvite: String?,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onCreate: (LocalDate) -> Unit,
    onJoin: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("반가워요, ${myName}님") },
                actions = { TextButton(onClick = onSignOut) { Text("로그아웃") } },
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
            val createCard = @Composable { modifier: Modifier ->
                CreateCard(busy = busy, onCreate = onCreate, modifier = modifier)
            }
            val joinCard = @Composable { modifier: Modifier ->
                JoinCard(pendingInvite = pendingInvite, busy = busy, onJoin = onJoin, modifier = modifier)
            }

            if (twoPane) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // 초대받은 경우 참여 카드를 먼저 보여준다
                    if (pendingInvite != null) {
                        joinCard(Modifier.weight(1f)); createCard(Modifier.weight(1f))
                    } else {
                        createCard(Modifier.weight(1f)); joinCard(Modifier.weight(1f))
                    }
                }
            } else {
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
                    if (pendingInvite != null) {
                        joinCard(m); createCard(m)
                    } else {
                        createCard(m); joinCard(m)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCard(busy: Boolean, onCreate: (LocalDate) -> Unit, modifier: Modifier) {
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }

    SetupCard(
        title = "새 커플 공간 만들기",
        description = "사귀기 시작한 날을 고르고 공간을 만든 뒤, 초대 링크를 상대에게 보내세요.",
        modifier = modifier,
    ) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text("시작일: ${startDate.format(KoreanLongDate)}")
        }
        Button(
            onClick = { onCreate(startDate) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("만들기") }
    }

    if (picking) {
        StartDatePickerDialog(
            initial = startDate,
            onDismiss = { picking = false },
            onConfirm = {
                startDate = it
                picking = false
            },
        )
    }
}

@Composable
private fun JoinCard(pendingInvite: String?, busy: Boolean, onJoin: (String) -> Unit, modifier: Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) input = InviteToken.link(BuildConfig.INVITE_HOST, pendingInvite)
    }

    SetupCard(
        title = if (pendingInvite != null) "💌 초대받았어요!" else "초대받았어요",
        description = if (pendingInvite != null) {
            "아래 연결하기를 누르면 상대와 연결돼요."
        } else {
            "상대가 보낸 초대 링크를 붙여넣어 주세요."
        },
        highlighted = pendingInvite != null,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("초대 링크") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onJoin(input) },
            enabled = !busy && input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("연결하기") }
    }
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    modifier: Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

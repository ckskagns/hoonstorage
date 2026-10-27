package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.BuildConfig
import com.uridul.app.UpdateState
import com.uridul.app.data.Character

/** 새 버전 안내 → (설치 허용 안내) → 내려받는 중 → 안드로이드 설치 화면 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onOpenPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> Unit

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { CharacterPair(Character.DRAGON, Character.BUNNY, 44.dp) },
            title = { Text("새 버전이 있어요") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${BuildConfig.VERSION_NAME} → ${state.release.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.release.notes.isNotBlank()) {
                        Text(state.release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "업데이트해도 일정과 기록은 그대로 남아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onUpdate) { Text("업데이트") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("나중에") } },
        )

        is UpdateState.NeedPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("설치 허용이 한 번 필요해요") },
            text = {
                Text(
                    "다음 화면에서 '이 출처 허용'을 켠 뒤 뒤로 돌아오면 업데이트가 이어서 진행돼요.\n" +
                        "우리둘 앱에만 적용되고, 언제든 다시 끌 수 있어요.",
                )
            },
            confirmButton = { TextButton(onClick = onOpenPermission) { Text("허용하러 가기") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("나중에") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("내려받는 중…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            confirmButton = {},
        )
    }
}

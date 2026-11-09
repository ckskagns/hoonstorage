package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uridul.app.ai.AiConfig
import com.uridul.app.ai.AiConfigStore
import com.uridul.app.ai.AiService
import com.uridul.app.ai.AiReply
import com.uridul.app.ai.ClaudeClient
import kotlinx.coroutines.launch

/**
 * 관리자 설정 (커플 공간을 만든 사람 = 후이 폰에만 메뉴가 보인다. 서버 규칙도 관리자만 바꿀 수 있다).
 * 지금은 AI 비서(Claude): 켜기/끄기, API 키 넣기·보기·지우기, 연결 시험. 앞으로 관리자 기능은 여기에 모은다.
 */
@Composable
internal fun AdminDialog(coupleId: String, myUid: String, onDismiss: () -> Unit) {
    val store = remember { AiConfigStore() }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<AiConfig?>(null) }
    var input by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    suspend fun reload() { config = store.load(coupleId) }
    LaunchedEffect(coupleId) {
        reload()
        // 예전 제미나이 설정(키 포함)은 Claude 로 바꾸면서 쓰지 않으므로 지운다
        store.removeOldGemini(coupleId)
    }

    fun run(block: suspend () -> String?) {
        busy = true
        result = null
        scope.launch {
            result = runCatching { block() }.getOrElse { "⚠️ 저장하지 못했어요: ${it.message?.take(80)}" }
            runCatching { reload() }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관리자 설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🤖 AI 비서 (Claude)", style = MaterialTheme.typography.titleSmall)
                val c = config
                if (c == null) {
                    CircularProgressIndicator()
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (c.enabled) "AI 기능 켜짐" else "AI 기능 꺼짐", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (c.enabled) "두 사람 모두 AI 비서를 쓸 수 있어요" else "앱이 Claude 를 부르지 않아 요금이 생기지 않아요. 나나 폰은 키도 받을 수 없어요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = c.enabled,
                            enabled = !busy,
                            onCheckedChange = { on ->
                                run {
                                    store.setEnabled(coupleId, myUid, on)
                                    if (on) "AI 기능을 켰어요" else "AI 기능을 껐어요"
                                }
                            },
                        )
                    }
                    HorizontalDivider()
                    // 키 상태·보기
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                !c.hasKey -> "Claude API 키: 아직 없음"
                                showKey -> "Claude API 키: ${c.key}"
                                else -> "Claude API 키: ${AiConfig.masked(c.key!!)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (c.hasKey) TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "가리기" else "키 보기") }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim() },
                        label = { Text(if (c.hasKey) "새 키로 바꾸기" else "키 붙여 넣기") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (input.isNotEmpty() && !AiConfig.looksLikeKey(input)) {
                        Text("키 모양이 아니에요 (빈칸 없이 20자 이상)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !busy && AiConfig.looksLikeKey(input),
                            onClick = {
                                val key = input
                                run {
                                    store.saveKey(coupleId, myUid, key, c.enabled)
                                    input = ""
                                    "키를 저장했어요"
                                }
                            },
                        ) { Text("키 저장") }
                        TextButton(
                            enabled = !busy && c.usable,
                            onClick = {
                                run {
                                    when (val r = AiService.ask(coupleId, "우리둘 앱 연결 시험이야. 한국어로 짧게 한 문장 인사해 줘.")) {
                                        is AiReply.Text -> "✅ 연결됐어요 (${ClaudeClient.MODEL})\nClaude: ${r.text.take(200)}"
                                        is AiReply.Error -> "⚠️ ${r.message}"
                                    }
                                }
                            },
                        ) { Text("연결 시험") }
                        if (c.hasKey) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    run {
                                        store.clearKey(coupleId, myUid)
                                        showKey = false
                                        "키를 지우고 AI 기능을 껐어요"
                                    }
                                },
                            ) { Text("키 지우기", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (!c.enabled && c.hasKey) {
                        Text("연결 시험은 AI 기능을 켠 뒤에 할 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "요금은 Claude 콘솔에 충전한 금액에서 나가요. 자동 충전을 끄면 충전한 금액 이상은 쓰이지 않아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy && config != null) CircularProgressIndicator()
                result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

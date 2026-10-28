package com.uridul.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.CalendarEvent
import kotlinx.coroutines.delay

/** "되돌리기" 안내가 떠 있는 시간 */
private const val UNDO_SHOW_MILLIS = 8_000L

/**
 * 일정을 지운 직후 아래에 잠깐 뜨는 안내: 🗑 "저녁 데이트" 일정을 지웠어요 [되돌리기]
 * 시간이 지나면 저절로 사라진다 (메뉴 "최근 지운 일정" 에서 30일 동안 다시 되돌릴 수 있다).
 */
@Composable
fun UndoBar(event: CalendarEvent?, onUndo: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // 사라지는 애니메이션 동안에도 글자가 남아 있도록 마지막 일정을 기억한다
    var last by remember { mutableStateOf(event) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(event) {
        if (event != null) {
            last = event
            delay(UNDO_SHOW_MILLIS)
            dismiss()
        }
    }
    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val shown = event ?: last
        if (shown != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 520.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🗑 \"${shown.title}\" 일정을 지웠어요",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onUndo) {
                        // 어두운 바탕(화이트 모드)에는 연한 분홍, 밝은 바탕(다크 모드)에는 진한 분홍
                        val dark = com.uridul.app.ui.theme.LocalDarkTheme.current
                        Text(
                            "되돌리기",
                            color = if (dark) androidx.compose.ui.graphics.Color(0xFFC03A62) else androidx.compose.ui.graphics.Color(0xFFFFB1C5),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.data.EventColor
import com.uridul.app.data.ON_EVENT_FILL
import com.uridul.app.data.ThemeMode
import com.uridul.app.data.UiSettings

/** 화면 모드 고르기: 폰 설정 따라가기 / 화이트 / 다크 */
@Composable
fun ThemeModeDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("화면 모드") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == current, onClick = { onPick(mode) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "📱 ${mode.label}"
                                ThemeMode.LIGHT -> "☀️ ${mode.label}"
                                ThemeMode.DARK -> "🌙 ${mode.label}"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 일정 막대 색 고르기: 내 일정 / 상대 일정 각각 파스텔 색 중에서 */
@Composable
fun EventColorDialog(
    settings: UiSettings,
    myName: String,
    partnerName: String,
    onPickMine: (EventColor) -> Unit,
    onPickPartner: (EventColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 색") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$myName 일정", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                ColorPalette(selected = settings.mine, onPick = onPickMine)
                Text("$partnerName 일정", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                ColorPalette(selected = settings.partner, onPick = onPickPartner)
                // 미리보기: 달력 막대 모양
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewBar("저녁 데이트", settings.mine, Modifier.weight(1f))
                    PreviewBar("제주도 여행", settings.partner, Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPalette(selected: EventColor, onPick: (EventColor) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EventColor.entries.forEach { c ->
            val isSelected = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c.fill))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color(c.strong) else Color(0x22000000), CircleShape)
                    .selectable(selected = isSelected, onClick = { onPick(c) })
                    .semantics { contentDescription = c.label + if (isSelected) ", 선택됨" else "" },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Text("♥", color = Color(c.strong), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PreviewBar(text: String, color: EventColor, modifier: Modifier) {
    Text(
        text,
        color = Color(ON_EVENT_FILL),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(color.fill))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(18.dp),
    )
}

package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.data.EventColor

/**
 * 일정 색 고르기 (안드로이드 기본 캘린더처럼 일정마다 색).
 * 맨 앞 "기본" = 등록한 사람 색(메뉴 "일정 색"에서 고른 색), 그 뒤로 파스텔 9가지. 옆으로 밀어서 본다.
 */
@Composable
fun EventColorPicker(
    /** 고른 색 (null = 기본) */
    selected: EventColor?,
    /** 기본일 때 보이는 색 (등록한 사람 색) */
    defaultColor: EventColor,
    onPick: (EventColor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "🎨 일정 색 · ${selected?.label ?: "기본 (${defaultColor.label})"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(defaultColor, isSelected = selected == null, label = "기본", description = "기본 색 (${defaultColor.label})") { onPick(null) }
            EventColor.entries.forEach { c ->
                ColorDot(c, isSelected = selected == c, label = null, description = c.label) { onPick(c) }
            }
        }
    }
}

@Composable
private fun ColorDot(color: EventColor, isSelected: Boolean, label: String?, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color.fill))
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color(color.strong) else Color(0x22000000), CircleShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description + if (isSelected) ", 선택됨" else "" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            label != null -> Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color(color.strong),
            )
            isSelected -> Text("♥", color = Color(color.strong), style = MaterialTheme.typography.labelLarge)
        }
    }
}

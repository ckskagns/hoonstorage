package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uridul.app.data.EventCategory

/**
 * 일정 분류 고르기: 우리둘(기본) / 후이 일정 / 나나 일정.
 * 우리둘이 아닌 일정은 후기·추억 모음에 나오지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventCategoryPicker(
    selected: EventCategory,
    label: (EventCategory) -> String,
    onPick: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("📂 분류", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventCategory.entries.forEach { c ->
                FilterChip(
                    selected = c == selected,
                    onClick = { onPick(c) },
                    label = { Text(label(c)) },
                    leadingIcon = if (c == selected) {
                        { Text("✓", fontWeight = FontWeight.Bold) }
                    } else {
                        null
                    },
                )
            }
        }
        if (!selected.together) {
            Text(
                "각자 일정은 후기·추억 모음에 나오지 않아요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.uridul.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.MapApp
import com.uridul.app.data.Place

/**
 * 타임라인 줄 오른쪽의 장소 자리.
 *  - 장소가 없으면 [장소 찾기] 버튼 → 지도 앱 고르기
 *  - 장소가 있으면 [📍 장소명] 태그 → 누르면 그 지도 앱에서 바로 열림 (onRemove 가 있으면 ✕ 로 빼기)
 */
@Composable
fun PlaceTag(
    place: Place?,
    onFind: () -> Unit,
    onOpen: (Place) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    if (place == null) {
        AssistChip(
            onClick = onFind,
            label = { Text("📍 장소 찾기") },
            modifier = modifier,
        )
    } else {
        AssistChip(
            onClick = { onOpen(place) },
            label = {
                Text(
                    "📍 ${place.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = onRemove?.let { remove ->
                {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "장소 빼기",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = remove),
                    )
                }
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            border = null,
            modifier = modifier
                .widthIn(max = 200.dp)
                .semantics { contentDescription = "${place.name}, ${(place.app ?: MapApp.NAVER).label}에서 열기" },
        )
    }
}

/** 어느 지도 앱에서 장소를 찾을지 (기본값: 네이버 지도) */
@Composable
fun MapChooserDialog(onDismiss: () -> Unit, onChoose: (MapApp) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(MapApp.NAVER.key) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어느 지도에서 찾을까요?") },
        text = {
            Column {
                MapApp.entries.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == app.key, onClick = { selected = app.key })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == app.key, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(if (app == MapApp.NAVER) "${app.label} (기본)" else app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "지도 앱에서 장소를 고른 뒤 공유 → 우리둘 을 누르면 장소가 들어가요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(MapApp.from(selected) ?: MapApp.NAVER) }) { Text("찾기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

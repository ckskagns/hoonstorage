package com.uridul.app.ui

import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.layout.fillMaxSize
import com.uridul.app.widget.HeartDesign
import com.uridul.app.widget.HeartFace
import com.uridul.app.widget.heartViews
import java.time.LocalDate
import com.uridul.app.HeartTarget

/** 하트 위젯 디자인 고르기 화면 (HeartWidgetConfigActivity) */
@Composable
internal fun HeartDesignScreen(
    targets: List<HeartTarget>,
    initialTarget: Int?,
    designOf: (Int?) -> HeartDesign,
    face: HeartFace?,
    placedCount: Int?,
    onApply: (HeartDesign, Int?) -> Unit,
    onCancel: () -> Unit,
) {
    /** -1 = 모든 하트 위젯 */
    var targetId by rememberSaveable { mutableStateOf(initialTarget ?: -1) }
    val target = targetId.takeIf { it != -1 }
    val current = remember(targets) { targets.associate { it.appWidgetId to designOf(it.appWidgetId) } }
    var selectedKey by rememberSaveable { mutableStateOf(designOf(target).key) }
    val selected = HeartDesign.from(selectedKey)
    var dark by rememberSaveable { mutableStateOf(true) }
    fun pickTarget(id: Int?) {
        targetId = id ?: -1
        selectedKey = designOf(id).key
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text("하트 위젯 디자인", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        placedCount == 0 -> "아직 놓인 하트 위젯이 없어요. 고른 모양은 새로 놓는 하트 위젯에 쓰여요"
                        target == null -> "모든 하트 위젯(홈·잠금화면)에 적용돼요. 위젯 하나만 바꾸려면 아래에서 골라 주세요. 이 폰에만 저장돼요"
                        else -> "고른 위젯 하나만 바뀌어요. 괄호 안 숫자는 지금 쓰는 모양 번호예요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = target == null, onClick = { pickTarget(null) }, label = { Text("모든 하트 위젯") })
                    targets.forEach { t ->
                        val now = current[t.appWidgetId] ?: designOf(t.appWidgetId)
                        FilterChip(selected = target == t.appWidgetId, onClick = { pickTarget(t.appWidgetId) }, label = { Text("${t.label} (${now.number})") })
                    }
                }
                FilterChip(
                    selected = dark,
                    onClick = { dark = !dark },
                    label = { Text(if (dark) "미리보기: 어두운 배경" else "미리보기: 밝은 배경") },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (face == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("불러오는 중…", Modifier.padding(16.dp)) }
                } else {
                    items(HeartDesign.entries, key = { it.key }) { d ->
                        DesignTile(d, face, selected = d == selected, dark = dark, onClick = { selectedKey = d.key })
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onCancel) { Text("닫기") }
                Button(onClick = { onApply(selected, target) }) { Text("적용") }
            }
        }
    }
}

private val DarkWall = Brush.linearGradient(listOf(Color(0xFF3D3757), Color(0xFF1D2134)))
private val LightWall = Brush.linearGradient(listOf(Color(0xFFF7DCCF), Color(0xFFCDDFF2)))

@Composable
private fun DesignTile(design: HeartDesign, face: HeartFace, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .border(if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(onClick = onClick),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(104.dp).background(if (dark) DarkWall else LightWall),
            contentAlignment = Alignment.Center,
        ) {
            val size = DpSize(maxWidth, maxHeight)
            // 크기·디자인이 바뀔 때만 새로 띄운다 (실제 위젯 그림 그대로라 하트가 움직인다)
            key(design, size, face) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            val views = heartViews(ctx, design, face, LocalDate.now(), size, clickable = false)
                            addView(views.apply(ctx, this))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(design.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

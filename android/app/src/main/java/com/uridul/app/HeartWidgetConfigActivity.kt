package com.uridul.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.uridul.app.data.WidgetStore
import com.uridul.app.ui.theme.UridulTheme
import com.uridul.app.widget.HeartDesign
import com.uridul.app.widget.HeartDesignStore
import com.uridul.app.widget.HeartFace
import com.uridul.app.widget.HeartRedraw
import com.uridul.app.widget.HeartWidget
import com.uridul.app.widget.HeartWidgetReceiver
import com.uridul.app.widget.heartViews
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 하트 위젯 디자인 고르기 (지금 하트 + 14종).
 *  - 위젯을 길게 눌러 "설정" 으로 열면: 그 위젯만, 또는 모든 하트 위젯에 적용
 *  - 앱 메뉴 "하트 위젯 디자인" 으로 열면: 모든 하트 위젯 (새로 놓는 위젯도)
 * 미리보기는 실제 위젯 그림을 그대로 띄워서 하트도 똑같이 움직인다.
 */
class HeartWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val forWidget = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (forWidget) {
            // 우리 하트 위젯이 아닌 ID 로 열리면 아무것도 하지 않는다
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            if (info == null || info.provider != ComponentName(this, HeartWidgetReceiver::class.java)) {
                finish()
                return
            }
        }
        val initial = if (forWidget) HeartDesignStore.forWidget(this, appWidgetId) else HeartDesignStore.forAll(this)

        setContent {
            UridulTheme {
                var face by remember { mutableStateOf<HeartFace?>(null) }
                var placed by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    face = HeartFace.of(WidgetStore.load(this@HeartWidgetConfigActivity))
                    placed = runCatching { GlanceAppWidgetManager(this@HeartWidgetConfigActivity).getGlanceIds(HeartWidget::class.java).size }.getOrNull()
                }
                HeartDesignScreen(
                    forWidget = forWidget,
                    initial = initial,
                    face = face,
                    placedCount = placed,
                    onApply = ::apply,
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun apply(design: HeartDesign, onlyThis: Boolean) {
        if (onlyThis) HeartDesignStore.setWidget(this, appWidgetId, design) else HeartDesignStore.setAll(this, design)
        lifecycleScope.launch {
            runCatching { HeartRedraw.updateChanged(applicationContext) }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            }
            finish()
        }
    }
}

@Composable
private fun HeartDesignScreen(
    forWidget: Boolean,
    initial: HeartDesign,
    face: HeartFace?,
    placedCount: Int?,
    onApply: (HeartDesign, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf(initial.key) }
    val selected = HeartDesign.from(selectedKey)
    var onlyThis by rememberSaveable { mutableStateOf(forWidget) }
    var dark by rememberSaveable { mutableStateOf(true) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text("하트 위젯 디자인", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        forWidget -> "고른 모양은 이 폰에만 저장돼요. 언제든 위젯을 길게 눌러 \"설정\" 이나 앱 메뉴에서 바꿀 수 있어요"
                        placedCount == 0 -> "아직 놓인 하트 위젯이 없어요. 고른 모양은 새로 놓는 하트 위젯에 쓰여요"
                        else -> "모든 하트 위젯(홈·잠금 화면)에 적용돼요. 이 폰에만 저장돼요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (forWidget) {
                        FilterChip(selected = onlyThis, onClick = { onlyThis = true }, label = { Text("이 위젯만") })
                        FilterChip(selected = !onlyThis, onClick = { onlyThis = false }, label = { Text("모든 하트 위젯") })
                    }
                    FilterChip(selected = dark, onClick = { dark = !dark }, label = { Text(if (dark) "어두운 배경" else "밝은 배경") })
                }
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
                Button(onClick = { onApply(selected, forWidget && onlyThis) }) { Text("적용") }
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

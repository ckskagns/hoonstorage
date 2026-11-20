package com.uridul.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.PhotoMeta
import kotlinx.coroutines.launch
import java.time.LocalDate

// 사진 꾸미기: 크게 보기의 🖼 → 폴라로이드·필름 테두리와 날짜 도장을 얹어 폰 사진에 저장 (원본·서버 사진은 그대로).

/** 테두리 모양 */
enum class PhotoFrame(val label: String) { POLAROID("📸 폴라로이드"), FILM("🎞 필름"), NONE("액자 없음") }

/** 날짜 도장 글자: 옛날 필름 카메라처럼 "'26 11 14" */
fun dateStamp(date: LocalDate): String = "'%02d %d %d".format(date.year % 100, date.monthValue, date.dayOfMonth)

@Composable
fun PhotoFrameDialog(photo: PhotoMeta, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = rememberSharpPhoto(photo)
    var frame by remember { mutableStateOf(PhotoFrame.POLAROID) }
    var stamp by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF1C1B1F)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("🖼 사진 꾸미기", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoFrame.entries.forEach { f ->
                        FilterChip(selected = frame == f, onClick = { frame = f }, label = { Text(f.label) })
                    }
                    FilterChip(selected = stamp, onClick = { stamp = !stamp }, label = { Text("📅 날짜 도장") })
                }
                Box(
                    Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                ) {
                    FramedPhoto(view.bitmap, frame, if (stamp) dateStamp(photo.date) else null, title, photo.date)
                }
                Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("닫기", color = Color.White) }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                val ok = runCatching {
                                    GallerySaver.save(context, layer.toImageBitmap().asAndroidBitmap(), "우리둘_${photo.date}_${photo.id.take(6)}")
                                }.getOrDefault(false)
                                saving = false
                                android.widget.Toast.makeText(context, if (ok) "사진(Pictures/우리둘)에 저장했어요" else "저장하지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        // 큰 사진을 받기 전에는 저장하지 않는다 (작은 사진으로 저장되지 않게)
                        enabled = !saving && view.sharp,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (saving) "저장 중…" else if (!view.sharp) "사진 받는 중…" else "📥 사진에 저장") }
                }
                Text(
                    "원본 사진과 일기의 사진은 그대로예요. 꾸민 사진만 폰 사진첩에 새로 저장돼요.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private val StampOrange = Color(0xFFFF8A1E)

@Composable
private fun FramedPhoto(bitmap: ImageBitmap?, frame: PhotoFrame, stamp: String?, title: String, date: LocalDate) {
    val photo: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Color(0xFF2B2B2B))) {
            bitmap?.let { Image(it, contentDescription = "꾸밀 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (stamp != null) Text(
                stamp,
                style = TextStyle(
                    color = StampOrange,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(color = Color(0xAA7A2E00), blurRadius = 6f),
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
    }
    when (frame) {
        PhotoFrame.POLAROID -> Column(
            Modifier.fillMaxWidth().background(Color(0xFFFBFAF6)).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        ) {
            photo(Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                title.ifBlank { "우리 데이트" },
                style = TextStyle(color = Color(0xFF3A3A3A), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
            Text(
                "${date.year}. ${date.monthValue}. ${date.dayOfMonth} · 우리둘",
                style = TextStyle(color = Color(0xFF8A8A8A), fontSize = 12.sp),
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
        PhotoFrame.FILM -> Column(Modifier.fillMaxWidth().background(Color(0xFF141414)).padding(horizontal = 10.dp)) {
            FilmHoles()
            photo(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(2.dp)))
            FilmHoles()
        }
        PhotoFrame.NONE -> photo(Modifier.fillMaxWidth().aspectRatio(4f / 5f))
    }
}

/** 필름 위아래 구멍 줄 */
@Composable
private fun FilmHoles() {
    Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        repeat(12) { Box(Modifier.size(width = 10.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE8E2D6))) }
    }
}

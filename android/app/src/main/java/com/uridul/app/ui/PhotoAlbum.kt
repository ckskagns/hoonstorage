package com.uridul.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.PhotoMeta
import com.uridul.app.domain.PhotoQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** 사진첩을 쓰는 곳(일정 카드·추억 슬라이드)이 함께 쓰는 값. 메인 화면에서 한 번 넣어 준다 */
class PhotoHooks(
    val album: (CalendarEvent) -> Flow<List<PhotoMeta>> = { flowOf(emptyList()) },
    /** 이번 주 올린 장수 (두 사람 합계) */
    val weekUsed: Int = 0,
    val uploading: Boolean = false,
    val onUpload: (event: CalendarEvent, uris: List<Uri>, inAlbum: Int) -> Unit = { _, _, _ -> },
    val onDelete: (PhotoMeta) -> Unit = {},
    val loadFull: suspend (id: String) -> ByteArray? = { null },
    /** 추억 슬라이드 배경 사진 (일정ID_날짜 → 작은 사진) */
    val memoryThumbs: Map<String, ByteArray> = emptyMap(),
)

val LocalPhotos = compositionLocalOf { PhotoHooks() }

/** JPEG 바이트 → 화면 그림 (같은 사진은 다시 풀지 않음) */
@Composable
fun rememberPhotoBitmap(key: String, bytes: ByteArray?): ImageBitmap? =
    remember(key, bytes?.size) { bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }

/**
 * 일정 카드의 사진첩 줄: [사진들…] [+ 사진]  ·  이번 주 7/25장
 * 이번 주 장수를 다 쓰면 + 가 꺼지고, 이번 주 사진을 지우면 다시 올릴 수 있다고 알려준다.
 */
@Composable
fun AlbumStrip(event: CalendarEvent, couple: Couple, myUid: String) {
    val hooks = LocalPhotos.current
    val photos by remember(event.id, event.date) { hooks.album(event) }.collectAsState(initial = emptyList())
    var viewing by remember { mutableStateOf<Int?>(null) }
    val room = PhotoQuota.remaining(hooks.weekUsed, photos.size)
    val weekFull = hooks.weekUsed >= PhotoQuota.WEEKLY_LIMIT
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoQuota.PER_EVENT_MAX)) { uris ->
        if (uris.isNotEmpty()) hooks.onUpload(event, uris, photos.size)
    }
    Column(Modifier.padding(start = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📷 사진첩", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // 한 주 업로드 제한을 조그맣게
            Text(
                "이번 주 ${hooks.weekUsed}/${PhotoQuota.WEEKLY_LIMIT}장",
                style = MaterialTheme.typography.labelSmall,
                color = if (weekFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
                val bmp = rememberPhotoBitmap(p.id, p.thumb)
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { viewing = i }
                        .semantics { contentDescription = "사진 ${i + 1}, 눌러서 크게 보기" },
                ) {
                    bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
            item {
                val enabled = room > 0 && !hooks.uploading
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.8f else 0.3f), RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .semantics { contentDescription = if (enabled) "사진 더하기, ${room}장 더 올릴 수 있어요" else "사진을 더 올릴 수 없어요" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hooks.uploading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            "+",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
        if (weekFull) {
            Text(
                "이번 주 ${PhotoQuota.WEEKLY_LIMIT}장을 다 올렸어요 · 이번 주에 올린 사진을 지우면 다시 올릴 수 있어요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (photos.size >= PhotoQuota.PER_EVENT_MAX) {
            Text("이 일정에는 ${PhotoQuota.PER_EVENT_MAX}장까지 올릴 수 있어요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    viewing?.let { start ->
        PhotoViewerDialog(
            photos = photos,
            start = start.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
            couple = couple,
            myUid = myUid,
            onDelete = { p -> hooks.onDelete(p) },
            onDismiss = { viewing = null },
        )
    }
}

/** 크게 보기: 좌우로 넘기기, 누가 올렸는지, 지우기 */
@Composable
fun PhotoViewerDialog(
    photos: List<PhotoMeta>,
    start: Int,
    couple: Couple,
    myUid: String,
    onDelete: (PhotoMeta) -> Unit,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val hooks = LocalPhotos.current
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var deleting by remember { mutableStateOf<PhotoMeta?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val meta = photos[page]
                // 큰 사진은 볼 때 받아온다 (받는 동안은 작은 사진)
                val full by produceState<ImageBitmap?>(null, meta.id) {
                    value = withContext(Dispatchers.Default) {
                        hooks.loadFull(meta.id)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
                    }
                }
                val thumb = rememberPhotoBitmap(meta.id, meta.thumb)
                (full ?: thumb)?.let { Image(it, contentDescription = "사진 ${page + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                if (full == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            val current = photos.getOrNull(pager.currentPage)
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(4.dp).background(Color(0x66000000), RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    current?.let { p ->
                        val who = if (p.uploadedBy == myUid) couple.myName(myUid) else (couple.names[p.uploadedBy] ?: "상대")
                        Text("${who}가 올림 · ${p.date.monthValue}월 ${p.date.dayOfMonth}일", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { deleting = current }) { Icon(Icons.Filled.Delete, contentDescription = "사진 지우기", tint = Color.White) }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
    deleting?.let { p ->
        val thisWeek = p.week == PhotoQuota.weekKey(java.time.LocalDate.now())
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("사진을 지울까요?") },
            text = {
                Text(
                    "상대 폰에서도 함께 사라져요." +
                        if (thisWeek) "\n이번 주에 올린 사진이라, 지우면 이번 주에 한 장 더 올릴 수 있어요." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(p)
                    if (photos.size <= 1) onDismiss()
                }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

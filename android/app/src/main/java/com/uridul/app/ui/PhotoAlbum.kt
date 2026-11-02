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
import androidx.compose.ui.graphics.asAndroidBitmap
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
    /** 이번 주 장수 다시 세기 (사진첩 사진이 바뀔 때) */
    val refreshWeek: () -> Unit = {},
    /** 추억 슬라이드 배경 사진 (일정ID_날짜 → 작은 사진) */
    val memoryThumbs: Map<String, ByteArray> = emptyMap(),
)

val LocalPhotos = compositionLocalOf { PhotoHooks() }

/** 풀어 둔 작은 사진 (최대 16MB, 오래 안 본 것부터 버림) */
private val photoCache = object : android.util.LruCache<String, ImageBitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = (value.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1)
}

/**
 * JPEG 바이트 → 화면 그림. 화면을 그리는 줄에서 풀지 않고 뒤에서 풀어서 넘길 때 끊기지 않게 하고,
 * 한 번 푼 사진은 기억해 둔다.
 */
@Composable
fun rememberPhotoBitmap(key: String, bytes: ByteArray?): ImageBitmap? {
    val cacheKey = "$key:${bytes?.size}"
    val state = produceState(initialValue = bytes?.let { photoCache.get(cacheKey) }, cacheKey) {
        if (value == null && bytes != null) {
            value = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }?.also { photoCache.put(cacheKey, it) }
        }
    }
    return state.value
}

/**
 * 일정 카드의 사진첩 줄: [사진들…] [+ 사진]  ·  이번 주 7/25장
 * 이번 주 장수를 다 쓰면 + 가 꺼지고, 이번 주 사진을 지우면 다시 올릴 수 있다고 알려준다.
 */
@Composable
fun AlbumStrip(
    event: CalendarEvent,
    couple: Couple,
    myUid: String,
    startPadding: androidx.compose.ui.unit.Dp = 16.dp,
    thumbSize: androidx.compose.ui.unit.Dp = 64.dp,
    modifier: Modifier = Modifier,
    /** 크게 보기에서 후기 줄을 보일지 (각자 일정·후기 창 안이면 false) */
    allowReview: Boolean = event.category.together,
) {
    val hooks = LocalPhotos.current
    val photos by remember(event.id, event.date) { hooks.album(event) }.collectAsState(initial = emptyList())
    // 사진이 늘거나 줄면(상대가 올린 것 포함) 이번 주 장수를 다시 센다
    LaunchedEffect(photos.map { it.id }) { hooks.refreshWeek() }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val room = PhotoQuota.remaining(hooks.weekUsed, photos.size)
    val weekFull = hooks.weekUsed >= PhotoQuota.WEEKLY_LIMIT
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoQuota.PER_EVENT_MAX)) { uris ->
        if (uris.isNotEmpty()) hooks.onUpload(event, uris, photos.size)
    }
    Column(modifier.padding(start = startPadding, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        .size(thumbSize)
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
                        .size(thumbSize)
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
        FeedViewerDialog(
            post = FeedPost.of(event),
            couple = couple,
            myUid = myUid,
            startPage = start.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
            onDismiss = { viewing = null },
            allowReview = allowReview,
        )
    }
}

package com.uridul.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Couple
import com.uridul.app.data.DateReview
import com.uridul.app.data.MapApp
import com.uridul.app.data.PhotoMeta
import com.uridul.app.data.Place
import com.uridul.app.data.ReviewEntry
import com.uridul.app.domain.Memories
import com.uridul.app.domain.PhotoQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 크게 보기 팝업 (인스타그램 사진 보기): 일기장 게시물·홈 추억 슬라이드·캘린더 사진첩이 함께 쓴다.

/**
 * 크게 보기 (인스타그램 사진 보기): 검은 화면에 사진을 크게, 옆으로 넘기며 그 일정의 사진 모두.
 * 사진 밑에는 두 사람의 후기를 한 줄씩 (길면 "…", 누르면 글 전체를 크게). 두 손가락으로 확대.
 */
@Composable
fun FeedViewerDialog(
    post: FeedPost,
    couple: Couple,
    myUid: String,
    startPage: Int = 0,
    onDismiss: () -> Unit,
    /** 후기 보기·쓰기 줄 (각자 일정이거나 이미 후기 창 안이면 false) */
    allowReview: Boolean = true,
) {
    val hooks = LocalPhotos.current
    val reviews = LocalReviews.current
    val review = reviews.reviews[post.reviewId]
    val photos by remember(post.reviewId) { hooks.album(post.asEvent()) }.collectAsState(initial = emptyList())
    val pager = rememberPagerState(initialPage = startPage) { photos.size.coerceAtLeast(1) }
    var deleting by remember { mutableStateOf<PhotoMeta?>(null) }
    var writing by remember { mutableStateOf(false) }
    val addPhoto = rememberAddPhoto(post, photos)
    val mine = review?.entries?.get(myUid)
    // 사진이 늦게 불러와져도 처음 고른 장으로 (한 번만: 그 뒤 사진 수가 바뀌어도 보던 장 그대로)
    var jumped by remember { mutableStateOf(startPage == 0) }
    LaunchedEffect(photos.size) {
        if (!jumped && startPage < photos.size) {
            pager.scrollToPage(startPage)
            jumped = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // 위: 닫기 · 제목/날짜 · 지우기
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(post.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = buildString {
                            append(post.date.format(KoreanMonthDay))
                            post.place?.let { append(" · ${it.name}") }
                            photos.getOrNull(pager.currentPage)?.let { p ->
                                val who = if (p.uploadedBy == myUid) couple.myName(myUid) else (couple.names[p.uploadedBy] ?: "상대")
                                append(" · ${who}가 올림")
                            }
                        }
                        Text(sub, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (photos.isNotEmpty()) {
                        IconButton(onClick = { deleting = photos.getOrNull(pager.currentPage) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "사진 지우기", tint = Color.White)
                        }
                    }
                }

                // 가운데: 사진 크게 (옆으로 넘기기)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (photos.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PostArt(post, Modifier.fillMaxWidth().aspectRatio(1f))
                            if (addPhoto != null) {
                                TextButton(onClick = addPhoto) { Text("📷 이 날 사진 올리기", color = Color.White) }
                            }
                        }
                    } else {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            val meta = photos.getOrNull(page) ?: return@HorizontalPager
                            // 큰 사진은 볼 때 받아온다 (받는 동안은 작은 사진)
                            val full = rememberFullPhoto(meta)
                            val thumb = rememberPhotoBitmap(meta.id, meta.thumb)
                            Box(Modifier.fillMaxSize().pinchToZoom(), contentAlignment = Alignment.Center) {
                                (full ?: thumb)?.let { Image(it, contentDescription = "사진 ${page + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                                if (full == null) CircularProgressIndicator(color = Color.White)
                            }
                        }
                        PageBadge(pager, photos.size, Modifier.align(Alignment.TopEnd).padding(12.dp))
                    }
                }

                // 아래: 점 · 후기 한 줄씩 (누르면 크게)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) { PageDots(pager, photos.size, onDark = true) }
                    if (allowReview) review?.entries?.entries?.sortedBy { if (it.key == myUid) 0 else 1 }?.forEach { (uid, e) ->
                        ReviewComment(uid, e, couple, myUid, onDark = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (allowReview) Text(
                            if (mine == null) "💗 내 후기 남기기…" else "내 후기 고치기",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { writing = true }.padding(vertical = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        if (photos.isNotEmpty() && addPhoto != null) {
                            Text(
                                "📷 사진 더하기",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { addPhoto() }.padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { p ->
        PhotoDeleteDialog(p, onConfirm = { deleting = null; hooks.onDelete(p) }, onDismiss = { deleting = null })
    }
    if (writing) ReviewWriter(post, couple, myUid) { writing = false }
}

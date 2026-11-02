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

// 일기장을 인스타그램 피드처럼: 게시물(머리글 · 옆으로 넘기는 사진 · 하트/후기/사진 줄 · 한 줄 후기 · 날짜)과
// 사진을 누르면 뜨는 크게 보기(사진 넘기기 + 사진 밑 한 줄 후기, 누르면 크게).

/** 일기장 게시물 하나 = 일정 하루 (사진첩·후기 모두 "일정 ID + 날짜" 로 찾는다) */
@Immutable
data class FeedPost(val eventId: String, val date: LocalDate, val title: String, val place: Place?) {
    val reviewId: String get() = DateReview.idOf(eventId, date)

    /** 사진첩·후기 창에 넘길 일정 모양 */
    fun asEvent(): CalendarEvent = CalendarEvent(eventId, title, date, date, null, "", "", place = place)

    /** 화면을 돌리거나 접어도 남도록 글자로 */
    fun encode(): String = JSONObject()
        .put("e", eventId).put("d", date.toString()).put("t", title)
        .put("pn", place?.name ?: JSONObject.NULL).put("pu", place?.url ?: JSONObject.NULL).put("pa", place?.app?.key ?: JSONObject.NULL)
        .toString()

    companion object {
        fun of(r: DateReview) = FeedPost(r.eventId, r.date, r.title, r.place)
        fun of(e: CalendarEvent) = FeedPost(e.id, e.date, e.title, e.place ?: e.slots.firstOrNull { it.place != null }?.place)
        fun decode(raw: String): FeedPost? = if (raw.isEmpty()) null else runCatching {
            val o = JSONObject(raw)
            val place = if (o.isNull("pn")) null else Place(
                o.getString("pn"),
                if (o.isNull("pu")) null else o.getString("pu"),
                MapApp.from(if (o.isNull("pa")) null else o.getString("pa")),
            )
            FeedPost(o.getString("e"), LocalDate.parse(o.getString("d")), o.getString("t"), place)
        }.getOrNull()
    }
}

/** "오늘", "어제", "3일 전", 일주일이 넘으면 "11월 2일" (작년 이전이면 연도까지) */
internal fun feedDateText(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> "오늘"
        days == 1L -> "어제"
        days in 2..6 -> "${days}일 전"
        date.year == today.year -> "${date.monthValue}월 ${date.dayOfMonth}일"
        else -> "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
    }
}

/**
 * 인스타그램처럼 두 손가락으로 벌리면 사진이 커지고, 손을 떼면 제자리로 돌아온다.
 * 한 손가락 손짓(옆으로 넘기기·누르기)은 그대로 아래로 내려간다.
 */
internal fun Modifier.pinchToZoom(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    this
        .zIndex(if (scale.value > 1.01f) 1f else 0f)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            translationX = pan.value.x
            translationY = pan.value.y
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zooming = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        zooming = true
                        val zoom = event.calculateZoom()
                        val move = event.calculatePan()
                        scope.launch {
                            scale.snapTo((scale.value * zoom).coerceIn(1f, 4f))
                            pan.snapTo(pan.value + move)
                        }
                    }
                    // 확대를 시작했으면 손을 뗄 때까지 넘기기가 따라 움직이지 않게
                    if (zooming) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                if (zooming) {
                    scope.launch { scale.animateTo(1f) }
                    scope.launch { pan.animateTo(Offset.Zero) }
                }
            }
        }
}

/** 사진이 없는 게시물의 그림: 일정 분위기 그림 + 제목 */
@Composable
private fun PostArt(post: FeedPost, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Memories.emojiFor(post.asEvent()), style = MaterialTheme.typography.displayLarge)
            Text(
                post.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** 사진 아래 점: 지금 보는 장은 진하게 (인스타 넘기기 점) */
@Composable
private fun PageDots(pager: PagerState, count: Int, onDark: Boolean) {
    if (count < 2) return
    val on = if (onDark) Color.White else MaterialTheme.colorScheme.primary
    val off = if (onDark) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val current = i == pager.currentPage
            Box(Modifier.size(if (current) 7.dp else 6.dp).background(if (current) on else off, CircleShape))
        }
    }
}

/** "3/5" 표시 (사진 오른쪽 위) */
@Composable
private fun PageBadge(pager: PagerState, count: Int, modifier: Modifier) {
    if (count < 2) return
    Text(
        "${pager.currentPage + 1}/$count",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * 후기 한 줄 (인스타 댓글처럼 "이름 글…"). 길면 한 줄에서 "…" 로 자르고, 누르면 글 전체를 크게 보여준다.
 */
@Composable
internal fun ReviewComment(uid: String, entry: ReviewEntry, couple: Couple, myUid: String, onDark: Boolean = false) {
    var expanded by remember(entry.text) { mutableStateOf(false) }
    val who = if (uid == myUid) couple.myName(myUid) else (couple.names[uid] ?: couple.partnerName(myUid) ?: "상대")
    val ink = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val heart = if (onDark) Color(0xFFFF6B8A) else MaterialTheme.colorScheme.error
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(who) }
        append(" ")
        withStyle(SpanStyle(color = heart, fontSize = 12.sp, fontWeight = FontWeight.Bold)) { append("♥${entry.hearts}") }
        append("  ")
        append(entry.text.ifBlank { "(하트만 남겼어요)" })
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (expanded) "접기" else "글 크게 보기") { expanded = !expanded }
            .padding(vertical = 4.dp)
            .animateContentSize(),
        verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
    ) {
        CharacterAvatar(couple.characterOf(uid), if (expanded) 32.dp else 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = ink,
            style = if (expanded) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 이 게시물에 사진을 더 올릴 수 있으면 사진 고르기를 여는 함수, 아니면 null (이번 주 25장 / 일정당 10장) */
@Composable
private fun rememberAddPhoto(post: FeedPost, photos: List<PhotoMeta>): (() -> Unit)? {
    val hooks = LocalPhotos.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoQuota.PER_EVENT_MAX)) { uris ->
        if (uris.isNotEmpty()) hooks.onUpload(post.asEvent(), uris, photos.size)
    }
    val room = PhotoQuota.remaining(hooks.weekUsed, photos.size)
    return if (room > 0 && !hooks.uploading) {
        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    } else {
        null
    }
}

/**
 * 일기장 게시물 (인스타그램 피드 한 칸).
 *  - 머리글: 두 캐릭터 · 일정 제목 · 장소 · 언제 (누르면 그 날 하루 페이지)
 *  - 사진: 옆으로 넘기기, 오른쪽 위 "1/3", 아래 점, 두 손가락으로 확대 / 누르면 크게 보기
 *  - 줄: ♥ 하트 평균 · 💬 후기 쓰기 · 📷 사진 올리기 · 이번 주 사진 수
 *  - 두 사람 후기 한 줄씩 (길면 "…", 누르면 크게), 날짜
 */
@Composable
fun DiaryPost(
    post: FeedPost,
    review: DateReview?,
    couple: Couple,
    myUid: String,
    onOpenDay: () -> Unit,
    onOpenViewer: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hooks = LocalPhotos.current
    val reviews = LocalReviews.current
    val photos by remember(post.reviewId) { hooks.album(post.asEvent()) }.collectAsState(initial = emptyList())
    LaunchedEffect(photos.map { it.id }) { hooks.refreshWeek() }
    val pager = rememberPagerState { photos.size.coerceAtLeast(1) }
    val addPhoto = rememberAddPhoto(post, photos)
    var writing by remember { mutableStateOf(false) }
    val mine = review?.entries?.get(myUid)
    val today = rememberToday()

    Column(modifier.fillMaxWidth()) {
        // 머리글
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "그 날 일정 보기", onClick = onOpenDay)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp)) {
                CharacterAvatar(couple.myCharacter(myUid), 30.dp, modifier = Modifier.align(Alignment.TopStart))
                CharacterAvatar(couple.partnerCharacter(myUid), 30.dp, modifier = Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.title.ifBlank { "(제목 없음)" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                post.place?.let {
                    Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(feedDateText(post.date, today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 사진 (정사각형, 옆으로 넘기기)
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (photos.isEmpty()) {
                PostArt(post, Modifier.fillMaxSize().clickable { onOpenViewer(0) })
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    val meta = photos.getOrNull(page) ?: return@HorizontalPager
                    val bmp = rememberPhotoBitmap(meta.id, meta.thumb)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pinchToZoom()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClickLabel = "사진 크게 보기") { onOpenViewer(page) }
                            .semantics { contentDescription = "사진 ${page + 1}/${photos.size}" },
                    ) {
                        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                }
                PageBadge(pager, photos.size, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }

        // 하트 · 후기 · 사진 줄 (+ 아래 점)
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "후기 쓰기") { writing = true }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    review?.takeIf { it.entries.isNotEmpty() }?.let {
                        Spacer(Modifier.width(4.dp))
                        Text("%.1f".format(it.averageHearts), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "💬",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clip(CircleShape).clickable(onClickLabel = "후기 쓰기") { writing = true }.padding(8.dp),
                )
                if (hooks.uploading) {
                    CircularProgressIndicator(Modifier.padding(8.dp).size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "📷",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = addPhoto != null, onClickLabel = "사진 올리기") { addPhoto?.invoke() }
                            .padding(8.dp)
                            .graphicsLayer { alpha = if (addPhoto != null) 1f else 0.35f },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "이번 주 ${hooks.weekUsed}/${PhotoQuota.WEEKLY_LIMIT}장",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hooks.weekUsed >= PhotoQuota.WEEKLY_LIMIT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Box(Modifier.align(Alignment.Center)) { PageDots(pager, photos.size, onDark = false) }
        }

        // 후기 (댓글처럼 한 줄씩, 내 것 먼저)
        Column(Modifier.padding(horizontal = 14.dp)) {
            review?.entries?.entries?.sortedBy { if (it.key == myUid) 0 else 1 }?.forEach { (uid, e) ->
                ReviewComment(uid, e, couple, myUid)
            }
            Text(
                if (mine == null) "💗 내 후기 남기기…" else "내 후기 고치기",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { writing = true }.padding(vertical = 4.dp),
            )
            Text(
                post.date.format(KoreanMonthDay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
        }
    }

    if (writing) {
        ReviewDialog(
            title = post.title,
            event = post.asEvent(),
            couple = couple,
            myUid = myUid,
            existing = mine,
            onSave = { hearts, text -> writing = false; reviews.onSave(post.asEvent(), hearts, text) },
            onDelete = review?.takeIf { mine != null }?.let { r -> { writing = false; reviews.onDelete(r) } },
            onDismiss = { writing = false },
        )
    }
}

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
    // 사진이 늦게 불러와져도 처음 고른 장으로
    LaunchedEffect(photos.size) {
        if (startPage in 1 until photos.size && pager.currentPage == 0) pager.scrollToPage(startPage)
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
                            val full by produceState<ImageBitmap?>(null, meta.id) {
                                value = withContext(Dispatchers.Default) {
                                    hooks.loadFull(meta.id)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
                                }
                            }
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
                    review?.entries?.entries?.sortedBy { if (it.key == myUid) 0 else 1 }?.forEach { (uid, e) ->
                        ReviewComment(uid, e, couple, myUid, onDark = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
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
        val thisWeek = p.week == PhotoQuota.weekKey(LocalDate.now())
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("사진을 지울까요?") },
            text = { Text("상대 폰에서도 함께 사라져요." + if (thisWeek) "\n이번 주에 올린 사진이라, 지우면 이번 주에 한 장 더 올릴 수 있어요." else "") },
            confirmButton = {
                TextButton(onClick = { deleting = null; hooks.onDelete(p) }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
    if (writing) {
        ReviewDialog(
            title = post.title,
            event = post.asEvent(),
            couple = couple,
            myUid = myUid,
            existing = mine,
            onSave = { hearts, text -> writing = false; reviews.onSave(post.asEvent(), hearts, text) },
            onDelete = review?.takeIf { mine != null }?.let { r -> { writing = false; reviews.onDelete(r) } },
            onDismiss = { writing = false },
        )
    }
}

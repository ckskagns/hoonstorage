package com.uridul.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.data.DateReview
import com.uridul.app.domain.MonthRecap
import com.uridul.app.domain.PhotoGrid
import java.time.LocalDate
import java.time.YearMonth

/**
 * 일기장: 인스타그램 피드처럼 위아래로 넘겨 보는 우리 하루들 (최신 날짜부터).
 *  - 게시물마다 일정 제목·장소, 옆으로 넘기는 사진, 하트·후기·사진 줄, 두 사람 후기 한 줄씩
 *  - 사진을 누르면 크게 보기 (홈 추억 슬라이드를 눌러도 여기로 와서 크게 보기가 열린다)
 *  - 머리글을 누르면 그 날 하루 페이지
 *  - "🔲 모아보기": 사진 있는 날을 달마다 격자로. 넓은 화면(폴드 펼침)은 왼쪽 격자 + 오른쪽 고른 날 게시물
 */
@Composable
fun DiaryContent(
    couple: Couple,
    myUid: String,
    onOpenDay: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
    /** 크게 보기로 열어 둘 게시물 (홈 추억 슬라이드에서 넘어온 경우 등) */
    viewing: FeedPost? = null,
    viewingPage: Int = 0,
    onView: (FeedPost?, Int) -> Unit = { _, _ -> },
    /** 빈 일기장의 [캘린더로 가기] */
    onGoCalendar: () -> Unit = {},
) {
    val reviews = LocalReviews.current.reviews
    // 최신 날짜부터 (같은 날은 제목순), 후기가 남아 있는 날만
    val posts = remember(reviews) {
        reviews.values.filter { it.entries.isNotEmpty() }
            .sortedWith(compareByDescending<DateReview> { it.date }.thenBy { it.title })
            .map(FeedPost::of)
    }
    val today = rememberToday()

    // 위쪽 "11월의 우리" 단추들 (후기·사진이 있는 달) → 한 달 돌아보기
    val photoHooks = LocalPhotos.current
    val dayPhotos = photoHooks.dayPhotos
    val recapMonths = remember(reviews, dayPhotos.keys, today) { MonthRecap.months(reviews.values, dayPhotos.keys, today) }
    var recapText by rememberSaveable { mutableStateOf("") }
    recapText.takeIf { it.isNotEmpty() }?.let { m ->
        MonthRecapDialog(YearMonth.parse(m), couple, myUid) { recapText = "" }
    }

    // 피드 / 모아보기
    var gridMode by rememberSaveable { mutableStateOf(false) }
    // 모아보기: 사귄 달부터 사진 수를 물어 사진 있는 날을 모두 불러 둔다 (사진 달력과 같은 저장본)
    if (gridMode) LaunchedEffect(couple.startDate, today) {
        PhotoGrid.monthsToLoad(couple.startDate, today).forEach { photoHooks.loadMonthPhotos(it) }
    }
    val sections = remember(dayPhotos) { PhotoGrid.sections(dayPhotos.mapValues { it.value.count }) }
    var selectedText by rememberSaveable { mutableStateOf("") }

    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            RecapMonthChips(recapMonths, onOpen = { recapText = it.toString() }, modifier = Modifier.padding(bottom = 4.dp))
            DiaryModeChips(gridMode, onChange = { gridMode = it })
            Text(
                when {
                    gridMode -> "사진 있는 날 ${sections.sumOf { it.days.size }}일 · 누르면 그날 사진을 크게 봐요"
                    posts.isEmpty() -> "지난 일정에 한 줄 후기를 남기면 여기에 차곡차곡 모여요"
                    else -> "하루 ${posts.size}개 · 최근 날짜부터 · 사진을 누르면 크게 보여요"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val gridEmpty: @Composable () -> Unit = {
        CharacterEmpty(couple, myUid, title = "아직 올린 사진이 없어요 📷", body = "일기 게시물의 📷 를 눌러 그날 사진을 올려 보세요.", action = "📰 피드로 가기", onAction = { gridMode = false }, emotion = Emotion.PHOTO)
    }

    // 게시물마다 "이날 데이트 비용" (그 달 가계부를 한 번만 읽는다)
    val dayCosts = rememberDayCosts(couple.id)
    androidx.compose.runtime.CompositionLocalProvider(LocalDayCosts provides dayCosts) {
        if (gridMode) {
            BoxWithConstraints(modifier.fillMaxSize()) {
                val twoPane = maxWidth >= TwoPaneMinWidth
                val selectedDay = selectedText.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)?.takeIf { it in dayPhotos }
                    ?: sections.firstOrNull()?.days?.firstOrNull()
                val pick: (LocalDate, com.uridul.app.data.DayPhoto) -> Unit = { day, p ->
                    if (twoPane) selectedText = day.toString() else onView(postFor(day, p, reviews), 0)
                }
                if (!twoPane) {
                    DiaryPhotoGrid(sections, dayPhotos, null, pick, header, empty = gridEmpty)
                } else {
                    // 폴드 펼침: 왼쪽 격자, 오른쪽 고른 날 게시물
                    Row(Modifier.fillMaxSize()) {
                        DiaryPhotoGrid(sections, dayPhotos, selectedDay, pick, header, Modifier.weight(1f), empty = gridEmpty)
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                            val p = selectedDay?.let { d -> dayPhotos[d]?.let { postFor(d, it, reviews) } }
                            if (p != null) {
                                androidx.compose.runtime.key(p.reviewId) {
                                    DiaryPost(
                                        post = p,
                                        review = reviews[p.reviewId],
                                        couple = couple,
                                        myUid = myUid,
                                        onOpenDay = { onOpenDay(p) },
                                        onOpenViewer = { page -> onView(p, page) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 12.dp)) { header() }
                }
                if (posts.isEmpty()) {
                    item(key = "empty") {
                        CharacterEmpty(
                            couple, myUid,
                            title = "아직 쓴 후기가 없어요 🌷",
                            body = "캘린더에서 지난 일정을 누르고 💗 한 줄 후기 남기기 를 눌러 보세요.\n홈의 추억 슬라이드에서도 바로 쓸 수 있어요.",
                            action = "📅 캘린더로 가기",
                            onAction = onGoCalendar,
                            emotion = Emotion.LETTER,
                        )
                    }
                }
                items(posts, key = { it.reviewId }) { post ->
                    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                        DiaryPost(
                            post = post,
                            review = reviews[post.reviewId],
                            couple = couple,
                            myUid = myUid,
                            onOpenDay = { onOpenDay(post) },
                            onOpenViewer = { page -> onView(post, page) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    viewing?.let { post ->
        FeedViewerDialog(post = post, couple = couple, myUid = myUid, startPage = viewingPage, onDismiss = { onView(null, 0) })
    }
}

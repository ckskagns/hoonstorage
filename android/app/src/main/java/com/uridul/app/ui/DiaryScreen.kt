package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.data.DateReview

/**
 * 일기장: 인스타그램 피드처럼 위아래로 넘겨 보는 우리 하루들 (최신 날짜부터).
 *  - 게시물마다 일정 제목·장소, 옆으로 넘기는 사진, 하트·후기·사진 줄, 두 사람 후기 한 줄씩
 *  - 사진을 누르면 크게 보기 (홈 추억 슬라이드를 눌러도 여기로 와서 크게 보기가 열린다)
 *  - 머리글을 누르면 그 날 하루 페이지
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
) {
    val reviews = LocalReviews.current.reviews
    // 최신 날짜부터 (같은 날은 제목순), 후기가 남아 있는 날만
    val posts = remember(reviews) {
        reviews.values.filter { it.entries.isNotEmpty() }
            .sortedWith(compareByDescending<DateReview> { it.date }.thenBy { it.title })
            .map(FeedPost::of)
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (posts.isEmpty()) "지난 일정에 한 줄 후기를 남기면 여기에 차곡차곡 모여요" else "하루 ${posts.size}개 · 최근 날짜부터 · 사진을 누르면 크게 보여요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (posts.isEmpty()) {
            item(key = "empty") {
                Text(
                    "아직 쓴 후기가 없어요 🌷\n캘린더에서 지난 일정을 누르고\n💗 한 줄 후기 남기기 를 눌러 보세요.\n홈의 추억 슬라이드에서도 바로 쓸 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp),
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

    viewing?.let { post ->
        FeedViewerDialog(post = post, couple = couple, myUid = myUid, startPage = viewingPage, onDismiss = { onView(null, 0) })
    }
}

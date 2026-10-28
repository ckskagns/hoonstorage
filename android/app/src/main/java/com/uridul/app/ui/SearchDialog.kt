package com.uridul.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.CalendarEvent
import com.uridul.app.domain.EventSearch
import com.uridul.app.ui.theme.CoupleColors
import java.time.LocalDate

/**
 * 일정 검색: 제목·장소·메모로 찾기 ("선릉감자탕 언제 갔지?")
 * 결과를 누르면 그 날 하루 페이지가 그 일정을 연 채로 열린다.
 */
@Composable
fun SearchDialog(
    myUid: String,
    load: suspend () -> List<CalendarEvent>?,
    onOpen: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = rememberToday()
    var query by rememberSaveable { mutableStateOf("") }
    /** null = 불러오는 중 */
    var all by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val list = load()
        failed = list == null
        all = list.orEmpty()
    }
    val result = remember(all, query, today) { all?.let { EventSearch.search(it, query, today) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FullScreenDialogBars()
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 6 } + fadeIn(),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기") }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(50) },
                            placeholder = { Text("제목·장소·메모로 찾기") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "지우기") }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        when {
                            all == null -> CircularProgressIndicator(Modifier.padding(top = 48.dp))
                            query.isBlank() -> Hint(
                                if (failed) "일정을 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
                                else "우리 일정 ${all.orEmpty().size}개에서 찾아요 🔎\n예) 감자탕, 영화, 여행",
                            )
                            result == null || result.isEmpty -> Hint("\"${query.trim()}\" 이(가) 들어간 일정이 없어요")
                            else -> LazyColumn(
                                Modifier.fillMaxSize().widthIn(max = 640.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (result.upcoming.isNotEmpty()) {
                                    item { Header("다가오는 일정 ${result.upcoming.size}") }
                                    items(result.upcoming, key = { "u" + it.id }) { e -> ResultRow(e, myUid, today, onOpen) }
                                }
                                if (result.past.isNotEmpty()) {
                                    item { Header("지난 일정 ${result.past.size}") }
                                    items(result.past, key = { "p" + it.id }) { e -> ResultRow(e, myUid, today, onOpen) }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.padding(top = 48.dp, start = 24.dp, end = 24.dp),
    )
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun ResultRow(e: CalendarEvent, myUid: String, today: LocalDate, onOpen: (CalendarEvent) -> Unit) {
    val mine = e.createdBy == myUid
    val strong = if (mine) CoupleColors.mine else CoupleColors.partner
    val dateText = buildString {
        if (e.date.year != today.year) append("${e.date.year}년 ")
        append(e.date.format(KoreanMonthDay))
        if (e.isMultiDay) append(" ~ ${e.endDate.monthValue}월 ${e.endDate.dayOfMonth}일")
    }
    val detail = buildList {
        add(e.slots.firstOrNull()?.start?.format(KoreanTime) ?: "하루 종일")
        (e.place ?: e.slots.firstOrNull { it.place != null }?.place)?.let { add("📍${it.name}") }
        if (e.slots.size > 1) add("시간대 ${e.slots.size}개")
    }.joinToString("  ·  ")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpen(e) },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(strong, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

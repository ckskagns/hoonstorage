package com.uridul.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.data.WishPlace
import java.time.LocalDate

/**
 * 가고 싶은 곳: 지도 앱에서 "공유 → 우리둘" 로 담은 장소 목록.
 *  - [+ 장소 담기] → 지도 앱 고르기 → 장소를 고르고 공유하면 여기에 담긴다
 *  - 장소 이름을 누르면 그 지도 앱에서 바로 열린다
 *  - [이 날 가자] → 날짜를 고르면 그 장소로 새 일정이 열린다
 *  - [다녀왔어요] → 아래 "다녀온 곳" 으로
 */
@Composable
fun WishlistContent(
    wishes: List<WishPlace>,
    couple: Couple,
    myUid: String,
    onPlan: (WishPlace, LocalDate) -> Unit,
    onVisited: (WishPlace, Boolean) -> Unit,
    onMemo: (WishPlace, String) -> Unit,
    onDelete: (WishPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var choosingMap by remember { mutableStateOf(false) }
    var planning by remember { mutableStateOf<WishPlace?>(null) }
    var editingMemo by remember { mutableStateOf<WishPlace?>(null) }
    var deleting by remember { mutableStateOf<WishPlace?>(null) }
    val todo = wishes.filter { !it.visited }
    val done = wishes.filter { it.visited }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("같이 가고 싶은 곳 ${todo.size}곳", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (done.isEmpty()) "지도 앱에서 골라 담아 두고, 날을 정해 가 봐요 💕" else "다녀온 곳 ${done.size}곳 ✨",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Button(onClick = { choosingMap = true }) { Text("+ 장소 담기") }
                }
            }
        }
        if (wishes.isEmpty()) {
            item {
                Text(
                    "아직 담은 곳이 없어요 🌷\n[+ 장소 담기] 를 눌러 지도 앱에서 장소를 고른 뒤\n공유 → 우리둘 을 누르면 여기에 담겨요.\n\n지도 앱에서 그냥 공유해도 \"가고 싶은 곳에 담기\" 를 고를 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
        items(todo, key = { it.id }) { w ->
            WishCard(w, couple, myUid, onOpen = { MapLinks.open(context, w.place) }, onPlan = { planning = w },
                onVisited = { onVisited(w, true) }, onMemo = { editingMemo = w }, onDelete = { deleting = w })
        }
        if (done.isNotEmpty()) {
            item {
                Text(
                    "다녀온 곳 ${done.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 12.dp, start = 4.dp),
                )
            }
            items(done, key = { it.id }) { w ->
                WishCard(w, couple, myUid, onOpen = { MapLinks.open(context, w.place) }, onPlan = { planning = w },
                    onVisited = { onVisited(w, false) }, onMemo = { editingMemo = w }, onDelete = { deleting = w })
            }
        }
    }

    if (choosingMap) {
        MapChooserDialog(
            onDismiss = { choosingMap = false },
            onChoose = { app ->
                choosingMap = false
                PlaceInbox.expectWishlist()
                MapLinks.startSearch(context, app, "")
            },
        )
    }
    planning?.let { w ->
        StartDatePickerDialog(
            initial = LocalDate.now(),
            title = "📍 ${w.place.name} 언제 갈까요?",
            onDismiss = { planning = null },
            onConfirm = { date ->
                planning = null
                onPlan(w, date)
            },
        )
    }
    editingMemo?.let { w ->
        var text by rememberSaveable(w.id) { mutableStateOf(w.memo) }
        AlertDialog(
            onDismissRequest = { editingMemo = null },
            title = { Text("📍 ${w.place.name}") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(WishPlace.MEMO_MAX) },
                    label = { Text("한 줄 메모") },
                    placeholder = { Text("예) 웨이팅 길대요, 주말 점심 추천") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { onMemo(w, text); editingMemo = null }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { editingMemo = null }) { Text("취소") } },
        )
    }
    deleting?.let { w ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("목록에서 뺄까요?") },
            text = { Text("\"${w.place.name}\" 을(를) 가고 싶은 곳에서 빼요. 상대 폰에서도 함께 빠져요.") },
            confirmButton = { TextButton(onClick = { onDelete(w); deleting = null }) { Text("빼기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun WishCard(
    w: WishPlace,
    couple: Couple,
    myUid: String,
    onOpen: () -> Unit,
    onPlan: () -> Unit,
    onVisited: () -> Unit,
    onMemo: () -> Unit,
    onDelete: () -> Unit,
) {
    val who = if (w.createdBy == myUid) couple.myName(myUid) else (couple.names[w.createdBy] ?: "상대")
    val press = remember { MutableInteractionSource() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .pressBounce(press)
            .animateContentSize()
            .alpha(if (w.visited) 0.7f else 1f),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip14()
                        .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current, onClick = onOpen)
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        (if (w.visited) "✅ " else "📍 ") + w.place.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${w.place.app?.label ?: "지도"}에서 열기 · ${who}가 담음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CharacterAvatar(couple.characterOf(w.createdBy), 30.dp, contentDescription = "$who 담음")
            }
            Text(
                w.memo.ifBlank { "+ 메모" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (w.memo.isBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp).clip14().clickable(onClick = onMemo).padding(vertical = 4.dp, horizontal = 2.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPlan, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("📅 이 날 가자") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onVisited) { Text(if (w.visited) "다시 가고 싶어요" else "✓ 다녀왔어요") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("빼기", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun Modifier.clip14(): Modifier = clip(RoundedCornerShape(14.dp))

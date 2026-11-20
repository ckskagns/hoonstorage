package com.uridul.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.Couple
import com.uridul.app.data.DayPhoto
import com.uridul.app.domain.MonthRecap
import com.uridul.app.ledger.LedgerEntry
import com.uridul.app.ledger.Won
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// "이달의 우리": 한 달 돌아보기 카드 (홈 1~7일 카드, 일기장 위 달 단추에서 연다). 계산은 domain/MonthRecap.

/** 이 달 돌아보기 창: 카드 한 장 + 사진에 저장 */
@Composable
fun MonthRecapDialog(month: YearMonth, couple: Couple, myUid: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = LocalPhotos.current
    val reviews = LocalReviews.current.reviews
    LaunchedEffect(month) { photos.loadMonthPhotos(month) }
    // 그 달 데이트 가계부 (한 번만 읽는다, 못 읽으면 돈 칸만 뺀다)
    var ledger by remember(month) { mutableStateOf<List<LedgerEntry>?>(null) }
    LaunchedEffect(month) {
        val repo = appContainer(context)?.ledger
        ledger = runCatching { repo?.fetchMonth(couple.id, month) }.getOrNull().orEmpty()
    }
    val dayPhotos = photos.dayPhotos
    val recap = remember(month, reviews, dayPhotos, ledger) {
        MonthRecap.build(month, reviews.values, dayPhotos.mapValues { it.value.count }, ledger.orEmpty())
    }
    val layer = rememberGraphicsLayer()
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 저장할 카드: 그리는 대로 layer 에도 담아 둔다
                Box(
                    Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                ) {
                    RecapCard(recap, couple, myUid, dayPhotos, ledgerLoaded = ledger != null)
                }
                Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("닫기") }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                val ok = runCatching {
                                    val bmp = layer.toImageBitmap().asAndroidBitmap()
                                    GallerySaver.save(context, bmp, "우리둘_${month}")
                                }.getOrDefault(false)
                                saving = false
                                android.widget.Toast.makeText(
                                    context,
                                    if (ok) "사진(Pictures/우리둘)에 저장했어요" else "저장하지 못했어요",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (saving) "저장 중…" else "📥 사진에 저장") }
                }
            }
        }
    }
}

/** 돌아보기 카드 한 장 (이 모양 그대로 이미지로 저장된다) */
@Composable
private fun RecapCard(recap: MonthRecap.Recap, couple: Couple, myUid: String, photos: Map<LocalDate, DayPhoto>, ledgerLoaded: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${recap.month.year}년", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text("${recap.month.monthValue}월의 우리", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = scheme.primary)
                Text("${couple.myName(myUid)} ♥ ${couple.partnerName(myUid) ?: "?"}", style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
            }
            // 캐릭터 그림이 있으면 "꼬옥" 장면
            EmotionArt(Emotion.HUG, couple, myUid, 92.dp) {
                CharacterAvatar(couple.myCharacter(myUid), 44.dp, bob = true, phase = 0)
                CharacterAvatar(couple.partnerCharacter(myUid), 44.dp, bob = true, phase = 1)
            }
        }

        // 사진 콜라주 (하트·사진 많은 날 최대 4장)
        if (recap.collage.isNotEmpty()) RecapCollage(recap.collage.mapNotNull { d -> photos[d]?.let { d to it } })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("데이트 기록", "${recap.days}일", Modifier.weight(1f))
            StatTile("사진", "${recap.photos}장", Modifier.weight(1f))
            StatTile("하트 평균", recap.hearts?.let { "%.1f".format(it) } ?: "-", Modifier.weight(1f))
        }

        recap.best?.let { b ->
            InfoBox("💗 하트가 가장 많은 날") {
                Text("${b.date.monthValue}월 ${b.date.dayOfMonth}일 · ${b.title.ifBlank { "우리 데이트" }}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                b.entries.values.maxByOrNull { it.text.length }?.text?.takeIf { it.isNotBlank() }?.let {
                    Text("“$it”", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (recap.places > 0) InfoBox("📍 다녀온 곳") {
            Text(
                recap.topPlace?.let { (name, n) -> "${recap.places}곳 · 가장 많이 간 곳 $name (${n}번)" } ?: "${recap.places}곳",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (ledgerLoaded && recap.expense > 0) InfoBox("💳 데이트 가계부") {
            Text("함께 쓴 돈 ${Won.text(recap.expense)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            recap.topCategory?.let { (c, amount) ->
                Text("가장 많이 쓴 곳 ${c.emoji} ${c.label} ${Won.text(amount)}", style = MaterialTheme.typography.bodyMedium)
            }
            recap.biggest?.let { e ->
                Text("가장 큰 결제 ${e.merchant} ${Won.text(e.amount)}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (recap.isEmpty) {
            Text("이 달에는 아직 남긴 기록이 없어요", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text("우리둘", style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer.copy(alpha = 0.5f), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

/** 사진 1장이면 크게, 2장 이상이면 2칸씩 */
@Composable
private fun RecapCollage(items: List<Pair<LocalDate, DayPhoto>>) {
    if (items.isEmpty()) return
    val rows = if (items.size == 1) listOf(items) else items.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (date, p) ->
                    val bmp = rememberPhotoBitmap("cal:${p.id}", p.thumb)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (items.size == 1) 4f / 3f else 1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        bmp?.let { Image(it, contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        PhotoBadge(
                            "${date.dayOfMonth}일",
                            Modifier.align(Alignment.BottomStart),
                            MaterialTheme.typography.labelMedium,
                            outer = 6.dp, inner = 6.dp, corner = 8.dp,
                        )
                    }
                }
                // 홀수 장이면 빈칸으로 크기 맞춤
                if (row.size == 1 && items.size > 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InfoBox(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** 일기장 위: 달마다 "11월의 우리" 단추 (최근 달부터, 옆으로 넘김) */
@Composable
fun RecapMonthChips(months: List<YearMonth>, onOpen: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    if (months.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(months.size) { i ->
            val m = months[i]
            androidx.compose.material3.AssistChip(
                onClick = { onOpen(m) },
                label = { Text("📖 ${if (m.year == LocalDate.now().year) "" else "${m.year % 100}년 "}${m.monthValue}월의 우리") },
            )
        }
    }
}

/** 홈: 매달 1~7일 "지난달의 우리" 카드 (닫으면 그 달은 다시 안 뜸) */
@Composable
fun RecapHomeBanner(month: YearMonth, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    androidx.compose.material3.Card(
        onClick = onOpen,
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("📖 ${month.monthValue}월의 우리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("지난달 데이트·사진·쓴 돈을 한 장으로 돌아봐요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            androidx.compose.material3.IconButton(onClick = onClose) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "${month.monthValue}월의 우리 닫기")
            }
        }
    }
}

/** 홈 "지난달의 우리" 카드를 닫은 달 (이 폰에만) */
object RecapFlags {
    private const val PREFS = "recap_flags"
    fun closed(context: Context, month: YearMonth): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("closed_$month", false)

    fun close(context: Context, month: YearMonth) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("closed_$month", true).apply()
    }
}

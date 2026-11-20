package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.uridul.app.data.Couple
import com.uridul.app.data.PartnerAnswer
import com.uridul.app.data.QuestionDay
import com.uridul.app.data.QuestionRepository
import com.uridul.app.domain.DailyQuestion
import com.uridul.app.domain.QuestionText
import java.time.LocalDate

// 오늘의 질문 (선호도 조사 4번): 홈 카드 + 답하기 창.
// 하루 한 질문, 내가 답하면 상대 답이 열린다. 질문은 앱 안 목록에서 커플마다 섞은 순서로 (domain/DailyQuestion.kt).

/** 불러온 상태: 아직 모름 / 준비 안 됨(보안 규칙 배포 전) / 오류 / 결과 */
private sealed interface QLoad {
    data object Loading : QLoad
    data object NotReady : QLoad
    data object Failed : QLoad
    data class Done(val day: QuestionDay) : QLoad
}

/**
 * 홈의 "오늘의 질문" 카드.
 * @param openRequest 0 이 아니면 답하기 창을 바로 연다 (앱 아이콘 바로가기)
 */
@Composable
fun DailyQuestionCard(
    couple: Couple,
    myUid: String,
    today: LocalDate,
    modifier: Modifier = Modifier,
    openRequest: Int = 0,
    onOpenRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = appContainer(context)?.questions
    val qid = remember(couple.id, today) { DailyQuestion.indexFor(couple.id, today) }
    val question = DailyQuestion.QUESTIONS[qid]
    val partnerUid = couple.partnerUid(myUid)
    val partnerName = couple.partnerName(myUid) ?: "상대"

    // 다시 읽기: 창을 열 때·답한 뒤·앱으로 돌아올 때
    var reload by remember { mutableIntStateOf(0) }
    // 처음 그릴 때 이미 읽기 시작하므로, 곧바로 오는 ON_RESUME 로 또 읽지 않게 지금 시각으로 시작
    var loadedAt by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    // 방금 쓴 내 답 (서버에 닿기 전에도 바로 보이게)
    var localMine by remember(today) { mutableStateOf<String?>(null) }
    val load by produceState<QLoad>(QLoad.Loading, couple.id, today, partnerUid, reload) {
        if (repo == null) { value = QLoad.Failed; return@produceState }
        value = try {
            QLoad.Done(repo.load(couple.id, today, myUid, partnerUid))
        } catch (e: QuestionRepository.NotReady) {
            QLoad.NotReady
        } catch (e: Exception) {
            QLoad.Failed
        }
        loadedAt = System.currentTimeMillis()
    }
    val day = (load as? QLoad.Done)?.day
    // 앱으로 돌아올 때 다시 읽기: 둘 다 답해 열린 날은 더 바뀔 게 없으니 안 읽고, 그 밖에는 1분에 한 번까지
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val settled = day?.mine != null && day.partner is PartnerAnswer.Open
        if (!settled && System.currentTimeMillis() - loadedAt > 60_000) reload++
    }
    val mine = localMine ?: day?.mine
    val partner = day?.partner ?: PartnerAnswer.Unknown

    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        if (openRequest != 0) { open = true; onOpenRequestHandled() }
    }

    val status = QuestionText.cardStatus(load !is QLoad.NotReady, couple.isPaired, mine, partner, partnerName)
    Card(
        onClick = { open = true; reload++ },
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💬 오늘의 질문", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                question,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }

    if (open) DailyQuestionDialog(
        couple = couple,
        myUid = myUid,
        question = question,
        load = load,
        mine = mine,
        partner = partner,
        partnerName = partnerName,
        onSubmit = { text ->
            val before = mine
            localMine = text
            repo?.answer(
                couple.id, today, myUid, qid, text,
                onSaved = { reload++ },
                onError = {
                    localMine = before
                    android.widget.Toast.makeText(context, "답을 저장하지 못했어요. 잠시 뒤 다시 해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        },
        onDismiss = { open = false },
    )
}

@Composable
private fun DailyQuestionDialog(
    couple: Couple,
    myUid: String,
    question: String,
    load: QLoad,
    mine: String?,
    partner: PartnerAnswer,
    partnerName: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 내가 답한 뒤 상대 답이 열리는 순간 하트
    var wasOpen by remember { mutableStateOf(partner is PartnerAnswer.Open) }
    LaunchedEffect(partner) {
        if (partner is PartnerAnswer.Open && !wasOpen) Celebrate.fire()
        wasOpen = partner is PartnerAnswer.Open
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(mine) { mutableStateOf(mine.orEmpty()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 답 전 고민 → 상대만 답해 잠김이면 보고 싶어(창가) → 둘 다 답하면 사랑해 하트
                    val art = when {
                        mine != null && partner is PartnerAnswer.Open -> Emotion.LOVE
                        mine == null && partner is PartnerAnswer.Locked -> Emotion.MISS
                        else -> Emotion.THINK
                    }
                    EmotionArt(art, couple, myUid, 130.dp)
                    Text("💬 오늘의 질문", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(question, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

                    if (load is QLoad.NotReady) {
                        Text(
                            "오늘의 질문은 서버 설정이 끝나면 열려요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        // 내 답
                        AnswerBox(couple.myName(myUid)) {
                            if (mine == null || editing) {
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it.take(DailyQuestion.ANSWER_MAX) },
                                    placeholder = { Text("솔직하게 한 줄이라도 좋아요") },
                                    supportingText = { Text("${draft.length} / ${DailyQuestion.ANSWER_MAX}") },
                                    minLines = 2,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (editing) TextButton(onClick = { editing = false; draft = mine.orEmpty() }) { Text("취소") }
                                    Button(
                                        enabled = draft.isNotBlank() && draft.trim() != mine,
                                        onClick = { onSubmit(draft.trim()); editing = false },
                                    ) { Text(if (mine == null) "답하기" else "고치기") }
                                }
                            } else {
                                Text(mine, style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = { editing = true }, modifier = Modifier.align(Alignment.End)) { Text("고치기") }
                            }
                        }
                        // 상대 답
                        AnswerBox(partnerName) {
                            val (text, dim) = QuestionText.partnerLine(couple.isPaired, partner, load is QLoad.Loading)
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
                }
            }
            HeartBurstOverlay(haptic = false)
        }
    }
}

@Composable
private fun AnswerBox(name: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

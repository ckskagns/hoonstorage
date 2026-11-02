package com.uridul.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.time.LocalDate

/**
 * 일정 삭제 확인 (하루 페이지·상세 보기가 함께 쓴다).
 * 반복 일정이면 "이 날만 빼기" / "반복 전체 삭제" 중에서 고른다.
 */
@Composable
internal fun DeleteEventDialog(
    title: String,
    /** 반복 일정이면 "매주 수요일" 같은 설명, 아니면 null */
    repeatLabel: String?,
    /** 반복 일정에서 고른 날 (그 날의 시작일) */
    occurrence: LocalDate?,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val repeating = repeatLabel != null && occurrence != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (repeating) "반복 일정을 삭제할까요?" else "일정을 삭제할까요?") },
        text = {
            Text(
                if (repeating) {
                    "\"$title\" 은(는) $repeatLabel 반복 일정이에요.\n" +
                        "이 날(${occurrence!!.monthValue}월 ${occurrence.dayOfMonth}일)만 뺄 수도, 반복 전체를 지울 수도 있어요. 상대 폰에서도 똑같이 바뀌어요."
                } else {
                    "\"$title\" 일정이 상대 폰에서도 함께 사라져요.\n지운 뒤 바로 [되돌리기]를 누르거나, 메뉴의 '최근 지운 일정'에서 30일 안에 되살릴 수 있어요."
                },
            )
        },
        confirmButton = {
            Row {
                if (repeating) TextButton(onClick = onSkip) { Text("이 날만 빼기") }
                TextButton(onClick = onDelete) {
                    Text(if (repeating) "반복 전체 삭제" else "삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

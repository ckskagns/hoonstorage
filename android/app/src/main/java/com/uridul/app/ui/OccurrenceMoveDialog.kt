package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uridul.app.data.EventDraft

/**
 * 반복 일정의 어느 날 날짜를 바꿨을 때: 이 날만 옮길지, 반복 전체를 옮길지.
 * 어느 쪽이든 그 날(들)의 사진·후기가 함께 옮겨진다.
 */
@Composable
fun OccurrenceMoveDialog(input: EventDraft, onResolve: (onlyThisDay: Boolean?) -> Unit) {
    val from = input.occurrence ?: return
    val to = input.date
    val fromText = "${from.monthValue}월 ${from.dayOfMonth}일"
    val toText = "${to.monthValue}월 ${to.dayOfMonth}일"
    AlertDialog(
        onDismissRequest = { onResolve(null) },
        title = { Text("반복 일정을 옮길까요?") },
        text = {
            Text(
                "\"${input.title}\" 은(는) 반복 일정이에요.\n" +
                    "$fromText 하루만 ${toText}로 옮길 수도, 반복 전체를 같은 만큼 옮길 수도 있어요. " +
                    "그 날 남긴 사진·후기도 함께 옮겨져요.",
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onResolve(true) }, modifier = Modifier.fillMaxWidth()) { Text("이 날만 옮기기") }
                OutlinedButton(onClick = { onResolve(false) }, modifier = Modifier.fillMaxWidth()) { Text("반복 전체 옮기기") }
                TextButton(onClick = { onResolve(null) }, modifier = Modifier.fillMaxWidth()) { Text("취소") }
            }
        },
    )
}

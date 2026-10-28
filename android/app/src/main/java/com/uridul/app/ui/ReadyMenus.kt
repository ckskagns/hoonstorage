package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple
import com.uridul.app.data.ImportPreview
import com.uridul.app.data.Place
import com.uridul.app.ui.theme.CoupleColors
import java.time.LocalDate

// 메인 화면(ReadyScreen)의 오른쪽 위 메뉴와 작은 확인 창들

/** 메뉴에서 고른 것 */
internal enum class MenuAction {
    Profile, StartDate, ToggleCountFromOne, Invite,
    ToggleNotify, Theme, Colors, Deleted, BackupSave, BackupLoad, CheckUpdate, SignOut,
}

/** 오른쪽 위 ⋮ 메뉴 */
@Composable
internal fun MainMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    couple: Couple,
    myUid: String,
    notifyEnabled: Boolean,
    themeLabel: String,
    deletedCount: Int,
    onAction: (MenuAction) -> Unit,
) {
    fun pick(action: MenuAction) {
        onDismiss()
        onAction(action)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("내 프로필 (이름·캐릭터)") },
            leadingIcon = { CharacterAvatar(couple.myCharacter(myUid), 24.dp) },
            onClick = { pick(MenuAction.Profile) },
        )
        DropdownMenuItem(text = { Text("시작일 변경") }, onClick = { pick(MenuAction.StartDate) })
        DropdownMenuItem(
            text = { Text("사귄 날을 1일로 계산") },
            trailingIcon = { Checkbox(checked = couple.countFromOne, onCheckedChange = null) },
            onClick = { pick(MenuAction.ToggleCountFromOne) },
        )
        if (!couple.isPaired) {
            DropdownMenuItem(text = { Text("초대 링크 보내기") }, onClick = { pick(MenuAction.Invite) })
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("일정 알림 (아침 9시·일정 전)") },
            trailingIcon = { Checkbox(checked = notifyEnabled, onCheckedChange = null) },
            onClick = { pick(MenuAction.ToggleNotify) },
        )
        DropdownMenuItem(text = { Text("화면 모드 ($themeLabel)") }, onClick = { pick(MenuAction.Theme) })
        DropdownMenuItem(
            text = { Text("일정 색") },
            trailingIcon = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.size(14.dp).background(CoupleColors.mineFill, CircleShape))
                    Box(Modifier.size(14.dp).background(CoupleColors.partnerFill, CircleShape))
                }
            },
            onClick = { pick(MenuAction.Colors) },
        )
        DropdownMenuItem(
            text = { Text(if (deletedCount == 0) "최근 지운 일정" else "최근 지운 일정 ($deletedCount)") },
            onClick = { pick(MenuAction.Deleted) },
        )
        DropdownMenuItem(text = { Text("백업 파일 저장") }, onClick = { pick(MenuAction.BackupSave) })
        DropdownMenuItem(text = { Text("백업 불러오기") }, onClick = { pick(MenuAction.BackupLoad) })
        DropdownMenuItem(
            text = { Text("업데이트 확인 (현재 ${com.uridul.app.BuildConfig.VERSION_NAME})") },
            onClick = { pick(MenuAction.CheckUpdate) },
        )
        DropdownMenuItem(text = { Text("로그아웃") }, onClick = { pick(MenuAction.SignOut) })
    }
}

/** 지도 앱에서 그냥 공유한 장소: 가고 싶은 곳 / 고른 날 일정 중 어디에 담을지 */
@Composable
internal fun PlaceChoiceDialog(
    place: Place,
    selected: LocalDate,
    onWish: () -> Unit,
    onEvent: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📍 ${place.name}") },
        text = { Text("이 장소를 어디에 담을까요?") },
        confirmButton = { TextButton(onClick = onWish) { Text("가고 싶은 곳에 담기") } },
        dismissButton = { TextButton(onClick = onEvent) { Text("${selected.monthValue}월 ${selected.dayOfMonth}일 일정에 넣기") } },
    )
}

/** 백업 불러오기 전 확인: 파일 내용과 새로 더할 것 */
@Composable
internal fun ImportPreviewDialog(preview: ImportPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val hasNew = preview.newEvents.isNotEmpty() || preview.newWishes.isNotEmpty() || preview.newReviews.isNotEmpty()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("백업 불러오기") },
        text = {
            Text(
                buildString {
                    append("파일: 일정 ${preview.data.events.size}개 · 가고 싶은 곳 ${preview.data.wishes.size}곳 · 후기 ${preview.data.reviews.size}개")
                    if (preview.data.exportedAt.isNotBlank()) append("\n저장한 때: ${preview.data.exportedAt.replace('T', ' ')}")
                    append("\n\n")
                    if (!hasNew) {
                        append("지금 달력에 모두 있어서 더할 것이 없어요.")
                    } else {
                        append("지금 없는 일정 ${preview.newEvents.size}개 · 장소 ${preview.newWishes.size}곳 · 후기 ${preview.newReviews.size}개를 더해요.\n이미 있는 것은 바꾸지 않아요.")
                    }
                },
            )
        },
        confirmButton = {
            if (hasNew) TextButton(onClick = onConfirm) { Text("더하기") } else TextButton(onClick = onCancel) { Text("확인") }
        },
        dismissButton = { if (hasNew) TextButton(onClick = onCancel) { Text("취소") } },
    )
}

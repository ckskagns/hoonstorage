package com.uridul.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.AppSettings
import com.uridul.app.data.Couple
import com.uridul.app.ui.theme.CoupleColors

// 설정: 오른쪽 위 ⚙ → 기능별 대메뉴 → 대메뉴마다 상세 페이지.
// 각 줄은 ReadyScreen 이 처리하는 MenuAction 을 그대로 보낸다 (프로필·시작일·백업 창 등은 기존 그대로).

/** 설정 페이지에서 고른 것 (ReadyScreen 이 처리) */
internal enum class MenuAction {
    Profile, StartDate, ToggleCountFromOne, Invite,
    ToggleNotify, TogglePartnerAlerts, Theme, TogglePhotoTint, TogglePhotoCalendar, Colors, HeartDesign, Deleted, BackupSave, BackupLoad, CheckUpdate, SignOut,
}

/** 설정 대메뉴 */
internal enum class SettingsSection(val emoji: String, val title: String) {
    PROFILE("👤", "프로필·연결"),
    NOTIFY("🔔", "알림"),
    SCREEN("🎨", "화면"),
    WIDGET("🧩", "위젯"),
    DATA("💾", "데이터"),
    APP("ℹ️", "앱 정보"),
}

@Composable
internal fun SettingsPage(
    couple: Couple,
    myUid: String,
    notifyEnabled: Boolean,
    partnerAlertsEnabled: Boolean,
    themeLabel: String,
    deletedCount: Int,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var sectionKey by rememberSaveable { mutableStateOf("") }
    val section = SettingsSection.entries.firstOrNull { it.name == sectionKey }
    val back = { if (section != null) sectionKey = "" else onDismiss() }
    val ui by AppSettings.state.collectAsState()

    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BackHandler { back() }
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // 위: ← 제목
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                    Text(
                        section?.let { "${it.emoji} ${it.title}" } ?: "설정",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (section) {
                            null -> SettingsSection.entries.forEach { s ->
                                SectionRow(s, summaryOf(s, couple, notifyEnabled, partnerAlertsEnabled, themeLabel, ui.photoTint, deletedCount)) { sectionKey = s.name }
                            }
                            SettingsSection.PROFILE -> Group {
                                ActionRow("내 프로필", "이름·캐릭터 (${couple.myName(myUid)})", leading = { CharacterAvatar(couple.myCharacter(myUid), 32.dp) }) { onAction(MenuAction.Profile) }
                                ActionRow("시작일 변경", couple.startDate.format(KoreanLongDate)) { onAction(MenuAction.StartDate) }
                                SwitchRow("사귄 날을 1일로 계산", "켜면 사귄 첫날이 1일", couple.countFromOne) { onAction(MenuAction.ToggleCountFromOne) }
                                if (!couple.isPaired) ActionRow("초대 링크 보내기", "상대가 눌러서 바로 들어와요") { onAction(MenuAction.Invite) }
                            }
                            SettingsSection.NOTIFY -> {
                                Group {
                                    SwitchRow("일정 알림", "일정 있는 날 아침 9시", notifyEnabled) { onAction(MenuAction.ToggleNotify) }
                                    SwitchRow("상대 소식 알림", "상대가 일정·가고 싶은 곳·후기를 남기면", partnerAlertsEnabled) { onAction(MenuAction.TogglePartnerAlerts) }
                                }
                                GroupTitle("🩺 알림 점검", "알림이 안 온다면 아래가 모두 ✅ 인지 확인해 주세요. 누르면 그 설정 화면이 열려요.")
                                NotifyCheck()
                            }
                            SettingsSection.SCREEN -> Group {
                                ActionRow("화면 모드", themeLabel) { onAction(MenuAction.Theme) }
                                SwitchRow("디데이 사진 색 따라가기", "홈 사진에서 뽑은 색으로 앱 색을 은은하게", ui.photoTint) { onAction(MenuAction.TogglePhotoTint) }
                                SwitchRow("사진 달력", "달력 칸에 그날 일기 사진 깔기", ui.photoCalendar) { onAction(MenuAction.TogglePhotoCalendar) }
                                ActionRow("일정 색", "내 일정 · 상대 일정", trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.size(18.dp).background(CoupleColors.mineFill, CircleShape))
                                        Box(Modifier.size(18.dp).background(CoupleColors.partnerFill, CircleShape))
                                    }
                                }) { onAction(MenuAction.Colors) }
                            }
                            SettingsSection.WIDGET -> {
                                GroupTitle("➕ 홈 화면에 위젯 놓기", "누르면 홈 화면에 바로 놓을지 물어봐요 (삼성 홈 화면은 위치를 고르라고 할 수 있어요)")
                                WidgetPinList()
                                Group {
                                    ActionRow("하트 위젯 디자인", "잠금 화면·홈 화면 하트 모양 15가지") { onAction(MenuAction.HeartDesign) }
                                }
                            }
                            SettingsSection.DATA -> Group {
                                ActionRow("최근 지운 일정", if (deletedCount == 0) "없음" else "${deletedCount}개 · 30일 동안 되돌릴 수 있어요") { onAction(MenuAction.Deleted) }
                                ActionRow("백업 파일 저장", "일정·가고 싶은 곳·후기를 파일로") { onAction(MenuAction.BackupSave) }
                                ActionRow("백업 불러오기", "백업 파일에서 지금 없는 것만") { onAction(MenuAction.BackupLoad) }
                            }
                            SettingsSection.APP -> {
                                var licenses by remember { mutableStateOf(false) }
                                Group {
                                    ActionRow("업데이트 확인", "현재 ${com.uridul.app.BuildConfig.VERSION_NAME}") { onAction(MenuAction.CheckUpdate) }
                                    ActionRow("오픈소스·글꼴 라이선스", "주아체(SIL OFL 1.1), AndroidX(Apache 2.0) 등") { licenses = true }
                                }
                                Group {
                                    ActionRow("로그아웃", null, danger = true) { onAction(MenuAction.SignOut) }
                                }
                                if (licenses) LicensesDialog { licenses = false }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 대메뉴 줄 아래 작은 글씨: 지금 상태 한눈에 */
private fun summaryOf(
    s: SettingsSection, couple: Couple, notify: Boolean, partner: Boolean, theme: String, tint: Boolean, deleted: Int,
): String = when (s) {
    SettingsSection.PROFILE -> "시작일 ${couple.startDate.format(KoreanLongDate)}" + if (couple.isPaired) "" else " · 연결 전"
    SettingsSection.NOTIFY -> "일정 알림 ${onOff(notify)} · 상대 소식 ${onOff(partner)}"
    SettingsSection.SCREEN -> "$theme · 사진 색 ${onOff(tint)}"
    SettingsSection.WIDGET -> "위젯 놓기 · 하트 디자인"
    SettingsSection.DATA -> "백업 · 최근 지운 일정" + if (deleted > 0) " ${deleted}개" else ""
    SettingsSection.APP -> "버전 ${com.uridul.app.BuildConfig.VERSION_NAME} · 라이선스 · 로그아웃"
}

private fun onOff(on: Boolean) = if (on) "켬" else "끔"

@Composable
private fun SectionRow(s: SettingsSection, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = "${s.title} 설정 열기", onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 상세 페이지의 한 묶음 (둥근 카드 안에 줄들, 줄 사이 얇은 선) */
@Composable
internal fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
internal fun GroupTitle(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ActionRow(
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { Box(Modifier.padding(end = 12.dp)) { it() } }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke() ?: if (!danger) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) else Unit
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (checked) "끄기" else "켜기", onClick = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

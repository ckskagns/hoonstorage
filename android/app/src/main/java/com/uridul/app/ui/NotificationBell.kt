package com.uridul.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uridul.app.data.InboxItem

/**
 * 오른쪽 위 🔔: 누르면 별도 페이지 없이 작은 창으로 알림 목록.
 *  - 안 읽은 알림(위) / 읽은 알림(아래, 흐리게)
 *  - 알림을 누르면 공유 캘린더의 그 일정(날짜)으로 이동하고 읽음으로 바뀐다
 */
@Composable
fun NotificationBell(
    items: List<InboxItem>,
    onOpen: (InboxItem) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val unread = items.filter { !it.read }
    val read = items.filter { it.read }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = if (unread.isEmpty()) "알림" else "알림, 안 읽은 알림 ${unread.size}개" },
        ) {
            BadgedBox(badge = {
                if (unread.isNotEmpty()) Badge { Text(if (unread.size > 99) "99+" else "${unread.size}") }
            }) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(320.dp).heightIn(max = 460.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔔 알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (unread.isNotEmpty()) TextButton(onClick = onMarkAllRead) { Text("모두 읽음") }
            }
            if (items.isEmpty()) {
                Text(
                    "아직 알림이 없어요 🌷\n일정 알림(아침 9시·일정 전)이 오면 여기에 모여요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
            if (unread.isNotEmpty()) {
                SectionLabel("안 읽은 알림 ${unread.size}")
                unread.forEach { item -> InboxRow(item) { open = false; onOpen(item) } }
            }
            if (read.isNotEmpty()) {
                if (unread.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SectionLabel("읽은 알림")
                read.forEach { item -> InboxRow(item) { open = false; onOpen(item) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** 알림 한 줄: ● 제목 / 내용 두 줄 / "3시간 전 · 10월 28일 일정" */
@Composable
private fun InboxRow(item: InboxItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (item.read) 0.6f else 1f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(if (item.read) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (item.read) FontWeight.Normal else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.text.isNotBlank()) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${timeAgo(item.postedAtMillis)} · ${item.date.monthValue}월 ${item.date.dayOfMonth}일 일정",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "방금", "12분 전", "3시간 전", "어제", "10월 26일" */
internal fun timeAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - millis) / 60_000
    return when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 전"
        minutes < 24 * 60 -> "${minutes / 60}시간 전"
        minutes < 48 * 60 -> "어제"
        else -> java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .let { "${it.monthValue}월 ${it.dayOfMonth}일" }
    }
}

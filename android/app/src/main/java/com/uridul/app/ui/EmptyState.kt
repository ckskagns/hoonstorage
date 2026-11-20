package com.uridul.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple

/**
 * 빈 화면: 두 캐릭터가 살랑이며 한 줄 제목 + 설명 + (있으면) 할 일 단추.
 * 사진 없는 모아보기, 후기 없는 일기장, 기록 없는 가계부 달, 담은 곳 없는 가고 싶은 곳에서 쓴다.
 */
@Composable
fun CharacterEmpty(
    couple: Couple,
    myUid: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            CharacterAvatar(couple.myCharacter(myUid), 64.dp, bob = true, phase = 0)
            CharacterAvatar(couple.partnerCharacter(myUid), 64.dp, bob = true, phase = 1)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { Text(action) }
    }
}

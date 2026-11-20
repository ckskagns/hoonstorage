package com.uridul.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uridul.app.data.Couple
import com.uridul.app.domain.CelebrateDay
import com.uridul.app.domain.SpecialDay
import java.time.LocalDate

/**
 * 기념일 당일(100일 단위·주년·생일) 앱을 처음 열 때 한 번 뜨는 축하 창.
 * "축하해" 그림 + 하트 터짐, [오늘 사진 남기기] 는 일기장으로.
 */
@Composable
fun CelebrateDayDialog(
    day: SpecialDay,
    couple: Couple,
    myUid: String,
    onOpenDiary: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(day) { Celebrate.fire() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmotionArt(Emotion.CELEBRATE, couple, myUid, 200.dp)
                    Text(CelebrateDay.headline(day), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (day.note.isNotBlank()) {
                        Text(day.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("💗 축하해") }
                    OutlinedButton(onClick = onOpenDiary, modifier = Modifier.fillMaxWidth()) { Text("📷 오늘 사진 남기기") }
                }
            }
            HeartBurstOverlay(haptic = false)
        }
    }
}

/** 축하 창을 이미 띄운 날 (이 폰에만) */
object CelebrateFlags {
    private const val PREFS = "celebrate_flags"
    fun shown(context: Context, day: LocalDate): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last", null) == day.toString()

    fun markShown(context: Context, day: LocalDate) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last", day.toString()).apply()
    }
}

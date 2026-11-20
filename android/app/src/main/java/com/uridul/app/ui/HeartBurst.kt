package com.uridul.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * 하트 손맛: 좋은 일이 끝났을 때(후기 저장, 사진 올리기) 화면 가운데서 하트가 퍼지며 짧게 진동한다.
 * 어디서든 [Celebrate.fire] 를 부르면 [HeartBurstOverlay] 가 받아서 터뜨린다.
 */
object Celebrate {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun fire() {
        _events.tryEmit(Unit)
    }
}

/** 화면 맨 위에 깔아 두는 하트 터짐 (누르기는 막지 않는다) */
@Composable
fun HeartBurstOverlay() {
    val view = LocalView.current
    var shots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        Celebrate.events.collect {
            shots++
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HeartBurst(shots, count = 12, radius = 150.dp, size = 28.sp, durationMs = 900)
    }
}

private val BURST_HEARTS = listOf("💗", "💖", "💕", "❤️")

/**
 * [trigger] 가 바뀔 때마다 하트 [count] 개가 가운데서 사방으로 퍼지며 사라진다 (0 이면 아무것도 안 그림).
 * 하트마다 각도·거리를 조금씩 달리해서 손으로 뿌린 것처럼.
 */
@Composable
fun HeartBurst(trigger: Int, count: Int, radius: Dp, size: TextUnit, durationMs: Int = 700, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    if (p >= 1f) return
    val r = with(LocalDensity.current) { radius.toPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        repeat(count) { i ->
            // 같은 터짐 안에서는 늘 같은 모양 (trigger 로 섞어서 터질 때마다 조금 다르게)
            val seed = (i * 7919 + trigger * 104729) % 1000 / 1000f
            val angle = (i.toFloat() / count) * 2 * Math.PI + seed * 0.6
            val dist = r * (0.6f + 0.4f * seed) * p
            Text(
                BURST_HEARTS[(i + trigger) % BURST_HEARTS.size],
                fontSize = size,
                modifier = Modifier.graphicsLayer {
                    translationX = (cos(angle) * dist).toFloat()
                    translationY = (sin(angle) * dist).toFloat() - r * 0.25f * p // 살짝 위로 떠오르게
                    val s = 0.5f + 0.7f * p
                    scaleX = s
                    scaleY = s
                    alpha = (1f - p) * 1.2f
                },
            )
        }
    }
}

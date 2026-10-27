package com.uridul.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 누르면 살짝 작아졌다가 말랑하게 돌아오는 효과 (카드·버튼에 귀여운 손맛).
 * 사용: val src = remember { MutableInteractionSource() }; Card(onClick, interactionSource = src, modifier = Modifier.pressBounce(src))
 */
@Composable
fun Modifier.pressBounce(source: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-bounce",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 선택되면 통통 튀며 커지는 크기 (달력 날짜 선택 등) */
@Composable
fun bounceScale(selected: Boolean, selectedScale: Float = 1.06f): Float {
    val scale by animateFloatAsState(
        targetValue = if (selected) selectedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "select-bounce",
    )
    return scale
}

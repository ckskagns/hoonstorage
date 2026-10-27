package com.uridul.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD14D72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7C5635),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF514347),
    surfaceContainer = Color(0xFFFAEBEE),
    surfaceContainerHigh = Color(0xFFF4E5E8),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C5),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94),
    tertiaryContainer = Color(0xFF623F20),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEE2),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEE2),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31282A),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
)

/** 일정 색: 내가 등록한 일정은 분홍, 상대가 등록한 일정은 파랑 */
object CoupleColors {
    val mine: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB1C5) else Color(0xFFD14D72)
    val partner: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFB0C6FF) else Color(0xFF4F6FC4)
    /** 일요일·공휴일 빨강 / 토요일 파랑 */
    val sunday: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFD32F2F)
    val saturday: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA8C8FF) else Color(0xFF1E62C8)
}

@Composable
fun UridulTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

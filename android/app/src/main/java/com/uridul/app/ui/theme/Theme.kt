package com.uridul.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.uridul.app.data.AppSettings
import com.uridul.app.data.ON_EVENT_FILL
import com.uridul.app.data.ThemeMode
import com.uridul.app.data.UiSettings
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

/** 지금 다크 모드인지 (앱 설정의 화면 모드 반영) */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 지금 쓰는 일정 색 설정 */
val LocalUiSettings = staticCompositionLocalOf { UiSettings() }

/**
 * 일정 색: 내 일정 / 상대 일정 (메뉴의 "일정 색"에서 고름, 기본 연핑크 / 하늘).
 *  - mine·partner: 진한 색 (왼쪽 선·점·이름)
 *  - mineFill·partnerFill: 파스텔 바탕 (달력 막대·칸), 그 위 글자는 onFill
 */
object CoupleColors {
    val mine: Color
        @Composable get() = Color(LocalUiSettings.current.mine.strong)
    val partner: Color
        @Composable get() = Color(LocalUiSettings.current.partner.strong)
    val mineFill: Color
        @Composable get() = Color(LocalUiSettings.current.mine.fill)
    val partnerFill: Color
        @Composable get() = Color(LocalUiSettings.current.partner.fill)
    val onFill: Color = Color(ON_EVENT_FILL)
    /** 일요일·공휴일 빨강 / 토요일 파랑 */
    val sunday: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0xFFFFB4AB) else Color(0xFFD32F2F)
    val saturday: Color
        @Composable get() = if (LocalDarkTheme.current) Color(0xFFA8C8FF) else Color(0xFF1E62C8)
}

/** 둥글둥글한 모서리: 카드·창·버튼을 기본보다 더 동글게 해서 부드럽고 귀여운 느낌 */
private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 앱 설정의 화면 모드에 따라 다크 모드인지 */
@Composable
fun isAppInDarkTheme(): Boolean {
    val settings by AppSettings.state.collectAsState()
    return when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun UridulTheme(content: @Composable () -> Unit) {
    val settings by AppSettings.state.collectAsState()
    val dark = isAppInDarkTheme()
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalUiSettings provides settings) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = SoftShapes,
            content = content,
        )
    }
}

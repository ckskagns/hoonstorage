package com.uridul.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import com.uridul.app.R

/**
 * 둥근 한글 글꼴 "주아" (배달의민족 주아체, Woowahan Brothers, SIL Open Font License 1.1).
 * 라이선스 문서: assets/licenses/Jua-OFL.txt (앱과 함께 배포). 큰 제목·디데이 숫자에만 쓰고 본문은 기본 글꼴.
 * 주아는 굵기가 하나라 굵게 바꾸지 않는다(가짜 굵게 그리면 뭉개져서).
 */
val JuaFamily = FontFamily(Font(R.font.jua))

private fun TextStyle.jua() = copy(fontFamily = JuaFamily, fontSynthesis = FontSynthesis.None)

/** 기본 글자 모양에서 큰 제목(display·headline·titleLarge)만 주아로 */
internal fun uridulTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.jua(),
        displayMedium = base.displayMedium.jua(),
        displaySmall = base.displaySmall.jua(),
        headlineLarge = base.headlineLarge.jua(),
        headlineMedium = base.headlineMedium.jua(),
        headlineSmall = base.headlineSmall.jua(),
        titleLarge = base.titleLarge.jua(),
    )
}

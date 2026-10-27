package com.uridul.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartLayoutTest {

    private fun estimatedWidth(text: String, textDp: Float): Float = heartRowWidthDp(text, textDp)

    @Test
    fun fitsInsideCommonWidgetSizes() {
        // 기본 4×2, 폰 2×2, 폴드 커버 2×1, 잠금 화면 작은 칸, 넓은 4×1, 폴드 펼침 4×2(넓은 칸)
        val sizes = listOf(250f to 110f, 120f to 120f, 120f to 57f, 57f to 57f, 250f to 57f, 180f to 100f, 380f to 170f)
        for ((w, h) in sizes) {
            for (text in listOf("7일", "103일", "1000일", "D-12")) {
                val layout = heartLayout(w, h, text)
                assertTrue("$text @ ${w}x$h 너비 초과", estimatedWidth(text, layout.textDp) <= w + 1f || layout.textDp == 12f)
                assertTrue("$text @ ${w}x$h 높이 초과", layout.textDp * 1.3f + 8f <= h || layout.textDp == 12f)
            }
        }
    }

    @Test
    fun defaultFourByTwoIsBigAndShowsDate() {
        val layout = heartLayout(250f, 110f, "103일")
        assertTrue("4×2 에서는 숫자가 충분히 커야 함: ${layout.textDp}", layout.textDp >= 40f)
        assertTrue(layout.showSince)
    }

    @Test
    fun showsSinceDateOnlyWhenThereIsRoom() {
        assertTrue(heartLayout(120f, 120f, "103일").showSince)
        assertFalse(heartLayout(120f, 40f, "103일").showSince)
    }
}

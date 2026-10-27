package com.uridul.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartLayoutTest {

    private fun estimatedWidth(text: String, textDp: Float): Float = heartRowWidthDp(text, textDp)

    @Test
    fun fitsInsideCommonWidgetSizes() {
        // 폰 2×2, 폴드 커버 2×1, 잠금 화면 작은 칸, 넓은 4×1
        val sizes = listOf(120f to 120f, 120f to 57f, 57f to 57f, 250f to 57f, 180f to 100f)
        for ((w, h) in sizes) {
            for (text in listOf("7일", "103일", "1000일", "D-12")) {
                val layout = heartLayout(w, h, text)
                assertTrue("$text @ ${w}x$h 너비 초과", estimatedWidth(text, layout.textDp) <= w + 1f || layout.textDp == 12f)
                assertTrue("$text @ ${w}x$h 높이 초과", layout.textDp * 1.3f + 8f <= h || layout.textDp == 12f)
            }
        }
    }

    @Test
    fun showsSinceDateOnlyWhenThereIsRoom() {
        assertTrue(heartLayout(120f, 120f, "103일").showSince)
        assertFalse(heartLayout(120f, 40f, "103일").showSince)
    }
}

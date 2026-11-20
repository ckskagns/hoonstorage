package com.uridul.app.ui

import com.uridul.app.ui.theme.PhotoTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTintTest {

    @Test
    fun hslRoundTrip() {
        val (h, s, l) = PhotoTint.hsl(0xFFD14D72.toInt()) // 지금 앱 분홍
        assertEquals(343f, h, 1.5f)
        val back = PhotoTint.argb(h, s, l)
        assertTrue(kotlin.math.abs((back shr 16 and 0xFF) - 0xD1) <= 2)
    }

    @Test
    fun grayPhotoKeepsPink() {
        assertNull(PhotoTint.tones(0xFF808080.toInt(), dark = false))
        assertNull(PhotoTint.tones(0xFF8A8580.toInt(), dark = true))
    }

    @Test
    fun textStaysReadableForEveryHue() {
        // 색상환을 15도씩 돌며 강조색 위 글자, 바탕 위 글자 대비 확인 (WCAG 큰 글자 3:1, 본문 4.5:1)
        for (h in 0 until 360 step 15) {
            val seed = PhotoTint.argb(h.toFloat(), 0.7f, 0.5f)
            for (dark in listOf(false, true)) {
                val t = PhotoTint.tones(seed, dark)!!
                val onPrimary = PhotoTint.contrast(t.primary, t.onPrimary)
                val onContainer = PhotoTint.contrast(t.primaryContainer, t.onPrimaryContainer)
                assertTrue("h=$h dark=$dark onPrimary=$onPrimary", onPrimary >= 3.0)
                assertTrue("h=$h dark=$dark onContainer=$onContainer", onContainer >= 4.5)
            }
        }
    }
}

class PhotoSeedTest {
    @Test
    fun picksColorfulOverGrayEvenIfSmaller() {
        val gray = Triple(0xFF808878.toInt(), 0.06f, 0.50f) to 5000
        val green = Triple(0xFF3E7A3A.toInt(), 0.35f, 0.35f) to 400
        val orange = Triple(0xFFE08A2C.toInt(), 0.75f, 0.53f) to 60
        // 채도 × √양: 초록 0.35×20=7.0 > 주황 0.75×7.7=5.8
        assertEquals(0xFF3E7A3A.toInt(), com.uridul.app.ui.theme.PhotoSeed.pick(listOf(gray, green, orange)))
        // 아주 어둡거나 하얀 색, 회색만 있으면 없음 (분홍 그대로)
        val black = Triple(0xFF101010.toInt(), 0.5f, 0.06f) to 9000
        assertNull(com.uridul.app.ui.theme.PhotoSeed.pick(listOf(gray, black)))
    }
}

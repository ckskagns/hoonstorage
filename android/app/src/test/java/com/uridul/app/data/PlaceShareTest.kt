package com.uridul.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceShareTest {
    @Test
    fun naverShare() {
        val p = PlaceShare.parse("[네이버 지도]\n선릉감자탕\n서울 강남구 선릉로 123\nhttps://naver.me/abcDEF")!!
        assertEquals("선릉감자탕", p.name)
        assertEquals("https://naver.me/abcDEF", p.url)
        assertEquals(MapApp.NAVER, p.app)
    }

    @Test
    fun kakaoShareWithNameOnBracketLine() {
        val p = PlaceShare.parse("[카카오맵] 선릉감자탕\n서울 강남구 선릉로 123\nhttps://kko.to/xYz12")!!
        assertEquals("선릉감자탕", p.name)
        assertEquals(MapApp.KAKAO, p.app)
    }

    @Test
    fun googleShareUrlOnlyUsesSubject() {
        val p = PlaceShare.parse("https://maps.app.goo.gl/AbC123", subject = "선릉감자탕")!!
        assertEquals("선릉감자탕", p.name)
        assertEquals(MapApp.GOOGLE, p.app)
    }

    @Test
    fun plainTextWithoutLink() {
        val p = PlaceShare.parse("성수동 파스타집")!!
        assertEquals("성수동 파스타집", p.name)
        assertNull(p.url)
        assertNull(p.app)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(PlaceShare.parse("  ", null))
    }
}

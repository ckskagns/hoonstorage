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

    @Test
    fun appUriFromPlaceLinks() {
        val pkg = "com.uridul.app"
        assertEquals(
            "nmap://place?id=2023130036&appname=com.uridul.app",
            PlaceLinks.appUri(MapApp.NAVER, "https://map.naver.com/p/entry/place/2023130036?placePath=%2Fhome", pkg),
        )
        assertEquals(
            "nmap://place?id=1936878162&appname=com.uridul.app",
            PlaceLinks.appUri(MapApp.NAVER, "https://m.place.naver.com/restaurant/1936878162/home", pkg),
        )
        assertEquals("kakaomap://place?id=12345678", PlaceLinks.appUri(MapApp.KAKAO, "https://place.map.kakao.com/12345678", pkg))
        assertEquals(
            "https://www.google.com/maps/place/abc",
            PlaceLinks.appUri(MapApp.GOOGLE, "https://www.google.com/maps/place/abc", pkg),
        )
        assertNull(PlaceLinks.appUri(MapApp.NAVER, "https://naver.me/abc", pkg))
        assertEquals(true, PlaceLinks.isShortLink("https://naver.me/abc"))
    }
}

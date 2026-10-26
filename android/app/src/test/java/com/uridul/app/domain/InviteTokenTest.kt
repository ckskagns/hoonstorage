package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteTokenTest {

    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"

    @Test
    fun `생성된 토큰은 43자이고 매번 다르다`() {
        val a = InviteToken.generate()
        val b = InviteToken.generate()
        assertEquals(43, a.length)
        assertNotEquals(a, b)
        assertEquals(a, InviteToken.parse(a))
    }

    @Test
    fun `https 링크, 앱 스킴 링크, 토큰 단독을 모두 인식한다`() {
        assertEquals(token, InviteToken.parse("https://my-app.web.app/invite/$token"))
        assertEquals(token, InviteToken.parse("uridul://invite/$token"))
        assertEquals(token, InviteToken.parse("  $token  "))
        assertEquals(token, InviteToken.parse("우리둘 앱에서 연결해줘\nhttps://my-app.web.app/invite/$token\n(24시간)"))
    }

    @Test
    fun `짧거나 이상한 문자열은 거부한다`() {
        assertNull(InviteToken.parse("https://my-app.web.app/invite/short"))
        assertNull(InviteToken.parse("hello world"))
        assertNull(InviteToken.parse(""))
    }

    @Test
    fun `링크 생성`() {
        assertEquals("https://h.web.app/invite/$token", InviteToken.link("h.web.app", token))
    }
}

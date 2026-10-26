package com.uridul.app.domain

import java.security.SecureRandom
import java.util.Base64

/** 1회용 초대 토큰 생성·파싱. 토큰 자체가 비밀번호 역할을 하므로 충분히 길게(256비트) 만든다. */
object InviteToken {

    private val TOKEN = Regex("[A-Za-z0-9_-]{32,64}")
    private val IN_LINK = Regex("invite/([A-Za-z0-9_-]{32,64})")

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * 사용자가 붙여넣은 문자열에서 토큰을 찾는다.
     * 지원: https://<host>/invite/<토큰>, uridul://invite/<토큰>, 토큰만 단독.
     */
    fun parse(input: String): String? {
        val s = input.trim()
        if (TOKEN.matches(s)) return s
        return IN_LINK.find(s)?.groupValues?.get(1)
    }

    fun link(host: String, token: String): String = "https://$host/invite/$token"
}

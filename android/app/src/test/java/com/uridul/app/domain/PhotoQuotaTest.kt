package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhotoQuotaTest {

    @Test
    fun weeklyLimitFitsThreeYearsInFreeStorage() {
        // 900MB ÷ 241KB ≈ 3,915장, 3년 ≈ 156.5주 → 한 주 25장
        assertEquals(3915L, PhotoQuota.TOTAL_PHOTOS)
        assertEquals(25, PhotoQuota.WEEKLY_LIMIT)
        // 한 주 25장을 3년 꽉 채워 올려도 사진 몫(900MB) 안
        val used = PhotoQuota.WEEKLY_LIMIT * PhotoQuota.PLAN_WEEKS * PhotoQuota.MAX_BYTES_PER_PHOTO
        assertTrue(used <= PhotoQuota.BUDGET_BYTES)
        // 무료 저장 공간 1GiB 안
        assertTrue(PhotoQuota.BUDGET_BYTES < 1024L * 1024 * 1024)
    }

    @Test
    fun weekStartsOnMonday() {
        // 2026-10-26 (월) ~ 11-01 (일) 은 같은 주
        val monday = LocalDate.of(2026, 10, 26)
        val sunday = LocalDate.of(2026, 11, 1)
        assertEquals(PhotoQuota.weekKey(monday), PhotoQuota.weekKey(sunday))
        assertEquals("2026-W44", PhotoQuota.weekKey(LocalDate.of(2026, 10, 28)))
        assertTrue(PhotoQuota.weekKey(sunday) != PhotoQuota.weekKey(sunday.plusDays(1)))
        assertEquals(monday..sunday, PhotoQuota.weekRange(LocalDate.of(2026, 10, 28)))
        // 연말: 2026-12-31 (목) 은 2026년 53주
        assertEquals("2026-W53", PhotoQuota.weekKey(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun remainingIsSmallerOfWeekAndEvent() {
        assertEquals(10, PhotoQuota.remaining(weekUsed = 0, eventUsed = 0))
        assertEquals(3, PhotoQuota.remaining(weekUsed = 22, eventUsed = 0))
        assertEquals(2, PhotoQuota.remaining(weekUsed = 5, eventUsed = 8))
        assertEquals(0, PhotoQuota.remaining(weekUsed = 25, eventUsed = 0))
        assertEquals(0, PhotoQuota.remaining(weekUsed = 30, eventUsed = 0))
    }
}

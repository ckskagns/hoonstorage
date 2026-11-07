package com.uridul.app.ui

import android.app.usage.UsageEvents
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 장소 찾기: 지도 앱이 켜져 있는지 판단 */
class MapAppStateTest {
    private val hour = 60L * 60 * 1000

    @Test fun recentlyOpenedCountsAsRunning() {
        val now = 100 * hour
        assertTrue(MapAppState.isRecent(now - 10 * 60 * 1000, now)) // 10분 전에 열었음
        assertFalse(MapAppState.isRecent(now - 4 * hour, now)) // 4시간 전
        assertFalse(MapAppState.isRecent(0L, now)) // 연 적 없음
        assertFalse(MapAppState.isRecent(now + hour, now)) // 시계가 뒤로 간 경우
    }

    @Test fun aliveWhileAnyScreenNotClosed() {
        val resumed = UsageEvents.Event.ACTIVITY_RESUMED
        val stopped = UsageEvents.Event.ACTIVITY_STOPPED
        val destroyed = MapAppState.ACTIVITY_DESTROYED
        assertTrue(MapAppState.isAlive(listOf(stopped))) // 뒤로 숨어 있음
        assertTrue(MapAppState.isAlive(listOf(destroyed, resumed))) // 검색 화면은 닫혔지만 지도 화면은 열려 있음
        assertFalse(MapAppState.isAlive(listOf(destroyed))) // 최근 앱에서 지웠음
        assertFalse(MapAppState.isAlive(emptyList())) // 오늘 쓴 적 없음
    }
}

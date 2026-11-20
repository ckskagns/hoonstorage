package com.uridul.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** "방금", "12분 전", "3시간 전", "어제", "4일 전", "11월 2일", "2025년 3월 2일" (위젯·알림용 짧은 시각) */
object TimeAgo {
    fun text(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.of("Asia/Seoul")): String {
        val diff = (nowMillis - atMillis).coerceAtLeast(0)
        val minutes = diff / 60_000
        val day = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            days == 0L -> "${minutes / 60}시간 전"
            days == 1L -> "어제"
            days < 7 -> "${days}일 전"
            day.year == today.year -> "${day.monthValue}월 ${day.dayOfMonth}일"
            else -> "${day.year}년 ${day.monthValue}월 ${day.dayOfMonth}일"
        }
    }
}

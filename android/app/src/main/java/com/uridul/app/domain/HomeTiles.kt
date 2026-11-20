package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.WishPlace
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 홈 벤토 칸에 쓸 글자 (다음 일정 · 이번 달 데이트 비용 · 가고 싶은 곳 · 이번 주 사진).
 */
object HomeTiles {

    /** 다음 일정이 언제인지: "지금 진행 중", "오늘", "내일", "모레", "D-5 · 11월 25일 (수)" */
    fun whenText(event: CalendarEvent, today: LocalDate): String {
        if (event.date.isBefore(today) && !event.endDate.isBefore(today)) return "진행 중 · ${event.endDate.monthValue}월 ${event.endDate.dayOfMonth}일까지"
        val days = ChronoUnit.DAYS.between(today, event.date)
        val day = "${event.date.monthValue}월 ${event.date.dayOfMonth}일 (${event.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
        // 앱 다른 곳과 같은 "오후 6:30"
        val time = event.time?.let { " " + it.format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)) }.orEmpty()
        return when (days) {
            0L -> "오늘$time"
            1L -> "내일$time"
            2L -> "모레$time"
            else -> "D-$days · $day"
        }
    }

    /** 아직 안 가 본 곳 중 오늘의 한 곳 (날마다 바뀜, 같은 날은 같은 곳) */
    fun wishOfDay(wishes: List<WishPlace>, today: LocalDate): WishPlace? {
        val open = wishes.filter { !it.visited }.sortedBy { it.id }
        if (open.isEmpty()) return null
        return open[Math.floorMod(today.toEpochDay(), open.size.toLong()).toInt()]
    }
}

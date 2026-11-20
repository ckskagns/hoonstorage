package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.DayPhoto
import com.uridul.app.data.Repeat
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 일정 날짜를 고치면 그 일정의 사진·후기도 함께 옮긴다.
 * 사진·후기는 "일정 ID + 그 날 시작일" 로 묶여 있어서, 날짜만 바뀌면 예전 날에 남아 버린다.
 *  - 반복 안 하는 일정: 원래 시작일 → 새 시작일
 *  - 반복 일정: 몇 번째 반복인지 그대로 (매주 3번째 → 새 날짜에서도 3번째)
 *  - 반복 방식이 바뀌면 첫 날 것만 옮기고 나머지는 그대로 둔다
 */
object MemoryMove {

    /** 사진·후기를 옮겨야 하는 고침인지 */
    fun needed(old: CalendarEvent, newStart: LocalDate): Boolean = old.date != newStart

    /** [day] 에 묶인 사진·후기를 옮길 날짜 (옮길 게 아니면 null). [old] 는 고치기 전 원본(첫 날 기준) */
    fun target(old: CalendarEvent, newStart: LocalDate, newRepeat: Repeat?, day: LocalDate): LocalDate? {
        if (!needed(old, newStart)) return null
        val k = index(old.date, old.repeat, day) ?: return null
        if (newRepeat != old.repeat) return if (k == 0L) newStart else null
        val moved = when (newRepeat) {
            null -> newStart
            Repeat.WEEKLY -> newStart.plusWeeks(k)
            // 31일처럼 없는 달은 반복이 건너뛰므로 옮길 곳도 없다
            Repeat.MONTHLY -> newStart.plusMonths(k).takeIf { it.dayOfMonth == newStart.dayOfMonth }
            Repeat.YEARLY -> MonthDay.from(newStart).atYear(newStart.year + k.toInt())
        }
        return moved?.takeIf { it != day }
    }

    /** [day] 가 반복의 몇 번째 날인지 (첫 날 0). 그 일정의 날이 아니면 null */
    private fun index(start: LocalDate, repeat: Repeat?, day: LocalDate): Long? = when (repeat) {
        null -> if (day == start) 0L else null
        Repeat.WEEKLY -> ChronoUnit.DAYS.between(start, day).takeIf { it >= 0 && it % 7 == 0L }?.div(7)
        Repeat.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(day))
            .takeIf { it >= 0 && day.dayOfMonth == start.dayOfMonth }
        Repeat.YEARLY -> (day.year - start.year).toLong()
            .takeIf { it >= 0 && MonthDay.from(start).atYear(day.year) == day }
    }

    /**
     * 사진 달력에 저장해 둔 칸 중 일정이 다른 날로 옮겨진 것 (상대 폰에서 고친 경우).
     * 같은 달 안에서 옮기면 그 달 사진 수가 같아 저장해 둔 달력이 그대로 쓰이므로, 이것으로 다시 받을 달을 찾는다.
     * 반복 안 하는 일정만 본다 (사진의 날 = 일정 시작일).
     */
    fun staleMonths(dayPhotos: Map<LocalDate, DayPhoto>, events: List<CalendarEvent>): Set<YearMonth> {
        val starts = events.filter { it.repeat == null && it.seriesStart == null }.associate { it.id to it.date }
        return dayPhotos.mapNotNull { (day, p) ->
            val start = starts[p.eventId] ?: return@mapNotNull null
            if (start != day) YearMonth.from(day) else null
        }.toSet()
    }
}

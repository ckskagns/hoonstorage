package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Repeat
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 반복 일정 펼치기. 서버에는 반복 일정이 하나(첫 날 기준)로만 저장되고, 화면·위젯·알림에서 날짜별로 펼친다.
 *  - 매주: 같은 요일마다
 *  - 매달: 같은 날짜마다 (31일처럼 없는 달은 건너뛴다)
 *  - 매년: 같은 월·일마다 (2월 29일은 평년에 2월 28일)
 *  - 뺀 날(skip)은 건너뛴다
 */
object Recurrence {

    /** 기간 [from, to] 에 걸치는 반복 시작일들 (첫 날 이후만) */
    fun occurrenceStarts(e: CalendarEvent, from: LocalDate, to: LocalDate, limit: Int = 400): List<LocalDate> {
        val repeat = e.repeat ?: return if (!e.endDate.isBefore(from) && !e.date.isAfter(to)) listOf(e.date) else emptyList()
        if (to.isBefore(from)) return emptyList()
        val span = ChronoUnit.DAYS.between(e.date, e.endDate)
        // 시작일이 이 날 이후면 기간에 걸칠 수 있다
        val earliest = maxOf(e.date, from.minusDays(span))
        val result = mutableListOf<LocalDate>()
        fun take(s: LocalDate) {
            if (!s.isBefore(earliest) && !s.isAfter(to) && s !in e.skip) result += s
        }
        when (repeat) {
            Repeat.WEEKLY -> {
                val weeks = (ChronoUnit.DAYS.between(e.date, earliest) + 6) / 7
                var s = e.date.plusWeeks(weeks)
                while (!s.isAfter(to) && result.size < limit) {
                    take(s)
                    s = s.plusWeeks(1)
                }
            }
            Repeat.MONTHLY -> {
                var ym = YearMonth.from(earliest)
                val last = YearMonth.from(to)
                val day = e.date.dayOfMonth
                while (!ym.isAfter(last) && result.size < limit) {
                    if (day <= ym.lengthOfMonth()) take(ym.atDay(day))
                    ym = ym.plusMonths(1)
                }
            }
            Repeat.YEARLY -> {
                val md = MonthDay.from(e.date)
                for (y in earliest.year..to.year) {
                    if (result.size >= limit) break
                    take(md.atYear(y))
                }
            }
        }
        return result
    }

    /** 펼친 일정 하나 (그 날 기준 날짜 + 원래 첫 날) */
    fun occurrence(e: CalendarEvent, start: LocalDate): CalendarEvent {
        if (e.repeat == null) return e
        val span = ChronoUnit.DAYS.between(e.date, e.endDate)
        return e.copy(date = start, endDate = start.plusDays(span), seriesStart = e.date)
    }

    /** 반복 일정을 기간 안의 날짜별로 펼친다 (반복 아닌 일정은 그대로) */
    fun expand(events: List<CalendarEvent>, from: LocalDate, to: LocalDate): List<CalendarEvent> =
        events.flatMap { e ->
            if (e.repeat == null) listOf(e) else occurrenceStarts(e, from, to).map { occurrence(e, it) }
        }

    /**
     * 날짜 범위로 받아온 일정 + 반복 일정 목록을 합친다.
     * (범위 조회에 반복 일정 원본이 섞여 오면 빼고, 반복 일정은 펼쳐서 넣는다)
     */
    fun merge(plain: List<CalendarEvent>, recurring: List<CalendarEvent>, from: LocalDate, to: LocalDate): List<CalendarEvent> =
        plain.filter { it.repeat == null } + expand(recurring.filter { it.repeat != null }, from, to)

    /** 오늘 이후 가장 가까운 날 (진행 중이면 그 날). 없으면 null */
    fun next(e: CalendarEvent, today: LocalDate): CalendarEvent? {
        if (e.repeat == null) return if (!e.endDate.isBefore(today)) e else null
        val start = occurrenceStarts(e, today, today.plusYears(2), limit = 1).firstOrNull() ?: return null
        return occurrence(e, start)
    }
}

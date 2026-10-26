package com.uridul.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 디데이 계산.
 *
 * 한국식(countFromOne = true)은 사귄 첫날을 1일로 센다.
 * 예) 1월 1일에 사귀기 시작 → 1월 1일 = 1일, 4월 10일 = 100일.
 */
object DDay {

    data class Milestone(
        val label: String,
        val date: LocalDate,
        val daysLeft: Long,
    )

    /** 오늘이 며칠째인지. 시작일이 아직 오지 않았으면 음수(남은 일수의 음수)를 돌려준다. */
    fun dayNumber(start: LocalDate, today: LocalDate, countFromOne: Boolean): Long {
        val diff = ChronoUnit.DAYS.between(start, today)
        return if (diff >= 0 && countFromOne) diff + 1 else diff
    }

    /** 홈 화면 큰 숫자: "523일" 또는 시작 전이면 "D-12". */
    fun heroText(start: LocalDate, today: LocalDate, countFromOne: Boolean): String {
        val n = dayNumber(start, today, countFromOne)
        return if (n < 0) "D${n}" else "${n}일"
    }

    /** 위젯용 짧은 표기: "D+523" 또는 "D-12". */
    fun shortText(start: LocalDate, today: LocalDate, countFromOne: Boolean): String {
        val n = dayNumber(start, today, countFromOne)
        return if (n < 0) "D${n}" else "D+${n}"
    }

    /** 남은 일수 표기: "D-12", 당일은 "D-Day". */
    fun daysLeftText(daysLeft: Long): String = if (daysLeft == 0L) "D-Day" else "D-$daysLeft"

    /**
     * 오늘(포함) 이후 다가오는 기념일. 100일 단위 기념일과 매년 주년을 합쳐 날짜순으로 돌려준다.
     */
    fun upcoming(
        start: LocalDate,
        today: LocalDate,
        countFromOne: Boolean,
        limit: Int = 3,
    ): List<Milestone> {
        if (limit <= 0) return emptyList()
        val offset = if (countFromOne) 1L else 0L
        val current = dayNumber(start, today, countFromOne).coerceAtLeast(0)
        val candidates = mutableListOf<Milestone>()

        // 100일, 200일, ...
        val firstHundred = maxOf(1L, current / 100)
        for (k in firstHundred until firstHundred + limit + 1) {
            val days = k * 100
            val date = start.plusDays(days - offset)
            candidates += Milestone("${days}일", date, ChronoUnit.DAYS.between(today, date))
        }

        // 1주년, 2주년, ... (2월 29일 시작이면 평년에는 2월 28일)
        val firstYear = maxOf(1L, ChronoUnit.YEARS.between(start, today))
        for (y in firstYear until firstYear + limit + 1) {
            val date = start.plusYears(y)
            candidates += Milestone("${y}주년", date, ChronoUnit.DAYS.between(today, date))
        }

        return candidates
            .filter { !it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.label }))
            .take(limit)
    }
}

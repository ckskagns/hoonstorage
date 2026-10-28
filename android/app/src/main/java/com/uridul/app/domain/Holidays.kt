package com.uridul.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 한국 공휴일 (관공서의 공휴일에 관한 규정 기준) + 대체공휴일.
 *
 *  - 음력 공휴일(설날·부처님오신날·추석)은 음력 계산 대신 확인한 양력 날짜를 표로 둔다
 *    (2025~2031, 한국 음력(단기력) 변환으로 확인. 칠석 표와 같은 방식)
 *  - 노동절(5/1)·제헌절(7/17)은 2026년부터 공휴일 (2026-04 개정령)
 *  - 선거일은 확정된 날만 (임시공휴일은 정부가 따로 정하므로 넣지 않는다)
 *  - 대체공휴일
 *      설날·추석 연휴: 일요일 또는 다른 공휴일과 겹친 날 수만큼
 *      어린이날: 토·일요일 또는 다른 공휴일과 겹치면
 *      삼일절·노동절·부처님오신날·제헌절·광복절·개천절·한글날·성탄절: 토·일요일과 겹치면
 *      → 연휴 다음의 첫 번째 평일(공휴일이 아닌 날)
 *  - 신정·현충일·선거일은 대체공휴일이 없다
 */
object Holidays {

    data class Holiday(
        val date: LocalDate,
        val name: String,
        val emoji: String,
        val substitute: Boolean = false,
    )

    /** 표가 있는 해 (이 밖의 해는 양력 공휴일만) */
    val lunarYears = 2025..2031

    private data class Lunar(val seol: LocalDate, val buddha: LocalDate, val chuseok: LocalDate)

    private val lunar = mapOf(
        2025 to Lunar(LocalDate.of(2025, 1, 29), LocalDate.of(2025, 5, 5), LocalDate.of(2025, 10, 6)),
        2026 to Lunar(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 5, 24), LocalDate.of(2026, 9, 25)),
        2027 to Lunar(LocalDate.of(2027, 2, 7), LocalDate.of(2027, 5, 13), LocalDate.of(2027, 9, 15)),
        2028 to Lunar(LocalDate.of(2028, 1, 27), LocalDate.of(2028, 5, 2), LocalDate.of(2028, 10, 3)),
        2029 to Lunar(LocalDate.of(2029, 2, 13), LocalDate.of(2029, 5, 20), LocalDate.of(2029, 9, 22)),
        2030 to Lunar(LocalDate.of(2030, 2, 3), LocalDate.of(2030, 5, 9), LocalDate.of(2030, 9, 12)),
        2031 to Lunar(LocalDate.of(2031, 1, 23), LocalDate.of(2031, 5, 28), LocalDate.of(2031, 10, 1)),
    )

    /** 확정된 선거일 (공휴일, 대체공휴일 없음) */
    private val elections = mapOf(
        LocalDate.of(2025, 6, 3) to "대통령 선거일",
        LocalDate.of(2026, 6, 3) to "지방선거일",
        LocalDate.of(2028, 4, 12) to "국회의원 선거일",
    )

    private enum class Rule { NONE, WEEKEND, WEEKEND_OR_OVERLAP, SUNDAY_OR_OVERLAP }

    /** 연휴 하나 (설날·추석은 3일, 나머지는 하루) */
    private class Group(val days: List<Holiday>, val rule: Rule)

    private val cache = ConcurrentHashMap<Int, List<Holiday>>()

    /** 그 해의 공휴일 (날짜순, 대체공휴일 포함) */
    fun ofYear(year: Int): List<Holiday> = cache.getOrPut(year) { build(year) }

    fun on(date: LocalDate): List<Holiday> = ofYear(date.year).filter { it.date == date }

    fun isHoliday(date: LocalDate): Boolean = ofYear(date.year).any { it.date == date }

    fun between(from: LocalDate, to: LocalDate): List<Holiday> {
        if (to.isBefore(from)) return emptyList()
        return (from.year..to.year).flatMap { ofYear(it) }.filter { it.date in from..to }
    }

    private fun build(year: Int): List<Holiday> {
        fun day(m: Int, d: Int, name: String, emoji: String, rule: Rule) =
            Group(listOf(Holiday(LocalDate.of(year, m, d), name, emoji)), rule)

        fun threeDays(center: LocalDate, name: String, emoji: String) = Group(
            listOf(
                Holiday(center.minusDays(1), "$name 연휴", emoji),
                Holiday(center, name, emoji),
                Holiday(center.plusDays(1), "$name 연휴", emoji),
            ),
            Rule.SUNDAY_OR_OVERLAP,
        )

        val groups = buildList {
            add(day(1, 1, "신정", "🎉", Rule.NONE))
            add(day(3, 1, "삼일절", "🇰🇷", Rule.WEEKEND))
            if (year >= 2026) add(day(5, 1, "노동절", "🛠️", Rule.WEEKEND))
            add(day(5, 5, "어린이날", "🎈", Rule.WEEKEND_OR_OVERLAP))
            add(day(6, 6, "현충일", "🕊️", Rule.NONE))
            if (year >= 2026) add(day(7, 17, "제헌절", "📜", Rule.WEEKEND))
            add(day(8, 15, "광복절", "🇰🇷", Rule.WEEKEND))
            add(day(10, 3, "개천절", "🇰🇷", Rule.WEEKEND))
            add(day(10, 9, "한글날", "✍️", Rule.WEEKEND))
            add(day(12, 25, "성탄절", "🎄", Rule.WEEKEND))
            lunar[year]?.let { l ->
                add(threeDays(l.seol, "설날", "🧧"))
                add(Group(listOf(Holiday(l.buddha, "부처님오신날", "🪷")), Rule.WEEKEND))
                add(threeDays(l.chuseok, "추석", "🌕"))
            }
            elections.filterKeys { it.year == year }.forEach { (d, name) ->
                add(Group(listOf(Holiday(d, name, "🗳️")), Rule.NONE))
            }
        }

        val all = groups.flatMap { it.days }
        val taken = all.map { it.date }.toMutableSet()
        val substitutes = mutableListOf<Holiday>()

        fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        /** 같은 날 다른 공휴일이 또 있는지 */
        fun overlaps(g: Group, d: LocalDate) = all.any { it.date == d && it !in g.days }

        groups.sortedBy { it.days.last().date }.forEach { g ->
            val needed = when (g.rule) {
                Rule.NONE -> 0
                Rule.WEEKEND -> g.days.count { weekend(it.date) }
                Rule.WEEKEND_OR_OVERLAP -> g.days.count { weekend(it.date) || overlaps(g, it.date) }
                Rule.SUNDAY_OR_OVERLAP -> g.days.count { it.date.dayOfWeek == DayOfWeek.SUNDAY || overlaps(g, it.date) }
            }
            var next = g.days.last().date
            repeat(needed) {
                do {
                    next = next.plusDays(1)
                } while (weekend(next) || next in taken)
                taken += next
                substitutes += Holiday(next, "대체공휴일", "🌿", substitute = true)
            }
        }
        return (all + substitutes).sortedBy { it.date }
    }
}

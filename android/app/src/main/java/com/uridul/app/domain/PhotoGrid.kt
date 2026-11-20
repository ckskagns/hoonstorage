package com.uridul.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * 일기장 "모아보기": 사진이 있는 날을 달마다 묶어 격자로 (한 날 = 한 칸, 그날 대표 사진 + 나머지 장수).
 */
object PhotoGrid {

    /** 한 달 묶음: 최근 날부터, 머리글에 쓸 사진 수 */
    data class Section(val month: YearMonth, val days: List<LocalDate>, val photos: Int) {
        val header: String get() = "${month.year}년 ${month.monthValue}월 · 사진 ${photos}장 · ${days.size}일"
    }

    /** 최근 달부터, 달 안에서도 최근 날부터 */
    fun sections(photoCounts: Map<LocalDate, Int>): List<Section> =
        photoCounts.filterValues { it > 0 }
            .entries.groupBy { YearMonth.from(it.key) }
            .toSortedMap(compareByDescending { it })
            .map { (m, list) -> Section(m, list.map { it.key }.sortedDescending(), list.sumOf { it.value }) }

    /**
     * 모아보기에서 사진 수를 물어볼 달: 사귄 달부터 다음 달까지 (일정에 미리 올린 사진이 있을 수 있어서).
     * 달마다 사진 수만 물으니(사진은 안 받음) 몇 년치여도 가볍다. 너무 오래된 시작일은 5년까지만.
     */
    fun monthsToLoad(start: LocalDate, today: LocalDate): List<YearMonth> {
        val last = YearMonth.from(today).plusMonths(1)
        val first = maxOf(YearMonth.from(start), YearMonth.from(today).minusYears(5))
        return generateSequence(last) { it.minusMonths(1) }.takeWhile { it >= first }.toList()
    }
}

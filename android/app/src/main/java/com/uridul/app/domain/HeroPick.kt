package com.uridul.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 디데이 카드에 깔 "오늘의 사진" 고르기 (사진 달력의 날마다 대표 사진 중에서).
 *  1) 몇 년 전 오늘 사진이 있으면 그것 (최근 해부터)
 *  2) 없으면 지난 사진들을 날마다 하나씩 돌려 보여준다: 사진마다 정해진 순서(섞인 순서)로 줄 세우고
 *     하루에 한 칸씩 넘어가서, 모든 사진을 한 번씩 본 뒤에야 같은 사진이 다시 나온다.
 *  "다른 사진" 을 누르면([skip]) 그 줄에서 다음 사진.
 * 아직 오지 않은 날 사진(일정에 미리 올린 사진)은 추억이 아니라서 뺀다.
 */
object HeroPick {

    data class Candidate(val date: LocalDate, val id: String, val eventId: String)

    fun order(candidates: List<Candidate>, today: LocalDate): List<Candidate> {
        val past = candidates.filter { it.date <= today }.distinctBy { it.id }
        if (past.isEmpty()) return emptyList()
        val yearly = past
            .filter { it.date.year < today.year && it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth }
            .sortedByDescending { it.date }
        val rest = past.filter { it !in yearly }.sortedWith(compareBy<Candidate> { mix(it.id) }.thenBy { it.id })
        if (rest.isEmpty()) return yearly
        val start = Math.floorMod(today.toEpochDay(), rest.size.toLong()).toInt()
        return yearly + rest.drop(start) + rest.take(start)
    }

    fun pick(candidates: List<Candidate>, today: LocalDate, skip: Int): Candidate? {
        val order = order(candidates, today)
        return if (order.isEmpty()) null else order[Math.floorMod(skip, order.size)]
    }

    /** 사진 위 작은 글자: "1년 전 오늘", "오늘", "어제", "3일 전", "11월 14일", "2025년 3월 2일" */
    fun label(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            date.year < today.year && date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth -> "${today.year - date.year}년 전 오늘"
            days == 0L -> "오늘"
            days == 1L -> "어제"
            days in 2..6 -> "${days}일 전"
            date.year == today.year -> "${date.monthValue}월 ${date.dayOfMonth}일"
            else -> "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
        }
    }

    /** 사진 ID 로 정한 섞인 순서 (폰·날짜와 상관없이 늘 같음) */
    private fun mix(id: String): Int {
        var h = 0x2545F491
        for (c in id) h = (h xor c.code) * 0x01000193
        return h
    }
}

package com.uridul.app.domain

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** 생일 (매년 같은 양력 날짜) */
data class Birthday(val name: String, val date: MonthDay)

enum class SpecialKind(val personal: Boolean) {
    /** 100일 단위, 주년 */
    MILESTONE(true),
    BIRTHDAY(true),
    /** 발렌타인데이, 크리스마스 같은 연인 기념일 */
    COUPLE_DAY(false),
}

/** 달력·위젯·알림에 함께 보여줄 특별한 날 */
data class SpecialDay(
    val date: LocalDate,
    val title: String,
    val emoji: String,
    val kind: SpecialKind,
    /** 짧은 설명 (예: "사탕으로 마음 전하는 날") */
    val note: String = "",
) {
    val label: String get() = "$emoji $title"
}

/**
 * 우리 두 사람의 기념일(100일 단위·주년·생일) + 연인 기념일.
 *
 * 연인 기념일: 매달 14일 시리즈, 빼빼로데이, 크리스마스 이브·크리스마스, 새해, 칠석(음력 7월 7일) 등.
 * 14일 시리즈는 자료마다 이름이 조금씩 다른데(8월 뮤직데이, 11월 쿠키데이 등) 가장 널리 쓰는 이름을 골랐다.
 */
object SpecialDays {

    private data class Yearly(val month: Int, val day: Int, val title: String, val emoji: String, val note: String)

    private val yearly = listOf(
        Yearly(1, 1, "새해 첫날", "🎉", "올해도 함께해요"),
        Yearly(1, 14, "다이어리데이", "📔", "한 해 함께 쓸 다이어리를 선물하는 날"),
        Yearly(2, 14, "발렌타인데이", "🍫", "초콜릿으로 마음 전하는 날"),
        Yearly(3, 14, "화이트데이", "🍬", "사탕으로 마음 전하는 날"),
        Yearly(4, 14, "블랙데이", "🖤", "짜장면 먹는 날 (원래는 솔로의 날)"),
        Yearly(5, 14, "로즈데이", "🌹", "장미꽃을 선물하는 날"),
        Yearly(6, 14, "키스데이", "💋", "뽀뽀로 사랑을 확인하는 날"),
        Yearly(7, 14, "실버데이", "💍", "은반지를 나눠 끼는 날"),
        Yearly(8, 14, "그린데이", "🌳", "숲길을 함께 걷는 날"),
        Yearly(9, 14, "포토데이", "📸", "함께 사진 찍는 날"),
        Yearly(10, 14, "와인데이", "🍷", "와인 한 잔 나누는 날"),
        Yearly(11, 11, "빼빼로데이", "🥢", "빼빼로를 주고받는 날"),
        Yearly(11, 14, "무비데이", "🎬", "함께 영화 보는 날"),
        Yearly(12, 14, "허그데이", "🤗", "따뜻하게 안아 주는 날"),
        Yearly(12, 24, "크리스마스 이브", "🎄", "둘이 보내는 크리스마스 이브"),
        Yearly(12, 25, "크리스마스", "🎅", "메리 크리스마스"),
        Yearly(12, 31, "올해 마지막 날", "🎆", "함께 새해 카운트다운"),
    )

    /**
     * 칠석(음력 7월 7일, 견우와 직녀가 만나는 날)의 양력 날짜.
     * 음력 계산 대신 확인된 날짜를 표로 둔다 (2026~2030: datedb.net 음양력 변환 확인).
     */
    private val chilseok = mapOf(
        2025 to LocalDate.of(2025, 8, 29),
        2026 to LocalDate.of(2026, 8, 19),
        2027 to LocalDate.of(2027, 8, 8),
        2028 to LocalDate.of(2028, 8, 26),
        2029 to LocalDate.of(2029, 8, 16),
        2030 to LocalDate.of(2030, 8, 5),
    )

    /** 기간 안의 특별한 날. 날짜별로 우리 기념일 → 생일 → 연인 기념일 순. */
    fun between(
        start: LocalDate,
        countFromOne: Boolean,
        birthdays: List<Birthday>,
        from: LocalDate,
        to: LocalDate,
        includeCoupleDays: Boolean = true,
    ): Map<LocalDate, List<SpecialDay>> {
        if (to.isBefore(from)) return emptyMap()
        val all = mutableListOf<SpecialDay>()

        DDay.milestonesBetween(start, from, to, countFromOne).forEach { (date, label) ->
            all += SpecialDay(date, label, "💕", SpecialKind.MILESTONE, "우리 $label")
        }
        for (year in from.year..to.year) {
            birthdays.forEach { b ->
                val date = b.date.atYear(year) // 2월 29일생은 평년에 2월 28일
                if (date in from..to) all += SpecialDay(date, "${b.name} 생일", "🎂", SpecialKind.BIRTHDAY, "${b.name}의 생일")
            }
            if (includeCoupleDays) {
                yearly.forEach { y ->
                    val date = LocalDate.of(year, y.month, y.day)
                    if (date in from..to) all += SpecialDay(date, y.title, y.emoji, SpecialKind.COUPLE_DAY, y.note)
                }
                chilseok[year]?.takeIf { it in from..to }?.let {
                    all += SpecialDay(it, "칠석", "🌌", SpecialKind.COUPLE_DAY, "견우와 직녀가 만나는 날 (음력 7월 7일)")
                }
            }
        }
        return all
            .groupBy { it.date }
            .mapValues { (_, days) -> days.sortedBy { it.kind.ordinal } }
            .toSortedMap()
    }

    fun on(start: LocalDate, countFromOne: Boolean, birthdays: List<Birthday>, day: LocalDate): List<SpecialDay> =
        between(start, countFromOne, birthdays, day, day)[day].orEmpty()

    /**
     * 홈 화면 "다가오는 기념일": 우리 기념일·생일 [personalLimit]개 + 가까운 연인 기념일 [coupleDayLimit]개를 날짜순으로.
     * 14일 시리즈가 매달 있어서 섞어 세면 우리 기념일이 밀려나기 때문에 따로 센다.
     */
    fun upcoming(
        start: LocalDate,
        countFromOne: Boolean,
        birthdays: List<Birthday>,
        today: LocalDate,
        personalLimit: Int,
        coupleDayLimit: Int,
    ): List<SpecialDay> {
        val all = between(start, countFromOne, birthdays, today, today.plusDays(800)).values.flatten()
        val personal = all.filter { it.kind.personal }.take(personalLimit)
        val coupleDays = all.filter { !it.kind.personal }.take(coupleDayLimit)
        return (personal + coupleDays).sortedWith(compareBy({ it.date }, { it.kind.ordinal }))
    }

    fun daysLeft(today: LocalDate, day: SpecialDay): Long = ChronoUnit.DAYS.between(today, day.date)
}

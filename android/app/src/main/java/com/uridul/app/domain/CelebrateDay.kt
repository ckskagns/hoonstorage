package com.uridul.app.domain

import java.time.LocalDate

/**
 * 오늘이 축하할 날인지: 우리 기념일(100일 단위·주년)·생일만 (연인 기념일·공휴일은 축하 창을 띄우지 않음).
 * 홈 디데이 카드 그림을 "축하해"로 바꾸고, 그날 처음 열 때 한 번 축하 창을 띄우는 데 쓴다.
 */
object CelebrateDay {

    fun today(start: LocalDate, countFromOne: Boolean, birthdays: List<Birthday>, today: LocalDate): SpecialDay? =
        SpecialDays.on(start, countFromOne, birthdays, today)
            .firstOrNull { it.kind == SpecialKind.MILESTONE || it.kind == SpecialKind.BIRTHDAY }

    /** 축하 창 제목: "오늘 우리 100일이에요 🎉" / "오늘은 나나 생일이에요 🎂" */
    fun headline(day: SpecialDay): String = when (day.kind) {
        SpecialKind.MILESTONE -> "오늘 우리 ${day.title}이에요 🎉"
        SpecialKind.BIRTHDAY -> "오늘은 ${day.title}이에요 🎂"
        else -> day.label
    }
}

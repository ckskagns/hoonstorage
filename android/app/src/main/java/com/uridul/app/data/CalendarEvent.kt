package com.uridul.app.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * 공유 캘린더 일정 하나.
 * 날짜는 "yyyy-MM-dd" 문자열로 저장한다(두 사람 모두 한국 시간 기준이라 시간대 변환이 필요 없다).
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate,
    /** null 이면 하루 종일 */
    val time: LocalTime?,
    val memo: String,
    val createdBy: String,
) {
    val allDay: Boolean get() = time == null
    val isMultiDay: Boolean get() = endDate.isAfter(date)

    fun occursOn(day: LocalDate): Boolean = !day.isBefore(date) && !day.isAfter(endDate)

    companion object {
        const val TITLE_MAX = 100
        const val MEMO_MAX = 1000
        /** 여러 날 일정의 최대 길이. 달력 조회 범위를 계산할 때 쓴다. */
        const val MAX_SPAN_DAYS = 31L
    }
}

/** 일정 추가/수정 화면에서 넘어오는 입력값. */
data class EventDraft(
    val id: String? = null,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate,
    val time: LocalTime?,
    val memo: String,
)

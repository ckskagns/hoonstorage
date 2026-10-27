package com.uridul.app.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * 타임라인 한 줄: 시작~끝 시간, 내용(선택), 장소(선택).
 * 여러 날 일정이면 한 줄만 쓰고, 시작은 첫날·끝은 마지막 날의 시각이다.
 */
data class TimeSlot(
    val start: LocalTime,
    val end: LocalTime? = null,
    val title: String = "",
    val place: Place? = null,
) {
    companion object {
        const val TITLE_MAX = 100
        /** 하루에 넣을 수 있는 시간대 수 */
        const val MAX_SLOTS = 12
    }
}

/**
 * 공유 캘린더 일정 하나.
 * 날짜는 "yyyy-MM-dd" 문자열로 저장한다(두 사람 모두 한국 시간 기준이라 시간대 변환이 필요 없다).
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate,
    /** 첫 시간대의 시작 시각. null 이면 하루 종일 */
    val time: LocalTime?,
    val memo: String,
    val createdBy: String,
    /** 하루 종일 일정의 장소 (시간 일정은 시간대마다 장소를 둔다) */
    val place: Place? = null,
    /** 시간 일정의 타임라인 */
    val timeline: List<TimeSlot> = emptyList(),
) {
    val allDay: Boolean get() = time == null
    val isMultiDay: Boolean get() = endDate.isAfter(date)

    /** 화면에 보여줄 시간대 목록 (예전 데이터는 시작 시각 한 줄로) */
    val slots: List<TimeSlot>
        get() = if (time == null) emptyList() else timeline.ifEmpty { listOf(TimeSlot(time, null, "", place)) }

    fun occursOn(day: LocalDate): Boolean = !day.isBefore(date) && !day.isAfter(endDate)

    companion object {
        const val TITLE_MAX = 100
        const val MEMO_MAX = 1000
        /** 여러 날 일정의 최대 길이. 달력 조회 범위를 계산할 때 쓴다. */
        const val MAX_SPAN_DAYS = 31L
    }
}

/** 일정 추가/수정 화면에서 넘어오는 입력값. time 은 첫 시간대의 시작 시각(하루 종일이면 null). */
data class EventDraft(
    val id: String? = null,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate,
    val time: LocalTime?,
    val memo: String,
    val place: Place? = null,
    val timeline: List<TimeSlot> = emptyList(),
)

/** 저장된 일정을 수정용 입력값으로 */
fun CalendarEvent.toDraft(): EventDraft =
    EventDraft(id, title, date, endDate, time, memo, if (time == null) place else null, slots)

package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 달력 화면에 필요한 날짜 계산 (일요일 시작, 한국 달력 기준). */
object CalendarMath {

    /** 달력에 보이는 6주(42칸). 앞뒤 달의 날짜도 포함한다. */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return List(42) { first.plusDays(it.toLong()) }
    }

    /**
     * 달력 한 화면(앞뒤 달 포함)을 그리려고 서버에서 가져올 시작일 범위.
     * 여러 날 일정은 최대 31일이라, 시작일이 31일 앞인 일정까지 포함한다.
     */
    fun queryRange(month: YearMonth): ClosedRange<LocalDate> {
        val from = month.minusMonths(1).atDay(1).minusDays(CalendarEvent.MAX_SPAN_DAYS)
        val to = month.plusMonths(1).atEndOfMonth().plusDays(7)
        return from..to
    }

    /** 하루에 보여줄 일정: 하루 종일 → 시간순 → 제목순 */
    fun eventsOn(events: List<CalendarEvent>, day: LocalDate): List<CalendarEvent> =
        events.filter { it.occursOn(day) }.sortedWith(dayOrder)

    /**
     * 위젯·목록용 다가오는 일정. 이미 시작해서 진행 중인 일정은 오늘 날짜로 정렬한다.
     */
    fun upcoming(events: List<CalendarEvent>, today: LocalDate, limit: Int): List<CalendarEvent> =
        events
            .filter { !it.endDate.isBefore(today) }
            .sortedWith(
                compareBy<CalendarEvent> { maxOf(it.date, today) }
                    .then(dayOrder),
            )
            .take(limit)

    private val dayOrder: Comparator<CalendarEvent> =
        compareBy<CalendarEvent>({ !it.allDay }, { it.time }, { it.title }, { it.id })

    /** 입력값 검사. 문제가 없으면 null, 있으면 사용자에게 보여줄 문구. */
    fun validate(draft: EventDraft): String? {
        val title = draft.title.trim()
        return when {
            title.isEmpty() -> "제목을 입력해 주세요."
            title.length > CalendarEvent.TITLE_MAX -> "제목은 ${CalendarEvent.TITLE_MAX}자까지 쓸 수 있어요."
            draft.memo.length > CalendarEvent.MEMO_MAX -> "메모는 ${CalendarEvent.MEMO_MAX}자까지 쓸 수 있어요."
            draft.endDate.isBefore(draft.date) -> "끝나는 날이 시작하는 날보다 빨라요."
            ChronoUnit.DAYS.between(draft.date, draft.endDate) >= CalendarEvent.MAX_SPAN_DAYS ->
                "여러 날 일정은 ${CalendarEvent.MAX_SPAN_DAYS}일까지 만들 수 있어요."
            else -> null
        }
    }
}

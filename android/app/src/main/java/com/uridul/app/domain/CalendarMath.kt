package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import com.uridul.app.data.TimeSlot
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

    /** 한 주에 걸친 여러 날 일정 막대 하나 (열 번호는 일요일 0 ~ 토요일 6) */
    data class WeekBar(
        val event: CalendarEvent,
        val startCol: Int,
        val endCol: Int,
        val lane: Int,
        /** 지난주부터 이어지는지 (왼쪽 모서리를 둥글게 하지 않음) */
        val continuesLeft: Boolean,
        /** 다음 주로 이어지는지 */
        val continuesRight: Boolean,
    ) {
        val span: Int get() = endCol - startCol + 1
    }

    /**
     * 여러 날 일정을 한 주 단위 막대로 배치한다. 겹치면 아래 줄(lane)로 내린다.
     * [lanesUsed] 는 요일별로 막대가 차지하는 줄 수 (그 아래부터 하루 일정을 그린다).
     */
    data class WeekLayout(val bars: List<WeekBar>, val lanesUsed: List<Int>)

    fun weekLayout(week: List<LocalDate>, events: List<CalendarEvent>): WeekLayout {
        val first = week.first()
        val last = week.last()
        val multi = events
            .filter { it.isMultiDay && !it.endDate.isBefore(first) && !it.date.isAfter(last) }
            .sortedWith(compareBy<CalendarEvent>({ it.date }, { -ChronoUnit.DAYS.between(it.date, it.endDate) }).then(dayOrder))
        val laneEnds = mutableListOf<Int>() // 줄마다 마지막으로 차지한 열
        val bars = multi.map { e ->
            val startCol = ChronoUnit.DAYS.between(first, maxOf(e.date, first)).toInt()
            val endCol = ChronoUnit.DAYS.between(first, minOf(e.endDate, last)).toInt()
            var lane = laneEnds.indexOfFirst { it < startCol }
            if (lane == -1) {
                lane = laneEnds.size
                laneEnds += endCol
            } else {
                laneEnds[lane] = endCol
            }
            WeekBar(e, startCol, endCol, lane, e.date.isBefore(first), e.endDate.isAfter(last))
        }
        val lanesUsed = List(week.size) { col ->
            (bars.filter { col in it.startCol..it.endCol }.maxOfOrNull { it.lane } ?: -1) + 1
        }
        return WeekLayout(bars, lanesUsed)
    }

    /** 하루 일정만 (여러 날 일정은 막대로 따로 그린다) */
    fun singleDayEventsOn(events: List<CalendarEvent>, day: LocalDate): List<CalendarEvent> =
        eventsOn(events, day).filter { !it.isMultiDay }

    /** 입력값 검사. 문제가 없으면 null, 있으면 사용자에게 보여줄 문구. */
    fun validate(draft: EventDraft): String? {
        val title = draft.title.trim()
        return when {
            title.isEmpty() -> "제목을 입력해 주세요."
            title.length > CalendarEvent.TITLE_MAX -> "제목은 ${CalendarEvent.TITLE_MAX}자까지 쓸 수 있어요."
            draft.memo.length > CalendarEvent.MEMO_MAX -> "메모는 ${CalendarEvent.MEMO_MAX}자까지 쓸 수 있어요."
            draft.remind != null && draft.remind !in com.uridul.app.data.Remind.allowed -> "알림 시간을 다시 골라 주세요."
            draft.endDate.isBefore(draft.date) -> "끝나는 날이 시작하는 날보다 빨라요."
            ChronoUnit.DAYS.between(draft.date, draft.endDate) >= CalendarEvent.MAX_SPAN_DAYS ->
                "여러 날 일정은 ${CalendarEvent.MAX_SPAN_DAYS}일까지 만들 수 있어요."
            draft.time != null && draft.timeline.size > TimeSlot.MAX_SLOTS ->
                "시간대는 ${TimeSlot.MAX_SLOTS}개까지 넣을 수 있어요."
            draft.time != null && draft.date != draft.endDate && draft.timeline.size > 1 ->
                "여러 날 일정은 시간대를 하나만 넣을 수 있어요."
            draft.timeline.any { it.title.length > TimeSlot.TITLE_MAX } ->
                "시간대 내용은 ${TimeSlot.TITLE_MAX}자까지 쓸 수 있어요."
            // 하루 일정은 끝나는 시각이 시작보다 늦어야 한다 (여러 날 일정은 마지막 날 시각이라 비교하지 않음)
            draft.time != null && draft.date == draft.endDate &&
                draft.timeline.any { s -> s.end != null && !s.end.isAfter(s.start) } ->
                "끝나는 시간이 시작 시간보다 빨라요."
            else -> null
        }
    }
}

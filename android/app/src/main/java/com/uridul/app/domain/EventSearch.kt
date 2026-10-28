package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import java.time.LocalDate

/**
 * 일정 검색: 제목·시간대 내용·장소·메모에서 찾는다.
 * 띄어쓰기와 대소문자는 무시한다 ("선릉 감자탕" 으로 "선릉감자탕" 도 찾는다).
 */
object EventSearch {

    data class Result(val upcoming: List<CalendarEvent>, val past: List<CalendarEvent>) {
        val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
        val size: Int get() = upcoming.size + past.size
    }

    private fun normalize(s: String) = s.lowercase().filterNot { it.isWhitespace() }

    fun matches(event: CalendarEvent, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return false
        val fields = buildList {
            add(event.title)
            add(event.memo)
            event.place?.let { add(it.name) }
            event.slots.forEach { s ->
                add(s.title)
                s.place?.let { add(it.name) }
            }
        }
        return fields.any { normalize(it).contains(q) }
    }

    /** 다가오는 일정(끝나는 날이 오늘 이후)은 가까운 순, 지난 일정은 최근 순 */
    fun search(events: List<CalendarEvent>, query: String, today: LocalDate, limit: Int = 100): Result {
        // 반복 일정은 다음에 오는 날로 보여준다 (앞으로 없으면 원래 첫 날)
        val found = events.filter { matches(it, query) }.map { e -> if (e.repeat != null) Recurrence.next(e, today) ?: e else e }
        val upcoming = found.filter { !it.endDate.isBefore(today) }.sortedWith(compareBy({ it.date }, { it.time }))
        val past = found.filter { it.endDate.isBefore(today) }.sortedWith(compareByDescending<CalendarEvent> { it.date }.thenBy { it.time })
        return Result(upcoming.take(limit), past.take(limit))
    }
}

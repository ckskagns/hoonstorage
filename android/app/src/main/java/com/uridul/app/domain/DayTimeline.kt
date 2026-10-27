package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.Place
import java.time.LocalDate

/**
 * 하루 페이지의 시간표 계산.
 *  - 하루 일정의 시간대마다 블록 하나 (시작~끝, 끝이 없으면 1시간)
 *  - 여러 날 일정·하루 종일 일정은 위쪽 "하루 종일" 칸으로
 *  - 시간이 겹치는 블록은 나란히 (열 나누기)
 */
object DayTimeline {

    data class Block(
        /** 화면에서 구분하는 키: 일정 ID + 시간대 번호 */
        val key: String,
        val event: CalendarEvent,
        val slotIndex: Int,
        /** 0시부터 분 */
        val startMin: Int,
        val endMin: Int,
        val title: String,
        val place: Place?,
    )

    data class Placed(val block: Block, val column: Int, val columns: Int)

    const val DAY_MIN = 24 * 60
    /** 끝 시각이 없거나 너무 짧으면 이 길이로 그린다 */
    private const val DEFAULT_LENGTH = 60
    private const val MIN_LENGTH = 20

    /** 위쪽 하루 종일 칸에 둘 일정: 하루 종일 + 여러 날 일정 */
    fun allDayEvents(events: List<CalendarEvent>, day: LocalDate): List<CalendarEvent> =
        events.filter { it.occursOn(day) && (it.allDay || it.isMultiDay) }
            .sortedWith(compareBy({ it.date }, { it.title }))

    /** 시간표에 그릴 블록: 그날 하루 일정의 시간대들 */
    fun blocks(events: List<CalendarEvent>, day: LocalDate): List<Block> =
        events.filter { it.occursOn(day) && !it.allDay && !it.isMultiDay }
            .flatMap { e -> e.slots.mapIndexed { i, s -> block(e, i, s.start.toSecondOfDay() / 60, s.end?.let { it.toSecondOfDay() / 60 }, s.title, s.place) } }
            .sortedWith(compareBy({ it.startMin }, { it.endMin }))

    fun block(event: CalendarEvent, slotIndex: Int, start: Int, end: Int?, slotTitle: String, place: Place?): Block {
        val realEnd = (end?.takeIf { it > start } ?: (start + DEFAULT_LENGTH)).coerceAtMost(DAY_MIN)
        val title = when {
            slotTitle.isBlank() -> event.title
            event.slots.size > 1 -> "${event.title} · $slotTitle"
            else -> slotTitle
        }
        return Block("${event.id}#$slotIndex", event, slotIndex, start, maxOf(realEnd, start + MIN_LENGTH).coerceAtMost(DAY_MIN), title, place)
    }

    /**
     * 겹치는 블록 나란히 놓기: 서로 겹쳐 이어지는 묶음마다 필요한 열 수를 정하고, 비어 있는 첫 열에 넣는다.
     */
    fun layout(blocks: List<Block>): List<Placed> {
        val sorted = blocks.sortedWith(compareBy({ it.startMin }, { it.endMin }))
        val result = mutableListOf<Placed>()
        var cluster = mutableListOf<Pair<Block, Int>>()
        var clusterEnd = -1
        val columnEnds = mutableListOf<Int>()

        fun flush() {
            val cols = (cluster.maxOfOrNull { it.second } ?: -1) + 1
            cluster.forEach { (b, c) -> result += Placed(b, c, cols) }
            cluster = mutableListOf()
            columnEnds.clear()
        }

        for (b in sorted) {
            if (cluster.isNotEmpty() && b.startMin >= clusterEnd) flush()
            var col = columnEnds.indexOfFirst { it <= b.startMin }
            if (col == -1) {
                col = columnEnds.size
                columnEnds += b.endMin
            } else {
                columnEnds[col] = b.endMin
            }
            cluster += b to col
            clusterEnd = maxOf(if (cluster.size == 1) b.endMin else clusterEnd, b.endMin)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}

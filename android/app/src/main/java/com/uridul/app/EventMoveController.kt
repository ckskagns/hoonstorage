package com.uridul.app

import com.uridul.app.data.CalendarEvent
import com.uridul.app.data.EventDraft
import com.uridul.app.data.normalized
import com.uridul.app.data.toSeriesDates
import com.uridul.app.domain.MemoryMove
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 일정 저장과 날짜 옮기기 (AppViewModel 에서 덜어냄).
 * 날짜가 바뀌면 그 일정의 사진·후기도 새 날짜(와 일정)로 함께 옮기고,
 * 반복 일정의 어느 날 날짜를 바꾸면 "이 날만 / 반복 전체" 를 묻는다.
 */
class EventMoveController internal constructor(
    private val f: FeatureScope,
    private val photos: PhotoController,
    /** 고치기 전 원본 (첫 날 기준). 저장하면 화면 목록이 바로 바뀌므로 저장 전에 찾는다 */
    private val seriesOf: (String?) -> CalendarEvent?,
) {
    private val _occurrenceMove = MutableStateFlow<EventDraft?>(null)
    /** 반복 일정의 어느 날 날짜를 바꿔 "이 날만 / 반복 전체" 를 묻는 중인 입력값 */
    val occurrenceMove: StateFlow<EventDraft?> = _occurrenceMove.asStateFlow()
    /** 물을 때의 원본 (고르기 전에 달을 넘겨도 그대로 쓰도록) */
    private var occurrenceSeries: CalendarEvent? = null

    private fun saveError(e: Exception, fallback: String) {
        f.message = f.errorText(e) ?: fallback
    }

    /**
     * 확인을 마친 입력값을 저장한다. [draft] 는 반복 일정이면 첫 날 기준으로 바꾼 값.
     * 반복 일정의 어느 날 날짜를 바꿨으면 그 날만 옮길지, 반복 전체를 옮길지 묻는다 (창은 닫고 물음 창을 띄운다).
     */
    fun save(input: EventDraft, draft: EventDraft): Boolean {
        if (f.ready() == null || f.container.events == null) return false
        val series = seriesOf(input.id)
        if (MemoryMove.asksOccurrenceChoice(input, series)) {
            occurrenceSeries = series
            _occurrenceMove.value = input
            return true
        }
        return saveSeries(draft, series)
    }

    /** "이 날만" / "반복 전체" 를 고른 뒤 (null = 취소, 저장하지 않음) */
    fun resolve(onlyThisDay: Boolean?) {
        val input = _occurrenceMove.value ?: return
        val series = occurrenceSeries
        _occurrenceMove.value = null
        occurrenceSeries = null
        when (onlyThisDay) {
            null -> f.message = "일정을 고치지 않았어요"
            true -> moveOneOccurrence(input, series)
            false -> saveSeries(input.toSeriesDates().normalized(), series)
        }
    }

    /** [old] 고치기 전 원본 (날짜가 바뀌면 그 일정의 사진·후기를 함께 옮긴다) */
    private fun saveSeries(draft: EventDraft, old: CalendarEvent?): Boolean {
        val ready = f.ready() ?: return false
        val repo = f.container.events ?: return false
        repo.save(ready.couple.id, ready.myUid, draft) { saveError(it, "저장하지 못했어요.") }
        if (old != null && MemoryMove.needed(old, draft.date)) {
            moveMemories(old.id, old.id, { d -> MemoryMove.target(old, draft.date, draft.repeat, d) }) { what ->
                if (old.repeat != null && draft.repeat != null) "📷 날마다 남긴 ${what}도 반복과 함께 옮겼어요"
                else "📷 ${what}도 ${draft.date.monthValue}월 ${draft.date.dayOfMonth}일로 함께 옮겼어요"
            }
        }
        return true
    }

    /**
     * 반복 일정의 하루만 다른 날로: 그 날은 반복에서 빼고,
     *  - 옮길 날이 이미 그 반복의 날이면 그 날에 합치고 (새 일정 없음)
     *  - 아니면 고친 내용으로 반복 없는 일정을 새로 만든다.
     * 그 날의 사진·후기도 함께 옮긴다.
     */
    private fun moveOneOccurrence(input: EventDraft, found: CalendarEvent?) {
        val ready = f.ready() ?: return
        val repo = f.container.events ?: return
        val series = found ?: seriesOf(input.id) ?: run { f.message = "일정을 찾지 못했어요. 다시 시도해 주세요."; return }
        val from = input.occurrence ?: return
        val to = input.date
        val merge = MemoryMove.mergesIntoSeries(series, to)
        val toId = if (merge) {
            series.id
        } else {
            repo.save(ready.couple.id, ready.myUid, MemoryMove.detached(input).normalized()) { saveError(it, "저장하지 못했어요.") }
        }
        repo.setSkipped(ready.couple.id, ready.myUid, series.id, from, skipped = true) {
            saveError(it, "반복에서 이 날을 빼지 못했어요.")
        }
        // 반복의 날에 합치면 그 날만 따로 고친 내용(시간·메모 등)은 남길 곳이 없다: 알려 준다
        val note = if (merge && MemoryMove.editsBesidesDate(input, series)) " · 바꾼 내용은 반복 전체에서 고쳐 주세요" else ""
        val day = "${from.monthValue}월 ${from.dayOfMonth}일 일정을 ${to.monthValue}월 ${to.dayOfMonth}일로 옮겼어요$note"
        f.message = day
        moveMemories(series.id, toId, { d -> to.takeIf { d == from } }) { what -> "$day ($what 함께)" }
    }

    /**
     * 사진은 날짜·일정만 고치고, 후기는 날짜가 문서 ID 에 들어 있어 새로 만들고 예전 것을 지운다.
     * @param eventId 원래 일정, [toEventId] 옮긴 뒤 일정 (반복 일정의 하루만 떼어 내면 새 일정)
     * @param target 원래 날짜 → 새 날짜 (옮기지 않으면 null)
     * @param done 무엇을 옮겼는지("사진·후기") 받아 안내 문구를 만든다
     */
    private fun moveMemories(eventId: String, toEventId: String, target: (LocalDate) -> LocalDate?, done: (String) -> String) {
        val ready = f.ready() ?: return
        val coupleId = ready.couple.id
        f.scope.launch {
            try {
                val photoMoves = f.container.photos?.moveDates(coupleId, eventId, target, toEventId).orEmpty()
                val reviewMoves = f.container.reviews?.moveDates(coupleId, ready.myUid, eventId, target, toEventId)
                val reviewCount = reviewMoves?.moved ?: 0
                val kept = reviewMoves?.kept ?: 0
                if (photoMoves.isNotEmpty()) {
                    // 사진 달력: 빠진 달과 들어간 달을 다시 받는다
                    photoMoves.flatMap { (from, to) -> listOf(YearMonth.from(from), YearMonth.from(to)) }.toSet()
                        .forEach { photos.loadMonthPhotos(it, force = true) }
                    photos.refreshMemoryPhotos()
                }
                val what = listOfNotNull("사진".takeIf { photoMoves.isNotEmpty() }, "후기".takeIf { reviewCount > 0 }).joinToString("·")
                // 옮길 날에 이미 후기가 있으면 섞지 않고 원래 날에 둔다: 알려 준다
                val keptNote = if (kept > 0) "옮길 날에 이미 후기가 있어 후기 ${kept}개는 원래 날짜에 그대로 뒀어요" else null
                when {
                    what.isNotEmpty() -> f.message = done(what) + (keptNote?.let { " · $it" } ?: "")
                    keptNote != null -> f.message = keptNote
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                f.message = f.errorText(e) ?: "일정은 고쳤지만 사진·후기를 옮기지 못했어요. 다시 저장해 보세요."
            }
        }
    }
}

package com.uridul.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 일정을 옮겼을 때 그 일정의 사진·후기도 새 날짜(와 일정)로 옮긴다 (AppViewModel 에서 덜어냄).
 * 사진은 날짜·일정만 고치고, 후기는 날짜가 문서 ID 에 들어 있어 새로 만들고 예전 것을 지운다.
 */
class MemoryMover internal constructor(private val f: FeatureScope, private val photos: PhotoController) {

    /**
     * @param eventId 원래 일정, [toEventId] 옮긴 뒤 일정 (반복 일정의 하루만 떼어 내면 새 일정)
     * @param target 원래 날짜 → 새 날짜 (옮기지 않으면 null)
     * @param done 무엇을 옮겼는지("사진·후기") 받아 안내 문구를 만든다
     */
    fun move(eventId: String, toEventId: String, target: (LocalDate) -> LocalDate?, done: (String) -> String) {
        val ready = f.ready() ?: return
        val coupleId = ready.couple.id
        f.scope.launch {
            try {
                val photoMoves = f.container.photos?.moveDates(coupleId, eventId, target, toEventId).orEmpty()
                val reviewCount = f.container.reviews?.moveDates(coupleId, ready.myUid, eventId, target, toEventId) ?: 0
                if (photoMoves.isNotEmpty()) {
                    // 사진 달력: 빠진 달과 들어간 달을 다시 받는다
                    photoMoves.flatMap { (from, to) -> listOf(YearMonth.from(from), YearMonth.from(to)) }.toSet()
                        .forEach { photos.loadMonthPhotos(it, force = true) }
                    photos.refreshMemoryPhotos()
                }
                val what = listOfNotNull("사진".takeIf { photoMoves.isNotEmpty() }, "후기".takeIf { reviewCount > 0 }).joinToString("·")
                if (what.isNotEmpty()) f.message = done(what)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                f.message = f.errorText(e) ?: "일정은 고쳤지만 사진·후기를 옮기지 못했어요. 다시 저장해 보세요."
            }
        }
    }
}

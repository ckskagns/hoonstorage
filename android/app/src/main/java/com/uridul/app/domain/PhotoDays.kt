package com.uridul.app.domain

import com.uridul.app.data.PhotoMeta
import java.time.LocalDate
import java.time.YearMonth

/**
 * 사진 달력: 달력 칸마다 그날 사진첩의 대표 사진 한 장 + 그날 사진 수.
 *
 * 한 달치 사진(작은 사진)을 서버에서 한 번 받아 날마다 대표 사진만 폰에 저장해 둔다 ([com.uridul.app.data.PhotoMonthCache]).
 * 다음부터는 그 달 사진 "수"만 서버에 물어서(사진은 받지 않음) 바뀌지 않았으면 저장해 둔 것을 그대로 쓴다.
 */
object PhotoDays {
    /** 저장해 둔 달이 이보다 오래되면 사진 수가 같아도 다시 받는다 (지우고 같은 수만큼 올린 경우 대비) */
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** 같은 달 사진 수를 다시 묻기까지 (앱을 켜 둔 채 달력을 오가도 매번 묻지 않게) */
    const val RECHECK_MS = 5L * 60 * 1000

    /** 하루의 대표 사진 (가장 먼저 올린 사진) 과 그날 사진 수 */
    data class Cover(val photo: PhotoMeta, val count: Int)

    fun covers(photos: List<PhotoMeta>): Map<LocalDate, Cover> =
        photos.groupBy { it.date }.mapValues { (_, list) ->
            Cover(list.minWith(compareBy<PhotoMeta> { it.uploadedAtMillis }.thenBy { it.id }), list.size)
        }

    /**
     * 사진 수를 물어볼 달인지. 일정 사진은 날짜 전에도 올릴 수 있어서(예: 12월 여행 일정에 미리 올린 사진) 다가오는 달도 묻는다.
     * 다만 1년 넘게 남은 달은 사진이 있을 리 없어 묻지 않는다 (달을 빨리 넘길 때 헛물음 줄이기).
     */
    fun mayHavePhotos(month: YearMonth, today: LocalDate): Boolean = month <= YearMonth.from(today).plusYears(1)

    /**
     * 저장해 둔 달을 그대로 써도 되는지.
     * @param savedCount 저장할 때 그 달 사진 수 (저장한 적 없으면 null)
     * @param serverCount 지금 서버의 그 달 사진 수
     */
    fun cacheValid(savedCount: Int?, savedAt: Long, serverCount: Int, now: Long): Boolean =
        savedCount != null && savedCount == serverCount && now - savedAt in 0 until MAX_AGE_MS

    /** 칸 아래 "+N" (대표 사진 말고 더 있는 장수, 없으면 null) */
    fun moreText(count: Int): String? = if (count > 1) "+${count - 1}" else null
}

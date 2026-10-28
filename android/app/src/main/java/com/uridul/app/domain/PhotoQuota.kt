package com.uridul.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * 사진첩 용량 계획: Firestore 무료 저장 공간(1GiB) 안에서 약 3년 동안 쓸 수 있게 한 주 업로드 수를 정한다.
 *
 *  - 사진에 쓰는 몫: 900MB (일정·후기 등 다른 데이터를 위해 약 100MB 남김)
 *  - 한 장 = 큰 사진(최대 200KB) + 작은 사진(최대 40KB) + 문서 기본 크기(약 1KB) ≈ 최대 241KB
 *  - 900MB ÷ 241KB ≈ 3,900장 → 3년(약 156주) 으로 나누면 한 주 약 25장 (두 사람 합계)
 *
 * 한 주는 월요일 ~ 일요일. "이번 주에 올렸고 아직 남아 있는 사진" 수로 센다 → 지우면 다시 올릴 수 있다.
 */
object PhotoQuota {
    /** 사진에 쓰는 저장 공간 */
    const val BUDGET_BYTES = 900L * 1024 * 1024
    /** 큰 사진(크게 보기) 최대 크기 */
    const val PHOTO_MAX_BYTES = 200_000
    /** 작은 사진(목록·슬라이드) 최대 크기 */
    const val THUMB_MAX_BYTES = 40_000
    /** 문서 이름·필드 등 기본 크기 (넉넉히) */
    const val OVERHEAD_BYTES = 1_000
    /** 큰 사진 긴 변 */
    const val PHOTO_LONG_SIDE = 1280
    /** 작은 사진 긴 변 */
    const val THUMB_LONG_SIDE = 480
    /** 몇 년 동안 쓰게 할지 */
    const val PLAN_YEARS = 3
    /** 일정(그 날) 하나에 올릴 수 있는 사진 */
    const val PER_EVENT_MAX = 10

    /** 한 장 최대 크기 */
    const val MAX_BYTES_PER_PHOTO: Long = (PHOTO_MAX_BYTES + THUMB_MAX_BYTES + OVERHEAD_BYTES).toLong()

    /** 3년 동안 올릴 수 있는 사진 수 */
    val TOTAL_PHOTOS: Long = BUDGET_BYTES / MAX_BYTES_PER_PHOTO

    /** 3년의 주 수 */
    val PLAN_WEEKS: Double = PLAN_YEARS * 365.25 / 7

    /** 한 주 업로드 제한 (두 사람 합계) */
    val WEEKLY_LIMIT: Int = (TOTAL_PHOTOS / PLAN_WEEKS).toInt()

    /** "2026-W44" (월요일 시작 주) */
    fun weekKey(date: LocalDate): String =
        "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** 이번 주 월요일 ~ 일요일 */
    fun weekRange(date: LocalDate): ClosedRange<LocalDate> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday..monday.plusDays(6)
    }

    /** 지금 더 올릴 수 있는 장수 (이번 주 남은 수와 이 일정 남은 수 중 작은 쪽) */
    fun remaining(weekUsed: Int, eventUsed: Int): Int =
        minOf(WEEKLY_LIMIT - weekUsed, PER_EVENT_MAX - eventUsed).coerceAtLeast(0)
}

package com.uridul.app.domain

import com.uridul.app.data.CalendarEvent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 추억 슬라이드: 홈에서 자동으로 넘어가는 그림 카드.
 *  - 1년 전·2년 전… 같은 날 일정이 있으면 맨 앞에 ("1년 전 오늘")
 *  - 지난 한 달 동안의 일정 (최근 것부터, "3일 전")
 * 반복 일정(매주 운동 같은)은 추억이 아니라서 뺀다.
 */
object Memories {

    data class Slide(
        val event: CalendarEvent,
        /** "1년 전 오늘", "3일 전" */
        val label: String,
        /** 카드에 크게 그릴 그림 (제목·장소로 고른다) */
        val emoji: String,
    )

    /** 거슬러 볼 해 */
    const val MAX_YEARS = 5
    /** 슬라이드 최대 장수 */
    const val MAX_SLIDES = 20

    /** 서버에서 받아올 범위들: 몇 년 전 같은 날(여러 날 일정 포함) + 지난 한 달 */
    fun queryRanges(today: LocalDate): List<ClosedRange<LocalDate>> {
        val span = CalendarEvent.MAX_SPAN_DAYS
        val yearly = (1..MAX_YEARS).map { y -> today.minusYears(y.toLong()).let { it.minusDays(span)..it } }
        return yearly + (today.minusMonths(1).minusDays(span)..today.minusDays(1))
    }

    fun slides(today: LocalDate, events: List<CalendarEvent>): List<Slide> {
        val usable = events.filter { it.repeat == null }.distinctBy { it.id }
        val yearly = (1..MAX_YEARS).flatMap { years ->
            val day = today.minusYears(years.toLong())
            CalendarMath.eventsOn(usable, day).map { Slide(it, "${years}년 전 오늘", emojiFor(it)) }
        }
        val from = today.minusMonths(1)
        val recent = usable
            .filter { !it.date.isBefore(from) && it.date.isBefore(today) }
            .sortedWith(compareByDescending<CalendarEvent> { it.date }.thenBy { it.time })
            .map { e ->
                val days = ChronoUnit.DAYS.between(e.date, today)
                Slide(e, if (days == 1L) "어제" else "${days}일 전", emojiFor(e))
            }
        return (yearly + recent).distinctBy { it.event.id }.take(MAX_SLIDES)
    }

    private val keywords = listOf(
        listOf("영화", "무비", "넷플") to "🎬",
        listOf("카페", "커피", "디저트", "빵") to "☕",
        listOf("여행", "비행기", "공항", "숙소", "호텔", "제주") to "✈️",
        listOf("바다", "해변", "수영") to "🌊",
        listOf("등산", "캠핑", "산행") to "⛰️",
        listOf("술", "와인", "맥주", "칵테일") to "🍷",
        listOf("저녁", "점심", "아침", "식사", "맛집", "밥", "먹", "찜", "탕", "고기", "파스타") to "🍽️",
        listOf("생일", "케이크") to "🎂",
        listOf("공연", "콘서트", "뮤지컬", "전시") to "🎤",
        listOf("쇼핑", "선물") to "🛍️",
        listOf("운동", "헬스", "러닝", "산책") to "🏃",
        listOf("집", "홈") to "🏠",
        listOf("병원") to "🏥",
        listOf("데이트", "만나") to "💕",
    )

    /** 제목·장소 이름으로 카드 그림 고르기 (없으면 하트) */
    fun emojiFor(e: CalendarEvent): String {
        val text = buildString {
            append(e.title)
            e.place?.let { append(' ').append(it.name) }
            e.slots.forEach { s -> append(' ').append(s.title); s.place?.let { append(' ').append(it.name) } }
        }
        return keywords.firstOrNull { (words, _) -> words.any { it in text } }?.second ?: "💗"
    }
}

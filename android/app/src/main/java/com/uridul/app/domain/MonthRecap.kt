package com.uridul.app.domain

import com.uridul.app.data.DateReview
import com.uridul.app.ledger.CancelPairs
import com.uridul.app.ledger.EntryStatus
import com.uridul.app.ledger.EntryType
import com.uridul.app.ledger.LedgerCategories
import com.uridul.app.ledger.LedgerEntry
import com.uridul.app.ledger.LedgerTotals
import java.time.LocalDate
import java.time.YearMonth

/**
 * "이달의 우리": 한 달 동안의 일기(후기)·사진·데이트 가계부를 한 장으로 돌아보기.
 * 모두 이미 있는 데이터로 계산한다 (후기 전체, 사진 달력의 날짜별 사진 수, 그 달 가계부 거래).
 */
object MonthRecap {

    data class Recap(
        val month: YearMonth,
        /** 후기를 남긴 일정 수 */
        val posts: Int,
        /** 후기나 사진이 있는 날 수 */
        val days: Int,
        val photos: Int,
        /** 두 사람 후기 하트 평균 (후기가 없으면 null) */
        val hearts: Double?,
        /** 하트가 가장 많은 날 (같으면 사진이 많은 날, 그다음 늦은 날) */
        val best: DateReview?,
        /** 다녀온 곳 수 (장소가 있는 후기, 이름으로 셈) */
        val places: Int,
        /** 두 번 이상 간 곳 중 가장 많이 간 곳과 횟수 */
        val topPlace: Pair<String, Int>?,
        /** 데이트 가계부에서 쓴 돈 (취소 짝 빼고) */
        val expense: Long,
        /** 가장 많이 쓴 분류와 금액 */
        val topCategory: Pair<LedgerCategories.Category, Long>?,
        /** 가장 큰 결제 한 건 */
        val biggest: LedgerEntry?,
        /** 콜라주에 넣을 날 (최대 4, 하트·사진 많은 날 먼저) */
        val collage: List<LocalDate>,
    ) {
        val isEmpty: Boolean get() = posts == 0 && photos == 0 && expense <= 0
    }

    /**
     * @param reviews 후기 전체 (이 달 것만 골라 쓴다)
     * @param photoCounts 날짜별 사진 수 (사진 달력)
     * @param ledger 이 달 가계부 거래
     */
    fun build(month: YearMonth, reviews: Collection<DateReview>, photoCounts: Map<LocalDate, Int>, ledger: List<LedgerEntry>): Recap {
        val inMonth = { d: LocalDate -> YearMonth.from(d) == month }
        val posts = reviews.filter { inMonth(it.date) && it.entries.isNotEmpty() }
        val photoDays = photoCounts.filter { (d, n) -> inMonth(d) && n > 0 }
        val allHearts = posts.flatMap { r -> r.entries.values.map { it.hearts } }
        val best = posts.maxWithOrNull(
            compareBy<DateReview> { it.averageHearts }.thenBy { photoDays[it.date] ?: 0 }.thenBy { it.date },
        )
        val placeCounts = posts.mapNotNull { it.place?.name?.trim()?.takeIf(String::isNotEmpty) }.groupingBy { it }.eachCount()
        val topPlace = placeCounts.entries.filter { it.value >= 2 }
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.toPair()

        val live = ledger.filter { !it.deleted && it.status == EntryStatus.CONFIRMED && inMonth(it.date) }
        val pairs = CancelPairs.of(ledger)
        val spends = live.filter { it.type == EntryType.EXPENSE && !it.cancel && !pairs.isPaired(it.id) }
        val topCategory = spends.groupBy { LedgerCategories.of(it.category) }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
            .maxByOrNull { it.value }?.toPair()

        val heartsOn = posts.groupBy { it.date }.mapValues { (_, l) -> l.maxOf { it.averageHearts } }
        val collage = photoDays.keys
            .sortedWith(compareByDescending<LocalDate> { heartsOn[it] ?: 0.0 }.thenByDescending { photoDays[it] ?: 0 }.thenBy { it })
            .take(4)
            .sorted()

        return Recap(
            month = month,
            posts = posts.size,
            days = (posts.map { it.date } + photoDays.keys).toSet().size,
            photos = photoDays.values.sum(),
            hearts = allHearts.takeIf { it.isNotEmpty() }?.average(),
            best = best,
            places = placeCounts.size,
            topPlace = topPlace,
            expense = LedgerTotals.expense(live, pairs).coerceAtLeast(0),
            topCategory = topCategory,
            biggest = spends.maxByOrNull { it.amount },
            collage = collage,
        )
    }

    /**
     * 돌아볼 달 목록: 후기·사진이 있는 달, 최근 달부터. 아직 오지 않은 달은 뺀다
     * (일정에 미리 올린 사진이 있어도: 12월 2일 사진만 있는 12월은 12월이 되어야 나온다)
     */
    fun months(reviews: Collection<DateReview>, photoDays: Collection<LocalDate>, today: LocalDate): List<YearMonth> =
        (reviews.filter { it.entries.isNotEmpty() }.map { YearMonth.from(it.date) } + photoDays.map(YearMonth::from))
            .filter { it <= YearMonth.from(today) }
            .toSortedSet(compareByDescending { it }).toList()

    /** 홈에 "지난달의 우리" 카드를 띄울 기간: 매달 1~7일 */
    const val HOME_DAYS = 7

    fun homeMonth(today: LocalDate): YearMonth? =
        if (today.dayOfMonth <= HOME_DAYS) YearMonth.from(today).minusMonths(1) else null
}

package com.uridul.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.uridul.app.ledger.LedgerTotals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 일기장 게시물의 "이날 데이트 비용": 데이트 가계부(같이 쓰는 카드·통장)에서 그날 쓴 돈.
 * 게시물이 보일 때 그 달 거래를 한 번만 읽어 날짜별로 더해 둔다 (일기장을 여는 동안만 기억).
 */
class DayCosts internal constructor(
    private val load: suspend (YearMonth) -> Map<LocalDate, Long>?,
    private val scope: CoroutineScope,
) {
    private val months = mutableStateMapOf<YearMonth, Map<LocalDate, Long>>()
    private val asked = HashSet<YearMonth>()

    /** 그날 쓴 돈 (모르거나 없으면 null) */
    fun of(date: LocalDate): Long? = months[YearMonth.from(date)]?.get(date)

    /** 그 달 거래를 아직 안 읽었으면 읽는다 */
    fun request(month: YearMonth) {
        if (!asked.add(month)) return
        scope.launch {
            val map = runCatching { load(month) }.getOrNull()
            if (map == null) asked.remove(month) else months[month] = map
        }
    }
}

/** 데모·미리보기: 가계부 없음 */
val LocalDayCosts = staticCompositionLocalOf<DayCosts?> { null }

@Composable
fun rememberDayCosts(coupleId: String): DayCosts? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(coupleId) {
        val repo = ledgerRepo(context) ?: return@remember null
        DayCosts({ m -> LedgerTotals.expenseByDay(repo.fetchMonth(coupleId, m)) }, scope)
    }
}

private fun ledgerRepo(context: Context) = appContainer(context)?.ledger

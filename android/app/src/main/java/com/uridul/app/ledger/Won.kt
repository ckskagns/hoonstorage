package com.uridul.app.ledger

import java.text.NumberFormat
import java.util.Locale

/** 원 금액 글자 (화면·저장하는 메모가 같은 모양을 쓰게) */
object Won {
    private val format = NumberFormat.getNumberInstance(Locale.KOREA)

    /** 38000 → "38,000원" */
    fun text(amount: Long): String = "${format.format(amount)}원"

    /** 부호 붙인 금액: 양수 "+1,000원", 음수 "-1,000원" (부호는 한 번만) */
    fun signed(amount: Long): String = (if (amount < 0) "-" else "+") + text(kotlin.math.abs(amount))
}

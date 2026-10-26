package com.uridul.app.data

import java.time.LocalDate

data class Couple(
    val id: String,
    val members: List<String>,
    val names: Map<String, String>,
    val startDate: LocalDate,
    val countFromOne: Boolean,
) {
    val isPaired: Boolean get() = members.size >= 2

    fun myName(myUid: String): String = names[myUid] ?: "나"

    fun partnerName(myUid: String): String? =
        members.firstOrNull { it != myUid }?.let { names[it] ?: "상대" }
}

/** 사용자에게 그대로 보여줄 수 있는 오류. */
class UserFacingException(message: String) : Exception(message)

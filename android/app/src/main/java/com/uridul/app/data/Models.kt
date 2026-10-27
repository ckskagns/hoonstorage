package com.uridul.app.data

import java.time.LocalDate

/** 커플 캐릭터. 오리지널 디자인(통통한 주황 아기 용 / 하얀 토끼). */
enum class Character(val key: String, val label: String) {
    DRAGON("dragon", "아기 용"),
    BUNNY("bunny", "토끼");

    val other: Character get() = if (this == DRAGON) BUNNY else DRAGON

    companion object {
        fun from(key: String?): Character? = entries.firstOrNull { it.key == key }
    }
}

/** 상대가 연결되기 전에 미리 정해 두는 상대 이름·캐릭터 (초대 링크에 함께 실려 간다). */
data class PartnerDefaults(val name: String, val character: Character)

data class Couple(
    val id: String,
    val members: List<String>,
    val names: Map<String, String>,
    val startDate: LocalDate,
    val countFromOne: Boolean,
    val characters: Map<String, String> = emptyMap(),
    val partnerDefaults: PartnerDefaults? = null,
) {
    val isPaired: Boolean get() = members.size >= 2

    fun myName(myUid: String): String = names[myUid] ?: "나"

    fun partnerUid(myUid: String): String? = members.firstOrNull { it != myUid }

    /** 상대 이름. 연결 전이면 미리 정해 둔 이름, 그것도 없으면 null. */
    fun partnerName(myUid: String): String? =
        partnerUid(myUid)?.let { names[it] ?: "상대" } ?: partnerDefaults?.name

    /** 캐릭터를 따로 고르지 않았으면 먼저 만든 사람은 아기 용, 들어온 사람은 토끼. */
    fun characterOf(uid: String): Character =
        Character.from(characters[uid]) ?: if (uid == members.firstOrNull()) Character.DRAGON else Character.BUNNY

    fun myCharacter(myUid: String): Character = characterOf(myUid)

    fun partnerCharacter(myUid: String): Character =
        partnerUid(myUid)?.let { characterOf(it) } ?: partnerDefaults?.character ?: myCharacter(myUid).other
}

/** 사용자에게 그대로 보여줄 수 있는 오류. */
class UserFacingException(message: String) : Exception(message)

/** 이름 길이 제한 */
const val NAME_MAX = 10

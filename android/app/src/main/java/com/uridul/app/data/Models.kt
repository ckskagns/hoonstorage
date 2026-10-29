package com.uridul.app.data

import com.uridul.app.domain.Birthday
import java.time.LocalDate
import java.time.MonthDay

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
    /** 일정 색 (두 사람이 같은 색으로 보도록 서버에): "creator" = 만든 사람, "partner" = 들어온 사람 → 색 이름 */
    val colors: Map<String, String> = emptyMap(),
) {
    val isPaired: Boolean get() = members.size >= 2

    /** 내가 커플 공간을 만든 사람인지 (색 역할 구분) */
    fun isCreator(myUid: String): Boolean = members.firstOrNull() == myUid

    /** 내 일정 색 이름, 상대 일정 색 이름 (서버에 아직 없으면 null) */
    fun colorKeys(myUid: String): Pair<String?, String?> =
        if (isCreator(myUid)) colors["creator"] to colors["partner"] else colors["partner"] to colors["creator"]

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

/**
 * 두 사람 생일 (고정): 커플 공간을 만든 사람(후이) 5월 13일, 초대로 들어온 사람(나나) 10월 18일.
 * 이름은 프로필 이름을 따라간다.
 */
object FixedBirthdays {
    val CREATOR: MonthDay = MonthDay.of(5, 13)
    val PARTNER: MonthDay = MonthDay.of(10, 18)
}

fun Couple.birthdays(): List<Birthday> {
    val creator = members.firstOrNull()
    val joined = members.getOrNull(1)
    val creatorName = creator?.let { names[it] } ?: "후이"
    val partnerName = joined?.let { names[it] } ?: partnerDefaults?.name ?: "나나"
    return listOf(Birthday(creatorName, FixedBirthdays.CREATOR), Birthday(partnerName, FixedBirthdays.PARTNER))
}

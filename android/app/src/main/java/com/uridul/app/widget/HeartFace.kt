package com.uridul.app.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.uridul.app.data.WidgetSnapshot
import com.uridul.app.domain.DDay
import java.time.LocalDate

/** 하트 위젯에 보이는 내용을 정하는 값: 사귄 날(없으면 연결 전), 첫날을 1일로 셀지, 두 사람 이름 */
internal data class HeartFace(
    val start: LocalDate?,
    val countFromOne: Boolean = true,
    val myName: String? = null,
    val partnerName: String? = null,
) {

    /** "113일" / "D-3", 연결 전이면 null */
    fun daysText(today: LocalDate): String? = start?.let { DDay.heroText(it, today, countFromOne) }

    /** 위젯 상태(다시 그리기 신호와 함께 넣은 값)에 적기 */
    fun writeTo(p: MutablePreferences) {
        p[KEY_SET] = true
        if (start != null) p[KEY_START] = start.toString() else p.remove(KEY_START)
        p[KEY_COUNT1] = countFromOne
        if (myName != null) p[KEY_ME] = myName else p.remove(KEY_ME)
        if (partnerName != null) p[KEY_PARTNER] = partnerName else p.remove(KEY_PARTNER)
    }

    companion object {
        private val KEY_SET = booleanPreferencesKey("heart_face_set")
        private val KEY_START = stringPreferencesKey("heart_start")
        private val KEY_COUNT1 = booleanPreferencesKey("heart_count_from_one")
        private val KEY_ME = stringPreferencesKey("heart_me")
        private val KEY_PARTNER = stringPreferencesKey("heart_partner")
        /** 디자인을 바꾸면 위젯 상태도 바뀌어 다시 그려지게 */
        val KEY_DESIGN = stringPreferencesKey("heart_design")

        fun of(snapshot: WidgetSnapshot?) = HeartFace(snapshot?.startDate, snapshot?.countFromOne ?: true, snapshot?.myName, snapshot?.partnerName)

        /** 위젯 상태에 적힌 값 (한 번도 적지 않았으면 null) */
        fun from(p: Preferences): HeartFace? {
            if (p[KEY_SET] != true) return null
            return HeartFace(p[KEY_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, p[KEY_COUNT1] ?: true, p[KEY_ME], p[KEY_PARTNER])
        }
    }
}

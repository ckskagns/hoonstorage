package com.uridul.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 위젯이 네트워크 없이 바로 그릴 수 있도록 폰에 저장해 두는 최소 정보. */
data class WidgetSnapshot(
    val startDate: LocalDate,
    val countFromOne: Boolean,
    val myName: String,
    val partnerName: String?,
)

private val Context.widgetDataStore by preferencesDataStore(name = "widget_state")

object WidgetStore {
    private val START = stringPreferencesKey("start_date")
    private val COUNT_FROM_ONE = booleanPreferencesKey("count_from_one")
    private val MY_NAME = stringPreferencesKey("my_name")
    private val PARTNER_NAME = stringPreferencesKey("partner_name")

    suspend fun save(context: Context, snapshot: WidgetSnapshot) {
        context.widgetDataStore.edit {
            it[START] = snapshot.startDate.toString()
            it[COUNT_FROM_ONE] = snapshot.countFromOne
            it[MY_NAME] = snapshot.myName
            if (snapshot.partnerName != null) it[PARTNER_NAME] = snapshot.partnerName else it.remove(PARTNER_NAME)
        }
    }

    suspend fun load(context: Context): WidgetSnapshot? {
        val prefs = context.widgetDataStore.data.first()
        val start = prefs[START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return WidgetSnapshot(
            startDate = start,
            countFromOne = prefs[COUNT_FROM_ONE] ?: true,
            myName = prefs[MY_NAME] ?: "나",
            partnerName = prefs[PARTNER_NAME],
        )
    }

    suspend fun clear(context: Context) {
        context.widgetDataStore.edit { it.clear() }
    }
}

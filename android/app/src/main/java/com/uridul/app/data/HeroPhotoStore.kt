package com.uridul.app.data

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * 디데이 카드 사진 설정 (이 폰에만): 매일 바뀜 / 고정 / 끔, "다른 사진" 누른 횟수(그날만),
 * 그리고 카드에 깐 큰 사진 파일 (긴 변 1280px, 200KB 이하, 최근 3장만 남김).
 */
object HeroPhotoStore {
    private const val PREFS = "hero_photo"
    private const val KEEP_FILES = 3

    enum class Mode(val key: String) { DAILY("daily"), FIXED("fixed"), OFF("off") }

    /** 고정한 사진 */
    data class Fixed(val id: String, val date: LocalDate, val eventId: String)

    data class Settings(
        val mode: Mode = Mode.DAILY,
        val fixed: Fixed? = null,
        /** 오늘 "다른 사진" 을 누른 횟수 (날이 바뀌면 0) */
        val skip: Int = 0,
    )

    fun read(context: Context, today: LocalDate): Settings {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = Mode.entries.firstOrNull { it.key == p.getString("mode", null) } ?: Mode.DAILY
        val fixed = runCatching {
            Fixed(p.getString("fixedId", null)!!, LocalDate.parse(p.getString("fixedDate", null)!!), p.getString("fixedEvent", "")!!)
        }.getOrNull()
        val skip = if (p.getString("skipDate", null) == today.toString()) p.getInt("skip", 0) else 0
        return Settings(if (mode == Mode.FIXED && fixed == null) Mode.DAILY else mode, fixed, skip)
    }

    fun setMode(context: Context, mode: Mode, fixed: Fixed? = null) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("mode", mode.key)
            if (fixed != null) {
                putString("fixedId", fixed.id)
                putString("fixedDate", fixed.date.toString())
                putString("fixedEvent", fixed.eventId)
            } else if (mode != Mode.FIXED) {
                remove("fixedId"); remove("fixedDate"); remove("fixedEvent")
            }
        }.apply()
    }

    /** "다른 사진": 오늘 몇 번째 후보를 볼지 하나 늘린다 */
    fun nextSkip(context: Context, today: LocalDate) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = if (p.getString("skipDate", null) == today.toString()) p.getInt("skip", 0) else 0
        p.edit().putString("skipDate", today.toString()).putInt("skip", now + 1).apply()
    }

    private fun dir(context: Context) = File(context.filesDir, "hero_photo").apply { mkdirs() }
    private fun file(context: Context, id: String) = File(dir(context), id.filter { it.isLetterOrDigit() } + ".jpg")

    fun readPhoto(context: Context, id: String): ByteArray? =
        runCatching { file(context, id).takeIf { it.exists() }?.readBytes() }.getOrNull()

    /** 큰 사진 저장 (가장 오래된 것부터 지워 [KEEP_FILES] 장만) */
    fun savePhoto(context: Context, id: String, bytes: ByteArray) {
        runCatching {
            file(context, id).writeBytes(bytes)
            dir(context).listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(KEEP_FILES).forEach { it.delete() }
        }
    }
}

/** 디데이 카드 사진 메뉴 */
enum class HeroAction { NEXT, PIN, DAILY, OFF, ON }

/** 디데이 카드에 깐 사진 ([sharp] = 큰 사진, 아니면 받기 전까지 작은 사진) */
class HeroPhoto(val id: String, val date: LocalDate, val eventId: String, val bytes: ByteArray, val sharp: Boolean)

/** 디데이 카드 사진 상태: 깐 사진, 설정, 고를 사진이 있는지 (꺼 둔 카드에서 켜기 단추를 보일지) */
data class HeroState(
    val photo: HeroPhoto? = null,
    val mode: HeroPhotoStore.Mode = HeroPhotoStore.Mode.DAILY,
    val available: Boolean = false,
)

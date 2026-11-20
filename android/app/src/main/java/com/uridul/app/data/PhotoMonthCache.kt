package com.uridul.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** 사진 달력 칸 하나: 대표 사진(작은 사진) + 그날 사진 수 */
class DayPhoto(val id: String, val thumb: ByteArray, val count: Int)

/**
 * 사진 달력용으로 폰에 저장해 둔 달별 대표 사진 (앱 전용 폴더, 지우면 다시 받으면 된다).
 *   files/photo_calendar/{커플ID}/{2026-11}.json   { count, savedAt, days: { "2026-11-15": { id, n } } }
 *   files/photo_calendar/{커플ID}/{사진ID}.jpg      대표 사진 (작은 사진 그대로, 40KB 이하)
 */
object PhotoMonthCache {

    class Month(val count: Int, val savedAt: Long, val days: Map<LocalDate, DayPhoto>)

    private fun dir(context: Context, coupleId: String) =
        File(context.filesDir, "photo_calendar/" + coupleId.filter { it.isLetterOrDigit() }).apply { mkdirs() }

    private fun indexFile(dir: File, month: YearMonth) = File(dir, "$month.json")
    private fun thumbFile(dir: File, id: String) = File(dir, id.filter { it.isLetterOrDigit() } + ".jpg")

    fun read(context: Context, coupleId: String, month: YearMonth): Month? = runCatching {
        val dir = dir(context, coupleId)
        val f = indexFile(dir, month)
        if (!f.exists()) return null
        val json = JSONObject(f.readText())
        val days = json.getJSONObject("days")
        val map = buildMap {
            for (key in days.keys()) {
                val d = days.getJSONObject(key)
                val id = d.getString("id")
                val bytes = thumbFile(dir, id).takeIf { it.exists() }?.readBytes() ?: return null // 사진이 빠졌으면 다시 받기
                put(LocalDate.parse(key), DayPhoto(id, bytes, d.getInt("n")))
            }
        }
        Month(json.getInt("count"), json.getLong("savedAt"), map)
    }.getOrNull()

    fun write(context: Context, coupleId: String, month: YearMonth, count: Int, days: Map<LocalDate, DayPhoto>, now: Long) {
        runCatching {
            val dir = dir(context, coupleId)
            val old = read(context, coupleId, month)
            days.values.forEach { p ->
                val f = thumbFile(dir, p.id)
                if (!f.exists()) f.writeBytes(p.thumb)
            }
            val json = JSONObject()
                .put("count", count)
                .put("savedAt", now)
                .put("days", JSONObject().apply { days.forEach { (d, p) -> put(d.toString(), JSONObject().put("id", p.id).put("n", p.count)) } })
            indexFile(dir, month).writeText(json.toString())
            // 이 달에서 더는 대표가 아닌 사진 파일은 지운다
            val keep = days.values.map { it.id }.toSet()
            old?.days?.values?.filter { it.id !in keep }?.forEach { thumbFile(dir, it.id).delete() }
        }
    }
}

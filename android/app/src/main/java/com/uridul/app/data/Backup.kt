package com.uridul.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 백업 파일 내용 */
data class BackupData(
    val exportedAt: String,
    val startDate: LocalDate?,
    val events: List<CalendarEvent>,
    val wishes: List<WishPlace>,
    val reviews: List<DateReview> = emptyList(),
)

/** 불러오기 전에 보여줄 요약: 파일에 있는 것 중 지금 없는 것만 더한다 */
data class ImportPreview(
    val data: BackupData,
    val newEvents: List<CalendarEvent>,
    val newWishes: List<WishPlace>,
    val newReviews: List<DateReview> = emptyList(),
)

/**
 * 백업 파일 (JSON, 사람이 열어 봐도 읽을 수 있게).
 *  { "format": "uridul-backup", "version": 1, "exportedAt": "...", "couple": { startDate, countFromOne, names },
 *    "events": [ ...일정 ], "wishes": [ ...가고 싶은 곳 ] }
 * 불러올 때는 지금 없는 일정·장소만 더한다 (있는 것은 덮어쓰지 않는다).
 */
object Backup {
    const val FORMAT = "uridul-backup"
    const val VERSION = 1
    /** 너무 큰 파일은 읽지 않는다 */
    const val MAX_BYTES = 5 * 1024 * 1024

    fun fileName(today: LocalDate) = "우리둘_백업_$today.json"

    /** 서버 문서 이름으로 써도 안전한 ID 만 (슬래시 등 금지) */
    private val SafeId = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun encode(
        events: List<CalendarEvent>,
        wishes: List<WishPlace>,
        startDate: LocalDate?,
        countFromOne: Boolean?,
        names: Map<String, String>,
        exportedAt: String,
        reviews: List<DateReview> = emptyList(),
    ): String {
        val couple = JSONObject()
            .put("startDate", startDate?.toString() ?: JSONObject.NULL)
            .put("countFromOne", countFromOne ?: JSONObject.NULL)
            .put("names", JSONObject(names as Map<*, *>))
        val wishArr = JSONArray()
        wishes.forEach { w ->
            wishArr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.place.name)
                    .put("url", w.place.url ?: JSONObject.NULL)
                    .put("app", w.place.app?.key ?: JSONObject.NULL)
                    .put("memo", w.memo)
                    .put("visited", w.visited)
                    .put("createdBy", w.createdBy),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("couple", couple)
            // 반복 일정은 원본 하나로 (펼친 날짜가 아니라)
            .put("events", JSONArray(WidgetStore.encode(events.map { it.asSeries() }.distinctBy { it.id })))
            .put("wishes", wishArr)
            .put("reviews", JSONArray().also { arr ->
                reviews.forEach { r ->
                    arr.put(
                        JSONObject()
                            .put("id", r.id).put("eventId", r.eventId).put("date", r.date.toString()).put("title", r.title)
                            .put("placeName", r.place?.name ?: JSONObject.NULL).put("placeUrl", r.place?.url ?: JSONObject.NULL)
                            .put("placeApp", r.place?.app?.key ?: JSONObject.NULL)
                            .put("entries", JSONObject().also { m -> r.entries.forEach { (uid, e) -> m.put(uid, JSONObject().put("text", e.text).put("hearts", e.hearts)) } }),
                    )
                }
            })
            .toString(2)
    }

    /** 우리둘 백업 파일이 아니거나 깨져 있으면 [UserFacingException] */
    fun decode(text: String): BackupData {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw UserFacingException("백업 파일을 읽지 못했어요. 우리둘에서 저장한 파일인지 확인해 주세요.")
        if (root.optString("format") != FORMAT) throw UserFacingException("우리둘 백업 파일이 아니에요.")
        if (root.optInt("version", 0) > VERSION) throw UserFacingException("더 새 버전 앱에서 만든 백업이에요. 앱을 업데이트한 뒤 불러와 주세요.")
        val events = root.optJSONArray("events")?.let { WidgetStore.decode(it.toString()) }.orEmpty()
            // 서버 규칙에 맞지 않는 일정(너무 긴 메모·시간대 등)은 건너뛴다
            .filter { it.id.matches(SafeId) && com.uridul.app.domain.CalendarMath.validate(it.toDraft()) == null }
        val wishes = root.optJSONArray("wishes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.matches(SafeId) } ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotBlank() && it.length <= Place.NAME_MAX } ?: return@mapNotNull null
                WishPlace(
                    id = id,
                    place = Place(
                        name,
                        if (o.isNull("url")) null else o.optString("url").takeIf { it.isNotBlank() },
                        MapApp.from(if (o.isNull("app")) null else o.optString("app")),
                    ),
                    memo = o.optString("memo").take(WishPlace.MEMO_MAX),
                    createdBy = o.optString("createdBy"),
                    visited = o.optBoolean("visited", false),
                )
            }
        }.orEmpty()
        val couple = root.optJSONObject("couple")
        val start = couple?.optString("startDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val reviews = root.optJSONArray("reviews")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                runCatching {
                    val entries = o.getJSONObject("entries").let { m ->
                        m.keys().asSequence().mapNotNull { uid ->
                            val e = m.getJSONObject(uid)
                            val hearts = e.optInt("hearts", 0).takeIf { it in ReviewEntry.HEARTS } ?: return@mapNotNull null
                            uid to ReviewEntry(e.optString("text").take(ReviewEntry.TEXT_MAX), hearts)
                        }.toMap()
                    }
                    DateReview(
                        id = o.getString("id").also { require(it.matches(SafeId)) },
                        eventId = o.getString("eventId"),
                        date = LocalDate.parse(o.getString("date")),
                        title = o.optString("title").take(CalendarEvent.TITLE_MAX),
                        place = if (o.isNull("placeName")) null else Place(o.getString("placeName").take(Place.NAME_MAX), if (o.isNull("placeUrl")) null else o.optString("placeUrl"), MapApp.from(if (o.isNull("placeApp")) null else o.optString("placeApp"))),
                        entries = entries,
                    )
                }.getOrNull()
            }
        }.orEmpty()
        return BackupData(root.optString("exportedAt"), start, events, wishes, reviews)
    }

    /** 지금 서버에 없는 것만 고른다 (같은 ID 가 있으면 건너뜀) */
    fun preview(data: BackupData, existingEventIds: Set<String>, existingWishIds: Set<String>, existingReviewIds: Set<String> = emptySet()): ImportPreview =
        ImportPreview(
            data = data,
            newEvents = data.events.filter { it.id !in existingEventIds },
            newWishes = data.wishes.filter { it.id !in existingWishIds },
            newReviews = data.reviews.filter { it.id !in existingReviewIds },
        )
}

/** 백업 파일 쓰기·읽기 (사용자가 고른 파일 주소로, 백그라운드에서) */
object BackupFiles {
    suspend fun write(context: android.content.Context, uri: android.net.Uri, text: String) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw UserFacingException("파일을 만들지 못했어요.")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }

    suspend fun read(context: android.content.Context, uri: android.net.Uri): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: throw UserFacingException("파일을 열지 못했어요.")
            input.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > Backup.MAX_BYTES) throw UserFacingException("파일이 너무 커요. 우리둘 백업 파일인지 확인해 주세요.")
                bytes.toString(Charsets.UTF_8)
            }
        }
}

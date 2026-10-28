package com.uridul.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.uridul.app.domain.PhotoQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** 사진첩 사진 하나 (목록용 작은 사진 포함, 큰 사진은 따로 받는다) */
data class PhotoMeta(
    val id: String,
    val eventId: String,
    val date: LocalDate,
    val week: String,
    val thumb: ByteArray,
    val width: Int,
    val height: Int,
    val uploadedBy: String,
    val uploadedAtMillis: Long,
) {
    val albumKey: String get() = albumKey(eventId, date)

    override fun equals(other: Any?): Boolean = other is PhotoMeta && other.id == id && other.uploadedAtMillis == uploadedAtMillis
    override fun hashCode(): Int = id.hashCode()

    companion object {
        /** 일정 하루(반복 일정은 날짜마다) 의 사진첩 */
        fun albumKey(eventId: String, date: LocalDate) = "${eventId}_$date"
    }
}

/** 올리기 전에 줄인 사진 */
class ProcessedPhoto(val photo: ByteArray, val thumb: ByteArray, val width: Int, val height: Int)

/**
 * 폰 사진을 줄인다: 긴 변 1280px(큰 사진, 200KB 이하) + 480px(작은 사진, 40KB 이하), JPEG.
 * 정해진 크기를 넘으면 품질을 낮추고, 그래도 크면 더 작게 줄인다 (용량 계획이 지켜지도록).
 */
object PhotoProcessor {

    suspend fun process(context: Context, uri: Uri): ProcessedPhoto = withContext(Dispatchers.Default) {
        val big = decode(context, uri, PhotoQuota.PHOTO_LONG_SIDE)
        val photo = compressUnder(big, PhotoQuota.PHOTO_MAX_BYTES)
        val thumbBitmap = scale(big, PhotoQuota.THUMB_LONG_SIDE)
        val thumb = compressUnder(thumbBitmap, PhotoQuota.THUMB_MAX_BYTES)
        ProcessedPhoto(photo, thumb, big.width, big.height).also {
            if (thumbBitmap !== big) thumbBitmap.recycle()
            big.recycle()
        }
    }

    /** 사진 방향(세로·가로)까지 맞춰서 긴 변 [longSide] 이하로 읽기 */
    private fun decode(context: Context, uri: Uri, longSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val ratio = longSide.toFloat() / max(w, h)
            if (ratio < 1f) decoder.setTargetSize((w * ratio).roundToInt().coerceAtLeast(1), (h * ratio).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    fun scale(src: Bitmap, longSide: Int): Bitmap {
        val ratio = longSide.toFloat() / max(src.width, src.height)
        if (ratio >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * ratio).roundToInt().coerceAtLeast(1), (src.height * ratio).roundToInt().coerceAtLeast(1), true)
    }

    /** JPEG 로 [maxBytes] 이하가 될 때까지 품질 85 → 40, 그래도 크면 85% 크기로 줄여서 다시 */
    fun compressUnder(src: Bitmap, maxBytes: Int): ByteArray {
        var bitmap = src
        repeat(6) {
            for (quality in intArrayOf(85, 75, 65, 55, 45, 40)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) {
                    if (bitmap !== src) bitmap.recycle()
                    return out.toByteArray()
                }
            }
            val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.85f).roundToInt().coerceAtLeast(1), (bitmap.height * 0.85f).roundToInt().coerceAtLeast(1), true)
            if (bitmap !== src) bitmap.recycle()
            bitmap = smaller
        }
        throw UserFacingException("사진을 줄이지 못했어요. 다른 사진으로 해 주세요.")
    }
}

/**
 * couples/{coupleId}/photos/{id}     { eventId, date, week, thumb(작은 사진), width, height, uploadedBy, uploadedAt }
 * couples/{coupleId}/photoData/{id}  { data(큰 사진), uploadedBy }
 * 두 사람만 읽고 쓴다 (firestore.rules). 사진은 바꾸지 않고 올리기·지우기만 한다.
 */
class PhotoRepository(private val db: FirebaseFirestore) {

    private fun photos(coupleId: String) = db.collection("couples").document(coupleId).collection("photos")
    private fun data(coupleId: String) = db.collection("couples").document(coupleId).collection("photoData")

    /** 일정 하루의 사진첩 (올린 순서대로) */
    fun observeAlbum(coupleId: String, eventId: String, date: LocalDate): Flow<List<PhotoMeta>> =
        listen(photos(coupleId).whereEqualTo("eventId", eventId).whereEqualTo("date", date.toString()))

    /** 기간 안의 사진 (추억 슬라이드용) */
    suspend fun fetchRange(coupleId: String, from: LocalDate, to: LocalDate): List<PhotoMeta> =
        photos(coupleId).whereGreaterThanOrEqualTo("date", from.toString()).whereLessThanOrEqualTo("date", to.toString())
            .get().await().documents.mapNotNull { it.toMeta() }

    /** 이번 주에 올린(아직 남아 있는) 사진 수. 사진을 받지 않고 개수만 센다 */
    suspend fun countWeek(coupleId: String, week: String): Int =
        photos(coupleId).whereEqualTo("week", week).count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count.toInt()

    /** 큰 사진 (크게 볼 때만) */
    suspend fun fetchPhoto(coupleId: String, id: String): ByteArray? =
        data(coupleId).document(id).get().await().getBlob("data")?.toBytes()

    /** 올리기: 작은 사진 문서 + 큰 사진 문서를 한 번에 */
    suspend fun upload(coupleId: String, uid: String, event: CalendarEvent, photo: ProcessedPhoto, today: LocalDate) {
        val ref = photos(coupleId).document()
        db.batch()
            .set(
                ref,
                mapOf(
                    "eventId" to event.id,
                    "date" to event.date.toString(),
                    "week" to PhotoQuota.weekKey(today),
                    "thumb" to Blob.fromBytes(photo.thumb),
                    "width" to photo.width,
                    "height" to photo.height,
                    "uploadedBy" to uid,
                    "uploadedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .set(data(coupleId).document(ref.id), mapOf("data" to Blob.fromBytes(photo.photo), "uploadedBy" to uid))
            .commit().await()
    }

    /** 지우기: 두 문서를 함께 */
    suspend fun delete(coupleId: String, id: String) {
        db.batch().delete(photos(coupleId).document(id)).delete(data(coupleId).document(id)).commit().await()
    }

    private fun listen(query: Query): Flow<List<PhotoMeta>> = callbackFlow {
        val reg = query.addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().mapNotNull { it.toMeta() }.sortedBy { it.uploadedAtMillis })
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMeta(): PhotoMeta? {
        val eventId = getString("eventId") ?: return null
        val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val thumb = getBlob("thumb")?.toBytes() ?: return null
        return PhotoMeta(
            id = id,
            eventId = eventId,
            date = date,
            week = getString("week").orEmpty(),
            thumb = thumb,
            width = (get("width") as? Number)?.toInt() ?: 0,
            height = (get("height") as? Number)?.toInt() ?: 0,
            uploadedBy = getString("uploadedBy").orEmpty(),
            // 올린 직후(서버 시각 전)에는 지금 시각으로 두어 맨 뒤에 오게
            uploadedAtMillis = getTimestamp("uploadedAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                ?: System.currentTimeMillis(),
        )
    }
}

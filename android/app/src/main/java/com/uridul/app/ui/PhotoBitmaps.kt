package com.uridul.app.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.uridul.app.data.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 사진 바이트 → 화면 그림. 화면을 그리는 줄에서 풀지 않고 뒤에서 풀어서 넘길 때 끊기지 않게 하고,
 * 한 번 푼 사진은 기억해 둔다.
 *  - 작은 사진(긴 변 480px): 사진첩 줄·추억 슬라이드
 *  - 큰 사진(긴 변 1280px): 일기장 피드·크게 보기 (볼 때 받아온다)
 */

/** 풀어 둔 작은 사진 (최대 16MB, 오래 안 본 것부터 버림) */
private val photoCache = object : android.util.LruCache<String, ImageBitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = (value.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1)
}

/** 작은 사진 바이트 → 화면 그림 ([key] 사진 ID, 풀 동안은 null) */
@Composable
fun rememberPhotoBitmap(key: String, bytes: ByteArray?): ImageBitmap? {
    val cacheKey = "$key:${bytes?.size}"
    val state = produceState(initialValue = bytes?.let { photoCache.get(cacheKey) }, cacheKey) {
        if (value == null && bytes != null) {
            value = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }?.also { photoCache.put(cacheKey, it) }
        }
    }
    return state.value
}

/** 풀어 둔 큰 사진 (최대 64MB·앱 메모리의 1/8, 오래 안 본 것부터 버림). 옆으로 넘겼다 돌아와도 다시 받지 않게 */
private val fullPhotoCache = object : android.util.LruCache<String, ImageBitmap>(
    (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(32 * 1024, 64 * 1024),
) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = (value.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1)
}

/** 큰 사진: 볼 때 받아와서 풀어 둔다 (받는 동안은 null) */
@Composable
internal fun rememberFullPhoto(meta: PhotoMeta): ImageBitmap? {
    val hooks = LocalPhotos.current
    val full by produceState(fullPhotoCache.get(meta.id), meta.id) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            hooks.loadFull(meta.id)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
        }?.also { fullPhotoCache.put(meta.id, it) }
    }
    return full
}

/** 보여줄 사진: 큰 사진이 오기 전에는 작은 사진 ([sharp] = 큰 사진인지) */
internal class PhotoView(val bitmap: ImageBitmap?, val sharp: Boolean)

/** 큰 사진을 받아 보여주고, 받는 동안은 작은 사진 */
@Composable
internal fun rememberSharpPhoto(meta: PhotoMeta): PhotoView {
    val full = rememberFullPhoto(meta)
    val thumb = rememberPhotoBitmap(meta.id, meta.thumb)
    return PhotoView(full ?: thumb, full != null)
}

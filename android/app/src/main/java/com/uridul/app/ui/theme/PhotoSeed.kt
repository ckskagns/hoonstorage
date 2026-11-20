package com.uridul.app.ui.theme

import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 사진에서 씨앗 색 뽑기: 사진 속 색 묶음(swatch) 중 너무 어둡거나 밝지 않고 채도가 있는 색에서
 * "채도 × √(그 색이 차지하는 양)" 이 가장 큰 색. 밤 사진처럼 전체가 차분해도 나무·불빛 색을 고른다.
 * 그런 색이 없으면(흑백 사진 등) null → 앱은 분홍 그대로.
 */
object PhotoSeed {
    suspend fun of(bytes: ByteArray): Int? = withContext(Dispatchers.Default) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 160) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val swatches = Palette.from(bmp).maximumColorCount(24).generate().swatches.also { bmp.recycle() }
            pick(swatches.map { Triple(it.rgb, it.hsl[1], it.hsl[2]) to it.population })
        }.getOrNull()
    }

    /** (색, 채도, 밝기) 와 양 → 씨앗 색 (테스트용으로 따로) */
    fun pick(swatches: List<Pair<Triple<Int, Float, Float>, Int>>): Int? =
        swatches
            .filter { (c, _) -> c.second >= 0.25f && c.third in 0.15f..0.85f }
            .maxByOrNull { (c, pop) -> c.second * Math.sqrt(pop.toDouble()) }
            ?.first?.first
}

package com.uridul.app.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * "디데이 사진 색 따라가기": 디데이 카드 사진에서 뽑은 색(씨앗 색)으로 앱 강조색을 은은하게 바꾼다.
 * 씨앗 색의 색상(H)만 쓰고, 밝기·채도는 정해 둔 값으로 맞춰 글자가 늘 잘 읽히게 한다.
 * (사진이 회색에 가까우면 분홍 그대로)
 */
object PhotoTint {
    private val _seed = MutableStateFlow<Int?>(null)
    /** 지금 디데이 사진의 씨앗 색 (ARGB, 없으면 null) */
    val seed: StateFlow<Int?> = _seed.asStateFlow()

    fun setSeed(argb: Int?) {
        _seed.value = argb
    }

    /** 앱 색 몇 가지 (ARGB) */
    data class Tones(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val background: Int,
    )

    /** 회색에 가까운 색(채도 낮음)은 씨앗으로 쓰지 않는다 */
    const val MIN_SATURATION = 0.18f

    fun tones(seedArgb: Int, dark: Boolean): Tones? {
        val (h, s, _) = hsl(seedArgb)
        if (s < MIN_SATURATION) return null
        val sat = s.coerceIn(0.45f, 0.75f)
        // 노랑~하늘(밝게 보이는 색)은 강조색을 조금 더 어둡게 해야 흰 글자가 읽힌다
        val brightHue = h in 40f..200f
        return if (!dark) Tones(
            primary = argb(h, sat, if (brightHue) 0.34f else 0.44f),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = argb(h, sat * 0.9f, 0.90f),
            onPrimaryContainer = argb(h, sat, 0.14f),
            secondaryContainer = argb(h, 0.35f, 0.90f),
            onSecondaryContainer = argb(h, 0.35f, 0.15f),
            surfaceContainer = argb(h, 0.30f, 0.945f),
            surfaceContainerHigh = argb(h, 0.25f, 0.92f),
            background = argb(h, 0.35f, 0.985f),
        ) else Tones(
            primary = argb(h, sat, 0.78f),
            onPrimary = argb(h, sat, 0.18f),
            primaryContainer = argb(h, sat * 0.6f, 0.30f),
            onPrimaryContainer = argb(h, sat, 0.90f),
            secondaryContainer = argb(h, 0.22f, 0.28f),
            onSecondaryContainer = argb(h, 0.30f, 0.90f),
            surfaceContainer = argb(h, 0.12f, 0.14f),
            surfaceContainerHigh = argb(h, 0.12f, 0.18f),
            background = argb(h, 0.12f, 0.08f),
        )
    }

    /** ARGB → (색상 0~360, 채도 0~1, 밝기 0~1) */
    fun hsl(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        if (d == 0f) return Triple(0f, 0f, l)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (max) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }.let { if (it < 0) it + 360 else it }
        return Triple(h, s.coerceIn(0f, 1f), l)
    }

    fun argb(h: Float, s: Float, l: Float): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** 두 색의 명암 대비 (WCAG, 1~21) */
    fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun f(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * f(c shr 16 and 0xFF) + 0.7152 * f(c shr 8 and 0xFF) + 0.0722 * f(c and 0xFF)
        }
        val (hi, lo) = lum(a).let { la -> lum(b).let { lb -> if (la > lb) la to lb else lb to la } }
        return (hi + 0.05) / (lo + 0.05)
    }
}

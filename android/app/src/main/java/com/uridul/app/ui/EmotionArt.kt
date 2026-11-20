package com.uridul.app.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uridul.app.data.Couple

/**
 * 커플 캐릭터 감정 그림 25장 (docs/캐릭터_제작가이드.md 의 표와 같은 번호·이름).
 * 그림 파일 `res/drawable-nodpi/char_NN_key.png` 이 있으면 그 그림, 없으면 지금 캐릭터 둘(용·토끼 또는 연인)을 그린다.
 * → 그림을 받으면 scripts/import_characters.ps1 로 넣기만 하면 앱 곳곳에 바로 나온다 (코드 고칠 필요 없음).
 */
enum class Emotion(val no: Int, val key: String, val label: String) {
    BASE(0, "base", "기본"),
    JOY(1, "joy", "기쁨"),
    LOVE(2, "love", "사랑"),
    HUG(3, "hug", "꼬옥"),
    KISS(4, "kiss", "뽀뽀"),
    FLUTTER(5, "flutter", "설렘"),
    CELEBRATE(6, "celebrate", "축하"),
    THANKS(7, "thanks", "고마워"),
    SORRY(8, "sorry", "미안해"),
    SULK(9, "sulk", "삐짐"),
    SAD(10, "sad", "슬픔"),
    SURPRISE(11, "surprise", "놀람"),
    GOODNIGHT(12, "goodnight", "잘 자"),
    MORNING(13, "morning", "굿모닝"),
    YUMMY(14, "yummy", "맛있다"),
    DATE(15, "date", "데이트 가자"),
    TRIP(16, "trip", "여행"),
    PHOTO(17, "photo", "사진"),
    SAVE(18, "save", "아껴 쓰자"),
    MISS(19, "miss", "보고 싶어"),
    CHEER(20, "cheer", "파이팅"),
    THINK(21, "think", "고민"),
    LETTER(22, "letter", "편지"),
    PAT(23, "pat", "토닥토닥"),
    REST(24, "rest", "함께 쉬기");

    /** 그림 파일 이름 (확장자 빼고): char_03_hug */
    val resName: String get() = "char_%02d_%s".format(no, key)

    companion object {
        /** 시간대에 맞는 기본 그림: 밤(22~5시) 잘 자, 아침(5~9시) 굿모닝, 그 밖에는 기본 */
        fun forHour(hour: Int): Emotion = when (hour) {
            in 22..23, in 0..4 -> GOODNIGHT
            in 5..8 -> MORNING
            else -> BASE
        }
    }
}

/** 그 감정 그림의 리소스 ID (없으면 0). 그림 파일 이름으로 찾는다 */
@SuppressLint("DiscouragedApi")
@Composable
fun rememberEmotionRes(emotion: Emotion): Int {
    val context = LocalContext.current
    return remember(emotion) { CharArt.res(context, emotion.resName) }
}

/**
 * 우리 캐릭터 그림을 이름으로 찾기 (없으면 0).
 * 그림은 git 밖(src/chars)에서 빌드할 때만 들어가므로 R.drawable 로 바로 부를 수 없다.
 * 이름으로 찾는 건 느린 편이라 앱이 켜져 있는 동안 한 번만 (그림은 설치할 때 정해지므로 바뀌지 않음).
 */
object CharArt {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun res(context: android.content.Context, name: String): Int =
        cache.getOrPut(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }

    /** 알림 큰 아이콘용 작은 비트맵 (그림이 없으면 null) */
    fun bitmap(context: android.content.Context, name: String, sizePx: Int = 192): android.graphics.Bitmap? {
        val id = res(context, name)
        if (id == 0) return null
        return runCatching {
            val full = android.graphics.BitmapFactory.decodeResource(context.resources, id) ?: return null
            full.scale(sizePx, sizePx).also { if (it !== full) full.recycle() }
        }.getOrNull()
    }

    /** 우리 캐릭터 그림이 들어 있는 앱인지 (고르기 목록에 "우리 캐릭터"를 보일지) */
    fun available(context: android.content.Context): Boolean = res(context, "char_bust_he") != 0
}

/** 작은 장식 그림 (벤토 빈 칸·알림 목록 등). 그림이 없는 앱이면 아무것도 그리지 않는다 */
@Composable
fun SmallArt(emotion: Emotion, size: Dp, modifier: Modifier = Modifier) {
    val res = rememberEmotionRes(emotion)
    if (res != 0) {
        Image(painterResource(res), contentDescription = null, modifier = modifier.size(size).artBacking(isDarkSurface()))
    }
}

/** 다크 모드 받침 색: 머리카락·외곽선이 어두운 배경에 묻히지 않게 (대비 1.3:1 → 받침 위 10:1 이상) */
private val ArtBacking = androidx.compose.ui.graphics.Color(0xFFFFE6EC)

/** 지금 화면이 어두운 테마인지 (앱 설정·사진 색 테마까지 반영된 배경색으로 판단) */
@Composable
internal fun isDarkSurface(): Boolean = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.4f

/** 다크 모드에서만 그림 뒤에 연분홍 동그란 받침 (가장자리는 흐리게) */
internal fun Modifier.artBacking(dark: Boolean): Modifier = if (!dark) this else drawBehind {
    drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(
            0f to ArtBacking, 0.82f to ArtBacking, 1f to ArtBacking.copy(alpha = 0f),
            center = center, radius = size.minDimension / 2f,
        ),
        radius = size.minDimension / 2f,
    )
}

/**
 * 감정 그림 한 장. 그림 파일이 아직 없으면 [fallback] (기본: 두 사람 캐릭터 나란히).
 * @param size 그림 한 변 (그림은 정사각형으로 만든다)
 */
@Composable
fun EmotionArt(
    emotion: Emotion,
    couple: Couple,
    myUid: String,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: (@Composable () -> Unit)? = null,
) {
    val res = rememberEmotionRes(emotion)
    if (res != 0) {
        Image(
            painterResource(res),
            contentDescription = "캐릭터 · ${emotion.label}",
            modifier = modifier.size(size).artBacking(isDarkSurface()),
        )
    } else if (fallback != null) {
        fallback()
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CharacterAvatar(couple.myCharacter(myUid), size * 0.45f, bob = true, phase = 0)
            CharacterAvatar(couple.partnerCharacter(myUid), size * 0.45f, bob = true, phase = 1)
        }
    }
}

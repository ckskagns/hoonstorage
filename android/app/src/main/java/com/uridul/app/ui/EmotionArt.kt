package com.uridul.app.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    return remember(emotion) {
        // 이름으로 찾는 건 느린 편이라 앱이 켜져 있는 동안 한 번만 (그림은 설치할 때 정해지므로 바뀌지 않음)
        EmotionResCache.map.getOrPut(emotion) { context.resources.getIdentifier(emotion.resName, "drawable", context.packageName) }
    }
}

private object EmotionResCache {
    val map = java.util.concurrent.ConcurrentHashMap<Emotion, Int>()
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
        Image(painterResource(res), contentDescription = "캐릭터 · ${emotion.label}", modifier = modifier.size(size))
    } else if (fallback != null) {
        fallback()
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CharacterAvatar(couple.myCharacter(myUid), size * 0.45f, bob = true, phase = 0)
            CharacterAvatar(couple.partnerCharacter(myUid), size * 0.45f, bob = true, phase = 1)
        }
    }
}

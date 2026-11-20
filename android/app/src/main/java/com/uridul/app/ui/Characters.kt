package com.uridul.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uridul.app.R
import com.uridul.app.data.Character
import com.uridul.app.data.NAME_MAX
import com.uridul.app.data.PartnerDefaults
import kotlinx.coroutines.launch

@get:DrawableRes
val Character.drawable: Int
    get() = when (this) {
        Character.DRAGON -> R.drawable.char_dragon
        Character.BUNNY -> R.drawable.char_bunny
        Character.COUPLE_HE -> R.drawable.char_couple_he
        Character.COUPLE_SHE -> R.drawable.char_couple_she
        // 우리 캐릭터 그림이 없는 앱에서는 연인 캐릭터로
        Character.OUR_HE -> R.drawable.char_couple_he
        Character.OUR_SHE -> R.drawable.char_couple_she
    }

/**
 * 캐릭터 얼굴. bob = true 면 살랑살랑 위아래로 움직이고, 누르면 콩 뛰어오른다.
 * phase 로 두 캐릭터가 엇갈려 움직이게 한다.
 */
@Composable
fun CharacterAvatar(
    character: Character,
    size: Dp,
    modifier: Modifier = Modifier,
    bob: Boolean = false,
    phase: Int = 0,
    contentDescription: String? = null,
    /** false 면 눌러도 뛰지 않음 (카드 안에서 카드 누르기를 가로채지 않게) */
    interactive: Boolean = true,
) {
    val density = LocalDensity.current
    val bobOffset = if (bob) {
        val transition = rememberInfiniteTransition(label = "bob")
        val t by transition.animateFloat(
            initialValue = if (phase % 2 == 0) 0f else 1f,
            targetValue = if (phase % 2 == 0) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bobT",
        )
        with(density) { (t * 3.dp.toPx()) }
    } else {
        0f
    }
    val jump = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 우리 캐릭터: 얼굴 그림이 들어 있으면 동그란 얼굴로 (연분홍 바탕이라 다크 모드에서도 또렷)
    val context = androidx.compose.ui.platform.LocalContext.current
    val art = remember(character) { character.artName?.let { CharArt.res(context, it) } ?: 0 }
    Image(
        painter = painterResource(if (art != 0) art else character.drawable),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .then(
                if (art != 0) Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFFFFE6EC))
                else Modifier,
            )
            .graphicsLayer {
                translationY = -bobOffset - jump.value * size.toPx() * 0.18f
                val squash = 1f + jump.value * 0.06f
                scaleX = squash
                scaleY = 2f - squash
            }
            .clickable(
                enabled = interactive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Image,
            ) {
                scope.launch {
                    jump.animateTo(1f, tween(140))
                    jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            },
    )
}

/** 홈 화면 맨 위: [내 캐릭터] 이름 ♥ 이름 [상대 캐릭터] */
@Composable
fun CoupleHeader(
    myCharacter: Character,
    partnerCharacter: Character,
    myName: String,
    partnerName: String?,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
) {
    val heart = rememberInfiniteTransition(label = "heart")
    val beat by heart.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beat",
    )
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CharacterAvatar(myCharacter, avatarSize, bob = true, phase = 0, contentDescription = "$myName 캐릭터")
            NameLabel(myName)
        }
        Text(
            "♥",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .graphicsLayer { scaleX = beat; scaleY = beat },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CharacterAvatar(
                partnerCharacter,
                avatarSize,
                bob = true,
                phase = 1,
                contentDescription = "${partnerName ?: "상대"} 캐릭터",
                modifier = Modifier.graphicsLayer { alpha = if (partnerName == null) 0.45f else 1f },
            )
            NameLabel(partnerName ?: "?")
        }
    }
}

@Composable
private fun NameLabel(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 캐릭터 고르기 카드 두 장 */
@Composable
private fun CharacterPicker(selected: Character, onSelect: (Character) -> Unit) {
    // 한 줄에 한 쌍씩 (동물 / 연인 / 우리 캐릭터)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      // 우리 캐릭터 그림은 그림이 들어 있는 앱에서만
      val context = androidx.compose.ui.platform.LocalContext.current
      val choices = remember { Character.entries.filter { it.artName == null || CharArt.available(context) } }
      choices.chunked(2).forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        pair.forEach { c ->
            val isSelected = c == selected
            Card(
                onClick = { onSelect(c) },
                modifier = Modifier.weight(1f),
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CharacterAvatar(c, 56.dp, interactive = false)
                    Text(c.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
      }
      }
    }
}

/**
 * 내 프로필: 이름 + 캐릭터. 상대가 아직 연결 전이면 상대 이름·캐릭터도 미리 정한다
 * (초대 링크에 함께 실려 가서 상대가 연결하면 그대로 적용된다).
 */
@Composable
fun ProfileDialog(
    myName: String,
    myCharacter: Character,
    partnerDefaults: PartnerDefaults?,
    isPaired: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Character, PartnerDefaults?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(myName) }
    var character by rememberSaveable { mutableStateOf(myCharacter) }
    var partnerName by rememberSaveable { mutableStateOf(partnerDefaults?.name.orEmpty()) }
    var partnerCharacter by rememberSaveable { mutableStateOf(partnerDefaults?.character ?: myCharacter.other) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내 프로필") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NAME_MAX) },
                    label = { Text("내 이름") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("내 캐릭터", style = MaterialTheme.typography.labelLarge)
                CharacterPicker(character) { character = it }

                if (!isPaired) {
                    HorizontalDivider()
                    Text(
                        "연결 전 상대 정보 (초대 링크에 함께 보내져요)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedTextField(
                        value = partnerName,
                        onValueChange = { partnerName = it.take(NAME_MAX) },
                        label = { Text("상대 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CharacterPicker(partnerCharacter) { partnerCharacter = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val partner = if (!isPaired && partnerName.isNotBlank()) PartnerDefaults(partnerName, partnerCharacter) else null
                onSave(name, character, partner)
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 빈 화면용: 두 캐릭터가 나란히 */
@Composable
fun CharacterPair(first: Character, second: Character, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CharacterAvatar(first, size, bob = true, phase = 0)
            CharacterAvatar(second, size, bob = true, phase = 1)
        }
    }
}

package com.zaijian.zhoumuyun.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaijian.zhoumuyun.data.model.CharacterConfig
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.data.model.DefaultPresenceStates
import com.zaijian.zhoumuyun.data.model.PresenceState
import com.zaijian.zhoumuyun.data.model.StatusType
import com.zaijian.zhoumuyun.data.model.dotColor
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.AvatarSize
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.RingWidth
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme
import com.zaijian.zhoumuyun.ui.theme.appSpring
import com.zaijian.zhoumuyun.ui.theme.snapSpring
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  BookCard  — 书架里的单本书（3 × 3 = 9 本）
//  设计规范 §11  书本卡片
//
//  层级（由下至上）：
//    [0] 封面渐变背景
//    [1] 左侧书脊色条（12dp 固定宽）
//    [2] 角色头像（oval 中部，上移 8dp，向右让出书脊）
//    [3] 角色名（底部居中）
//    [4] 状态点（右上角 8dp 圆点）
//
//  交互：
//    单击 → onBookClick（进入角色详情）
//    长按 → onBookLongClick（快速预览 BottomSheet）
//    按下 → scale 0.96 弹性回弹
// ─────────────────────────────────────────────────────────────

@Composable
fun BookCard(
    character: CharacterConfig,
    presence: PresenceState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope   = rememberCoroutineScope()
    val scale   = remember { Animatable(1f) }
    val isLocked = !character.isUnlocked

    // Darken accent for spine and gradient bottom
    val spineColor = character.accentColor.darkenBy(0.28f)
    val gradBottom = character.accentColor.darkenBy(0.18f)

    val coverBrush = if (isLocked) {
        Brush.verticalGradient(listOf(Color(0xFF4A3828), Color(0xFF2E2018)))
    } else {
        Brush.verticalGradient(listOf(character.accentColor, gradBottom))
    }

    Box(
        modifier = modifier
            .aspectRatio(0.69f)
            .scale(scale.value)
            .clip(RoundedCornerShape(Radius.sm))
            .background(coverBrush)
            .pointerInput(character.id) {
                detectTapGestures(
                    onTap = { if (!isLocked) onClick() },
                    onLongPress = { onLongClick() },
                    onPress = {
                        scope.launch { scale.animateTo(0.96f, snapSpring) }
                        tryAwaitRelease()
                        scope.launch { scale.animateTo(1f, appSpring) }
                    },
                )
            },
    ) {
        // ── [1] 左侧书脊 ──────────────────────────────────────
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight()
                .background(
                    if (isLocked) Color(0xFF2A1E12) else spineColor,
                ),
        )

        if (isLocked) {
            // ── 未解锁占位 ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text       = "?",
                    color      = Color.White.copy(alpha = 0.30f),
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = character.name,
                    color      = Color.White.copy(alpha = 0.22f),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
            }
        } else {
            // ── [2] 呼吸头像（中部，让出书脊后居中，上移 8dp）──
            BreathingAvatar(
                imageUrl     = character.avatarUrl,
                breathColor  = character.accentColor,
                statusType   = presence.statusType,
                size         = AvatarSize.shelf,
                ringWidth    = RingWidth.shelf,
                enableBreath = presence.statusType == StatusType.ACTIVE,
                modifier     = Modifier
                    .align(Alignment.Center)
                    .padding(start = 12.dp)
                    .offset(y = (-8).dp),
            )

            // ── [3] 角色名（底部居中）─────────────────────────
            Text(
                text       = character.name,
                color      = Color.White,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp, start = 14.dp, end = 4.dp),
            )

            // ── [4] 状态点（右上角）───────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(presence.statusType.dotColor()),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Color helper — darken an accent color by a fraction (0–1)
//  用于书脊深色和渐变底色
// ─────────────────────────────────────────────────────────────

fun Color.darkenBy(fraction: Float): Color = copy(
    red   = (red   * (1f - fraction)).coerceIn(0f, 1f),
    green = (green * (1f - fraction)).coerceIn(0f, 1f),
    blue  = (blue  * (1f - fraction)).coerceIn(0f, 1f),
)

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(name = "BookCard · Active · Dark", showBackground = true,
    backgroundColor = 0xFF12131A.toLong(), widthDp = 110, heightDp = 160)
@Composable
private fun PreviewBookCardActiveDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) {
        BookCard(
            character   = DefaultCharacters[0],
            presence    = DefaultPresenceStates[0],
            onClick     = {},
            onLongClick = {},
        )
    }
}

@Preview(name = "BookCard · Idle · Light", showBackground = true, widthDp = 110, heightDp = 160)
@Composable
private fun PreviewBookCardIdleLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) {
        BookCard(
            character   = DefaultCharacters[1],
            presence    = DefaultPresenceStates[1],
            onClick     = {},
            onLongClick = {},
        )
    }
}

@Preview(name = "BookCard · Locked · Dark", showBackground = true,
    backgroundColor = 0xFF12131A.toLong(), widthDp = 110, heightDp = 160)
@Composable
private fun PreviewBookCardLocked() {
    ZaijianTheme(appTheme = AppTheme.DARK) {
        BookCard(
            character   = DefaultCharacters[8], // 江凡 isUnlocked = false
            presence    = DefaultPresenceStates[8],
            onClick     = {},
            onLongClick = {},
        )
    }
}

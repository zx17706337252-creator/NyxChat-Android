package com.zaijian.zhoumuyun.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────
//  Spacing  (4dp grid)
// ─────────────────────────────────────────────────────────────
object Spacing {
    val xs   : Dp = 4.dp
    val sm   : Dp = 8.dp
    val md   : Dp = 16.dp
    val lg   : Dp = 24.dp
    val xl   : Dp = 32.dp
    val xxl  : Dp = 48.dp

    /** 页面左右边距 */
    val screenHorizontal: Dp = 20.dp
    /** 卡片内边距 */
    val cardPadding: Dp = 16.dp
    /** 列表项高（单行） */
    val listItemSingle: Dp = 56.dp
    /** 列表项高（双行） */
    val listItemDouble: Dp = 72.dp
    /** 底部导航栏高 */
    val bottomNavHeight: Dp = 64.dp
    /** 顶部 Header 高 */
    val topBarHeight: Dp = 56.dp
}

// ─────────────────────────────────────────────────────────────
//  Corner radius  (5-stop scale)
// ─────────────────────────────────────────────────────────────
object Radius {
    /** 小标签 / 徽章 */
    val xs  : Dp = 6.dp
    /** 输入框 / 小卡片 */
    val sm  : Dp = 12.dp
    /** 书本卡片 / 标准卡片 */
    val md  : Dp = 20.dp
    /** 底部弹窗 / 大卡片 */
    val lg  : Dp = 28.dp
    /** 头像 / 全圆 — use CircleShape in code */
    val circle: Dp = 999.dp
}

// ─────────────────────────────────────────────────────────────
//  Avatar sizes
// ─────────────────────────────────────────────────────────────
object AvatarSize {
    /** 公馆窗口 */
    val mansion  : Dp = 52.dp
    /** 书架书脊 */
    val shelf    : Dp = 44.dp
    /** 聊天气泡旁 */
    val chat     : Dp = 32.dp
    /** 角色详情页 */
    val detail   : Dp = 80.dp
    /** 任务 / 小工具 */
    val small    : Dp = 24.dp
}

// ─────────────────────────────────────────────────────────────
//  Status ring widths
// ─────────────────────────────────────────────────────────────
object RingWidth {
    val mansion : Dp = 2.5.dp
    val shelf   : Dp = 2.dp
    val chat    : Dp = 1.5.dp
    val detail  : Dp = 3.dp
}

// ─────────────────────────────────────────────────────────────
//  Status dot size
// ─────────────────────────────────────────────────────────────
object DotSize {
    val normal : Dp = 10.dp
    val small  : Dp = 8.dp
}

// ─────────────────────────────────────────────────────────────
//  Glassmorphism opacities (as float 0–1)
// ─────────────────────────────────────────────────────────────
object GlassOpacity {
    const val topBarLight   = 0.92f
    const val topBarDark    = 0.75f
    const val bottomNav     = 0.88f
    const val windowMask    = 0.85f   // 公馆亮灯遮罩
    const val previewCard   = 0.80f   // 快速预览卡
    const val fullscreenDim = 0.60f   // 全屏弹窗遮罩
}

// ─────────────────────────────────────────────────────────────
//  Animation durations (milliseconds)
// ─────────────────────────────────────────────────────────────
object AnimDuration {
    const val instant    = 80    // 按钮按下态
    const val fast       = 150   // Tab 切换、状态变化
    const val bottomSheet = 220  // BottomSheet 出现
    const val pageSwitch = 250   // 页面切换
    const val bookOpen   = 300   // 书本翻开
    const val breath     = 4000  // 呼吸半周期 (full = 8000ms)
}

// ─────────────────────────────────────────────────────────────
//  Chat bubble
// ─────────────────────────────────────────────────────────────
object BubbleDimen {
    /** 气泡最大宽度比例 */
    const val maxWidthFraction = 0.72f
}

// ─────────────────────────────────────────────────────────────
//  Elevation / shadow layers (dp values for BoxShadow / Modifier.shadow)
// ─────────────────────────────────────────────────────────────
object Elevation {
    /** 书本卡片、普通卡片 */
    val card     : Dp = 2.dp
    /** 悬浮卡片、快速预览 */
    val elevated : Dp = 4.dp
    /** 全屏弹窗 */
    val dialog   : Dp = 8.dp
}

// ─────────────────────────────────────────────────────────────
//  CompositionLocal — reserved for future per-screen overrides
// ─────────────────────────────────────────────────────────────
@Immutable
data class AppDimens(
    val spacing: SpacingTokens = SpacingTokens(),
    val radius: RadiusTokens = RadiusTokens(),
)

@Immutable
data class SpacingTokens(
    val xs: Dp = Spacing.xs,
    val sm: Dp = Spacing.sm,
    val md: Dp = Spacing.md,
    val lg: Dp = Spacing.lg,
    val xl: Dp = Spacing.xl,
    val xxl: Dp = Spacing.xxl,
)

@Immutable
data class RadiusTokens(
    val xs: Dp = Radius.xs,
    val sm: Dp = Radius.sm,
    val md: Dp = Radius.md,
    val lg: Dp = Radius.lg,
)

val LocalAppDimens = staticCompositionLocalOf { AppDimens() }

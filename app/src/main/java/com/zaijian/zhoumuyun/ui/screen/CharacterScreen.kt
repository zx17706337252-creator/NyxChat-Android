package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.zaijian.zhoumuyun.R
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zaijian.zhoumuyun.ui.component.CharacterPreviewSheet
import com.zaijian.zhoumuyun.ui.component.ShelfRow
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.GlassOpacity
import com.zaijian.zhoumuyun.ui.theme.Palette
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme
import com.zaijian.zhoumuyun.ui.viewmodel.PresenceViewModel

// ─────────────────────────────────────────────────────────────
//  CharacterScreen  — 书架世界（Tab 2）
//  设计规范 §11
//
//  层级（从后到前）：
//    [0] ShelfBackground（Canvas 木纹背景）
//    [1] 可滚动内容（ShelfSlat × 3 + ShelfRow × 3 + NoteCard）
//    [2] ShelfHeader（毛玻璃顶栏，固定悬浮）
//    [3] CharacterPreviewSheet（长按书本弹出）
//
//  导航：
//    单击书本 → onNavigateToDetail（书打开动画，Phase 4 完善）
//    长按书本 → 预览 BottomSheet → 「发起对话」onNavigateToChat
//                              → 「查看完整档案」onNavigateToDetail
// ─────────────────────────────────────────────────────────────

@Composable
fun CharacterScreen(
    onNavigateToDetail: (characterId: Int) -> Unit = {},
    onNavigateToChat: (characterId: Int) -> Unit = {},
    viewModel: PresenceViewModel = viewModel(),
) {
    val uiState     by viewModel.uiState.collectAsState()
    val colors      = ZaijianTheme.colors
    val type        = ZaijianTheme.typography
    val scrollState = rememberScrollState()

    // Long-press preview state 独立于 WorldScreen，不共享
    var previewCharacterId by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        // ── [0] 木纹背景 ──────────────────────────────────────
        ShelfBackground(
            isDark   = colors.isDark,
            modifier = Modifier.fillMaxSize(),
        )

        // ── [1] 可滚动内容 ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // 顶栏占位，防止内容被 Header 遮住
            Spacer(Modifier.height(Spacing.topBarHeight + Spacing.md))

            // 副标题区
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.screenHorizontal),
            ) {
                Text(
                    text  = "她们所有的过去",
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // ── 三行书架 ──────────────────────────────────────
            // row 1 = 二楼（蒂法·露娜·伊芙）
            // row 2 = 一楼（宥熙·索菲娅·顾澜）
            // row 3 = 地下室（明媚·莫婉凝·江凡）
            (1..3).forEach { rowIndex ->
                val rowLabel = when (rowIndex) {
                    1 -> "二楼"
                    2 -> "一楼"
                    else -> "地下室"
                }
                val rowChars = uiState.characters
                    .filter { it.shelfRow == rowIndex }
                    .sortedBy { it.shelfCol }

                // 楼层标签
                ShelfRowLabel(
                    label  = rowLabel,
                    isDark = colors.isDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal),
                )
                Spacer(Modifier.height(Spacing.xs))

                // 木质搁板
                ShelfSlat(
                    isDark   = colors.isDark,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))

                // 书本行
                ShelfRow(
                    characters      = rowChars,
                    presenceMap     = uiState.presenceMap,
                    onBookClick     = { id -> onNavigateToDetail(id) },
                    onBookLongClick = { id -> previewCharacterId = id },
                    modifier        = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal),
                )

                Spacer(Modifier.height(Spacing.lg))
            }

            // ── 便条区域（亮色主题专属）──────────────────────
            if (!colors.isDark) {
                val noteChar = uiState.characters.firstOrNull { it.isUnlocked }
                if (noteChar != null) {
                    NoteCard(
                        characterName = noteChar.name,
                        accentColor   = noteChar.accentColor,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screenHorizontal),
                    )
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        // ── [2] 毛玻璃 Header（固定悬浮）─────────────────────
        ShelfHeader(
            isDark   = colors.isDark,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )
    }

    // ── [3] 长按预览底部弹窗 ──────────────────────────────────
    val previewId = previewCharacterId
    if (previewId != null) {
        val character = uiState.characters.find { it.id == previewId }
        val presence  = uiState.presenceMap[previewId]

        if (character != null && presence != null) {
            CharacterPreviewSheet(
                character     = character,
                presence      = presence,
                onDismiss     = { previewCharacterId = null },
                onStartChat   = { id ->
                    previewCharacterId = null
                    onNavigateToChat(id)
                },
                onViewProfile = { id ->
                    previewCharacterId = null
                    onNavigateToDetail(id)
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Private sub-composables
// ─────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────
//  ShelfBackground
//  【接入说明】插图就绪后放入 res/drawable：
//    shelf_day.png / shelf_night.png
//  然后将 USE_SHELF_ILLUSTRATION = true
// ─────────────────────────────────────────────────────────────
private const val USE_SHELF_ILLUSTRATION = true

/**
 * 书架背景
 * 亮色：暖米白（对齐图2 Parchment）  /  暗色：深棕烛光（对齐图4）
 */
@Composable
private fun ShelfBackground(isDark: Boolean, modifier: Modifier = Modifier) {
    if (USE_SHELF_ILLUSTRATION) {
        val res = if (isDark) R.drawable.shelf_night else R.drawable.shelf_day
        Image(
            painter      = painterResource(res),
            contentDescription = null,
            modifier     = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Canvas(modifier = modifier) {
            val bgColors = if (isDark) {
                listOf(Color(0xFF14100C), Color(0xFF1E1810), Color(0xFF16120E))
            } else {
                listOf(Color(0xFFF2EAE0), Color(0xFFFAF5EE), Color(0xFFF5EFE6))
            }
            drawRect(brush = Brush.verticalGradient(bgColors))

            val grainColor = if (isDark) Color(0x08E8D8C0) else Color(0x0A5C3A1A)
            val lineCount  = 30
            val lineStep   = size.height / lineCount
            repeat(lineCount) { i ->
                val y = i * lineStep + lineStep * 0.3f
                drawLine(
                    color       = grainColor,
                    start       = Offset(0f, y),
                    end         = Offset(size.width, y + size.width * 0.012f),
                    strokeWidth = if (i % 5 == 0) 1.8f else 1.0f,
                )
            }
            if (isDark) {
                drawCircle(
                    brush  = Brush.radialGradient(
                        listOf(Color(0x18FFA040), Color(0x00FFA040)),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.15f, size.height * 0.85f),
                )
            }
        }
    }
}

/**
 * 搁板横条（行与行之间的木质分隔）
 */
@Composable
private fun ShelfSlat(isDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .background(
                Brush.verticalGradient(
                    if (isDark) listOf(Color(0xFF302618), Color(0xFF1E1510))
                    else         listOf(Color(0xFFD6BC9C), Color(0xFFC4A882)),
                ),
            ),
    )
    // 搁板底部细阴影线
    Box(
        modifier = modifier
            .height(2.dp)
            .background(
                if (isDark) Color(0x30000000) else Color(0x18000000),
            ),
    )
}

/**
 * 行标签（二楼 / 一楼 / 地下室）
 */
@Composable
private fun ShelfRowLabel(
    label: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (isDark) Color(0x80D4C4A8) else Color(0x80795540)

    Row(
        modifier          = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(labelColor.copy(alpha = 0.4f)),
        )
        Text(
            text     = label,
            style    = ZaijianTheme.typography.label,
            color    = labelColor,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        Box(
            Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(labelColor.copy(alpha = 0.4f)),
        )
    }
}

/**
 * 书架顶部悬浮 Header（毛玻璃）
 * 亮色：奶白半透明  /  暗色：深棕半透明
 */
@Composable
private fun ShelfHeader(isDark: Boolean, modifier: Modifier = Modifier) {
    val bgColor    = if (isDark)
        Palette.Night.copy(alpha = GlassOpacity.topBarDark)
    else
        Color(0xF0F5EFE6)
    val borderColor = if (isDark)
        Color(0x15FFFFFF)
    else
        Color(0x20795540)

    Box(
        modifier = modifier
            .height(Spacing.topBarHeight)
            .background(bgColor)
            .border(
                width  = 0.5.dp,
                color  = borderColor,
                shape  = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text     = "书架",
            style    = ZaijianTheme.typography.titleBold,
            color    = ZaijianTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
        )
    }
}

/**
 * 书架底部便条卡片（仅亮色主题显示）
 * 设计规范 §11  书架底部便条区域
 */
@Composable
private fun NoteCard(
    characterName: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(Color(0xFFFAF7F2))
            .border(
                width  = 0.5.dp,
                color  = accentColor.copy(alpha = 0.20f),
                shape  = RoundedCornerShape(Radius.sm),
            )
            .height(IntrinsicSize.Min)
            .padding(Spacing.md),
    ) {
        // 左侧竖线装饰
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                text  = "$characterName 在书页里夹了一张纸条：",
                style = type.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "\"还在想你那天说的话。\"",
                style = type.caption.copy(fontStyle = FontStyle.Italic),
                color = colors.textDisabled,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(name = "CharacterScreen · Dark", showBackground = true,
    backgroundColor = 0xFF1A1610.toLong(), widthDp = 390, heightDp = 844)
@Composable
private fun PreviewCharacterScreenDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) {
        CharacterScreen()
    }
}

@Preview(name = "CharacterScreen · Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PreviewCharacterScreenLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) {
        CharacterScreen()
    }
}

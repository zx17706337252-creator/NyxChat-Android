package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zaijian.zhoumuyun.data.model.FloorEnum
import com.zaijian.zhoumuyun.ui.component.CharacterPreviewSheet
import com.zaijian.zhoumuyun.ui.component.FloorSection
import com.zaijian.zhoumuyun.ui.component.MansionBackground
import com.zaijian.zhoumuyun.ui.component.MansionHeader
import com.zaijian.zhoumuyun.ui.component.OnboardingTooltip
import com.zaijian.zhoumuyun.ui.component.TaskCenterEntryCard
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme
import com.zaijian.zhoumuyun.ui.viewmodel.PresenceViewModel

// ─────────────────────────────────────────────────────────────
//  WorldScreen  — 公馆首页（阶段二核心页面）
//  设计规范 §10
//
//  层级（从后到前）：
//    [0] MansionBackground（Canvas 绘制的建筑背景）
//    [1] 滚动内容（楼层列表）
//    [2] MansionHeader（毛玻璃顶栏，固定悬浮）
//    [3] CharacterPreviewSheet（长按弹出，覆盖全屏）
//    [4] OnboardingTooltip（首次引导，固定在底部）← Phase 3 新增
// ─────────────────────────────────────────────────────────────

@Composable
fun WorldScreen(
    onNavigateToChat: (characterId: Int) -> Unit = {},
    onNavigateToProfile: (characterId: Int) -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    viewModel: PresenceViewModel = viewModel(),
) {
    val uiState     by viewModel.uiState.collectAsState()
    val colors      = ZaijianTheme.colors
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        // ── [0] 建筑背景（全屏）──────────────────────────────
        MansionBackground(modifier = Modifier.fillMaxSize())

        // ── [1] 主要可滚动内容 ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(Spacing.topBarHeight + Spacing.lg + Spacing.lg))

            listOf(FloorEnum.SECOND, FloorEnum.FIRST, FloorEnum.BASEMENT).forEach { floor ->
                val floorCharacters = uiState.characters
                    .filter { it.floor == floor }
                    .sortedBy { it.shelfCol }

                FloorSection(
                    floor             = floor,
                    characters        = floorCharacters,
                    presenceMap       = uiState.presenceMap,
                    onWindowClick     = { charId -> onNavigateToChat(charId) },
                    onWindowLongClick = { charId -> viewModel.showPreview(charId) },
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal),
                )

                Spacer(Modifier.height(Spacing.md))
            }

            Spacer(Modifier.height(Spacing.sm))
            TaskCenterEntryCard(
                activeTaskCount = 0,
                onClick         = onNavigateToTasks,
                modifier        = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal),
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        // ── [2] 毛玻璃 Header（固定在顶部）───────────────────
        MansionHeader(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        // ── [4] 首次引导 Tooltip（固定在底部，导航栏上方）────
        //  Phase 3 新增 — 设计规范 §12 连接逻辑
        //  仅在 showOnboardingTooltip = true 时可见，3 秒自动消失
        OnboardingTooltip(
            visible   = uiState.showOnboardingTooltip,
            onDismiss = { viewModel.dismissOnboarding() },
            modifier  = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.sm),
        )
    }

    // ── [3] 长按预览底部弹窗 ──────────────────────────────────
    val previewId = uiState.previewCharacterId
    if (previewId != null) {
        val character = uiState.characters.find { it.id == previewId }
        val presence  = uiState.presenceMap[previewId]

        if (character != null && presence != null) {
            CharacterPreviewSheet(
                character     = character,
                presence      = presence,
                onDismiss     = {
                    viewModel.dismissPreview()
                    // 首次长按后关闭预览卡，触发引导 Tooltip
                    viewModel.markFirstInteraction()
                },
                onStartChat   = { id ->
                    viewModel.dismissPreview()
                    onNavigateToChat(id)
                },
                onViewProfile = { id ->
                    viewModel.dismissPreview()
                    onNavigateToProfile(id)
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(name = "WorldScreen · Dark", showBackground = true,
    backgroundColor = 0xFF12131A.toLong(), widthDp = 390, heightDp = 844)
@Composable
private fun PreviewWorldDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) {
        WorldScreen()
    }
}

@Preview(name = "WorldScreen · Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PreviewWorldLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) {
        WorldScreen()
    }
}

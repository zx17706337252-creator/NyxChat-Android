package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zaijian.zhoumuyun.ui.viewmodel.IdentityViewModel
import com.zaijian.zhoumuyun.ui.viewmodel.MemoryFilter
import com.zaijian.zhoumuyun.ui.viewmodel.MemoryUiItem
import com.zaijian.zhoumuyun.ui.viewmodel.MemoryViewModel
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.data.model.DefaultPresenceStates
import com.zaijian.zhoumuyun.data.model.StatusType
import com.zaijian.zhoumuyun.data.model.accentLight
import com.zaijian.zhoumuyun.ui.component.BreathingAvatar
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.AvatarSize
import com.zaijian.zhoumuyun.ui.theme.GlassOpacity
import com.zaijian.zhoumuyun.ui.theme.Palette
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.RingWidth
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  MemoryEntry — 保留本地结构供 UI 组件使用（Phase 8 仅内部传参用）
// ─────────────────────────────────────────────────────────────

private data class MemoryEntry(
    val id: String,
    val content: String,
    val dateLabel: String,
    val isImportant: Boolean = false,
    val isCore: Boolean = false,
    /** true = 关于用户（PERSONAL domain），false = 关于角色/世界 */
    val aboutSelf: Boolean = true,
)

private fun MemoryUiItem.toEntry() = MemoryEntry(
    id          = id,
    content     = content,
    dateLabel   = dateLabel,
    isImportant = isImportant,
    isCore      = isCore,
    aboutSelf   = aboutSelf,
)

private data class ToolItem(val icon: ImageVector, val label: String)
private val toolItems = listOf(
    ToolItem(Icons.Outlined.Search,      "搜索"),
    ToolItem(Icons.Outlined.Description, "文件"),
    ToolItem(Icons.Outlined.Code,        "代码"),
    ToolItem(Icons.Outlined.TableChart,  "表格"),
    ToolItem(Icons.Outlined.Email,       "邮件"),
)

private val skillTags = listOf("写作", "逻辑推理", "情绪陪伴", "信息整理", "头脑风暴")

// ─────────────────────────────────────────────────────────────
//  CharacterDetailScreen  — 角色详情页（Phase 4 Step 2）
//  设计规范 §15
//
//  两个顶级 Tab：
//    [记忆] 全部 / 重要 / 关于我 / 关于他
//    [能力] 能力 / 工具 / 任务（任务 Phase 5 完善）
//
//  进入方式：
//    书架单击书本（300ms bounceSpring，由 AppNavigation 控制）
//    公馆长按预览卡 → 「查看完整档案」
//    聊天页顶栏头像
// ─────────────────────────────────────────────────────────────

@Composable
fun CharacterDetailScreen(
    characterId: Int,
    onBack: () -> Unit = {},
    onStartChat: (Int) -> Unit = {},
    identityViewModel: IdentityViewModel = viewModel(),
    memoryViewModel: MemoryViewModel = viewModel(),
) {
    val colors    = ZaijianTheme.colors
    val type      = ZaijianTheme.typography

    val character = remember(characterId) { DefaultCharacters.find { it.id == characterId } }
        ?: return
    val presence  = remember(characterId) { DefaultPresenceStates.find { it.characterId == characterId } }

    // 初始化 Identity ViewModel
    LaunchedEffect(characterId) { identityViewModel.init(characterId) }
    val identityState by identityViewModel.uiState.collectAsState()

    // 【Phase 8】初始化 MemoryViewModel，收集真实数据
    LaunchedEffect(characterId) { memoryViewModel.init(characterId) }
    val memoryState by memoryViewModel.uiState.collectAsState()

    // 主 Tab：0 = 记忆  1 = 能力  2 = 人设
    var mainTab by remember { mutableIntStateOf(0) }

    // 记忆子 Tab：0=全部 1=重要 2=关于我 3=关于他
    var memoryTab by remember { mutableIntStateOf(0) }

    // 能力子 Tab：0=能力 1=工具 2=任务
    var abilityTab by remember { mutableIntStateOf(0) }

    val accentColor = character.accentColor
    val accentLight = character.accentLight()

    // Header 毛玻璃背景
    val headerBg = if (colors.isDark)
        colors.bgBase.copy(alpha = GlassOpacity.topBarDark)
    else
        colors.bgBase.copy(alpha = GlassOpacity.topBarLight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
        ) {
            // ── 顶部 Header 占位 ──────────────────────────────
            item { Spacer(Modifier.height(Spacing.topBarHeight)) }

            // ── 角色卡（头像 + 名 + 状态 + 「发起对话」）──────
            item {
                CharacterHeroCard(
                    name        = character.name,
                    avatarUrl   = character.avatarUrl,
                    breathColor = character.breathColor,
                    accentColor = accentColor,
                    statusText  = presence?.statusText ?: "",
                    statusType  = presence?.statusType ?: StatusType.OFFLINE,
                    activityHint = presence?.activityHint,
                    onStartChat = { onStartChat(characterId) },
                )
            }

            // ── 主 Tab（记忆 / 能力）─────────────────────────
            item {
                MainTabRow(
                    selectedIndex = mainTab,
                    accentColor   = accentColor,
                    onSelect      = { mainTab = it },
                )
            }

            // ── 记忆模块（Phase 8：接入 MemoryViewModel 真实数据）──
            if (mainTab == 0) {
                item {
                    MemorySubTabRow(
                        selectedIndex = memoryTab,
                        accentColor   = accentColor,
                        onSelect      = { idx ->
                            memoryTab = idx
                            val filter = when (idx) {
                                1 -> MemoryFilter.IMPORTANT
                                2 -> MemoryFilter.ABOUT_ME
                                3 -> MemoryFilter.ABOUT_WORLD
                                else -> MemoryFilter.ALL
                            }
                            memoryViewModel.setFilter(filter)
                        },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }

                // 加载中状态
                if (memoryState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color       = accentColor,
                                strokeWidth = 2.dp,
                                modifier    = Modifier.size(24.dp),
                            )
                        }
                    }
                } else {
                    val entries = memoryState.items.map { it.toEntry() }

                    if (entries.isEmpty()) {
                        item {
                            EmptyState(
                                text    = "还没有记忆，去聊聊吧",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xxl),
                            )
                        }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            MemoryRow(
                                entry       = entry,
                                accentColor = accentColor,
                                onToggleStar = { memoryViewModel.toggleImportant(entry.id) },
                                modifier    = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screenHorizontal),
                            )
                            Spacer(Modifier.height(Spacing.xs))
                        }
                    }

                    // 新增记忆按钮
                    item {
                        Spacer(Modifier.height(Spacing.sm))
                        AddButton(
                            label       = "新增记忆",
                            accentColor = accentColor,
                            modifier    = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenHorizontal),
                        )
                    }
                }
            }

            // ── 能力模块 ─────────────────────────────────────
            if (mainTab == 1) {
                item {
                    AbilitySubTabRow(
                        selectedIndex = abilityTab,
                        accentColor   = accentColor,
                        onSelect      = { abilityTab = it },
                    )
                    Spacer(Modifier.height(Spacing.md))
                }

                when (abilityTab) {
                    0 -> item {
                        AbilityPanel(
                            tags        = skillTags,
                            accentColor = accentColor,
                            accentLight = accentLight,
                        )
                    }
                    1 -> item {
                        ToolsPanel(
                            tools       = toolItems,
                            accentLight = accentLight,
                            accentColor = accentColor,
                        )
                    }
                    2 -> item {
                        EmptyState(
                            text     = "有点卡住，先歇一歇",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxl),
                        )
                    }
                }
            }

            // ── 人设模块 ─────────────────────────────────────
            if (mainTab == 2) {
                item {
                    IdentityPanel(
                        state       = identityState,
                        accentColor = accentColor,
                        onPersonaChange           = identityViewModel::onPersonaChange,
                        onSpeechStyleChange       = identityViewModel::onSpeechStyleChange,
                        onAttitudeToUserChange    = identityViewModel::onAttitudeToUserChange,
                        onCustomSystemPromptChange = identityViewModel::onCustomSystemPromptChange,
                        onSave      = identityViewModel::save,
                    )
                }
            }

            item { Spacer(Modifier.navigationBarsPadding()) }
        }

        // ── 固定顶栏（毛玻璃）────────────────────────────────
        DetailHeader(
            name     = character.name,
            headerBg = headerBg,
            onBack   = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  DetailHeader  — 返回 + 标题
// ─────────────────────────────────────────────────────────────

@Composable
private fun DetailHeader(
    name: String,
    headerBg: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Box(
        modifier = modifier
            .height(Spacing.topBarHeight)
            .background(headerBg)
            .border(
                width = 0.5.dp,
                color = if (colors.isDark) Color(0x15FFFFFF) else colors.border,
                shape = RoundedCornerShape(0.dp),
            )
            .statusBarsPadding(),
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint               = colors.textPrimary,
                    modifier           = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text  = name,
                style = type.navTitle,
                color = colors.textPrimary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  CharacterHeroCard  — 头像 + 状态 + 「发起对话」
//  规范 §15：头像 80dp，3dp 状态环，16dp 光晕
// ─────────────────────────────────────────────────────────────

@Composable
private fun CharacterHeroCard(
    name: String,
    avatarUrl: String,
    breathColor: Color,
    accentColor: Color,
    statusText: String,
    statusType: StatusType,
    activityHint: String?,
    onStartChat: () -> Unit,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreathingAvatar(
            imageUrl    = avatarUrl,
            breathColor = breathColor,
            statusType  = statusType,
            size        = AvatarSize.detail,
            ringWidth   = RingWidth.detail,
            glowRadius  = 16.dp,
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            text  = name,
            style = type.titleBold,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(Spacing.xs))

        if (statusText.isNotEmpty()) {
            Text(
                text  = statusText,
                style = type.caption,
                color = colors.textSecondary,
            )
        }

        if (activityHint != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text  = activityHint,
                style = type.caption,
                color = accentColor,
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        // 「发起对话」全宽按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(accentColor)
                .clickable { onStartChat() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "发起对话",
                style = type.button,
                color = Color.White,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  主 Tab 行（记忆 / 能力）
// ─────────────────────────────────────────────────────────────

@Composable
private fun MainTabRow(
    selectedIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography
    val tabs   = listOf("记忆", "能力", "人设")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(
                        if (selected) accentColor
                        else if (colors.isDark) colors.bgElevated else colors.bgCard
                    )
                    .border(
                        width  = 0.5.dp,
                        color  = if (selected) Color.Transparent else colors.border,
                        shape  = RoundedCornerShape(Radius.sm),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = label,
                    style = type.button,
                    color = if (selected) Color.White else colors.textSecondary,
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.md))
}

// ─────────────────────────────────────────────────────────────
//  记忆子 Tab（全部 / 重要 / 关于我 / 关于他）
// ─────────────────────────────────────────────────────────────

@Composable
private fun MemorySubTabRow(
    selectedIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography
    val tabs   = listOf("全部", "重要", "关于我", "关于他")

    ScrollableTabRow(
        selectedTabIndex  = selectedIndex,
        containerColor    = Color.Transparent,
        contentColor      = accentColor,
        edgePadding       = Spacing.screenHorizontal,
        indicator         = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color    = accentColor,
                    height   = 2.dp,
                )
            }
        },
        divider = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.border)
            )
        },
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected      = selectedIndex == index,
                onClick       = { onSelect(index) },
                text          = {
                    Text(
                        text  = label,
                        style = type.caption.copy(
                            fontWeight = if (selectedIndex == index) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (selectedIndex == index) accentColor else colors.textSecondary,
                    )
                },
                selectedContentColor   = accentColor,
                unselectedContentColor = colors.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  能力子 Tab（能力 / 工具 / 任务）
// ─────────────────────────────────────────────────────────────

@Composable
private fun AbilitySubTabRow(
    selectedIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography
    val tabs   = listOf("能力", "工具", "任务")

    ScrollableTabRow(
        selectedTabIndex  = selectedIndex,
        containerColor    = Color.Transparent,
        contentColor      = accentColor,
        edgePadding       = Spacing.screenHorizontal,
        indicator         = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color    = accentColor,
                    height   = 2.dp,
                )
            }
        },
        divider = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.border)
            )
        },
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected      = selectedIndex == index,
                onClick       = { onSelect(index) },
                text          = {
                    Text(
                        text  = label,
                        style = type.caption.copy(
                            fontWeight = if (selectedIndex == index) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (selectedIndex == index) accentColor else colors.textSecondary,
                    )
                },
                selectedContentColor   = accentColor,
                unselectedContentColor = colors.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MemoryRow  — 单条记忆
//  规范 §15：日期标签（右）/ 内容 / ⭐ 重要性标记
// ─────────────────────────────────────────────────────────────

@Composable
private fun MemoryRow(
    entry: MemoryEntry,
    accentColor: Color,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Row(
        modifier          = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (colors.isDark) colors.bgCard else Palette.White)
            .border(
                width = 0.5.dp,
                color = colors.border,
                shape = RoundedCornerShape(Radius.sm),
            )
            .padding(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        // 内容（占满剩余宽度）
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = entry.content,
                style = type.body,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = entry.dateLabel,
                style = type.label,
                color = colors.textDisabled,
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        // 星标
        Icon(
            imageVector        = if (entry.isImportant) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            contentDescription = if (entry.isImportant) "取消重要" else "标记重要",
            tint               = if (entry.isImportant) accentColor else colors.textDisabled,
            modifier           = Modifier
                .size(20.dp)
                .clickable { onToggleStar() },
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  AbilityPanel  — 擅长领域 Tags（规范 §15）
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityPanel(
    tags: List<String>,
    accentColor: Color,
    accentLight: Color,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Text(
            text  = "擅长领域",
            style = type.cardTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement   = Arrangement.spacedBy(Spacing.sm),
        ) {
            tags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(accentLight)
                        .border(
                            width = 0.5.dp,
                            color = accentColor.copy(alpha = 0.30f),
                            shape = RoundedCornerShape(Radius.xs),
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text(
                        text  = tag,
                        style = type.caption,
                        color = accentColor,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  ToolsPanel  — 可用工具（4 列图标，规范 §15）
// ─────────────────────────────────────────────────────────────

@Composable
private fun ToolsPanel(
    tools: List<ToolItem>,
    accentLight: Color,
    accentColor: Color,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Text(
            text  = "可用工具",
            style = type.cardTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.sm))

        // 固定 4 列布局
        val rows = tools.chunked(4)
        rows.forEach { rowTools ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                rowTools.forEach { tool ->
                    Column(
                        modifier            = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(accentLight)
                                .clickable { /* TODO Phase 5 */ },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector        = tool.icon,
                                contentDescription = tool.label,
                                tint               = accentColor,
                                modifier           = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = tool.label,
                            style = type.label,
                            color = colors.textSecondary,
                        )
                    }
                }
                // 补空列保持对齐
                repeat(4 - rowTools.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  AddButton  — 「新增记忆」全宽按钮（规范 §15）
// ─────────────────────────────────────────────────────────────

@Composable
private fun AddButton(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val type = ZaijianTheme.typography

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(accentColor)
            .clickable { /* TODO Phase 5: 新增记忆 dialog */ }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Add,
                contentDescription = label,
                tint               = Color.White,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text  = label,
                style = type.button,
                color = Color.White,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  EmptyState
// ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = text, style = type.caption, color = colors.textDisabled)
    }
}


// ─────────────────────────────────────────────────────────────
//  IdentityPanel — 「人设」Tab（Phase 7）
//
//  让用户可以自定义角色的 persona / speechStyle / attitudeToUser，
//  写入 character_identity 表，PromptOrchestrator 下次对话立刻生效。
//
//  高级选项：customSystemPrompt（展开/折叠，替换整个 Identity Layer）
// ─────────────────────────────────────────────────────────────

@Composable
private fun IdentityPanel(
    state: com.zaijian.zhoumuyun.ui.viewmodel.IdentityUiState,
    accentColor: Color,
    onPersonaChange: (String) -> Unit,
    onSpeechStyleChange: (String) -> Unit,
    onAttitudeToUserChange: (String) -> Unit,
    onCustomSystemPromptChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography
    var advancedExpanded by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color       = accentColor,
                strokeWidth = 2.dp,
                modifier    = Modifier.size(24.dp),
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ── 说明文字 ─────────────────────────────────────────
        Text(
            text  = "编辑后的人设在下次对话时立即生效。留空则使用角色默认设定。",
            style = type.caption,
            color = colors.textSecondary,
        )

        // ── 性格核心 ─────────────────────────────────────────
        IdentityField(
            label       = "性格核心",
            placeholder = "描述这个角色是什么样的人…",
            value       = state.persona,
            onValueChange = onPersonaChange,
            accentColor = accentColor,
            minLines    = 3,
        )

        // ── 说话风格 ─────────────────────────────────────────
        IdentityField(
            label       = "说话风格",
            placeholder = "语气、句式特点，例如「简洁克制，偶尔反问」…",
            value       = state.speechStyle,
            onValueChange = onSpeechStyleChange,
            accentColor = accentColor,
            minLines    = 2,
        )

        // ── 对你的态度 ───────────────────────────────────────
        IdentityField(
            label       = "对你的态度",
            placeholder = "例如「温柔但有距离感，不轻易表露情绪」…",
            value       = state.attitudeToUser,
            onValueChange = onAttitudeToUserChange,
            accentColor = accentColor,
            minLines    = 2,
        )

        // ── 高级：完全替换 System Prompt ────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advancedExpanded = !advancedExpanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "高级：自定义完整 System Prompt",
                style = type.label,
                color = accentColor,
            )
            Text(
                text  = if (advancedExpanded) "收起" else "展开",
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        if (advancedExpanded) {
            Text(
                text  = "非空时将完全替换上方字段，直接作为 AI 的 System Prompt。",
                style = type.caption,
                color = colors.textDisabled,
            )
            IdentityField(
                label       = "",
                placeholder = "你是…（直接写 System Prompt）",
                value       = state.customSystemPrompt,
                onValueChange = onCustomSystemPromptChange,
                accentColor = accentColor,
                minLines    = 5,
            )
        }

        // ── 保存按钮 ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(accentColor)
                .clickable { onSave() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = if (state.isSaved) "已保存 ✓" else "保存人设",
                style = type.button,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

// ─────────────────────────────────────────────────────────────
//  IdentityField — 多行文本输入框（人设专用）
// ─────────────────────────────────────────────────────────────

@Composable
private fun IdentityField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color,
    minLines: Int = 2,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) {
            Text(text = label, style = type.label, color = colors.textSecondary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (colors.isDark) colors.bgElevated else colors.bgCard)
                .border(
                    width = 0.5.dp,
                    color = colors.border,
                    shape = RoundedCornerShape(Radius.sm),
                )
                .padding(horizontal = Spacing.md, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = type.body, color = colors.textDisabled)
            }
            BasicTextField(
                value         = value,
                onValueChange = onValueChange,
                textStyle     = type.body.copy(color = colors.textPrimary),
                minLines      = minLines,
                modifier      = Modifier.fillMaxWidth(),
                cursorBrush   = androidx.compose.ui.graphics.SolidColor(accentColor),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(
    name            = "CharacterDetail · Dark",
    showBackground  = true,
    backgroundColor = 0xFF12131A,
    widthDp         = 390,
    heightDp        = 844,
)
@Composable
private fun PreviewDetailDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) {
        CharacterDetailScreen(characterId = 1)
    }
}

@Preview(
    name           = "CharacterDetail · Light",
    showBackground = true,
    widthDp        = 390,
    heightDp       = 844,
)
@Composable
private fun PreviewDetailLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) {
        CharacterDetailScreen(characterId = 2)
    }
}

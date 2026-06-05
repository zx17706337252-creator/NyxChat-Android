package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.AvatarSize
import com.zaijian.zhoumuyun.ui.theme.GlassOpacity
import com.zaijian.zhoumuyun.ui.theme.Palette
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  本地数据模型（Phase 5 暂用内存数据，后续接入 Repository）
// ─────────────────────────────────────────────────────────────

private enum class TaskState { ACTIVE, PAUSED, DONE, FAILED }

private data class TaskItem(
    val id: Long,
    val characterId: Int,
    val title: String,
    val progressFraction: Float,   // 0.0f ~ 1.0f
    val state: TaskState,
    val timestampLabel: String,
)

private val sampleTasks = listOf(
    TaskItem(1L, 1, "整理上周的对话记录",  0.75f, TaskState.ACTIVE,  "今天 14:22"),
    TaskItem(2L, 4, "生成项目周报草稿",    0.40f, TaskState.ACTIVE,  "今天 11:05"),
    TaskItem(3L, 3, "搜集竞品分析资料",    0.20f, TaskState.PAUSED,  "昨天 18:30"),
    TaskItem(4L, 6, "帮我写一封道歉邮件",  1.00f, TaskState.DONE,    "昨天 09:15"),
    TaskItem(5L, 2, "总结本月阅读书单",    1.00f, TaskState.DONE,    "3天前"),
    TaskItem(6L, 7, "调研海外市场报告",    0.10f, TaskState.FAILED,  "5天前"),
)

// ─────────────────────────────────────────────────────────────
//  TaskCenterScreen  — 任务中心（Phase 5 Step 1）
//  设计规范 §16
//
//  顶部 Tab：进行中 X / 已完成 X / 失败 X
//  任务卡片：角色头像 / 角色名·任务名 / 进度条 / 状态标签 + 时间戳
// ─────────────────────────────────────────────────────────────

@Composable
fun TaskCenterScreen() {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    var selectedTab by remember { mutableIntStateOf(0) }

    val activeTasks  = sampleTasks.filter { it.state == TaskState.ACTIVE || it.state == TaskState.PAUSED }
    val doneTasks    = sampleTasks.filter { it.state == TaskState.DONE }
    val failedTasks  = sampleTasks.filter { it.state == TaskState.FAILED }

    val tabLabels = listOf(
        "进行中 ${activeTasks.size}",
        "已完成 ${doneTasks.size}",
        "失败 ${failedTasks.size}",
    )

    val currentList = when (selectedTab) {
        0    -> activeTasks
        1    -> doneTasks
        else -> failedTasks
    }

    // 顶栏毛玻璃背景
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
            contentPadding = PaddingValues(
                top    = Spacing.topBarHeight + 48.dp + Spacing.sm,  // 顶栏 + Tab 高度
                bottom = Spacing.xxl,
                start  = Spacing.screenHorizontal,
                end    = Spacing.screenHorizontal,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (currentList.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = when (selectedTab) {
                                0    -> "暂时没有进行中的任务"
                                1    -> "还没有完成的任务"
                                else -> "还没有失败的任务"
                            },
                            style = type.caption,
                            color = colors.textDisabled,
                        )
                    }
                }
            } else {
                items(currentList, key = { it.id }) { task ->
                    TaskCard(task = task)
                }
            }
        }

        // ── 固定顶部区域（Header + Tab）────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.topBarHeight)
                    .background(headerBg)
                    .border(
                        width = 0.5.dp,
                        color = if (colors.isDark) Color(0x15FFFFFF) else colors.border,
                        shape = RoundedCornerShape(0.dp),
                    )
                    .statusBarsPadding(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text     = "任务中心",
                    style    = type.navTitle,
                    color    = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }

            // Tab 行
            ScrollableTabRow(
                selectedTabIndex  = selectedTab,
                containerColor    = if (colors.isDark) colors.bgCard else colors.bgBase,
                contentColor      = colors.accent,
                edgePadding       = Spacing.screenHorizontal,
                indicator         = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = colors.accent,
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
                tabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected      = selectedTab == index,
                        onClick       = { selectedTab = index },
                        text          = {
                            Text(
                                text  = label,
                                style = type.caption.copy(
                                    fontWeight = if (selectedTab == index)
                                        FontWeight.Medium else FontWeight.Normal,
                                ),
                                color = if (selectedTab == index)
                                    colors.accent else colors.textSecondary,
                            )
                        },
                        selectedContentColor   = colors.accent,
                        unselectedContentColor = colors.textSecondary,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TaskCard  — 单个任务卡片
//  规范 §16：角色头像 32dp / 角色名·任务名 / 进度条 / 状态标签 + 时间戳
// ─────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(task: TaskItem) {
    val colors    = ZaijianTheme.colors
    val type      = ZaijianTheme.typography
    val character = DefaultCharacters.find { it.id == task.characterId }

    val stateColor = when (task.state) {
        TaskState.ACTIVE  -> colors.taskActive
        TaskState.PAUSED  -> colors.taskPaused
        TaskState.DONE    -> colors.taskDone
        TaskState.FAILED  -> colors.taskFailed
    }
    val stateLabel = when (task.state) {
        TaskState.ACTIVE  -> "进行中"
        TaskState.PAUSED  -> "暂停"
        TaskState.DONE    -> "已完成"
        TaskState.FAILED  -> "有点卡住"
    }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (colors.isDark) colors.bgCard else Palette.White)
            .border(
                width = 0.5.dp,
                color = colors.border,
                shape = RoundedCornerShape(Radius.md),
            )
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 角色头像 32dp
        if (character != null) {
            AsyncImage(
                model              = character.avatarUrl,
                contentDescription = character.name,
                modifier           = Modifier
                    .size(AvatarSize.chat)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(AvatarSize.chat)
                    .clip(CircleShape)
                    .background(colors.bgElevated),
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        // 主体：角色名·任务名 / 进度条 / 标签+时间
        Column(modifier = Modifier.weight(1f)) {
            // 角色名 · 任务名
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (character != null) {
                    Text(
                        text  = character.name,
                        style = type.caption.copy(fontWeight = FontWeight.Medium),
                        color = character.accentColor,
                    )
                    Text(
                        text  = " · ",
                        style = type.caption,
                        color = colors.textDisabled,
                    )
                }
                Text(
                    text  = task.title,
                    style = type.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(6.dp))

            // 进度条 3dp 高
            LinearProgressIndicator(
                progress          = { task.progressFraction },
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color             = stateColor,
                trackColor        = stateColor.copy(alpha = 0.15f),
                strokeCap         = StrokeCap.Round,
            )

            Spacer(Modifier.height(6.dp))

            // 状态标签 + 时间戳
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // 状态标签（圆角 6dp）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(stateColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text  = stateLabel,
                        style = type.label,
                        color = stateColor,
                    )
                }

                // 进度百分比 + 时间戳
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "${(task.progressFraction * 100).toInt()}%",
                        style = type.label.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                    )
                    Text(
                        text  = "  ·  ${task.timestampLabel}",
                        style = type.label,
                        color = colors.textDisabled,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(
    name            = "TaskCenter · Dark",
    showBackground  = true,
    backgroundColor = 0xFF12131A,
    widthDp         = 390,
    heightDp        = 844,
)
@Composable
private fun PreviewTaskDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) { TaskCenterScreen() }
}

@Preview(
    name           = "TaskCenter · Light",
    showBackground = true,
    widthDp        = 390,
    heightDp       = 844,
)
@Composable
private fun PreviewTaskLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) { TaskCenterScreen() }
}

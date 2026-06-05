package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaijian.zhoumuyun.data.provider.ProviderManager
import com.zaijian.zhoumuyun.data.provider.ProviderType
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.GlassOpacity
import com.zaijian.zhoumuyun.ui.theme.Palette
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  ProfileScreen — 「我」Tab（Phase 7 AI 配置接真实逻辑）
// ─────────────────────────────────────────────────────────────

// ── ProviderType → ProviderManager 内部 id 映射 ──────────────

private fun ProviderType.toProviderId() = when (this) {
    ProviderType.DEEPSEEK    -> "deepseek"
    ProviderType.VOLCENGINE  -> "volcengine"
    ProviderType.ALIYUN      -> "aliyun"
    ProviderType.MODELSCOPE  -> "modelscope"
    ProviderType.CUSTOM      -> "custom"
}

// ── 设置项数据模型 ────────────────────────────────────────────

private data class SettingItem(
    val label: String,
    val description: String? = null,
    val trailingLabel: String? = null,
    val onClick: () -> Unit = {},
)

private data class SettingGroup(
    val title: String,
    val items: List<SettingItem>,
)

// ─────────────────────────────────────────────────────────────
//  ProfileScreen 主体
// ─────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen() {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    val headerBg = if (colors.isDark)
        colors.bgBase.copy(alpha = GlassOpacity.topBarDark)
    else
        colors.bgBase.copy(alpha = GlassOpacity.topBarLight)

    // 静态设置分组（外观 / 通知 / 关于）
    val settingGroups = remember {
        listOf(
            SettingGroup(
                title = "外观",
                items = listOf(
                    SettingItem("主题",       trailingLabel = "跟随系统"),
                    SettingItem("字体大小",    trailingLabel = "标准"),
                    SettingItem("公馆背景风格", trailingLabel = "暗夜版"),
                ),
            ),
            SettingGroup(
                title = "通知",
                items = listOf(
                    SettingItem("消息通知",    trailingLabel = "已开启"),
                    SettingItem("任务完成提醒", trailingLabel = "已开启"),
                ),
            ),
            SettingGroup(
                title = "关于",
                items = listOf(
                    SettingItem("版本",   trailingLabel = "0.1.0-dev"),
                    SettingItem("设计方案", description = "再见周慕云 · v3.0"),
                    SettingItem("隐私政策"),
                ),
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = Spacing.topBarHeight + Spacing.md,
                bottom = Spacing.xxl,
            ),
        ) {
            // ── 用户信息卡片 ──────────────────────────────────
            item {
                UserCard()
                Spacer(Modifier.height(Spacing.md))
            }

            // ── 统计概览行 ────────────────────────────────────
            item {
                StatsRow()
                Spacer(Modifier.height(Spacing.lg))
            }

            // ── AI 配置（接真实 ProviderManager）────────────
            item {
                AiConfigSection()
                Spacer(Modifier.height(Spacing.lg))
            }

            // ── 其他设置分组列表 ──────────────────────────────
            settingGroups.forEach { group ->
                item {
                    SettingGroupSection(group)
                    Spacer(Modifier.height(Spacing.md))
                }
            }
        }

        // ── 固定顶部 Header ────────────────────────────────
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
                .statusBarsPadding()
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text     = "我",
                style    = type.navTitle,
                color    = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  AiConfigSection — AI 提供商配置（接 ProviderManager）
// ─────────────────────────────────────────────────────────────

@Composable
private fun AiConfigSection() {
    val colors  = ZaijianTheme.colors
    val type    = ZaijianTheme.typography
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val pm      = remember { ProviderManager.instance }

    // ── 本地 UI State ──────────────────────────────────────

    // 当前选择的 ProviderType
    var selectedType by remember {
        val savedId = pm.getActiveProviderId()
        val initial = ProviderType.entries.find { it.toProviderId() == savedId }
            ?: ProviderType.DEEPSEEK
        mutableStateOf(initial)
    }

    // Key 输入（不显示明文）
    // 使用 TextFieldValue 以便在粘贴后将光标重置到首位，
    // 让用户能看到 key 的开头（如 sk-… / ms-…），而不是末尾。
    var apiKey by remember {
        mutableStateOf(
            TextFieldValue(
                text      = pm.getKey(selectedType.toProviderId()) ?: "",
                selection = TextRange(0),
            )
        )
    }
    var keyVisible by remember { mutableStateOf(false) }

    // 自定义 URL / Model（仅 CUSTOM 展示）
    var customUrl   by remember { mutableStateOf(pm.getCustomBaseUrl()) }
    var customModel by remember { mutableStateOf(pm.getCustomModel()) }

    // 下拉菜单
    var dropdownExpanded by remember { mutableStateOf(false) }

    // 测试状态：idle / testing / ok / fail
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }

    // 当提供商切换时，重新加载已存储的 Key；光标置于首位以显示前缀
    LaunchedEffect(selectedType) {
        val raw = pm.getKey(selectedType.toProviderId()) ?: ""
        apiKey    = TextFieldValue(text = raw, selection = TextRange(0))
        testState = TestState.Idle
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)) {
        // 分组标题
        Text(
            text     = "AI 配置",
            style    = type.label.copy(fontWeight = FontWeight.Medium),
            color    = colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(if (colors.isDark) colors.bgCard else Palette.White)
                .border(
                    width = 0.5.dp,
                    color = colors.border,
                    shape = RoundedCornerShape(Radius.md),
                )
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ── 提供商选择器 ─────────────────────────────────
            Text(
                text  = "提供商",
                style = type.label,
                color = colors.textSecondary,
            )
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(colors.bgElevated)
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = Spacing.md, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = selectedType.displayName,
                        style = type.body,
                        color = colors.textPrimary,
                    )
                    Icon(
                        imageVector        = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "展开",
                        tint               = colors.textSecondary,
                        modifier           = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded         = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier         = Modifier.background(
                        if (colors.isDark) colors.bgCard else Palette.White
                    ),
                ) {
                    ProviderType.entries.forEach { pt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text  = pt.displayName,
                                    style = type.body,
                                    color = if (pt == selectedType) colors.accent
                                            else colors.textPrimary,
                                )
                            },
                            trailingIcon = if (pt == selectedType) ({
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint     = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }) else null,
                            onClick = {
                                selectedType     = pt
                                dropdownExpanded = false
                                // 保存活跃提供商
                                pm.saveActiveProviderId(pt.toProviderId())
                            },
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.border)

            // ── API Key 输入框 ────────────────────────────────
            Text(
                text  = "API Key",
                style = type.label,
                color = colors.textSecondary,
            )
            OutlinedTextField(
                value         = apiKey,
                onValueChange = { new ->
                    // 粘贴检测：文本长度一次跳增超过 1 字符视为粘贴。
                    // 粘贴后将光标重置到首位，让用户看到 key 前缀而非末尾。
                    val isPaste = new.text.length - apiKey.text.length > 1
                    apiKey = if (isPaste) new.copy(selection = TextRange(0)) else new
                },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                placeholder   = {
                    Text(
                        text  = "sk-…",
                        style = type.body,
                        color = colors.textDisabled,
                    )
                },
                visualTransformation = if (keyVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Text(
                        text  = if (keyVisible) "隐藏" else "显示",
                        style = type.caption,
                        color = colors.accent,
                        modifier = Modifier
                            .clickable { keyVisible = !keyVisible }
                            .padding(end = 8.dp),
                    )
                },
                textStyle = type.body.copy(color = colors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor          = colors.accent,
                ),
                shape = RoundedCornerShape(Radius.sm),
            )

            // ── 自定义 Base URL / Model（CUSTOM 专属）────────
            if (selectedType == ProviderType.CUSTOM) {
                HorizontalDivider(thickness = 0.5.dp, color = colors.border)

                Text(
                    text  = "Base URL",
                    style = type.label,
                    color = colors.textSecondary,
                )
                OutlinedTextField(
                    value         = customUrl,
                    onValueChange = { customUrl = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    placeholder   = {
                        Text(
                            text  = "https://your-endpoint.com",
                            style = type.body,
                            color = colors.textDisabled,
                        )
                    },
                    textStyle = type.body.copy(color = colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = colors.accent,
                        unfocusedBorderColor = colors.border,
                        cursorColor          = colors.accent,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
                )

                Text(
                    text  = "模型名称",
                    style = type.label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                OutlinedTextField(
                    value         = customModel,
                    onValueChange = { customModel = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    placeholder   = {
                        Text(
                            text  = "gpt-4o / deepseek-chat / …",
                            style = type.body,
                            color = colors.textDisabled,
                        )
                    },
                    textStyle = type.body.copy(color = colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = colors.accent,
                        unfocusedBorderColor = colors.border,
                        cursorColor          = colors.accent,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.border)

            // ── 操作行：保存 + 测试连接 ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // 保存按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(colors.bgElevated)
                        .clickable {
                            val id = selectedType.toProviderId()
                            pm.saveActiveProviderId(id)
                            pm.saveKey(id, apiKey.text.trim())
                            if (selectedType == ProviderType.CUSTOM) {
                                pm.saveCustomBaseUrl(customUrl.trim())
                                pm.saveCustomModel(customModel.trim())
                            }
                            testState = TestState.Saved
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "保存",
                        style = type.body,
                        color = colors.textPrimary,
                    )
                }

                // 测试连接按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(colors.accent)
                        .clickable(enabled = testState !is TestState.Testing) {
                            // 先保存再测试
                            val id = selectedType.toProviderId()
                            pm.saveActiveProviderId(id)
                            pm.saveKey(id, apiKey.text.trim())
                            if (selectedType == ProviderType.CUSTOM) {
                                pm.saveCustomBaseUrl(customUrl.trim())
                                pm.saveCustomModel(customModel.trim())
                            }
                            testState = TestState.Testing
                            scope.launch {
                                val provider = pm.activeProvider
                                testState = if (provider != null && provider.testConnection()) {
                                    TestState.Ok
                                } else {
                                    TestState.Fail
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (testState) {
                        is TestState.Testing ->
                            CircularProgressIndicator(
                                modifier  = Modifier.size(18.dp),
                                color     = Color.White,
                                strokeWidth = 2.dp,
                            )
                        else ->
                            Text(
                                text  = "测试连接",
                                style = type.body,
                                color = Color.White,
                            )
                    }
                }
            }

            // ── 测试结果提示 ──────────────────────────────────
            when (val s = testState) {
                is TestState.Ok ->
                    StatusHint(text = "✓ 连接成功，可以开始对话了", color = Color(0xFF4CAF50))
                is TestState.Fail ->
                    StatusHint(text = "✗ 连接失败，请检查 Key 或网络", color = Color(0xFFF44336))
                is TestState.Saved ->
                    StatusHint(text = "已保存", color = colors.textSecondary)
                else -> Spacer(Modifier.height(0.dp))
            }
        }
    }
}

private sealed class TestState {
    object Idle    : TestState()
    object Testing : TestState()
    object Ok      : TestState()
    object Fail    : TestState()
    object Saved   : TestState()
}

@Composable
private fun StatusHint(text: String, color: Color) {
    val type = ZaijianTheme.typography
    Text(
        text     = text,
        style    = type.caption,
        color    = color,
        modifier = Modifier.padding(top = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────
//  UserCard
// ─────────────────────────────────────────────────────────────

@Composable
private fun UserCard() {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal)
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
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.accent.copy(alpha = 0.6f),
                            colors.accent.copy(alpha = 0.3f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "我",
                style = type.cardTitle.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = Palette.White,
            )
        }

        Spacer(Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = "旅人", style = type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "还没有签名，去写一句吧",
                style = type.caption,
                color = colors.textDisabled,
            )
        }

        Text(
            text     = "编辑",
            style    = type.caption,
            color    = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { /* TODO */ }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  StatsRow
// ─────────────────────────────────────────────────────────────

@Composable
private fun StatsRow() {
    val colors = ZaijianTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (colors.isDark) colors.bgCard else Palette.White)
            .border(
                width = 0.5.dp,
                color = colors.border,
                shape = RoundedCornerShape(Radius.md),
            )
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatCell(value = "128", label = "次对话")
        StatDivider()
        StatCell(value = "4",   label = "任务完成")
        StatDivider()
        StatCell(value = "23",  label = "条记忆")
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = type.titleBold, color = colors.accent)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = type.label, color = colors.textSecondary)
    }
}

@Composable
private fun StatDivider() {
    val colors = ZaijianTheme.colors
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(36.dp)
            .background(colors.border),
    )
}

// ─────────────────────────────────────────────────────────────
//  SettingGroupSection / SettingRow（通用设置项，不变）
// ─────────────────────────────────────────────────────────────

@Composable
private fun SettingGroupSection(group: SettingGroup) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Column(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)) {
        Text(
            text     = group.title,
            style    = type.label.copy(fontWeight = FontWeight.Medium),
            color    = colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(if (colors.isDark) colors.bgCard else Palette.White)
                .border(
                    width = 0.5.dp,
                    color = colors.border,
                    shape = RoundedCornerShape(Radius.md),
                ),
        ) {
            group.items.forEachIndexed { index, item ->
                SettingRow(item)
                if (index < group.items.lastIndex) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(start = Spacing.md),
                        thickness = 0.5.dp,
                        color     = colors.border,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.label, style = type.body, color = colors.textPrimary)
            if (item.description != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = item.description, style = type.label, color = colors.textSecondary)
            }
        }
        if (item.trailingLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = item.trailingLabel,
                    style = type.caption,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "›", style = type.cardTitle, color = colors.textDisabled)
            }
        } else {
            Text(text = "›", style = type.cardTitle, color = colors.textDisabled)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(
    name            = "Profile · Dark",
    showBackground  = true,
    backgroundColor = 0xFF12131A,
    widthDp         = 390,
    heightDp        = 844,
)
@Composable
private fun PreviewProfileDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) { ProfileScreen() }
}

@Preview(
    name           = "Profile · Light",
    showBackground = true,
    widthDp        = 390,
    heightDp       = 844,
)
@Composable
private fun PreviewProfileLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) { ProfileScreen() }
}

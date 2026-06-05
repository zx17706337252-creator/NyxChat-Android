package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.data.model.DefaultPresenceStates
import com.zaijian.zhoumuyun.data.model.StatusType
import com.zaijian.zhoumuyun.ui.component.BreathingAvatar
import com.zaijian.zhoumuyun.ui.theme.AnimDuration
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.AvatarSize
import com.zaijian.zhoumuyun.ui.theme.BubbleDimen
import com.zaijian.zhoumuyun.ui.theme.GlassOpacity
import com.zaijian.zhoumuyun.ui.theme.Palette
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.RingWidth
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme
import com.zaijian.zhoumuyun.ui.viewmodel.ChatViewModel
import com.zaijian.zhoumuyun.ui.viewmodel.PresenceViewModel
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  数据模型
// ─────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────
//  本地消息 ID 生成（时间戳用途）
// ─────────────────────────────────────────────────────────────
private const val TIMESTAMP_INTERVAL_MS = 30 * 60 * 1000L

private fun formatTimestamp(ms: Long): String {
    val h = (ms / 3600000 % 24).toInt()
    val m = (ms / 60000 % 60).toInt()
    val ampm = if (h < 12) "上午" else "下午"
    val h12 = when {
        h == 0 -> 12
        h <= 12 -> h
        else -> h - 12
    }
    return "$ampm $h12:${m.toString().padStart(2, '0')}"
}

// ─────────────────────────────────────────────────────────────
//  ChatScreen  — 单聊页（Phase 4 Step 1）
//  设计规范 §13
//
//  结构（从后到前）：
//    [0] 背景色（bgBase）
//    [1] 消息列表（LazyColumn，可滚动）
//    [2] 顶部情绪卡（activityHint，可折叠）
//    [3] 顶部栏（毛玻璃，56dp）
//    [4] 底部输入栏（毛玻璃，imePadding）
// ─────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    characterId: Int,
    onBack: () -> Unit = {},
    onNavigateToProfile: (Int) -> Unit = {},
    presenceViewModel: PresenceViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
) {
    val colors   = ZaijianTheme.colors
    val type     = ZaijianTheme.typography
    val scope    = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // 初始化 ChatViewModel（绑定角色 ID）
    LaunchedEffect(characterId) {
        chatViewModel.init(characterId)
    }

    // 观察 UI 状态
    val uiState by chatViewModel.uiState.collectAsState()

    // 从 ViewModel 查找角色和 Presence
    val character = remember(characterId) {
        DefaultCharacters.find { it.id == characterId }
    } ?: return

    val presence = remember(characterId) {
        DefaultPresenceStates.find { it.characterId == characterId }
    }

    var inputText by remember { mutableStateOf("") }
    var emotionCardVisible by remember { mutableStateOf(presence?.activityHint != null) }

    // 消息列表（来自 DB + 流式 streaming 追加）
    val messages = uiState.messages
    val isTyping = uiState.isTyping
    val streamingContent = uiState.streamingContent

    // 新消息时自动滚动到底部
    LaunchedEffect(messages.size, isTyping) {
        val totalItems = messages.size + (if (isTyping) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // 错误提示 Snackbar
    LaunchedEffect(uiState.error) {
        val err = uiState.error
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            chatViewModel.clearError()
        }
    }

    // API Key 未配置提示
    if (uiState.isApiKeyMissing) {
        LaunchedEffect(Unit) {
            chatViewModel.clearApiKeyMissingFlag()
            onNavigateToProfile(characterId)
        }
    }

    // 顶部 Header 背景色
    val headerBg = if (colors.isDark)
        colors.bgBase.copy(alpha = GlassOpacity.topBarDark)
    else
        colors.bgBase.copy(alpha = GlassOpacity.topBarLight)

    // 底部输入栏背景色
    val inputBarBg = if (colors.isDark)
        colors.bgCard.copy(alpha = 0.92f)
    else
        colors.bgBase.copy(alpha = 0.95f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        // ── [1] 消息列表 ──────────────────────────────────────
        LazyColumn(
            state            = listState,
            modifier         = Modifier.fillMaxSize(),
            contentPadding   = PaddingValues(
                top    = Spacing.topBarHeight +
                         (if (emotionCardVisible && presence?.activityHint != null) 40.dp else 0.dp) +
                         Spacing.md,
                bottom = 80.dp + Spacing.xl,   // 底栏高度 + 安全区
                start  = Spacing.screenHorizontal,
                end    = Spacing.screenHorizontal,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                // 时间戳：相邻消息超过 30 分钟才显示
                val prevMsg = if (index > 0) messages[index - 1] else null
                val showTimestamp = prevMsg == null ||
                        (msg.createdAt - prevMsg.createdAt) >= TIMESTAMP_INTERVAL_MS

                if (showTimestamp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = formatTimestamp(msg.createdAt),
                            style = type.label,
                            color = colors.textDisabled,
                        )
                    }
                }

                MessageBubble(
                    message      = msg,
                    accentColor  = character.accentColor,
                    avatarUrl    = character.avatarUrl,
                    characterName = character.name,
                )
            }
            // 流式打字机效果（AI 正在回复）
            if (isTyping) {
                item(key = "streaming") {
                    val displayContent = if (streamingContent.isNotEmpty())
                        streamingContent else "…"
                    MessageBubble(
                        message = com.zaijian.zhoumuyun.ui.viewmodel.ChatMessage(
                            id        = "streaming",
                            role      = "assistant",
                            content   = displayContent,
                            createdAt = System.currentTimeMillis(),
                        ),
                        accentColor   = character.accentColor,
                        avatarUrl     = character.avatarUrl,
                        characterName = character.name,
                    )
                }
            }
        }

        // ── [3] 顶部情绪卡（可折叠，40dp）──────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            // Header 占位层
            Box(modifier = Modifier.height(Spacing.topBarHeight))

            // 情绪卡
            AnimatedVisibility(
                visible = emotionCardVisible && presence?.activityHint != null,
                enter   = fadeIn(tween(AnimDuration.fast)) +
                          slideInVertically(tween(AnimDuration.fast)) { -it },
            ) {
                if (presence?.activityHint != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(character.accentColor.copy(alpha = 0.12f))
                            .clickable { emotionCardVisible = false },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text     = presence.activityHint,
                            style    = type.caption,
                            color    = character.accentColor,
                            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                        )
                    }
                }
            }
        }

        // ── [2] 顶部栏（毛玻璃，56dp）────────────────────────
        ChatHeader(
            name         = character.name,
            avatarUrl    = character.avatarUrl,
            breathColor  = character.breathColor,
            accentColor  = character.accentColor,
            statusText   = presence?.statusText ?: "",
            statusType   = presence?.statusType ?: StatusType.OFFLINE,
            headerBg     = headerBg,
            onBack       = onBack,
            onAvatarClick = { onNavigateToProfile(characterId) },
            modifier     = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        // ── [4] 底部输入栏 ────────────────────────────────────
        ChatInputBar(
            value       = inputText,
            onValueChange = { inputText = it },
            accentColor = character.accentColor,
            bgColor     = inputBarBg,
            onSend      = {
                val text = inputText.trim()
                if (text.isNotEmpty()) {
                    chatViewModel.sendMessage(text)
                    inputText = ""
                    scope.launch {
                        val total = messages.size + 1
                        listState.animateScrollToItem(total)
                    }
                }
            },
            modifier    = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        )

        // ── [5] 错误 Snackbar ─────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 88.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = ZaijianTheme.colors.bgCard,
                    contentColor   = ZaijianTheme.colors.textPrimary,
                    shape          = RoundedCornerShape(12.dp),
                )
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  ChatHeader — 毛玻璃顶栏
//  规范 §13：返回箭头 / 头像+角色名+状态文案 / 更多图标
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    name: String,
    avatarUrl: String,
    breathColor: Color,
    accentColor: Color,
    statusText: String,
    statusType: StatusType,
    headerBg: Color,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Box(
        modifier = modifier
            .height(Spacing.topBarHeight)
            .background(headerBg)
            .border(
                width  = 0.5.dp,
                color  = if (colors.isDark) Color(0x15FFFFFF) else colors.border,
                shape  = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
            )
            .statusBarsPadding(),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回箭头
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint               = colors.textPrimary,
                    modifier           = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(Spacing.xs))

            // 头像（点击进入详情页）
            Box(
                modifier = Modifier
                    .size(AvatarSize.chat)
                    .clickable { onAvatarClick() },
            ) {
                BreathingAvatar(
                    imageUrl    = avatarUrl,
                    breathColor = breathColor,
                    statusType  = statusType,
                    modifier    = Modifier.fillMaxSize(),
                    size        = AvatarSize.chat,
                    ringWidth   = RingWidth.chat,
                    glowRadius  = 4.dp,
                    enableBreath = false,   // 顶栏不呼吸，减少干扰
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            // 角色名 + 状态文案
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = name,
                    style = type.navTitle,
                    color = colors.textPrimary,
                )
                if (statusText.isNotEmpty()) {
                    Text(
                        text  = statusText,
                        style = type.label,
                        color = colors.textSecondary,
                    )
                }
            }

            // 更多图标
            IconButton(onClick = { /* TODO Phase 5: 聊天设置 */ }) {
                Icon(
                    imageVector        = Icons.Outlined.MoreVert,
                    contentDescription = "更多",
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MessageBubble — 单条消息
//  规范 §13：
//    角色气泡 圆角 20/20/20/4dp，左侧 32dp 头像
//    用户气泡 圆角 20/4/20/20dp，右对齐，accentColor 填充
//    最大宽度 屏幕宽 × 0.72
// ─────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: com.zaijian.zhoumuyun.ui.viewmodel.ChatMessage,
    accentColor: Color,
    avatarUrl: String,
    characterName: String,
) {
    val colors         = ZaijianTheme.colors
    val type           = ZaijianTheme.typography
    val screenWidth    = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * BubbleDimen.maxWidthFraction

    if (message.role == "user") {
        // ── 用户气泡（右对齐）──────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(
                        RoundedCornerShape(
                            topStart    = Radius.md,
                            topEnd      = Radius.md,
                            bottomStart = Radius.md,
                            bottomEnd   = Radius.xs,
                        )
                    )
                    .background(accentColor)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    text  = message.content,
                    style = type.body,
                    color = Color.White,
                )
            }
        }
    } else {
        // ── 角色气泡（左对齐，带头像）───────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            // 头像占位（32dp）
            AsyncImage(
                model              = avatarUrl,
                contentDescription = characterName,
                modifier           = Modifier
                    .size(AvatarSize.chat)
                    .clip(CircleShape),
            )

            Spacer(Modifier.width(Spacing.sm))

            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(
                        RoundedCornerShape(
                            topStart    = Radius.md,
                            topEnd      = Radius.md,
                            bottomStart = Radius.xs,
                            bottomEnd   = Radius.md,
                        )
                    )
                    .background(if (colors.isDark) colors.bgCard else Palette.White)
                    .border(
                        width  = 0.5.dp,
                        color  = colors.border,
                        shape  = RoundedCornerShape(
                            topStart    = Radius.md,
                            topEnd      = Radius.md,
                            bottomStart = Radius.xs,
                            bottomEnd   = Radius.md,
                        ),
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    text  = message.content,
                    style = type.body,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  ChatInputBar — 底部输入栏
//  规范 §13：输入框圆角 28dp，发送按钮 accentColor 圆形 32dp
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color,
    bgColor: Color,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors       = ZaijianTheme.colors
    val type         = ZaijianTheme.typography
    val canSend      = value.trim().isNotEmpty()

    Row(
        modifier          = modifier
            .background(bgColor)
            .border(
                width  = 0.5.dp,
                color  = if (colors.isDark) Color(0x15FFFFFF) else colors.border,
                shape  = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            )
            .padding(
                horizontal = Spacing.screenHorizontal,
                vertical   = Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 输入框
        BasicTextField(
            value         = value,
            onValueChange = onValueChange,
            textStyle     = type.body.copy(color = colors.textPrimary),
            cursorBrush   = SolidColor(accentColor),
            modifier      = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (colors.isDark)
                        colors.bgElevated
                    else
                        colors.bgCard,
                )
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text  = "说点什么…",
                        style = type.body,
                        color = colors.textDisabled,
                    )
                }
                innerTextField()
            },
        )

        Spacer(Modifier.width(Spacing.sm))

        // 发送按钮（32dp 圆形）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) accentColor
                    else colors.textDisabled.copy(alpha = 0.3f)
                )
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Send,
                contentDescription = "发送",
                tint               = Color.White,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(
    name            = "ChatScreen · Dark",
    showBackground  = true,
    backgroundColor = 0xFF12131A,
    widthDp         = 390,
    heightDp        = 844,
)
@Composable
private fun PreviewChatDark() {
    ZaijianTheme(appTheme = AppTheme.DARK) {
        ChatScreen(characterId = 1)
    }
}

@Preview(
    name           = "ChatScreen · Light",
    showBackground = true,
    widthDp        = 390,
    heightDp       = 844,
)
@Composable
private fun PreviewChatLight() {
    ZaijianTheme(appTheme = AppTheme.LIGHT) {
        ChatScreen(characterId = 6)
    }
}

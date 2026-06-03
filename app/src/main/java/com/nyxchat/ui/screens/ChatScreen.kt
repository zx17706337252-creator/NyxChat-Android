package com.nyxchat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.nyxchat.ui.components.AmbientParticles
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nyxchat.data.*
import com.nyxchat.ui.components.*
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val chars        by vm.characters.collectAsState()
    val messages     by vm.messages.collectAsState()
    val typingCharId by vm.typingCharId.collectAsState()
    val groupMode    by vm.groupMode.collectAsState()
    val selectedId   by vm.selectedCharId.collectAsState()
    val apiCfg       by vm.apiConfig.collectAsState()
    val error        by vm.error.collectAsState()
    val isStreaming  by vm.isStreaming.collectAsState()
    val sessions     by vm.sessions.collectAsState()
    val activeSession by vm.activeSession.collectAsState()
    val context      = LocalContext.current
    val keyboard     = LocalSoftwareKeyboardController.current
    // Bug 1 fix: 必须在 @Composable 作用域内读取 CompositionLocal，不能在 lambda 中
    val clipboardManager = LocalClipboardManager.current

    val activeChars   = chars.filter { it.isActive }
    val currentChar   = if (!groupMode) chars.find { it.id == selectedId } else null
    val listState     = rememberLazyListState()
    var input         by remember { mutableStateOf("") }
    val narrativeMode by vm.narrativeMode.collectAsState()
    val ttsCfg        by vm.ttsConfig.collectAsState()
    val isTtsPlaying  by vm.isTtsPlaying.collectAsState()
    val enableParticles by vm.enableParticles.collectAsState()
    val chatBackground  by vm.chatBackground.collectAsState()
    val isDark          by vm.isDarkMode.collectAsState()
    // 会话操作对话框状态
    var sessionMenuTarget by remember { mutableStateOf<com.nyxchat.data.ChatSession?>(null) }
    var showRenameDialog  by remember { mutableStateOf(false) }
    var renameText        by remember { mutableStateOf("") }
    var searchText        by remember { mutableStateOf("") }
    var searchCharFilter  by remember { mutableStateOf<String?>(null) }
    var showSearchResults by remember { mutableStateOf(false) }
    var immersiveMode     by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val sceneState    by vm.sceneState.collectAsState()
    val fragmentIds   by vm.fragmentIds.collectAsState()
    var sceneExpanded by remember { mutableStateOf(false) }
    var sceneDraft    by remember(sceneState) { mutableStateOf(sceneState) }

    // ── 步骤7b：Prompt 预览底部弹层 ───────────────────────────────────────
    val debugStages   by vm.debugStages.collectAsState()
    var showPromptDebug by remember { mutableStateOf(false) }

    // ── 步骤9b：世界书触发指示 ─────────────────────────────────────────────
    val lastTriggeredWbIds by vm.lastTriggeredWbIds.collectAsState()
    val worldBook          by vm.worldBook.collectAsState()
    var showWbInfo         by remember { mutableStateOf(false) }

    // Animate background color when active character changes — 600ms smooth transition
    val animatedCharColor by animateColorAsState(
        targetValue   = currentChar?.color ?: LocalNyxColors.current.Accent,
        animationSpec = tween(600),
        label         = "charColorSwitch"
    )

    // Greet on char switch
    LaunchedEffect(selectedId) {
        selectedId?.let { vm.maybeGreet(it) }
    }

    // Auto-scroll
    LaunchedEffect(messages.size, typingCharId) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Bug 6 fix: 不再直接访问数据库，通过 ViewModel 搜索（MVVM 分层）
    val searchResults by vm.searchResults.collectAsState()
    LaunchedEffect(searchText, searchCharFilter) {
        if (searchText.isBlank()) {
            vm.clearSearch()
            showSearchResults = false
        } else {
            vm.searchMessages(searchText.trim(), searchCharFilter)
            showSearchResults = true
        }
    }
    // 当 ViewModel 返回结果后同步显示状态
    LaunchedEffect(searchResults) {
        if (searchResults.isEmpty() && searchText.isNotBlank()) showSearchResults = false
        else if (searchResults.isNotEmpty()) showSearchResults = true
    }

    // ── imePadding KEY FIX ───────────────────────────────────────────────
    Box(
        Modifier.fillMaxSize().imePadding()
    ) {
        // ── Full-screen chat background (behind top bar + input) ─────────
        if (chatBackground.isNotBlank()) {
            AsyncImage(
                model = File(chatBackground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isDark) 0.45f else 0.52f
            )
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isDark)
                                    LocalNyxColors.current.Background.copy(alpha = 0.48f)
                                else
                                    Color(0xFFEEE8F6).copy(alpha = 0.28f),
                                Color.Transparent,
                                if (isDark)
                                    LocalNyxColors.current.Background.copy(alpha = 0.32f)
                                else
                                    Color(0xFFEEE8F6).copy(alpha = 0.18f)
                            )
                        )
                    )
            )
        } else if (currentChar?.hasBackground == true) {
            AsyncImage(
                model = File(currentChar.backgroundPath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isDark) 0.38f else 0.45f
            )
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                animatedCharColor.copy(alpha = if (isDark) 0.08f else 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    Column(
        Modifier.fillMaxSize()
    ) {
    // ── 会话重命名对话框 ─────────────────────────────────────────────────
    if (showRenameDialog && sessionMenuTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名对话", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = LocalNyxColors.current.AccentSoft) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocalNyxColors.current.Accent.copy(0.6f),
                        unfocusedBorderColor = LocalNyxColors.current.BorderSubtle,
                        focusedTextColor = LocalNyxColors.current.TextPrimary,
                        unfocusedTextColor = LocalNyxColors.current.TextSecond,
                        cursorColor = LocalNyxColors.current.Accent,
                        focusedContainerColor = LocalNyxColors.current.Layer3,
                        unfocusedContainerColor = LocalNyxColors.current.Layer3
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sessionMenuTarget?.let { vm.renameSession(it.id, renameText.trim().ifBlank { it.title }) }
                    showRenameDialog = false
                }) { Text("确定", color = LocalNyxColors.current.AccentSoft, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消", color = LocalNyxColors.current.TextDim, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

        // ── Top bar ─────────────────────────────────────────────────────────
        var searchExpanded by remember { mutableStateOf(false) }
        val nyx = LocalNyxColors.current
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .drawBehind {
                    // Bottom edge glow line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, nyx.BorderMid.copy(0.8f), Color.Transparent)
                        ),
                        start = Offset(0f, size.height),
                        end   = Offset(size.width, size.height),
                        strokeWidth = 0.75.dp.toPx()
                    )
                    // Top edge highlight (simulates glass top rim)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, nyx.EdgeHighlight.copy(0.6f), Color.Transparent)
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(size.width, 0f),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }
                .background(
                    if ((chatBackground.isNotBlank() || currentChar?.hasBackground == true))
                        Brush.verticalGradient(
                            listOf(
                                nyx.Layer2.copy(alpha = 0.85f),
                                nyx.Layer1.copy(alpha = 0.78f)
                            )
                        )
                    else
                        Brush.verticalGradient(
                            listOf(nyx.Layer2.copy(alpha = 0.98f), nyx.Layer1)
                        )
                )
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：角色名 + 沉浸模式切换
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clickable { if (currentChar != null) immersiveMode = !immersiveMode }
                    .weight(1f, fill = false)
            ) {
                if (immersiveMode) {
                    Text("◁", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = nyx.TextDim)
                    Text("沉浸", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        color = nyx.TextDim.copy(alpha = 0.6f))
                }
                Text(
                    if (currentChar != null) currentChar.name else "永恒之家",
                    fontSize = 14.sp, fontFamily = CinzelFamily,
                    color = if (currentChar != null && !immersiveMode) currentChar.color
                            else nyx.AccentSoft,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                // 非沉浸时，角色名后显示极淡的可点击提示
                if (currentChar != null && !immersiveMode) {
                    Text("⊙", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        color = nyx.TextDim.copy(alpha = 0.35f))
                }
                // 群聊状态标记 — pill style with gradient
                if (groupMode) {
                    Text("群", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = nyx.AccentSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(nyx.AccentDeep.copy(0.30f), nyx.AccentPill)
                                )
                            )
                            .border(0.5.dp, nyx.AccentBright.copy(0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }

            // 右侧：搜索展开态 or 图标按钮组
            if (searchExpanded) {
                // 搜索展开：TextField + 关闭按钮
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("搜索…", color = LocalNyxColors.current.TextDim,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                        leadingIcon = { Icon(Icons.Default.Search, "搜索",
                            tint = LocalNyxColors.current.TextDim, modifier = Modifier.size(15.dp)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = LocalNyxColors.current.Layer3,
                            unfocusedContainerColor = LocalNyxColors.current.Layer3,
                            focusedIndicatorColor = LocalNyxColors.current.Accent.copy(0.5f),
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = LocalNyxColors.current.TextPrimary,
                            unfocusedTextColor = LocalNyxColors.current.TextSecond,
                            cursorColor = LocalNyxColors.current.Accent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace)
                    )
                    // 关闭搜索
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalNyxColors.current.Layer2)
                            .clickable { searchExpanded = false; searchText = ""; vm.clearSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                    }
                }
            } else {
                // 图标按钮组（收起搜索状态）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 搜索
                    TopBarIconBtn("搜索") { searchExpanded = true }
                    // 群/单聊切换
                    TopBarIconBtn(if (groupMode) "群聊" else "单聊", tinted = groupMode) { vm.toggleGroupMode() }
                    // 重新生成
                    TopBarIconBtn("重发") { if (!isStreaming) vm.regenerateLast() }
                    // 导出
                    TopBarIconBtn("导出") {
                        val text = vm.exportAsNovel()
                        if (text.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // 清空
                    TopBarIconBtn("清空") { vm.clearChat() }
                }
            }
        } // end top bar Row

        // ── Search results panel (hidden in immersive mode) ──────────
        if (showSearchResults && !immersiveMode) {
            Column(
                Modifier.fillMaxWidth().background(LocalNyxColors.current.Layer2).padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("找到 ${searchResults.size} 条结果", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.AccentSoft)
                    Text("× 关闭", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim,
                        modifier = Modifier.clickable { searchText = ""; vm.clearSearch(); showSearchResults = false }.padding(4.dp))
                }
                // Character filter chips
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val uniqueChars = searchResults.mapNotNull { r -> r.charId }.distinct().mapNotNull { cid -> chars.find { it.id == cid } }
                    uniqueChars.forEach { char ->
                        val isFiltered = searchCharFilter == char.id
                        Box(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (isFiltered) char.color.copy(0.2f) else LocalNyxColors.current.Layer3)
                            .border(0.5.dp, if (isFiltered) char.color.copy(0.5f) else LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable {
                                searchCharFilter = if (isFiltered) null else char.id
                            }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(char.name, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = if (isFiltered) char.color else LocalNyxColors.current.TextDim)
                        }
                    }
                    if (searchCharFilter != null) {
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(LocalNyxColors.current.Layer3)
                            .clickable { searchCharFilter = null }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("× 清除过滤", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(searchResults.take(20)) { result ->
                        val char = result.charId?.let { chars.find { c -> c.id == it } }
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(LocalNyxColors.current.Layer3)
                            .clickable {
                                val idx = messages.indexOfFirst { it.id == result.id }
                                if (idx >= 0) coroutineScope.launch {
                                    listState.animateScrollToItem(idx)
                                }
                            }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Column {
                                Text(char?.name ?: "用户", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                    color = char?.color ?: LocalNyxColors.current.TextDim)
                                Text(result.content.take(40).replace("\n", " ") + if (result.content.length > 40) "…" else "",
                                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.TextSecond, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // ── Session bar (hidden in immersive mode) ────────────────────
        if (!immersiveMode) {
        Row(
            Modifier.fillMaxWidth()
                .background(LocalNyxColors.current.Background)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sessions.sortedByDescending { it.lastMessageAt }.forEach { session ->
                val isActive = session.id == activeSession
                var showSessMenu by remember(session.id) { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (isActive) LocalNyxColors.current.Accent.copy(0.18f) else LocalNyxColors.current.Layer1)
                            .border(0.5.dp,
                                if (isActive) LocalNyxColors.current.Accent.copy(0.5f) else LocalNyxColors.current.BorderSubtle,
                                RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick     = { vm.switchSession(session.id) },
                                onLongClick = { showSessMenu = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(session.title, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = if (isActive) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim,
                            letterSpacing = 0.3.sp)
                    }
                    DropdownMenu(
                        expanded = showSessMenu,
                        onDismissRequest = { showSessMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                            onClick = {
                                sessionMenuTarget = session
                                renameText = session.title
                                showRenameDialog = true
                                showSessMenu = false
                            }
                        )
                        // Bug fix ③: collectAsState 提升到 DropdownMenu 的 @Composable 作用域内，
                        // enabled 参数与 text lambda 共用同一个响应式状态，
                        // 避免 enabled = !vm.isCompressing.value 这种非响应式裸读
                        // 导致压缩进行中按钮无法自动置灰的问题。
                        val compressingState by vm.isCompressing.collectAsState()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (compressingState) "压缩中…" else "生成本章小结",
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace
                                )
                            },
                            onClick = {
                                vm.compressCurrentSession()
                                showSessMenu = false
                                Toast.makeText(context, "正在生成章节小结…", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !compressingState
                        )
                        if (session.id != "default") {
                            DropdownMenuItem(
                                text = { Text("删除", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.Error) },
                                onClick = { vm.deleteSession(session.id); showSessMenu = false }
                            )
                        }
                    }
                }
            }
            // New session button
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(LocalNyxColors.current.Layer1)
                    .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(16.dp))
                    .clickable { vm.createSession() }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("＋ 新对话", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
            }
        }
        }

        // ── Character color strip ─────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, animatedCharColor.copy(0.3f), Color.Transparent)
                    )
                )
        )

        // ── Error banner ─────────────────────────────────────────────────
        if (error != null) {
            Row(
                Modifier.fillMaxWidth()
                    .background(LocalNyxColors.current.Error.copy(0.1f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(error ?: "", color = LocalNyxColors.current.Error, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("×", color = LocalNyxColors.current.Error, fontSize = 16.sp,
                    modifier = Modifier.clickable { vm.clearError() }.padding(4.dp))
            }
        }

        // ── Messages ─────────────────────────────────────────────────────
        Box(Modifier.weight(1f)) {
            // Ambient particles — subtle drifting light points behind messages
            if (enableParticles) AmbientParticles(
                modifier  = Modifier.fillMaxSize(),
                tintColor = animatedCharColor,
                count     = 16
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("◈", fontSize = 32.sp, color = LocalNyxColors.current.Accent.copy(0.12f))
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    if (activeChars.isNotEmpty()) "开口，让夜晚开始" else "在角色页添加并启用角色",
                                    fontSize = 13.sp, fontFamily = CrimsonProFamily,
                                    color = LocalNyxColors.current.TextDim, fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isLastMsg  = msg == messages.lastOrNull()
                    val isFragment = msg.id in fragmentIds
                    var showMsgMenu by remember { mutableStateOf(false) }
                    Box {
                        Box(Modifier.combinedClickable(
                            onClick     = {},
                            onLongClick = { showMsgMenu = true }
                        )) {
                            Column {
                            MessageBubble(msg = msg, chars = chars,
                                showCursor = isStreaming && isLastMsg && msg.role == "assistant")
                            // Search highlight badge
                            if (searchText.isNotBlank() && msg.content.contains(searchText, ignoreCase = true)) {
                                val matchCount = msg.content.split(searchText, ignoreCase = true).size - 1
                                Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 2.dp)) {
                                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(LocalNyxColors.current.Accent.copy(0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("⚡ ${matchCount}处匹配",
                                            fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                            color = LocalNyxColors.current.AccentSoft)
                                    }
                                }
                            }
                            // TTS 控制（播放 + 停止）
                            if (msg.role == "assistant" && msg.charId != null && ttsCfg.enabled) {
                                val char = chars.find { it.id == msg.charId }
                                if (char != null) {
                                    Row(
                                        Modifier.padding(start = 50.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(12.dp))
                                                .background(char.color.copy(0.08f))
                                                .border(0.5.dp, char.color.copy(0.2f), RoundedCornerShape(12.dp))
                                                .clickable { vm.playTts(msg.content, msg.charId) }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("▶ 播放", fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace, color = char.color.copy(0.7f))
                                        }
                                        if (isTtsPlaying) {
                                            Box(
                                                Modifier.clip(RoundedCornerShape(12.dp))
                                                    .background(LocalNyxColors.current.Error.copy(0.08f))
                                                    .border(0.5.dp, LocalNyxColors.current.Error.copy(0.25f), RoundedCornerShape(12.dp))
                                                    .clickable { vm.stopTts() }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("◼ 停止", fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.Error.copy(0.7f))
                                            }
                                        }
                                    }
                                }
                            }
                            } // end Column
                            // ── 步骤9b：世界书触发指示（最后一条 assistant 消息下方）────────
                            if (isLastMsg && msg.role == "assistant" && lastTriggeredWbIds.isNotEmpty()) {
                                Row(
                                    Modifier.padding(start = 50.dp, top = 1.dp, bottom = 3.dp)
                                ) {
                                    Box(
                                        Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(LocalNyxColors.current.Accent.copy(0.07f))
                                            .border(0.5.dp, LocalNyxColors.current.Accent.copy(0.18f), RoundedCornerShape(8.dp))
                                            .clickable { showWbInfo = true }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "◇ 世界书 ×${lastTriggeredWbIds.size}",
                                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                            color = LocalNyxColors.current.AccentSoft.copy(0.65f)
                                        )
                                    }
                                }
                            }
                            // Fragment bookmark indicator
                            if (isFragment) {
                                Text("◆", fontSize = 9.sp, color = LocalNyxColors.current.AccentSoft.copy(0.8f),
                                    modifier = Modifier
                                        .align(if (msg.role == "user") Alignment.TopStart else Alignment.TopEnd)
                                        .padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        } // end inner Box

                        // 消息长按菜单
                        DropdownMenu(
                            expanded = showMsgMenu,
                            onDismissRequest = { showMsgMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制文本", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = if (msg.content.startsWith("[旁白：") && msg.content.endsWith("]"))
                                        msg.content.removePrefix("[旁白：").removeSuffix("]") else msg.content
                                    cm.setPrimaryClip(ClipData.newPlainText("永恒之家", text))
                                    showMsgMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(
                                    if (isFragment) "取消碎片标记" else "标记为碎片",
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                },
                                onClick = { vm.toggleFragment(msg.id); showMsgMenu = false }
                            )
                            if (isLastMsg && msg.role == "assistant") {
                                DropdownMenuItem(
                                    text = { Text("重新生成", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                        color = LocalNyxColors.current.AccentSoft) },
                                    onClick = { if (!isStreaming) vm.regenerateLast(); showMsgMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("删除", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.Error) },
                                onClick = { vm.deleteMessage(msg.id); showMsgMenu = false }
                            )
                        }
                    } // end outer Box
                }

                typingCharId?.let { cid ->
                    chars.find { it.id == cid }?.let { item { TypingIndicator(it) } }
                }
            }

            // Fade at top of message list
            Box(
                Modifier.fillMaxWidth().height(24.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(
                        if ((chatBackground.isNotBlank() || currentChar?.hasBackground == true)) Color.Transparent else LocalNyxColors.current.Background,
                        Color.Transparent
                    )))
            )
        }

        // ── Scene state bar (hidden in immersive mode) ────────────────
        if (!immersiveMode) {
        val fragCount = fragmentIds.size
        Column(Modifier.background(
            if ((chatBackground.isNotBlank() || currentChar?.hasBackground == true))
                LocalNyxColors.current.Layer2.copy(alpha = 0.78f)
            else
                LocalNyxColors.current.Layer2
        )) {
            // Top micro-divider
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, LocalNyxColors.current.BorderSubtle, Color.Transparent))
            ))
            Row(
                Modifier.fillMaxWidth()
                    .clickable { sceneExpanded = !sceneExpanded }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scene indicator
                Text(if (sceneDraft.enabled) "◎" else "○", fontSize = 10.sp,
                    color = if (sceneDraft.enabled) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                Text(
                    if (sceneDraft.enabled && (sceneDraft.location.isNotBlank() || sceneDraft.atmosphere.isNotBlank()))
                        listOf(sceneDraft.location, sceneDraft.atmosphere).filter { it.isNotBlank() }.joinToString(" · ")
                    else "场景",
                    fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                    color = if (sceneDraft.enabled) LocalNyxColors.current.TextSecond else LocalNyxColors.current.TextDim,
                    modifier = Modifier.weight(1f)
                )
                // Fragment count chip — 显示已标记的碎片数量
                if (fragCount > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalNyxColors.current.AccentPill)
                            .border(0.5.dp, LocalNyxColors.current.BorderHi, RoundedCornerShape(10.dp))
                            .clickable { vm.toggleNarrativeMode() /* no-op, just visual */ }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "✦ $fragCount 片段",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.AccentSoft
                        )
                    }
                }
                Text(if (sceneExpanded) "∧" else "∨", fontSize = 9.sp, color = LocalNyxColors.current.TextDim)
            }
            // Expanded editor
            if (sceneExpanded) {
                Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("启用场景注入", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                        Switch(checked = sceneDraft.enabled,
                            onCheckedChange = { sceneDraft = sceneDraft.copy(enabled = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor  = LocalNyxColors.current.Accent,
                                checkedTrackColor  = LocalNyxColors.current.Accent.copy(0.3f),
                                uncheckedTrackColor = LocalNyxColors.current.BorderSubtle))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("地点", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 3.dp))
                            TextField(value = sceneDraft.location,
                                onValueChange = { sceneDraft = sceneDraft.copy(location = it) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                placeholder = { Text("图书馆", fontSize = 12.sp, color = LocalNyxColors.current.TextDim) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = LocalNyxColors.current.Layer3, unfocusedContainerColor = LocalNyxColors.current.Layer3,
                                    focusedIndicatorColor = LocalNyxColors.current.Accent.copy(0.5f), unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = LocalNyxColors.current.TextPrimary, unfocusedTextColor = LocalNyxColors.current.TextSecond,
                                    cursorColor = LocalNyxColors.current.Accent),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), singleLine = true)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("氛围", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 3.dp))
                            TextField(value = sceneDraft.atmosphere,
                                onValueChange = { sceneDraft = sceneDraft.copy(atmosphere = it) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                placeholder = { Text("深夜·安静", fontSize = 12.sp, color = LocalNyxColors.current.TextDim) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = LocalNyxColors.current.Layer3, unfocusedContainerColor = LocalNyxColors.current.Layer3,
                                    focusedIndicatorColor = LocalNyxColors.current.Accent.copy(0.5f), unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = LocalNyxColors.current.TextPrimary, unfocusedTextColor = LocalNyxColors.current.TextSecond,
                                    cursorColor = LocalNyxColors.current.Accent),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), singleLine = true)
                        }
                    }
                    // Quick presets
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            "图书馆" to "安静", "雨天" to "阳台", "深夜" to "卧室",
                            "咖啡馆" to "午后", "公园" to "黄昏", "车站" to "分别前"
                        )
                        items(presets) { (loc, atm) ->
                            val sel = sceneDraft.location == loc && sceneDraft.atmosphere == atm
                            Box(Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) LocalNyxColors.current.AccentPill else LocalNyxColors.current.Layer3)
                                .border(0.5.dp, if (sel) LocalNyxColors.current.BorderHi else LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { sceneDraft = sceneDraft.copy(location = loc, atmosphere = atm, enabled = true) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text("$loc · $atm", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                                    color = if (sel) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            sceneDraft = sceneDraft.copy(location = "", atmosphere = "", enabled = false)
                            vm.saveSceneState(sceneDraft); sceneExpanded = false
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalNyxColors.current.TextDim),
                            border = BorderStroke(0.5.dp, LocalNyxColors.current.BorderSubtle)) {
                            Text("清除", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Button(onClick = { vm.saveSceneState(sceneDraft); sceneExpanded = false },
                            modifier = Modifier.weight(2f), shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalNyxColors.current.Accent.copy(0.22f), contentColor = LocalNyxColors.current.AccentSoft)) {
                            Text("应用场景", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        }

        // ── Input bar (hidden in immersive mode) ──────────────────────
        if (!immersiveMode) {
        Column(
            Modifier.background(
                if ((chatBackground.isNotBlank() || currentChar?.hasBackground == true))
                    LocalNyxColors.current.Layer1.copy(alpha = 0.78f)
                else
                    LocalNyxColors.current.Layer1
            ).padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Character quick switch bar
            if (activeChars.size > 1 && !groupMode) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeChars.forEach { char ->
                        val isSelected = char.id == selectedId
                        val hexRing = HexagonShape()
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(hexRing)
                                .background(if (isSelected) char.color.copy(0.18f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) char.color else LocalNyxColors.current.BorderSubtle.copy(0.4f),
                                    hexRing
                                )
                                .combinedClickable(
                                    onClick = { vm.selectChar(char.id); vm.maybeGreet(char.id) },
                                    onLongClick = { vm.toggleGroupMode() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Portrait(char, 28.dp, char.id == typingCharId)
                        }
                    }
                }
            }

            if (apiCfg.apiKey.isBlank()) {
                Text("⚠  请先在设置中填入 API 密钥", color = LocalNyxColors.current.Error.copy(0.8f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            // ── Floating pill input ────────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(LocalNyxColors.current.Layer2)
                    .border(0.5.dp,
                        if (input.isNotBlank()) LocalNyxColors.current.BorderMid else LocalNyxColors.current.BorderSubtle,
                        RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (narrativeMode) "描述场景…" else "说点什么…",
                            color = LocalNyxColors.current.TextDim, fontSize = 15.sp,
                            fontFamily = CrimsonProFamily,
                            fontStyle = if (narrativeMode) FontStyle.Italic else FontStyle.Normal
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = LocalNyxColors.current.TextPrimary,
                        unfocusedTextColor = LocalNyxColors.current.TextPrimary,
                        cursorColor = LocalNyxColors.current.Accent
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.5.sp, fontFamily = CrimsonProFamily, lineHeight = 24.sp,
                        color = LocalNyxColors.current.TextPrimary,
                        fontStyle = if (narrativeMode) FontStyle.Italic else FontStyle.Normal
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = {
                        // Step 4 fix (Bug 4): 补充 !isStreaming 判断，与屏幕发送按钮的
                        // canSend 逻辑保持一致。
                        // 原来只检查 typingCharId == null，但在 AI 开始回复的头几毫秒内，
                        // isStreaming 已经是 true 而 typingCharId 还未设置，此窗口内按
                        // 键盘回车键会重复发送并取消正在进行的流式回复。
                        if (input.isNotBlank() && typingCharId == null && !isStreaming) {
                            val text = if (narrativeMode) "[旁白：${input.trim()}]" else input.trim()
                            vm.sendMessage(text); input = ""; keyboard?.hide()
                        }
                    }),
                    maxLines = 6
                )

                // Narrative mode toggle
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (narrativeMode) LocalNyxColors.current.AccentPill else Color.Transparent)
                        .border(0.5.dp,
                            if (narrativeMode) LocalNyxColors.current.BorderHi else LocalNyxColors.current.BorderSubtle,
                            RoundedCornerShape(10.dp))
                        .clickable { vm.toggleNarrativeMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (narrativeMode) "叙" else "对",
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = if (narrativeMode) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                }

                val canSend = input.isNotBlank() && typingCharId == null && !isStreaming && apiCfg.apiKey.isNotBlank()
                if (isStreaming) {
                    // Stop generation button
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(LocalNyxColors.current.Accent, LocalNyxColors.current.AccentSoft)))
                            .clickable { vm.stopStreaming() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("■", color = Color.White, fontSize = 14.sp)
                    }
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(LocalNyxColors.current.Accent, LocalNyxColors.current.AccentSoft))
                                else Brush.linearGradient(listOf(LocalNyxColors.current.Layer3, LocalNyxColors.current.Layer3))
                            )
                            .combinedClickable(
                                enabled   = true,
                                onClick   = {
                                    if (canSend) {
                                        val text = if (narrativeMode) "[旁白：${input.trim()}]" else input.trim()
                                        vm.sendMessage(text); input = ""; keyboard?.hide()
                                    }
                                },
                                onLongClick = {
                                    // 步骤7b：长按触发 Prompt 预览
                                    vm.buildDebugPrompt()
                                    showPromptDebug = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↑", color = if (canSend) Color.White else LocalNyxColors.current.TextDim, fontSize = 17.sp)
                    }
                }
            }
        }
        }
    }
    } // end Box

    // ── 步骤7b：Prompt 预览底部弹层 ───────────────────────────────────────────
    if (showPromptDebug) {
        ModalBottomSheet(
            onDismissRequest = { showPromptDebug = false },
            containerColor   = LocalNyxColors.current.Layer1,
            tonalElevation   = 0.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Prompt 预览",
                    fontSize = 11.sp, fontFamily = CinzelFamily,
                    color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp
                )
                if (debugStages.isEmpty()) {
                    Text(
                        "暂无数据，请先选择角色",
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.heightIn(max = 480.dp)
                    ) {
                        items(debugStages) { (stageName, content) ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LocalNyxColors.current.Layer2)
                                    .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stageName,
                                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                        color = LocalNyxColors.current.AccentSoft
                                    )
                                    Text(
                                        "${content.length} 字",
                                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                        color = LocalNyxColors.current.TextDim
                                    )
                                }
                                Text(
                                    content,
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.TextPrimary.copy(0.85f),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 步骤9b：世界书触发详情弹层 ────────────────────────────────────────────
    if (showWbInfo) {
        val triggeredEntries = worldBook.filter { it.id in lastTriggeredWbIds }
        ModalBottomSheet(
            onDismissRequest = { showWbInfo = false },
            containerColor   = LocalNyxColors.current.Layer1,
            tonalElevation   = 0.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "本轮触发的世界书",
                    fontSize = 11.sp, fontFamily = CinzelFamily,
                    color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp
                )
                if (triggeredEntries.isEmpty()) {
                    Text(
                        "无条目",
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim
                    )
                } else {
                    triggeredEntries.forEach { entry ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(LocalNyxColors.current.Layer2)
                                .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                entry.title.ifBlank { "（无标题）" },
                                fontSize = 11.sp, fontFamily = CinzelFamily,
                                color = LocalNyxColors.current.TextPrimary, letterSpacing = 0.5.sp
                            )
                            if (entry.keywords.isNotEmpty() && !entry.sticky) {
                                // Bug #9 fix：原来显示该条目全部关键词，用户无法判断哪个词触发了本次注入。
                                // 改为：对最近 12 条消息窗口做 contains 过滤，只显示实际命中的词；
                                // 无命中词时（如 NOT 模式或旧数据）fallback 显示全部，避免空行。
                                val window = messages.takeLast(12)
                                    .joinToString(" ") { it.content }.lowercase()
                                val hitKws = entry.keywords.filter { kw ->
                                    window.contains(kw.lowercase())
                                }
                                val displayKws = hitKws.ifEmpty { entry.keywords }
                                Text(
                                    "命中关键词：${displayKws.joinToString(" · ")}",
                                    fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.Accent.copy(0.65f)
                                )
                            } else if (entry.sticky) {
                                Text(
                                    "常驻条目",
                                    fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.Warning.copy(0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniChip(label: String, tinted: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (tinted) LocalNyxColors.current.Accent.copy(0.15f) else LocalNyxColors.current.Layer2)
            .border(0.5.dp,
                if (tinted) LocalNyxColors.current.Accent.copy(0.5f) else LocalNyxColors.current.BorderSubtle,
                RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = if (tinted) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim,
            letterSpacing = 0.3.sp)
    }
}

/**
 * Batch 3 Item 7: 顶栏图标按钮 — 正方形小圆角，尺寸 30dp
 */
@Composable
fun TopBarIconBtn(icon: String, tinted: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = 40.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (tinted) LocalNyxColors.current.Accent.copy(0.15f)
                else LocalNyxColors.current.Layer2
            )
            .border(
                0.5.dp,
                if (tinted) LocalNyxColors.current.Accent.copy(0.4f)
                else LocalNyxColors.current.BorderSubtle,
                RoundedCornerShape(7.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            icon, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = if (tinted) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim
        )
    }
}

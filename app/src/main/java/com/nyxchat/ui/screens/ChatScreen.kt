package com.nyxchat.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxchat.data.*
import com.nyxchat.ui.components.*
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val chars        by vm.characters.collectAsState()
    val messages     by vm.messages.collectAsState()
    val typingCharId by vm.typingCharId.collectAsState()
    val groupMode    by vm.groupMode.collectAsState()
    val selectedId   by vm.selectedCharId.collectAsState()
    val apiCfg       by vm.apiConfig.collectAsState()
    val error        by vm.error.collectAsState()

    val activeChars = chars.filter { it.isActive }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    // Auto-scroll to bottom
    LaunchedEffect(messages.size, typingCharId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NyxColors.Background)
                .border(
                    width = 1.dp,
                    color = NyxColors.Border,
                    shape = RoundedCornerShape(0.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    append("NYX")
                    append("CHAT") // handled by color
                },
                fontSize = 15.sp,
                fontFamily = CinzelFamily,
                color = NyxColors.AccentSoft,
                letterSpacing = 2.sp
            )
            // Let's do this in two spans
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Group/Single toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (groupMode) Color(0x209D6FFF) else Color(0x0DFFFFFF)
                        )
                        .border(
                            1.dp,
                            if (groupMode) Color(0x809D6FFF) else Color(0x1AFFFFFF),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { vm.toggleGroupMode() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (groupMode) "✦ 群聊" else "◈ 单聊",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (groupMode) NyxColors.AccentSoft else NyxColors.TextDim,
                        letterSpacing = 0.5.sp
                    )
                }

                // Clear button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
                        .clickable { vm.clearChat() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "清空",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // ── Character strip ───────────────────────────────────────────────
        if (activeChars.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NyxColors.Surface.copy(alpha = 0.85f))
                    .border(1.dp, NyxColors.Border, RoundedCornerShape(0.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeChars.forEach { char ->
                    val isSelected = !groupMode && char.id == selectedId
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) char.color.copy(alpha = 0.1f) else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isSelected) char.color.copy(alpha = 0.4f) else Color.Transparent,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable(enabled = !groupMode) { vm.selectChar(char.id) }
                            .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Portrait(
                            char = char, size = 26.dp,
                            isTyping = char.id == typingCharId
                        )
                        Column {
                            Text(
                                text = char.name,
                                fontSize = 11.sp,
                                fontFamily = CinzelFamily,
                                color = if (isSelected) char.color else NyxColors.TextDim,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = moodLabel(char.mood),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = moodColor(char.mood)
                            )
                        }
                    }
                }
            }
        }

        // ── Error toast ───────────────────────────────────────────────────
        if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x14F87171))
                    .border(1.dp, Color(0x4DF87171), RoundedCornerShape(0.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error ?: "",
                    color = NyxColors.Error,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "×", color = NyxColors.Error, fontSize = 16.sp,
                    modifier = Modifier.clickable { vm.clearError() }.padding(4.dp)
                )
            }
        }

        // ── Messages ──────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("◈", fontSize = 28.sp, color = NyxColors.Accent.copy(alpha = 0.15f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (activeChars.isNotEmpty()) "开口，让夜晚开始" else "先在角色页添加角色",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NyxColors.TextDim,
                                letterSpacing = 0.5.sp,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg = msg, chars = chars)
            }

            // Typing indicator
            typingCharId?.let { cid ->
                val typingChar = chars.find { it.id == cid }
                typingChar?.let {
                    item { TypingIndicator(char = it) }
                }
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .background(NyxColors.Background)
                .border(1.dp, NyxColors.Border, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (apiCfg.apiKey.isBlank()) {
                Text(
                    text = "⚠  请先在设置中填入 API Key",
                    color = NyxColors.Error.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, if (input.isNotBlank()) NyxColors.Border.copy(alpha = 0.4f) else NyxColors.Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "说点什么…",
                            color = NyxColors.TextDim,
                            fontSize = 15.sp,
                            fontFamily = CrimsonProFamily
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = NyxColors.TextPrimary,
                        unfocusedTextColor = NyxColors.TextPrimary
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.5.sp,
                        fontFamily = CrimsonProFamily,
                        lineHeight = 24.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() && typingCharId == null) {
                            vm.sendMessage(input.trim()); input = ""
                        }
                    }),
                    maxLines = 5
                )

                // Send button
                val canSend = input.isNotBlank() && typingCharId == null && apiCfg.apiKey.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend)
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(NyxColors.Accent, NyxColors.AccentSoft)
                                )
                            else
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0x15FFFFFF), Color(0x15FFFFFF))
                                )
                        )
                        .clickable(enabled = canSend) {
                            vm.sendMessage(input.trim()); input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↑",
                        color = if (canSend) Color.White else NyxColors.TextDim,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

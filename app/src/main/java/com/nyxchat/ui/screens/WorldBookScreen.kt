package com.nyxchat.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nyxchat.data.WorldBookEntry
import com.nyxchat.pipeline.detectWorldBookConflicts
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel
import java.util.UUID

@Composable
fun WorldBookScreen(vm: ChatViewModel) {
    val entries by vm.worldBook.collectAsState()
    var editingId by remember { mutableStateOf<String?>(null) }
    var showAdd   by remember { mutableStateOf(false) }
    val context   = LocalContext.current

    // 文档导入：生成 sticky WorldBookEntry（迁移自原知识库）
    val docLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.importDocumentAsWorldBook(context, it) } }

    // 冲突检测（仅对启用的非黏着条目）
    val conflictIds = remember(entries) { detectWorldBookConflicts(entries) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("世界书", fontSize = 13.sp, fontFamily = CinzelFamily,
                    color = NyxColors.AccentSoft, letterSpacing = 2.sp)
                Text("关键词触发 · 自动注入上下文", fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, color = NyxColors.TextDim, letterSpacing = 0.3.sp)
            }
        }

        // 冲突警告横幅
        if (conflictIds.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NyxColors.Warning.copy(0.08f))
                        .border(0.5.dp, NyxColors.Warning.copy(0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠", fontSize = 16.sp, color = NyxColors.Warning)
                    Text(
                        "检测到 ${conflictIds.size} 个条目存在关键词冲突，可能导致场景矛盾。已用 ！ 标记。",
                        fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.Warning.copy(0.85f), lineHeight = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("世界书为空\n添加条目来构建你的世界",
                        fontSize = 13.sp, fontFamily = CrimsonProFamily,
                        color = NyxColors.TextDim, fontStyle = FontStyle.Italic,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 22.sp)
                }
            }
        }

        items(entries, key = { it.id }) { entry ->
            WorldBookCard(
                entry          = entry,
                isConflicting  = entry.id in conflictIds,
                isExpanded     = editingId == entry.id,
                onToggle       = { editingId = if (editingId == entry.id) null else entry.id },
                onSave         = { vm.updateWorldBookEntry(it); editingId = null },
                onDelete       = { vm.deleteWorldBookEntry(entry.id) },
                onToggleEnabled = { vm.updateWorldBookEntry(entry.copy(enabled = !entry.enabled)) }
            )
        }

        item {
            if (!showAdd) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        AddButton("+ 添加世界书条目") { showAdd = true }
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NyxColors.Success.copy(alpha = 0.1f))
                            .border(0.5.dp, NyxColors.Success.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { docLauncher.launch("text/*") }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📄 导入文档", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = NyxColors.Success)
                    }
                }
            } else {
                AddWorldBookForm(
                    onAdd = { vm.addWorldBookEntry(it); showAdd = false },
                    onCancel = { showAdd = false }
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun WorldBookCard(
    entry: WorldBookEntry,
    isConflicting: Boolean = false,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSave: (WorldBookEntry) -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    var draft by remember(isExpanded) { mutableStateOf(entry) }
    var keywordsText by remember(isExpanded) { mutableStateOf(entry.keywords.joinToString(", ")) }

    val accentColor = if (entry.enabled) NyxColors.Success else NyxColors.TextDim

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                val edgeColor = if (isExpanded) NyxColors.BorderHi else NyxColors.EdgeHighlight
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeColor, Color.Transparent)),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(listOf(NyxColors.Layer2, NyxColors.Layer1))
            )
            .border(
                0.5.dp,
                if (isExpanded) NyxColors.BorderHi else NyxColors.BorderSubtle,
                RoundedCornerShape(12.dp)
            )
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Priority indicator — shows the numeric value
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(.1f))
                    .border(1.dp, accentColor.copy(.3f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (entry.sticky) "★" else "${entry.priority}",
                    fontSize = if (entry.sticky) 12.sp else 11.sp,
                    color = if (entry.sticky) NyxColors.Warning else accentColor
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(entry.title.ifBlank { "（无标题）" }, fontSize = 13.sp,
                        fontFamily = CinzelFamily, color = if (entry.enabled) NyxColors.TextPrimary else NyxColors.TextDim,
                        letterSpacing = 0.5.sp)
                    if (entry.sticky) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(NyxColors.Warning.copy(.12f))
                                .border(0.5.dp, NyxColors.Warning.copy(.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("常驻", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.Warning.copy(.8f))
                        }
                    }
                    if (isConflicting) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(NyxColors.Warning.copy(.1f))
                                .border(0.5.dp, NyxColors.Warning.copy(.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("！冲突", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.Warning.copy(.9f))
                        }
                    }
                }
                if (entry.keywords.isNotEmpty() && !entry.sticky) {
                    Text(entry.keywords.take(4).joinToString(" · "),
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.Accent.copy(.7f), modifier = Modifier.padding(top = 2.dp))
                } else if (entry.sticky) {
                    Text("每次对话自动注入", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.Warning.copy(.5f), modifier = Modifier.padding(top = 2.dp))
                }
            }

            Chip(if (entry.enabled) "激活" else "关闭",
                if (entry.enabled) NyxColors.Success else NyxColors.TextDim,
                if (entry.enabled) Color(0x5934D399) else Color(0x1AFFFFFF),
                if (entry.enabled) Color(0x0D34D399) else Color(0x0AFFFFFF),
                onToggleEnabled)

            Chip(if (isExpanded) "收起" else "编辑",
                NyxColors.TextDim, Color(0x1AFFFFFF), Color(0x0DFFFFFF), onToggle)

            Text("×", color = NyxColors.Error.copy(.5f), fontSize = 18.sp,
                modifier = Modifier.clickable { onDelete() }.padding(4.dp))
        }

        if (isExpanded) {
            HorizontalDivider(color = NyxColors.BorderSubtle)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NyxField("标题", draft.title) { draft = draft.copy(title = it) }
                NyxField("内容（触发时注入到上下文）", draft.content, minLines = 4) { draft = draft.copy(content = it) }
                NyxField("关键词（逗号分隔，出现时触发）", keywordsText,
                    placeholder = "城市, 夜晚, 这里") {
                    keywordsText = it
                    draft = draft.copy(keywords = it.split(",").map { kw -> kw.trim() }.filter { kw -> kw.isNotBlank() })
                }

                // Match mode
                Text("触发模式", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim,
                    modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        com.nyxchat.data.MatchMode.ANY   to "任意匹配",
                        com.nyxchat.data.MatchMode.REGEX to "正则"
                    ).forEach { (mode, label) ->
                        Box(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (draft.matchMode==mode) Color(0x209D6FFF) else Color(0x0AFFFFFF))
                            .border(0.5.dp, if (draft.matchMode==mode) Color(0x669D6FFF) else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                            .clickable { draft = draft.copy(matchMode = mode) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)) {
                            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (draft.matchMode==mode) NyxColors.AccentSoft else NyxColors.TextDim)
                        }
                    }
                }

                // Cooldown
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    Text("冷却（条）", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                    listOf(0, 5, 10, 20).forEach { n ->
                        Box(Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (draft.cooldownMsgs==n) Color(0x209D6FFF) else Color(0x0AFFFFFF))
                            .border(0.5.dp, if (draft.cooldownMsgs==n) Color(0x559D6FFF) else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                            .clickable { draft = draft.copy(cooldownMsgs = n) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)) {
                            Text(if (n==0) "无" else "$n", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (draft.cooldownMsgs==n) NyxColors.AccentSoft else NyxColors.TextDim)
                        }
                    }
                }

                // Sticky toggle
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("常驻注入", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                        Text("忽略关键词，每次对话强制注入", fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace, color = NyxColors.TextDim.copy(.6f))
                    }
                    Switch(
                        checked = draft.sticky,
                        onCheckedChange = { draft = draft.copy(sticky = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = NyxColors.Warning,
                            checkedTrackColor  = NyxColors.Warning.copy(.3f),
                            uncheckedTrackColor = NyxColors.BorderSubtle
                        )
                    )
                }

                // Priority — slider for fine-grained control
                if (!draft.sticky) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("触发优先级", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                            Text("${draft.priority}", fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, color = NyxColors.AccentSoft)
                        }
                        Slider(
                            value = draft.priority.toFloat(),
                            onValueChange = { draft = draft.copy(priority = it.toInt()) },
                            valueRange = 0f..100f, steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = NyxColors.Accent,
                                activeTrackColor = NyxColors.Accent,
                                inactiveTrackColor = NyxColors.BorderSubtle
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("低（0）", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                            Text("高（100）", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                        }
                        Text(
                            when {
                                draft.priority == 0  -> "预算耗尽时最先被丢弃"
                                draft.priority < 30  -> "低优先级：背景补充信息"
                                draft.priority < 70  -> "中等：重要但可让位"
                                else                 -> "高优先级：几乎总会注入"
                            },
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = NyxColors.TextDim.copy(.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color(0x509D6FFF), NyxColors.AccentSoft),
                    shape = RoundedCornerShape(10.dp)) {
                    Text("保存", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AddWorldBookForm(onAdd: (WorldBookEntry) -> Unit, onCancel: () -> Unit) {
    var title    by remember { mutableStateOf("") }
    var content  by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, NyxColors.BorderHi, RoundedCornerShape(12.dp))
            .background(NyxColors.Layer1).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("新建世界书条目", fontSize = 11.sp, fontFamily = CinzelFamily,
            color = NyxColors.Accent, letterSpacing = 1.5.sp)
        NyxField("标题", title) { title = it }
        NyxField("内容", content, minLines = 3, placeholder = "这段背景在关键词触发时注入到角色的上下文中...") { content = it }
        NyxField("触发关键词（逗号分隔）", keywords, placeholder = "城市, 夜晚, 秘密") { keywords = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (content.isNotBlank()) {
                    onAdd(WorldBookEntry(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { "无标题" },
                        content = content,
                        keywords = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    ))
                }
            }, Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(Color(0x509D6FFF), NyxColors.AccentSoft),
                shape = RoundedCornerShape(10.dp)) {
                Text("创建", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onCancel, Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NyxColors.TextDim),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF))) {
                Text("取消", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

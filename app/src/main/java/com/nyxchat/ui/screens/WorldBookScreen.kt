package com.nyxchat.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
    WorldBookContent(vm)
}

/**
 * 世界书内容主体——可独立使用（WorldBookScreen），
 * 也可嵌入 CharactersScreen 的世界书 Tab。
 */
@Composable
fun WorldBookContent(vm: ChatViewModel) {
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
                    color = LocalNyxColors.current.AccentSoft, letterSpacing = 2.sp)
                Text("关键词触发 · 自动注入上下文", fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim, letterSpacing = 0.3.sp)
            }
        }

        // 冲突警告横幅
        if (conflictIds.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalNyxColors.current.Warning.copy(0.08f))
                        .border(0.5.dp, LocalNyxColors.current.Warning.copy(0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠", fontSize = 16.sp, color = LocalNyxColors.current.Warning)
                    Text(
                        "检测到 ${conflictIds.size} 个条目存在关键词冲突，可能导致场景矛盾。已用 ！ 标记。",
                        fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.Warning.copy(0.85f), lineHeight = 17.sp,
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
                        color = LocalNyxColors.current.TextDim, fontStyle = FontStyle.Italic,
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
                            .background(LocalNyxColors.current.Success.copy(alpha = 0.1f))
                            .border(0.5.dp, LocalNyxColors.current.Success.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { docLauncher.launch("text/*") }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📄 导入文档", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.Success)
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
    // Phase 4-A：四个参数说明的展开状态
    var tipMatchMode by remember { mutableStateOf(false) }
    var tipCooldown  by remember { mutableStateOf(false) }
    var tipSticky    by remember { mutableStateOf(false) }
    var tipPriority  by remember { mutableStateOf(false) }

    val accentColor   = if (entry.enabled) LocalNyxColors.current.Success else LocalNyxColors.current.TextDim
    val borderHi      = LocalNyxColors.current.BorderHi       // 提前提取，drawBehind 非 Composable 上下文
    val edgeHighlight = LocalNyxColors.current.EdgeHighlight

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                val edgeColor = if (isExpanded) borderHi else edgeHighlight
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeColor, Color.Transparent)),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(listOf(LocalNyxColors.current.Layer2, LocalNyxColors.current.Layer1))
            )
            .border(
                0.5.dp,
                if (isExpanded) LocalNyxColors.current.BorderHi else LocalNyxColors.current.BorderSubtle,
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
                    color = if (entry.sticky) LocalNyxColors.current.Warning else accentColor
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(entry.title.ifBlank { "（无标题）" }, fontSize = 13.sp,
                        fontFamily = CinzelFamily, color = if (entry.enabled) LocalNyxColors.current.TextPrimary else LocalNyxColors.current.TextDim,
                        letterSpacing = 0.5.sp)
                    if (entry.sticky) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(LocalNyxColors.current.Warning.copy(.12f))
                                .border(0.5.dp, LocalNyxColors.current.Warning.copy(.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("常驻", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.Warning.copy(.8f))
                        }
                    }
                    if (isConflicting) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(LocalNyxColors.current.Warning.copy(.1f))
                                .border(0.5.dp, LocalNyxColors.current.Warning.copy(.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("！冲突", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.Warning.copy(.9f))
                        }
                    }
                }
                if (entry.keywords.isNotEmpty() && !entry.sticky) {
                    Text(entry.keywords.take(4).joinToString(" · "),
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.Accent.copy(.7f), modifier = Modifier.padding(top = 2.dp))
                } else if (entry.sticky) {
                    Text("每次对话自动注入", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.Warning.copy(.5f), modifier = Modifier.padding(top = 2.dp))
                }
            }

            Chip(if (entry.enabled) "激活" else "关闭",
                if (entry.enabled) LocalNyxColors.current.Success else LocalNyxColors.current.TextDim,
                if (entry.enabled) Color(0x5934D399) else LocalNyxColors.current.BorderSubtle,
                if (entry.enabled) Color(0x0D34D399) else LocalNyxColors.current.Layer2,
                onToggleEnabled)

            Chip(if (isExpanded) "收起" else "编辑",
                LocalNyxColors.current.TextDim, LocalNyxColors.current.BorderSubtle, LocalNyxColors.current.Layer2, onToggle)

            Text("×", color = LocalNyxColors.current.Error.copy(.5f), fontSize = 18.sp,
                modifier = Modifier.clickable { onDelete() }.padding(4.dp))
        }

        if (isExpanded) {
            HorizontalDivider(color = LocalNyxColors.current.BorderSubtle)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NyxField("标题", draft.title) { draft = draft.copy(title = it) }
                NyxField("内容（触发时注入到上下文）", draft.content, minLines = 4) { draft = draft.copy(content = it) }
                NyxField("关键词（逗号分隔，出现时触发）", keywordsText,
                    placeholder = "城市, 夜晚, 这里") {
                    keywordsText = it
                    draft = draft.copy(keywords = it.split(",").map { kw -> kw.trim() }.filter { kw -> kw.isNotBlank() })
                }

                // Match mode — Phase 2-D: 补全 ALL / NOT（数据模型已实现，仅补 UI 入口）
                // Phase 4-A: 加参数说明 tooltip
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    Text("触发模式", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                    Text("ⓘ", fontSize = 9.sp, color = LocalNyxColors.current.TextDim.copy(0.45f),
                        modifier = Modifier.clickable { tipMatchMode = !tipMatchMode })
                }
                AnimatedVisibility(tipMatchMode) {
                    Text(
                        "ANY=任一关键词出现 / ALL=全部出现 / NOT=关键词不出现时触发 / REGEX=正则",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.AccentSoft.copy(0.7f),
                        lineHeight = 14.sp, modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        com.nyxchat.data.MatchMode.ANY   to "任意",
                        com.nyxchat.data.MatchMode.ALL   to "全匹配",
                        com.nyxchat.data.MatchMode.NOT   to "反向",
                        com.nyxchat.data.MatchMode.REGEX to "正则"
                    ).forEach { (mode, label) ->
                        Box(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (draft.matchMode==mode) Color(0x209D6FFF) else LocalNyxColors.current.Layer3)
                            .border(0.5.dp, if (draft.matchMode==mode) Color(0x669D6FFF) else LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { draft = draft.copy(matchMode = mode) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)) {
                            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (draft.matchMode==mode) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                        }
                    }
                }
                // 模式说明提示
                Text(
                    when (draft.matchMode) {
                        com.nyxchat.data.MatchMode.ANY   -> "任意一个关键词出现即触发"
                        com.nyxchat.data.MatchMode.ALL   -> "全部关键词都出现才触发"
                        com.nyxchat.data.MatchMode.NOT   -> "关键词均不出现时触发（黑名单）"
                        com.nyxchat.data.MatchMode.REGEX -> "关键词作为正则表达式匹配"
                    },
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim.copy(0.65f),
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Cooldown — Phase 4-A: 加参数说明 tooltip
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("冷却（条）", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                        Text("ⓘ", fontSize = 9.sp, color = LocalNyxColors.current.TextDim.copy(0.45f),
                            modifier = Modifier.clickable { tipCooldown = !tipCooldown })
                        listOf(0, 5, 10, 20).forEach { n ->
                            Box(Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (draft.cooldownMsgs==n) Color(0x209D6FFF) else LocalNyxColors.current.Layer3)
                                .border(0.5.dp, if (draft.cooldownMsgs==n) Color(0x559D6FFF) else LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { draft = draft.copy(cooldownMsgs = n) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text(if (n==0) "无" else "$n", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    color = if (draft.cooldownMsgs==n) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                            }
                        }
                    }
                    AnimatedVisibility(tipCooldown) {
                        Text(
                            "触发后多少条消息内不再重复注入该条目",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.AccentSoft.copy(0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Sticky toggle — Phase 4-A: 加参数说明 tooltip
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("常驻注入", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                                Text("ⓘ", fontSize = 9.sp, color = LocalNyxColors.current.TextDim.copy(0.45f),
                                    modifier = Modifier.clickable { tipSticky = !tipSticky })
                            }
                            Text("忽略关键词，每次对话强制注入", fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim.copy(.6f))
                        }
                        Switch(
                            checked = draft.sticky,
                            onCheckedChange = { draft = draft.copy(sticky = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor  = LocalNyxColors.current.Warning,
                                checkedTrackColor  = LocalNyxColors.current.Warning.copy(.3f),
                                uncheckedTrackColor = LocalNyxColors.current.BorderSubtle
                            )
                        )
                    }
                    AnimatedVisibility(tipSticky) {
                        Text(
                            "勾选后无论对话内容如何都注入，适合核心世界观设定",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.Warning.copy(0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Priority — slider for fine-grained control — Phase 4-A: 加参数说明 tooltip
                if (!draft.sticky) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("触发优先级", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                                Text("ⓘ", fontSize = 9.sp, color = LocalNyxColors.current.TextDim.copy(0.45f),
                                    modifier = Modifier.clickable { tipPriority = !tipPriority })
                            }
                            Text("${draft.priority}", fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.AccentSoft)
                        }
                        AnimatedVisibility(tipPriority) {
                            Text(
                                "Context 快满时高优先级条目优先保留；常驻条目始终优先于触发条目",
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.AccentSoft.copy(0.7f),
                                lineHeight = 14.sp, modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Slider(
                            value = draft.priority.toFloat(),
                            onValueChange = { draft = draft.copy(priority = it.toInt()) },
                            valueRange = 0f..100f, steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = LocalNyxColors.current.Accent,
                                activeTrackColor = LocalNyxColors.current.Accent,
                                inactiveTrackColor = LocalNyxColors.current.BorderSubtle
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("低（0）", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                            Text("高（100）", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                        }
                        Text(
                            when {
                                draft.priority == 0  -> "预算耗尽时最先被丢弃"
                                draft.priority < 30  -> "低优先级：背景补充信息"
                                draft.priority < 70  -> "中等：重要但可让位"
                                else                 -> "高优先级：几乎总会注入"
                            },
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim.copy(.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color(0x509D6FFF), LocalNyxColors.current.AccentSoft),
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
            .border(1.dp, LocalNyxColors.current.BorderHi, RoundedCornerShape(12.dp))
            .background(LocalNyxColors.current.Layer1).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("新建世界书条目", fontSize = 11.sp, fontFamily = CinzelFamily,
            color = LocalNyxColors.current.Accent, letterSpacing = 1.5.sp)
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
                colors = ButtonDefaults.buttonColors(Color(0x509D6FFF), LocalNyxColors.current.AccentSoft),
                shape = RoundedCornerShape(10.dp)) {
                Text("创建", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onCancel, Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalNyxColors.current.TextDim),
                border = BorderStroke(1.dp, LocalNyxColors.current.BorderSubtle)) {
                Text("取消", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

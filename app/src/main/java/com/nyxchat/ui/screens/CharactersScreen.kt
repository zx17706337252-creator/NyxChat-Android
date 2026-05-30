package com.nyxchat.ui.screens

import com.nyxchat.ui.dialog.AvatarCropDialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nyxchat.data.*
import com.nyxchat.ui.components.Portrait
import com.nyxchat.ui.theme.*
import com.nyxchat.ui.theme.CHARACTER_COLORS
import com.nyxchat.viewmodel.ChatViewModel
import java.io.File
import java.util.UUID

@Composable
fun CharactersScreen(vm: ChatViewModel) {
    val context  = androidx.compose.ui.platform.LocalContext.current
    val chars    by vm.characters.collectAsState()
    val memories by vm.memories.collectAsState()
    // 步骤8：有效记忆预览数据
    val effectiveMemsPreview by vm.effectiveMemsPreview.collectAsState()
    var editingId by remember { mutableStateOf<String?>(null) }
    var showAdd   by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf("name") } // "name" or "active"

    val sortedChars = remember(chars, sortOrder) {
        when (sortOrder) {
            "active" -> chars.sortedByDescending { it.lastActiveAt }
            else -> chars.sortedBy { it.name.lowercase() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("角色列表", fontSize = 13.sp, fontFamily = CinzelFamily,
                    color = NyxColors.AccentSoft, letterSpacing = 2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 排序切换
                    Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(NyxColors.Layer1)
                        .border(0.5.dp, NyxColors.BorderSubtle, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .clickable { sortOrder = if (sortOrder == "name") "active" else "name" }
                        .padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(
                            if (sortOrder == "name") "按名称 ↑" else "按活跃 ↓",
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim
                        )
                    }

                }
            }
        }
    }

    items(sortedChars, key = { it.id }) { char ->
            val charMems = memories.filter { it.charId == char.id }
            CharacterCard(
                char = char, charMems = charMems,
                isExpanded = editingId == char.id,
                onToggleEdit = { editingId = if (editingId == char.id) null else char.id },
                onSave = { vm.updateCharacter(it); editingId = null },
                onDelete = { vm.deleteCharacter(char.id) },
                onToggleActive = { vm.updateCharacter(char.copy(isActive = !char.isActive)) },
                onDeleteMem = { vm.deleteMemory(it) },
                onClearMems = { vm.clearCharMemories(char.id) },
                onSetAvatar = { uri -> vm.setCharacterAvatar(char.id, uri) },
                onSetBackground = { uri -> vm.setCharacterBackground(char.id, uri) },
                onAddMemory = { text -> vm.addManualMemory(char.id, text) },
                onUpdateMem = { mem -> vm.updateMemory(mem) },
                onExport = { vm.shareCharacter(context, char) },
                onSaveCroppedAvatar = { bmp -> vm.saveCroppedAvatar(char.id, bmp) },
                effectiveMems = effectiveMemsPreview[char.id],
                onRequestEffectiveMems = { vm.loadEffectiveMems(char.id) }
            )
        }

        item {
            if (!showAdd) {
                AddButton("+ 添加角色") { showAdd = true }
            } else {
                AddCharacterForm(onAdd = { vm.addCharacter(it); showAdd = false }, onCancel = { showAdd = false })
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun CharacterCard(
    char: NyxCharacter,
    charMems: List<NyxMemory>,
    isExpanded: Boolean,
    onToggleEdit: () -> Unit,
    onSave: (NyxCharacter) -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onDeleteMem: (String) -> Unit,
    onClearMems: () -> Unit,
    onSetAvatar: (android.net.Uri) -> Unit,
    onSetBackground: (android.net.Uri) -> Unit,
    onAddMemory: (String) -> Unit,
    onExport: () -> Unit,
    onSaveCroppedAvatar: (android.graphics.Bitmap) -> Unit = {},
    onUpdateMem: (com.nyxchat.data.NyxMemory) -> Unit = {},
    // 步骤8：有效记忆预览
    effectiveMems: List<NyxMemory>? = null,
    onRequestEffectiveMems: () -> Unit = {},
) {
    var draft by remember(isExpanded) { mutableStateOf(char) }
    var showMems     by remember { mutableStateOf(false) }
    // 步骤8：有效记忆折叠区展开状态
    var showEffectiveMems by remember { mutableStateOf(false) }
    var pendingAvatarUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingAvatarUri = it }
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSetBackground(it) }
    }

    pendingAvatarUri?.let { uri ->
        AvatarCropDialog(
            uri      = uri,
            context  = context,
            charName = char.name,
            onConfirm = { bmp -> onSaveCroppedAvatar(bmp); pendingAvatarUri = null },
            onDismiss = { pendingAvatarUri = null }
        )
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                // Top edge highlight — simulates light from above
                val edgeColor = if (isExpanded) char.color.copy(0.45f) else NyxColors.EdgeHighlight
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeColor, Color.Transparent)),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .background(
                if (isExpanded)
                    Brush.verticalGradient(listOf(NyxColors.Layer2, NyxColors.Layer1))
                else
                    Brush.verticalGradient(listOf(NyxColors.Layer1.copy(1f), NyxColors.Layer1))
            )
            .border(
                0.5.dp,
                if (isExpanded) char.color.copy(0.28f) else NyxColors.BorderSubtle,
                RoundedCornerShape(16.dp)
            )
    ) {
        // ── Banner / background preview ───────────────────────────────
        Box(
            Modifier.fillMaxWidth().height(72.dp)
                .background(Brush.horizontalGradient(listOf(char.color.copy(0.15f), char.color.copy(0.05f))))
        ) {
            if (char.hasBackground) {
                AsyncImage(File(char.backgroundPath), null,
                    Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
            }
            // Background change button
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x60000000))
                .clickable { bgLauncher.launch("image/*") }
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("换背景", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(0.7f))
            }
        }

        // ── Header row ────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            // Avatar with tap-to-change
            Box(Modifier.clickable { avatarLauncher.launch("image/*") }) {
                Portrait(if (isExpanded) draft else char, 46.dp)
                // Camera badge
                Box(Modifier.align(Alignment.BottomEnd)
                    .size(16.dp).clip(CircleShape)
                    .background(NyxColors.Layer3)
                    .border(1.dp, NyxColors.BorderMid, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("✎", fontSize = 7.sp, color = NyxColors.TextSecond)
                }
            }

            Column(Modifier.weight(1f)) {
                Text(char.name, fontSize = 15.sp, fontFamily = CinzelFamily,
                    color = char.color, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(moodLabel(char.mood), fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, color = moodColor(char.mood))
                    if (char.age.isNotBlank())
                        Text("${char.age}岁", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                    if (char.height.isNotBlank())
                        Text(char.height, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                }
                if (char.occupation.isNotBlank())
                    Text(char.occupation, fontSize = 10.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextDim)
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (charMems.isNotEmpty()) {
                        Chip("◉ ${charMems.size}", char.color, char.color.copy(.25f), char.color.copy(.08f)) { showMems = !showMems }
                    }
                    Chip(if (char.isActive) "启用" else "停用",
                        if (char.isActive) NyxColors.Success else NyxColors.TextDim,
                        if (char.isActive) NyxColors.Success.copy(.3f) else Color(0x1AFFFFFF),
                        if (char.isActive) NyxColors.Success.copy(.08f) else Color(0x0AFFFFFF),
                        onToggleActive)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip("导出", NyxColors.AccentSoft, NyxColors.Accent.copy(.3f),
                        NyxColors.Accent.copy(.08f), onExport)
                    Chip(if (isExpanded) "收起" else "编辑",
                        NyxColors.TextSecond, NyxColors.BorderSubtle, Color.Transparent, onToggleEdit)
                    Chip("删", NyxColors.Error, NyxColors.Error.copy(.3f), NyxColors.Error.copy(.08f), onDelete)
                }
            }
        }

        // ── Memory panel ─────────────────────────────────────────────
        if (showMems && charMems.isNotEmpty()) {
            HorizontalDivider(color = NyxColors.BorderSubtle)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("记忆碎片", fontSize = 10.sp, fontFamily = CinzelFamily, color = char.color, letterSpacing = 1.sp)
                    Text("全清", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.Error,
                        modifier = Modifier.clickable { onClearMems(); showMems = false }.padding(4.dp))
                }
                SectionLabel("记忆碎片")
                charMems.forEach { mem ->
                    var editingMem by remember { mutableStateOf<String?>(null) }
                    Column {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                                repeat(5) { i ->
                                    Box(Modifier.size(4.dp).clip(CircleShape)
                                        .background(if (i < mem.importance) char.color else Color(0x15FFFFFF)))
                                }
                            }
                            Text(mem.content, fontSize = 13.sp, fontFamily = CrimsonProFamily,
                                color = NyxColors.TextPrimary, modifier = Modifier.weight(1f), lineHeight = 18.sp)
                            // 步骤13：类型标签徽章
                            if (mem.type != "Public") {
                                val typeLabel = when (mem.type) {
                                    "Private"         -> "私"
                                    "HiddenInference" -> "猜"
                                    "Sensitive"       -> "敏"
                                    else              -> mem.type.take(1)
                                }
                                Text(typeLabel, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                    color = char.color.copy(0.7f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(char.color.copy(0.12f))
                                        .padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                            Text("✎", color = NyxColors.TextDim.copy(.5f), fontSize = 12.sp,
                                modifier = Modifier.clickable { editingMem = if (editingMem == mem.id) null else mem.id }.padding(2.dp))
                            Text("×", color = NyxColors.Error.copy(.4f), fontSize = 14.sp,
                                modifier = Modifier.clickable { onDeleteMem(mem.id) }.padding(2.dp))
                        }
                        if (editingMem == mem.id) {
                            var editContent by remember { mutableStateOf(mem.content) }
                            var editImportance by remember { mutableStateOf(mem.importance.toFloat()) }
                            var editType by remember { mutableStateOf(mem.type) }
                            Column(Modifier.padding(start = 14.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = editContent,
                                    onValueChange = { editContent = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = char.color.copy(0.5f),
                                        unfocusedBorderColor = NyxColors.BorderSubtle
                                    ),
                                    minLines = 2, maxLines = 4
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("重要度", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                                    Slider(value = editImportance, onValueChange = { editImportance = it },
                                        valueRange = 1f..5f, steps = 3, modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = char.color, activeTrackColor = char.color.copy(0.4f)))
                                    Text(editImportance.toInt().toString(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = char.color)
                                }
                                // 步骤13：记忆类型选择 Chip
                                val memTypeOptions = listOf(
                                    "Public"          to "公开",
                                    "Private"         to "私有",
                                    "HiddenInference" to "猜测",
                                    "Sensitive"       to "敏感"
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    memTypeOptions.forEach { (typeKey, typeLabel) ->
                                        val selected = editType == typeKey
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (selected) char.color.copy(0.22f) else Color(0x10FFFFFF))
                                                .clickable { editType = typeKey }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                typeLabel,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (selected) char.color else NyxColors.TextDim
                                            )
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("保存", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = char.color,
                                        modifier = Modifier.clickable {
                                            onUpdateMem(mem.copy(content = editContent, importance = editImportance.toInt(), type = editType))
                                            editingMem = null
                                        }.padding(4.dp))
                                    Text("取消", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim,
                                        modifier = Modifier.clickable { editingMem = null }.padding(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 步骤8：当前有效记忆预览 ─────────────────────────────────────────
        HorizontalDivider(color = NyxColors.BorderSubtle)
        Row(
            Modifier.fillMaxWidth()
                .clickable {
                    showEffectiveMems = !showEffectiveMems
                    if (showEffectiveMems) onRequestEffectiveMems()
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "当前有效记忆",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.TextDim
            )
            Text(
                if (showEffectiveMems) "▲" else "▽",
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.TextDim.copy(0.6f)
            )
        }
        if (showEffectiveMems) {
            Column(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (effectiveMems == null) {
                    Text(
                        "加载中…",
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim
                    )
                } else if (effectiveMems.isEmpty()) {
                    Text(
                        "暂无有效记忆",
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim
                    )
                } else {
                    effectiveMems.forEachIndexed { idx, mem ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${idx + 1}.",
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.TextDim.copy(0.5f),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                mem.content,
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.TextPrimary.copy(0.85f),
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        "按衰减权重降序排列 · 实际对话会注入前 ${effectiveMems.size} 条",
                        fontSize = 8.5.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim.copy(0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ── Expanded editor ───────────────────────────────────────────
        if (isExpanded) {
            HorizontalDivider(color = NyxColors.BorderSubtle)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {

                SectionLabel("基本信息")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NyxField("名字", draft.name, Modifier.weight(1f)) { draft = draft.copy(name = it) }
                    NyxField("缩写", draft.initials, Modifier.width(52.dp)) { draft = draft.copy(initials = it.take(2)) }
                }

                Spacer(Modifier.height(8.dp))
                Column {
                    Text("角色颜色", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim, letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CHARACTER_COLORS) { (argb, label) ->
                            val c = androidx.compose.ui.graphics.Color(argb)
                            val selected = draft.colorArgb == argb
                            Box(
                                Modifier
                                    .size(if (selected) 34.dp else 28.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(c)
                                    .border(if (selected) 2.5.dp else 0.dp,
                                        Color.White.copy(if (selected) 0.8f else 0f),
                                        androidx.compose.foundation.shape.CircleShape)
                                    .clickable { draft = draft.copy(colorArgb = argb) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Text("✓", fontSize = 12.sp, color = Color.White,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NyxField("年龄", draft.age, Modifier.weight(1f), placeholder = "24") { draft = draft.copy(age = it) }
                    NyxField("身高", draft.height, Modifier.weight(1f), placeholder = "170 cm") { draft = draft.copy(height = it) }
                    NyxField("体重", draft.weight, Modifier.weight(1f), placeholder = "56 kg") { draft = draft.copy(weight = it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NyxField("国籍/种族", draft.nationality, Modifier.weight(1f)) { draft = draft.copy(nationality = it) }
                    NyxField("职业", draft.occupation, Modifier.weight(1f)) { draft = draft.copy(occupation = it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NyxField(
                        label = "生日（MM-dd）",
                        value = draft.birthday,
                        modifier = Modifier.weight(1f),
                        placeholder = "01-25"
                    ) { v ->
                        val cleaned = v.filter { it.isDigit() || it == '-' }.take(5)
                        draft = draft.copy(birthday = cleaned)
                    }
                    // placeholder to keep row balanced
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                NyxField("外貌描述", draft.appearance, minLines = 2, placeholder = "发色、眼睛、体型、气质、着装风格…") { draft = draft.copy(appearance = it) }

                Spacer(Modifier.height(14.dp))
                SectionLabel("性格与语言")
                NyxField("性格特质", draft.traits, minLines = 2) { draft = draft.copy(traits = it) }
                Spacer(Modifier.height(8.dp))
                NyxField("说话方式（硬性规则）", draft.style, minLines = 3) { draft = draft.copy(style = it) }
                Spacer(Modifier.height(8.dp))
                NyxField("背景与内驱力", draft.background, minLines = 2) { draft = draft.copy(background = it) }

                Spacer(Modifier.height(14.dp))
                SectionLabel("关系与心理")
                NyxField("与其他角色的关系", draft.relationships, minLines = 2, placeholder = "与露娜：…") { draft = draft.copy(relationships = it) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NyxField("喜好", draft.likes, Modifier.weight(1f), minLines = 2) { draft = draft.copy(likes = it) }
                    NyxField("厌恶", draft.dislikes, Modifier.weight(1f), minLines = 2) { draft = draft.copy(dislikes = it) }
                }
                Spacer(Modifier.height(8.dp))
                NyxField("秘密 / 隐藏面", draft.secrets, minLines = 2, placeholder = "只有你知道…") { draft = draft.copy(secrets = it) }

                // ── 三层人格：内核字段 ─────────────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                SectionLabel("角色内核（AI可见，用户不可见）")

                NyxField(
                    "核心创伤",
                    draft.coreWound, minLines = 2,
                    placeholder = "曾经付出过全部，被彻底辜负。此后不再轻易动心。"
                ) { draft = draft.copy(coreWound = it) }

                NyxField(
                    "核心渴望",
                    draft.coreDesire, minLines = 2,
                    placeholder = "被一个人完全接住，不需要交换，不需要表演。"
                ) { draft = draft.copy(coreDesire = it) }

                NyxField(
                    "面具何时碎裂（触发条件）",
                    draft.maskTrigger, minLines = 2,
                    placeholder = "对方第一次让她感到真正的安全；或她突然意识到自己已经在乎了。"
                ) { draft = draft.copy(maskTrigger = it) }

                NyxField(
                    "私下真实面目（面具碎裂后）",
                    draft.privatePersona, minLines = 2,
                    placeholder = "情感极度浓烈，像最纯粹的孩子，没有防御，也没有理智。"
                ) { draft = draft.copy(privatePersona = it) }

                NyxField(
                    "私下说话方式（面具碎裂后）",
                    draft.privateStyle, minLines = 3,
                    placeholder = "语气突然软下来。说话开始没有逻辑。可能哑口无言，也可能一下子说很多。"
                ) { draft = draft.copy(privateStyle = it) }

                NyxField(
                    "私下对话示例（破防/激活状态）",
                    draft.privateExamples, minLines = 4,
                    placeholder = "用户：你哭了吗？\n${draft.name}：（没有回答，只是把头埋进他肩膀）"
                ) { draft = draft.copy(privateExamples = it) }

                Spacer(Modifier.height(14.dp))
                SectionLabel("对话行为")
                NyxField("开场白（对话开始时自动发送）", draft.greeting, placeholder = "……") { draft = draft.copy(greeting = it) }
                Spacer(Modifier.height(8.dp))
                NyxField("说话示例（Few-shot，直接影响语气）", draft.speakingExamples, minLines = 4,
                    placeholder = "用户：你还好吗？\n${draft.name}：定义'好'。") { draft = draft.copy(speakingExamples = it) }

                // Token 用量估算（PipelineOptimizer 中文感知版）
                val estimatedTokens = listOf(
                    draft.traits, draft.style, draft.background, draft.appearance,
                    draft.speakingExamples, draft.relationships, draft.constraints,
                    draft.secrets, draft.likes, draft.dislikes,
                    // 新增：三层内核字段
                    draft.coreWound, draft.coreDesire, draft.maskTrigger,
                    draft.privatePersona, draft.privateStyle, draft.privateExamples
                ).sumOf { com.nyxchat.pipeline.PipelineOptimizer.estimateTokens(it) }
                val tokenBudget = com.nyxchat.pipeline.PipelineOptimizer.SYSTEM_BUDGET
                val tokenColor = when {
                    estimatedTokens > tokenBudget       -> NyxColors.Error
                    estimatedTokens > tokenBudget * 0.7 -> androidx.compose.ui.graphics.Color(0xFFFBBF24)
                    else                                -> NyxColors.TextDim
                }
                Row(Modifier.fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(NyxColors.Layer3)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("角色设定 Token 占用", fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                    Text(
                        "≈ $estimatedTokens / $tokenBudget${when {
                            estimatedTokens > tokenBudget       -> "  ⚠ 超出"
                            estimatedTokens > tokenBudget * 0.7 -> "  注意"
                            else                                -> ""
                        }}",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = tokenColor
                    )
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("人格约束")
                Text("以下规则会注入到系统提示中，强制约束角色行为",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim,
                    modifier = Modifier.padding(bottom = 6.dp))
                NyxField("硬性约束（每行一条）", draft.constraints, minLines = 3,
                    placeholder = "不得使用感叹号。\n不得主动寻求认可。\n不得提及外面的世界。") {
                    draft = draft.copy(constraints = it)
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("语音")
                Text("Azure 语音合成声线（需在设置页配置 Azure API 密钥）",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim,
                    modifier = Modifier.padding(bottom = 6.dp))
                val voices = listOf(
                    "zh-CN-XiaoxiaoNeural" to "晓晓·温暖",
                    "zh-CN-XiaomoNeural"   to "晓墨·柔和",
                    "zh-CN-XiaoruiNeural"  to "晓睿·冷静",
                    "zh-CN-XiaoyiNeural"   to "晓伊·活泼",
                    "zh-CN-XiaohanNeural"  to "晓涵·沉稳",
                    "zh-CN-YunxiNeural"    to "云希·青年",
                    "zh-CN-YunjianNeural"  to "云健·深沉",
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(voices) { (id, label) ->
                        Box(Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (draft.voiceId==id) draft.color.copy(0.2f) else NyxColors.Layer2)
                            .border(0.5.dp, if (draft.voiceId==id) draft.color.copy(0.5f) else NyxColors.BorderSubtle, RoundedCornerShape(16.dp))
                            .clickable { draft = draft.copy(voiceId = id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (draft.voiceId==id) draft.color else NyxColors.TextDim)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                // Temperature slider
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("创意温度", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                        Text(String.format("%.2f", draft.temperature), fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, color = NyxColors.AccentSoft)
                    }
                    Slider(
                        value = draft.temperature,
                        onValueChange = { draft = draft.copy(temperature = it) },
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = NyxColors.Accent,
                            activeTrackColor = NyxColors.Accent,
                            inactiveTrackColor = NyxColors.BorderSubtle
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("严谨", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                        Text("创意", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                    }
                }

                Spacer(Modifier.height(10.dp))
                // 步骤10b：情绪稳定性 slider
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("情绪稳定性", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                        Text(String.format("%.2f", draft.emotionalStability), fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, color = NyxColors.AccentSoft)
                    }
                    Slider(
                        value = draft.emotionalStability,
                        onValueChange = { draft = draft.copy(emotionalStability = it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = NyxColors.Accent,
                            activeTrackColor = NyxColors.Accent,
                            inactiveTrackColor = NyxColors.BorderSubtle
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("情绪易变", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                        Text("情绪稳定", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("主动消息 / 通知")
                Text(
                    "开启后角色会在设定的时间段内主动向你发消息（需要系统通知权限）",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim,
                    lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
                )

                // Enable switch
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NyxColors.Layer3)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("启用主动消息", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = NyxColors.TextPrimary)
                        Text("角色会定时给你发通知", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = NyxColors.TextDim)
                    }
                    Switch(
                        checked = draft.proactiveConfig.enabled,
                        onCheckedChange = {
                            draft = draft.copy(proactiveConfig = draft.proactiveConfig.copy(enabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = draft.color,
                            checkedTrackColor   = draft.color.copy(0.3f),
                            uncheckedTrackColor = NyxColors.BorderSubtle
                        )
                    )
                }

                if (draft.proactiveConfig.enabled) {
                    Spacer(Modifier.height(10.dp))

                    // Active hours
                    Text("活跃时段", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim, modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("开始（时）", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.TextDim, modifier = Modifier.padding(bottom = 2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(6, 8, 10, 12).forEach { h ->
                                    val sel = draft.proactiveConfig.activeStart == h
                                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) draft.color.copy(.2f) else NyxColors.Layer2)
                                        .border(0.5.dp, if (sel) draft.color.copy(.5f) else NyxColors.BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { draft = draft.copy(proactiveConfig = draft.proactiveConfig.copy(activeStart = h)) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text("$h", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                            color = if (sel) draft.color else NyxColors.TextDim)
                                    }
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("结束（时）", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.TextDim, modifier = Modifier.padding(bottom = 2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(18, 20, 22, 23).forEach { h ->
                                    val sel = draft.proactiveConfig.activeEnd == h
                                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) draft.color.copy(.2f) else NyxColors.Layer2)
                                        .border(0.5.dp, if (sel) draft.color.copy(.5f) else NyxColors.BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { draft = draft.copy(proactiveConfig = draft.proactiveConfig.copy(activeEnd = h)) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text("$h", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                            color = if (sel) draft.color else NyxColors.TextDim)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Interval
                    Text("发送间隔", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim, modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60 to "1小时", 120 to "2小时", 240 to "4小时", 480 to "8小时").forEach { (mins, label) ->
                            val sel = draft.proactiveConfig.minIntervalMinutes == mins
                            Box(Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) draft.color.copy(.2f) else NyxColors.Layer2)
                                .border(0.5.dp, if (sel) draft.color.copy(.5f) else NyxColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    draft = draft.copy(proactiveConfig = draft.proactiveConfig.copy(
                                        minIntervalMinutes = mins,
                                        maxIntervalMinutes = mins * 2
                                    ))
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    color = if (sel) draft.color else NyxColors.TextDim)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    NyxField(
                        label = "自定义 Prompt（留空则角色自由发挥）",
                        value = draft.proactiveConfig.customPrompt,
                        minLines = 2,
                        placeholder = "例：从角色视角发一条想念对方的消息，不超过40字"
                    ) {
                        draft = draft.copy(proactiveConfig = draft.proactiveConfig.copy(customPrompt = it))
                    }
                }

                // ─── 手动记忆 ───────────────────────────────────────────────
                Spacer(Modifier.height(14.dp))
                SectionLabel("手动记忆")
                Text(
                    "直接写入 · 权重最高（5）· 角色会自然融入对话",
                    fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                    color = NyxColors.TextDim, modifier = Modifier.padding(bottom = 8.dp)
                )

                var newMemText by remember { mutableStateOf("") }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    NyxField(
                        label    = "内容",
                        value    = newMemText,
                        modifier = Modifier.weight(1f),
                        minLines = 2,
                        placeholder = "她知道我不喜欢被催…"
                    ) { newMemText = it }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (newMemText.isNotBlank()) draft.color.copy(0.22f)
                                else NyxColors.Layer3
                            )
                            .border(
                                0.5.dp,
                                if (newMemText.isNotBlank()) draft.color.copy(0.45f)
                                else NyxColors.BorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (newMemText.isNotBlank()) {
                                    onAddMemory(newMemText.trim())
                                    newMemText = ""
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "写入",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (newMemText.isNotBlank()) draft.color else NyxColors.TextDim
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(NyxColors.Accent.copy(0.25f), NyxColors.AccentSoft),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("保存修改", fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.8.sp)
                }
            }
        }
    }
}

@Composable fun SectionLabel(text: String) {
    Row(
        Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left accent bar with glow
        Box(
            Modifier
                .width(2.dp).height(14.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(NyxColors.AccentSoft, NyxColors.Accent.copy(0.3f))
                        )
                    )
                    // Glow bloom
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(NyxColors.Accent.copy(0.3f), Color.Transparent)
                        ),
                        size = size.copy(width = size.width * 4)
                    )
                }
        )
        Text(text, fontSize = 10.sp, fontFamily = CinzelFamily, color = NyxColors.AccentSoft,
            letterSpacing = 1.5.sp)
    }
}

@Composable
fun AddCharacterForm(onAdd: (NyxCharacter) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var initials by remember { mutableStateOf("") }
    var traits by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(NyxColors.Layer1)
            .border(0.5.dp, NyxColors.BorderHi, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("新建角色", fontSize = 11.sp, fontFamily = CinzelFamily, color = NyxColors.Accent, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NyxField("名字", name, Modifier.weight(1f)) { name = it }
            NyxField("缩写", initials, Modifier.width(52.dp)) { initials = it.take(2) }
        }
        NyxField("性格特质", traits, minLines = 2, placeholder = "冷静、直接、话少…") { traits = it }
        NyxField("说话方式", style, minLines = 2, placeholder = "短句。不解释。回答前反问…") { style = it }
        NyxField("背景", background, minLines = 2, placeholder = "她是谁，什么驱动她…") { background = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (name.isNotBlank()) onAdd(NyxCharacter(
                    id = UUID.randomUUID().toString(), name = name.trim(),
                    initials = initials.ifBlank { name.take(1) },
                    colorArgb = 0xFF9D6FFF, traits = traits, style = style, background = background,
                    mood = "neutral", isActive = true
                ))
            }, Modifier.weight(2f), colors = ButtonDefaults.buttonColors(NyxColors.Accent.copy(0.25f), NyxColors.AccentSoft),
                shape = RoundedCornerShape(10.dp)) { Text("创建", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            OutlinedButton(onClick = onCancel, Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NyxColors.TextDim),
                border = BorderStroke(0.5.dp, NyxColors.BorderSubtle)) {
                Text("取消", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun NyxField(label: String, value: String, modifier: Modifier = Modifier,
             minLines: Int = 1, placeholder: String = "", onChange: (String) -> Unit) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim,
            letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
        TextField(value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NyxColors.Layer3)
                .border(0.5.dp, NyxColors.BorderSubtle, RoundedCornerShape(10.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = NyxColors.Layer3,
                unfocusedContainerColor = NyxColors.Layer3,
                focusedIndicatorColor   = NyxColors.Accent.copy(0.6f),
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor        = NyxColors.TextPrimary,
                unfocusedTextColor      = NyxColors.TextSecond,
                focusedPlaceholderColor = NyxColors.TextDim,
                unfocusedPlaceholderColor = NyxColors.TextDim,
                cursorColor             = NyxColors.Accent
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = CrimsonProFamily, lineHeight = 20.sp),
            placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder, fontSize = 13.sp,
                fontFamily = CrimsonProFamily, color = NyxColors.TextDim) }} else null,
            minLines = minLines, maxLines = if (minLines > 1) minLines + 3 else 1
        )
    }
}

@Composable
fun Chip(text: String, color: Color, borderColor: Color, bgColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(10.dp))
            .drawBehind {
                // Subtle top edge highlight for depth
                drawLine(
                    color = Color.White.copy(0.06f),
                    start = Offset(4.dp.toPx(), 0f), end = Offset(size.width - 4.dp.toPx(), 0f),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = color)
    }
}

@Composable fun AddButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, NyxColors.BorderSubtle, Color.Transparent)
                    )
                )
            }
            .border(
                0.5.dp,
                Brush.horizontalGradient(listOf(Color.Transparent, NyxColors.BorderMid, Color.Transparent)),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = NyxColors.TextDim, letterSpacing = 0.5.sp)
    }
}

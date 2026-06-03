package com.nyxchat.ui.screens

import com.nyxchat.ui.dialog.AvatarCropDialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextOverflow
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

// ══════════════════════════════════════════════════════════════════════
//  CharactersScreen — 顶层入口，管理格子 ↔ 编辑视图的切换
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CharactersScreen(vm: ChatViewModel) {
    val context              = androidx.compose.ui.platform.LocalContext.current
    val chars                by vm.characters.collectAsState()
    val memories             by vm.memories.collectAsState()
    val effectiveMemsPreview by vm.effectiveMemsPreview.collectAsState()

    var selectedCharId by remember { mutableStateOf<String?>(null) }
    var selectedTab    by remember { mutableStateOf(0) }   // 0=角色  1=世界书
    var sortOrder      by remember { mutableStateOf("name") }
    var showAdd        by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = selectedCharId,
        transitionSpec = {
            if (targetState != null) {
                // 格子 → 编辑：从右侧滑入
                (slideInHorizontally { it } + fadeIn()) togetherWith
                (slideOutHorizontally { -it } + fadeOut())
            } else {
                // 编辑 → 格子：从左侧滑入
                (slideInHorizontally { -it } + fadeIn()) togetherWith
                (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "CharactersNav"
    ) { charId ->
        if (charId == null) {
            Column(Modifier.fillMaxSize()) {
                // ── Tab 栏 ──────────────────────────────────────────────
                CharactersTabBar(
                    selectedTab = selectedTab,
                    onTabChange = { tab ->
                        selectedTab = tab
                        showAdd = false   // 切换 Tab 时关闭添加表单
                    }
                )
                // ── Tab 内容 ────────────────────────────────────────────
                when (selectedTab) {
                    0 -> CharacterGridView(
                        chars        = chars,
                        sortOrder    = sortOrder,
                        onSortChange = { sortOrder = it },
                        onSelectChar = { selectedCharId = it },
                        showAdd      = showAdd,
                        onShowAdd    = { showAdd = true },
                        onAddChar    = { vm.addCharacter(it); showAdd = false },
                        onCancelAdd  = { showAdd = false }
                    )
                    1 -> WorldBookContent(vm)
                }
            }
        } else {
            val char = chars.find { it.id == charId }
            if (char != null) {
                CharacterEditView(
                    char                   = char,
                    charMems               = memories.filter { it.charId == charId },
                    effectiveMems          = effectiveMemsPreview[charId],
                    onBack                 = { selectedCharId = null },
                    onSave                 = { vm.updateCharacter(it); selectedCharId = null },
                    onDelete               = { vm.deleteCharacter(charId); selectedCharId = null },
                    onToggleActive         = { vm.updateCharacter(char.copy(isActive = !char.isActive)) },
                    onDeleteMem            = { vm.deleteMemory(it) },
                    onClearMems            = { vm.clearCharMemories(charId) },
                    onSetAvatar            = { uri -> vm.setCharacterAvatar(charId, uri) },
                    onSetBackground        = { uri -> vm.setCharacterBackground(charId, uri) },
                    onAddMemory            = { text -> vm.addManualMemory(charId, text) },
                    onUpdateMem            = { mem -> vm.updateMemory(mem) },
                    onExport               = { vm.shareCharacter(context, char) },
                    onSaveCroppedAvatar    = { bmp -> vm.saveCroppedAvatar(charId, bmp) },
                    onRequestEffectiveMems = { vm.loadEffectiveMems(charId) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab 栏（角色 / 世界书）
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun CharactersTabBar(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs      = listOf("角色", "世界书")
    val borderMid = LocalNyxColors.current.BorderMid   // 提前提取，drawBehind 非 Composable 上下文
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, borderMid, Color.Transparent)
                    ),
                    start = Offset(0f, size.height), end = Offset(size.width, size.height),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            .background(LocalNyxColors.current.Layer1)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val selected = selectedTab == idx
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) LocalNyxColors.current.Accent.copy(0.15f)
                        else Color.Transparent
                    )
                    .border(
                        0.5.dp,
                        if (selected) LocalNyxColors.current.Accent.copy(0.45f)
                        else LocalNyxColors.current.BorderSubtle,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onTabChange(idx) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 11.sp, fontFamily = CinzelFamily,
                    color = if (selected) LocalNyxColors.current.AccentSoft
                    else LocalNyxColors.current.TextDim,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  格子视图  (selectedCharId == null)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun CharacterGridView(
    chars: List<NyxCharacter>,
    sortOrder: String,
    onSortChange: (String) -> Unit,
    onSelectChar: (String) -> Unit,
    showAdd: Boolean,
    onShowAdd: () -> Unit,
    onAddChar: (NyxCharacter) -> Unit,
    onCancelAdd: () -> Unit
) {
    val sortedChars = remember(chars, sortOrder) {
        when (sortOrder) {
            "active" -> chars.sortedByDescending { it.lastActiveAt }
            else     -> chars.sortedBy { it.name.lowercase() }
        }
    }
    val rows = remember(sortedChars) { sortedChars.chunked(2) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 标题行
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "角色列表", fontSize = 13.sp, fontFamily = CinzelFamily,
                    color = LocalNyxColors.current.AccentSoft, letterSpacing = 2.sp
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalNyxColors.current.Layer1)
                        .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { onSortChange(if (sortOrder == "name") "active" else "name") }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (sortOrder == "name") "按名称 ↑" else "按活跃 ↓",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim
                    )
                }
            }
        }

        // 两列格子
        items(rows, key = { it.first().id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { char ->
                    CharacterGridTile(
                        char     = char,
                        modifier = Modifier.weight(1f),
                        onClick  = { onSelectChar(char.id) }
                    )
                }
                // 奇数个角色时补空
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // 添加按钮 / 表单
        item {
            if (!showAdd) {
                AddButton("+ 添加角色") { onShowAdd() }
            } else {
                AddCharacterForm(onAdd = onAddChar, onCancel = onCancelAdd)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CharacterGridTile(
    char: NyxCharacter,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                // 顶部角色色高光线
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, char.color.copy(0.28f), Color.Transparent)
                    ),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(
                    listOf(LocalNyxColors.current.Layer1.copy(1f), LocalNyxColors.current.Layer1)
                )
            )
            .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        // 角色色细带 — 30dp，干净的垂直渐变，幻想卡牌风格
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .background(Brush.verticalGradient(listOf(char.color.copy(0.28f), char.color.copy(0.03f))))
        ) {
            if (char.hasBackground) {
                AsyncImage(
                    File(char.backgroundPath), null,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop, alpha = 0.22f
                )
            }
        }

        // 头像 + 信息
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Portrait(char, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        char.name, fontSize = 12.sp, fontFamily = CinzelFamily,
                        color = char.color, letterSpacing = 0.3.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (char.occupation.isNotBlank()) {
                        Text(
                            char.occupation, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 情绪指示点
                Box(Modifier.size(5.dp).clip(CircleShape).background(moodColor(char.mood)))
                Text(
                    moodLabel(char.mood), fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, color = moodColor(char.mood)
                )
                Spacer(Modifier.weight(1f))
                // 角色色细标签（幻想卡牌风格，替代通用绿色"启"）
                Text(
                    if (char.isActive) "启" else "停",
                    fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    color = if (char.isActive) char.color else LocalNyxColors.current.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(
                            0.5.dp,
                            if (char.isActive) char.color.copy(0.5f) else LocalNyxColors.current.BorderSubtle,
                            RoundedCornerShape(3.dp)
                        )
                        .background(
                            if (char.isActive) char.color.copy(0.12f)
                            else LocalNyxColors.current.Layer3
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  全屏编辑视图  (selectedCharId != null)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun CharacterEditView(
    char: NyxCharacter,
    charMems: List<NyxMemory>,
    effectiveMems: List<NyxMemory>?,
    onBack: () -> Unit,
    onSave: (NyxCharacter) -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onDeleteMem: (String) -> Unit,
    onClearMems: () -> Unit,
    onSetAvatar: (android.net.Uri) -> Unit,
    onSetBackground: (android.net.Uri) -> Unit,
    onAddMemory: (String) -> Unit,
    onUpdateMem: (NyxMemory) -> Unit,
    onExport: () -> Unit,
    onSaveCroppedAvatar: (android.graphics.Bitmap) -> Unit,
    onRequestEffectiveMems: () -> Unit
) {
    var draft              by remember(char.id) { mutableStateOf(char) }
    var showMems           by remember { mutableStateOf(false) }
    var showEffectiveMems  by remember { mutableStateOf(false) }
    var pendingAvatarUri   by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingAvatarUri = it }
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onSetBackground(it) }
    }

    pendingAvatarUri?.let { uri ->
        AvatarCropDialog(
            uri       = uri,
            context   = context,
            charName  = char.name,
            onConfirm = { bmp -> onSaveCroppedAvatar(bmp); pendingAvatarUri = null },
            onDismiss = { pendingAvatarUri = null }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {

        // ── Banner + 顶栏 ─────────────────────────────────────────────
        item {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(80.dp)
                        .background(Brush.horizontalGradient(listOf(char.color.copy(0.15f), char.color.copy(0.05f))))
                ) {
                    if (char.hasBackground) {
                        AsyncImage(File(char.backgroundPath), null,
                            Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
                    }
                    Box(
                        Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)).background(LocalNyxColors.current.Layer4.copy(alpha = 0.70f))
                            .clickable { onBack() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("← 返回", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(0.8f))
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)).background(LocalNyxColors.current.Layer4.copy(alpha = 0.70f))
                            .clickable { bgLauncher.launch("image/*") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("换背景", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(0.7f))
                    }
                }

                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(LocalNyxColors.current.Layer2, LocalNyxColors.current.Layer1)
                            )
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 头像（点击换图）
                    Box(Modifier.clickable { avatarLauncher.launch("image/*") }) {
                        Portrait(draft, 46.dp)
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .size(16.dp).clip(CircleShape)
                                .background(LocalNyxColors.current.Layer3)
                                .border(1.dp, LocalNyxColors.current.BorderMid, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✎", fontSize = 7.sp, color = LocalNyxColors.current.TextSecond)
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(char.name, fontSize = 15.sp, fontFamily = CinzelFamily,
                            color = char.color, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(moodLabel(char.mood), fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, color = moodColor(char.mood))
                            if (char.age.isNotBlank())
                                Text("${char.age}岁", fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.TextDim)
                            if (char.height.isNotBlank())
                                Text(char.height, fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.TextDim)
                        }
                        if (char.occupation.isNotBlank())
                            Text(char.occupation, fontSize = 10.sp, fontFamily = CrimsonProFamily,
                                color = LocalNyxColors.current.TextDim)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (charMems.isNotEmpty()) {
                                Chip("◉ ${charMems.size}", char.color,
                                    char.color.copy(.25f), char.color.copy(.08f)) {
                                    showMems = !showMems
                                }
                            }
                            Chip(
                                if (char.isActive) "启用" else "停用",
                                if (char.isActive) LocalNyxColors.current.Success else LocalNyxColors.current.TextDim,
                                if (char.isActive) LocalNyxColors.current.Success.copy(.3f) else LocalNyxColors.current.BorderSubtle,
                                if (char.isActive) LocalNyxColors.current.Success.copy(.08f) else LocalNyxColors.current.Layer3,
                                onToggleActive
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Chip("导出", LocalNyxColors.current.AccentSoft,
                                LocalNyxColors.current.Accent.copy(.3f),
                                LocalNyxColors.current.Accent.copy(.08f), onExport)
                            Chip("删", LocalNyxColors.current.Error,
                                LocalNyxColors.current.Error.copy(.3f),
                                LocalNyxColors.current.Error.copy(.08f), onDelete)
                        }
                    }
                }
            }
        }

        // ── 记忆碎片面板 ──────────────────────────────────────────────
        if (showMems && charMems.isNotEmpty()) {
            item {
                HorizontalDivider(color = LocalNyxColors.current.BorderSubtle)
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("记忆碎片", fontSize = 10.sp, fontFamily = CinzelFamily,
                            color = char.color, letterSpacing = 1.sp)
                        Text("全清", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.Error,
                            modifier = Modifier
                                .clickable { onClearMems(); showMems = false }
                                .padding(4.dp))
                    }
                    SectionLabel("记忆碎片")
                    charMems.forEach { mem ->
                        MemoryItem(
                            mem      = mem,
                            charColor = char.color,
                            onDelete  = { onDeleteMem(mem.id) },
                            onUpdate  = { updated -> onUpdateMem(updated) }
                        )
                    }
                }
            }
        }

        // ── 当前有效记忆预览 ──────────────────────────────────────────
        item {
            HorizontalDivider(color = LocalNyxColors.current.BorderSubtle)
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
                Text("当前有效记忆", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim)
                Text(if (showEffectiveMems) "▲" else "▽", fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim.copy(0.6f))
            }
            if (showEffectiveMems) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    when {
                        effectiveMems == null ->
                            Text("加载中…", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim)
                        effectiveMems.isEmpty() ->
                            Text("暂无有效记忆", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim)
                        else -> {
                            effectiveMems.forEachIndexed { idx, mem ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top) {
                                    Text("${idx + 1}.", fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = LocalNyxColors.current.TextDim.copy(0.5f),
                                        modifier = Modifier.padding(top = 3.dp))
                                    Text(mem.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                        color = LocalNyxColors.current.TextPrimary.copy(0.85f),
                                        lineHeight = 18.sp, modifier = Modifier.weight(1f))
                                }
                            }
                            Text(
                                "按衰减权重降序排列 · 实际对话会注入前 ${effectiveMems.size} 条",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim.copy(0.5f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 各编辑 Section ────────────────────────────────────────────
        item {
            HorizontalDivider(color = LocalNyxColors.current.BorderSubtle)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                BasicInfoSection(draft = draft, onDraftChange = { draft = it })
                PersonalitySection(draft = draft, onDraftChange = { draft = it })
                CoreLayerSection(draft = draft, onDraftChange = { draft = it })
                BehaviorSection(
                    draft         = draft,
                    onDraftChange = { draft = it },
                    onAddMemory   = onAddMemory
                )
                VoiceSection(draft = draft, onDraftChange = { draft = it })

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick  = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = LocalNyxColors.current.Accent.copy(0.25f),
                        contentColor   = LocalNyxColors.current.AccentSoft
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存修改", fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, letterSpacing = 0.8.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  编辑 Section 组件
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun BasicInfoSection(draft: NyxCharacter, onDraftChange: (NyxCharacter) -> Unit) {
    SectionLabel("基本信息")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NyxField("名字", draft.name, Modifier.weight(1f)) { onDraftChange(draft.copy(name = it)) }
        NyxField("缩写", draft.initials, Modifier.width(52.dp)) { onDraftChange(draft.copy(initials = it.take(2))) }
    }
    Spacer(Modifier.height(8.dp))

    // 颜色选择
    Column {
        Text("角色颜色", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CHARACTER_COLORS) { (argb, _) ->
                val c = Color(argb)
                val selected = draft.colorArgb == argb
                Box(
                    Modifier
                        .size(if (selected) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (selected) 2.5.dp else 0.dp,
                            Color.White.copy(if (selected) 0.8f else 0f), CircleShape)
                        .clickable { onDraftChange(draft.copy(colorArgb = argb)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Text("✓", fontSize = 12.sp, color = Color.White,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NyxField("年龄", draft.age, Modifier.weight(1f), placeholder = "24") {
            onDraftChange(draft.copy(age = it))
        }
        NyxField("身高", draft.height, Modifier.weight(1f), placeholder = "170 cm") {
            onDraftChange(draft.copy(height = it))
        }
        NyxField("体重", draft.weight, Modifier.weight(1f), placeholder = "56 kg") {
            onDraftChange(draft.copy(weight = it))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NyxField("国籍/种族", draft.nationality, Modifier.weight(1f)) {
            onDraftChange(draft.copy(nationality = it))
        }
        NyxField("职业", draft.occupation, Modifier.weight(1f)) {
            onDraftChange(draft.copy(occupation = it))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NyxField(
            label = "生日（MM-dd）", value = draft.birthday,
            modifier = Modifier.weight(1f), placeholder = "01-25"
        ) { v ->
            val cleaned = v.filter { it.isDigit() || it == '-' }.take(5)
            onDraftChange(draft.copy(birthday = cleaned))
        }
        Spacer(Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    NyxField("外貌描述", draft.appearance, minLines = 2,
        placeholder = "发色、眼睛、体型、气质、着装风格…") {
        onDraftChange(draft.copy(appearance = it))
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun PersonalitySection(draft: NyxCharacter, onDraftChange: (NyxCharacter) -> Unit) {
    SectionLabel("性格与语言")
    NyxField("性格特质", draft.traits, minLines = 2) { onDraftChange(draft.copy(traits = it)) }
    Spacer(Modifier.height(8.dp))
    NyxField("说话方式（硬性规则）", draft.style, minLines = 3) { onDraftChange(draft.copy(style = it)) }
    Spacer(Modifier.height(8.dp))
    NyxField("背景与内驱力", draft.background, minLines = 2) { onDraftChange(draft.copy(background = it)) }

    Spacer(Modifier.height(14.dp))
    SectionLabel("关系与心理")

    val presetTypes = listOf("同学", "同事", "主仆", "恋人", "家人")
    var showCustomRelInput by remember(draft.id) {
        mutableStateOf(draft.relationType.isNotBlank() && draft.relationType !in presetTypes)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("与你的关系类型", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presetTypes.forEach { preset ->
                val isSelected = draft.relationType == preset
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) draft.color.copy(0.18f)
                            else LocalNyxColors.current.Layer2
                        )
                        .border(0.5.dp,
                            if (isSelected) draft.color.copy(0.6f)
                            else LocalNyxColors.current.BorderSubtle,
                            RoundedCornerShape(12.dp))
                        .clickable {
                            onDraftChange(draft.copy(relationType = if (isSelected) "" else preset))
                            showCustomRelInput = false
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(preset, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = if (isSelected) draft.color else LocalNyxColors.current.TextDim)
                }
            }
            // 自定义 Chip
            val isCustomSelected = draft.relationType.isNotBlank() && draft.relationType !in presetTypes
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCustomSelected) LocalNyxColors.current.Accent.copy(0.18f)
                        else LocalNyxColors.current.Layer2
                    )
                    .border(0.5.dp,
                        if (isCustomSelected) LocalNyxColors.current.Accent.copy(0.5f)
                        else LocalNyxColors.current.BorderSubtle,
                        RoundedCornerShape(12.dp))
                    .clickable { showCustomRelInput = !showCustomRelInput }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    if (isCustomSelected) draft.relationType else "自定义…",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = if (isCustomSelected) LocalNyxColors.current.AccentSoft
                    else LocalNyxColors.current.TextDim
                )
            }
        }
        if (showCustomRelInput) {
            NyxField(
                label = "自定义关系类型",
                value = if (draft.relationType !in presetTypes) draft.relationType else "",
                placeholder = "例：青梅竹马、前女友…"
            ) { onDraftChange(draft.copy(relationType = it)) }
        }
    }

    Spacer(Modifier.height(8.dp))
    NyxField("与其他角色的关系", draft.relationships, minLines = 2,
        placeholder = "与露娜：…") { onDraftChange(draft.copy(relationships = it)) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NyxField("喜好", draft.likes, Modifier.weight(1f), minLines = 2) {
            onDraftChange(draft.copy(likes = it))
        }
        NyxField("厌恶", draft.dislikes, Modifier.weight(1f), minLines = 2) {
            onDraftChange(draft.copy(dislikes = it))
        }
    }
    Spacer(Modifier.height(8.dp))
    NyxField("秘密 / 隐藏面", draft.secrets, minLines = 2,
        placeholder = "只有你知道…") { onDraftChange(draft.copy(secrets = it)) }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun CoreLayerSection(draft: NyxCharacter, onDraftChange: (NyxCharacter) -> Unit) {
    SectionLabel("角色内核（AI可见，用户不可见）")

    NyxField(
        "核心创伤", draft.coreWound, minLines = 2,
        placeholder = "曾经付出过全部，被彻底辜负。此后不再轻易动心。"
    ) { onDraftChange(draft.copy(coreWound = it)) }

    NyxField(
        "核心渴望", draft.coreDesire, minLines = 2,
        placeholder = "被一个人完全接住，不需要交换，不需要表演。"
    ) { onDraftChange(draft.copy(coreDesire = it)) }

    NyxField(
        "面具何时碎裂（触发条件）", draft.maskTrigger, minLines = 2,
        placeholder = "对方第一次让她感到真正的安全；或她突然意识到自己已经在乎了。"
    ) { onDraftChange(draft.copy(maskTrigger = it)) }

    Spacer(Modifier.height(8.dp))
    NyxField(
        "情境反应规则（不同触发情境→对应行为）", draft.situationRules, minLines = 4,
        placeholder = "感知到他与另一人亲密时：不质问，只对他更温柔，接触频率提升。\n连续两次未发生时：情欲积压，第三次主动性远超平时。\n察觉另一女性有动作时：对他更体贴，让他陷进去无法自拔。"
    ) { onDraftChange(draft.copy(situationRules = it)) }

    Spacer(Modifier.height(8.dp))
    NyxField(
        "有心事时的外显信号（行为细节）", draft.deviationSignals, minLines = 3,
        placeholder = "反复整理已经干净的东西。\n出诊回来直接去厨房，不先找人。\n水声停了很久，才把盘子放上沥水架。"
    ) { onDraftChange(draft.copy(deviationSignals = it)) }

    Spacer(Modifier.height(8.dp))
    NyxField(
        "私下真实面目（面具碎裂后）", draft.privatePersona, minLines = 2,
        placeholder = "情感极度浓烈，像最纯粹的孩子，没有防御，也没有理智。"
    ) { onDraftChange(draft.copy(privatePersona = it)) }

    NyxField(
        "私下说话方式（面具碎裂后）", draft.privateStyle, minLines = 3,
        placeholder = "语气突然软下来。说话开始没有逻辑。可能哑口无言，也可能一下子说很多。"
    ) { onDraftChange(draft.copy(privateStyle = it)) }

    NyxField(
        "私下对话示例（破防/激活状态）", draft.privateExamples, minLines = 4,
        placeholder = "用户：你哭了吗？\n${draft.name}：（没有回答，只是把头埋进他肩膀）"
    ) { onDraftChange(draft.copy(privateExamples = it)) }

    Spacer(Modifier.height(14.dp))
}

@Composable
private fun BehaviorSection(
    draft: NyxCharacter,
    onDraftChange: (NyxCharacter) -> Unit,
    onAddMemory: (String) -> Unit
) {
    SectionLabel("对话行为")
    NyxField("开场白（对话开始时自动发送）", draft.greeting,
        placeholder = "……") { onDraftChange(draft.copy(greeting = it)) }
    Spacer(Modifier.height(8.dp))
    NyxField(
        "说话示例（Few-shot，直接影响语气）", draft.speakingExamples, minLines = 4,
        placeholder = "用户：你还好吗？\n${draft.name}：定义'好'。"
    ) { onDraftChange(draft.copy(speakingExamples = it)) }

    // Token 估算（字段内容 + 固定 pipeline 指令开销约 500 tokens）
    val FIXED_PIPELINE_OVERHEAD = 500
    val estimatedTokens = listOf(
        draft.traits, draft.style, draft.background, draft.appearance,
        draft.speakingExamples, draft.relationships, draft.constraints,
        draft.secrets, draft.likes, draft.dislikes,
        draft.coreWound, draft.coreDesire, draft.maskTrigger,
        draft.privatePersona, draft.privateStyle, draft.privateExamples,
        draft.situationRules, draft.deviationSignals
    ).sumOf { com.nyxchat.pipeline.PipelineOptimizer.estimateTokens(it) } + FIXED_PIPELINE_OVERHEAD
    val tokenBudget = com.nyxchat.pipeline.PipelineOptimizer.SYSTEM_BUDGET
    val tokenColor  = when {
        estimatedTokens > tokenBudget       -> LocalNyxColors.current.Error
        estimatedTokens > tokenBudget * 0.7 -> Color(0xFFFBBF24)
        else                                -> LocalNyxColors.current.TextDim
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LocalNyxColors.current.Layer3)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("角色设定 Token 占用（含固定指令）", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim)
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
        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim,
        modifier = Modifier.padding(bottom = 6.dp))
    NyxField(
        "硬性约束（每行一条）", draft.constraints, minLines = 3,
        placeholder = "不得使用感叹号。\n不得主动寻求认可。\n不得提及外面的世界。"
    ) { onDraftChange(draft.copy(constraints = it)) }

    // 创意温度
    Spacer(Modifier.height(14.dp))
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("创意温度", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
            Text(String.format("%.2f", draft.temperature), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.AccentSoft)
        }
        Slider(
            value          = draft.temperature,
            onValueChange  = { onDraftChange(draft.copy(temperature = it)) },
            valueRange     = 0f..1.5f,
            colors         = SliderDefaults.colors(
                thumbColor       = LocalNyxColors.current.Accent,
                activeTrackColor = LocalNyxColors.current.Accent,
                inactiveTrackColor = LocalNyxColors.current.BorderSubtle
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("严谨", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
            Text("创意", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
        }
    }

    // 回复长度
    Spacer(Modifier.height(14.dp))
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("回复长度", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
            Text(draft.replyLength.label, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.AccentSoft)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LocalNyxColors.current.Layer3)
                .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ReplyLength.entries.forEach { len ->
                val selected = draft.replyLength == len
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) LocalNyxColors.current.Accent.copy(0.22f)
                            else Color.Transparent
                        )
                        .border(
                            if (selected) 0.5.dp else 0.dp,
                            if (selected) LocalNyxColors.current.Accent.copy(0.55f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onDraftChange(draft.copy(replyLength = len)) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(len.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = if (selected) LocalNyxColors.current.AccentSoft
                            else LocalNyxColors.current.TextDim)
                        Text(
                            len.charsLimit.replace("不超过", "").replace("篇幅自由发挥", "不限"),
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = if (selected) LocalNyxColors.current.Accent.copy(0.75f)
                            else LocalNyxColors.current.TextDim.copy(0.55f)
                        )
                    }
                }
            }
        }
    }

    // 情绪稳定性
    Spacer(Modifier.height(10.dp))
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("情绪稳定性", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
            Text(String.format("%.2f", draft.emotionalStability), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.AccentSoft)
        }
        Slider(
            value         = draft.emotionalStability,
            onValueChange = { onDraftChange(draft.copy(emotionalStability = it)) },
            valueRange    = 0f..1f,
            colors        = SliderDefaults.colors(
                thumbColor         = LocalNyxColors.current.Accent,
                activeTrackColor   = LocalNyxColors.current.Accent,
                inactiveTrackColor = LocalNyxColors.current.BorderSubtle
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("情绪易变", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
            Text("情绪稳定", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
        }
    }

    // 主动消息
    Spacer(Modifier.height(14.dp))
    SectionLabel("主动消息 / 通知")
    Text(
        "开启后角色会在设定的时间段内主动向你发消息（需要系统通知权限）",
        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim,
        lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp)
    )
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LocalNyxColors.current.Layer3)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("启用主动消息", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextPrimary)
            Text("角色会定时给你发通知", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim)
        }
        Switch(
            checked         = draft.proactiveConfig.enabled,
            onCheckedChange = {
                onDraftChange(draft.copy(proactiveConfig = draft.proactiveConfig.copy(enabled = it)))
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor   = draft.color,
                checkedTrackColor   = draft.color.copy(0.3f),
                uncheckedTrackColor = LocalNyxColors.current.BorderSubtle
            )
        )
    }

    if (draft.proactiveConfig.enabled) {
        Spacer(Modifier.height(10.dp))
        Text("活跃时段", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("开始（时）", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(6, 8, 10, 12).forEach { h ->
                        val sel = draft.proactiveConfig.activeStart == h
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) draft.color.copy(.2f) else LocalNyxColors.current.Layer2)
                                .border(0.5.dp,
                                    if (sel) draft.color.copy(.5f) else LocalNyxColors.current.BorderSubtle,
                                    RoundedCornerShape(8.dp))
                                .clickable {
                                    onDraftChange(draft.copy(proactiveConfig = draft.proactiveConfig.copy(activeStart = h)))
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$h", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (sel) draft.color else LocalNyxColors.current.TextDim)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("结束（时）", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(18, 20, 22, 23).forEach { h ->
                        val sel = draft.proactiveConfig.activeEnd == h
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) draft.color.copy(.2f) else LocalNyxColors.current.Layer2)
                                .border(0.5.dp,
                                    if (sel) draft.color.copy(.5f) else LocalNyxColors.current.BorderSubtle,
                                    RoundedCornerShape(8.dp))
                                .clickable {
                                    onDraftChange(draft.copy(proactiveConfig = draft.proactiveConfig.copy(activeEnd = h)))
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$h", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (sel) draft.color else LocalNyxColors.current.TextDim)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("发送间隔", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(60 to "1小时", 120 to "2小时", 240 to "4小时", 480 to "8小时")
                .forEach { (mins, label) ->
                    val sel = draft.proactiveConfig.minIntervalMinutes == mins
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) draft.color.copy(.2f) else LocalNyxColors.current.Layer2)
                            .border(0.5.dp,
                                if (sel) draft.color.copy(.5f) else LocalNyxColors.current.BorderSubtle,
                                RoundedCornerShape(8.dp))
                            .clickable {
                                onDraftChange(draft.copy(proactiveConfig = draft.proactiveConfig.copy(
                                    minIntervalMinutes = mins,
                                    maxIntervalMinutes = mins * 2
                                )))
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = if (sel) draft.color else LocalNyxColors.current.TextDim)
                    }
                }
        }

        Spacer(Modifier.height(10.dp))
        NyxField(
            label = "自定义 Prompt（留空则角色自由发挥）",
            value = draft.proactiveConfig.customPrompt,
            minLines = 2,
            placeholder = "例：从角色视角发一条想念对方的消息，不超过40字"
        ) { onDraftChange(draft.copy(proactiveConfig = draft.proactiveConfig.copy(customPrompt = it))) }
    }

    // 手动记忆
    Spacer(Modifier.height(14.dp))
    SectionLabel("手动记忆")
    Text(
        "直接写入 · 权重最高（5）· 角色会自然融入对话",
        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
        color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 8.dp)
    )
    var newMemText by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        NyxField(
            label = "内容", value = newMemText, modifier = Modifier.weight(1f),
            minLines = 2, placeholder = "她知道我不喜欢被催…"
        ) { newMemText = it }
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (newMemText.isNotBlank()) draft.color.copy(0.22f)
                    else LocalNyxColors.current.Layer3
                )
                .border(0.5.dp,
                    if (newMemText.isNotBlank()) draft.color.copy(0.45f)
                    else LocalNyxColors.current.BorderSubtle,
                    RoundedCornerShape(10.dp))
                .clickable {
                    if (newMemText.isNotBlank()) {
                        onAddMemory(newMemText.trim())
                        newMemText = ""
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("写入", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = if (newMemText.isNotBlank()) draft.color else LocalNyxColors.current.TextDim)
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun VoiceSection(draft: NyxCharacter, onDraftChange: (NyxCharacter) -> Unit) {
    SectionLabel("语音")
    Text(
        "Azure 语音合成声线（需在设置页配置 Azure API 密钥）",
        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim,
        modifier = Modifier.padding(bottom = 6.dp)
    )
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (draft.voiceId == id) draft.color.copy(0.2f)
                        else LocalNyxColors.current.Layer2
                    )
                    .border(0.5.dp,
                        if (draft.voiceId == id) draft.color.copy(0.5f)
                        else LocalNyxColors.current.BorderSubtle,
                        RoundedCornerShape(16.dp))
                    .clickable { onDraftChange(draft.copy(voiceId = id)) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = if (draft.voiceId == id) draft.color else LocalNyxColors.current.TextDim)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

// ══════════════════════════════════════════════════════════════════════
//  公共辅助组件（保持不变）
// ══════════════════════════════════════════════════════════════════════

@Composable
fun SectionLabel(text: String) {
    val accentSoft = LocalNyxColors.current.AccentSoft  // 提前提取，drawBehind 非 Composable 上下文
    val accent     = LocalNyxColors.current.Accent
    Row(
        Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .width(2.dp).height(14.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(accentSoft, accent.copy(0.3f))
                        )
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(accent.copy(0.3f), Color.Transparent)
                        ),
                        size = size.copy(width = size.width * 4)
                    )
                }
        )
        Text(text, fontSize = 10.sp, fontFamily = CinzelFamily,
            color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
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

@Composable
fun AddButton(label: String, onClick: () -> Unit) {
    val borderSubtle = LocalNyxColors.current.BorderSubtle  // 提前提取，drawBehind 非 Composable 上下文
    val borderMid    = LocalNyxColors.current.BorderMid
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, borderSubtle, Color.Transparent)
                    )
                )
            }
            .border(
                0.5.dp,
                Brush.horizontalGradient(
                    listOf(Color.Transparent, borderMid, Color.Transparent)
                ),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
    }
}

@Composable
fun AddCharacterForm(onAdd: (NyxCharacter) -> Unit, onCancel: () -> Unit) {
    var name       by remember { mutableStateOf("") }
    var initials   by remember { mutableStateOf("") }
    var traits     by remember { mutableStateOf("") }
    var style      by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(LocalNyxColors.current.Layer1)
            .border(0.5.dp, LocalNyxColors.current.BorderHi, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("新建角色", fontSize = 11.sp, fontFamily = CinzelFamily,
            color = LocalNyxColors.current.Accent, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NyxField("名字", name, Modifier.weight(1f)) { name = it }
            NyxField("缩写", initials, Modifier.width(52.dp)) { initials = it.take(2) }
        }
        NyxField("性格特质", traits, minLines = 2, placeholder = "冷静、直接、话少…") { traits = it }
        NyxField("说话方式", style, minLines = 2, placeholder = "短句。不解释。回答前反问…") { style = it }
        NyxField("背景", background, minLines = 2, placeholder = "她是谁，什么驱动她…") { background = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (name.isNotBlank()) onAdd(NyxCharacter(
                        id = UUID.randomUUID().toString(), name = name.trim(),
                        initials = initials.ifBlank { name.take(1) },
                        colorArgb = 0xFF9D6FFF, traits = traits, style = style,
                        background = background, mood = "neutral", isActive = true
                    ))
                },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalNyxColors.current.Accent.copy(0.25f),
                    contentColor   = LocalNyxColors.current.AccentSoft
                ),
                shape = RoundedCornerShape(10.dp)
            ) { Text("创建", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            OutlinedButton(
                onClick  = onCancel,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = LocalNyxColors.current.TextDim),
                border   = BorderStroke(0.5.dp, LocalNyxColors.current.BorderSubtle)
            ) {
                Text("取消", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun NyxField(
    label: String, value: String, modifier: Modifier = Modifier,
    minLines: Int = 1, placeholder: String = "", onChange: (String) -> Unit
) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = LocalNyxColors.current.TextDim,
            letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
        TextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LocalNyxColors.current.Layer3)
                .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor     = LocalNyxColors.current.Layer3,
                unfocusedContainerColor   = LocalNyxColors.current.Layer3,
                focusedIndicatorColor     = LocalNyxColors.current.Accent.copy(0.6f),
                unfocusedIndicatorColor   = Color.Transparent,
                focusedTextColor          = LocalNyxColors.current.TextPrimary,
                unfocusedTextColor        = LocalNyxColors.current.TextSecond,
                focusedPlaceholderColor   = LocalNyxColors.current.TextDim,
                unfocusedPlaceholderColor = LocalNyxColors.current.TextDim,
                cursorColor               = LocalNyxColors.current.Accent
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 13.sp, fontFamily = CrimsonProFamily, lineHeight = 20.sp
            ),
            placeholder = if (placeholder.isNotBlank()) {{
                Text(placeholder, fontSize = 13.sp, fontFamily = CrimsonProFamily,
                    color = LocalNyxColors.current.TextDim)
            }} else null,
            minLines = minLines,
            maxLines = if (minLines > 1) minLines + 3 else 1
        )
    }
}

/**
 * 单条记忆碎片行
 */
@Composable
private fun MemoryItem(
    mem: NyxMemory,
    charColor: Color,
    onDelete: () -> Unit,
    onUpdate: (NyxMemory) -> Unit
) {
    var editingOpen    by remember(mem.id) { mutableStateOf(false) }
    var editContent    by remember(mem.id) { mutableStateOf(mem.content) }
    var editImportance by remember(mem.id) { mutableStateOf(mem.importance.toFloat()) }
    var editType       by remember(mem.id) { mutableStateOf(mem.type) }

    Column {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 4.dp)) {
                repeat(5) { i ->
                    Box(Modifier.size(4.dp).clip(CircleShape)
                        .background(if (i < mem.importance) charColor else LocalNyxColors.current.Layer3))
                }
            }
            Text(mem.content, fontSize = 13.sp, fontFamily = CrimsonProFamily,
                color = LocalNyxColors.current.TextPrimary,
                modifier = Modifier.weight(1f), lineHeight = 18.sp)
            if (mem.type != "Public") {
                val typeLabel = when (mem.type) {
                    "Private"         -> "私"
                    "HiddenInference" -> "猜"
                    "Sensitive"       -> "敏"
                    else              -> mem.type.take(1)
                }
                Text(typeLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = charColor.copy(0.8f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(charColor.copy(0.14f))
                        .padding(horizontal = 5.dp, vertical = 2.dp))
            }
            Text("✎", color = LocalNyxColors.current.TextDim.copy(.5f), fontSize = 12.sp,
                modifier = Modifier.clickable { editingOpen = !editingOpen }.padding(2.dp))
            Text("×", color = LocalNyxColors.current.Error.copy(.4f), fontSize = 14.sp,
                modifier = Modifier.clickable { onDelete() }.padding(2.dp))
        }
        if (editingOpen) {
            Column(Modifier.padding(start = 14.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = editContent, onValueChange = { editContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = charColor.copy(0.5f),
                        unfocusedBorderColor = LocalNyxColors.current.BorderSubtle
                    ),
                    minLines = 2, maxLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重要度", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim)
                    Slider(value = editImportance, onValueChange = { editImportance = it },
                        valueRange = 1f..5f, steps = 3, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor       = charColor,
                            activeTrackColor = charColor.copy(0.4f)
                        ))
                    Text(editImportance.toInt().toString(), fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, color = charColor)
                }
                val memTypeOptions = listOf(
                    "Public" to "公开", "Private" to "私有",
                    "HiddenInference" to "猜测", "Sensitive" to "敏感"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    memTypeOptions.forEach { (typeKey, typeLabel) ->
                        val selected = editType == typeKey
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (selected) charColor.copy(0.22f) else LocalNyxColors.current.Layer2
                                )
                                .clickable { editType = typeKey }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(typeLabel, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = if (selected) charColor else LocalNyxColors.current.TextDim)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("保存", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = charColor,
                        modifier = Modifier.clickable {
                            onUpdate(mem.copy(content = editContent,
                                importance = editImportance.toInt(), type = editType))
                            editingOpen = false
                        }.padding(4.dp))
                    Text("取消", fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim,
                        modifier = Modifier.clickable { editingOpen = false }.padding(4.dp))
                }
            }
        }
    }
}

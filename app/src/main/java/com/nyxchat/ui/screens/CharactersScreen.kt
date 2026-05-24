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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxchat.data.*
import com.nyxchat.ui.components.Portrait
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel
import java.util.UUID

@Composable
fun CharactersScreen(vm: ChatViewModel) {
    val chars by vm.characters.collectAsState()
    var editingId by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "CHARACTERS",
                fontSize = 13.sp, fontFamily = CinzelFamily,
                color = NyxColors.AccentSoft, letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(chars, key = { it.id }) { char ->
            CharacterCard(
                char = char,
                isExpanded = editingId == char.id,
                onToggleEdit = { editingId = if (editingId == char.id) null else char.id },
                onSave = { updated -> vm.updateCharacter(updated); editingId = null },
                onDelete = { vm.deleteCharacter(char.id) },
                onToggleActive = { vm.updateCharacter(char.copy(isActive = !char.isActive)) }
            )
        }

        item {
            if (!showAdd) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0x40784ED0), RoundedCornerShape(14.dp))
                        .clickable { showAdd = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ 添加角色", fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim, letterSpacing = 0.5.sp
                    )
                }
            } else {
                AddCharacterForm(
                    onAdd = { newChar -> vm.addCharacter(newChar); showAdd = false },
                    onCancel = { showAdd = false }
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun CharacterCard(
    char: NyxCharacter,
    isExpanded: Boolean,
    onToggleEdit: () -> Unit,
    onSave: (NyxCharacter) -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    var draft by remember(isExpanded) { mutableStateOf(char) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (isExpanded) NyxColors.BorderHi else NyxColors.Border, RoundedCornerShape(14.dp))
            .background(NyxColors.Surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Portrait(char = if (isExpanded) draft else char, size = 42.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    char.name, fontSize = 14.sp,
                    fontFamily = CinzelFamily, color = char.color,
                    letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    moodLabel(char.mood), fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = moodColor(char.mood)
                )
            }

            // Active toggle
            Chip(
                text = if (char.isActive) "启用" else "停用",
                color = if (char.isActive) NyxColors.Success else NyxColors.TextDim,
                borderColor = if (char.isActive) Color(0x5934D399) else Color(0x1AFFFFFF),
                bgColor = if (char.isActive) Color(0x0D34D399) else Color(0x0DFFFFFF),
                onClick = onToggleActive
            )

            Chip(text = if (isExpanded) "取消" else "编辑",
                color = NyxColors.TextDim, borderColor = Color(0x1AFFFFFF),
                bgColor = Color(0x0DFFFFFF), onClick = onToggleEdit)

            Chip(text = "删", color = NyxColors.Error,
                borderColor = Color(0x33F87171), bgColor = Color(0x14F87171),
                onClick = onDelete)
        }

        // Edit form
        if (isExpanded) {
            HorizontalDivider(color = NyxColors.Border)
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NyxField(label = "名字", value = draft.name, modifier = Modifier.weight(1f)) {
                        draft = draft.copy(name = it)
                    }
                    NyxField(label = "缩写", value = draft.initials, modifier = Modifier.width(60.dp)) {
                        draft = draft.copy(initials = it.take(2))
                    }
                }
                NyxField(label = "性格特质", value = draft.traits, minLines = 2) {
                    draft = draft.copy(traits = it)
                }
                NyxField(label = "说话方式（越具体越好）", value = draft.style, minLines = 3) {
                    draft = draft.copy(style = it)
                }
                NyxField(label = "背景故事", value = draft.background, minLines = 2) {
                    draft = draft.copy(background = it)
                }
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x509D6FFF),
                        contentColor = NyxColors.AccentSoft
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("保存修改", fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.8.sp)
                }
            }
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, NyxColors.BorderHi, RoundedCornerShape(14.dp))
            .background(NyxColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "新建角色", fontSize = 11.sp,
            fontFamily = CinzelFamily, color = NyxColors.Accent,
            letterSpacing = 1.5.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NyxField("名字", name, Modifier.weight(1f)) { name = it }
            NyxField("缩写", initials, Modifier.width(60.dp)) { initials = it.take(2) }
        }
        NyxField("性格特质", traits, minLines = 2, placeholder = "冷静、直接、话少…") { traits = it }
        NyxField("说话方式", style, minLines = 2, placeholder = "短句。不解释。回答前反问…") { style = it }
        NyxField("背景", background, minLines = 2, placeholder = "她是谁，什么驱动她…") { background = it }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(NyxCharacter(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            initials = initials.ifBlank { name.take(1) },
                            colorArgb = 0xFF9D6FFF,
                            traits = traits,
                            style = style,
                            background = background,
                            mood = "neutral",
                            isActive = true
                        ))
                    }
                },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x509D6FFF), contentColor = NyxColors.AccentSoft
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("创建", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NyxColors.TextDim),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
            ) {
                Text("取消", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun NyxField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    placeholder: String = "",
    onChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            color = NyxColors.TextDim, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, NyxColors.Border, RoundedCornerShape(8.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x0AFFFFFF),
                unfocusedContainerColor = Color(0x06FFFFFF),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = NyxColors.TextPrimary,
                unfocusedTextColor = NyxColors.TextPrimary,
                focusedPlaceholderColor = NyxColors.TextDim,
                unfocusedPlaceholderColor = NyxColors.TextDim
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 13.sp, fontFamily = CrimsonProFamily, lineHeight = 20.sp
            ),
            placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder, fontSize = 13.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextDim) }} else null,
            minLines = minLines,
            maxLines = if (minLines > 1) minLines + 2 else 1
        )
    }
}

@Composable
fun Chip(
    text: String,
    color: Color,
    borderColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = color)
    }
}

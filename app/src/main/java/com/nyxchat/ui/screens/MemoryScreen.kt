package com.nyxchat.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxchat.data.NyxCharacter
import com.nyxchat.data.NyxMemory
import com.nyxchat.ui.components.Portrait
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MemoryScreen(vm: ChatViewModel) {
    val chars    by vm.characters.collectAsState()
    val memories by vm.memories.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    "MEMORY FRAGMENTS",
                    fontSize = 13.sp, fontFamily = CinzelFamily,
                    color = NyxColors.AccentSoft, letterSpacing = 2.sp
                )
                Text(
                    "对话后自动提取 · 注入角色记忆",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = NyxColors.TextDim, letterSpacing = 0.3.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        items(chars) { char ->
            val charMems = memories
                .filter { it.charId == char.id }
                .sortedByDescending { it.importance * 1_000_000L + it.timestamp }

            CharacterMemorySection(
                char = char,
                memories = charMems,
                onDelete = { vm.deleteMemory(it) },
                onClear = { vm.clearCharMemories(char.id) }
            )
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun CharacterMemorySection(
    char: NyxCharacter,
    memories: List<NyxMemory>,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Portrait(char = char, size = 22.dp)
                Text(
                    char.name.uppercase(),
                    fontSize = 11.sp, fontFamily = CinzelFamily,
                    color = char.color, letterSpacing = 1.2.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(char.color.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${memories.size} 条",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = char.color
                    )
                }
            }

            if (memories.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14F87171))
                        .border(1.dp, Color(0x33F87171), RoundedCornerShape(8.dp))
                        .clickable { onClear() }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        "全清", fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NyxColors.Error
                    )
                }
            }
        }

        HorizontalDivider(
            color = char.color.copy(alpha = 0.15f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "暂无记忆碎片",
                    fontSize = 13.sp,
                    fontFamily = CrimsonProFamily,
                    color = NyxColors.TextDim,
                    fontStyle = FontStyle.Italic
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                memories.forEach { mem ->
                    MemoryItem(mem = mem, charColor = char.color, onDelete = { onDelete(mem.id) })
                }
            }
        }
    }
}

@Composable
fun MemoryItem(mem: NyxMemory, charColor: Color, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x06FFFFFF))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mem.content,
                fontSize = 14.sp,
                fontFamily = CrimsonProFamily,
                color = NyxColors.TextPrimary,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Importance dots
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < mem.importance) charColor
                                else Color(0x1AFFFFFF)
                            )
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(mem.timestamp)),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NyxColors.TextDim
                )
            }
        }

        Text(
            "×",
            color = NyxColors.Error.copy(alpha = 0.45f),
            fontSize = 16.sp,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(4.dp)
        )
    }
}

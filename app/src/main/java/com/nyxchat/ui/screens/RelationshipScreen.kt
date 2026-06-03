package com.nyxchat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxchat.data.NyxCharacter
import com.nyxchat.data.Relationship
import com.nyxchat.data.USER_PSEUDO_ID
import com.nyxchat.data.newUserCharRelationship
import com.nyxchat.ui.components.Portrait
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel

@Composable
fun RelationshipScreen(vm: ChatViewModel) {
    val chars = vm.characters.collectAsState().value
    val rels  = vm.relationships.collectAsState().value

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ─── 我与角色的关系区 ─────────────────────────────────────────────────
        item {
            Text("我与角色的关系", fontSize = 13.sp, fontFamily = CinzelFamily,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 2.sp)
        }
        val activeChars = chars.filter { it.isActive }
        if (activeChars.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center) {
                    Text("暂无活跃角色",
                        fontSize = 13.sp, fontFamily = CrimsonProFamily, color = LocalNyxColors.current.TextDim)
                }
            }
        }
        items(activeChars.size) { i ->
            val char = activeChars[i]
            val rel = rels.find { it.fromCharId == char.id && it.toCharId == USER_PSEUDO_ID }
                ?: newUserCharRelationship(char.id)
            UserCharCard(char = char, rel = rel, vm = vm)
        }

        // ─── 角色间关系区 ─────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("角色间关系", fontSize = 13.sp, fontFamily = CinzelFamily,
                    color = LocalNyxColors.current.AccentSoft, letterSpacing = 2.sp)
                Text("角色间关系随对话动态更新", fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
            }
        }

        val pairs = buildList {
            for (i in chars.indices) for (j in i + 1 until chars.size) {
                add(chars[i] to chars[j])
            }
        }

        if (pairs.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center) {
                    Text("至少需要两个角色才能显示关系矩阵",
                        fontSize = 13.sp, fontFamily = CrimsonProFamily, color = LocalNyxColors.current.TextDim)
                }
            }
        }

        items(pairs.size) { i ->
            val (a, b) = pairs[i]
            RelationshipCard(a, b, rels, vm)
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun RelationshipCard(a: NyxCharacter, b: NyxCharacter, rels: List<Relationship>, vm: ChatViewModel? = null) {
    val relAtoB = rels.find { it.fromCharId == a.id && it.toCharId == b.id }
        ?: Relationship("${a.id}_${b.id}", a.id, b.id)
    val relBtoA = rels.find { it.fromCharId == b.id && it.toCharId == a.id }
        ?: Relationship("${b.id}_${a.id}", b.id, a.id)

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalNyxColors.current.Layer1)
            .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header — two portraits facing each other
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Portrait(a, 36.dp)
                Text(a.name, fontSize = 13.sp, fontFamily = CinzelFamily, color = a.color, letterSpacing = 0.5.sp)
            }
            Text("⟷", fontSize = 14.sp, color = LocalNyxColors.current.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, fontSize = 13.sp, fontFamily = CinzelFamily, color = b.color, letterSpacing = 0.5.sp)
                Portrait(b, 36.dp)
            }
        }

        // Relationship summary
        if (relAtoB.summary.isNotBlank()) {
            Text(
                "${a.name}对${b.name}：${relAtoB.summary}",
                fontSize = 12.sp, fontFamily = CrimsonProFamily,
                color = LocalNyxColors.current.TextSecond, lineHeight = 18.sp
            )
        }
        if (relBtoA.summary.isNotBlank()) {
            Text(
                "${b.name}对${a.name}：${relBtoA.summary}",
                fontSize = 12.sp, fontFamily = CrimsonProFamily,
                color = LocalNyxColors.current.TextSecond, lineHeight = 18.sp
            )
        }

        // Dimension bars — A→B and B→A
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A→B
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${a.name} → ${b.name}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = a.color.copy(0.7f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RelBar("信任", relAtoB.trust,       LocalNyxColors.current.Success,   "控制角色是否愿意坦白内心、分享秘密")
                RelBar("亲密", relAtoB.affection,   Color(0xFFF472B6),                "情感表达的温柔程度和主动性；也是推进关系阶段的核心指标")
                RelBar("张力", relAtoB.tension,     LocalNyxColors.current.Error,     "高时对话容易产生冲突、敏感激动；低时平稳淡然")
                RelBar("尊重", relAtoB.respect,     LocalNyxColors.current.AccentSoft,"影响措辞礼貌程度，是否在乎对方感受")
                // ── 步骤11：扩展维度显示 ────────────────────────────────────────
                RelBar("依赖", relAtoB.dependency,  Color(0xFFA78BFA),                "高时角色主动联系频率更高、语气更黏人")
                RelBar("嫉妒", relAtoB.jealousy,    Color(0xFFFB923C),                "多角色场景下影响独占性言行和对其他角色的态度")
                RelBar("压抑", relAtoB.suppression, Color(0xFF60A5FA),                "低时角色情绪内敛克制；高时直接表达感受")
            }
            Spacer(Modifier.height(4.dp))
            // B→A
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${b.name} → ${a.name}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = b.color.copy(0.7f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RelBar("信任", relBtoA.trust,       LocalNyxColors.current.Success,   "控制角色是否愿意坦白内心、分享秘密")
                RelBar("亲密", relBtoA.affection,   Color(0xFFF472B6),                "情感表达的温柔程度和主动性；也是推进关系阶段的核心指标")
                RelBar("张力", relBtoA.tension,     LocalNyxColors.current.Error,     "高时对话容易产生冲突、敏感激动；低时平稳淡然")
                RelBar("尊重", relBtoA.respect,     LocalNyxColors.current.AccentSoft,"影响措辞礼貌程度，是否在乎对方感受")
                // ── 步骤11：扩展维度显示 ────────────────────────────────────────
                RelBar("依赖", relBtoA.dependency,  Color(0xFFA78BFA),                "高时角色主动联系频率更高、语气更黏人")
                RelBar("嫉妒", relBtoA.jealousy,    Color(0xFFFB923C),                "多角色场景下影响独占性言行和对其他角色的态度")
                RelBar("压抑", relBtoA.suppression, Color(0xFF60A5FA),                "低时角色情绪内敛克制；高时直接表达感受")
            }
        }

        // Timestamp
        if (relAtoB.updatedAt > 0L) {
            Text(
                "上次更新：${formatRelTime(relAtoB.updatedAt)}",
                fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim
            )
        }

        // ── 手动编辑区 ─────────────────────────────────────────────────
        if (vm != null) {
            var editMode    by remember { mutableStateOf(false) }
            // direction: true = A→B, false = B→A
            var editDirAtoB by remember { mutableStateOf(true) }

            // A→B sliders
            var aToBTrust       by remember(relAtoB.trust)       { mutableStateOf(relAtoB.trust) }
            var aToBAfaction    by remember(relAtoB.affection)   { mutableStateOf(relAtoB.affection) }
            var aToBTension     by remember(relAtoB.tension)     { mutableStateOf(relAtoB.tension) }
            var aToBRespect     by remember(relAtoB.respect)     { mutableStateOf(relAtoB.respect) }
            // 步骤11：扩展维度 A→B
            var aToBDependency  by remember(relAtoB.dependency)  { mutableStateOf(relAtoB.dependency) }
            var aToBJealousy    by remember(relAtoB.jealousy)    { mutableStateOf(relAtoB.jealousy) }
            var aToBSuppression by remember(relAtoB.suppression) { mutableStateOf(relAtoB.suppression) }
            // B→A sliders
            var bToATrust       by remember(relBtoA.trust)       { mutableStateOf(relBtoA.trust) }
            var bToAAffection   by remember(relBtoA.affection)   { mutableStateOf(relBtoA.affection) }
            var bToATension     by remember(relBtoA.tension)     { mutableStateOf(relBtoA.tension) }
            var bToARespect     by remember(relBtoA.respect)     { mutableStateOf(relBtoA.respect) }
            // 步骤11：扩展维度 B→A
            var bToADependency  by remember(relBtoA.dependency)  { mutableStateOf(relBtoA.dependency) }
            var bToAJealousy    by remember(relBtoA.jealousy)    { mutableStateOf(relBtoA.jealousy) }
            var bToASuppression by remember(relBtoA.suppression) { mutableStateOf(relBtoA.suppression) }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (editMode) LocalNyxColors.current.AccentPill else LocalNyxColors.current.Layer2)
                        .border(0.5.dp, if (editMode) LocalNyxColors.current.BorderHi else LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { editMode = !editMode }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (editMode) "收起编辑" else "手动调整",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = if (editMode) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                }
                // Reset resets BOTH directions
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(LocalNyxColors.current.Layer2)
                        .border(0.5.dp, LocalNyxColors.current.Error.copy(0.3f), RoundedCornerShape(8.dp))
                        .clickable {
                            vm.resetRelationship(a.id, b.id)
                            vm.resetRelationship(b.id, a.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("重置", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.Error.copy(0.7f))
                }
            }

            if (editMode) {
                // Direction toggle
                val dirColor = if (editDirAtoB) a.color else b.color
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalNyxColors.current.Layer2)
                        .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(true to "${a.name} → ${b.name}", false to "${b.name} → ${a.name}").forEach { (isAtoB, label) ->
                        val active = editDirAtoB == isAtoB
                        val tColor = if (isAtoB) a.color else b.color
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (active) tColor.copy(0.18f) else Color.Transparent)
                                .clickable { editDirAtoB = isAtoB }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (active) tColor else LocalNyxColors.current.TextDim)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val sliders = if (editDirAtoB) listOf(
                        Triple("信任", aToBTrust,       { v: Float -> aToBTrust = v }),
                        Triple("亲密", aToBAfaction,    { v: Float -> aToBAfaction = v }),
                        Triple("张力", aToBTension,     { v: Float -> aToBTension = v }),
                        Triple("尊重", aToBRespect,     { v: Float -> aToBRespect = v }),
                        // 步骤11：扩展维度滑块
                        Triple("依赖", aToBDependency,  { v: Float -> aToBDependency = v }),
                        Triple("嫉妒", aToBJealousy,    { v: Float -> aToBJealousy = v }),
                        Triple("压抑", aToBSuppression, { v: Float -> aToBSuppression = v })
                    ) else listOf(
                        Triple("信任", bToATrust,       { v: Float -> bToATrust = v }),
                        Triple("亲密", bToAAffection,   { v: Float -> bToAAffection = v }),
                        Triple("张力", bToATension,     { v: Float -> bToATension = v }),
                        Triple("尊重", bToARespect,     { v: Float -> bToARespect = v }),
                        // 步骤11：扩展维度滑块
                        Triple("依赖", bToADependency,  { v: Float -> bToADependency = v }),
                        Triple("嫉妒", bToAJealousy,    { v: Float -> bToAJealousy = v }),
                        Triple("压抑", bToASuppression, { v: Float -> bToASuppression = v })
                    )
                    sliders.forEach { (label, value, onChange) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim, modifier = Modifier.width(30.dp))
                            Slider(
                                value = value,
                                onValueChange = onChange,
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = dirColor,
                                    activeTrackColor = dirColor.copy(0.7f),
                                    inactiveTrackColor = LocalNyxColors.current.Layer3
                                )
                            )
                            Text("${(value * 100).toInt()}",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = dirColor, modifier = Modifier.width(28.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(dirColor.copy(0.15f))
                            .border(0.5.dp, dirColor.copy(0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                if (editDirAtoB) {
                                    vm.saveRelationshipManual(relAtoB.copy(
                                        trust = aToBTrust, affection = aToBAfaction,
                                        tension = aToBTension, respect = aToBRespect,
                                        dependency = aToBDependency,
                                        jealousy = aToBJealousy,
                                        suppression = aToBSuppression,
                                        updatedAt = System.currentTimeMillis()
                                    ))
                                } else {
                                    vm.saveRelationshipManual(relBtoA.copy(
                                        trust = bToATrust, affection = bToAAffection,
                                        tension = bToATension, respect = bToARespect,
                                        dependency = bToADependency,
                                        jealousy = bToAJealousy,
                                        suppression = bToASuppression,
                                        updatedAt = System.currentTimeMillis()
                                    ))
                                }
                                editMode = false
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("保存调整", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = dirColor.copy(0.9f))
                    }
                }
            }

            // ── 步骤12：关系记录折叠区 ───────────────────────────────────────
            // Bug 3 fix: 原来 remember(a.id) 只在 a.id 变化时重新读取，
            //            对话途中新写入的日志不会触发重组。
            //            改为以 vm.relLogTick.collectAsState().value 为 key，
            //            任何一次日志写入（_relLogTick++）都会让折叠区实时刷新。
            // Bug 4 fix: 原来加载 charA 的全部日志，会把「charA↔用户」的条目
            //            混入「charA↔charB」卡片。现在用 toCharId 过滤：
            //            只保留对端 = b.id 的条目（旧数据 toCharId 为空时兼容保留）。
            val logTick = vm.relLogTick.collectAsState().value
            val logsA = remember(a.id, logTick) {
                vm.loadRelationshipLog(a.id)
                    .filter { it.toCharId == b.id || it.toCharId.isEmpty() }
            }
            val logsB = remember(b.id, logTick) {
                vm.loadRelationshipLog(b.id)
                    .filter { it.toCharId == a.id || it.toCharId.isEmpty() }
            }
            val allLogs = (logsA + logsB).sortedByDescending { it.ts }
            if (allLogs.isNotEmpty()) {
                var showLog by remember { mutableStateOf(false) }
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalNyxColors.current.Layer2)
                        .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { showLog = !showLog }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (showLog) "收起关系记录" else "关系记录 (${allLogs.size})",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.AccentSoft.copy(0.7f)
                    )
                }
                if (showLog) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalNyxColors.current.Layer1)
                            .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        allLogs.take(30).forEach { entry ->
                            val charName = listOf(a, b).find { it.id == entry.charId }?.name ?: entry.charId
                            val sign = if (entry.delta >= 0) "+" else ""
                            val dimColor = when (entry.dim) {
                                "trust"       -> LocalNyxColors.current.Success
                                "affection"   -> Color(0xFFF472B6)
                                "tension"     -> LocalNyxColors.current.Error
                                "respect"     -> LocalNyxColors.current.AccentSoft
                                "dependency"  -> Color(0xFFA78BFA)
                                "jealousy"    -> Color(0xFFFB923C)
                                "suppression" -> Color(0xFF60A5FA)
                                else          -> LocalNyxColors.current.TextSecond
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${formatRelTime(entry.ts)}  $charName",
                                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                    color = LocalNyxColors.current.TextDim, modifier = Modifier.weight(1f)
                                )
                                Text(
                                    entry.dim,
                                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                    color = dimColor, modifier = Modifier.width(42.dp)
                                )
                                Text(
                                    // Bug 6 fix: 原来统一用 "%.3f"，stage 整数变化显示为 "+1.000"，视觉别扭。
                                    // stage → 整数（"+1"）；其余维度 → 百分比（"+2.4%"），一目了然。
                                    if (entry.dim == "stage")
                                        "$sign${entry.delta.toInt()}"
                                    else
                                        "$sign${"%.1f".format(entry.delta * 100)}%",
                                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                    color = if (entry.delta >= 0) LocalNyxColors.current.Success else LocalNyxColors.current.Error,
                                    modifier = Modifier.width(48.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// §5.2  用户↔角色关系卡片
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun UserCharCard(
    char: NyxCharacter,
    rel: Relationship,
    vm: ChatViewModel
) {
    // 阶段标签与颜色
    val stageLabel = when (rel.stage) {
        0 -> "陌生"; 1 -> "初识"; 2 -> "熟悉"
        3 -> "暧昧"; 4 -> "恋人"; 5 -> "深恋"
        else -> "深恋"
    }
    val stageColor = when (rel.stage) {
        4, 5 -> Color(0xFFF472B6)   // 粉红：恋爱阶段
        3    -> Color(0xFFA78BFA)   // 紫：暧昧
        else -> LocalNyxColors.current.TextDim   // 灰：早期
    }

    // 分手开关本地状态，key=rel.allowBreakup，外部变化时同步
    var allowBreakup by remember(rel.allowBreakup) { mutableStateOf(rel.allowBreakup) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalNyxColors.current.Layer1)
            .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header：头像 + 名字 + 阶段徽章
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Portrait(char, 36.dp)
                Text(
                    char.name,
                    fontSize = 13.sp,
                    fontFamily = CinzelFamily,
                    color = char.color,
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(stageColor.copy(alpha = 0.15f))
                    .border(0.5.dp, stageColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "$stageLabel · ${rel.stage}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = stageColor
                )
            }
        }

        // 六维关系条（用户↔角色无"嫉妒"维度，对称展示）
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RelBar("信任", rel.trust,       LocalNyxColors.current.Success,   "控制角色是否愿意坦白内心、分享秘密")
            RelBar("亲密", rel.affection,   Color(0xFFF472B6),                "情感表达的温柔程度和主动性；也是推进关系阶段的核心指标")
            RelBar("张力", rel.tension,     LocalNyxColors.current.Error,     "高时对话容易产生冲突、敏感激动；低时平稳淡然")
            RelBar("尊重", rel.respect,     LocalNyxColors.current.AccentSoft,"影响措辞礼貌程度，是否在乎对方感受")
            RelBar("依赖", rel.dependency,  Color(0xFFA78BFA),                "高时角色主动联系频率更高、语气更黏人")
            RelBar("压抑", rel.suppression, Color(0xFF60A5FA),                "低时角色情绪内敛克制；高时直接表达感受")
        }

        // ── Step 3 / Bug #7 fix：手动编辑区 ─────────────────────────────────
        // 与 RelationshipCard 对称；差异：单方向（角色→用户），6 个维度（无嫉妒）。
        var editMode    by remember { mutableStateOf(false) }
        var eTrust       by remember(rel.trust)       { mutableStateOf(rel.trust) }
        var eAffection   by remember(rel.affection)   { mutableStateOf(rel.affection) }
        var eTension     by remember(rel.tension)     { mutableStateOf(rel.tension) }
        var eRespect     by remember(rel.respect)     { mutableStateOf(rel.respect) }
        var eDependency  by remember(rel.dependency)  { mutableStateOf(rel.dependency) }
        var eSuppression by remember(rel.suppression) { mutableStateOf(rel.suppression) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (editMode) LocalNyxColors.current.AccentPill else LocalNyxColors.current.Layer2)
                    .border(0.5.dp, if (editMode) LocalNyxColors.current.BorderHi else LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable { editMode = !editMode }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (editMode) "收起编辑" else "手动调整",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = if (editMode) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(LocalNyxColors.current.Layer2)
                    .border(0.5.dp, LocalNyxColors.current.Error.copy(0.3f), RoundedCornerShape(8.dp))
                    .clickable { vm.resetRelationship(char.id, USER_PSEUDO_ID) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("重置", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.Error.copy(0.7f))
            }
        }

        if (editMode) {
            val sliderColor = char.color
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple("信任", eTrust,       { v: Float -> eTrust = v }),
                    Triple("亲密", eAffection,   { v: Float -> eAffection = v }),
                    Triple("张力", eTension,     { v: Float -> eTension = v }),
                    Triple("尊重", eRespect,     { v: Float -> eRespect = v }),
                    Triple("依赖", eDependency,  { v: Float -> eDependency = v }),
                    Triple("压抑", eSuppression, { v: Float -> eSuppression = v })
                ).forEach { (label, value, onChange) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim, modifier = Modifier.width(30.dp))
                        Slider(
                            value = value, onValueChange = onChange,
                            valueRange = 0f..1f, modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = sliderColor,
                                activeTrackColor = sliderColor.copy(0.7f),
                                inactiveTrackColor = LocalNyxColors.current.Layer3
                            )
                        )
                        Text("${(value * 100).toInt()}",
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = sliderColor, modifier = Modifier.width(28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(sliderColor.copy(0.15f))
                        .border(0.5.dp, sliderColor.copy(0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            vm.saveRelationshipManual(rel.copy(
                                trust       = eTrust,       affection   = eAffection,
                                tension     = eTension,     respect     = eRespect,
                                dependency  = eDependency,  suppression = eSuppression,
                                updatedAt   = System.currentTimeMillis()
                            ))
                            editMode = false
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存调整", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = sliderColor.copy(0.9f))
                }
            }
        }

        // ── 分手开关区：仅 stage >= 4 时渲染 ────────────────────────────────
        if (rel.stage >= 4) {
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = LocalNyxColors.current.BorderSubtle)
            Spacer(Modifier.height(2.dp))

            // 开关行
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (allowBreakup) Color(0xFFFF4444).copy(alpha = 0.08f)
                        else LocalNyxColors.current.Layer2
                    )
                    .border(
                        0.5.dp,
                        if (allowBreakup) Color(0xFFFF4444).copy(alpha = 0.4f)
                        else LocalNyxColors.current.BorderSubtle,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "⚠ 允许结束关系",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (allowBreakup) Color(0xFFFF4444) else LocalNyxColors.current.TextSecond,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (allowBreakup)
                            "关系将允许自然走向终结（亲密回升将自动关闭）"
                        else
                            "关闭时角色不会同意结束这段关系",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim,
                        lineHeight = 13.sp
                    )
                }
                Switch(
                    checked = allowBreakup,
                    onCheckedChange = { newVal ->
                        allowBreakup = newVal
                        vm.setAllowBreakup(char.id, newVal)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color(0xFFFF4444),
                        checkedTrackColor   = Color(0xFFFF4444).copy(alpha = 0.3f),
                        uncheckedThumbColor = LocalNyxColors.current.TextDim,
                        uncheckedTrackColor = LocalNyxColors.current.Layer3
                    )
                )
            }

            // 开关打开时：亲密 vs 分手门槛进度条
            if (allowBreakup) {
                val threshold = 0.25f
                val pct = ((rel.affection - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "分手门槛",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim,
                        modifier = Modifier.width(48.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LocalNyxColors.current.Layer2)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(pct)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (rel.affection < 0.35f) Color(0xFFFF4444).copy(alpha = 0.7f)
                                    else Color(0xFFFB923C).copy(alpha = 0.7f)
                                )
                        )
                    }
                    Text(
                        if (rel.affection <= threshold) "可分手"
                        else "${((rel.affection - threshold) * 100).toInt()}% 余量",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (rel.affection <= threshold) Color(0xFFFF4444) else LocalNyxColors.current.TextDim
                    )
                }
            }
        }

        // ── Step 6 / Bug #5 fix：关系记录折叠区 ────────────────────────────
        // 与 RelationshipCard 对称，过滤条件改为 toCharId == USER_PSEUDO_ID，
        // 避免角色间日志混入此卡片。logTick 驱动实时刷新同 RelationshipCard。
        val logTick = vm.relLogTick.collectAsState().value
        val userCharLogs = remember(char.id, logTick) {
            vm.loadRelationshipLog(char.id)
                .filter { it.toCharId == USER_PSEUDO_ID || it.toCharId.isEmpty() }
                .sortedByDescending { it.ts }
        }
        if (userCharLogs.isNotEmpty()) {
            var showLog by remember { mutableStateOf(false) }
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalNyxColors.current.Layer2)
                    .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable { showLog = !showLog }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (showLog) "收起关系记录" else "关系记录 (${userCharLogs.size})",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.AccentSoft.copy(0.7f)
                )
            }
            if (showLog) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalNyxColors.current.Layer1)
                        .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    userCharLogs.take(30).forEach { entry ->
                        val sign = if (entry.delta >= 0) "+" else ""
                        val dimColor = when (entry.dim) {
                            "trust"       -> LocalNyxColors.current.Success
                            "affection"   -> Color(0xFFF472B6)
                            "tension"     -> LocalNyxColors.current.Error
                            "respect"     -> LocalNyxColors.current.AccentSoft
                            "dependency"  -> Color(0xFFA78BFA)
                            "suppression" -> Color(0xFF60A5FA)
                            else          -> LocalNyxColors.current.TextSecond
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatRelTime(entry.ts),
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim, modifier = Modifier.weight(1f)
                            )
                            Text(
                                entry.dim,
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = dimColor, modifier = Modifier.width(42.dp)
                            )
                            Text(
                                if (entry.dim == "stage")
                                    "$sign${entry.delta.toInt()}"
                                else
                                    "$sign${"%.1f".format(entry.delta * 100)}%",
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = if (entry.delta >= 0) LocalNyxColors.current.Success else LocalNyxColors.current.Error,
                                modifier = Modifier.width(48.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelBar(label: String, value: Float, color: Color, tooltip: String? = null) {
    var showTip by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.width(30.dp)
            ) {
                Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim)
                if (tooltip != null) {
                    Text("ⓘ", fontSize = 7.sp, color = LocalNyxColors.current.TextDim.copy(0.45f),
                        modifier = Modifier.clickable { showTip = !showTip })
                }
            }

            Box(
                Modifier.weight(1f).height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(LocalNyxColors.current.Layer2)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.8f))
                )
            }

            Text(
                "${(value * 100).toInt()}",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = color, modifier = Modifier.width(28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        if (tooltip != null) {
            AnimatedVisibility(visible = showTip) {
                Text(
                    tooltip,
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = color.copy(0.65f), lineHeight = 13.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 1.dp, bottom = 1.dp)
                )
            }
        }
    }
}

private fun formatRelTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000        -> "刚刚"
        diff < 3_600_000     -> "${diff / 60_000}分钟前"
        diff < 86_400_000    -> "${diff / 3_600_000}小时前"
        else                 -> "${diff / 86_400_000}天前"
    }
}

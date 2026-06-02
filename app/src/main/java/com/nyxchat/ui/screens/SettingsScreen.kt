package com.nyxchat.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nyxchat.data.ApiConfig
import com.nyxchat.data.API_PROVIDERS
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val apiCfg by vm.apiConfig.collectAsState()
    val ttsCfg by vm.ttsConfig.collectAsState()
    var draft by remember(apiCfg) { mutableStateOf(apiCfg) }
    var showKey by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ─── 界面设置 ────────────────────────────────────────────────────────
        val enableParticles by vm.enableParticles.collectAsState()
        val chatBackground  by vm.chatBackground.collectAsState()
        // Batch 3 Item 9: 图片选取 launcher（TYPE_IMAGE 过滤器，持久化 URI 权限）
        val bgPickerContext = androidx.compose.ui.platform.LocalContext.current
        val bgPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                // 将图片复制到 App 内部存储，避免 URI 权限随时间失效
                val destFile = java.io.File(bgPickerContext.filesDir, "chat_background.jpg")
                try {
                    bgPickerContext.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    vm.setChatBackground(destFile.absolutePath)
                } catch (_: Exception) { /* 文件复制失败时静默忽略 */ }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("界面", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("环境粒子效果", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = LocalNyxColors.current.TextPrimary)
                    Text("关闭可降低中低端机耗电和发热", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                }
                Switch(
                    checked = enableParticles,
                    onCheckedChange = { vm.setParticlesEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = LocalNyxColors.current.Accent, checkedTrackColor = LocalNyxColors.current.Accent.copy(0.4f))
                )
            }

            // Batch 3 Item 9: 聊天背景图设置行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("聊天背景图", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                        color = LocalNyxColors.current.TextPrimary)
                    Text(
                        if (chatBackground.isNotBlank()) "已设置  · 点击更换"
                        else "默认（无背景）",
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 选取图片
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalNyxColors.current.Accent.copy(0.12f))
                            .border(0.5.dp, LocalNyxColors.current.BorderMid, RoundedCornerShape(10.dp))
                            .clickable { bgPickerLauncher.launch("image/*") }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("选图", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.AccentSoft)
                    }
                    // 清除背景
                    if (chatBackground.isNotBlank()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(LocalNyxColors.current.Error.copy(0.10f))
                                .border(0.5.dp, LocalNyxColors.current.Error.copy(0.3f), RoundedCornerShape(10.dp))
                                .clickable { vm.setChatBackground("") }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("清除", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.Error.copy(0.8f))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // ─── 系统权限 ──────────────────────────────────────────────────────
        val context = androidx.compose.ui.platform.LocalContext.current
        // Bug fix: 原 remember{} 只在 Composable 首次进入时检查一次权限，
        // 用户去系统设置授权后返回 App，UI 仍显示"未授权"（状态过时）。
        // 改用 DisposableEffect + LifecycleEventObserver：每次 ON_RESUME 重新读取，
        // 确保从设置页返回后立即反映最新权限状态。
        fun checkNotifPerm() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        var hasNotifPerm by remember { mutableStateOf(checkNotifPerm()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) hasNotifPerm = checkNotifPerm()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("系统", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("通知权限", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                        color = if (hasNotifPerm) Color(0xFF4CAF50) else LocalNyxColors.current.Error)
                    Text(if (hasNotifPerm) "主动消息功能正常" else "主动消息将无法推送",
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim)
                }
                if (!hasNotifPerm && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Text("去设置 →", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.Accent,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                android.net.Uri.parse("package:" + context.packageName)
                            )
                            context.startActivity(intent)
                        }.padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalNyxColors.current.Accent.copy(0.15f)))
                }
            }
        }

        // ─── 深色模式 ───────────────────────────────────────────────────────
        val isDark by vm.isDarkMode.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("深色模式", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                    color = LocalNyxColors.current.TextPrimary)
                Text(if (isDark) "深色主题" else "浅色主题",
                    fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim)
            }
            Switch(
                checked = isDark,
                onCheckedChange = { vm.setDarkMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LocalNyxColors.current.Accent,
                    checkedTrackColor = LocalNyxColors.current.Accent.copy(alpha = 0.3f),
                )
            )
        }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // ─── Prompt 管线开关面板（唯一声明）──────────────────────────────────
        val stages by vm.pipelineStages.collectAsState()
        val stageNames = mapOf(
            "base_persona"       to "角色人格",
            "user_persona"       to "用户角色",
            "persona_constraint" to "人格约束",
            "relationship"       to "关系注入",
            "world_book"         to "世界书",
            "memory"             to "记忆注入",
            "few_shot"           to "示例对话",
            "scene_state"        to "场景状态",
            "narrative_mode"     to "旁白模式",
            "group_context"      to "群聊上下文",   // P3-A fix: 新 stage
            "output_directive"   to "输出指令"
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("注入控制", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
            Text("控制哪些信息会注入给角色", fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
            val hiddenStages = setOf("base_persona", "persona_constraint", "output_directive", "group_context")
            stages.filter { it.name !in hiddenStages }.forEach { stage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalNyxColors.current.Layer2)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        stageNames[stage.name] ?: stage.name,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = if (stage.enabled) LocalNyxColors.current.TextPrimary else LocalNyxColors.current.TextDim
                    )
                    Switch(
                        checked = stage.enabled,
                        onCheckedChange = { vm.toggleStage(stage.name, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LocalNyxColors.current.Accent,
                            checkedTrackColor = LocalNyxColors.current.Accent.copy(0.3f),
                            uncheckedTrackColor = LocalNyxColors.current.BorderSubtle
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // ─── 历史压缩 ──────────────────────────────────────────────────────
        val isCompressing by vm.isCompressing.collectAsState()
        val compressInfo  by vm.lastCompressInfo.collectAsState()
        // Phase 2-B: 压缩阈值可编辑
        val compressTriggerValue by vm.compressTrigger.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("历史压缩", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
            Text("每次压缩最早的 ${com.nyxchat.data.NyxRepository.COMPRESS_BATCH} 条 → 1 条摘要",
                fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)

            // 压缩阈值调节器
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalNyxColors.current.Layer2)
                    .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("自动触发阈值", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextPrimary)
                    Text("超过此条数自动压缩（范围 20–200）", fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 减按钮
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (compressTriggerValue > 20)
                                    LocalNyxColors.current.Accent.copy(0.15f)
                                else LocalNyxColors.current.Layer3
                            )
                            .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable(enabled = compressTriggerValue > 20) {
                                vm.setCompressTrigger(compressTriggerValue - 10)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("−", fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                            color = if (compressTriggerValue > 20) LocalNyxColors.current.AccentSoft
                                    else LocalNyxColors.current.TextDim)
                    }
                    Text(
                        "$compressTriggerValue",
                        fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.AccentSoft,
                        modifier = Modifier.widthIn(min = 36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // 加按钮
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (compressTriggerValue < 200)
                                    LocalNyxColors.current.Accent.copy(0.15f)
                                else LocalNyxColors.current.Layer3
                            )
                            .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable(enabled = compressTriggerValue < 200) {
                                vm.setCompressTrigger(compressTriggerValue + 10)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                            color = if (compressTriggerValue < 200) LocalNyxColors.current.AccentSoft
                                    else LocalNyxColors.current.TextDim)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalNyxColors.current.Layer2)
                    .border(0.5.dp,
                        if (isCompressing) LocalNyxColors.current.Accent.copy(0.5f) else LocalNyxColors.current.BorderSubtle,
                        RoundedCornerShape(10.dp))
                    .clickable(enabled = !isCompressing) { vm.compressCurrentSession() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isCompressing) "压缩中…" else "立即压缩当前会话",
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = if (isCompressing) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextPrimary
                )
                if (isCompressing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LocalNyxColors.current.Accent, strokeWidth = 1.5.dp
                    )
                } else {
                    Text("⟳", fontSize = 14.sp, color = LocalNyxColors.current.TextDim)
                }
            }
            compressInfo?.let {
                Text(it, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.AccentSoft.copy(0.75f))
            }
        }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // ─── Phase 5: 高级参数 ──────────────────────────────────────────────
        val wbScanDepth       by vm.wbScanDepth.collectAsState()
        val wbMaxEntries      by vm.wbMaxEntries.collectAsState()
        val memoryInjectCount by vm.memoryInjectCount.collectAsState()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("高级参数", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
            Text("调节触发和注入行为，默认值适合大多数场景",
                fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)

            // 共用的 ± 调节行 Composable
            @Composable
            fun AdvancedParamRow(
                title: String,
                subtitle: String,
                value: Int,
                min: Int,
                max: Int,
                step: Int,
                onDec: () -> Unit,
                onInc: () -> Unit
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalNyxColors.current.Layer2)
                        .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextPrimary)
                        Text(subtitle, fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (value > min) LocalNyxColors.current.Accent.copy(0.15f) else LocalNyxColors.current.Layer3)
                                .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable(enabled = value > min, onClick = onDec),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                                color = if (value > min) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                        }
                        Text("$value", fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.AccentSoft,
                            modifier = Modifier.widthIn(min = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (value < max) LocalNyxColors.current.Accent.copy(0.15f) else LocalNyxColors.current.Layer3)
                                .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable(enabled = value < max, onClick = onInc),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                                color = if (value < max) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim)
                        }
                    }
                }
            }

            AdvancedParamRow(
                title    = "世界书扫描深度",
                subtitle = "触发检测时回看最近 N 条消息（6–30）",
                value = wbScanDepth, min = 6, max = 30, step = 1,
                onDec = { vm.setWbScanDepth(wbScanDepth - 1) },
                onInc = { vm.setWbScanDepth(wbScanDepth + 1) }
            )
            AdvancedParamRow(
                title    = "世界书触发上限",
                subtitle = "每次最多注入几条触发条目（1–30）",
                value = wbMaxEntries, min = 1, max = 30, step = 1,
                onDec = { vm.setWbMaxEntries(wbMaxEntries - 1) },
                onInc = { vm.setWbMaxEntries(wbMaxEntries + 1) }
            )
            AdvancedParamRow(
                title    = "记忆注入数量",
                subtitle = "每次对话注入多少条记忆碎片（5–50）",
                value = memoryInjectCount, min = 5, max = 50, step = 5,
                onDec = { vm.setMemoryInjectCount(memoryInjectCount - 5) },
                onInc = { vm.setMemoryInjectCount(memoryInjectCount + 5) }
            )
        }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        val personaState by vm.userPersona.collectAsState()
        var personaDraft by remember(personaState) { mutableStateOf(personaState) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("用户角色", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)
            Text("设定你的名字，角色会用这个称呼你",
                fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("你的名字", personaDraft.name, "") to { v: String -> personaDraft = personaDraft.copy(name = v) },
                        Triple("简短描述（可选）", personaDraft.description, "例：喜欢安静，不善言辞") to { v: String -> personaDraft = personaDraft.copy(description = v) }
                    ).forEach { (triple, onChange) ->
                        val (label, value, placeholder) = triple
                        Column {
                            Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = LocalNyxColors.current.TextDim, modifier = Modifier.padding(bottom = 3.dp))
                            TextField(
                                value = value, onValueChange = onChange,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor   = LocalNyxColors.current.Layer3,
                                    unfocusedContainerColor = LocalNyxColors.current.Layer3,
                                    focusedIndicatorColor   = LocalNyxColors.current.Accent.copy(0.6f),
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor        = LocalNyxColors.current.TextPrimary,
                                    unfocusedTextColor      = LocalNyxColors.current.TextSecond,
                                    cursorColor             = LocalNyxColors.current.Accent
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder, fontSize = 12.sp, color = LocalNyxColors.current.TextDim) }} else null,
                                singleLine = true
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { vm.saveUserPersona(personaDraft) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalNyxColors.current.AccentSoft),
                border = BorderStroke(0.5.dp, LocalNyxColors.current.BorderMid),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存角色设定", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "API 设置",
            fontSize = 13.sp, fontFamily = CinzelFamily,
            color = LocalNyxColors.current.AccentSoft, letterSpacing = 2.sp
        )

        // ── 厂商选择器 ───────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("服务商", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                API_PROVIDERS.forEach { provider ->
                    val isSelected = draft.providerName == provider.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0x259D6FFF) else LocalNyxColors.current.Layer2)
                            .border(
                                1.dp,
                                if (isSelected) Color(0x669D6FFF) else LocalNyxColors.current.BorderSubtle,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                val firstModel = provider.models.firstOrNull()
                                draft = draft.copy(
                                    providerName  = provider.id,
                                    baseUrl       = if (!provider.isCustom) provider.baseUrl else draft.baseUrl,
                                    model         = firstModel?.modelId ?: draft.model,
                                    historyLimit  = firstModel?.historyLimit
                                )
                                testStatus = null
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            provider.displayName,
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = if (isSelected) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // ── 模型选择（仅内置厂商显示）────────────────────────────────────────
        val selectedProvider = API_PROVIDERS.find { it.id == draft.providerName }
        if (selectedProvider != null && !selectedProvider.isCustom && selectedProvider.models.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模型", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedProvider.models.forEach { mo ->
                        val isSelected = draft.model == mo.modelId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x209D6FFF) else LocalNyxColors.current.Layer2)
                                .border(
                                    0.5.dp,
                                    if (isSelected) Color(0x669D6FFF) else LocalNyxColors.current.BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    draft = draft.copy(model = mo.modelId, historyLimit = mo.historyLimit)
                                    testStatus = null
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                mo.displayName,
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = if (isSelected) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextSecond
                            )
                            if (isSelected) Text("✓", fontSize = 11.sp, color = LocalNyxColors.current.Accent)
                        }
                    }
                }
            }
        }

        // ── 提供商专属提示 ─────────────────────────────────────────────────
        if (selectedProvider?.id == "ark") {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalNyxColors.current.Warning.copy(0.08f))
                    .border(0.5.dp, LocalNyxColors.current.Warning.copy(0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("⚠", fontSize = 11.sp, color = LocalNyxColors.current.Warning)
                Text(
                    "Doubao 模型需在火山引擎控制台「模型接入点」创建接入点后，将 ep-xxxxxxxx 格式 ID 填入上方模型输入框。",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextSecond, lineHeight = 15.sp
                )
            }
        }
        if (selectedProvider?.id == "modelscope") {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalNyxColors.current.AccentGlow.copy(0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("ℹ", fontSize = 11.sp, color = LocalNyxColors.current.AccentSoft)
                Text(
                    "魔塔模型 ID 格式为「组织/模型名」，如 Qwen/Qwen2.5-72B-Instruct，可在魔塔社区模型页查看。",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextSecond, lineHeight = 15.sp
                )
            }
        }

        // ── 自定义厂商：手动填写 URL 和模型名 ─────────────────────────────────
        if (selectedProvider?.isCustom == true || draft.providerName.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("基础地址", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = LocalNyxColors.current.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("模型名称", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = LocalNyxColors.current.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            // Phase 2-C: 自定义厂商历史条数输入框（后端 ApiConfig.historyLimit 已完整实现）
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("保留历史条数", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = draft.historyLimit?.toString() ?: "",
                    onValueChange = { v ->
                        draft = draft.copy(historyLimit = v.filter { it.isDigit() }.toIntOrNull())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = LocalNyxColors.current.TextPrimary
                    ),
                    placeholder = { Text("留空自动识别", fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, color = LocalNyxColors.current.TextDim) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Text("对话中保留最近 N 条消息；留空则根据模型名自动估算",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    color = LocalNyxColors.current.TextDim.copy(0.7f))
            }
        }  // end: if (selectedProvider?.isCustom == true || ...)

        // ── API Key（始终显示）──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("API 密钥", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { draft = draft.copy(apiKey = it); testStatus = null },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        if (showKey) "隐藏" else "显示",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim,
                        modifier = Modifier.clickable { showKey = !showKey }.padding(8.dp)
                    )
                },
                colors = outlinedFieldColors(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = LocalNyxColors.current.TextPrimary
                ),
                placeholder = { Text("sk-…", fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, color = LocalNyxColors.current.TextDim) },
                shape = RoundedCornerShape(10.dp)
            )
        }

        // ── 保存 / 测试 ─────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.saveApiConfig(draft); testStatus = "saved" },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x509D6FFF), contentColor = LocalNyxColors.current.AccentSoft
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存", fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.5.sp)
            }

            OutlinedButton(
                onClick = {
                    testStatus = "testing"
                    scope.launch {
                        vm.saveApiConfig(draft)
                        val ok = vm.testConnection(draft)
                        testStatus = if (ok) "ok" else "fail"
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalNyxColors.current.Success),
                border = BorderStroke(1.dp, Color(0x4D34D399)),
                shape = RoundedCornerShape(10.dp),
                enabled = draft.apiKey.isNotBlank()
            ) {
                Text("测试", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        // ── 状态提示 ────────────────────────────────────────────────────────
        testStatus?.let { status ->
            val (bg, border, text, msg) = when (status) {
                "testing" -> listOf(LocalNyxColors.current.Layer2, LocalNyxColors.current.BorderSubtle, LocalNyxColors.current.TextDim,    "连接中…")
                "ok"      -> listOf(Color(0x0D34D399), Color(0x4D34D399), LocalNyxColors.current.Success,    "✓  连接成功")
                "saved"   -> listOf(Color(0x0D9D6FFF), Color(0x4D9D6FFF), LocalNyxColors.current.AccentSoft, "✓  已保存")
                else      -> listOf(Color(0x14F87171), Color(0x4DF87171), LocalNyxColors.current.Error,      "✗  连接失败")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg as Color)
                    .border(1.dp, border as Color, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(msg as String, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = text as Color)
            }
        }



        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

        TtsSettingsSection(ttsCfg) { vm.saveTtsConfig(it) }

        HorizontalDivider(color = LocalNyxColors.current.BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

        // ─── 崩溃日志 ───────────────────────────────────────────────────
        var crashLogs by remember { mutableStateOf(com.nyxchat.util.CrashLogger.getCrashLogs(context)) }
        var showCrashLog by remember { mutableStateOf(false) }
        var crashLogContent by remember { mutableStateOf("") }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("调试信息", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.AccentSoft, letterSpacing = 1.5.sp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("崩溃日志", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = LocalNyxColors.current.TextPrimary)
                    Text(
                        if (crashLogs.isEmpty()) "无崩溃记录" else "${crashLogs.size} 条记录",
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextDim
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (crashLogs.isNotEmpty()) {
                        // Bug fix: File.readText() / File I/O 必须在 IO 线程执行，不能在点击回调（主线程）
                        Text("查看", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.Accent,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) {
                                        crashLogs.firstOrNull()?.readText() ?: "无日志内容"
                                    }
                                    crashLogContent = text
                                    showCrashLog = true
                                }
                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalNyxColors.current.Accent.copy(0.15f)))
                        Text("清除", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.Error,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        com.nyxchat.util.CrashLogger.clearLogs(context)
                                    }
                                    crashLogs = withContext(Dispatchers.IO) {
                                        com.nyxchat.util.CrashLogger.getCrashLogs(context)
                                    }
                                }
                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalNyxColors.current.Error.copy(0.15f)))
                    }
                }
            }
        }

        if (showCrashLog) {
            AlertDialog(
                onDismissRequest = { showCrashLog = false },
                title = { Text("崩溃日志", fontFamily = FontFamily.Monospace, color = LocalNyxColors.current.TextPrimary) },
                text = {
                    Text(
                        crashLogContent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LocalNyxColors.current.TextDim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showCrashLog = false }) {
                        Text("关闭", color = LocalNyxColors.current.Accent)
                    }
                },
                containerColor = LocalNyxColors.current.Layer2
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalNyxColors.current.Layer3,
    unfocusedContainerColor = LocalNyxColors.current.Layer3,
    focusedBorderColor = LocalNyxColors.current.BorderMid,
    unfocusedBorderColor = LocalNyxColors.current.BorderSubtle,
    focusedTextColor = LocalNyxColors.current.TextPrimary,
    unfocusedTextColor = LocalNyxColors.current.TextPrimary,
    cursorColor = LocalNyxColors.current.Accent
)

// ─── TTS Settings Section ─────────────────────────────────────────────────────

@Composable
fun TtsSettingsSection(
    ttsConfig: com.nyxchat.data.TtsConfig,
    onSave: (com.nyxchat.data.TtsConfig) -> Unit
) {
    var draft by remember(ttsConfig) { mutableStateOf(ttsConfig) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("语音合成", fontSize = 11.sp, fontFamily = CinzelFamily,
            color = LocalNyxColors.current.AccentSoft, letterSpacing = 2.sp)

        // ── 启用开关 ──────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("启用语音合成", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                color = LocalNyxColors.current.TextPrimary)
            Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LocalNyxColors.current.Accent,
                    checkedTrackColor = LocalNyxColors.current.Accent.copy(0.4f)))
        }

        if (draft.enabled) {

            // ── 提供商选择 Tab ────────────────────────────────────────
            val providers = listOf(
                com.nyxchat.data.TtsProvider.Azure        to "Azure",
                com.nyxchat.data.TtsProvider.AlibabaCloud to "阿里云百炼",
                com.nyxchat.data.TtsProvider.Volcengine   to "火山方舟"
            )
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalNyxColors.current.Layer2)
            ) {
                providers.forEach { (prov, label) ->
                    val selected = draft.provider == prov
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) LocalNyxColors.current.Accent.copy(0.2f)
                                else Color.Transparent
                            )
                            .clickable { draft = draft.copy(provider = prov) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = if (selected) LocalNyxColors.current.AccentSoft
                                    else LocalNyxColors.current.TextDim)
                    }
                }
            }

            // ── 按提供商显示配置字段 ──────────────────────────────────
            when (draft.provider) {

                com.nyxchat.data.TtsProvider.Azure ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Azure 订阅密钥", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        OutlinedTextField(draft.subscriptionKey,
                            { draft = draft.copy(subscriptionKey = it) },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("输入 Azure API 密钥", fontSize = 13.sp,
                                color = LocalNyxColors.current.TextDim) },
                            visualTransformation = PasswordVisualTransformation(),
                            colors = outlinedFieldColors(), shape = RoundedCornerShape(10.dp))

                        Text("区域", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        val regions = listOf("eastasia","japaneast","southeastasia","eastus","westeurope")
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            regions.forEach { r ->
                                TtsChip(r, draft.region == r) { draft = draft.copy(region = r) }
                            }
                        }
                    }

                com.nyxchat.data.TtsProvider.AlibabaCloud ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DashScope API Key", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        OutlinedTextField(draft.alibabaDashscopeKey,
                            { draft = draft.copy(alibabaDashscopeKey = it) },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("sk-xxxxxxxx", fontSize = 13.sp,
                                color = LocalNyxColors.current.TextDim) },
                            visualTransformation = PasswordVisualTransformation(),
                            colors = outlinedFieldColors(), shape = RoundedCornerShape(10.dp))

                        Text("选择语音", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        val aliVoices = listOf(
                            "longxiaochun" to "龙小纯（温柔女声）",
                            "longhua"      to "龙华（成熟男声）",
                            "longxiaoxia"  to "龙小夏（元气女声）",
                            "longyuan"     to "龙媛（知性女声）",
                            "longfei"      to "龙飞（青年男声）",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            aliVoices.forEach { (id, name) ->
                                TtsChip(name, draft.alibabaVoiceId == id) {
                                    draft = draft.copy(alibabaVoiceId = id)
                                }
                            }
                        }
                    }

                com.nyxchat.data.TtsProvider.Volcengine ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("火山引擎 AppID", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        OutlinedTextField(draft.volcengineAppId,
                            { draft = draft.copy(volcengineAppId = it) },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("在火山引擎控制台获取", fontSize = 13.sp,
                                color = LocalNyxColors.current.TextDim) },
                            colors = outlinedFieldColors(), shape = RoundedCornerShape(10.dp))

                        Text("Access Token", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        OutlinedTextField(draft.volcengineToken,
                            { draft = draft.copy(volcengineToken = it) },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("Bearer token", fontSize = 13.sp,
                                color = LocalNyxColors.current.TextDim) },
                            visualTransformation = PasswordVisualTransformation(),
                            colors = outlinedFieldColors(), shape = RoundedCornerShape(10.dp))

                        Text("选择语音", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = LocalNyxColors.current.TextDim)
                        val volVoices = listOf(
                            "zh_female_wanwanxiaohe_moon_bigtts"    to "湾湾小何（台湾温柔）",
                            "zh_female_qingxin_emo"                 to "清新情感女声",
                            "zh_male_M392_conversation"             to "对话男声",
                            "zh_female_shuangkuaisisi_moon_bigtts"  to "爽快思思",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            volVoices.forEach { (id, name) ->
                                TtsChip(name, draft.volcengineVoiceType == id) {
                                    draft = draft.copy(volcengineVoiceType = id)
                                }
                            }
                        }
                    }
            }

            // ── 通用开关 ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("自动播放回复", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                    color = LocalNyxColors.current.TextPrimary)
                Switch(checked = draft.autoPlay, onCheckedChange = { draft = draft.copy(autoPlay = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalNyxColors.current.Accent,
                        checkedTrackColor = LocalNyxColors.current.Accent.copy(0.4f)))
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("主动消息自动朗读", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                    color = LocalNyxColors.current.TextPrimary)
                Switch(checked = draft.autoReadProactive, onCheckedChange = { draft = draft.copy(autoReadProactive = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalNyxColors.current.Accent,
                        checkedTrackColor = LocalNyxColors.current.Accent.copy(0.4f)))
            }
        }

        Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                LocalNyxColors.current.Accent.copy(0.25f), LocalNyxColors.current.AccentSoft),
            shape = RoundedCornerShape(10.dp)) {
            Text("保存语音设置", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TtsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) LocalNyxColors.current.Accent.copy(0.2f)
                else LocalNyxColors.current.Layer2
            )
            .border(0.5.dp,
                if (selected) LocalNyxColors.current.Accent.copy(0.5f)
                else LocalNyxColors.current.BorderSubtle,
                RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            color = if (selected) LocalNyxColors.current.AccentSoft
                    else LocalNyxColors.current.TextDim)
    }
}

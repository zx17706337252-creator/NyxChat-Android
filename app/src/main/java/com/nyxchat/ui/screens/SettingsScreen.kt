package com.nyxchat.ui.screens

import android.Manifest
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
import androidx.compose.ui.text.TextStyle

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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("界面", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.AccentSoft, letterSpacing = 1.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("环境粒子效果", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextPrimary)
                    Text("关闭可降低中低端机耗电和发热", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                }
                Switch(
                    checked = enableParticles,
                    onCheckedChange = { vm.setParticlesEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NyxColors.Accent, checkedTrackColor = NyxColors.Accent.copy(0.4f))
                )
            }
        }

        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

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
                color = NyxColors.AccentSoft, letterSpacing = 1.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("通知权限", fontSize = 13.sp, fontFamily = CrimsonProFamily,
                        color = if (hasNotifPerm) Color(0xFF4CAF50) else NyxColors.Error)
                    Text(if (hasNotifPerm) "主动消息功能正常" else "主动消息将无法推送",
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim)
                }
                if (!hasNotifPerm && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Text("去设置 →", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.Accent,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                android.net.Uri.parse("package:" + context.packageName)
                            )
                            context.startActivity(intent)
                        }.padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NyxColors.Accent.copy(0.15f)))
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
                    color = NyxColors.TextPrimary)
                Text(if (isDark) "深色主题" else "浅色主题",
                    fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                    color = NyxColors.TextDim)
            }
            Switch(
                checked = isDark,
                onCheckedChange = { vm.setDarkMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NyxColors.Accent,
                    checkedTrackColor = NyxColors.Accent.copy(alpha = 0.3f),
                )
            )
        }

        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

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
            Text("提示词管线", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.AccentSoft, letterSpacing = 1.5.sp)
            Text("调试用——可临时关闭某个注入阶段", fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
            stages.forEach { stage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NyxColors.Layer2)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        stageNames[stage.name] ?: stage.name,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = if (stage.enabled) NyxColors.TextPrimary else NyxColors.TextDim
                    )
                    Switch(
                        checked = stage.enabled,
                        onCheckedChange = { vm.toggleStage(stage.name, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NyxColors.Accent,
                            checkedTrackColor = NyxColors.Accent.copy(0.3f),
                            uncheckedTrackColor = NyxColors.BorderSubtle
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // ─── 历史压缩 ──────────────────────────────────────────────────────
        val isCompressing by vm.isCompressing.collectAsState()
        val compressInfo  by vm.lastCompressInfo.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("历史压缩", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.AccentSoft, letterSpacing = 1.5.sp)
            Text("超过 ${com.nyxchat.data.NyxRepository.COMPRESS_TRIGGER} 条自动触发；" +
                 "也可手动压缩当前会话最早的 ${com.nyxchat.data.NyxRepository.COMPRESS_BATCH} 条",
                fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NyxColors.Layer2)
                    .border(0.5.dp,
                        if (isCompressing) NyxColors.Accent.copy(0.5f) else NyxColors.BorderSubtle,
                        RoundedCornerShape(10.dp))
                    .clickable(enabled = !isCompressing) { vm.compressCurrentSession() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isCompressing) "压缩中…" else "立即压缩当前会话",
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = if (isCompressing) NyxColors.AccentSoft else NyxColors.TextPrimary
                )
                if (isCompressing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = NyxColors.Accent, strokeWidth = 1.5.dp
                    )
                } else {
                    Text("⟳", fontSize = 14.sp, color = NyxColors.TextDim)
                }
            }
            compressInfo?.let {
                Text(it, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace,
                    color = NyxColors.AccentSoft.copy(0.75f))
            }
        }

        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        // ─── User Persona ───────────────────────────────────────────────────
        val personaState by vm.userPersona.collectAsState()
        var personaDraft by remember(personaState) { mutableStateOf(personaState) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("用户角色", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.AccentSoft, letterSpacing = 1.5.sp)
            Text("设定你的名字，角色会用这个称呼你",
                fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("你的名字", personaDraft.name, "") to { v: String -> personaDraft = personaDraft.copy(name = v) },
                        Triple("简短描述（可选）", personaDraft.description, "例：喜欢安静，不善言辞") to { v: String -> personaDraft = personaDraft.copy(description = v) }
                    ).forEach { (triple, onChange) ->
                        val (label, value, placeholder) = triple
                        Column {
                            Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                color = NyxColors.TextDim, modifier = Modifier.padding(bottom = 3.dp))
                            TextField(
                                value = value, onValueChange = onChange,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor   = NyxColors.Layer3,
                                    unfocusedContainerColor = NyxColors.Layer3,
                                    focusedIndicatorColor   = NyxColors.Accent.copy(0.6f),
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor        = NyxColors.TextPrimary,
                                    unfocusedTextColor      = NyxColors.TextSecond,
                                    cursorColor             = NyxColors.Accent
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder, fontSize = 12.sp, color = NyxColors.TextDim) }} else null,
                                singleLine = true
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { vm.saveUserPersona(personaDraft) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NyxColors.AccentSoft),
                border = BorderStroke(0.5.dp, NyxColors.BorderMid),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存角色设定", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "API 设置",
            fontSize = 13.sp, fontFamily = CinzelFamily,
            color = NyxColors.AccentSoft, letterSpacing = 2.sp
        )

        // ── 厂商选择器 ───────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("服务商", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.TextDim, letterSpacing = 0.5.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                API_PROVIDERS.forEach { provider ->
                    val isSelected = draft.providerName == provider.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0x259D6FFF) else Color(0x0DFFFFFF))
                            .border(
                                1.dp,
                                if (isSelected) Color(0x669D6FFF) else Color(0x1AFFFFFF),
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
                            color = if (isSelected) NyxColors.AccentSoft else NyxColors.TextDim,
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
                    color = NyxColors.TextDim, letterSpacing = 0.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedProvider.models.forEach { mo ->
                        val isSelected = draft.model == mo.modelId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x209D6FFF) else NyxColors.Layer2)
                                .border(
                                    0.5.dp,
                                    if (isSelected) Color(0x669D6FFF) else NyxColors.BorderSubtle,
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
                                color = if (isSelected) NyxColors.AccentSoft else NyxColors.TextSecond
                            )
                            if (isSelected) Text("✓", fontSize = 11.sp, color = NyxColors.Accent)
                        }
                    }
                }
            }
        }

        // ── 自定义厂商：手动填写 URL 和模型名 ─────────────────────────────────
        if (selectedProvider?.isCustom == true || draft.providerName.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("基础地址", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = NyxColors.TextDim, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = NyxColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("模型名称", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = NyxColors.TextDim, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedFieldColors(),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = NyxColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // ── API Key（始终显示）──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("API 密钥", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.TextDim, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { draft = draft.copy(apiKey = it); testStatus = null },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        if (showKey) "隐藏" else "显示",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim,
                        modifier = Modifier.clickable { showKey = !showKey }.padding(8.dp)
                    )
                },
                colors = outlinedFieldColors(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = NyxColors.TextPrimary
                ),
                placeholder = { Text("sk-…", fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, color = NyxColors.TextDim) },
                shape = RoundedCornerShape(10.dp)
            )
        }

        // ── 保存 / 测试 ─────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.saveApiConfig(draft); testStatus = "saved" },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x509D6FFF), contentColor = NyxColors.AccentSoft
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NyxColors.Success),
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
                "testing" -> listOf(Color(0x0DFFFFFF), Color(0x1AFFFFFF), NyxColors.TextDim,    "连接中…")
                "ok"      -> listOf(Color(0x0D34D399), Color(0x4D34D399), NyxColors.Success,    "✓  连接成功")
                "saved"   -> listOf(Color(0x0D9D6FFF), Color(0x4D9D6FFF), NyxColors.AccentSoft, "✓  已保存")
                else      -> listOf(Color(0x14F87171), Color(0x4DF87171), NyxColors.Error,      "✗  连接失败")
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



        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

        TtsSettingsSection(ttsCfg) { vm.saveTtsConfig(it) }

        HorizontalDivider(color = NyxColors.BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

        // ─── 崩溃日志 ───────────────────────────────────────────────────
        var crashLogs by remember { mutableStateOf(com.nyxchat.util.CrashLogger.getCrashLogs(context)) }
        var showCrashLog by remember { mutableStateOf(false) }
        var crashLogContent by remember { mutableStateOf("") }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("调试信息", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.AccentSoft, letterSpacing = 1.5.sp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("崩溃日志", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextPrimary)
                    Text(
                        if (crashLogs.isEmpty()) "无崩溃记录" else "${crashLogs.size} 条记录",
                        fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (crashLogs.isNotEmpty()) {
                        // Bug fix: File.readText() / File I/O 必须在 IO 线程执行，不能在点击回调（主线程）
                        Text("查看", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = NyxColors.Accent,
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
                            .background(NyxColors.Accent.copy(0.15f)))
                        Text("清除", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = NyxColors.Error,
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
                            .background(NyxColors.Error.copy(0.15f)))
                    }
                }
            }
        }

        if (showCrashLog) {
            AlertDialog(
                onDismissRequest = { showCrashLog = false },
                title = { Text("崩溃日志", fontFamily = FontFamily.Monospace, color = NyxColors.TextPrimary) },
                text = {
                    Text(
                        crashLogContent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NyxColors.TextDim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showCrashLog = false }) {
                        Text("关闭", color = NyxColors.Accent)
                    }
                },
                containerColor = NyxColors.Layer2
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0x0AFFFFFF),
    unfocusedContainerColor = Color(0x06FFFFFF),
    focusedBorderColor = NyxColors.BorderMid,
    unfocusedBorderColor = NyxColors.BorderSubtle,
    focusedTextColor = NyxColors.TextPrimary,
    unfocusedTextColor = NyxColors.TextPrimary,
    cursorColor = NyxColors.Accent
)

// ─── TTS Settings Section ─────────────────────────────────────────────────────

@Composable
fun TtsSettingsSection(
    ttsConfig: com.nyxchat.data.TtsConfig,
    onSave: (com.nyxchat.data.TtsConfig) -> Unit
) {
    var draft by remember(ttsConfig) { mutableStateOf(ttsConfig) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Azure 语音合成", fontSize = 11.sp, fontFamily = CinzelFamily,
            color = NyxColors.AccentSoft, letterSpacing = 2.sp)

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("启用语音合成", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextPrimary)
            Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) },
                colors = SwitchDefaults.colors(checkedThumbColor = NyxColors.Accent, checkedTrackColor = NyxColors.Accent.copy(0.4f)))
        }

        if (draft.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Azure 订阅密钥", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                OutlinedTextField(draft.subscriptionKey,
                    { draft = draft.copy(subscriptionKey = it) },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text("输入 Azure API 密钥", fontSize = 13.sp, color = NyxColors.TextDim) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = outlinedFieldColors(), shape = RoundedCornerShape(10.dp))

                Text("区域", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NyxColors.TextDim)
                val regions = listOf("eastasia","japaneast","southeastasia","eastus","westeurope")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    regions.forEach { r ->
                        Box(Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (draft.region==r) NyxColors.Accent.copy(0.2f) else NyxColors.Layer2)
                            .border(0.5.dp, if (draft.region==r) NyxColors.Accent.copy(0.5f) else NyxColors.BorderSubtle, RoundedCornerShape(16.dp))
                            .clickable { draft = draft.copy(region = r) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(r, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = if (draft.region==r) NyxColors.AccentSoft else NyxColors.TextDim)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("自动播放回复", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextPrimary)
                    Switch(checked = draft.autoPlay, onCheckedChange = { draft = draft.copy(autoPlay = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NyxColors.Accent, checkedTrackColor = NyxColors.Accent.copy(0.4f)))
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("主动消息自动朗读", fontSize = 13.sp, fontFamily = CrimsonProFamily, color = NyxColors.TextPrimary)
                    Switch(checked = draft.autoReadProactive, onCheckedChange = { draft = draft.copy(autoReadProactive = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NyxColors.Accent, checkedTrackColor = NyxColors.Accent.copy(0.4f)))
                }
            }
        }

        Button(onClick = { onSave(draft) }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(NyxColors.Accent.copy(0.25f), NyxColors.AccentSoft),
            shape = RoundedCornerShape(10.dp)) {
            Text("保存语音合成设置", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

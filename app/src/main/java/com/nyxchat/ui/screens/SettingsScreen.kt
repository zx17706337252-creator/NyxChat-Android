package com.nyxchat.ui.screens

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
import com.nyxchat.data.ApiConfig
import com.nyxchat.ui.theme.*
import com.nyxchat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

data class ApiPreset(val name: String, val url: String, val model: String)

val API_PRESETS = listOf(
    ApiPreset("OpenAI",         "https://api.openai.com/v1",                               "gpt-4o-mini"),
    ApiPreset("DS V4 Pro",      "https://api.deepseek.com/v1",                             "deepseek-v4-pro"),
    ApiPreset("DS V4 Flash",    "https://api.deepseek.com/v1",                             "deepseek-v4-flash"),
    ApiPreset("Gemini",         "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
    ApiPreset("Ollama",         "http://localhost:11434/v1",                                "llama3"),
)

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val apiCfg by vm.apiConfig.collectAsState()
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
        Text(
            "API SETTINGS",
            fontSize = 13.sp, fontFamily = CinzelFamily,
            color = NyxColors.AccentSoft, letterSpacing = 2.sp
        )

        // Presets
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "快速选择",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = NyxColors.TextDim, letterSpacing = 0.5.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                API_PRESETS.forEach { preset ->
                    val isSelected = draft.baseUrl == preset.url
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0x259D6FFF) else Color(0x0DFFFFFF))
                            .border(
                                1.dp,
                                if (isSelected) Color(0x669D6FFF) else Color(0x1AFFFFFF),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { draft = draft.copy(baseUrl = preset.url, model = preset.model) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            preset.name,
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = if (isSelected) NyxColors.AccentSoft else NyxColors.TextDim,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // API Key
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("API KEY", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
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

        // Base URL
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BASE URL", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
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

        // Model
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MODEL", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
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

        // Buttons
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

        // Status
        testStatus?.let { status ->
            val (bg, border, text, msg) = when (status) {
                "testing" -> listOf(Color(0x0DFFFFFF), Color(0x1AFFFFFF), NyxColors.TextDim, "连接中…")
                "ok"      -> listOf(Color(0x0D34D399), Color(0x4D34D399), NyxColors.Success, "✓  连接成功")
                "saved"   -> listOf(Color(0x0D9D6FFF), Color(0x4D9D6FFF), NyxColors.AccentSoft, "✓  已保存")
                else      -> listOf(Color(0x14F87171), Color(0x4DF87171), NyxColors.Error, "✗  连接失败")
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

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0x0AFFFFFF),
    unfocusedContainerColor = Color(0x06FFFFFF),
    focusedBorderColor = NyxColors.BorderHi,
    unfocusedBorderColor = NyxColors.Border,
    focusedTextColor = NyxColors.TextPrimary,
    unfocusedTextColor = NyxColors.TextPrimary,
    cursorColor = NyxColors.Accent
)

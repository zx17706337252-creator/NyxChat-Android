package com.nyxchat.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NyxRepository(context: Context) {

    private val prefs = context.getSharedPreferences("nyx_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ─── Characters ──────────────────────────────────────────────────────

    fun loadCharacters(): List<NyxCharacter> {
        val json = prefs.getString("characters", null) ?: return DEFAULT_CHARACTERS
        return try {
            val type = object : TypeToken<List<NyxCharacter>>() {}.type
            gson.fromJson(json, type) ?: DEFAULT_CHARACTERS
        } catch (e: Exception) { DEFAULT_CHARACTERS }
    }

    fun saveCharacters(chars: List<NyxCharacter>) {
        prefs.edit().putString("characters", gson.toJson(chars)).apply()
    }

    // ─── Messages ────────────────────────────────────────────────────────

    fun loadMessages(): List<NyxMessage> {
        val json = prefs.getString("messages", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NyxMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveMessages(messages: List<NyxMessage>) {
        // Keep last 200 messages
        val trimmed = if (messages.size > 200) messages.takeLast(200) else messages
        prefs.edit().putString("messages", gson.toJson(trimmed)).apply()
    }

    // ─── Memories ────────────────────────────────────────────────────────

    fun loadMemories(): List<NyxMemory> {
        val json = prefs.getString("memories", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NyxMemory>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveMemories(memories: List<NyxMemory>) {
        prefs.edit().putString("memories", gson.toJson(memories)).apply()
    }

    // ─── API Config ──────────────────────────────────────────────────────

    fun loadApiConfig(): ApiConfig {
        val json = prefs.getString("api_config", null) ?: return ApiConfig()
        return try {
            gson.fromJson(json, ApiConfig::class.java) ?: ApiConfig()
        } catch (e: Exception) { ApiConfig() }
    }

    fun saveApiConfig(config: ApiConfig) {
        prefs.edit().putString("api_config", gson.toJson(config)).apply()
    }

    // ─── AI Calls ────────────────────────────────────────────────────────

    suspend fun callAI(
        config: ApiConfig,
        messages: List<Map<String, String>>,
        maxTokens: Int = 350
    ): String = withContext(Dispatchers.IO) {
        val msgArray = JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg["role"] ?: "user")
                    put("content", msg["content"] ?: "")
                })
            }
        }

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("messages", msgArray)
        }

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("空响应")

        if (!response.isSuccessful) {
            val errJson = try { JSONObject(responseBody) } catch (e: Exception) { null }
            val errMsg = errJson?.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
            throw Exception(errMsg)
        }

        JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    suspend fun extractMemories(
        config: ApiConfig,
        charId: String,
        userText: String,
        charReply: String
    ): List<NyxMemory> = withContext(Dispatchers.IO) {
        try {
            val prompt = """分析以下对话片段，提取0到2条值得长期记忆的事实（关于用户的偏好、经历、情感、重要信息）。
没有值得记录的内容就返回 []。
只返回JSON数组，格式：[{"content":"具体事实","importance":整数1到5}]

对话片段：
用户：$userText
角色：$charReply"""

            val result = callAI(
                config,
                listOf(mapOf("role" to "user", "content" to prompt)),
                maxTokens = 200
            )

            val clean = result.replace("```json", "").replace("```", "").trim()
            val arr = JSONArray(clean)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        NyxMemory(
                            id = "m${System.currentTimeMillis()}$i",
                            charId = charId,
                            content = obj.getString("content"),
                            importance = obj.optInt("importance", 2).coerceIn(1, 5),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun inferMood(
        config: ApiConfig,
        charId: String,
        recentText: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = """根据角色在下面对话中的表现，判断角色此刻的情绪。
只返回一个英文词，从以下选项中选择：neutral happy sad curious cold affectionate angry

对话：
$recentText"""
            val result = callAI(
                config,
                listOf(mapOf("role" to "user", "content" to prompt)),
                maxTokens = 10
            )
            val mood = result.trim().lowercase().split(Regex("\\s"))[0]
            if (MOOD_META.containsKey(mood)) mood else null
        } catch (e: Exception) { null }
    }
}

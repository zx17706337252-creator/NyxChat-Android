package com.zaijian.zhoumuyun.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容协议的通用 LLM 提供商实现。
 *
 * 支持：DeepSeek / 火山方舟 / 阿里云百炼 / 自定义 Base URL
 * 协议：OpenAI Chat Completions API（/v1/chat/completions）
 *
 * 使用原生 HttpURLConnection，无需额外依赖。
 * 流式输出解析 SSE（Server-Sent Events）格式。
 *
 * ⚠️ 流式修复（Phase 7）：
 *   原实现在 flow {} 内用 withContext(IO) 阻塞读取再把 delta 缓冲到
 *   list 最后统一 emit，打字机效果失效。
 *   现改为 callbackFlow：IO 读取线程直接 trySend(delta) 到 Channel，
 *   collect 端实时收到每个 token，真正实现逐字打字机效果。
 */
class OpenAICompatProvider(
    override val id: String,
    override val name: String,
    private val baseUrl: String,       // 如 "https://api.deepseek.com"
    private val apiKey: String,
    private val defaultModel: String,
) : LLMProvider {

    override suspend fun chat(
        messages: List<LLMMessage>,
        systemPrompt: String,
        config: LLMConfig,
    ): Flow<String> = callbackFlow {
        // 用一个后台 Job 做阻塞 IO 读取，awaitClose 负责取消它。
        // 这样 callbackFlow 生命周期与 IO 线程完全对齐：
        //   - 读取正常结束 → channel.close()
        //   - 下游取消收集 → awaitClose 的 lambda 取消 job → 断开连接
        val body = buildRequestBody(messages, systemPrompt, config.copy(stream = true))
        // prepareConnection() 只配置参数，不触发网络 IO，可在任何线程安全调用
        val conn = prepareConnection()

        val job = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // writeBodyAndConnect 是阻塞 IO，必须在此 IO 协程里执行
                conn.writeBodyAndConnect(body)
                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val error = conn.errorStream?.bufferedReader()?.readText()
                        ?: "HTTP $responseCode"
                    close(IllegalStateException("API 错误：$error"))
                    return@launch
                }
                // 实时解析 SSE 流，每个 delta 立即 trySend
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val data = line!!.removePrefix("data: ").trim()
                        if (data == "[DONE]" || data.isEmpty()) continue
                        try {
                            val deltaObj = JSONObject(data)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("delta")
                            // isNull() 专门检测 JSON null（区别于字段不存在）
                            // DeepSeek 流式开头会发 "content": null，
                            // optString 会把它转成字符串 "null"，必须用 isNull 先过滤
                            val delta = if (deltaObj.isNull("content")) ""
                                        else deltaObj.optString("content", "")
                            if (delta.isNotEmpty()) {
                                trySend(delta)   // 非挂起，线程安全，立即投递到 Channel
                            }
                        } catch (_: Exception) { /* 跳过解析失败的行 */ }
                    }
                }
                close()   // 正常结束：关闭 channel，collect 端会收到完成信号
            } catch (e: Exception) {
                close(e)  // 异常结束：把错误传给 collect 端
            } finally {
                conn.disconnect()
            }
        }

        // 当 collect 端取消时，取消 IO job 并断开连接
        awaitClose { job.cancel() }
    }

    override suspend fun chatSync(
        messages: List<LLMMessage>,
        systemPrompt: String,
        config: LLMConfig,
    ): String = withContext(Dispatchers.IO) {
        val body = buildRequestBody(messages, systemPrompt, config.copy(stream = false))
        val conn = prepareConnection()
        try {
            conn.writeBodyAndConnect(body)
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                throw IllegalStateException("API 错误：$error")
            }
            val responseText = conn.inputStream.bufferedReader().readText()
            JSONObject(responseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            chatSync(
                messages = listOf(LLMMessage("user", "Hi")),
                systemPrompt = "Reply with one word.",
                config = LLMConfig(model = defaultModel, maxTokens = 10, stream = false),
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── 内部工具 ─────────────────────────────────────────────

    private fun buildRequestBody(
        messages: List<LLMMessage>,
        systemPrompt: String,
        config: LLMConfig,
    ): String {
        val messagesArr = JSONArray()
        if (systemPrompt.isNotEmpty()) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        messages.forEach { msg ->
            messagesArr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }
        return JSONObject().apply {
            put("model", config.model.ifEmpty { defaultModel })
            put("messages", messagesArr)
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature.toDouble())
            put("stream", config.stream)
        }.toString()
    }

    /**
     * 只配置连接参数，不触发任何 IO（不写 body，不 connect）。
     * 实际的 body 写入和 connect() 必须在 IO 线程调用。
     */
    private fun prepareConnection(): HttpURLConnection {
        val url = URL("$baseUrl/chat/completions")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = 15_000
            readTimeout    = 60_000
            doOutput       = true
            // 注意：不在此处写 body、不 connect，留给 IO 线程执行
        }
    }

    /** 在 IO 线程调用：写入 request body，然后触发 connect。*/
    private fun HttpURLConnection.writeBodyAndConnect(body: String) {
        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        // outputStream.close() 已由 use 处理，HttpURLConnection 会在 getResponseCode() 时自动 connect
    }

    companion object {
        fun deepSeek(apiKey: String) = OpenAICompatProvider(
            id           = "deepseek",
            name         = "DeepSeek",
            baseUrl      = "https://api.deepseek.com/v1",
            apiKey       = apiKey,
            defaultModel = "deepseek-v4-flash",
        )

        fun volcEngine(apiKey: String) = OpenAICompatProvider(
            id           = "volcengine",
            name         = "火山方舟",
            // 火山方舟 OpenAI 兼容端点路径为 /api/v3，不是 /api/v1
            baseUrl      = "https://ark.cn-beijing.volces.com/api/v3",
            apiKey       = apiKey,
            // 预置接入点，无日期后缀
            defaultModel = "deepseek-v4-flash",
        )

        fun aliyun(apiKey: String) = OpenAICompatProvider(
            id           = "aliyun",
            name         = "阿里云百炼",
            baseUrl      = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey       = apiKey,
            defaultModel = "deepseek-v4-flash",
        )

        fun modelScope(apiKey: String) = OpenAICompatProvider(
            id           = "modelscope",
            name         = "魔搭",
            // 魔搭 baseUrl 已含 /v1，openConnection 只追加 /chat/completions
            baseUrl      = "https://api-inference.modelscope.cn/v1",
            apiKey       = apiKey,
            defaultModel = "deepseek-ai/DeepSeek-V4-Flash",
        )

        fun custom(baseUrl: String, apiKey: String, model: String) = OpenAICompatProvider(
            id           = "custom",
            name         = "自定义",
            baseUrl      = baseUrl,
            apiKey       = apiKey,
            defaultModel = model,
        )
    }
}

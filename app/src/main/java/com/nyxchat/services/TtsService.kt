package com.nyxchat.services

import android.content.Context
import android.media.MediaPlayer
import com.nyxchat.data.TtsConfig
import com.nyxchat.data.TtsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.nyxchat.data.HttpClientHolder
import org.json.JSONObject
import java.io.File

object AzureVoices {
    val CHINESE = listOf(
        "zh-CN-XiaoxiaoNeural"  to "晓晓·温暖",
        "zh-CN-XiaomoNeural"    to "晓墨·柔和",
        "zh-CN-XiaoruiNeural"   to "晓睿·冷静",
        "zh-CN-XiaoyiNeural"    to "晓伊·活泼",
        "zh-CN-XiaohanNeural"   to "晓涵·沉稳",
        "zh-CN-YunxiNeural"     to "云希·青年",
        "zh-CN-YunjianNeural"   to "云健·深沉",
        "zh-CN-YunyeNeural"     to "云野·磁性",
        "zh-CN-XiaoxuanNeural"  to "晓萱·知性",
    )
}

// Feature E: mood → prosody parameters
private data class Prosody(val rate: String, val pitch: String)

private fun moodProsody(mood: String): Prosody = when (mood) {
    "happy"        -> Prosody("+8%",  "+12%")   // faster, higher pitch
    "angry"        -> Prosody("+12%", "+5%")    // fast, forceful
    "sad"          -> Prosody("-12%", "-10%")   // slow, low pitch
    "cold"         -> Prosody("-5%",  "-8%")    // measured, slightly flat
    "affectionate" -> Prosody("-5%",  "+5%")    // gentle, warm
    "curious"      -> Prosody("+3%",  "+8%")    // slightly animated
    else           -> Prosody("0%",   "0%")     // neutral
}

class TtsService(context: Context) {

    private val appContext = context.applicationContext
    
    companion object {
        // TTS 相关常量
        private const val MAX_TTS_TEXT_LENGTH = 500
    }

    // Opt 2: 复用全局 HttpClientHolder，不再各自创建独立连接池
    private val http = HttpClientHolder.client

    init {
        // Bug fix ⑨: App 上次被强杀时 onCompletionListener 不会执行，
        // tts_*.mp3 临时文件永久残留在 cacheDir。
        // 每次 TtsService 初始化时（随 ViewModel 创建）扫描清理，
        // 防止主动消息频繁触发的用户长期积累占用存储。
        runCatching {
            appContext.cacheDir
                .listFiles { f -> f.name.startsWith("tts_") && f.name.endsWith(".mp3") }
                ?.forEach { it.delete() }
        }
    }

    @Volatile
    private var currentPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    suspend fun speak(text: String, voiceId: String, config: TtsConfig, mood: String = "neutral") {
        if (!config.enabled || text.isBlank()) return
        // 按提供商检查密钥是否填写
        val keyMissing = when (config.provider) {
            TtsProvider.Azure        -> config.subscriptionKey.isBlank()
            TtsProvider.AlibabaCloud -> config.alibabaDashscopeKey.isBlank()
            TtsProvider.Volcengine   -> config.volcengineAppId.isBlank() || config.volcengineToken.isBlank()
        }
        if (keyMissing) return
        withContext(Dispatchers.IO) {
            val file = File(appContext.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            try {
                val audioBytes = when (config.provider) {
                    TtsProvider.Azure        -> synthesizeAzure(text, voiceId, config, mood)
                    TtsProvider.AlibabaCloud -> synthesizeAlibaba(text, config)
                    TtsProvider.Volcengine   -> synthesizeVolcengine(text, config)
                }
                file.writeBytes(audioBytes)
                withContext(Dispatchers.Main) { playFile(file) }
            } catch (e: Exception) {
                android.util.Log.e("TtsService", "speak failed", e)
                file.runCatching { if (exists()) delete() }
            }
        }
    }

    // ── Azure TTS ─────────────────────────────────────────────────────────────
    private fun synthesizeAzure(text: String, voiceId: String, config: TtsConfig, mood: String): ByteArray {
        val ssml = buildSsml(text, voiceId, mood)
        val url  = "https://${config.region}.tts.speech.microsoft.com/cognitiveservices/v1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", config.subscriptionKey)
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
            .addHeader("User-Agent", "永恒之家")
            .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
            .build()

        // Phase-3 fix (Bug A): response.use {} 确保无论成功/失败/异常，
        // Response 都会被关闭，底层 TCP 连接得以归还 OkHttp 连接池。
        // 原代码在 !isSuccessful 时直接抛出，连接永久泄漏；连接池耗尽后 TTS 请求开始挂起。
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("TTS ${response.code}")
            response.body?.bytes() ?: throw Exception("Empty TTS response")
        }
    }

    // ── 阿里云百炼 CosyVoice ──────────────────────────────────────────────────
    // Phase-3 fix (Bug B): 去除 suspend 修饰符和冗余的 withContext(Dispatchers.IO)。
    // speak() 已在 withContext(IO) 块内调用此函数，同 dispatcher 的嵌套 withContext
    // 是纯开销（额外分配一个 Continuation 帧），且函数内无真正的 suspend 点。
    // 改为与 synthesizeAzure 一致的普通 fun，消除冗余协程开销。
    private fun synthesizeAlibaba(text: String, config: TtsConfig): ByteArray {
        // Bug 1 fix: voice 参数应放在 "parameters" 对象内作字符串值，
        // 而非顶层的 "voice": { "voice_id": "..." } 嵌套结构。
        val body = JSONObject().apply {
            put("model", "cosyvoice-v1")
            put("input", JSONObject().apply { put("text", text.take(MAX_TTS_TEXT_LENGTH)) })
            put("parameters", JSONObject().apply { put("voice", config.alibabaVoiceId) })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-to-speech")
            .addHeader("Authorization", "Bearer ${config.alibabaDashscopeKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        // Phase-3 fix (Bug A): response.use {} 防止连接泄漏
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("百炼TTS错误 ${response.code}")
            // Bug 2 fix: DashScope TTS 非流式响应为 JSON，音频在 output.audio（base64）中；
            // 原代码直接取 body.bytes() 会把 JSON 文本写入 .mp3 导致 MediaPlayer 解码失败。
            // Risk fix: 用安全 ?. 替换 !! 避免极端边缘情况（网络中断导致 body 提前关闭）下的 NPE。
            val bodyStr = response.body?.string() ?: throw Exception("百炼TTS 空响应体")
            val json = JSONObject(bodyStr)
            val b64  = json.getJSONObject("output").getString("audio")
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        }
    }

    // ── 火山方舟 TTS ───────────────────────────────────────────────────────────
    // Phase-3 fix (Bug B): 同 synthesizeAlibaba — 去除 suspend 和冗余 withContext
    private fun synthesizeVolcengine(text: String, config: TtsConfig): ByteArray {
        val reqId = java.util.UUID.randomUUID().toString()
        val body = JSONObject().apply {
            put("app", JSONObject().apply {
                put("appid",   config.volcengineAppId)
                put("token",   config.volcengineToken)
                put("cluster", "volcano_tts")
            })
            put("user",    JSONObject().apply { put("uid", "nyxchat") })
            put("audio",   JSONObject().apply {
                put("voice_type",   config.volcengineVoiceType)
                put("encoding",     "mp3")
                put("speed_ratio",  1.0)
                put("volume_ratio", 1.0)
            })
            put("request", JSONObject().apply {
                put("reqid",     reqId)
                put("text",      text.take(MAX_TTS_TEXT_LENGTH))
                put("operation", "query")
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openspeech.bytedance.com/api/v1/tts")
            .addHeader("Authorization", "Bearer;${config.volcengineToken}")
            .post(body)
            .build()

        // Phase-3 fix (Bug A): response.use {} 防止连接泄漏
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("方舟TTS错误 ${response.code}")
            // Risk fix: 用安全 ?. 替换 !! 避免极端边缘情况下的 NPE。
            val bodyStr = response.body?.string() ?: throw Exception("方舟TTS 空响应体")
            val json = JSONObject(bodyStr)
            val code = json.optInt("code", -1)
            if (code != 3000) throw Exception("方舟TTS业务错误 code=$code")
            val b64 = json.getJSONObject("data").getString("audio")
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        }
    }

    // Feature E: SSML with mood-based prosody
    private fun buildSsml(text: String, voiceId: String, mood: String): String {
        val clean = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").take(MAX_TTS_TEXT_LENGTH)
        val lang     = if (voiceId.startsWith("zh")) "zh-CN" else "en-US"
        val prosody  = moodProsody(mood)
        return """<speak version='1.0' xml:lang='$lang'>
  <voice name='$voiceId'>
    <prosody rate='${prosody.rate}' pitch='${prosody.pitch}'>$clean</prosody>
  </voice>
</speak>"""
    }

    // Phase-4 fix (Bug 3): 补上原注释承诺但未实际添加的 @MainThread 注解，
    // 明确声明 playFile/stopCurrent 的线程契约，防止未来误从 IO 线程调用。
    @androidx.annotation.MainThread
    private fun playFile(file: File) {
        // playFile 必须在 Main 线程调用（MediaPlayer 要求），stopCurrent 同理。
        // currentPlayer 虽标注 @Volatile 确保可见性，但 check-then-act 序列
        // 不是原子操作——依赖于调用方始终在 Main 线程（speak() 通过
        // withContext(Dispatchers.Main) 调用本方法，stopTts() 在 viewModelScope(Main)
        // 中调用 stopCurrent()），满足此条件。
        stopCurrent()
        currentFile = file

        try {
            currentPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                // Bug 2 fix: 先注册所有监听器，再调用同步的 prepare()，最后直接 start()。
                // 原代码 prepare() 在前、setOnPreparedListener 在后，同步 prepare 完成后
                // 监听器永远不会触发，导致 start() 从未被调用——TTS 完全无声。
                //
                // Phase-4 fix (Bug 1+2): 错误回调中补全状态清理。
                // 原代码只调用 release()，currentPlayer 仍持有已释放的 MediaPlayer 引用。
                // 之后任何 currentPlayer?.isPlaying（isPlaying() 或 stopCurrent() 内部）
                // 都对已释放的实例求值，抛出 IllegalStateException。
                // 同时补删临时文件，与 setOnCompletionListener 的清理路径对称。
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("TtsService", "MediaPlayer error: what=$what, extra=$extra")
                    release()
                    currentFile?.runCatching { delete() }   // Phase-4 fix (Bug 2): 清理泄漏的临时文件
                    currentFile = null
                    currentPlayer = null                    // Phase-4 fix (Bug 1): 清除悬空引用
                    false
                }
                setOnCompletionListener {
                    it.release()
                    file.runCatching { delete() }.onFailure { e ->
                        android.util.Log.e("TtsService", "Failed to delete file on completion", e)
                    }
                    currentFile = null
                    currentPlayer = null
                }
                prepare()   // 本地文件，同步准备即可
                start()     // prepare() 成功后直接播放
            }
        } catch (e: Exception) {
            android.util.Log.e("TtsService", "Failed to play audio file", e)
            stopCurrent()
        }
    }

    @androidx.annotation.MainThread
    fun stopCurrent() {
        currentPlayer?.let { player ->
            try {
                // 清理监听器以避免内存泄漏
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                player.setOnPreparedListener(null)
                
                // 停止并释放资源
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
                android.util.Log.d("TtsService", "MediaPlayer released successfully")
            } catch (e: Exception) {
                android.util.Log.e("TtsService", "Error releasing MediaPlayer", e)
            }
        }
        currentPlayer = null
        
        // 清理临时文件
        currentFile?.runCatching { delete() }?.onFailure { e ->
            android.util.Log.e("TtsService", "Failed to delete temp file", e)
        }
        currentFile = null
    }
    
    // 添加销毁方法，彻底清理资源
    fun destroy() {
        stopCurrent()
        // ⚠️ 严禁在此调用 http.dispatcher.executor.shutdown()
        // http 是 HttpClientHolder.client 全局单例，NyxRepository 也持有同一实例。
        // 关闭其 executor 会使 ViewModel 销毁后所有 API 调用抛出 RejectedExecutionException，
        // 导致 App 永久无法联网（直到进程重启）。
        // OkHttpClient 单例无需手动关闭，GC 会在无引用时自动回收。
    }

    fun isPlaying() = currentPlayer?.isPlaying == true
}

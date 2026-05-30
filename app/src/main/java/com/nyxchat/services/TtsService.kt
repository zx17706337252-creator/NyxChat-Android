package com.nyxchat.services

import android.content.Context
import android.media.MediaPlayer
import com.nyxchat.data.TtsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.nyxchat.data.HttpClientHolder
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
        if (!config.enabled || config.subscriptionKey.isBlank() || text.isBlank()) return
        withContext(Dispatchers.IO) {
            val file = File(appContext.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            try {
                val audioBytes = synthesize(text, voiceId, config, mood)
                file.writeBytes(audioBytes)
                withContext(Dispatchers.Main) { playFile(file) }
            } catch (e: Exception) {
                android.util.Log.e("TtsService", "speak failed", e)
                // Bug fix: synthesize/write 失败后确保临时文件被清理，防止缓存目录堆积孤文件
                file.runCatching { if (exists()) delete() }
            }
        }
    }

    private fun synthesize(text: String, voiceId: String, config: TtsConfig, mood: String): ByteArray {
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

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("TTS ${response.code}")
        return response.body?.bytes() ?: throw Exception("Empty TTS response")
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

    private fun playFile(file: File) {
        // Bug fix: playFile 必须在 Main 线程调用（MediaPlayer 要求），stopCurrent 同理。
        // currentPlayer 虽标注 @Volatile 确保可见性，但 check-then-act 序列（playFile/stopCurrent）
        // 不是原子操作——依赖于调用方始终在 Main 线程（speak() 通过 withContext(Dispatchers.Main)
        // 调用本方法，stopTts() 在 viewModelScope（Main）中调用 stopCurrent()），满足此条件。
        // 用 @androidx.annotation.MainThread 注解声明意图；如未来在 IO 线程调用需改用 Mutex。
        stopCurrent()
        currentFile = file
        
        try {
            currentPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                // Bug 2 fix: 先注册所有监听器，再调用同步的 prepare()，最后直接 start()。
                // 原代码 prepare() 在前、setOnPreparedListener 在后，同步 prepare 完成后
                // 监听器永远不会触发，导致 start() 从未被调用——TTS 完全无声。
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("TtsService", "MediaPlayer error: what=$what, extra=$extra")
                    release()
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

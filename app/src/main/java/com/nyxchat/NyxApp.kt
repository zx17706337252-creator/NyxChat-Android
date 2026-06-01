package com.nyxchat

import android.app.Application
import androidx.work.Configuration
import com.nyxchat.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@HiltAndroidApp
class NyxApp : Application(), Configuration.Provider {

    /**
     * Bug 6 fix: Worker 路径的 TTS 请求通过此收件箱转交 ViewModel 统一播放。
     * ProactiveWorker 投递 (text, voiceId, mood)；ChatViewModel 监听并转入自己的 ttsQueue。
     * - replay=0: ViewModel 未在监听时（App 在后台）自动丢弃，后台无界面不应播音频。
     * - extraBufferCapacity=1 + DROP_OLDEST: tryEmit 不挂起，Worker 不会因此阻塞。
     */
    val proactiveTtsChannel = MutableSharedFlow<Triple<String, String, String>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}

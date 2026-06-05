package com.zaijian.zhoumuyun.data.provider

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API 提供商管理器。
 *
 * 职责：
 * - 存储 / 读取 API Key（EncryptedSharedPreferences，不明文落盘）
 * - 管理当前活跃 Provider 实例
 * - 提供快捷的 activeProvider 供 ChatViewModel 调用
 *
 * 使用方式（单例，由 Application 初始化）：
 *   ProviderManager.init(applicationContext)
 *   ProviderManager.instance.activeProvider
 */
class ProviderManager private constructor(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "zaijian_api_keys",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ── Key 存取 ─────────────────────────────────────────────

    fun saveKey(providerId: String, apiKey: String) {
        prefs.edit().putString("key_$providerId", apiKey).commit()
    }

    fun getKey(providerId: String): String? =
        prefs.getString("key_$providerId", null)

    fun saveActiveProviderId(providerId: String) {
        prefs.edit().putString("active_provider", providerId).commit()
    }

    fun getActiveProviderId(): String =
        prefs.getString("active_provider", "deepseek") ?: "deepseek"

    fun saveCustomBaseUrl(url: String) {
        prefs.edit().putString("custom_base_url", url).commit()
    }

    fun getCustomBaseUrl(): String =
        prefs.getString("custom_base_url", "") ?: ""

    fun saveCustomModel(model: String) {
        prefs.edit().putString("custom_model", model).commit()
    }

    fun getCustomModel(): String =
        prefs.getString("custom_model", "") ?: ""

    // ── 获取活跃 Provider ────────────────────────────────────

    /**
     * 根据当前配置构建活跃 Provider 实例。
     * 如果 API Key 未配置，返回 null（UI 层提示用户配置）。
     */
    val activeProvider: LLMProvider?
        get() {
            val id = getActiveProviderId()
            return when (id) {
                "deepseek" -> {
                    val key = getKey("deepseek") ?: return null
                    OpenAICompatProvider.deepSeek(key)
                }
                "volcengine" -> {
                    val key = getKey("volcengine") ?: return null
                    OpenAICompatProvider.volcEngine(key)
                }
                "aliyun" -> {
                    val key = getKey("aliyun") ?: return null
                    OpenAICompatProvider.aliyun(key)
                }
                "modelscope" -> {
                    val key = getKey("modelscope") ?: return null
                    OpenAICompatProvider.modelScope(key)
                }
                "custom" -> {
                    val key = getKey("custom") ?: return null
                    val url = getCustomBaseUrl().ifEmpty { return null }
                    val model = getCustomModel().ifEmpty { return null }
                    OpenAICompatProvider.custom(url, key, model)
                }
                else -> null
            }
        }

    companion object {
        @Volatile
        private var INSTANCE: ProviderManager? = null

        fun init(context: Context): ProviderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProviderManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        val instance: ProviderManager
            get() = INSTANCE ?: throw IllegalStateException(
                "ProviderManager 未初始化，请在 Application.onCreate() 中调用 ProviderManager.init()"
            )
    }
}

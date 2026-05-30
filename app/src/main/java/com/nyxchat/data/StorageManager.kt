package com.nyxchat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一存储管理器
 * - 敏感数据使用 EncryptedSharedPreferences 加密存储
 * - 普通数据使用普通 SharedPreferences
 * - 提供统一的读写接口
 */
class StorageManager(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    companion object {
        @Volatile
        private var instance: StorageManager? = null

        /**
         * 单例入口：Worker / 无法注入 Hilt 的场景使用此方法。
         * Hilt 主路径通过 RepositoryModule.provideStorageManager 调用此方法，
         * 两条路径共享同一实例，保证 EncryptedSharedPreferences / MasterKey 只初始化一次。
         */
        fun getInstance(context: Context): StorageManager =
            instance ?: synchronized(this) {
                instance ?: StorageManager(context.applicationContext).also { instance = it }
            }
    }

    // 普通存储（非敏感数据）
    private val prefs: SharedPreferences = appContext.getSharedPreferences("nyx_data", Context.MODE_PRIVATE)

    // 加密存储（敏感数据如API Key）
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "nyx_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── 普通存储读写 ─────────────────────────────────────────────────────────────

    internal inline fun <reified T> load(key: String, default: T): T {
        val json = prefs.getString(key, null) ?: return default
        return try { gson.fromJson(json, object : TypeToken<T>() {}.type) ?: default }
        catch (e: Exception) { default }
    }

    fun save(key: String, v: Any) = prefs.edit().putString(key, gson.toJson(v)).apply()

    fun loadBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun saveBoolean(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    fun loadStringSet(key: String, default: Set<String> = emptySet()): Set<String> =
        prefs.getStringSet(key, default) ?: default
    fun saveStringSet(key: String, v: Set<String>) = prefs.edit().putStringSet(key, v).apply()

    // ─── 加密存储读写（敏感数据）───────────────────────────────────────────────────

    fun loadSecureString(key: String, default: String = ""): String =
        encryptedPrefs.getString(key, default) ?: default

    fun saveSecureString(key: String, v: String) = encryptedPrefs.edit().putString(key, v).apply()

    internal inline fun <reified T> loadSecure(key: String, default: T): T {
        val json = encryptedPrefs.getString(key, null) ?: return default
        return try { gson.fromJson(json, object : TypeToken<T>() {}.type) ?: default }
        catch (e: Exception) { default }
    }

    fun saveSecure(key: String, v: Any) = encryptedPrefs.edit().putString(key, gson.toJson(v)).apply()

    // ─── 清除数据 ─────────────────────────────────────────────────────────────────

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        encryptedPrefs.edit().clear().apply()
    }
}

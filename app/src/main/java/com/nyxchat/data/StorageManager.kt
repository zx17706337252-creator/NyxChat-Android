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
    // Fix: 部分国产 ROM（如 realme/ColorOS）KeyStore 初始化会抛异常导致闪退，
    // 加 try-catch 降级到普通 SharedPreferences，保证 App 可以正常启动。
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "EncryptedSharedPreferences 初始化失败，降级到普通存储", e)
            // 降级：删除可能已损坏的加密文件，再用普通 SharedPreferences 顶替
            try {
                appContext.deleteSharedPreferences("nyx_secure")
            } catch (_: Exception) {}
            appContext.getSharedPreferences("nyx_secure_plain", Context.MODE_PRIVATE)
        }
    }

    // ─── 普通存储读写 ─────────────────────────────────────────────────────────────

    // Fix: Gson 2.10+ 禁止在非 inline 函数里用 TypeToken<T>（T 是类型变量会抛 IllegalArgumentException）。
    // 解决方案：loadJson 改为直接接收 java.lang.reflect.Type，
    // 调用方在 inline reified 函数里用 typeOf<T>().javaType 传入，此时 T 已被具体化，不含类型变量。
    @PublishedApi internal fun <T> loadJson(prefs: SharedPreferences, key: String, type: java.lang.reflect.Type, default: T): T {
        val json = prefs.getString(key, null) ?: return default
        return try { gson.fromJson(json, type) ?: default } catch (e: Exception) { default }
    }

    inline fun <reified T> load(key: String, default: T): T =
        loadJson(prefs, key, com.google.gson.reflect.TypeToken.get(T::class.java).type, default)

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

    inline fun <reified T> loadSecure(key: String, default: T): T =
        loadJson(encryptedPrefs, key, com.google.gson.reflect.TypeToken.get(T::class.java).type, default)

    fun saveSecure(key: String, v: Any) = encryptedPrefs.edit().putString(key, gson.toJson(v)).apply()

    // ─── 清除数据 ─────────────────────────────────────────────────────────────────

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        encryptedPrefs.edit().clear().apply()
    }
}

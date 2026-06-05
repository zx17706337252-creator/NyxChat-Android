package com.zaijian.zhoumuyun

import android.app.Application
import com.zaijian.zhoumuyun.data.db.AppDatabase
import com.zaijian.zhoumuyun.data.provider.ProviderManager

/**
 * Application 入口。
 * 负责初始化数据库和 Provider 管理器（单例）。
 */
class ZaijianApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化 Room 数据库
        AppDatabase.getInstance(this)
        // 初始化 API Provider 管理器（EncryptedSharedPreferences）
        ProviderManager.init(this)
    }
}

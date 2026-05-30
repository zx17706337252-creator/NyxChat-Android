package com.nyxchat.di

import android.content.Context
import com.nyxchat.data.NyxRepository
import com.nyxchat.data.StorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager {
        // 通过 getInstance() 获取单例，与 Worker 路径共享同一实例，
        // 保证 EncryptedSharedPreferences / MasterKey 在整个 App 生命周期内只初始化一次。
        return StorageManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideNyxRepository(
        @ApplicationContext context: Context,
        storage: StorageManager        // Bug fix: 注入单例 StorageManager，不再在 Repository 内重复创建
    ): NyxRepository {
        return NyxRepository(context, storage)
    }
}

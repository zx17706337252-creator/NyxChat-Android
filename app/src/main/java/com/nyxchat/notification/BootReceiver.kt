package com.nyxchat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.nyxchat.data.NyxRepository

// 设备重启后重新调度所有主动推送 Worker
// 修复：将耗时操作移至 WorkManager 避免 ANR
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 异步执行以避免 ANR
        val work = OneTimeWorkRequestBuilder<BootSchedWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }
}

// 修复：独立的 Worker 执行耗时调度操作
class BootSchedWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = NyxRepository(applicationContext)
            repo.loadCharacters()
                .filter { it.proactiveConfig.enabled }
                .forEach { ProactiveWorker.scheduleNext(applicationContext, it) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

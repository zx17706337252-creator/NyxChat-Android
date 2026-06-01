package com.nyxchat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.*

// 修复⑧：onReceive 不再做任何 IO（移除 repo/loadCharacters/showTyping）。
// showTyping 移至 ReplyWorker.doWork() 开头，在后台线程执行，彻底消除 ANR 风险。
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_REPLY) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_REPLY_TEXT)
            ?.toString()?.trim() ?: return

        val charId  = intent.getStringExtra(NotificationHelper.EXTRA_CHAR_ID) ?: return
        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIF_ID, 0)

        // 调度 ReplyWorker 处理完整流程（showTyping、保存消息、AI 回复）
        val work = OneTimeWorkRequestBuilder<ReplyWorker>()
            .setInputData(workDataOf(
                "char_id"  to charId,
                "notif_id" to notifId,
                "user_msg" to replyText
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("reply_$charId", ExistingWorkPolicy.REPLACE, work)
    }
}

package com.nyxchat.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.nyxchat.MainActivity
import com.nyxchat.data.NyxCharacter
import java.io.File
import com.nyxchat.R

object NotificationHelper {

    const val KEY_REPLY_TEXT  = "key_reply_text"
    const val ACTION_REPLY    = "com.nyxchat.ACTION_REPLY"
    const val ACTION_OPEN     = "com.nyxchat.ACTION_OPEN"
    const val EXTRA_CHAR_ID   = "char_id"
    const val EXTRA_NOTIF_ID  = "notif_id"
    /** PendingIntent 请求码偏移：Open 动作，避免与回复动作的请求码冲突 */
    private const val OPEN_PENDING_REQUEST_OFFSET  = 10_000
    /** PendingIntent 请求码偏移：Error 通知的 Open 动作 */
    private const val ERROR_PENDING_REQUEST_OFFSET = 20_000

    // Each character gets its own notification channel
    fun ensureChannel(context: Context, char: NyxCharacter) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId(char.id)) != null) return

        val channel = NotificationChannel(
            channelId(char.id),
            char.name,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description, char.name)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 100, 80, 100)
        }
        manager.createNotificationChannel(channel)
    }

    fun showMessage(context: Context, char: NyxCharacter, message: String, notifId: Int) {
        ensureChannel(context, char)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Inline reply action
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_label, char.name))
            .build()

        val replyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CHAR_ID, char.id)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val replyPending = PendingIntent.getBroadcast(
            context, notifId, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, context.getString(R.string.notification_reply_action), replyPending
        ).addRemoteInput(remoteInput).build()

        // Open app action
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAR_ID, char.id)
        }
        val openPending = PendingIntent.getActivity(
            context, notifId + OPEN_PENDING_REQUEST_OFFSET, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Load avatar if exists
        val largeIcon = if (char.avatarPath.isNotBlank()) {
            val file = File(char.avatarPath)
            if (file.exists()) BitmapFactory.decodeFile(char.avatarPath) else null
        } else null

        val notif = NotificationCompat.Builder(context, channelId(char.id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(char.name)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setAutoCancel(false)  // keep until user acts
            .addAction(replyAction)
            .setLargeIcon(largeIcon)
            .setColor(char.colorArgb.toInt())   // Bug fix: Color.hashCode() ≠ ARGB int，应用 colorArgb
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(notifId, notif)
    }

    // Show "typing" placeholder while AI is generating reply
    fun showTyping(context: Context, char: NyxCharacter, notifId: Int) {
        ensureChannel(context, char)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, channelId(char.id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(char.name)
            .setContentText(context.getString(R.string.notification_typing_text))
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(notifId, notif)
    }

    fun dismiss(context: Context, notifId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notifId)
    }

    /** 显示错误通知（如 API 未配置、回复失败等），以文本形式呈现，无回复操作 */
    fun showError(context: Context, char: NyxCharacter, message: String, notifId: Int) {
        ensureChannel(context, char)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAR_ID, char.id)
        }
        val openPending = PendingIntent.getActivity(
            context, notifId + ERROR_PENDING_REQUEST_OFFSET, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channelId(char.id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(char.name)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        manager.notify(notifId, notif)
    }

    fun channelId(charId: String) = "nyx_char_$charId"

    // Stable notification ID per character (for message threading)
    fun notifIdForChar(charId: String) = charId.hashCode() and 0x7FFFFFFF
}

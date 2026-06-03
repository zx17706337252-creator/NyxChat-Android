package com.nyxchat.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 轻量崩溃日志记录器
 * 将未捕获异常写入 files/crash_logs/ 目录，供 SettingsScreen 读取和清除
 */
object CrashLogger {

    private const val LOG_DIR = "crash_logs"
    private const val MAX_LOGS = 10

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLog(appCtx, thread, throwable)
            } catch (_: Exception) { /* 写日志失败不能再抛 */ }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$ts.txt")
        file.printWriter().use { pw ->
            pw.println("Time   : $ts")
            pw.println("Thread : ${thread.name}")
            pw.println("---")
            throwable.printStackTrace(pw)
        }
        // 超出上限则删除最旧的
        val logs = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (logs.size > MAX_LOGS) logs.take(logs.size - MAX_LOGS).forEach { it.delete() }
    }

    /** 返回所有崩溃日志文件（按时间倒序） */
    fun getCrashLogs(context: Context): List<File> =
        File(context.filesDir, LOG_DIR)
            .takeIf { it.exists() }
            ?.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 清除全部崩溃日志 */
    fun clearLogs(context: Context) {
        File(context.filesDir, LOG_DIR).listFiles()?.forEach { it.delete() }
    }
}

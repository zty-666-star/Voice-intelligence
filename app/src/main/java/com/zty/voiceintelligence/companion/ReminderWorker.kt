package com.zty.voiceintelligence.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zty.voiceintelligence.companion.ui.ReminderCenterActivity

/**
 * P3 伴生模块核心技术：WorkManager 后台定时触发器
 * 支持应用退出后准时弹出高优先级通知，并提供「一键完成」与「延后5分钟」两个通知栏直接操作按钮
 */
class ReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "voice_companion_reminder_channel"
        const val KEY_REMINDER_ID = "key_reminder_id"
        const val KEY_TITLE = "key_title"
        const val KEY_RAW_CMD = "key_raw_cmd"

        const val ACTION_COMPLETE = "com.zty.voiceintelligence.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "com.zty.voiceintelligence.ACTION_SNOOZE"
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, 1L)
        val title = inputData.getString(KEY_TITLE) ?: "定时语音提醒"
        val rawCmd = inputData.getString(KEY_RAW_CMD) ?: "来自智音助手的提醒事项"

        showInteractiveNotification(reminderId, title, rawCmd)
        return Result.success()
    }

    private fun showInteractiveNotification(reminderId: Long, title: String, rawCmd: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "智音伴生提醒中心",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于展示语音助手创建的定时提醒，支持通知内直接完成或延后"
            }
            nm.createNotificationChannel(channel)
        }

        // 点击通知本体打开伴生模块 P3 提醒中心
        val openIntent = Intent(appContext, ReminderCenterActivity::class.java)
        val openPi = PendingIntent.getActivity(
            appContext,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知栏按钮 1：直接标记已完成
        val completeIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra(KEY_REMINDER_ID, reminderId)
        }
        val completePi = PendingIntent.getBroadcast(
            appContext,
            reminderId.toInt() + 10000,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知栏按钮 2：直接延后 5 分钟
        val snoozeIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(KEY_REMINDER_ID, reminderId)
            putExtra(KEY_TITLE, title)
            putExtra(KEY_RAW_CMD, rawCmd)
        }
        val snoozePi = PendingIntent.getBroadcast(
            appContext,
            reminderId.toInt() + 20000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("【定时提醒】$title")
            .setContentText("原指令：$rawCmd")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "一键完成", completePi)
            .addAction(android.R.drawable.ic_media_pause, "延后5分钟", snoozePi)
            .build()

        nm.notify(reminderId.toInt(), notification)
    }
}

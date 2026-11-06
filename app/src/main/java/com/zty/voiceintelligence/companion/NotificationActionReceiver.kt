package com.zty.voiceintelligence.companion

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zty.voiceintelligence.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * P3 伴生模块通知栏直接操作广播接收器
 * 无需打开 App 即可在下拉通知栏直接修改主 App 的 Room 数据库状态并重调度 WorkManager
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderWorker.KEY_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId.toInt())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).reminderDao()
                when (intent.action) {
                    ReminderWorker.ACTION_COMPLETE -> {
                        dao.updateCompletedStatus(reminderId, true)
                        WorkManager.getInstance(context).cancelUniqueWork("reminder_work_$reminderId")
                    }
                    ReminderWorker.ACTION_SNOOZE -> {
                        val newTime = System.currentTimeMillis() + 5 * 60 * 1000L
                        dao.snoozeReminder(reminderId, newTime)
                        val title = intent.getStringExtra(ReminderWorker.KEY_TITLE) ?: "延后提醒"
                        val rawCmd = intent.getStringExtra(ReminderWorker.KEY_RAW_CMD) ?: ""
                        val data = Data.Builder()
                            .putLong(ReminderWorker.KEY_REMINDER_ID, reminderId)
                            .putString(ReminderWorker.KEY_TITLE, title)
                            .putString(ReminderWorker.KEY_RAW_CMD, rawCmd)
                            .build()
                        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                            .setInitialDelay(5, TimeUnit.MINUTES)
                            .setInputData(data)
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "reminder_work_$reminderId",
                            ExistingWorkPolicy.REPLACE,
                            req
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }

        val toastText = if (intent.action == ReminderWorker.ACTION_COMPLETE) {
            "已在通知栏直接完成该提醒事项！"
        } else {
            "已延后 5 分钟再次提醒！"
        }
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }
}

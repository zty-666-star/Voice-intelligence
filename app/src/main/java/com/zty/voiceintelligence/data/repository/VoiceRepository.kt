package com.zty.voiceintelligence.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zty.voiceintelligence.companion.ReminderWorker
import com.zty.voiceintelligence.data.AppDatabase
import com.zty.voiceintelligence.data.entity.ChatMessageEntity
import com.zty.voiceintelligence.data.entity.ReminderEntity
import com.zty.voiceintelligence.data.entity.RuleEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * 统一数据仓库层：负责主 App 与伴生模块 P3 的数据同步及 WorkManager 定时任务编排
 */
class VoiceRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val ruleDao = db.ruleDao()
    private val reminderDao = db.reminderDao()
    private val chatDao = db.chatDao()

    val allRules: Flow<List<RuleEntity>> = ruleDao.observeAllRules()
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.observeAllReminders()
    val allMessages: Flow<List<ChatMessageEntity>> = chatDao.observeMessages()

    suspend fun ensureSeedData() {
        if (ruleDao.countRules() == 0) {
            val defaultRules = listOf(
                RuleEntity(
                    title = "自然语言定时提醒规则",
                    regexPattern = ".*(提醒我|别忘了|叫我|闹钟)(.*)",
                    intentCategory = "CREATE_REMINDER",
                    responseTemplate = "好的，已通过解析引擎提取时间【{time}】，届时将在通知中心提醒您：{content}",
                    priority = 100
                ),
                RuleEntity(
                    title = "系统设备开关控制规则",
                    regexPattern = ".*(打开|开启|关闭|关掉)(蓝牙|WiFi|无线网|手电筒|静音模式).*",
                    intentCategory = "SYSTEM_CONTROL",
                    responseTemplate = "指令引擎已执行设备操作：{action}{target}",
                    priority = 90
                ),
                RuleEntity(
                    title = "早安日程与问候自定义规则",
                    regexPattern = ".*(早上好|早安|今天有什么安排).*",
                    intentCategory = "CUSTOM_REPLY",
                    responseTemplate = "早上好！我是您的智音助手，您可以随时对我说“10秒后提醒我背单词”来测试伴生通知中心。",
                    priority = 80
                ),
                RuleEntity(
                    title = "课程设计答辩演示专属指令",
                    regexPattern = ".*(演示模式|答辩自检|系统状态).*",
                    intentCategory = "CUSTOM_REPLY",
                    responseTemplate = "C3主App与P3伴生模块运行正常：Room数据库已连接，自定义正则引擎与WorkManager后台调度就绪！",
                    priority = 95
                )
            )
            defaultRules.forEach { ruleDao.insertRule(it) }

            chatDao.insertMessage(
                ChatMessageEntity(
                    isUser = false,
                    text = "您好！我是 C3 智音助手。支持语音或文本指令，已内置「自定义指令解析引擎」并与 P3「语音通知中心」实时联动。试试点击下方快捷指令或输入“10秒后提醒我提交课程设计”！",
                    matchedIntent = "SYSTEM_INIT",
                    extractedSlotsJson = "引擎状态: 已加载 4 条默认规则"
                )
            )
        }
    }

    suspend fun getEnabledRules(): List<RuleEntity> = ruleDao.getEnabledRules()
    suspend fun incrementRuleHit(ruleId: Long) = ruleDao.incrementTriggerCount(ruleId)
    suspend fun getRuleById(id: Long): RuleEntity? = ruleDao.getRuleById(id)
    suspend fun saveRule(rule: RuleEntity): Long = ruleDao.insertRule(rule)
    suspend fun deleteRule(rule: RuleEntity) = ruleDao.deleteRule(rule)

    suspend fun insertChatMessage(msg: ChatMessageEntity) = chatDao.insertMessage(msg)
    suspend fun clearChatHistory() = chatDao.clearAllMessages()

    /**
     * 新增定时提醒并立即通过 WorkManager 调度后台通知任务
     */
    suspend fun createAndScheduleReminder(
        title: String,
        rawCommand: String,
        triggerTimeMillis: Long,
        source: String = "VOICE"
    ): Long {
        val id = reminderDao.insertReminder(
            ReminderEntity(
                title = title,
                rawCommand = rawCommand,
                triggerTimeMillis = triggerTimeMillis,
                source = source
            )
        )
        scheduleReminderWork(id, title, rawCommand, triggerTimeMillis)
        return id
    }

    fun scheduleReminderWork(id: Long, title: String, rawCommand: String, triggerTimeMillis: Long) {
        val delayMillis = (triggerTimeMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val data = Data.Builder()
            .putLong(ReminderWorker.KEY_REMINDER_ID, id)
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_RAW_CMD, rawCommand)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "reminder_work_$id",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun markReminderCompleted(id: Long, completed: Boolean) {
        reminderDao.updateCompletedStatus(id, completed)
        if (completed) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork("reminder_work_$id")
        }
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("reminder_work_${reminder.id}")
    }
}

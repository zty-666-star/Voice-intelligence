package com.zty.voiceintelligence.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zty.voiceintelligence.core.device.DeviceController
import com.zty.voiceintelligence.core.engine.CommandRuleEngine
import com.zty.voiceintelligence.core.engine.model.IntentCategory
import com.zty.voiceintelligence.core.engine.model.SlotKey
import com.zty.voiceintelligence.data.entity.ChatMessageEntity
import com.zty.voiceintelligence.data.repository.VoiceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VoiceRepository(application)
    private val deviceController = DeviceController(application)

    private val engine = CommandRuleEngine(
        loadEnabledRules = { repository.getEnabledRules() },
        onRuleTriggered = { ruleId -> repository.incrementRuleHit(ruleId) }
    )

    val messages = repository.allMessages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
    }

    fun processUserCommand(
        rawText: String,
        onSpeakReply: (String) -> Unit,
        onOpenReminderCenter: () -> Unit = {}
    ) {
        val clean = rawText.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            // 1. 记录用户原始消息
            repository.insertChatMessage(
                ChatMessageEntity(
                    isUser = true,
                    text = clean,
                    matchedIntent = null,
                    extractedSlotsJson = null
                )
            )

            // 2. 调用自定义指令解析引擎
            val result = engine.parse(clean)
            var reply = result.replyMessage

            // 3. 若命中创建提醒意图，自动写入共享数据库并触发伴生模块 WorkManager
            if (result.intent == IntentCategory.CREATE_REMINDER && result.triggerTimestampMillis != null) {
                val taskTitle = result.slots[SlotKey.TASK_CONTENT] ?: clean
                repository.createAndScheduleReminder(
                    title = taskTitle,
                    rawCommand = clean,
                    triggerTimeMillis = result.triggerTimestampMillis,
                    source = "VOICE_ENGINE"
                )
            }

            // 4. 系统控制意图：真正调用设备 API，并把真实结果追加到回复里
            if (result.intent == IntentCategory.SYSTEM_CONTROL) {
                val action = result.slots[SlotKey.DEVICE_ACTION] ?: "操作"
                val target = result.slots[SlotKey.DEVICE_TARGET] ?: "设备"
                val outcome = deviceController.execute(action, target)
                reply = "$reply\n$outcome"
            }

            // 5. 查询待办意图：直接跳到伴生模块提醒中心
            if (result.intent == IntentCategory.QUERY_REMINDER) {
                onOpenReminderCenter()
            }

            // 6. 保存助手响应与槽位解析详情
            repository.insertChatMessage(
                ChatMessageEntity(
                    isUser = false,
                    text = reply,
                    matchedIntent = result.intent.name,
                    extractedSlotsJson = "[${result.matchedRuleTitle}] ${result.formatSlotsBadge()} (置信度:${(result.confidence * 100).toInt()}%)",
                    confidence = result.confidence
                )
            )

            // 7. 触发 TTS 语音播报
            onSpeakReply(reply)
        }
    }
}

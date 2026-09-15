package com.zty.voiceintelligence.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 语音助手对话与指令解析历史记录表
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isUser: Boolean,             // true: 用户输入, false: 助手响应
    val text: String,                // 消息文本
    val matchedIntent: String?,      // 命中的意图类别
    val extractedSlotsJson: String?, // 抽取出的槽位概要（用于界面透明化展示引擎推理过程）
    val confidence: Float = 1.0f,    // 置信度
    val timestamp: Long = System.currentTimeMillis()
)

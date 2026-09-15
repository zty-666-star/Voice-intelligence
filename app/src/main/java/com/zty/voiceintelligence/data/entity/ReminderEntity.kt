package com.zty.voiceintelligence.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时提醒事项表（主 App C3 与伴生模块 P3 共享核心数据表）
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,               // 提醒事项内容，如"交移动开发作业"
    val rawCommand: String,          // 原始语音指令，如"10分钟后提醒我交移动开发作业"
    val triggerTimeMillis: Long,     // 触发毫秒时间戳
    val isCompleted: Boolean = false,// 是否已完成
    val snoozeCount: Int = 0,        // 延后次数
    val source: String = "VOICE",    // 来源：VOICE (主App语音创建) 或 COMPANION (伴生模块创建)
    val createdAt: Long = System.currentTimeMillis()
)

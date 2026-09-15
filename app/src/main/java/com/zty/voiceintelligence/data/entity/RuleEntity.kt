package com.zty.voiceintelligence.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自定义指令解析规则表
 * 支持用户动态增删改规则、配置正则表达式、意图类型、优先级与回复模板
 */
@Entity(tableName = "command_rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,               // 规则名称，如"创建定时提醒"
    val regexPattern: String,        // 正则匹配表达式，如".*(提醒我|别忘了|叫我)(.*)"
    val intentCategory: String,      // 意图枚举名：CREATE_REMINDER / CHECK_WEATHER / SYSTEM_CONTROL / CUSTOM_REPLY
    val responseTemplate: String,    // 响应话术模板，支持 {time}, {content}, {target} 槽位替换
    val priority: Int = 10,          // 匹配优先级（越大越优先匹配）
    val isEnabled: Boolean = true,   // 规则开关
    val triggerCount: Int = 0,       // 累计命中次数（用于统计面板）
    val updatedAt: Long = System.currentTimeMillis()
)

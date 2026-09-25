package com.zty.voiceintelligence.core.engine.model

/**
 * 语音指令意图分类
 */
enum class IntentCategory(val displayName: String) {
    CREATE_REMINDER("定时提醒调度"),
    QUERY_REMINDER("查询待办提醒"),
    SYSTEM_CONTROL("系统设备控制"),
    QUICK_CALCULATE("智能四则运算"),
    CUSTOM_REPLY("自定义规则问答"),
    UNKNOWN("未知意图兜底")
}

/**
 * 语义槽位键（Slot Filling）
 */
enum class SlotKey(val label: String) {
    TARGET_TIME("目标时间"),
    DELAY_MINUTES("延迟分钟"),
    TASK_CONTENT("事项内容"),
    DEVICE_TARGET("系统设备"),
    DEVICE_ACTION("控制动作"),
    MATH_EXPRESSION("算术表达式"),
    MATH_RESULT("计算结果")
}

/**
 * 指令解析引擎输出的标准化结构
 */
data class ParseResult(
    val matchedRuleId: Long?,
    val matchedRuleTitle: String,
    val intent: IntentCategory,
    val confidence: Float,
    val slots: Map<SlotKey, String>,
    val triggerTimestampMillis: Long?,
    val rawText: String,
    val replyMessage: String
) {
    fun formatSlotsBadge(): String {
        if (slots.isEmpty()) return "意图: ${intent.displayName} (无槽位)"
        val pairs = slots.entries.joinToString(" | ") { "${it.key.label}=${it.value}" }
        return "意图: ${intent.displayName} | $pairs"
    }
}

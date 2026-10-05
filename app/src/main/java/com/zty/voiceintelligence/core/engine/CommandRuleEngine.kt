package com.zty.voiceintelligence.core.engine

import com.zty.voiceintelligence.core.engine.model.IntentCategory
import com.zty.voiceintelligence.core.engine.model.ParseResult
import com.zty.voiceintelligence.core.engine.model.SlotKey
import com.zty.voiceintelligence.data.entity.RuleEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 必做核心模块：自定义指令解析引擎 (CommandRuleEngine)
 *
 * 工作流：
 * 1. 文本规整与预处理
 * 2. 加载 Room 数据库中启用的自定义规则（按 priority 降序排列）
 * 3. 动态编译正则并匹配，若捕获组命中则融合 SlotExtractor 提取槽位并渲染模板
 * 4. 内置语义引擎兜底（提醒意图、查询提醒、系统控制、四则运算）
 * 5. 未知意图友好降级引导
 */
class CommandRuleEngine(
    private val loadEnabledRules: suspend () -> List<RuleEntity>,
    private val onRuleTriggered: suspend (Long) -> Unit = {}
) {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    suspend fun parse(rawInput: String): ParseResult {
        val cleanText = rawInput.trim()
        if (cleanText.isEmpty()) {
            return ParseResult(
                matchedRuleId = null,
                matchedRuleTitle = "空输入拦截",
                intent = IntentCategory.UNKNOWN,
                confidence = 0f,
                slots = emptyMap(),
                triggerTimestampMillis = null,
                rawText = rawInput,
                replyMessage = "我没有听清您说的话，请再说一次或直接输入文字指令。"
            )
        }

        val slots = SlotExtractor.extractAllSlots(cleanText)
        val dynamicRules = loadEnabledRules().sortedByDescending { it.priority }

        // 第一阶段：遍历用户自定义规则库（支持动态扩展与正则捕获）
        for (rule in dynamicRules) {
            try {
                val pattern = Pattern.compile(rule.regexPattern, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(cleanText)
                if (matcher.find()) {
                    onRuleTriggered(rule.id)
                    val intent = runCatching { IntentCategory.valueOf(rule.intentCategory) }
                        .getOrDefault(IntentCategory.CUSTOM_REPLY)

                    // 若正则含捕获组且未提取到内容，可用最后一个捕获组补充任务槽位
                    if (!slots.containsKey(SlotKey.TASK_CONTENT) && matcher.groupCount() >= 1) {
                        val lastGroup = matcher.group(matcher.groupCount())?.trim()
                        if (!lastGroup.isNullOrBlank()) {
                            slots[SlotKey.TASK_CONTENT] = lastGroup
                        }
                    }

                    val triggerTime = if (intent == IntentCategory.CREATE_REMINDER) {
                        SlotExtractor.resolveTriggerTimestamp(cleanText, slots)
                    } else null

                    val renderedReply = renderTemplate(rule.responseTemplate, slots, triggerTime)
                    return ParseResult(
                        matchedRuleId = rule.id,
                        matchedRuleTitle = rule.title,
                        intent = intent,
                        confidence = 0.96f,
                        slots = slots,
                        triggerTimestampMillis = triggerTime,
                        rawText = cleanText,
                        replyMessage = renderedReply
                    )
                }
            } catch (_: Exception) {
                // 忽略用户填写的非法正则表达式，保障引擎高可用不崩溃
            }
        }

        // 第二阶段：内置语义规则匹配（算术计算）
        if (slots.containsKey(SlotKey.MATH_RESULT)) {
            val expr = slots[SlotKey.MATH_EXPRESSION] ?: cleanText
            val result = slots[SlotKey.MATH_RESULT] ?: "0"
            return ParseResult(
                matchedRuleId = null,
                matchedRuleTitle = "内置算术求解引擎",
                intent = IntentCategory.QUICK_CALCULATE,
                confidence = 0.94f,
                slots = slots,
                triggerTimestampMillis = null,
                rawText = cleanText,
                replyMessage = "计算完成：$expr = $result"
            )
        }

        // 第三阶段：内置语义规则匹配（定时提醒）
        if (cleanText.contains("提醒") || cleanText.contains("闹钟") || cleanText.contains("别忘了") || cleanText.contains("叫我")) {
            val triggerTime = SlotExtractor.resolveTriggerTimestamp(cleanText, slots)
            val task = slots[SlotKey.TASK_CONTENT] ?: "待办事项"
            val formattedTime = timeFormat.format(Date(triggerTime))
            return ParseResult(
                matchedRuleId = null,
                matchedRuleTitle = "内置提醒语义引擎",
                intent = IntentCategory.CREATE_REMINDER,
                confidence = 0.91f,
                slots = slots,
                triggerTimestampMillis = triggerTime,
                rawText = cleanText,
                replyMessage = "已为您创建定时提醒：【$task】，将于 $formattedTime 准时通过伴生通知中心提醒您。"
            )
        }

        // 第四阶段：内置系统设备控制意图
        if (slots.containsKey(SlotKey.DEVICE_TARGET)) {
            val act = slots[SlotKey.DEVICE_ACTION] ?: "操作"
            val target = slots[SlotKey.DEVICE_TARGET] ?: "设备"
            return ParseResult(
                matchedRuleId = null,
                matchedRuleTitle = "内置系统设备控制引擎",
                intent = IntentCategory.SYSTEM_CONTROL,
                confidence = 0.89f,
                slots = slots,
                triggerTimestampMillis = null,
                rawText = cleanText,
                replyMessage = "已识别系统控制指令：正在为您【$act】【$target】。"
            )
        }

        // 第五阶段：内置查询待办提醒
        if (cleanText.contains("查看提醒") || cleanText.contains("有哪些提醒") || cleanText.contains("我的待办")) {
            return ParseResult(
                matchedRuleId = null,
                matchedRuleTitle = "内置待办查询引擎",
                intent = IntentCategory.QUERY_REMINDER,
                confidence = 0.88f,
                slots = slots,
                triggerTimestampMillis = null,
                rawText = cleanText,
                replyMessage = "正在为您播报并打开伴生模块「通知提醒中心」查看当前待办列表。"
            )
        }

        // 兜底策略
        return ParseResult(
            matchedRuleId = null,
            matchedRuleTitle = "未命中规则 (兜底应答)",
            intent = IntentCategory.UNKNOWN,
            confidence = 0.35f,
            slots = slots,
            triggerTimestampMillis = null,
            rawText = cleanText,
            replyMessage = "暂未匹配到特定指令规则。您可以说“10秒后提醒我交作业”，或点击右上角「规则库」添加自定义正则指令！"
        )
    }

    private fun renderTemplate(template: String, slots: Map<SlotKey, String>, triggerTime: Long?): String {
        var result = template
        result = result.replace("{time}", slots[SlotKey.TARGET_TIME] ?: (triggerTime?.let { timeFormat.format(Date(it)) } ?: "稍后"))
        result = result.replace("{content}", slots[SlotKey.TASK_CONTENT] ?: "待办事项")
        result = result.replace("{target}", slots[SlotKey.DEVICE_TARGET] ?: "系统设置")
        result = result.replace("{action}", slots[SlotKey.DEVICE_ACTION] ?: "执行")
        return result
    }
}

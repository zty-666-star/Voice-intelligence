package com.zty.voiceintelligence.core.engine.model

// 指令意图类别
enum class IntentCategory {
    CREATE_REMINDER,   // 触发伴生模块新建提醒
    CHECK_WEATHER,     // 查询天气
    SYSTEM_CONTROL,    // 系统控制（静音、手电筒、打开App）
    CUSTOM_REPLY,      // 自定义应答/快捷话术
    UNKNOWN            // 未识别意图（兜底降级）
}

// 槽位键（槽位抽取）
enum class SlotKey {
    TARGET_TIME,       // 时间槽位，例如"明天下午3点"、"10分钟后"
    TASK_CONTENT,      // 任务内容，例如"交移动开发作业"
    LOCATION,          // 地点
    KEYWORD            // 自定义关键词
}

// 解析结果
data class ParseResult(
    val matchedRuleId: Long?,
    val intent: IntentCategory,
    val confidence: Float,
    val slots: Map<SlotKey, String>,
    val rawText: String,
    val feedbackMessage: String
)

package com.zty.voiceintelligence.core.engine

import com.zty.voiceintelligence.core.engine.model.SlotKey
import java.util.Calendar
import java.util.regex.Pattern

object SlotExtractor {
    // 相对时间正则匹配，例如：10分钟后、2小时后、半小时后
    private val RELATIVE_TIME_PATTERN = Pattern.compile("(\\d+|半)(个)?(小时|分钟|秒|天)后")
    // 具体时间点正则匹配，例如：明天上午8点、今晚9点半
    private val SPECIFIC_TIME_PATTERN = Pattern.compile("(今天|明天|后天)?(上午|中午|下午|晚上)?(\\d{1,2})点(\\d{1,2}|半)?")

fun extractSlots(rawText: String): MutableMap<SlotKey, String> {
        val slots = mutableMapOf<SlotKey, String>()

        // 1. 抽取时间槽位
  val relMatcher = RELATIVE_TIME_PATTERN.matcher(rawText)
        if (relMatcher.find()) {
            slots[SlotKey.TARGET_TIME] = relMatcher.group()
        } else {
            val specMatcher = SPECIFIC_TIME_PATTERN.matcher(rawText)
            if (specMatcher.find()) {
                slots[SlotKey.TARGET_TIME] = specMatcher.group()
            }
        }

        // 2. 抽取任务内容槽位（去除“提醒我”、“别忘了”等触发动词及时间词）
   var content = rawText
            .replace(Regex("^(帮我|请)?(提醒我|记一下|设置一个闹钟|叫我)"), "")
            .replace(Regex("^(去|要|准备)?"), "")
        slots[SlotKey.TARGET_TIME]?.let { content = content.replace(it, "") }
        content = content.trim('，', '。', ' ', '、')
        if (content.isNotBlank()) {
            slots[SlotKey.TASK_CONTENT] = content
        }

   return slots
    }

    // 将自然语言时间转化为毫秒时间戳（供 WorkManager 调度）
 fun parseNaturalTimeToTimestamp(timeStr: String?): Long {
        if (timeStr == null) return System.currentTimeMillis() + 10 * 60 * 1000L // 默认10分钟后
        val now = Calendar.getInstance()

        // 相对时间推导
  val relMatcher = RELATIVE_TIME_PATTERN.matcher(timeStr)
        if (relMatcher.find()) {
            val numStr = relMatcher.group(1)
            val unit = relMatcher.group(3)
            val amount = if (numStr == "半") 0.5 else (numStr?.toDoubleOrNull() ?: 1.0)
            val millis = when (unit) {
                "秒" -> (amount * 1000).toLong()
                "分钟" -> (amount * 60 * 1000).toLong()
                "小时" -> (amount * 3600 * 1000).toLong()
                "天" -> (amount * 86400 * 1000).toLong()
                else -> 600000L
            }
            return System.currentTimeMillis() + millis
        }
        return System.currentTimeMillis() + 15 * 60 * 1000L
    }
}

package com.zty.voiceintelligence.core.engine

import com.zty.voiceintelligence.core.engine.model.*
import com.zty.voiceintelligence.data.entity.RuleEntity
import java.util.regex.Pattern

class CommandRuleEngine(private val dynamicRulesProvider: suspend () -> List<RuleEntity>) {

  suspend fun parse(input: String): ParseResult {
        val cleanInput = input.trim()
        val rules = dynamicRulesProvider() // 从 Room 数据库加载用户自定义规则

        // 1. 优先遍历用户自定义高优先级规则（数据库自定义规则）
  for (rule in rules.filter { it.isEnabled }.sortedByDescending { it.priority }) {
            val pattern = Pattern.compile(rule.regexPattern, Pattern.CASE_INSENSITIVE)
            if (pattern.matcher(cleanInput).find()) {
                val slots = SlotExtractor.extractSlots(cleanInput)
                return ParseResult(
                    matchedRuleId = rule.id,
                    intent = IntentCategory.valueOf(rule.intentCategory),
                    confidence = 0.95f,
                    slots = slots,
                    rawText = cleanInput,
                    feedbackMessage = rule.responseTemplate.replace("{content}", slots[SlotKey.TASK_CONTENT] ?: "待办事项")
                )
            }
        }

        // 2. 内置语义模板规则匹配（提醒意图内置引擎）
  if (cleanInput.contains("提醒") || cleanInput.contains("叫我") || cleanInput.contains("闹钟")) {
            val slots = SlotExtractor.extractSlots(cleanInput)
            val task = slots[SlotKey.TASK_CONTENT] ?: "待办事项"
            val time = slots[SlotKey.TARGET_TIME] ?: "稍后"
            return ParseResult(
                matchedRuleId = null,
                intent = IntentCategory.CREATE_REMINDER,
                confidence = 0.90f,
                slots = slots,
                rawText = cleanInput,
                feedbackMessage = "好的，已为你设置提醒：将在【$time】提醒你【$task】"
            )
        }

        // 3. 内置天气意图
  if (cleanInput.contains("天气")) {
            return ParseResult(
                matchedRuleId = null,
                intent = IntentCategory.CHECK_WEATHER,
                confidence = 0.85f,
                slots = emptyMap(),
                rawText = cleanInput,
                feedbackMessage = "正在为你查询当前城市最新天气..."
            )
        }

        // 4. 兜底策略（未知意图，转大模型/系统应答）
  return ParseResult(
            matchedRuleId = null,
            intent = IntentCategory.UNKNOWN,
            confidence = 0.30f,
            slots = emptyMap(),
            rawText = cleanInput,
            feedbackMessage = "抱歉，我还不能完全理解该指令。你可以在「指令规则库」中为该句式添加自定义匹配规则。"
        )
    }
}

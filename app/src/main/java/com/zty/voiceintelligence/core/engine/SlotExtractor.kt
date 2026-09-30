package com.zty.voiceintelligence.core.engine

import com.zty.voiceintelligence.core.engine.model.SlotKey
import java.util.Calendar
import java.util.regex.Pattern

/**
 * 核心算法模块：自然语言槽位抽取器 (Slot Extractor)
 * 支持中文数字转换、相对时间推导（X分钟/小时/秒后）、绝对时间推导（今天/明天上午X点X分）
 * 以及任务主体剥离与算术表达式求解。
 */
object SlotExtractor {

    private val RELATIVE_TIME_REGEX = Pattern.compile(
        "([零一二两三四五六七八九十百\d]+|半)(个)?(小时|分钟|分|秒|天)后?"
    )

    private val ABSOLUTE_TIME_REGEX = Pattern.compile(
        "(今天|明天|后天)?(早上|上午|中午|下午|晚上|今晚|明早)?([零一二两三四五六七八九十\d]{1,3})[点时](半|([零一二三四五六七八九十\d]{1,3})分?)?"
    )

    private val DEVICE_CONTROL_REGEX = Pattern.compile(
        "(打开|开启|关闭|关掉|调高|调低)(蓝牙|WiFi|无线网|手电筒|静音模式|音量|设置)"
    )

    private val MATH_REGEX = Pattern.compile(
        "(\d+(?:\.\d+)?)\s*([+\-*/×÷加减乘除])\s*(\d+(?:\.\d+)?)"
    )

    /**
     * 从原始自然语言中抽取所有可用槽位
     */
    fun extractAllSlots(rawInput: String): MutableMap<SlotKey, String> {
        val text = rawInput.trim()
        val slots = mutableMapOf<SlotKey, String>()

        // 1. 检测相对时间槽位
        val relMatcher = RELATIVE_TIME_REGEX.matcher(text)
        if (relMatcher.find()) {
            val matchedTime = relMatcher.group()
            slots[SlotKey.TARGET_TIME] = matchedTime
            val delayMin = parseRelativeMinutes(relMatcher.group(1) ?: "1", relMatcher.group(3) ?: "分钟")
            slots[SlotKey.DELAY_MINUTES] = delayMin.toString()
        } else {
            // 2. 检测绝对时间槽位
            val absMatcher = ABSOLUTE_TIME_REGEX.matcher(text)
            if (absMatcher.find()) {
                slots[SlotKey.TARGET_TIME] = absMatcher.group()
            }
        }

        // 3. 检测设备控制槽位
        val devMatcher = DEVICE_CONTROL_REGEX.matcher(text)
        if (devMatcher.find()) {
            slots[SlotKey.DEVICE_ACTION] = devMatcher.group(1) ?: "打开"
            slots[SlotKey.DEVICE_TARGET] = devMatcher.group(2) ?: "设置"
        }

        // 4. 检测算术运算槽位
        val mathMatcher = MATH_REGEX.matcher(text)
        if (mathMatcher.find()) {
            val a = mathMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val op = mathMatcher.group(2) ?: "+"
            val b = mathMatcher.group(3)?.toDoubleOrNull() ?: 0.0
            val res = when (op) {
                "+", "加" -> a + b
                "-", "减" -> a - b
                "*", "×", "乘" -> a * b
                "/", "÷", "除" -> if (b != 0.0) a / b else Double.NaN
                else -> 0.0
            }
            slots[SlotKey.MATH_EXPRESSION] = "${mathMatcher.group(1)} $op ${mathMatcher.group(3)}"
            slots[SlotKey.MATH_RESULT] = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.2f", res)
        }

        // 5. 抽取提醒任务主体内容（剔除前缀触发词与时间状语）
        var content = text
        slots[SlotKey.TARGET_TIME]?.let { timeToken ->
            content = content.replace(timeToken, "")
        }
        content = content
            .replace(Regex("^(请|帮我|麻烦|劳驾|助手)?(提醒我|记一下|设置提醒|定个闹钟|叫我|别忘了|通知我)"), "")
            .replace(Regex("(提醒我|记一下|定个闹钟|叫我|别忘了)"), "")
            .replace(Regex("^(去|要|准备|记得)"), "")
            .trim('，', '。', '！', '？', ' ', '、', ',')

        if (content.isNotBlank()) {
            slots[SlotKey.TASK_CONTENT] = content
        }

        return slots
    }

    /**
     * 将抽取出的自然语言时间槽位转换为精确的 Unix 毫秒时间戳
     */
    fun resolveTriggerTimestamp(rawInput: String, slots: Map<SlotKey, String>): Long {
        val now = System.currentTimeMillis()
        val relMatcher = RELATIVE_TIME_REGEX.matcher(rawInput)
        if (relMatcher.find()) {
            val numStr = relMatcher.group(1) ?: "1"
            val unit = relMatcher.group(3) ?: "分钟"
            if (numStr == "半" && unit == "小时") {
                return now + 30 * 60 * 1000L
            }
            val amount = chineseOrArabicToInt(numStr).coerceAtLeast(1)
            val deltaMillis = when (unit) {
                "秒" -> amount * 1000L
                "分钟", "分" -> amount * 60 * 1000L
                "小时" -> amount * 3600 * 1000L
                "天" -> amount * 86400 * 1000L
                else -> amount * 60 * 1000L
            }
            return now + deltaMillis
        }

        val absMatcher = ABSOLUTE_TIME_REGEX.matcher(rawInput)
        if (absMatcher.find()) {
            val dayToken = absMatcher.group(1) ?: "今天"
            val periodToken = absMatcher.group(2) ?: ""
            val hourToken = absMatcher.group(3) ?: "9"
            val minFullToken = absMatcher.group(4) ?: ""
            val minNumToken = absMatcher.group(5)

            val cal = Calendar.getInstance()
            when {
                dayToken == "明天" || periodToken == "明早" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                dayToken == "后天" -> cal.add(Calendar.DAY_OF_YEAR, 2)
            }

            var hour = chineseOrArabicToInt(hourToken).coerceIn(0, 23)
            if ((periodToken in listOf("下午", "晚上", "今晚")) && hour < 12) {
                hour += 12
            }
            val minute = when {
                minFullToken == "半" -> 30
                !minNumToken.isNullOrBlank() -> chineseOrArabicToInt(minNumToken).coerceIn(0, 59)
                else -> 0
            }
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)

            // 如果设定时间已过，自动顺延至明天
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // 默认兜底：1分钟后触发（方便课堂老师现场演示立即看到通知效果）
        return now + 60 * 1000L
    }

    private fun parseRelativeMinutes(numStr: String, unit: String): Int {
        if (numStr == "半" && unit == "小时") return 30
        val n = chineseOrArabicToInt(numStr)
        return when (unit) {
            "小时" -> n * 60
            "天" -> n * 1440
            "秒" -> 1
            else -> n
        }
    }

    /**
     * 中文数字与阿拉伯数字混合转整型（如 "十五" -> 15, "二十五" -> 25, "两" -> 2）
     */
    fun chineseOrArabicToInt(input: String): Int {
        input.toIntOrNull()?.let { return it }
        val map = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        var result = 0
        var temp = 0
        for (ch in input) {
            when {
                ch == '十' -> {
                    result += (if (temp == 0) 1 else temp) * 10
                    temp = 0
                }
                ch == '百' -> {
                    result += (if (temp == 0) 1 else temp) * 100
                    temp = 0
                }
                map.containsKey(ch) -> {
                    temp = map[ch] ?: 0
                }
            }
        }
        result += temp
        return if (result > 0) result else 1
    }
}

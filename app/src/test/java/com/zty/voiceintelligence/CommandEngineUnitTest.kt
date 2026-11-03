package com.zty.voiceintelligence

import com.zty.voiceintelligence.core.engine.CommandRuleEngine
import com.zty.voiceintelligence.core.engine.SlotExtractor
import com.zty.voiceintelligence.core.engine.model.IntentCategory
import com.zty.voiceintelligence.core.engine.model.SlotKey
import com.zty.voiceintelligence.data.entity.RuleEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * 针对必做核心模块「自定义指令解析引擎」的单元测试
 */
class CommandEngineUnitTest {

    @Test
    fun testChineseNumberAndRelativeTimeSlotExtraction() {
        val slots = SlotExtractor.extractAllSlots("十五分钟后提醒我交移动开发作业")
        assertEquals("十五分钟后", slots[SlotKey.TARGET_TIME])
        assertEquals("15", slots[SlotKey.DELAY_MINUTES])
        assertEquals("交移动开发作业", slots[SlotKey.TASK_CONTENT])
    }

    @Test
    fun testMathSlotExtraction() {
        val slots = SlotExtractor.extractAllSlots("帮我算一下 25 乘 4")
        assertEquals("100", slots[SlotKey.MATH_RESULT])
    }

    @Test
    fun testCustomRulePriorityMatching() = runBlocking {
        val mockRules = listOf(
            RuleEntity(
                id = 10L,
                title = "高优先级自定义提醒规则",
                regexPattern = ".*(提醒我|叫我)(.*)",
                intentCategory = "CREATE_REMINDER",
                responseTemplate = "自定义引擎已捕获：{content}",
                priority = 99,
                isEnabled = true
            )
        )
        val engine = CommandRuleEngine(loadEnabledRules = { mockRules })
        val result = engine.parse("10秒后提醒我背单词")
        assertEquals(IntentCategory.CREATE_REMINDER, result.intent)
        assertEquals(10L, result.matchedRuleId)
        assertTrue(result.replyMessage.contains("背单词"))
    }
}

package com.zty.voiceintelligence.ui.stats

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zty.voiceintelligence.data.repository.VoiceRepository
import com.zty.voiceintelligence.databinding.ActivityEngineStatsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 界面 5：指令引擎统计面板与历史记录管理页
 */
class EngineStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEngineStatsBinding
    private lateinit var repository: VoiceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEngineStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = VoiceRepository(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.allRules,
                    repository.allReminders,
                    repository.allMessages
                ) { rules, reminders, messages ->
                    val enabledCount = rules.count { it.isEnabled }
                    val totalRuleHits = rules.sumOf { it.triggerCount }
                    val completedReminders = reminders.count { it.isCompleted }
                    val pendingReminders = reminders.count { !it.isCompleted }

                    buildString {
                        appendLine("【自定义指令解析引擎运行看板】")
                        appendLine("• 规则库总数量：${rules.size} 条（已启用 $enabledCount 条）")
                        appendLine("• 自定义规则累计命中：$totalRuleHits 次")
                        appendLine("• 历史语音/文本交互消息：${messages.size} 条")
                        appendLine()
                        appendLine("【P3 伴生模块（通知提醒中心）数据同步】")
                        appendLine("• 共享提醒事项总数：${reminders.size} 项")
                        appendLine("• 待触发提醒：$pendingReminders 项 | 已完成：$completedReminders 项")
                    }
                }.collect { summaryText ->
                    binding.tvStatsSummary.text = summaryText
                }
            }
        }

        binding.btnClearChatHistory.setOnClickListener {
            lifecycleScope.launch {
                repository.clearChatHistory()
                Toast.makeText(this@EngineStatsActivity, "对话记录已清空", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnCloseStats.setOnClickListener { finish() }
    }
}

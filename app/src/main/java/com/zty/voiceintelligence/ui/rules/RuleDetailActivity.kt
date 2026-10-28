package com.zty.voiceintelligence.ui.rules

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zty.voiceintelligence.data.entity.RuleEntity
import com.zty.voiceintelligence.data.repository.VoiceRepository
import com.zty.voiceintelligence.databinding.ActivityRuleDetailBinding
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * 界面 3：自定义指令规则详情与新增/编辑页
 * 包含正则表达式合法性校验，防止错误正则入库
 */
class RuleDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
    }

    private lateinit var binding: ActivityRuleDetailBinding
    private lateinit var repository: VoiceRepository
    private var currentRule: RuleEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = VoiceRepository(this)

        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId != -1L) {
            lifecycleScope.launch {
                currentRule = repository.getRuleById(ruleId)
                currentRule?.let { rule ->
                    binding.tvDetailHeader.text = "编辑自定义规则 #${rule.id}"
                    binding.etRuleTitle.setText(rule.title)
                    binding.etRuleRegex.setText(rule.regexPattern)
                    binding.etRuleIntent.setText(rule.intentCategory)
                    binding.etRuleResponse.setText(rule.responseTemplate)
                    binding.etRulePriority.setText(rule.priority.toString())
                }
            }
        } else {
            binding.tvDetailHeader.text = "新建自定义指令规则"
            binding.btnDeleteRule.visibility = View.GONE
        }

        binding.btnSaveRule.setOnClickListener { saveRule() }
        binding.btnDeleteRule.setOnClickListener {
            currentRule?.let { rule ->
                lifecycleScope.launch {
                    repository.deleteRule(rule)
                    Toast.makeText(this@RuleDetailActivity, "规则已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun saveRule() {
        val title = binding.etRuleTitle.text.toString().trim()
        val regex = binding.etRuleRegex.text.toString().trim()
        val intentCat = binding.etRuleIntent.text.toString().trim().ifEmpty { "CUSTOM_REPLY" }
        val response = binding.etRuleResponse.text.toString().trim()
        val priority = binding.etRulePriority.text.toString().toIntOrNull() ?: 50

        if (title.isEmpty() || regex.isEmpty() || response.isEmpty()) {
            Toast.makeText(this, "请完整填写规则名称、正则表达式和响应模板", Toast.LENGTH_SHORT).show()
            return
        }

        // 校验正则表达式语法合法性
        try {
            Pattern.compile(regex)
        } catch (e: Exception) {
            Toast.makeText(this, "正则表达式语法有误: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val entity = RuleEntity(
                id = currentRule?.id ?: 0L,
                title = title,
                regexPattern = regex,
                intentCategory = intentCat,
                responseTemplate = response,
                priority = priority,
                isEnabled = currentRule?.isEnabled ?: true,
                triggerCount = currentRule?.triggerCount ?: 0
            )
            repository.saveRule(entity)
            Toast.makeText(this@RuleDetailActivity, "自定义规则保存成功！", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

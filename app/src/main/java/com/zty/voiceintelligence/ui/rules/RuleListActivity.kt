package com.zty.voiceintelligence.ui.rules

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zty.voiceintelligence.data.entity.RuleEntity
import com.zty.voiceintelligence.data.repository.VoiceRepository
import com.zty.voiceintelligence.databinding.ActivityRuleListBinding
import com.zty.voiceintelligence.databinding.ItemRuleBinding
import kotlinx.coroutines.launch

/**
 * 界面 2：自定义指令规则列表页（点击任意项进入界面 3 规则详情编辑页，构成标准“列表→详情”导航）
 */
class RuleListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuleListBinding
    private lateinit var repository: VoiceRepository
    private val rules = mutableListOf<RuleEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = VoiceRepository(this)

        val adapter = object : RecyclerView.Adapter<RuleViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
                val itemBinding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return RuleViewHolder(itemBinding)
            }

            override fun getItemCount(): Int = rules.size

            override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
                val rule = rules[position]
                holder.binding.tvRuleTitle.text = rule.title
                holder.binding.tvRuleRegex.text = "匹配正则: ${rule.regexPattern}"
                holder.binding.tvRuleTemplate.text = "回复模板: ${rule.responseTemplate}"
                holder.binding.tvRuleMeta.text = "意图: ${rule.intentCategory} | 优先级: ${rule.priority} | 累计命中: ${rule.triggerCount} 次"

                holder.binding.cbRuleEnabled.setOnCheckedChangeListener(null)
                holder.binding.cbRuleEnabled.isChecked = rule.isEnabled
                holder.binding.cbRuleEnabled.setOnCheckedChangeListener { _, checked ->
                    lifecycleScope.launch {
                        repository.saveRule(rule.copy(isEnabled = checked))
                    }
                }

                // 列表 -> 详情 跳转
                holder.itemView.setOnClickListener {
                    val intent = Intent(this@RuleListActivity, RuleDetailActivity::class.java).apply {
                        putExtra(RuleDetailActivity.EXTRA_RULE_ID, rule.id)
                    }
                    startActivity(intent)
                }
            }
        }

        binding.rvRules.layoutManager = LinearLayoutManager(this)
        binding.rvRules.adapter = adapter

        binding.btnBackFromRules.setOnClickListener { finish() }
        binding.btnAddRule.setOnClickListener {
            startActivity(Intent(this, RuleDetailActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.allRules.collect { list ->
                    rules.clear()
                    rules.addAll(list)
                    adapter.notifyDataSetChanged()
                    binding.tvEmptyRules.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    class RuleViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)
}

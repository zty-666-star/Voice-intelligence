package com.zty.voiceintelligence.companion.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zty.voiceintelligence.data.entity.ReminderEntity
import com.zty.voiceintelligence.data.repository.VoiceRepository
import com.zty.voiceintelligence.databinding.ActivityReminderCenterBinding
import com.zty.voiceintelligence.databinding.ItemReminderBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 界面 4 (兼 P3 伴生模块独立启动入口)：通知提醒中心
 * 可从手机桌面独立图标直接启动，读取并修改主 App 创建的提醒数据，支持一键 5 秒通知测试
 */
class ReminderCenterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderCenterBinding
    private lateinit var repository: VoiceRepository
    private val reminders = mutableListOf<ReminderEntity>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = VoiceRepository(this)

        val adapter = object : RecyclerView.Adapter<ReminderHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderHolder {
                val b = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ReminderHolder(b)
            }

            override fun getItemCount(): Int = reminders.size

            override fun onBindViewHolder(holder: ReminderHolder, position: Int) {
                val item = reminders[position]
                val statusTag = when {
                    item.isCompleted -> "[已完成] "
                    item.triggerTimeMillis <= System.currentTimeMillis() -> "[已提醒] "
                    else -> "[待触发] "
                }
                holder.binding.tvReminderTitle.text = "$statusTag${item.title}"
                holder.binding.tvReminderMeta.text =
                    "计划时间: ${dateFormat.format(Date(item.triggerTimeMillis))} | 来源: ${item.source} | 延后: ${item.snoozeCount}次"

                holder.binding.cbReminderDone.setOnCheckedChangeListener(null)
                holder.binding.cbReminderDone.isChecked = item.isCompleted
                holder.binding.cbReminderDone.setOnCheckedChangeListener { _, checked ->
                    lifecycleScope.launch {
                        repository.markReminderCompleted(item.id, checked)
                    }
                }

                holder.binding.btnDeleteReminder.setOnClickListener {
                    lifecycleScope.launch {
                        repository.deleteReminder(item)
                    }
                }
            }
        }

        binding.rvReminders.layoutManager = LinearLayoutManager(this)
        binding.rvReminders.adapter = adapter

        // 5秒快速触发通知栏交互测试（方便录屏演示通知栏直接点击“一键完成”或“延后5分钟”）
        binding.btnTestInstantNotify.setOnClickListener {
            lifecycleScope.launch {
                val triggerAt = System.currentTimeMillis() + 5000L
                repository.createAndScheduleReminder(
                    title = "演示测试：提交Android课程设计异步验证包",
                    rawCommand = "伴生模块一键5秒测试通知",
                    triggerTimeMillis = triggerAt,
                    source = "COMPANION_P3"
                )
                Toast.makeText(
                    this@ReminderCenterActivity,
                    "已创建！5秒后将弹出系统通知，请下拉通知栏体验直接点击「一键完成」或「延后5分钟」",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnCompanionAdd.setOnClickListener {
            val text = binding.etCompanionQuickTitle.text.toString().trim()
            if (text.isNotEmpty()) {
                lifecycleScope.launch {
                    repository.createAndScheduleReminder(
                        title = text,
                        rawCommand = "伴生模块手动新建：$text",
                        triggerTimeMillis = System.currentTimeMillis() + 10_000L,
                        source = "COMPANION_P3"
                    )
                    binding.etCompanionQuickTitle.setText("")
                    Toast.makeText(this@ReminderCenterActivity, "已添加定时提醒（10秒后触发通知）", Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.allReminders.collect { list ->
                    reminders.clear()
                    reminders.addAll(list)
                    adapter.notifyDataSetChanged()
                    binding.tvEmptyReminders.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    class ReminderHolder(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root)
}

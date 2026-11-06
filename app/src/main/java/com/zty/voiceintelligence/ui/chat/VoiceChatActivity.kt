package com.zty.voiceintelligence.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.zty.voiceintelligence.companion.ui.ReminderCenterActivity
import com.zty.voiceintelligence.core.voice.TtsHelper
import com.zty.voiceintelligence.core.voice.VoiceRecognizerHelper
import com.zty.voiceintelligence.databinding.ActivityVoiceChatBinding
import com.zty.voiceintelligence.ui.rules.RuleListActivity
import com.zty.voiceintelligence.ui.stats.EngineStatsActivity
import kotlinx.coroutines.launch

/**
 * 界面 1：C3 主 App 语音交互与指令解析主界面
 * 涵盖动态录音权限申请、权限拒绝优雅降级方案、原生语音识别与 TTS 播报
 */
class VoiceChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceChatBinding
    private val viewModel: VoiceChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()
    private var ttsHelper: TtsHelper? = null
    private var voiceHelper: VoiceRecognizerHelper? = null

    // 录音权限申请与降级处理（满足最低复杂度标准第5条）
    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.tvPermissionBanner.text = "录音权限已开启：点击「语音输入」即可开始说话"
            voiceHelper?.startListening()
        } else {
            // 用户拒绝权限后，App 仍能正常运行（清晰提示 + 降级为文本与快捷芯片输入）
            binding.tvPermissionBanner.text = "【权限降级模式】您拒绝了麦克风权限，已平滑切换为文本输入与快捷指令模式，所有解析功能正常可用！"
            binding.etCommandInput.requestFocus()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsHelper = TtsHelper(this)
        voiceHelper = VoiceRecognizerHelper(
            context = this,
            onResultText = { spokenText ->
                binding.etCommandInput.setText(spokenText)
                sendCommand(spokenText)
            },
            onStateChanged = { _, statusMsg ->
                binding.tvPermissionBanner.text = statusMsg
            }
        )

        requestNotificationPermissionIfNeeded()
        setupRecyclerView()
        setupClickListeners()
        observeData()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvChatList.layoutManager = LinearLayoutManager(this)
        binding.rvChatList.adapter = chatAdapter
    }

    private fun setupClickListeners() {
        binding.btnVoiceMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                voiceHelper?.startListening()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.btnSendCommand.setOnClickListener {
            val input = binding.etCommandInput.text.toString()
            if (input.isNotBlank()) {
                sendCommand(input)
                binding.etCommandInput.setText("")
            }
        }

        // 快捷演示指令芯片
        binding.chipQuickReminder.setOnClickListener {
            sendCommand("10秒后提醒我交安卓作业")
        }
        binding.chipQuickDevice.setOnClickListener {
            sendCommand("打开蓝牙")
        }
        binding.chipQuickMath.setOnClickListener {
            sendCommand("计算 128 乘 16")
        }
        binding.chipQuickDefense.setOnClickListener {
            sendCommand("演示模式系统状态自检")
        }

        // 页面导航跳转
        binding.btnOpenRules.setOnClickListener {
            startActivity(Intent(this, RuleListActivity::class.java))
        }
        binding.btnOpenCompanion.setOnClickListener {
            startActivity(Intent(this, ReminderCenterActivity::class.java))
        }
        binding.btnOpenStats.setOnClickListener {
            startActivity(Intent(this, EngineStatsActivity::class.java))
        }
    }

    private fun sendCommand(text: String) {
        viewModel.processUserCommand(
            rawText = text,
            onSpeakReply = { reply -> ttsHelper?.speak(reply) },
            onOpenReminderCenter = {
                startActivity(Intent(this, ReminderCenterActivity::class.java))
            }
        )
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { list ->
                    chatAdapter.submitList(list)
                    binding.tvEmptyChat.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    if (list.isNotEmpty()) {
                        binding.rvChatList.scrollToPosition(list.size - 1)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceHelper?.destroy()
        ttsHelper?.shutdown()
    }
}

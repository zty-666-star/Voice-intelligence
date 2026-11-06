Voice-intelligence/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml          # 双入口配置（主App + 伴生模块）
│       ├── java/com/zty/voiceintelligence/
│       │   ├── core/engine/             # 【核心亮点】自定义指令解析引擎
│       │   │   ├── CommandRuleEngine.kt # 规则匹配与调度核心
│       │   │   ├── SlotExtractor.kt     # 槽位提取器（时间/动作/内容/目标）
│       │   │   └── model/               # 意图、槽位、执行结果数据模型
│       │   ├── core/voice/              # 语音能力（麦克风识别+TTS+权限降级）
│       │   │   ├── VoiceManager.kt
│       │   │   └── TtsHelper.kt
│       │   ├── data/                    # Room 数据库与持久化
│       │   │   ├── AppDatabase.kt
│       │   │   ├── entity/ (RuleEntity, ReminderEntity, ChatHistoryEntity)
│       │   │   └── dao/
│       │   ├── companion/               # 【P3 伴生模块】通知提醒中心
│       │   │   ├── ReminderCenterActivity.kt # 独立桌面入口
│       │   │   ├── ReminderWorker.kt         # WorkManager 定时调度
│       │   │   └── NotificationActionReceiver.kt # 通知栏直接操作（完成/延后/播报）
│       │   └── ui/                      # 主 App 四大核心界面
│       │       ├── chat/                # 1. 语音/文本交互主界面（带波形与降级）
│       │       ├── rules/               # 2. 指令规则库列表
│       │       ├── rule_detail/         # 3. 规则详情与自定义编辑（列表->详情）
│       │       └── history/             # 4. 对话历史与指令统计中心

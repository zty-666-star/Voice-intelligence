package com.zty.voiceintelligence.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 原生语音识别封装类 (SpeechRecognizer)
 * 包含完整的识别状态回调与异常错误码转译，若模拟器无语音服务则平滑提示使用文字或快捷模拟语音芯片
 */
class VoiceRecognizerHelper(
    private val context: Context,
    private val onResultText: (String) -> Unit,
    private val onStateChanged: (Boolean, String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStateChanged(false, "当前设备不支持原生语音服务，已自动切换为文本与模拟语音输入模式")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onStateChanged(true, "正在聆听中，请说话...")
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        onStateChanged(false, "正在解析语音指令...")
                    }
                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "未识别到清晰语音，您可以重试或使用下方快捷指令"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "录音权限被拒绝，已降级为文字输入模式"
                            else -> "语音引擎未就绪(错误码:$error)，可使用下方文字或模拟语音输入"
                        }
                        onStateChanged(false, msg)
                    }
                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = list?.firstOrNull().orEmpty()
                        onStateChanged(false, "语音识别完成")
                        if (spoken.isNotBlank()) {
                            onResultText(spoken)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    fun destroy() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}

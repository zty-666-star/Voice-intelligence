package com.zty.voiceintelligence.core.device

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings

/**
 * 设备能力执行器：把解析引擎识别出的「系统控制」意图真正落到系统 API 上
 *
 * 支持：媒体音量增减、静音模式切换、手电筒开关、蓝牙开关（受系统授权限制）、WiFi 设置页跳转
 * 每一项都返回真实执行结果，无法执行时如实说明原因，不做假执行。
 */
class DeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun execute(action: String, target: String): String {
        return try {
            when {
                target.contains("音量") -> adjustVolume(action)
                target.contains("静音") -> setSilentMode(action.contains("打开") || action.contains("开启"))
                target.contains("手电筒") -> setTorch(action.contains("打开") || action.contains("开启"))
                target.contains("蓝牙") -> setBluetooth(action.contains("打开") || action.contains("开启"))
                target.contains("WiFi") || target.contains("无线网") -> openWifiSettings()
                else -> "暂不支持对「$target」的自动控制，请在系统设置中手动操作。"
            }
        } catch (e: Exception) {
            "执行失败（${e.javaClass.simpleName}），请在系统设置中手动操作。"
        }
    }

    private fun adjustVolume(action: String): String {
        val turnUp = action.contains("调高") || action.contains("打开") || action.contains("开启")
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (turnUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return "已${if (turnUp) "调高" else "调低"}媒体音量，当前音量 $current / $max。"
    }

    private fun setSilentMode(on: Boolean): String {
        audioManager.ringerMode =
            if (on) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        return if (on) "已切换到静音模式。" else "已退出静音模式。"
    }

    private fun setTorch(on: Boolean): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "当前设备没有可用闪光灯，手电筒操作未执行。"
        cameraManager.setTorchMode(cameraId, on)
        return if (on) "手电筒已开启。" else "手电筒已关闭。"
    }

    private fun setBluetooth(on: Boolean): String {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return "当前设备不支持蓝牙。"
        val ok = if (on) adapter.enable() else adapter.disable()
        return if (ok) {
            "蓝牙已${if (on) "开启" else "关闭"}。"
        } else {
            "系统未允许应用直接切换蓝牙，请在下拉控制中心手动${if (on) "开启" else "关闭"}。"
        }
    }

    private fun openWifiSettings(): String {
        context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Android 不允许应用直接开关 WiFi，已为您打开无线网络设置页。"
    }
}

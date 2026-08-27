package com.zwen.medreminder

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.zwen.medreminder.data.Prefs
import kotlin.concurrent.timer

/**
 * 自动锁屏无障碍服务：
 * - 任意触摸 / 滑动 / 切窗口 → 重置计时
 * - 连续 N 分钟无操作 → 执行系统锁屏（视频播放中不豁免）
 * - 屏幕熄灭 → 取消计时；解锁（亮屏）后重新计时
 */
class AutoLockService : AccessibilityService() {

    companion object {
        private const val CHECK_MS = 30_000L
    }

    private var lastTouch = 0L
    private var screenOn = true
    private var lockTriggered = false
    private var checker: java.util.Timer? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    lockTriggered = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    lastTouch = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastTouch = System.currentTimeMillis()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, f)
        checker = timer(name = "auto-lock", period = CHECK_MS) { checkLock() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!screenOn) return
        lastTouch = System.currentTimeMillis()
        lockTriggered = false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        checker?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    private fun checkLock() {
        if (!Prefs.autoLock || !screenOn || lockTriggered) return
        if (System.currentTimeMillis() - lastTouch >= Prefs.lockMinutes * 60_000L) {
            lockTriggered = true
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}

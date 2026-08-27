package com.zwen.medreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.zwen.medreminder.data.Prefs
import com.zwen.medreminder.util.TimeUtil

/**
 * 前台服务（静默通知常驻）：
 * 唯一职责 = 监听「解锁」（ACTION_USER_PRESENT），
 * 若当前落在提醒时段内且该时段未吃，则弹出提醒窗口。
 *
 * 弹窗通道按优先级：
 * 1. 已授权悬浮窗 → 直接以 TYPE_APPLICATION_OVERLAY 悬浮窗弹出（后台/桌面可用，
 *    不受 Android 10+ 后台启动 Activity 限制）
 * 2. 未授权悬浮窗 → 兜底 startActivity（App 处于前台时有效）
 *
 * 无定时唤醒、无网络，仅在解锁边界做轻量判定，符合省电要求。
 */
class ReminderService : Service() {

    companion object {
        private const val CHANNEL_ID = "reminder"
        private const val NOTIFY_ID = 1

        fun start(context: Context) {
            val i = Intent(context, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }

    private var overlayView: View? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) checkAndRemind()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFY_ID, buildNotification())
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        dismissOverlay()
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_MIN)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(getString(R.string.service_notify_title))
            .setContentText(getString(R.string.service_notify_text))
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    /** 解锁时：时段内且未吃 → 弹提醒窗口 */
    private fun checkAndRemind() {
        val slotId = TimeUtil.currentSlot(Prefs.getSlots())
        if (slotId < 0) return
        if (Prefs.getDay(TimeUtil.today()).dose(slotId).eaten) return

        if (Settings.canDrawOverlays(this)) {
            showOverlayAlert(slotId)
        } else {
            // 未授权悬浮窗：兜底走 Activity（App 处于前台时有效）
            startActivity(
                Intent(this, ReminderActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ---------- 悬浮窗弹窗（后台/桌面可用） ----------

    private fun showOverlayAlert(slotId: Int) {
        dismissOverlay()

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reminder, null)
        view.findViewById<TextView>(R.id.alert_title).text =
            resources.getStringArray(R.array.reminder_texts)[slotId]
        view.findViewById<TextView>(R.id.alert_time).text = getString(
            R.string.now_time, TimeUtil.formatHm(TimeUtil.minuteOfDay())
        )
        // 遮罩点击 = 还没吃；卡片本身在布局中已 clickable，不会冒泡到遮罩
        view.findViewById<View>(R.id.alert_mask).setOnClickListener { skip(slotId) }
        view.findViewById<View>(R.id.btn_not_yet).setOnClickListener { skip(slotId) }
        view.findViewById<View>(R.id.btn_eaten).setOnClickListener { eaten(slotId) }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        getSystemService(WindowManager::class.java).addView(view, params)
        overlayView = view
    }

    private fun eaten(slotId: Int) {
        Prefs.updateDose(TimeUtil.today(), slotId) {
            it.copy(eaten = true, time = TimeUtil.formatHm(TimeUtil.minuteOfDay()))
        }
        dismissOverlay()
    }

    private fun skip(slotId: Int) {
        Prefs.updateDose(TimeUtil.today(), slotId) { it.copy(skips = it.skips + 1) }
        dismissOverlay()
    }

    private fun dismissOverlay() {
        overlayView?.let { v ->
            runCatching { getSystemService(WindowManager::class.java).removeView(v) }
        }
        overlayView = null
    }
}

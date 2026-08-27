package com.zwen.medreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后恢复提醒服务（无障碍服务由系统在用户开启后自动恢复，无需处理） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderService.start(context)
        }
    }
}

package com.zwen.medreminder

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.zwen.medreminder.data.Prefs
import com.zwen.medreminder.util.TimeUtil

/**
 * 解锁触发的提醒页：透明 Activity，全屏 40% 遮罩 + 居中 iOS Alert 卡片。
 * 操作（已吃药 / 还没吃 / 点遮罩）后立即结束，不留任务栈。
 */
class ReminderActivity : Activity() {

    private var slotId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 仅在「当前落在时段内且该时段未吃」时展示，其余情况直接结束
        slotId = TimeUtil.currentSlot(Prefs.getSlots())
        if (slotId < 0) {
            finish()
            return
        }
        if (Prefs.getDay(TimeUtil.today()).dose(slotId).eaten) {
            finish()
            return
        }

        setContentView(R.layout.dialog_reminder)

        findViewById<TextView>(R.id.alert_title).text =
            resources.getStringArray(R.array.reminder_texts)[slotId]
        findViewById<TextView>(R.id.alert_time).text = getString(
            R.string.now_time, TimeUtil.formatHm(TimeUtil.minuteOfDay())
        )

        // 点遮罩 = 还没吃
        findViewById<View>(R.id.alert_mask).setOnClickListener { skip() }
        findViewById<View>(R.id.btn_not_yet).setOnClickListener { skip() }
        findViewById<View>(R.id.btn_eaten).setOnClickListener { eaten() }
    }

    private fun eaten() {
        Prefs.updateDose(TimeUtil.today(), slotId) {
            it.copy(eaten = true, time = TimeUtil.formatHm(TimeUtil.minuteOfDay()))
        }
        finish()
    }

    private fun skip() {
        Prefs.updateDose(TimeUtil.today(), slotId) { it.copy(skips = it.skips + 1) }
        finish()
    }
}

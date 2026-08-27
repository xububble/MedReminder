package com.zwen.medreminder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.zwen.medreminder.data.Prefs
import com.zwen.medreminder.model.TimeSlot
import com.zwen.medreminder.ui.CalendarView
import com.zwen.medreminder.util.TimeUtil
import java.util.Calendar

/**
 * 主界面：首次引导 + 日历 Tab / 设置 Tab + 日历详情浮层。
 * 单 Activity 视图切换，无 Fragment、无导航库。
 */
class MainActivity : Activity() {

    private val green = Color.parseColor("#34C759")
    private val orange = Color.parseColor("#FF9500")
    private val gray = Color.parseColor("#8E8E93")

    private lateinit var pageCalendar: View
    private lateinit var pageSettings: View
    private lateinit var pageOnboarding: View
    private lateinit var titleBar: View
    private lateinit var tabBar: View
    private lateinit var tabCalendar: TextView
    private lateinit var tabSettings: TextView
    private lateinit var titleText: TextView

    private lateinit var calendar: CalendarView
    private lateinit var monthTitle: TextView
    private lateinit var todayTitle: TextView
    private lateinit var todayRows: LinearLayout
    private lateinit var lockStatusText: TextView
    private lateinit var settingsRoot: LinearLayout

    private lateinit var permNotifyBtn: TextView
    private lateinit var permAccessBtn: TextView
    private lateinit var permOverlayBtn: TextView

    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        ReminderService.start(this)
        setContentView(R.layout.activity_main)
        bindViews()
        if (Prefs.onboarded) showMain() else showOnboarding()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAll()
    }

    // ---------- 视图绑定与页面切换 ----------

    private fun bindViews() {
        pageCalendar = findViewById(R.id.page_calendar)
        pageSettings = findViewById(R.id.page_settings)
        pageOnboarding = findViewById(R.id.page_onboarding)
        titleBar = findViewById(R.id.title_bar)
        tabBar = findViewById(R.id.tab_bar)
        tabCalendar = findViewById(R.id.tab_calendar)
        tabSettings = findViewById(R.id.tab_settings)
        titleText = findViewById(R.id.title_text)

        calendar = findViewById(R.id.calendar)
        monthTitle = findViewById(R.id.month_title)
        todayTitle = findViewById(R.id.today_title)
        todayRows = findViewById(R.id.today_rows)
        lockStatusText = findViewById(R.id.lock_status_text)
        settingsRoot = findViewById(R.id.settings_root)
        permNotifyBtn = findViewById(R.id.perm_notify_btn)
        permAccessBtn = findViewById(R.id.perm_access_btn)
        permOverlayBtn = findViewById(R.id.perm_overlay_btn)

        // 月份切换
        findViewById<ImageView>(R.id.btn_prev_month).setOnClickListener { calendar.changeMonth(-1) }
        findViewById<ImageView>(R.id.btn_next_month).setOnClickListener { calendar.changeMonth(1) }
        calendar.onMonthChanged = { refreshMonthTitle() }
        // 早于首次运行日期：无记录，不弹详情
        calendar.onDayClick = { date, cal -> if (date >= Prefs.firstRunDate) showDayDetail(date, cal) }

        // Tab
        tabCalendar.setOnClickListener { switchTab(0) }
        tabSettings.setOnClickListener { switchTab(1) }

        // 标题栏设置入口
        findViewById<ImageView>(R.id.btn_settings).setOnClickListener { switchTab(1) }

        // 锁屏状态条 → 设置
        findViewById<View>(R.id.lock_status).setOnClickListener { switchTab(1) }

        // 引导页
        permNotifyBtn.setOnClickListener { enableNotifyPermission() }
        permAccessBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        permOverlayBtn.setOnClickListener { enableOverlayPermission() }
        findViewById<View>(R.id.btn_start).setOnClickListener {
            Prefs.onboarded = true
            showMain()
        }
    }

    private fun showOnboarding() {
        titleBar.visibility = View.GONE
        tabBar.visibility = View.GONE
        pageCalendar.visibility = View.GONE
        pageSettings.visibility = View.GONE
        pageOnboarding.visibility = View.VISIBLE
        refreshOnboardingPerms()
    }

    private fun showMain() {
        titleBar.visibility = View.VISIBLE
        tabBar.visibility = View.VISIBLE
        pageOnboarding.visibility = View.GONE
        switchTab(0)
        refreshAll()  // 首次从引导页进入时，此前 onResume 走的是引导分支，主界面内容需显式填充
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        pageCalendar.visibility = if (tab == 0) View.VISIBLE else View.GONE
        pageSettings.visibility = if (tab == 1) View.VISIBLE else View.GONE
        titleText.text = getString(if (tab == 0) R.string.title_home else R.string.title_settings)
        tabCalendar.setTextColor(if (tab == 0) orange else gray)
        tabSettings.setTextColor(if (tab == 1) orange else gray)
        tabCalendar.setTypeface(null, if (tab == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabSettings.setTypeface(null, if (tab == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    // ---------- 统一刷新 ----------

    private fun refreshAll() {
        if (Prefs.onboarded) {
            refreshMonthTitle()
            calendar.invalidate()
            refreshToday()
            refreshLockStatus()
            buildSettings()
        } else {
            refreshOnboardingPerms()
        }
    }

    private fun refreshMonthTitle() {
        val m = calendar.month
        monthTitle.text = "${m.get(Calendar.YEAR)}年${m.get(Calendar.MONTH) + 1}月"
    }

    // ---------- 今日状态卡 ----------

    private fun refreshToday() {
        val now = TimeUtil.now()
        val md = now.get(Calendar.MONTH) + 1
        val dd = now.get(Calendar.DAY_OF_MONTH)
        todayTitle.text = getString(R.string.today_format, "${md}月${dd}日 ${TimeUtil.weekdayName(now)}")

        todayRows.removeAllViews()
        val names = resources.getStringArray(R.array.dose_names)
        val slots = Prefs.getSlots()
        val day = Prefs.getDay(TimeUtil.today())
        val nowMin = TimeUtil.minuteOfDay()

        for (i in 0..2) {
            val row = layoutInflater.inflate(R.layout.dose_row, todayRows, false)
            row.findViewById<TextView>(R.id.row_name).text = names[i]
            val d = day.dose(i)
            val s = slots[i]
            val status = row.findViewById<TextView>(R.id.row_status)
            val time = row.findViewById<TextView>(R.id.row_time)
            when {
                d.eaten -> {
                    status.text = getString(R.string.status_eaten)
                    status.setTextColor(green)
                    time.text = d.time
                }
                nowMin in s.startMin until s.endMin -> {
                    status.text = getString(R.string.status_ongoing)
                    status.setTextColor(orange)
                    time.text = "——"
                }
                nowMin < s.startMin -> {
                    status.text = getString(R.string.status_pending)
                    status.setTextColor(gray)
                    time.text = "——"
                }
                else -> {
                    status.text = getString(R.string.status_missed)
                    status.setTextColor(gray)
                    time.text = "——"
                }
            }
            todayRows.addView(row)
        }
    }

    // ---------- 锁屏状态条 ----------

    private fun refreshLockStatus() {
        lockStatusText.text = if (Prefs.autoLock) {
            getString(R.string.lock_status_on, Prefs.lockMinutes)
        } else {
            getString(R.string.lock_status_off)
        }
    }

    // ---------- 设置页（分组列表，代码构建） ----------

    private fun buildSettings() {
        settingsRoot.removeAllViews()
        val slots = Prefs.getSlots()
        val names = resources.getStringArray(R.array.dose_names)

        // 分组1：提醒时段
        addGroupTitle(getString(R.string.section_time_slots))
        addGroupCard(
            slots.mapIndexed { i, s ->
                row(names[i], getString(R.string.time_slot_value, TimeUtil.formatHm(s.startMin), TimeUtil.formatHm(s.endMin))) {
                    editSlot(s)
                }
            }
        )

        // 分组2：自动锁屏
        addGroupTitle(getString(R.string.section_auto_lock))
        addGroupCard(listOf(
            switchRow(getString(R.string.auto_lock), Prefs.autoLock) { on ->
                Prefs.autoLock = on
                refreshAll()
            },
            row(getString(R.string.lock_duration), getString(R.string.duration_value, Prefs.lockMinutes)) {
                pickLockDuration()
            }
        ))

        // 分组3：权限
        addGroupTitle(getString(R.string.section_permissions))
        addGroupCard(listOf(
            row(getString(R.string.notify_permission), permText(isNotifyEnabled())) { enableNotifyPermission() },
            row(getString(R.string.accessibility_permission), permText(isAccessibilityEnabled())) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            row(getString(R.string.overlay_permission), permText(isOverlayEnabled())) { enableOverlayPermission() }
        ))

        // 分组4：关于
        addGroupTitle(getString(R.string.section_about))
        addGroupCard(listOf(
            row(getString(R.string.version, "1.0.0"), "", {})
        ))
    }

    private fun addGroupTitle(text: String) {
        settingsRoot.addView(TextView(this).apply {
            this.text = text
            setTextColor(gray)
            textSize = 13f
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20); bottomMargin = dp(6); marginStart = dp(8) }
            layoutParams = p
        })
    }

    private fun addGroupCard(rows: List<View>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_card)
            setPadding(dp(16), dp(4), dp(8), dp(4))
        }
        rows.forEachIndexed { i, v ->
            if (i > 0) card.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(Color.parseColor("#E5E5EA"))
            })
            card.addView(v)
        }
        settingsRoot.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun row(name: String, value: String, onClick: () -> Unit): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            setPadding(dp(0), dp(0), dp(8), dp(0))
            setOnClickListener { onClick() }
        }
        v.background = getDrawable(android.R.drawable.list_selector_background)
        v.addView(TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#000000"))
            textSize = 17f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (value.isNotEmpty()) {
            v.addView(TextView(this).apply {
                text = value
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(4) }
            })
        }
        v.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right_small)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
        return v
    }

    private fun switchRow(name: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            setPadding(dp(0), dp(0), dp(8), dp(0))
        }
        v.addView(TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#000000"))
            textSize = 17f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        v.addView(Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onChange(on) }
        })
        return v
    }

    // ---------- 设置交互 ----------

    private fun editSlot(slot: TimeSlot) {
        pickTime(slot.startMin) { start ->
            pickTime(slot.endMin) { end ->
                Prefs.setSlot(slot.id, start, end)
                refreshAll()
            }
        }
    }

    private fun pickTime(initial: Int, onPick: (Int) -> Unit) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, initial / 60)
            set(Calendar.MINUTE, initial % 60)
        }
        TimePickerDialog(
            this,
            { _, h, m -> onPick(h * 60 + m) },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun pickLockDuration() {
        val options = intArrayOf(5, 10, 15, 20, 30)
        val labels = options.map { getString(R.string.duration_value, it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.lock_duration)
            .setItems(labels) { _, i ->
                Prefs.lockMinutes = options[i]
                refreshAll()
            }
            .show()
    }

    // ---------- 权限 ----------

    private fun isNotifyEnabled(): Boolean =
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val target = "$packageName/${AutoLockService::class.java.name}"
        return enabled.split(':').any { it.equals(target, ignoreCase = true) }
    }

    private fun isOverlayEnabled(): Boolean = Settings.canDrawOverlays(this)

    private fun enableOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
        )
    }

    private fun permText(enabled: Boolean): String = getString(if (enabled) R.string.perm_on else R.string.perm_off)

    private fun enableNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }

    private fun refreshOnboardingPerms() {
        setPermBtn(permNotifyBtn, isNotifyEnabled())
        setPermBtn(permAccessBtn, isAccessibilityEnabled())
        setPermBtn(permOverlayBtn, isOverlayEnabled())
    }

    private fun setPermBtn(btn: TextView, enabled: Boolean) {
        btn.text = getString(if (enabled) R.string.perm_on else R.string.btn_enable)
        btn.backgroundTintList = ColorStateList.valueOf(if (enabled) green else orange)
    }

    // ---------- 日历详情浮层 ----------

    private fun showDayDetail(date: String, cal: Calendar) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_day_detail)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        dialog.findViewById<TextView>(R.id.detail_title).text = getString(
            R.string.detail_date, date, TimeUtil.weekdayName(cal)
        )
        dialog.findViewById<View>(R.id.detail_close).setOnClickListener { dialog.dismiss() }

        val rows = dialog.findViewById<LinearLayout>(R.id.detail_rows)
        val day = Prefs.getDay(date)
        val names = resources.getStringArray(R.array.dose_names)
        val slots = Prefs.getSlots()

        for (i in 0..2) {
            val d = day.dose(i)
            val main = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
            }
            main.addView(TextView(this).apply {
                text = names[i]
                setTextColor(Color.parseColor("#000000"))
                textSize = 17f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            main.addView(TextView(this).apply {
                text = if (d.eaten) getString(R.string.status_eaten) else getString(R.string.status_missed)
                setTextColor(if (d.eaten) green else gray)
                textSize = 17f
            })
            main.addView(TextView(this).apply {
                text = if (d.eaten) d.time else "——"
                setTextColor(if (d.eaten) Color.parseColor("#000000") else gray)
                textSize = 17f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(88), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(main)
            }
            val note = StringBuilder()
            if (d.skips > 0) note.append(getString(R.string.skip_count, d.skips))
            if (!d.eaten && TimeUtil.minuteOfDay() > slots[i].endMin) {
                if (note.isNotEmpty()) note.append(" · ")
                note.append(getString(R.string.status_not_recorded))
            }
            if (note.isNotEmpty()) {
                container.addView(TextView(this).apply {
                    text = note.toString()
                    setTextColor(Color.parseColor("#8E8E93"))
                    textSize = 13f
                    setPadding(dp(0), 0, 0, dp(8))
                })
            }
            rows.addView(container)
        }
        dialog.show()
    }

    // ---------- 工具 ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

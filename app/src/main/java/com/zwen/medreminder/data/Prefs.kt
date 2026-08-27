package com.zwen.medreminder.data

import android.content.Context
import android.content.SharedPreferences
import com.zwen.medreminder.model.DayRecord
import com.zwen.medreminder.model.DoseRecord
import com.zwen.medreminder.model.TimeSlot

/**
 * 本地存储：设置 + 每日记录，全部基于 SharedPreferences + JSON，
 * 无数据库、无第三方库，数据量级为「每天 3 条记录」，此方案最轻量。
 */
object Prefs {

    private const val SETTINGS = "settings"
    private const val RECORDS = "records"
    private const val KEY_SLOT_START = "slot_start_%d"
    private const val KEY_SLOT_END = "slot_end_%d"
    private const val KEY_AUTO_LOCK = "auto_lock"
    private const val KEY_LOCK_MINUTES = "lock_minutes"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_FIRST_RUN = "first_run_date"
    private const val KEY_DAY = "day_%s"

    const val DEFAULT_LOCK_MINUTES = 15

    private lateinit var settings: SharedPreferences
    private lateinit var records: SharedPreferences

    fun init(context: Context) {
        if (::settings.isInitialized) return
        settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        records = context.getSharedPreferences(RECORDS, Context.MODE_PRIVATE)
    }

    // ---------- 提醒时段（默认：早 08:00-10:00 / 午 11:30-14:00 / 晚 18:00-21:00） ----------
    fun getSlots(): List<TimeSlot> = defaultSlots().map {
        TimeSlot(it.id, settings.getInt(KEY_SLOT_START.format(it.id), it.startMin),
            settings.getInt(KEY_SLOT_END.format(it.id), it.endMin))
    }

    fun setSlot(slotId: Int, startMin: Int, endMin: Int) {
        settings.edit()
            .putInt(KEY_SLOT_START.format(slotId), startMin)
            .putInt(KEY_SLOT_END.format(slotId), endMin)
            .apply()
    }

    // ---------- 自动锁屏 ----------
    var autoLock: Boolean
        get() = settings.getBoolean(KEY_AUTO_LOCK, true)
        set(v) = settings.edit().putBoolean(KEY_AUTO_LOCK, v).apply()

    var lockMinutes: Int
        get() = settings.getInt(KEY_LOCK_MINUTES, DEFAULT_LOCK_MINUTES)
        set(v) = settings.edit().putInt(KEY_LOCK_MINUTES, v).apply()

    // ---------- 首次引导 ----------
    var onboarded: Boolean
        get() = settings.getBoolean(KEY_ONBOARDED, false)
        set(v) = settings.edit().putBoolean(KEY_ONBOARDED, v).apply()

    /** 首次运行日期（"yyyy-MM-dd"），早于该日期不显示吃药状态；首次读取时落库 */
    val firstRunDate: String
        get() = settings.getString(KEY_FIRST_RUN, null)
            ?: com.zwen.medreminder.util.TimeUtil.today().also {
                settings.edit().putString(KEY_FIRST_RUN, it).apply()
            }

    // ---------- 每日记录 ----------
    fun getDay(date: String): DayRecord {
        val raw = records.getString(KEY_DAY.format(date), null) ?: return DayRecord()
        return try {
            DayRecord.fromJson(org.json.JSONObject(raw))
        } catch (_: Exception) {
            DayRecord()
        }
    }

    /** 对某天某时段做一次更新 */
    fun updateDose(date: String, slotId: Int, mutate: (DoseRecord) -> DoseRecord) {
        val day = getDay(date)
        val doses = arrayOf(day.breakfast, day.lunch, day.dinner)
        doses[slotId] = mutate(doses[slotId])
        records.edit()
            .putString(KEY_DAY.format(date), DayRecord(doses[0], doses[1], doses[2]).toJson().toString())
            .apply()
    }

    private fun defaultSlots() = listOf(
        TimeSlot(0, 8 * 60, 10 * 60),
        TimeSlot(1, 11 * 60 + 30, 14 * 60),
        TimeSlot(2, 18 * 60, 21 * 60)
    )
}

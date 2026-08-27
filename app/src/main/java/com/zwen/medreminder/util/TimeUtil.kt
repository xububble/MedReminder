package com.zwen.medreminder.util

import com.zwen.medreminder.model.TimeSlot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 时间工具：系统默认时区，当日分钟数与 HH:mm 互转 */
object TimeUtil {

    private val HHMM = SimpleDateFormat("HH:mm", Locale.US)
    private val YYYYMMDD = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val WEEK_NAMES = arrayOf("日", "一", "二", "三", "四", "五", "六")

    fun now(): Calendar = Calendar.getInstance()

    /** 当日分钟数（0-1439） */
    fun minuteOfDay(cal: Calendar = now()): Int =
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    /** 当日分钟数 → HH:mm */
    fun formatHm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** HH:mm → 当日分钟数 */
    fun parseHm(text: String): Int {
        val p = text.split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }

    fun today(): String = YYYYMMDD.format(now().time)

    fun dateOf(cal: Calendar): String = YYYYMMDD.format(cal.time)

    fun weekdayName(cal: Calendar): String =
        "周" + WEEK_NAMES[cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]

    /** 当前落在哪个提醒时段（0 早 / 1 午 / 2 晚），不在任何时段返回 -1 */
    fun currentSlot(slots: List<TimeSlot>, minute: Int = minuteOfDay()): Int {
        for (s in slots) {
            if (minute in s.startMin until s.endMin) return s.id
        }
        return -1
    }

    /** 取某月的天数 */
    fun daysInMonth(cal: Calendar): Int = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

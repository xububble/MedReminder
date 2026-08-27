package com.zwen.medreminder.model

import org.json.JSONObject

/** 单个时段的一次吃药记录 */
data class DoseRecord(
    val eaten: Boolean = false, // 是否已吃
    val time: String = "",      // 已吃精确时间 HH:mm，未吃为空
    val skips: Int = 0          // 「还没吃」点击次数（跳过计数）
)

/** 一天的吃药记录：早/中/晚三时段 */
data class DayRecord(
    val breakfast: DoseRecord = DoseRecord(),
    val lunch: DoseRecord = DoseRecord(),
    val dinner: DoseRecord = DoseRecord()
) {
    /** 已吃时段数（0-3） */
    val eatenCount: Int get() = listOf(breakfast, lunch, dinner).count { it.eaten }

    fun dose(slotId: Int): DoseRecord = when (slotId) {
        0 -> breakfast; 1 -> lunch; else -> dinner
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("b", doseToJson(breakfast))
        put("l", doseToJson(lunch))
        put("d", doseToJson(dinner))
    }

    companion object {
        private fun doseToJson(d: DoseRecord) = JSONObject().apply {
            put("e", d.eaten)
            put("t", d.time)
            put("s", d.skips)
        }

        private fun doseFrom(o: JSONObject?) = if (o == null) DoseRecord() else DoseRecord(
            eaten = o.optBoolean("e"),
            time = o.optString("t"),
            skips = o.optInt("s")
        )

        fun fromJson(o: JSONObject) = DayRecord(
            breakfast = doseFrom(o.optJSONObject("b")),
            lunch = doseFrom(o.optJSONObject("l")),
            dinner = doseFrom(o.optJSONObject("d"))
        )
    }
}

/** 提醒时段定义 */
data class TimeSlot(val id: Int, val startMin: Int, val endMin: Int)

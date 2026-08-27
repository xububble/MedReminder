package com.zwen.medreminder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.zwen.medreminder.data.Prefs
import com.zwen.medreminder.util.TimeUtil
import java.util.Calendar
import kotlin.math.abs

/**
 * 月历视图：7 列网格（周一为首日）。
 * 标记规则：绿点=三次全吃 / 橙点+角标=部分吃 / 灰点=全未吃 / 未来日期无标记 / 今天=橙色圆圈。
 * 支持点击日期查看详情、左右滑动切换月份。
 */
class CalendarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 当前展示的月份（day 固定为 1） */
    var month: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }

    /** 点击某天：回调 (日期 "yyyy-MM-dd", 该天零点) */
    var onDayClick: ((String, Calendar) -> Unit)? = null

    /** 月份切换后回调（用于更新月份标题） */
    var onMonthChanged: ((Calendar) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Number): Float = v.toFloat() * density
    private fun sp(v: Int): Float = v * scaledDensity

    private val pad = dp(8)
    private val headerHeight = dp(34)
    private val cellHeight = dp(46)

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8E8E93.toInt()
        textSize = sp(13)
        textAlign = Paint.Align.CENTER
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = sp(17)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
        color = 0xFFFF9500.toInt()
    }
    private val dotGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34C759.toInt() }
    private val dotOrangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF9F0A.toInt() }
    private val dotGrayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E8E93.toInt() }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9F0A.toInt()
        textSize = sp(12)
        textAlign = Paint.Align.LEFT
    }

    private val weekHeaders = arrayOf("一", "二", "三", "四", "五", "六", "日")

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            hit(e.x, e.y)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 != null && abs(e2.x - e1.x) > dp(60) && abs(vx) > abs(vy)) {
                changeMonth(if (vx < 0) 1 else -1)
            }
            return true
        }
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (pad * 2 + headerHeight + cellHeight * 6).toInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        performClick()
        return detector.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    fun changeMonth(delta: Int) {
        month.add(Calendar.MONTH, delta)
        onMonthChanged?.invoke(month)
        invalidate()
    }

    /** 当月 1 号是周几（周一=0） */
    private val firstDayOffset: Int
        get() = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7

    override fun onDraw(canvas: Canvas) {
        val cellW = (width - pad * 2) / 7f

        // 星期表头
        for (i in 0 until 7) {
            canvas.drawText(weekHeaders[i], pad + cellW * i + cellW / 2, dp(24), headerPaint)
        }

        val days = TimeUtil.daysInMonth(month)
        val today = Calendar.getInstance()
        val isCurrentMonth =
            today.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == month.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        for (d in 1..days) {
            val index = firstDayOffset + d - 1
            val cx = pad + cellW * (index % 7) + cellW / 2
            val tc = headerHeight + cellHeight * (index / 7) + cellHeight * 0.40f
            val baseline = tc + numberPaint.textSize * 0.35f

            val isToday = isCurrentMonth && d == todayDay

            if (isToday) {
                val r = dp(15)
                canvas.drawOval(cx - r, tc - r, cx + r, tc + r, todayPaint)
            }
            canvas.drawText(d.toString(), cx, baseline, numberPaint)

            // 状态点：仅非未来日期
            val dayCal = (month.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (dayCal.after(today)) continue

            // 早于首次运行日期：无记录，不显示状态点
            val dateStr = TimeUtil.dateOf(dayCal)
            if (dateStr < Prefs.firstRunDate) continue

            val eaten = Prefs.getDay(dateStr).eatenCount
            val dotY = tc + dp(17)
            val dotR = dp(3.5f)
            when {
                eaten == 3 -> canvas.drawCircle(cx, dotY, dotR, dotGreenPaint)
                eaten > 0 -> {
                    canvas.drawCircle(cx, dotY, dotR, dotOrangePaint)
                    canvas.drawText("$eaten/3", cx + dp(6), dotY + dp(4), badgePaint)
                }
                else -> canvas.drawCircle(cx, dotY, dotR, dotGrayPaint)
            }
        }
    }

    private fun hit(x: Float, y: Float) {
        if (y < headerHeight) return
        val cellW = (width - pad * 2) / 7f
        val col = ((x - pad) / cellW).toInt().coerceIn(0, 6)
        val row = ((y - headerHeight) / cellHeight).toInt().coerceIn(0, 5)
        val dayNum = row * 7 + col - firstDayOffset + 1
        val days = TimeUtil.daysInMonth(month)
        if (dayNum in 1..days) {
            val cal = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNum) }
            onDayClick?.invoke(TimeUtil.dateOf(cal), cal)
        }
    }
}

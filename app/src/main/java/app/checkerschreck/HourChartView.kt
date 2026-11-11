package app.checkerschreck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Balkendiagramm: Checks pro Stunde. */
class HourChartView(context: Context) : View(context) {
    var values = IntArray(24)
        set(v) {
            field = v; invalidate()
        }
    var currentHour = -1
        set(v) {
            field = v; invalidate()
        }

    private val d = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7E57C2") }
    private val hot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53935") }
    private val now = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#26A69A") }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 10 * d
        textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (130 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val labelH = 16 * d
        val chartH = height - labelH - 14 * d
        val slot = width / 24f
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        for (i in 0 until 24) {
            val v = values[i]
            val h = chartH * v / max
            val left = i * slot + slot * 0.15f
            val right = (i + 1) * slot - slot * 0.15f
            val top = 14 * d + chartH - h
            val p = when {
                i == currentHour -> now
                v == max && v >= 3 -> hot
                else -> bar
            }
            canvas.drawRoundRect(left, top, right, 14 * d + chartH, 3 * d, 3 * d, p)
            if (v > 0 && v == max) canvas.drawText(v.toString(), (left + right) / 2, top - 3 * d, label)
            if (i % 3 == 0) canvas.drawText("$i", i * slot + slot / 2, height - 3 * d, label)
        }
    }
}

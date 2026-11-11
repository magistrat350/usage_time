package app.checkerschreck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zeichnet die Hässlichkeit. Farbwechsel sind bewusst weich (kein Stroboskop-Flackern).
 * Stufe 1: Matsch-Tönung. Stufe 2: Kotz-Regenbogen + Streifen. Stufe 3: Schachbrett-Chaos.
 */
class UglyView(context: Context) : View(context) {
    var level = 1
    var lines: List<String> = emptyList()

    private val start = SystemClock.uptimeMillis()
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
    }
    private val textOutline = Paint(text).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF00FF")
    }
    private val path = Path()
    private val rnd = Random(42)
    private val blobs = List(14) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 30f + rnd.nextFloat() * 90f) }

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, if (level >= 2) 66L else 200L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        when (level) {
            1 -> drawLevel1(canvas, t, w, h)
            2 -> drawLevel2(canvas, t, w, h)
            else -> drawLevel3(canvas, t, w, h)
        }
        drawText(canvas, t, w, h)
    }

    private fun drawLevel1(c: Canvas, t: Float, w: Float, h: Float) {
        // Matschiges Braun-Grün, das langsam pulsiert
        val k = (sin(t * 0.8f) + 1f) / 2f
        c.drawColor(blend(Color.parseColor("#6B5B1E"), Color.parseColor("#7A8B2E"), k))
        stripe.color = Color.parseColor("#4A3B10")
        stripe.strokeWidth = 6 * density
        diagonalStripes(c, w, h, 60 * density, t * 10 * density)
    }

    private fun drawLevel2(c: Canvas, t: Float, w: Float, h: Float) {
        val hue = (t * 40f) % 360f
        c.drawColor(Color.HSVToColor(floatArrayOf(hue, 0.9f, 0.85f)))
        stripe.color = Color.HSVToColor(floatArrayOf((hue + 180f) % 360f, 1f, 0.9f))
        stripe.strokeWidth = 22 * density
        diagonalStripes(c, w, h, 70 * density, t * 40 * density)
        fill.color = Color.parseColor("#B08D57")
        for (b in blobs.take(6)) {
            c.drawCircle(b[0] * w, (b[1] * h + t * 30 * density) % h, b[2] * density * 0.6f, fill)
        }
    }

    private fun drawLevel3(c: Canvas, t: Float, w: Float, h: Float) {
        val size = 48 * density
        val k = (sin(t * 2.2f) + 1f) / 2f // ~0,35 Hz, weich
        val a = blend(Color.parseColor("#FF00CC"), Color.parseColor("#7FFF00"), k)
        val b = blend(Color.parseColor("#8B4513"), Color.parseColor("#00FFFF"), 1f - k)
        val off = (t * 25 * density) % (2 * size)
        var y = -2 * size
        var row = 0
        while (y < h + size) {
            var x = -2 * size + off
            var col = 0
            while (x < w + size) {
                fill.color = if ((row + col) % 2 == 0) a else b
                c.drawRect(x, y + off / 2, x + size, y + size + off / 2, fill)
                x += size; col++
            }
            y += size; row++
        }
        stripe.color = Color.parseColor("#FFFF00")
        stripe.strokeWidth = 14 * density
        diagonalStripes(c, w, h, 110 * density, -t * 60 * density)
        fill.color = Color.parseColor("#556B2F")
        for (bl in blobs) {
            val r = bl[2] * density * (0.8f + 0.3f * sin(t * 1.5f + bl[0] * 10))
            c.drawCircle(bl[0] * w, bl[1] * h, r, fill)
        }
    }

    private fun diagonalStripes(c: Canvas, w: Float, h: Float, gap: Float, shift: Float) {
        path.reset()
        var x = -h + (shift % gap)
        while (x < w + h) {
            path.moveTo(x, 0f)
            path.lineTo(x + h, h)
            x += gap
        }
        c.drawPath(path, stripe)
    }

    private fun drawText(c: Canvas, t: Float, w: Float, h: Float) {
        if (lines.isEmpty()) return
        val base = when (level) {
            1 -> 22f
            2 -> 30f
            else -> 40f
        } * density
        val wobble = if (level >= 2) sin(t * 3f) * 8f else 0f
        c.save()
        c.rotate(wobble, w / 2, h / 2)
        var y = h / 2 - (lines.size - 1) * base * 0.75f
        lines.forEachIndexed { i, line ->
            val size = if (i == 0) base * 1.25f else base * 0.7f
            text.textSize = size
            textOutline.textSize = size
            textOutline.strokeWidth = size / 6f
            c.drawText(line, w / 2, y, textOutline)
            c.drawText(line, w / 2, y, text)
            y += size * 1.3f
        }
        c.restore()
    }

    private fun blend(a: Int, b: Int, k: Float): Int {
        val f = k.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }

    companion object {
        val INSULTS = listOf(
            "Leg. Das. Handy. Weg.",
            "Dein Daumen braucht Urlaub",
            "Die Realität hat angerufen",
            "Es gibt nichts Neues. Ehrlich.",
            "Noch ein Scroll = noch hässlicher",
            "Schau mal aus dem Fenster",
            "Du hast das gerade schon gesehen",
            "Wer checkt, verliert",
            "Ich werde nur noch schlimmer",
        )
    }
}

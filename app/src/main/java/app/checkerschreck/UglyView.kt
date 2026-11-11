package app.checkerschreck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zeichnet die Hässlichkeit. Farbwechsel sind bewusst weich (kein Stroboskop-Flackern).
 *
 * Stufe 1: Matsch-Tönung, Streifen, Bildrauschen, dunkler Rand, Absperrband-Rahmen.
 * Stufe 2: dazu Kotz-Regenbogen, Wasserzeichen, Sprung im Display, treibende Ekel-Emojis.
 * Stufe 3: dazu Schachbrett-Chaos, defekte Pixelspalten, großer Displaybruch, zitternder Text.
 */
class UglyView(context: Context) : View(context) {
    var level = 1
    var lines: List<String> = emptyList()

    private val start = SystemClock.uptimeMillis()
    private val d = resources.displayMetrics.density
    private val rnd = Random(42)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("casual", Typeface.BOLD) // Androids Comic-Sans-Verschnitt
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
    }
    private val textOutline = Paint(text).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF00FF")
    }
    private val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("cursive", Typeface.BOLD)
        textSize = 26 * d
        color = Color.WHITE
    }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val crack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val crackShadow = Paint(crack).apply { color = Color.argb(170, 0, 0, 0) }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val noiseShader = BitmapShader(noiseBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val noise = Paint().apply { shader = noiseShader }
    private val noiseMatrix = Matrix()
    private val vignette = Paint()

    private val path = Path()
    private val crackPath = Path()
    private val blobs = List(14) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 30f + rnd.nextFloat() * 90f) }

    /** x, y, Größe, Tempo, Emoji-Index */
    private val drifters = List(10) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 36f + rnd.nextFloat() * 40f, 0.4f + rnd.nextFloat(), it.toFloat())
    }
    private val deadColumns = List(6) { rnd.nextFloat() }
    private val deadColors = intArrayOf(Color.MAGENTA, Color.GREEN, Color.CYAN, Color.WHITE, Color.RED, Color.YELLOW)

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, if (level >= 2) 66L else 300L)
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        vignette.shader = RadialGradient(
            w / 2f, h / 2f, hypot(w.toFloat(), h.toFloat()) / 2f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(215, 43, 26, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        buildCracks(w.toFloat(), h.toFloat())
    }

    private fun noiseBitmap(): Bitmap {
        val size = 96
        val px = IntArray(size * size) {
            val v = if (rnd.nextBoolean()) 255 else 0
            Color.argb(rnd.nextInt(256), v, v, v)
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Displaybruch: gezackte Strahlen von einem Einschlagpunkt, mit Abzweigungen und Ringen. */
    private fun buildCracks(w: Float, h: Float) {
        val r = Random(7)
        crackPath.reset()
        val cx = w * 0.64f
        val cy = h * 0.36f
        for (i in 0 until 11) {
            var angle = i * (2 * Math.PI / 11) + r.nextDouble(-0.25, 0.25)
            var x = cx
            var y = cy
            crackPath.moveTo(x, y)
            var steps = 0
            while (x in -50f..w + 50f && y in -50f..h + 50f && steps < 16) {
                val len = (40 + r.nextInt(80)) * d
                angle += r.nextDouble(-0.35, 0.35)
                x += (cos(angle) * len).toFloat()
                y += (sin(angle) * len).toFloat()
                crackPath.lineTo(x, y)
                if (r.nextFloat() < 0.35f) { // Abzweigung
                    var bx = x
                    var by = y
                    var ba = angle + (if (r.nextBoolean()) 1 else -1) * r.nextDouble(0.5, 1.1)
                    repeat(2 + r.nextInt(2)) {
                        val bl = (20 + r.nextInt(50)) * d
                        ba += r.nextDouble(-0.3, 0.3)
                        bx += (cos(ba) * bl).toFloat()
                        by += (sin(ba) * bl).toFloat()
                        crackPath.lineTo(bx, by)
                    }
                    crackPath.moveTo(x, y)
                }
                steps++
            }
        }
        for (radius in listOf(34f, 80f, 150f)) { // Ringe um den Einschlag
            val n = 14
            for (k in 0..n) {
                val a = k * (2 * Math.PI / n)
                val rr = radius * d * (0.8f + r.nextFloat() * 0.4f)
                val px = cx + (cos(a) * rr).toFloat()
                val py = cy + (sin(a) * rr).toFloat()
                if (k == 0) crackPath.moveTo(px, py) else crackPath.lineTo(px, py)
            }
        }
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
        if (level >= 2) {
            drawWatermark(canvas, t, w, h)
            drawDrifters(canvas, t, w, h)
        }
        drawNoise(canvas, w, h)
        canvas.drawRect(0f, 0f, w, h, vignette)
        if (level >= 2) drawCracks(canvas)
        if (level >= 3) drawDeadColumns(canvas, t, w, h)
        drawBorder(canvas, t, w, h)
        drawText(canvas, t, w, h)
    }

    private fun drawLevel1(c: Canvas, t: Float, w: Float, h: Float) {
        // Matschiges Braun-Grün, das langsam pulsiert
        val k = (sin(t * 0.8f) + 1f) / 2f
        c.drawColor(blend(Color.parseColor("#6B5B1E"), Color.parseColor("#7A8B2E"), k))
        stripe.color = Color.parseColor("#4A3B10")
        stripe.strokeWidth = 8 * d
        diagonalStripes(c, w, h, 54 * d, t * 10 * d)
        fill.color = Color.parseColor("#5A4A12")
        for (b in blobs.take(5)) c.drawCircle(b[0] * w, b[1] * h, b[2] * d * 0.5f, fill)
    }

    private fun drawLevel2(c: Canvas, t: Float, w: Float, h: Float) {
        val hue = (t * 40f) % 360f
        c.drawColor(Color.HSVToColor(floatArrayOf(hue, 0.9f, 0.85f)))
        stripe.color = Color.HSVToColor(floatArrayOf((hue + 180f) % 360f, 1f, 0.9f))
        stripe.strokeWidth = 22 * d
        diagonalStripes(c, w, h, 70 * d, t * 40 * d)
        fill.color = Color.parseColor("#B08D57")
        for (b in blobs.take(8)) {
            c.drawCircle(b[0] * w, (b[1] * h + t * 30 * d) % h, b[2] * d * 0.6f, fill)
        }
    }

    private fun drawLevel3(c: Canvas, t: Float, w: Float, h: Float) {
        val size = 48 * d
        val k = (sin(t * 2.2f) + 1f) / 2f // ~0,35 Hz, weich
        val a = blend(Color.parseColor("#FF00CC"), Color.parseColor("#7FFF00"), k)
        val b = blend(Color.parseColor("#8B4513"), Color.parseColor("#00FFFF"), 1f - k)
        val off = (t * 25 * d) % (2 * size)
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
        stripe.strokeWidth = 14 * d
        diagonalStripes(c, w, h, 110 * d, -t * 60 * d)
        fill.color = Color.parseColor("#556B2F")
        for (bl in blobs) {
            val r = bl[2] * d * (0.8f + 0.3f * sin(t * 1.5f + bl[0] * 10))
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

    /** Wasserzeichen wie auf einem geklauten Stockfoto. */
    private fun drawWatermark(c: Canvas, t: Float, w: Float, h: Float) {
        val label = "LEG DAS HANDY WEG"
        watermark.alpha = if (level >= 3) 130 else 85
        val stepX = watermark.measureText(label) + 50 * d
        val stepY = 130 * d
        val drift = (t * 18 * d) % stepX
        c.save()
        c.rotate(-28f, w / 2, h / 2)
        var y = -h * 0.6f
        var row = 0
        while (y < h * 1.6f) {
            var x = -w + drift + (if (row % 2 == 0) 0f else stepX / 2)
            while (x < w * 2) {
                c.drawText(label, x, y, watermark)
                x += stepX
            }
            y += stepY; row++
        }
        c.restore()
    }

    /** Ekel-Emojis, die langsam über den Bildschirm treiben. */
    private fun drawDrifters(c: Canvas, t: Float, w: Float, h: Float) {
        val n = if (level >= 3) drifters.size else 5
        for (i in 0 until n) {
            val f = drifters[i]
            val size = f[2] * d * (if (level >= 3) 1.3f else 1f)
            emoji.textSize = size
            val x = (f[0] * w + sin(t * f[3] + i) * 40 * d + w) % w
            val y = (f[1] * h + t * f[3] * 26 * d) % (h + size) - size / 2
            c.save()
            c.rotate(sin(t * f[3] * 1.3f + i) * 25f, x, y)
            c.drawText(UglyStickers.EMOJIS[(f[4].toInt()) % UglyStickers.EMOJIS.size], x, y, emoji)
            c.restore()
        }
    }

    /** Bildrauschen wie bei einem Fernseher ohne Empfang. */
    private fun drawNoise(c: Canvas, w: Float, h: Float) {
        noise.alpha = when (level) {
            1 -> 70
            2 -> 105
            else -> 150
        }
        val scale = 1.4f * d
        noiseMatrix.setScale(scale, scale)
        noiseMatrix.postTranslate(Random.nextInt(96) * scale, Random.nextInt(96) * scale)
        noiseShader.setLocalMatrix(noiseMatrix)
        c.drawRect(0f, 0f, w, h, noise)
    }

    private fun drawCracks(c: Canvas) {
        val width = if (level >= 3) 2.6f * d else 1.4f * d
        crackShadow.strokeWidth = width * 2.2f
        crack.strokeWidth = width
        crack.alpha = if (level >= 3) 255 else 190
        c.drawPath(crackPath, crackShadow)
        c.drawPath(crackPath, crack)
    }

    /** Defekte Pixelspalten wie bei einem kaputten Display. */
    private fun drawDeadColumns(c: Canvas, t: Float, w: Float, h: Float) {
        deadColumns.forEachIndexed { i, x ->
            fill.color = deadColors[i % deadColors.size]
            fill.alpha = (170 + 80 * sin(t * (1.1f + i * 0.3f))).toInt().coerceIn(60, 255)
            val left = x * w
            c.drawRect(left, 0f, left + (1.5f + i % 3) * d, h, fill)
        }
        fill.alpha = 255
    }

    /** Absperrband als Rahmen, das um den Bildschirm wandert. */
    private fun drawBorder(c: Canvas, t: Float, w: Float, h: Float) {
        val bw = when (level) {
            1 -> 7
            2 -> 13
            else -> 20
        } * d
        val dash = 26 * d
        border.strokeWidth = bw
        val inset = bw / 2
        val phase = (t * 30 * d) % (2 * dash)
        border.color = Color.parseColor("#FFEB00")
        border.pathEffect = DashPathEffect(floatArrayOf(dash, dash), phase)
        c.drawRect(inset, inset, w - inset, h - inset, border)
        border.color = if (level >= 2) Color.parseColor("#FF00CC") else Color.BLACK
        border.pathEffect = DashPathEffect(floatArrayOf(dash, dash), phase + dash)
        c.drawRect(inset, inset, w - inset, h - inset, border)
    }

    private fun drawText(c: Canvas, t: Float, w: Float, h: Float) {
        if (lines.isEmpty()) return
        val base = when (level) {
            1 -> 15f
            2 -> 30f
            else -> 40f
        } * d
        val wobble = if (level >= 2) sin(t * 3f) * 8f else 0f
        c.save()
        if (level >= 3) c.translate(Random.nextFloat() * 6 * d - 3 * d, Random.nextFloat() * 6 * d - 3 * d)
        c.rotate(wobble, w / 2, h / 2)
        var y = if (level == 1) h * 0.10f + base else h / 2 - (lines.size - 1) * base * 0.75f
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

package app.checkerschreck

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Schrumpfender Bildschirm: vier schwarze Balken an den Rändern, die mit der Zeit wachsen.
 * Die Balken sind undurchsichtig und schlucken Berührungen, der nutzbare Bereich wird also
 * wirklich kleiner.
 */
class ShrinkBars(context: Context) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val views = arrayOfNulls<View>(4)
    private var percent = 0

    /** [fraction]: Anteil der Bildschirmhöhe pro Balken oben/unten (links/rechts die Hälfte davon). */
    fun update(fraction: Float) {
        val pct = (fraction * 100).roundToInt().coerceIn(0, MAX_PERCENT)
        if (pct == percent) return
        percent = pct
        if (pct <= 0 || !Permissions.canDrawOverlays(ctx)) {
            hide(); return
        }
        val dm = ctx.resources.displayMetrics
        val h = dm.heightPixels * pct / 100
        val w = dm.widthPixels * pct / 200
        place(0, Gravity.TOP, WindowManager.LayoutParams.MATCH_PARENT, h)
        place(1, Gravity.BOTTOM, WindowManager.LayoutParams.MATCH_PARENT, h)
        place(2, Gravity.START, w, WindowManager.LayoutParams.MATCH_PARENT)
        place(3, Gravity.END, w, WindowManager.LayoutParams.MATCH_PARENT)
    }

    fun hide() {
        percent = 0
        for (i in views.indices) {
            views[i]?.let { runCatching { wm.removeView(it) } }
            views[i] = null
        }
    }

    private fun place(i: Int, gravity: Int, width: Int, height: Int) {
        val lp = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        lp.gravity = gravity
        val existing = views[i]
        if (existing != null) {
            runCatching { wm.updateViewLayout(existing, lp) }
            return
        }
        val v = View(ctx).apply {
            setBackgroundColor(Color.BLACK)
            setOnTouchListener { _, _ -> true }
        }
        runCatching { wm.addView(v, lp) }.onSuccess { views[i] = v }
    }

    companion object {
        const val MAX_PERCENT = 12

        /** Start bei 3 %, pro Minute über dem Limit 1 % mehr, höchstens [MAX_PERCENT] %. */
        fun fractionFor(overMs: Long): Float =
            ((3 + (overMs.coerceAtLeast(0) / 60_000L)).coerceAtMost(MAX_PERCENT.toLong())) / 100f
    }
}

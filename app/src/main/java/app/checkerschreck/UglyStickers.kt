package app.checkerschreck

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import kotlin.random.Random

/**
 * Kleckse: kleine, undurchsichtige Flecken, die alle paar Sekunden woanders kleben und
 * Berührungen schlucken. Status- und Navigationsleiste bleiben frei.
 */
class UglyStickers(context: Context) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val views = ArrayList<TextView>()
    private var lastMove = 0L

    fun update(count: Int, now: Long) {
        if (count <= 0 || !Permissions.canDrawOverlays(ctx)) {
            hide(); return
        }
        while (views.size > count) remove(views.last())
        while (views.size < count) if (!add()) break
        if (now - lastMove > MOVE_EVERY_MS) {
            lastMove = now
            views.forEach { move(it) }
        }
    }

    fun hide() {
        while (views.isNotEmpty()) remove(views.last())
    }

    private fun remove(v: TextView) {
        runCatching { wm.removeView(v) }
        views.remove(v)
    }

    private fun add(): Boolean {
        val size = ctx.dp(72 + Random.nextInt(40))
        val v = TextView(ctx).apply {
            text = EMOJIS.random()
            textSize = 30f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLORS.random())
                setStroke(ctx.dp(3), Color.BLACK)
            }
            setOnTouchListener { _, _ -> true }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        place(lp, size)
        return runCatching { wm.addView(v, lp) }.onSuccess { views += v }.isSuccess
    }

    private fun move(v: TextView) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        v.text = EMOJIS.random()
        place(lp, lp.width)
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun place(lp: WindowManager.LayoutParams, size: Int) {
        val dm = ctx.resources.displayMetrics
        val top = ctx.dp(70)
        val bottom = ctx.dp(90)
        lp.x = Random.nextInt((dm.widthPixels - size).coerceAtLeast(1))
        lp.y = top + Random.nextInt((dm.heightPixels - size - top - bottom).coerceAtLeast(1))
    }

    companion object {
        private const val MOVE_EVERY_MS = 9_000L
        val EMOJIS = listOf("🤢", "💩", "🦠", "🪰", "🧟", "🤮", "🪳", "👁️", "🧻", "🦷")
        private val COLORS = listOf("#8B4513", "#9ACD32", "#FF00CC", "#556B2F", "#B8860B", "#00CED1").map { Color.parseColor(it) }
    }
}

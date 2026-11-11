package app.checkerschreck

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.view.WindowManager
import kotlin.math.min

/**
 * Legt ein Fenster über alle Apps, das das Handy hässlich macht.
 * Berührungen gehen durch (FLAG_NOT_TOUCHABLE). Die Fenster-Deckkraft bleibt unter dem
 * Android-Grenzwert (0,8), sonst würde Android die Berührungen blockieren.
 */
class UglyOverlay(context: Context) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: UglyView? = null
    private var shownLevel = 0

    val isShowing get() = view != null

    fun show(level: Int, lines: List<String>) {
        if (level <= 0) {
            hide(); return
        }
        if (!Permissions.canDrawOverlays(ctx)) return
        val existing = view
        if (existing == null) {
            val v = UglyView(ctx)
            v.level = level
            v.lines = lines
            runCatching { wm.addView(v, params(level)) }.onSuccess {
                view = v
                shownLevel = level
            }
            return
        }
        existing.lines = lines
        if (level != shownLevel) {
            existing.level = level
            runCatching { wm.updateViewLayout(existing, params(level)) }
            shownLevel = level
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        shownLevel = 0
    }

    @Suppress("DEPRECATION")
    private fun params(level: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        alpha = alphaFor(level)
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun alphaFor(level: Int): Float {
        val wanted = when (level) {
            1 -> 0.42f
            2 -> 0.62f
            else -> 0.78f
        }
        val maxAllowed = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch ?: 0.8f
        } else 1f
        return min(wanted, maxAllowed - 0.02f).coerceAtLeast(0.05f)
    }
}

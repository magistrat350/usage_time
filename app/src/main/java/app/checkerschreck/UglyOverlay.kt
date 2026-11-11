package app.checkerschreck

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.view.WindowManager
import kotlin.math.min

/**
 * Legt ein Fenster über alles, das das Handy hässlich macht. Berührungen gehen durch.
 *
 * Ist die Bedienungshilfe aktiv, wird das Fenster über sie eingeblendet. Dann liegt es auch über
 * Benachrichtigungsleiste, Schnelleinstellungen, Status- und Navigationsleiste und dem
 * Sperrbildschirm. Ohne Bedienungshilfe bleibt nur ein normales Overlay, das Android unter diese
 * Bereiche legt; dessen Deckkraft muss außerdem unter dem Android-Grenzwert (0,8) bleiben, sonst
 * würde Android die Berührungen blockieren.
 */
class UglyOverlay(context: Context) {
    private val app = context.applicationContext
    private var view: UglyView? = null
    private var host: Context? = null
    private var shownLevel = 0

    val isShowing get() = view != null

    /** true, wenn das Overlay gerade auch über der Benachrichtigungsleiste liegt. */
    val coversSystemUi get() = view != null && host is AccessibilityService

    fun show(level: Int, lines: List<String>) {
        if (level <= 0) {
            hide(); return
        }
        val wanted: Context = RecipeAccessibilityService.instance ?: app
        val trusted = wanted is AccessibilityService
        if (!trusted && !Permissions.canDrawOverlays(app)) {
            hide(); return
        }
        // Bedienungshilfe an- oder ausgegangen: Fenster neu aufbauen
        if (view != null && host !== wanted) hide()

        val existing = view
        if (existing == null) {
            val v = UglyView(wanted)
            v.level = level
            v.lines = lines
            runCatching {
                wanted.getSystemService(WindowManager::class.java).addView(v, params(level, trusted))
            }.onSuccess {
                view = v
                host = wanted
                shownLevel = level
            }
            return
        }
        existing.lines = lines
        if (level != shownLevel) {
            existing.level = level
            runCatching {
                wanted.getSystemService(WindowManager::class.java).updateViewLayout(existing, params(level, trusted))
            }
            shownLevel = level
        }
    }

    fun hide() {
        val v = view
        if (v != null) runCatching { host?.getSystemService(WindowManager::class.java)?.removeView(v) }
        view = null
        host = null
        shownLevel = 0
    }

    @Suppress("DEPRECATION")
    private fun params(level: Int, trusted: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (trusted) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        alpha = alphaFor(level, trusted)
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun alphaFor(level: Int, trusted: Boolean): Float {
        val wanted = when (level) {
            1 -> 0.42f
            2 -> 0.62f
            else -> if (trusted) 0.84f else 0.78f
        }
        // Fenster der Bedienungshilfe gelten als vertrauenswürdig: kein Grenzwert
        if (trusted) return wanted
        val maxAllowed = if (Build.VERSION.SDK_INT >= 31) {
            app.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch ?: 0.8f
        } else 1f
        return min(wanted, maxAllowed - 0.02f).coerceAtLeast(0.05f)
    }
}

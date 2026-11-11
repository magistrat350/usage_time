package app.checkerschreck

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Morgensperre: undurchsichtiges Vollbild, das Berührungen schluckt. Telefonieren und die
 * Notfall-Freigabe bleiben über die Knöpfe erreichbar.
 */
class BlockerOverlay(context: Context) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var message: TextView? = null

    val isShowing get() = root != null

    fun show(text: String) {
        if (!Permissions.canDrawOverlays(ctx)) return
        message?.let {
            it.text = text
            return
        }
        val themed = ContextThemeWrapper(ctx, R.style.Theme_CheckerSchreck)
        val box = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(themed.dp(32), themed.dp(32), themed.dp(32), themed.dp(32))
            isClickable = true
        }
        box.text("☀️ Guten Morgen", 30f, bold = true).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val msg = box.text(text, 18f, topDp = 16).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        box.text(
            "Erst aufstehen, Wasser trinken, aus dem Fenster schauen.",
            15f, topDp = 12, alpha = 0.7f,
        ).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        box.button("📞 Telefon") {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        box.button("🆘 Notfall-Freigabe", outlined = true) {
            runCatching {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_LIFT_MORNING, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching { wm.addView(box, lp) }.onSuccess {
            root = box
            message = msg
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        message = null
    }
}

package app.checkerschreck

import android.content.Context
import android.provider.Settings
import kotlin.math.max

/**
 * Riesige Schrift (braucht "Systemeinstellungen ändern", ohne PC erteilbar) und
 * zähe Animationen (braucht die adb-Freigabe). Ursprüngliche Werte werden gemerkt
 * und wiederhergestellt.
 */
object SystemTweaks {
    private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

    fun apply(ctx: Context, p: Prefs, level: Int) {
        applyFont(ctx, p, if (p.uglyFont && level >= 2) level.coerceAtMost(3) else 0)
        applyAnimations(ctx, p, if (p.uglyAnimations && level >= 2) level.coerceAtMost(3) else 0)
    }

    fun restore(ctx: Context, p: Prefs) {
        applyFont(ctx, p, 0)
        applyAnimations(ctx, p, 0)
    }

    private fun applyFont(ctx: Context, p: Prefs, target: Int) {
        if (target == p.fontApplied) return
        if (!Permissions.canWriteSettings(ctx)) return
        val cr = ctx.contentResolver
        runCatching {
            if (p.fontApplied == 0) p.fontOrig = Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f).toString()
            val orig = p.fontOrig.toFloatOrNull() ?: 1f
            val scale = when (target) {
                2 -> max(1.5f, orig * 1.3f)
                3 -> max(2.0f, orig * 1.6f)
                else -> orig
            }
            Settings.System.putFloat(cr, Settings.System.FONT_SCALE, scale)
            p.fontApplied = target
        }
    }

    private fun applyAnimations(ctx: Context, p: Prefs, target: Int) {
        if (target == p.animApplied) return
        if (!Permissions.hasSecureSettings(ctx)) return
        val cr = ctx.contentResolver
        runCatching {
            if (p.animApplied == 0) {
                p.animOrig = ANIM_KEYS.joinToString("|") { Settings.Global.getFloat(cr, it, 1f).toString() }
            }
            val orig = p.animOrig.split('|').map { it.toFloatOrNull() ?: 1f }
            ANIM_KEYS.forEachIndexed { i, key ->
                val v = when (target) {
                    2 -> 2.5f
                    3 -> 5f
                    else -> orig.getOrElse(i) { 1f }
                }
                Settings.Global.putFloat(cr, key, v)
            }
            p.animApplied = target
        }
    }
}

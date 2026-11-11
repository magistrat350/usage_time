package app.checkerschreck

import android.content.Context
import android.provider.Settings

/**
 * Schaltet systemweit Graustufen bzw. Farbinvertierung (Android-Bedienungshilfen).
 * Funktioniert nur, wenn per adb WRITE_SECURE_SETTINGS erteilt wurde.
 * Merkt sich die ursprünglichen Werte und stellt sie wieder her.
 */
object SystemColorHack {
    private const val DALT_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALT = "accessibility_display_daltonizer"
    private const val INV = "accessibility_display_inversion_enabled"
    private const val MONOCHROMACY = 0

    fun apply(ctx: Context, p: Prefs, gray: Boolean, invert: Boolean) {
        if (gray == p.grayApplied && invert == p.invApplied) return
        if (!Permissions.hasSecureSettings(ctx)) return
        val cr = ctx.contentResolver
        runCatching {
            if ((gray || invert) && !p.colorOrigSaved) {
                p.origDaltEnabled = Settings.Secure.getInt(cr, DALT_ENABLED, 0)
                p.origDalt = Settings.Secure.getInt(cr, DALT, -1)
                p.origInv = Settings.Secure.getInt(cr, INV, 0)
                p.colorOrigSaved = true
            }
            if (gray != p.grayApplied) {
                if (gray) {
                    Settings.Secure.putInt(cr, DALT, MONOCHROMACY)
                    Settings.Secure.putInt(cr, DALT_ENABLED, 1)
                } else {
                    if (p.origDalt >= 0) Settings.Secure.putInt(cr, DALT, p.origDalt)
                    Settings.Secure.putInt(cr, DALT_ENABLED, p.origDaltEnabled)
                }
                p.grayApplied = gray
            }
            if (invert != p.invApplied) {
                Settings.Secure.putInt(cr, INV, if (invert) 1 else p.origInv)
                p.invApplied = invert
            }
            if (!gray && !invert) p.colorOrigSaved = false
        }
    }

    fun restore(ctx: Context, p: Prefs) = apply(ctx, p, gray = false, invert = false)
}

package app.checkerschreck

import android.content.Context
import android.widget.Toast

/** Manueller Kochmodus, falls das Rezept nicht automatisch erkannt wird. Begrenzt pro Tag. */
object CookingMode {
    fun start(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        MonitorService.rolloverIfNeeded(p, now)
        if (p.cookingUntil > now) {
            toast(ctx, "Kochmodus läuft schon noch ${fmtDuration(p.cookingUntil - now)}.")
            return false
        }
        if (p.cookingUsed >= p.cookingPerDay) {
            toast(ctx, "Heute schon ${p.cookingUsed}× gekocht. Netter Versuch. 🙄")
            return false
        }
        p.cookingUsed = p.cookingUsed + 1
        p.cookingUntil = now + p.cookingMinutes * 60_000L
        toast(ctx, "🍳 Kochmodus für ${p.cookingMinutes} min. Guten Appetit!")
        return true
    }

    fun stop(ctx: Context) {
        Prefs(ctx).cookingUntil = 0L
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

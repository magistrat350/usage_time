package app.checkerschreck

import kotlin.math.max
import kotlin.math.min

/** Berechnet die Hässlich-Stufe 0..3. */
object Escalation {
    fun level(p: Prefs, sessionMs: Long, dailyMs: Long, penaltyLevel: Int): Int {
        var l = 0
        val limit = p.sessionLimitMin * 60_000L
        val step = max(1, p.escalationStepMin) * 60_000L
        if (sessionMs >= limit) l = 1 + ((sessionMs - limit) / step).toInt()
        val daily = p.dailyLimitMin * 60_000L
        if (dailyMs >= daily) l = max(l, 1 + ((dailyMs - daily) / (30 * 60_000L)).toInt())
        l = max(l, penaltyLevel)
        return min(l, p.maxLevel.coerceIn(1, 3)).coerceAtLeast(0)
    }

    fun minutesToNextLevel(p: Prefs, sessionMs: Long): Long {
        val limit = p.sessionLimitMin * 60_000L
        if (sessionMs < limit) return (limit - sessionMs) / 60_000L
        val step = max(1, p.escalationStepMin) * 60_000L
        return (step - (sessionMs - limit) % step) / 60_000L
    }
}

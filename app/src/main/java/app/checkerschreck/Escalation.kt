package app.checkerschreck

import kotlin.math.max
import kotlin.math.min

/**
 * Berechnet die Hässlich-Stufe 0..3.
 * Mit "Grundstufe 1" ist das Handy immer mindestens Stufe 1 hässlich; das Session-Limit,
 * das Tageslimit und Check-Attacken schalten dann auf Stufe 2 und 3 hoch.
 */
object Escalation {
    fun base(p: Prefs) = if (p.alwaysUgly) 1 else 0

    fun level(p: Prefs, sessionMs: Long, dailyMs: Long, penaltyLevel: Int): Int {
        val base = base(p)
        var l = base
        val limit = p.sessionLimitMin * 60_000L
        val step = max(1, p.escalationStepMin) * 60_000L
        if (sessionMs >= limit) l = base + 1 + ((sessionMs - limit) / step).toInt()
        val daily = p.dailyLimitMin * 60_000L
        if (dailyMs >= daily) l = max(l, base + 1 + ((dailyMs - daily) / (30 * 60_000L)).toInt())
        if (penaltyLevel > 0) l = max(l, base + penaltyLevel)
        return min(l, p.maxLevel.coerceIn(1, 3)).coerceAtLeast(0)
    }

    fun minutesToNextLevel(p: Prefs, sessionMs: Long): Long {
        val limit = p.sessionLimitMin * 60_000L
        if (sessionMs < limit) return (limit - sessionMs) / 60_000L
        val step = max(1, p.escalationStepMin) * 60_000L
        return (step - (sessionMs - limit) % step) / 60_000L
    }
}

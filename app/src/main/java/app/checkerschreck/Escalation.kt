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

    /** Zusatzstufen, wenn das Check-Budget überzogen ist (1 = drüber, 2 = mehr als 50 % drüber). */
    fun checkLevel(p: Prefs, checksToday: Int): Int {
        val budget = p.checkBudget
        if (budget <= 0 || checksToday <= budget) return 0
        return if (checksToday > budget * 3 / 2) 2 else 1
    }

    /** Session-Limit in ms; [factor] < 1 macht es strenger (tagsüber). */
    fun sessionLimitMs(p: Prefs, factor: Double) = max(30_000L, (p.sessionLimitMin * 60_000L * factor).toLong())

    private fun stepMs(p: Prefs, factor: Double) = max(30_000L, (max(1, p.escalationStepMin) * 60_000L * factor).toLong())

    fun level(
        p: Prefs,
        sessionMs: Long,
        dailyMs: Long,
        penaltyLevel: Int,
        checkLevel: Int = 0,
        factor: Double = 1.0,
    ): Int {
        val base = base(p)
        var l = base
        val limit = sessionLimitMs(p, factor)
        val step = stepMs(p, factor)
        if (sessionMs >= limit) l = base + 1 + ((sessionMs - limit) / step).toInt()
        val daily = p.dailyLimitMin * 60_000L
        if (dailyMs >= daily) l = max(l, base + 1 + ((dailyMs - daily) / (30 * 60_000L)).toInt())
        if (penaltyLevel > 0) l = max(l, base + penaltyLevel)
        if (checkLevel > 0) l = max(l, base + checkLevel)
        return min(l, p.maxLevel.coerceIn(1, 3)).coerceAtLeast(0)
    }

    fun minutesToNextLevel(p: Prefs, sessionMs: Long, factor: Double = 1.0): Long {
        val limit = sessionLimitMs(p, factor)
        if (sessionMs < limit) return (limit - sessionMs + 59_999L) / 60_000L
        val step = stepMs(p, factor)
        return (step - (sessionMs - limit) % step + 59_999L) / 60_000L
    }
}

package app.checkerschreck

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Zusammenfassung eines abgeschlossenen Tages. */
data class DayRecord(
    val day: String,
    val activeMs: Long,
    val checks: Int,
    val youtubeMs: Long,
    val longestSessionMs: Long,
    val scrollMs: Long = 0L,
)

/** Tagesverlauf (letzte 60 Tage), gespeichert in den Prefs. */
object History {
    private const val KEEP = 60

    fun all(p: Prefs): List<DayRecord> = p.historyRaw.lines().mapNotNull { line ->
        val f = line.split('|')
        if (f.size < 5) return@mapNotNull null
        DayRecord(
            f[0],
            f[1].toLongOrNull() ?: return@mapNotNull null,
            f[2].toIntOrNull() ?: return@mapNotNull null,
            f[3].toLongOrNull() ?: return@mapNotNull null,
            f[4].toLongOrNull() ?: return@mapNotNull null,
            f.getOrNull(5)?.toLongOrNull() ?: 0L,
        )
    }

    fun add(p: Prefs, r: DayRecord) {
        val list = all(p).filter { it.day != r.day } + r
        p.historyRaw = list.sortedBy { it.day }.takeLast(KEEP).joinToString("\n") {
            "${it.day}|${it.activeMs}|${it.checks}|${it.youtubeMs}|${it.longestSessionMs}|${it.scrollMs}"
        }
    }

    fun recent(p: Prefs, n: Int = 7): List<DayRecord> = all(p).takeLast(n)
}

/**
 * Dreht die Limits automatisch zurück: Jeden Tag wird die echte Nutzung der letzten 7 Tage
 * ausgewertet und jedes Limit auf "Schnitt minus X %" gesetzt, sofern das strenger ist als bisher.
 * Limits werden nie automatisch lockerer.
 */
object Adaptive {
    const val MIN_DAYS = 3
    const val FLOOR_DAILY_MIN = 30
    const val FLOOR_SESSION_MIN = 3
    const val FLOOR_YOUTUBE_MIN = 5
    const val FLOOR_CHECKS = 15

    /** Ein Limit sinkt pro Tag höchstens um diesen Anteil. */
    private const val MAX_DROP_PER_DAY = 0.10

    /** Tut sich so viele Tage nichts, wird trotzdem um [FORCED_DROP] gedreht. */
    private const val FORCED_AFTER_DAYS = 7
    private const val FORCED_DROP = 0.05

    data class Averages(
        val days: Int,
        val activeMin: Double,
        val checks: Double,
        val longestSessionMin: Double,
        /** Schnitt nur über Tage, an denen YouTube lief; null, wenn es keinen gab. */
        val youtubeMin: Double?,
    )

    fun averages(p: Prefs): Averages? {
        val r = History.recent(p)
        if (r.isEmpty()) return null
        val yt = r.filter { it.youtubeMs > 0 }
        return Averages(
            days = r.size,
            activeMin = r.map { it.activeMs }.average() / 60_000.0,
            checks = r.map { it.checks }.average(),
            longestSessionMin = r.map { it.longestSessionMs }.average() / 60_000.0,
            youtubeMin = if (yt.isEmpty()) null else yt.map { it.youtubeMs }.average() / 60_000.0,
        )
    }

    /** Passt die Limits an. Gibt eine Zeile pro Änderung zurück. */
    fun adjust(p: Prefs, epochDay: Long): List<String> {
        if (!p.adaptiveEnabled) return emptyList()
        val a = averages(p) ?: return emptyList()
        if (a.days < MIN_DAYS) return emptyList()
        val factor = 1.0 - p.adaptiveReductionPct.coerceIn(0, 50) / 100.0
        val changes = ArrayList<String>()

        fun tighten(name: String, unit: String, old: Int, avg: Double, floorValue: Int, set: (Int) -> Unit) {
            if (old <= floorValue) return
            val target = floor(avg * factor).toInt()
            if (target >= old) return
            val new = max(max(target, floor(old * (1 - MAX_DROP_PER_DAY)).toInt()), floorValue)
            if (new >= old) return
            set(new)
            changes += "$name: $old → $new $unit (dein Schnitt: ${avg.roundToInt()})"
        }

        tighten("Tageslimit", "min", p.dailyLimitMin, a.activeMin, FLOOR_DAILY_MIN) { p.dailyLimitMin = it }
        tighten("Session-Limit", "min", p.sessionLimitMin, a.longestSessionMin, FLOOR_SESSION_MIN) {
            p.sessionLimitMin = it
        }
        if (p.youtubeLimitMin > 0 && a.youtubeMin != null) {
            tighten("YouTube-Limit", "min", p.youtubeLimitMin, a.youtubeMin, FLOOR_YOUTUBE_MIN) {
                p.youtubeLimitMin = it
            }
        }
        if (p.checkBudget == 0) {
            if (a.checks >= 1) {
                val start = max(FLOOR_CHECKS, floor(a.checks * factor).toInt())
                p.checkBudget = start
                changes += "Check-Budget: neu $start pro Tag (dein Schnitt: ${a.checks.roundToInt()})"
            }
        } else {
            tighten("Check-Budget", "pro Tag", p.checkBudget, a.checks, FLOOR_CHECKS) { p.checkBudget = it }
        }

        if (p.adaptiveLastDropDay == 0L) p.adaptiveLastDropDay = epochDay
        if (changes.isEmpty() && epochDay - p.adaptiveLastDropDay >= FORCED_AFTER_DAYS) {
            fun force(name: String, unit: String, old: Int, floorValue: Int, set: (Int) -> Unit) {
                if (old <= floorValue) return
                val new = max(floorValue, minOf(old - 1, floor(old * (1 - FORCED_DROP)).toInt()))
                set(new)
                changes += "$name: $old → $new $unit (Wochen-Dreh, weil sich nichts bewegt hat)"
            }
            force("Tageslimit", "min", p.dailyLimitMin, FLOOR_DAILY_MIN) { p.dailyLimitMin = it }
            force("Session-Limit", "min", p.sessionLimitMin, FLOOR_SESSION_MIN) { p.sessionLimitMin = it }
            if (p.youtubeLimitMin > 0) {
                force("YouTube-Limit", "min", p.youtubeLimitMin, FLOOR_YOUTUBE_MIN) { p.youtubeLimitMin = it }
            }
            if (p.checkBudget > 0) {
                force("Check-Budget", "pro Tag", p.checkBudget, FLOOR_CHECKS) { p.checkBudget = it }
            }
        }
        if (changes.isNotEmpty()) p.adaptiveLastDropDay = epochDay
        return changes
    }
}

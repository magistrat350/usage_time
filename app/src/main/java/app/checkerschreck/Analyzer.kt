package app.checkerschreck

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar
import kotlin.math.roundToInt

data class DayStats(
    val checks: Int,
    val shortChecks: Int,
    val screenMs: Long,
    val avgGapMs: Long,
    val longestBreakMs: Long,
    val longestSessionMs: Long,
    val perHour: IntArray,
    val checksLastHour: Int,
    val lastCheckAt: Long,
)

data class WeekAvg(val days: Int, val checks: Double, val screenMs: Long)

/** Wertet das Check-Protokoll aus. */
object Analyzer {
    private const val HOUR = 3600_000L
    private const val DAY = 24 * HOUR

    fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayKey(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun hourOf(ts: Long) = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)

    fun today(ctx: Context, prefs: Prefs, now: Long): DayStats {
        val dayStart = startOfDay(now)
        val data = UsageLog.read(ctx, dayStart)
        val sessions = data.sessions.filter { it.start >= dayStart }.toMutableList()
        // laufende Session dazurechnen
        if (prefs.unlockedAt in dayStart..now) {
            sessions += Session(prefs.unlockedAt, now, prefs.screenActiveMs)
        }
        return stats(data.checks.filter { it >= dayStart }, sessions, prefs, now)
    }

    private fun stats(checks: List<Long>, sessions: List<Session>, prefs: Prefs, now: Long): DayStats {
        val perHour = IntArray(24)
        checks.forEach { perHour[hourOf(it)]++ }
        val shortLimit = prefs.shortCheckSec * 1000L
        val finished = sessions.filter { it.end < now }
        val shortChecks = finished.count { it.durationMs < shortLimit }
        val screenMs = sessions.sumOf { it.durationMs }
        val avgGap = if (checks.size >= 2) (checks.last() - checks.first()) / (checks.size - 1) else 0L
        var longestBreak = 0L
        for (i in 1 until sessions.size) {
            longestBreak = maxOf(longestBreak, sessions[i].start - sessions[i - 1].end)
        }
        val longestSession = sessions.maxOfOrNull { it.durationMs } ?: 0L
        val lastHour = checks.count { it >= now - HOUR }
        return DayStats(
            checks.size, shortChecks, screenMs, avgGap, longestBreak, longestSession,
            perHour, lastHour, checks.lastOrNull() ?: 0L,
        )
    }

    /** Durchschnitt der letzten 7 Tage (nur Tage, an denen überhaupt gemessen wurde). */
    fun weekAverage(ctx: Context, now: Long): WeekAvg {
        val todayStart = startOfDay(now)
        val from = todayStart - 7 * DAY
        val data = UsageLog.read(ctx, from)
        var days = 0
        var checks = 0
        var screen = 0L
        for (d in 1..7) {
            val s = todayStart - d * DAY
            val e = s + DAY
            val c = data.checks.count { it in s until e }
            val sc = data.sessions.filter { it.start in s until e }.sumOf { it.durationMs }
            if (c > 0 || sc > 0) {
                days++; checks += c; screen += sc
            }
        }
        if (days == 0) return WeekAvg(0, 0.0, 0)
        return WeekAvg(days, checks.toDouble() / days, screen / days)
    }

    fun verdict(s: DayStats): String = when {
        s.checks < 15 -> "🧘 Zen-Meister – kaum Checks heute"
        s.checks < 35 -> "🙂 Gelegenheits-Checker"
        s.checks < 60 -> "😬 Routinierter Checker"
        s.checks < 100 -> "🫠 Notorischer Checker"
        else -> "🐟 Goldfisch mit WLAN – ${s.checks} Checks"
    }

    fun insights(s: DayStats, week: WeekAvg, prefs: Prefs): List<String> {
        val out = ArrayList<String>()
        if (s.checks == 0) {
            out += "Heute noch kein einziger Check. Respekt (oder die Überwachung läuft noch nicht)."
            return out
        }
        if (s.checks >= 2) {
            out += "Du checkst im Schnitt alle ${fmtDuration(s.avgGapMs)}."
        }
        val finished = s.checks.coerceAtLeast(1)
        val shortPct = (s.shortChecks * 100.0 / finished).roundToInt()
        if (s.shortChecks > 0) {
            out += "$shortPct % deiner Checks waren kürzer als ${prefs.shortCheckSec} s – reine Reflex-Checks."
        }
        val peak = s.perHour.indices.maxByOrNull { s.perHour[it] } ?: 0
        if (s.perHour[peak] >= 3) {
            out += "Check-Hotspot: $peak–${peak + 1} Uhr mit ${s.perHour[peak]} Checks."
        }
        if (s.checksLastHour >= prefs.burstCount) {
            out += "⚠️ Letzte Stunde: ${s.checksLastHour} Checks. Das Handy läuft nicht weg."
        }
        if (s.longestBreakMs > 0) {
            out += "Längste handyfreie Zeit heute: ${fmtDuration(s.longestBreakMs)}."
        }
        if (s.longestSessionMs > prefs.sessionLimitMin * 60_000L) {
            out += "Längste Session: ${fmtDuration(s.longestSessionMs)} (Limit: ${prefs.sessionLimitMin} min)."
        }
        if (week.days > 0 && week.checks > 0) {
            val diff = ((s.checks - week.checks) / week.checks * 100).roundToInt()
            out += if (diff > 0) {
                "Bisher schon $diff % mehr Checks als dein ${week.days}-Tage-Schnitt (${week.checks.roundToInt()})."
            } else {
                "Bisher ${-diff} % unter deinem ${week.days}-Tage-Schnitt (${week.checks.roundToInt()} Checks/Tag)."
            }
        }
        return out
    }

    /** Meistgenutzte Apps seit [since] aus den Android-Nutzungsereignissen. */
    @Suppress("DEPRECATION")
    fun topApps(ctx: Context, since: Long, now: Long, limit: Int = 5): List<Pair<String, Long>> {
        if (!Permissions.hasUsageAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val pm = ctx.packageManager
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val events = usm.queryEvents(since, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> resumedAt[e.packageName] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> resumedAt.remove(e.packageName)?.let {
                    totals[e.packageName] = (totals[e.packageName] ?: 0L) + (e.timeStamp - it)
                }
            }
        }
        resumedAt.forEach { (p, t) -> totals[p] = (totals[p] ?: 0L) + (now - t) }
        return totals.filterKeys { it != home && it != ctx.packageName }
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { appLabel(ctx, it.key) to it.value }
    }

    fun appLabel(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

fun fmtDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "$h h ${m} min"
        m > 0 -> "$m min"
        else -> "$s s"
    }
}

fun fmtClock(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

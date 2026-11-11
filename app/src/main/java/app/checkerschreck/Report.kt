package app.checkerschreck

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.util.Calendar
import kotlin.math.roundToInt

/** Wochenbericht: letzte 7 abgeschlossene Tage im Vergleich zur Woche davor. */
object Report {
    private const val NOTIF_ID = 4
    private const val REPORT_HOUR = 18

    private fun change(now: Double, before: Double): String {
        if (before <= 0) return ""
        val pct = ((now - before) / before * 100).roundToInt()
        return when {
            pct < 0 -> " (${-pct} % weniger als in der Woche davor 👍)"
            pct > 0 -> " ($pct % mehr als in der Woche davor 👎)"
            else -> " (wie in der Woche davor)"
        }
    }

    private fun weekday(day: String): String {
        val f = day.split('-').mapNotNull { it.toIntOrNull() }
        if (f.size != 3) return day
        val c = Calendar.getInstance().apply { set(f[0], f[1] - 1, f[2]) }
        return listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")[c.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun build(ctx: Context, p: Prefs, now: Long): String {
        val all = History.all(p)
        val week = all.takeLast(7)
        val prev = all.dropLast(7).takeLast(7)
        if (week.isEmpty()) {
            return "Noch keine abgeschlossenen Messtage. Der erste Bericht kommt, sobald mindestens ein voller Tag gemessen wurde."
        }
        val out = ArrayList<String>()
        out += "Zeitraum: ${week.first().day} bis ${week.last().day} (${week.size} Tage)"

        val checks = week.map { it.checks }.average()
        out += "🔓 Checks: Ø ${checks.roundToInt()} pro Tag" +
            if (prev.isEmpty()) "" else change(checks, prev.map { it.checks }.average())

        val active = week.map { it.activeMs }.average()
        out += "⏱ Aktive Zeit: Ø ${fmtDuration(active.toLong())} pro Tag" +
            if (prev.isEmpty()) "" else change(active, prev.map { it.activeMs }.average())

        val longest = week.maxOf { it.longestSessionMs }
        out += "📏 Längste Session: ${fmtDuration(longest)}"

        val ytDays = week.filter { it.youtubeMs > 0 }
        if (ytDays.isNotEmpty()) {
            out += "📺 YouTube: ${fmtDuration(ytDays.sumOf { it.youtubeMs })} an ${ytDays.size} Tagen"
        }
        val scroll = week.sumOf { it.scrollMs }
        if (scroll > 0) out += "📜 Gescrollt: Ø ${fmtDuration(scroll / week.size)} pro Tag"

        val best = week.minByOrNull { it.checks }
        val worst = week.maxByOrNull { it.checks }
        if (best != null && worst != null && best != worst) {
            out += "🏆 Bester Tag: ${weekday(best.day)} (${best.checks} Checks) · schlechtester: " +
                "${weekday(worst.day)} (${worst.checks} Checks)"
        }

        val t = Analyzer.triggers(ctx, now - 7 * 24 * 3600_000L)
        if (t.known > 0) {
            out += "🔔 Auslöser: ${t.spontaneous * 100 / t.known} % ohne Anlass" +
                t.byApp.take(3).joinToString("") { ", ${it.checks * 100 / t.known} % ${it.label}" }
        }

        out += "🎚 Limits jetzt: Tag ${p.dailyLimitMin} min · Session ${p.sessionLimitMin} min" +
            (if (p.checkBudget > 0) " · ${p.checkBudget} Checks" else "") +
            (if (p.youtubeLimitMin > 0) " · YouTube ${p.youtubeLimitMin} min" else "")
        return out.joinToString("\n\n")
    }

    /** Sonntagabend einmal benachrichtigen. */
    fun maybeNotify(ctx: Context, p: Prefs, now: Long) {
        if (!p.weeklyReport) return
        val c = Calendar.getInstance().apply { timeInMillis = now }
        if (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || c.get(Calendar.HOUR_OF_DAY) < REPORT_HOUR) return
        val day = Analyzer.dayKey(now)
        if (p.lastReportDay == day) return
        p.lastReportDay = day
        if (History.all(p).isEmpty() || !Permissions.hasNotifications(ctx)) return
        val text = build(ctx, p, now)
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, ReportActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, App.CH_ALERT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("📊 Dein Wochenbericht")
            .setContentText(text.lines().getOrElse(2) { "Tippen zum Ansehen" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
}

class ReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, col) = screen()
        col.text("📊 Wochenbericht", 28f, bold = true)
        val card = col.card()
        card.text(Report.build(this, Prefs(this), System.currentTimeMillis()), 15f)
        col.text(
            "Der Bericht kommt jeden Sonntag ab 18 Uhr als Benachrichtigung und lässt sich hier jederzeit ansehen.",
            12f, topDp = 12, alpha = 0.7f,
        )
        setContentView(scroll)
    }
}

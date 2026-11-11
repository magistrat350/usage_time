package app.checkerschreck

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Merkt sich, welche App wann eine Benachrichtigung geschickt hat (nur Paketname und Zeitpunkt,
 * nie den Inhalt). Damit lässt sich sagen, welche Benachrichtigung einen Check ausgelöst hat.
 */
object NotificationTriggers {
    /** Ein Check gilt als ausgelöst, wenn so kurz davor eine Benachrichtigung kam. */
    const val WINDOW_MS = 90_000L
    private const val PREFS = "notif_counts"
    private const val KEEP_DAYS = 9L

    private val recent = ArrayDeque<Pair<Long, String>>()

    @Volatile var connected = false

    @Synchronized
    fun onPosted(ctx: Context, pkg: String, now: Long) {
        recent.addLast(now to pkg)
        while (recent.size > 60) recent.removeFirst()
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = Analyzer.dayKey(now)
        val key = "$day|$pkg"
        val e = sp.edit().putInt(key, sp.getInt(key, 0) + 1)
        if (sp.getString("_day", "") != day) {
            val cutoff = Analyzer.dayKey(now - KEEP_DAYS * 24 * 3600_000L)
            sp.all.keys.filter { it.contains('|') && it.substringBefore('|') < cutoff }.forEach { e.remove(it) }
            e.putString("_day", day)
        }
        e.apply()
    }

    /** Paketname der letzten Benachrichtigung kurz vor [checkTs], sonst null. */
    @Synchronized
    fun triggerFor(checkTs: Long): String? =
        recent.lastOrNull { it.first <= checkTs && checkTs - it.first <= WINDOW_MS }?.second

    /** Anzahl Benachrichtigungen pro App seit dem Tag von [since]. */
    fun counts(ctx: Context, since: Long): Map<String, Int> {
        val from = Analyzer.dayKey(since)
        val out = HashMap<String, Int>()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (k.contains('|') && k.substringBefore('|') >= from && v is Int) {
                val pkg = k.substringAfter('|')
                out[pkg] = (out[pkg] ?: 0) + v
            }
        }
        return out
    }
}

class NotificationWatcher : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationTriggers.connected = true
        Watchdog.ensureRunning(this)
    }

    override fun onListenerDisconnected() {
        NotificationTriggers.connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        Watchdog.ensureRunning(this)
        if (sbn == null || sbn.packageName == packageName || sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // Nur Benachrichtigungen, die sich bemerkbar machen (nicht stumm, nicht von "Nicht stören" geschluckt)
        val r = Ranking()
        if (rankingMap != null && rankingMap.getRanking(sbn.key, r)) {
            if (r.importance < NotificationManager.IMPORTANCE_DEFAULT) return
            if (!r.matchesInterruptionFilter()) return
        }
        NotificationTriggers.onPosted(this, sbn.packageName, System.currentTimeMillis())
    }
}

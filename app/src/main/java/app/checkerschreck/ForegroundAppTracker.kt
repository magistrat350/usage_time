package app.checkerschreck

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Ermittelt die App im Vordergrund aus den Nutzungsereignissen. */
class ForegroundAppTracker(ctx: Context) {
    private val usm = ctx.getSystemService(UsageStatsManager::class.java)
    private var lastQuery = System.currentTimeMillis() - 3 * 3600_000L
    var current: String? = null
        private set

    @Suppress("DEPRECATION")
    fun update(now: Long): String? {
        val u = usm ?: return null
        runCatching {
            val events = u.queryEvents(lastQuery, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) current = e.packageName
            }
            lastQuery = now - 2000
        }
        return current
    }
}

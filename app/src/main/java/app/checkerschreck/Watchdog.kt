package app.checkerschreck

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Startet die Überwachung neu, wenn Android sie beendet hat. Angestoßen wird das von einem
 * Wecker alle 15 Minuten, von der Bedienungshilfe und vom Benachrichtigungszugriff (beide hält
 * Android selbst am Leben) und beim Wegwischen der App aus der Übersicht.
 */
object Watchdog {
    @Volatile private var lastTry = 0L

    fun ensureRunning(ctx: Context) {
        if (MonitorService.running) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTry < 30_000) return
        lastTry = now
        val p = Prefs(ctx)
        if (!p.monitoringEnabled) return
        if (!Permissions.hasUsageAccess(ctx) || !Permissions.canDrawOverlays(ctx)) return
        runCatching { MonitorService.start(ctx.applicationContext) }
    }

    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 10, Intent(ctx, WatchdogReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                intent(ctx),
            )
        }
    }

    /** In wenigen Sekunden nachsehen (z. B. nachdem die App weggewischt wurde). */
    fun kickSoon(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 11, Intent(ctx, WatchdogReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 3_000, pi) }
    }

    fun cancel(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java)?.cancel(intent(ctx)) }
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Watchdog.ensureRunning(context)
    }
}

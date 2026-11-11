package app.checkerschreck

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Läuft dauerhaft im Vordergrund, zählt Checks, misst Sessions und macht das Handy hässlich.
 */
class MonitorService : Service() {
    data class Snapshot(
        val level: Int,
        val foreground: String?,
        val exempt: String?,
        val sessionMs: Long,
        val dailyMs: Long,
        val penaltyUntil: Long,
        val youtubeMs: Long,
        val updatedAt: Long,
    )

    private lateinit var prefs: Prefs
    private lateinit var tracker: ForegroundAppTracker
    private lateinit var overlay: UglyOverlay
    private lateinit var pm: PowerManager
    private lateinit var km: KeyguardManager
    private val handler = Handler(Looper.getMainLooper())

    // Zähler im Speicher, werden regelmäßig gesichert
    private var sessionActiveMs = 0L
    private var dailyActiveMs = 0L
    private var screenActiveMs = 0L
    private var youtubeMs = 0L
    private var lastFg: String? = null
    private var lastKick = 0L

    private var ticking = false
    private var lastTick = 0L
    private var lastPersist = 0L
    private var lastNotif = 0L
    private var lastVibrate = 0L
    private var lastA11yScan = 0L
    private var lastInsultSwap = 0L
    private var insultIdx = 0
    private var level = 0
    private var exempt: String? = null
    private var limitNotifiedSession = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_USER_PRESENT -> registerCheck(System.currentTimeMillis())
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            if (ticking) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        tracker = ForegroundAppTracker(this)
        overlay = UglyOverlay(this)
        pm = getSystemService(PowerManager::class.java)
        km = getSystemService(KeyguardManager::class.java)
        val now = System.currentTimeMillis()
        rolloverIfNeeded(prefs, now)
        sessionActiveMs = prefs.sessionActiveMs
        dailyActiveMs = prefs.dailyActiveMs
        screenActiveMs = prefs.screenActiveMs
        youtubeMs = prefs.youtubeMsToday
        UsageLog.prune(this, now)
        // Aufräumen, falls der Dienst mitten in einer Session beendet wurde
        if (prefs.unlockedAt > 0 &&
            (!pm.isInteractive || km.isKeyguardLocked || now - prefs.unlockedAt > 3 * 3600_000L)
        ) {
            prefs.unlockedAt = 0L
            screenActiveMs = 0L
        }
        goForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        running = true
        if (pm.isInteractive) {
            if (!km.isKeyguardLocked && prefs.unlockedAt == 0L) registerCheck(now)
            startTicking()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.monitoringEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_COOKING -> {
                CookingMode.start(this)
                tick()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticking = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
        overlay.hide()
        SystemColorHack.restore(this, prefs)
        persist()
        running = false
        snapshot = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Bildschirm-Ereignisse

    private fun onScreenOn() {
        // Ohne Sperrbildschirm kommt kein USER_PRESENT, dann zählt schon das Einschalten.
        if (!km.isKeyguardLocked) registerCheck(System.currentTimeMillis())
        startTicking()
    }

    private fun onScreenOff() {
        val now = System.currentTimeMillis()
        tickAccounting(now) // letzte Sekunden noch mitzählen
        if (prefs.unlockedAt > 0) {
            UsageLog.addSession(this, Session(prefs.unlockedAt, now, screenActiveMs))
        }
        prefs.unlockedAt = 0L
        screenActiveMs = 0L
        prefs.lastScreenOff = now
        ticking = false
        handler.removeCallbacks(tickRunnable)
        overlay.hide()
        SystemColorHack.restore(this, prefs)
        level = 0
        persist()
        updateNotification(force = true)
    }

    private fun registerCheck(now: Long) {
        if (now - prefs.lastCheckAt < 3000) return
        if (rolloverIfNeeded(prefs, now)) resetDaily()
        // Session nur zurücksetzen, wenn der Bildschirm lange genug aus war (kein Schummeln
        // durch kurzes Aus- und Einschalten).
        val off = prefs.lastScreenOff
        if (off == 0L || now - off > prefs.resetPauseSec * 1000L) {
            sessionActiveMs = 0L
            prefs.sessionStart = now
        }
        if (prefs.unlockedAt == 0L) {
            prefs.unlockedAt = now
            screenActiveMs = 0L
        }
        val previous = prefs.lastCheckAt
        prefs.lastCheckAt = now
        prefs.checksToday = prefs.checksToday + 1
        UsageLog.addCheck(this, now)

        // Check-Attacke erkennen
        val recent = UsageLog.checksSince(this, now - prefs.burstWindowMin * 60_000L)
        if (recent >= prefs.burstCount) {
            val newLevel = if (recent >= prefs.burstCount * 2) 2 else 1
            val wasActive = prefs.penaltyUntil > now
            prefs.penaltyUntil = now + prefs.burstPenaltyMin * 60_000L
            prefs.penaltyLevel = maxOf(newLevel, if (wasActive) prefs.penaltyLevel else 0)
            if (!wasActive || newLevel > 1) {
                alert(
                    "Check-Attacke! 🚨",
                    "$recent Checks in ${prefs.burstWindowMin} min. Strafe: ${prefs.burstPenaltyMin} min hässlich.",
                )
            }
        }

        if (prefs.unlockNudge) {
            val since = if (previous > 0 && Analyzer.dayKey(previous) == Analyzer.dayKey(now)) {
                " · letzter vor ${fmtDuration(now - previous)}"
            } else ""
            Toast.makeText(this, "Check #${prefs.checksToday} heute$since", Toast.LENGTH_SHORT).show()
        }
        updateNotification(force = true)
    }

    // ------------------------------------------------------------------ Takt

    private fun startTicking() {
        if (ticking) return
        ticking = true
        lastTick = System.currentTimeMillis()
        handler.post(tickRunnable)
    }

    /** Rechnet die seit dem letzten Takt vergangene Zeit an. Gibt true zurück, wenn gezählt wurde. */
    private fun tickAccounting(now: Long): Boolean {
        val dt = (now - lastTick).coerceIn(0, 5_000)
        lastTick = now
        if (!pm.isInteractive || km.isKeyguardLocked || prefs.unlockedAt == 0L) return false
        if (exempt != null) return false
        sessionActiveMs += dt
        dailyActiveMs += dt
        screenActiveMs += dt
        if (YouTubeLimit.isYouTube(lastFg)) youtubeMs += dt
        return true
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (rolloverIfNeeded(prefs, now)) resetDaily()
        tickAccounting(now)

        val usable = pm.isInteractive && !km.isKeyguardLocked
        if (!usable) {
            overlay.hide()
            SystemColorHack.restore(this, prefs)
            level = 0
            publishSnapshot(now, null)
            return
        }

        val fg = if (Permissions.hasUsageAccess(this)) tracker.update(now) else null
        lastFg = fg
        if (now - lastA11yScan > 8_000) {
            lastA11yScan = now
            RecipeAccessibilityService.instance?.scanNow()
        }
        exempt = RecipeDetector.exemptReason(this, prefs, fg, now)

        val penalty = if (prefs.penaltyUntil > now) prefs.penaltyLevel else 0
        val onYouTube = exempt == null && YouTubeLimit.isYouTube(fg)
        val ytLevel = if (onYouTube) YouTubeLimit.level(prefs, youtubeMs) else 0
        val newLevel = if (exempt != null) 0 else {
            maxOf(Escalation.level(prefs, sessionActiveMs, dailyActiveMs, penalty), ytLevel)
                .coerceAtMost(maxOf(prefs.maxLevel.coerceIn(1, 3), minOf(ytLevel, 3)))
        }
        val levelChanged = newLevel != level
        level = newLevel
        if (level > prefs.maxLevelToday) prefs.maxLevelToday = level

        if (now - lastInsultSwap > 6_000) {
            lastInsultSwap = now
            insultIdx = (insultIdx + 1) % UglyView.INSULTS.size
        }
        overlay.show(level, overlayLines(now, penalty))
        SystemColorHack.apply(
            this, prefs,
            gray = prefs.useGrayscale && level >= 2,
            invert = prefs.useInversion && level >= 3,
        )

        if (level >= 3 && prefs.vibrate && now - lastVibrate > 45_000) {
            lastVibrate = now
            nag()
        }
        if (level >= 1 && limitNotifiedSession != prefs.sessionStart && penalty == 0 &&
            sessionActiveMs >= prefs.sessionLimitMin * 60_000L
        ) {
            limitNotifiedSession = prefs.sessionStart
            val msg = if (prefs.alwaysUgly) "Ab jetzt wird's noch hässlicher." else "Ab jetzt wird's hässlich."
            alert("Limit erreicht", "${prefs.sessionLimitMin} min am Stück. $msg 🤢")
        }

        if (onYouTube) checkYouTube(now)

        publishSnapshot(now, fg)
        if (now - lastPersist > 30_000) persist()
        updateNotification(force = levelChanged)
    }

    private fun checkYouTube(now: Long) {
        if (!YouTubeLimit.enabled(prefs)) return
        val limit = YouTubeLimit.limitMs(prefs)
        if (prefs.youtubeWarned < 1 && youtubeMs >= limit - 5 * 60_000L && youtubeMs < limit) {
            prefs.youtubeWarned = 1
            alert("Noch 5 min YouTube ⏳", "Du hast heute schon ${fmtDuration(youtubeMs)} von ${prefs.youtubeLimitMin} min verbraucht.")
        }
        if (youtubeMs < limit) return
        if (prefs.youtubeWarned < 2) {
            prefs.youtubeWarned = 2
            alert("YouTube-Limit erreicht 📺🚫", "${prefs.youtubeLimitMin} min YouTube für heute sind aufgebraucht.")
        }
        if (prefs.youtubeKick && now - lastKick > 4_000) {
            lastKick = now
            persist()
            Toast.makeText(this, "YouTube-Limit (${prefs.youtubeLimitMin} min) erreicht. Tschüss! 👋", Toast.LENGTH_SHORT).show()
            YouTubeLimit.kick(this)
        }
    }

    private fun resetDaily() {
        dailyActiveMs = 0L
        youtubeMs = 0L
    }

    private fun overlayLines(now: Long, penalty: Int): List<String> {
        val l = ArrayList<String>()
        if (YouTubeLimit.isYouTube(lastFg) && YouTubeLimit.isOver(prefs, youtubeMs)) {
            l += "YouTube-Limit überschritten!"
            l += "YouTube heute: ${fmtDuration(youtubeMs)} / ${prefs.youtubeLimitMin} min"
            l += "Session: ${fmtClock(sessionActiveMs)}"
            return l
        }
        if (level == 1 && penalty == 0) {
            // Grundstufe: kurz und oben, damit man noch lesen kann
            val next = Escalation.minutesToNextLevel(prefs, sessionActiveMs)
            l += "Session ${fmtClock(sessionActiveMs)} · Stufe 2 in $next min"
            l += "${prefs.checksToday} Checks heute"
            return l
        }
        l += UglyView.INSULTS[insultIdx]
        l += "Session: ${fmtClock(sessionActiveMs)}"
        l += "Heute: ${fmtDuration(dailyActiveMs)} · ${prefs.checksToday} Checks"
        if (penalty > 0) l += "Check-Strafe noch ${fmtDuration(prefs.penaltyUntil - now)}"
        return l
    }

    private fun publishSnapshot(now: Long, fg: String?) {
        snapshot = Snapshot(level, fg, exempt, sessionActiveMs, dailyActiveMs, prefs.penaltyUntil, youtubeMs, now)
    }

    private fun persist() {
        lastPersist = System.currentTimeMillis()
        prefs.sessionActiveMs = sessionActiveMs
        prefs.dailyActiveMs = dailyActiveMs
        prefs.screenActiveMs = screenActiveMs
        prefs.youtubeMsToday = youtubeMs
    }

    // ------------------------------------------------------------------ Benachrichtigungen

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotif < 10_000) return
        lastNotif = now
        if (!Permissions.hasNotifications(this)) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cook = PendingIntent.getForegroundService(
            this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_COOKING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when {
            exempt != null -> "Pause: $exempt"
            level > 0 -> "Hässlich-Stufe $level 🤢"
            else -> "Checker-Schreck passt auf 👀"
        }
        val text = "Heute ${prefs.checksToday} Checks · ${fmtDuration(dailyActiveMs)} · Session ${fmtClock(sessionActiveMs)}"
        return NotificationCompat.Builder(this, App.CH_MONITOR)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "🍳 Kochmodus", cook)
            .build()
    }

    private fun alert(title: String, text: String) {
        if (!Permissions.hasNotifications(this)) return
        val n = NotificationCompat.Builder(this, App.CH_ALERT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
    }

    @Suppress("DEPRECATION")
    private fun nag() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 400), -1)) }
    }

    companion object {
        private const val TICK_MS = 2_000L
        private const val NOTIF_ID = 1
        private const val ALERT_ID = 2
        const val ACTION_STOP = "app.checkerschreck.STOP"
        const val ACTION_COOKING = "app.checkerschreck.COOKING"

        @Volatile var running = false
            private set

        @Volatile var snapshot: Snapshot? = null
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs(ctx).monitoringEnabled = false
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }

        /** Neuer Tag? Dann Tageszähler zurücksetzen. Gibt true zurück, wenn zurückgesetzt wurde. */
        fun rolloverIfNeeded(p: Prefs, now: Long): Boolean {
            val key = Analyzer.dayKey(now)
            if (p.dayKey == key) return false
            p.dayKey = key
            p.dailyActiveMs = 0L
            p.checksToday = 0
            p.youtubeMsToday = 0L
            p.youtubeWarned = 0
            p.cookingUsed = 0
            p.maxLevelToday = 0
            return true
        }
    }
}

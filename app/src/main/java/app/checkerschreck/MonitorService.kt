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
import android.media.AudioManager
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
        val night: Boolean,
        val dayStrict: Boolean,
        val doomscroll: Boolean,
        val morningBlockUntil: Long,
        val reason: String,
        val error: String,
        val updatedAt: Long,
    )

    private lateinit var prefs: Prefs
    private lateinit var tracker: ForegroundAppTracker
    private lateinit var overlay: UglyOverlay
    private lateinit var shrink: ShrinkBars
    private lateinit var blocker: BlockerOverlay
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
    private var memDay = ""
    private var scrollMs = 0L
    private var night = false
    private var dayStrict = false
    private var doom = false
    private var blocked = false
    private var lastMinuteJobs = 0L
    private var lastUsable = false
    private var lastPoke = 0L
    private var lastError = ""
    private var audio: AudioManager? = null
    private lateinit var stickers: UglyStickers

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
                Intent.ACTION_USER_PRESENT -> onScreenOn()
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
        shrink = ShrinkBars(this)
        blocker = BlockerOverlay(this)
        stickers = UglyStickers(this)
        audio = getSystemService(AudioManager::class.java)
        pm = getSystemService(PowerManager::class.java)
        km = getSystemService(KeyguardManager::class.java)
        val now = System.currentTimeMillis()
        rolloverIfNeeded(prefs, now)
        memDay = Analyzer.dayKey(now)
        sessionActiveMs = prefs.sessionActiveMs
        dailyActiveMs = prefs.dailyActiveMs
        screenActiveMs = prefs.screenActiveMs
        youtubeMs = prefs.youtubeMsToday
        scrollMs = prefs.scrollMsToday
        backcharge(now)
        prefs.serviceAlive = true
        prefs.lastAliveAt = now
        prefs.serviceStartsToday = prefs.serviceStartsToday + 1
        instance = this
        Watchdog.schedule(this)
        if (prefs.morningBlockDay.isEmpty()) prefs.morningBlockDay = Analyzer.dayKey(now)
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
        announceAdaptive()
        if (pm.isInteractive) startTicking()
    }

    /** App aus der Übersicht gewischt: Manche Hersteller beenden dann den Dienst. Gleich wieder starten. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Watchdog.kickSoon(this)
        super.onTaskRemoved(rootIntent)
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
        hideWindows()
        restoreSystem(keepFont = false)
        persist()
        prefs.serviceAlive = false
        running = false
        instance = null
        snapshot = null
        WidgetProvider.render(this)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Bildschirm-Ereignisse

    private fun onScreenOn() {
        startTicking()
        tickNow()
    }

    private fun onScreenOff() {
        val now = System.currentTimeMillis()
        tickAccounting(now) // letzte Sekunden noch mitzählen
        lastUsable = false
        if (prefs.unlockedAt > 0) {
            UsageLog.addSession(this, Session(prefs.unlockedAt, now, screenActiveMs))
        }
        prefs.unlockedAt = 0L
        screenActiveMs = 0L
        prefs.lastScreenOff = now
        ticking = false
        handler.removeCallbacks(tickRunnable)
        // Fenster weg (spart Akku). Die Schrift bleibt, damit nicht bei jedem Sperren alle Apps neu laden.
        hideWindows()
        restoreSystem(keepFont = true)
        level = 0
        persist()
        updateNotification(force = true)
    }

    private fun inCall(): Boolean {
        val mode = audio?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    /**
     * Das Handy wurde entsperrt. Wird aus dem Takt aufgerufen, sobald der Bildschirm an und
     * entsperrt ist und für diese Bildschirm-an-Phase noch kein Entsperren vermerkt wurde.
     * So hängt nichts davon ab, ob Android das Entsperr-Signal zustellt.
     */
    private fun registerCheck(now: Long) {
        rollover(now)
        // Session nur zurücksetzen, wenn der Bildschirm lange genug aus war (kein Schummeln
        // durch kurzes Aus- und Einschalten).
        val off = prefs.lastScreenOff
        if (off == 0L || now - off > prefs.resetPauseSec * 1000L) {
            sessionActiveMs = 0L
            prefs.sessionStart = now
        }
        prefs.unlockedAt = now
        screenActiveMs = 0L

        // Kein Check: Bildschirm ging nur ganz kurz aus, oder der Annäherungssensor beim Telefonieren
        if (now - prefs.lastCheckAt < 3000 || inCall()) return

        val previous = prefs.lastCheckAt
        prefs.lastCheckAt = now
        prefs.checksToday = prefs.checksToday + 1
        // Auslöser: Kam kurz vorher eine Benachrichtigung? (nur wenn der Zugriff aktiv ist)
        val trigger = if (NotificationTriggers.connected) NotificationTriggers.triggerFor(now) ?: "-" else null
        UsageLog.addCheck(this, now, trigger)
        if (Schedule.maybeStartMorningBlock(prefs, now)) {
            alert("☀️ Morgensperre", "Das Handy ist die nächsten ${prefs.morningBlockMin} min gesperrt.")
        }
        val budget = prefs.checkBudget
        if (budget > 0 && prefs.checksToday == budget + 1) {
            alert("Check-Budget aufgebraucht 🧮", "$budget Checks waren für heute drin. Ab jetzt eine Stufe hässlicher.")
        }

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
            val of = if (budget > 0) " von $budget" else ""
            val why = when (trigger) {
                null -> ""
                "-" -> " · ohne Anlass"
                else -> " · Auslöser: ${Analyzer.appLabel(this, trigger)}"
            }
            Toast.makeText(this, "Check #${prefs.checksToday}$of heute$since$why", Toast.LENGTH_SHORT).show()
        }
        lastNotif = 0L
    }

    // ------------------------------------------------------------------ Takt

    private fun startTicking() {
        if (ticking) return
        ticking = true
        lastTick = System.currentTimeMillis()
        handler.post(tickRunnable)
    }

    /** Sofort einen Takt ausführen (nach Entsperren, App-Wechsel ...), statt auf den nächsten zu warten. */
    private fun tickNow() {
        if (!ticking) return
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun pokeTick() {
        val now = System.currentTimeMillis()
        if (now - lastPoke < 400) return
        lastPoke = now
        handler.postDelayed({ tickNow() }, 150)
    }

    /** Rechnet die seit dem letzten Takt vergangene Zeit an, wenn das Handy da in Benutzung war. */
    private fun tickAccounting(now: Long) {
        val dt = (now - lastTick).coerceIn(0, 20_000)
        lastTick = now
        if (!lastUsable || exempt != null || blocked) return
        // Beim Endlos-Scrollen läuft die Session-Uhr schneller
        sessionActiveMs += dt * (if (doom) prefs.doomscrollFactor.coerceIn(1, 5) else 1)
        if (ScrollMeter.scrolledWithin(now, 3_000)) scrollMs += dt
        dailyActiveMs += dt
        screenActiveMs += dt
        if (YouTubeLimit.isYouTube(lastFg)) youtubeMs += dt
    }

    /** Ein Fehler in einem Takt darf die Überwachung nicht beenden. */
    private fun tick() {
        try {
            tickInner()
        } catch (t: Throwable) {
            lastError = "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun tickInner() {
        val now = System.currentTimeMillis()
        rollover(now)
        tickAccounting(now)
        if (now - lastMinuteJobs > 60_000) minuteJobs(now)

        if (!pm.isInteractive) {
            lastUsable = false
            hideWindows()
            restoreSystem(keepFont = true)
            level = 0
            publishSnapshot(now, null, "Bildschirm aus")
            return
        }
        // Solange der Sperrbildschirm zu sehen ist, wird schon alles so gesetzt, wie es nach dem
        // Entsperren sein muss, damit es ohne Verzögerung da ist. Normale Overlays blendet Android
        // hinter dem Sperrbildschirm aus; über die Bedienungshilfe ist es auch dort schon hässlich.
        val locked = km.isKeyguardLocked
        if (!locked && prefs.unlockedAt == 0L) registerCheck(now)
        lastUsable = !locked

        val fg = if (Permissions.hasUsageAccess(this)) tracker.update(now) else null
        lastFg = fg
        if (!locked && now - lastA11yScan > 8_000) {
            lastA11yScan = now
            RecipeAccessibilityService.instance?.scanNow()
        }
        exempt = if (inCall()) "Telefonat" else RecipeDetector.exemptReason(this, prefs, fg, now)

        val sched = Schedule.state(prefs, now)
        night = sched.night
        dayStrict = sched.dayStrict && !sched.night
        doom = !locked && exempt == null && prefs.doomscrollEnabled && ScrollMeter.isDoomscrolling(now)
        val factor = limitFactor()

        // Morgensperre: Vollbild, außer in Ausnahme-Apps
        blocked = !locked && exempt == null && Schedule.morningBlocked(prefs, now)
        if (blocked) {
            blocker.show("Das Handy ist noch ${fmtDuration(prefs.morningBlockUntil - now + 59_000)} gesperrt.")
        } else {
            blocker.hide()
        }

        // Hinter dem Sperrbildschirm: Wird die Session beim Entsperren zurückgesetzt?
        val off = prefs.lastScreenOff
        val willReset = locked && prefs.unlockedAt == 0L && (off == 0L || now - off > prefs.resetPauseSec * 1000L)
        val session = if (willReset) 0L else sessionActiveMs

        val penalty = if (prefs.penaltyUntil > now) prefs.penaltyLevel else 0
        val checkLevel = Escalation.checkLevel(prefs, prefs.checksToday)
        val onYouTube = exempt == null && YouTubeLimit.isYouTube(fg)
        val ytLevel = if (onYouTube) YouTubeLimit.level(prefs, youtubeMs) else 0
        val newLevel = when {
            exempt != null -> 0
            night -> 3 // Nachtmodus: sofort die höchste Stufe
            else -> maxOf(Escalation.level(prefs, session, dailyActiveMs, penalty, checkLevel, factor), ytLevel)
                .coerceAtMost(maxOf(prefs.maxLevel.coerceIn(1, 3), minOf(ytLevel, 3)))
        }
        val levelChanged = newLevel != level
        level = newLevel
        if (level > prefs.maxLevelToday) prefs.maxLevelToday = level

        // Begründung, warum gerade diese Stufe gilt (für die Diagnose)
        val why = ArrayList<String>()
        if (exempt != null) {
            why += "Ausnahme: $exempt"
        } else {
            if (night) why += "Nachtmodus"
            if (session >= Escalation.sessionLimitMs(prefs, factor)) why += "Session über Limit"
            if (dailyActiveMs >= prefs.dailyLimitMin * 60_000L) why += "Tageslimit überschritten"
            if (penalty > 0) why += "Check-Strafe"
            if (checkLevel > 0) why += "Check-Budget überzogen"
            if (ytLevel > 0) why += "YouTube-Limit"
            if (why.isEmpty()) why += if (prefs.alwaysUgly) "Grundstufe" else "unter allen Limits"
            if (dayStrict) why += "tagsüber strenger"
            if (doom) why += "Endlos-Scrollen"
        }
        if (blocked) why += "Morgensperre"
        if (locked) why += "Sperrbildschirm"

        if (now - lastInsultSwap > 6_000) {
            lastInsultSwap = now
            insultIdx = (insultIdx + 1) % UglyView.INSULTS.size
        }
        overlay.show(if (blocked) 0 else level, overlayLines(now, penalty, session))
        SystemColorHack.apply(
            this, prefs,
            gray = (prefs.useGrayscale && level >= 2) || (night && exempt == null),
            invert = prefs.useInversion && level >= 3,
        )
        SystemTweaks.apply(this, prefs, level)
        val ugly2 = level >= 2 && !blocked
        shrink.update(
            if (prefs.shrinkScreen && ugly2) {
                ShrinkBars.fractionFor(session - Escalation.sessionLimitMs(prefs, factor))
            } else 0f,
        )
        stickers.update(if (prefs.stickers && ugly2) (if (level >= 3) 6 else 3) else 0, now)

        if (!locked) {
            if (level >= 3 && prefs.vibrate && now - lastVibrate > 45_000) {
                lastVibrate = now
                nag()
            }
            if (level >= 1 && limitNotifiedSession != prefs.sessionStart && penalty == 0 && !night &&
                sessionActiveMs >= Escalation.sessionLimitMs(prefs, factor)
            ) {
                limitNotifiedSession = prefs.sessionStart
                val msg = if (prefs.alwaysUgly) "Ab jetzt wird's noch hässlicher." else "Ab jetzt wird's hässlich."
                val extra = if (doom) " (Endlos-Scrollen zählt doppelt)" else if (dayStrict) " (tagsüber strenger)" else ""
                alert("Limit erreicht", "Session-Limit erreicht$extra. $msg 🤢")
            }
            if (onYouTube) checkYouTube(now)
        }

        publishSnapshot(now, fg, why.joinToString(", "))
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

    private fun limitFactor() = if (dayStrict) prefs.dayStrictPct.coerceIn(10, 100) / 100.0 else 1.0

    private fun hideWindows() {
        overlay.hide()
        shrink.hide()
        stickers.hide()
        blocker.hide()
        blocked = false
    }

    private fun restoreSystem(keepFont: Boolean) {
        SystemColorHack.restore(this, prefs)
        SystemTweaks.restore(this, prefs, keepFont)
    }

    /** Einmal pro Minute: fällige Lockerungen, fehlende Berechtigungen, Wochenbericht. */
    private fun minuteJobs(now: Long) {
        lastMinuteJobs = now
        Guard.applyDue(this, prefs, now).forEach { alert("Lockerung ist jetzt wirksam", it) }
        val day = Analyzer.dayKey(now)
        if (prefs.permAlertDay != day && (!Permissions.canDrawOverlays(this) || !Permissions.hasUsageAccess(this))) {
            prefs.permAlertDay = day
            alert("Berechtigung fehlt ⚠️", "Ohne Nutzungszugriff und Overlay kann Checker-Schreck nicht aufpassen.")
        }
        Report.maybeNotify(this, prefs, now)
    }

    /**
     * Lief die Überwachung zwischendurch nicht (abgeschossen, "Stopp erzwingen"), wird die
     * Nutzung aus dieser Zeit nachgetragen.
     */
    private fun backcharge(now: Long) {
        if (!prefs.serviceAlive || prefs.lastAliveAt <= 0) return
        val from = maxOf(prefs.lastAliveAt, Analyzer.startOfDay(now))
        if (now - from < 60_000) return
        val ms = runCatching { Analyzer.unmonitoredMs(this, prefs, from, now) }.getOrDefault(0L)
            .coerceAtMost(now - from)
        if (ms < 60_000) return
        dailyActiveMs += ms
        prefs.dailyActiveMs = dailyActiveMs
        prefs.backchargedToday = prefs.backchargedToday + ms
        alert("Überwachung war unterbrochen", "${fmtDuration(ms)} Nutzung ohne Aufsicht wurden nachgetragen.")
    }

    /** Tageswechsel: Zähler sichern, Tag abschließen (inkl. Limit-Anpassung), neu anfangen. */
    private fun rollover(now: Long) {
        val key = Analyzer.dayKey(now)
        if (memDay == key) return
        if (prefs.dayKey != key) persist() // damit der abgeschlossene Tag die letzten Sekunden enthält
        rolloverIfNeeded(prefs, now)
        dailyActiveMs = 0L
        youtubeMs = 0L
        scrollMs = 0L
        memDay = key
        announceAdaptive()
    }

    private fun announceAdaptive() {
        if (!prefs.adaptiveReportPending) return
        prefs.adaptiveReportPending = false
        val report = prefs.adaptiveReport
        if (report.isBlank() || !Permissions.hasNotifications(this)) return
        val n = NotificationCompat.Builder(this, App.CH_ALERT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Limits zurückgedreht 🔧")
            .setContentText(report.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(report))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ADAPTIVE_ID, n)
    }

    private fun overlayLines(now: Long, penalty: Int, session: Long): List<String> {
        val l = ArrayList<String>()
        if (YouTubeLimit.isYouTube(lastFg) && YouTubeLimit.isOver(prefs, youtubeMs)) {
            l += "YouTube-Limit überschritten!"
            l += "YouTube heute: ${fmtDuration(youtubeMs)} / ${prefs.youtubeLimitMin} min"
            l += "Session: ${fmtClock(sessionActiveMs)}"
            return l
        }
        if (night) {
            l += "🌙 Nachtmodus"
            l += "Bis ${prefs.nightEndHour} Uhr bleibt es so. Schlaf gut."
            l += "${prefs.checksToday} Checks heute"
            return l
        }
        if (level == 1 && penalty == 0) {
            // Grundstufe: kurz und oben, damit man noch lesen kann
            val next = Escalation.minutesToNextLevel(prefs, session, limitFactor())
            val fast = if (doom) " · 📜 Scrollen zählt ${prefs.doomscrollFactor}×" else ""
            l += "Session ${fmtClock(session)} · Stufe 2 in $next min$fast"
            val budget = prefs.checkBudget
            l += if (budget > 0) "${prefs.checksToday} von $budget Checks heute" else "${prefs.checksToday} Checks heute"
            return l
        }
        l += UglyView.INSULTS[insultIdx]
        l += "Session: ${fmtClock(sessionActiveMs)}"
        l += "Heute: ${fmtDuration(dailyActiveMs)} · ${prefs.checksToday} Checks"
        if (doom) l += "📜 Endlos-Scrollen: Uhr läuft ${prefs.doomscrollFactor}× so schnell"
        if (penalty > 0) l += "Check-Strafe noch ${fmtDuration(prefs.penaltyUntil - now)}"
        return l
    }

    private fun publishSnapshot(now: Long, fg: String?, reason: String) {
        snapshot = Snapshot(
            level, fg, exempt, sessionActiveMs, dailyActiveMs, prefs.penaltyUntil, youtubeMs,
            night, dayStrict, doom, if (blocked) prefs.morningBlockUntil else 0L, reason, lastError, now,
        )
    }

    private fun persist() {
        lastPersist = System.currentTimeMillis()
        prefs.sessionActiveMs = sessionActiveMs
        prefs.dailyActiveMs = dailyActiveMs
        prefs.screenActiveMs = screenActiveMs
        prefs.youtubeMsToday = youtubeMs
        prefs.scrollMsToday = scrollMs
        prefs.lastAliveAt = lastPersist
        if (sessionActiveMs > prefs.longestSessionToday) prefs.longestSessionToday = sessionActiveMs
        WidgetProvider.render(this)
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
            blocked -> "☀️ Morgensperre"
            night -> "🌙 Nachtmodus: Stufe 3"
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
        private const val TICK_MS = 1_000L
        private const val NOTIF_ID = 1
        private const val ALERT_ID = 2
        private const val ADAPTIVE_ID = 3
        const val ACTION_STOP = "app.checkerschreck.STOP"
        const val ACTION_COOKING = "app.checkerschreck.COOKING"

        @Volatile var running = false
            private set

        @Volatile var snapshot: Snapshot? = null
            private set

        @Volatile private var instance: MonitorService? = null

        /** Von der Bedienungshilfe: Es hat sich ein Fenster geändert, bitte sofort neu bewerten. */
        fun poke() {
            instance?.pokeTick()
        }

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs(ctx).monitoringEnabled = false
            Watchdog.cancel(ctx)
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }

        /** Neuer Tag? Dann Tageszähler zurücksetzen. Gibt true zurück, wenn zurückgesetzt wurde. */
        fun rolloverIfNeeded(p: Prefs, now: Long): Boolean {
            val key = Analyzer.dayKey(now)
            val old = p.dayKey
            if (old == key) return false
            if (p.firstDayKey.isEmpty()) p.firstDayKey = old.ifEmpty { key }
            // Abgeschlossenen Tag festhalten (der erste, unvollständige Messtag zählt nicht)
            if (old.isNotEmpty() && old != p.firstDayKey) {
                History.add(
                    p,
                    DayRecord(old, p.dailyActiveMs, p.checksToday, p.youtubeMsToday, p.longestSessionToday, p.scrollMsToday),
                )
                val changes = Adaptive.adjust(p, (Analyzer.startOfDay(now) + 12 * 3600_000L) / 86_400_000L)
                if (changes.isNotEmpty()) {
                    p.adaptiveReport = changes.joinToString("\n")
                    p.adaptiveReportDay = key
                    p.adaptiveReportPending = true
                }
            }
            p.longestSessionToday = 0L
            p.scrollMsToday = 0L
            p.backchargedToday = 0L
            p.serviceStartsToday = 0
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

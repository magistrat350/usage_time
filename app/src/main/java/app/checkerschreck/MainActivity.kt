package app.checkerschreck

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var previewOverlay: UglyOverlay? = null

    private lateinit var verdict: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var toggle: MaterialButton
    private lateinit var permBox: LinearLayout
    private lateinit var tChecks: StatTile
    private lateinit var tShort: StatTile
    private lateinit var tScreen: StatTile
    private lateinit var tGap: StatTile
    private lateinit var tBreak: StatTile
    private lateinit var tLastHour: StatTile
    private lateinit var chart: HourChartView
    private lateinit var insights: TextView
    private lateinit var topApps: TextView
    private lateinit var cookBtn: MaterialButton
    private lateinit var nightBtn: MaterialButton
    private lateinit var weaning: TextView
    private lateinit var triggers: TextView
    private lateinit var diagnosis: TextView
    private lateinit var pendingCard: LinearLayout
    private lateinit var liftBtn: MaterialButton
    private lateinit var updateBtn: MaterialButton
    private var updateBusy = false

    private var lastStatsRefresh = 0L

    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            if (System.currentTimeMillis() - lastStatsRefresh > 15_000) refreshStats()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val (scroll, col) = screen()

        col.text("Checker-Schreck", 30f, bold = true)
        verdict = col.text("", 16f, alpha = 0.8f)

        val status = col.card()
        statusTitle = status.text("", 20f, bold = true)
        statusDetail = status.text("", 14f, topDp = 4, alpha = 0.8f)
        toggle = status.button("") { toggleMonitoring() }
        liftBtn = status.button("🆘 Morgensperre heute aufheben", outlined = true) { liftMorningBlock() }
        liftBtn.visibility = android.view.View.GONE

        pendingCard = col.card("⏳ Geplante Lockerungen")

        permBox = col.card("Berechtigungen")

        val weaningCard = col.card("Entwöhnung")
        weaning = weaningCard.text("", 14f, topDp = 4)

        val today = col.card("Heute")
        val r1 = today.row()
        tChecks = StatTile(r1, "Checks")
        tShort = StatTile(r1, "Reflex-Checks")
        tScreen = StatTile(r1, "Bildschirmzeit")
        val r2 = today.row()
        tGap = StatTile(r2, "Ø Abstand")
        tBreak = StatTile(r2, "Längste Pause")
        tLastHour = StatTile(r2, "Letzte Stunde")

        val chartCard = col.card("Checks pro Stunde")
        chart = HourChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val analysis = col.card("Analyse")
        insights = analysis.text("", 14f, topDp = 4)

        val trig = col.card("Auslöser deiner Checks (7 Tage)")
        triggers = trig.text("", 14f, topDp = 4)

        val apps = col.card("Top-Apps heute")
        topApps = apps.text("", 14f, topDp = 4)

        val diag = col.card("Diagnose")
        diagnosis = diag.text("", 13f, topDp = 4)

        val actions = col.card("Aktionen")
        cookBtn = actions.button("🍳 Kochmodus") {
            if (CookingMode.start(this)) refreshStatus()
        }
        nightBtn = actions.button("🌙 Nachtmodus heute später", outlined = true) { askNightDelay() }
        actions.text("Hässlichkeit testen (6 Sekunden):", 13f, topDp = 10, alpha = 0.7f)
        val previewRow = actions.row()
        for (lvl in 1..3) {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b.text = "Stufe $lvl"
            b.isAllCaps = false
            b.setOnClickListener { preview(lvl) }
            previewRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = if (lvl < 3) dp(6) else 0
            })
        }
        actions.button("⚙️ Einstellungen", outlined = true) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        actions.button("🥕 Rezept- & Ausnahme-Apps", outlined = true) {
            startActivity(Intent(this, ExemptAppsActivity::class.java))
        }
        actions.button("📊 Wochenbericht", outlined = true) {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        updateBtn = actions.button("🔄 Nach Updates suchen", outlined = true) { checkForUpdates(manual = true) }
        actions.text("Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", 12f, topDp = 6, alpha = 0.6f)

        setContentView(scroll)
        if (intent?.getBooleanExtra(EXTRA_LIFT_MORNING, false) == true) liftMorningBlock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_LIFT_MORNING, false)) liftMorningBlock()
    }

    private fun liftMorningBlock() {
        if (!Schedule.morningBlocked(prefs, System.currentTimeMillis())) return
        val lift = {
            prefs.morningBlockUntil = 0L
            toast("Morgensperre für heute aufgehoben.")
            refreshStatus()
        }
        if (prefs.guardMode == Guard.OFF) {
            lift()
        } else {
            Guard.typeSentence(
                this, "Morgensperre aufheben?",
                "Nur für echte Notfälle. Tippe dafür diesen Satz ab:",
                onOk = lift, onCancel = {},
            )
        }
    }

    private fun refreshPending() {
        pendingCard.removeViews(1, pendingCard.childCount - 1)
        val list = Guard.pending(prefs)
        (pendingCard.parent as? android.view.View)?.visibility =
            if (list.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        val now = System.currentTimeMillis()
        for (item in list) {
            pendingCard.text(item.change.label, 14f, bold = true, topDp = 8)
            pendingCard.text("Wirkt in ${fmtDuration((item.dueAt - now).coerceAtLeast(0))}", 13f, alpha = 0.75f)
            pendingCard.button("Verwerfen", outlined = true) {
                Guard.cancel(prefs, item)
                refreshPending()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Guard.applyDue(this, prefs, System.currentTimeMillis())
        // Falls der Dienst nicht läuft, dürfen keine Systemänderungen hängen bleiben
        if (!MonitorService.running) {
            SystemColorHack.restore(this, prefs)
            SystemTweaks.restore(this, prefs)
        }
        refreshPending()
        // Dienst wieder starten, falls er gewollt ist, aber nicht läuft
        if (prefs.monitoringEnabled && !MonitorService.running && requiredGranted()) {
            runCatching { MonitorService.start(this) }
        }
        refreshPermissions()
        refreshStats()
        handler.post(ticker)
        // Einmal alle 12 Stunden still nach Updates schauen
        if (System.currentTimeMillis() - prefs.lastUpdateCheck > 12 * 3600_000L) checkForUpdates(manual = false)
    }

    // ------------------------------------------------------------------ Updates

    private fun alive() = !isFinishing && !isDestroyed

    private fun checkForUpdates(manual: Boolean) {
        if (updateBusy) return
        updateBusy = true
        prefs.lastUpdateCheck = System.currentTimeMillis()
        if (manual) updateBtn.text = "🔄 Suche …"
        Thread {
            val result = runCatching { Updater.fetchLatest() }
            runOnUiThread {
                updateBusy = false
                if (!alive()) return@runOnUiThread
                updateBtn.text = "🔄 Nach Updates suchen"
                result.onSuccess { info ->
                    if (!Updater.isNewer(info)) {
                        if (manual) toast("Du hast die neueste Version (Build ${BuildConfig.VERSION_CODE}).")
                        return@onSuccess
                    }
                    updateBtn.text = "⬇️ Update auf Build ${info.versionCode} installieren"
                    if (manual || prefs.updateDismissedVersion != info.versionCode) offerUpdate(info)
                }.onFailure { e ->
                    if (manual) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Update-Suche fehlgeschlagen")
                            .setMessage("${e.message ?: e.javaClass.simpleName}\n\nBesteht eine Internetverbindung? Du kannst die APK auch direkt von der Release-Seite laden.")
                            .setPositiveButton("Release-Seite öffnen") { _, _ -> openReleasePage() }
                            .setNegativeButton("Schließen", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    private fun offerUpdate(info: Updater.Info) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Update verfügbar")
            .setMessage(
                "Installiert: Build ${BuildConfig.VERSION_CODE}\nNeu: Build ${info.versionCode}" +
                    (if (info.sha.isNotEmpty()) " (${info.sha})" else "") +
                    "\n\nDeine Einstellungen und dein Check-Verlauf bleiben erhalten.",
            )
            .setPositiveButton("Herunterladen & installieren") { _, _ -> downloadAndInstall(info) }
            .setNegativeButton("Später") { _, _ -> prefs.updateDismissedVersion = info.versionCode }
            .show()
    }

    private fun downloadAndInstall(info: Updater.Info) {
        if (updateBusy) return
        updateBusy = true
        updateBtn.text = "⬇️ Lade … 0 %"
        Thread {
            val result = runCatching {
                Updater.download(this, info) { pct ->
                    runOnUiThread { if (alive()) updateBtn.text = "⬇️ Lade … $pct %" }
                }
            }
            runOnUiThread {
                updateBusy = false
                if (!alive()) return@runOnUiThread
                updateBtn.text = "⬇️ Update auf Build ${info.versionCode} installieren"
                result.onSuccess { apk ->
                    runCatching { Updater.install(this, apk) }.onFailure {
                        toast("Installer ließ sich nicht öffnen: ${it.message}")
                    }
                }.onFailure { e ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Download fehlgeschlagen")
                        .setMessage(e.message ?: e.javaClass.simpleName)
                        .setPositiveButton("Release-Seite öffnen") { _, _ -> openReleasePage() }
                        .setNegativeButton("Schließen", null)
                        .show()
                }
            }
        }.start()
    }

    private fun openReleasePage() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Updater.releasePage))) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ Entwöhnung

    private fun refreshWeaning(checksToday: Int) {
        val lines = ArrayList<String>()
        val a = Adaptive.averages(prefs)
        fun avg(v: Double?, unit: String) = if (v == null) "" else "  ·  Ø ${Math.round(v)} $unit"
        lines += "Tageslimit: ${prefs.dailyLimitMin} min" + avg(a?.activeMin, "min")
        lines += "Session-Limit: ${prefs.sessionLimitMin} min" + avg(a?.longestSessionMin, "min längste")
        lines += if (prefs.checkBudget > 0) {
            "Check-Budget: $checksToday von ${prefs.checkBudget} heute" + avg(a?.checks, "pro Tag")
        } else {
            "Check-Budget: noch keins" + avg(a?.checks, "Checks pro Tag")
        }
        if (prefs.youtubeLimitMin > 0) {
            lines += "YouTube-Limit: ${prefs.youtubeLimitMin} min" + avg(a?.youtubeMin, "min")
        }
        val days = a?.days ?: 0
        val footer = when {
            !prefs.adaptiveEnabled -> "Automatisches Zurückdrehen ist aus (Einstellungen)."
            days < Adaptive.MIN_DAYS -> {
                val missing = Adaptive.MIN_DAYS - days
                "Sammle noch $missing volle${if (missing == 1) "n Tag" else " Tage"} Daten. Danach werden die Limits " +
                    "jeden Tag auf ${prefs.adaptiveReductionPct} % unter deinen Schnitt gedreht."
            }
            prefs.adaptiveReport.isNotBlank() ->
                "Zuletzt gedreht (${prefs.adaptiveReportDay}):\n" + prefs.adaptiveReport.lines().joinToString("\n") { "• $it" }
            else -> "Ø aus $days Tagen. Bisher musste nichts gedreht werden."
        }
        weaning.text = lines.joinToString("\n") + "\n\n" + footer
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        previewOverlay?.hide()
        super.onDestroy()
    }

    private fun requiredGranted() = Permissions.hasUsageAccess(this) && Permissions.canDrawOverlays(this)

    private fun toggleMonitoring() {
        if (MonitorService.running) {
            if (prefs.pausedUntil > System.currentTimeMillis()) {
                prefs.pausedUntil = 0L // Pause vorzeitig beenden ist strenger: geht sofort
                toast("Pause beendet.")
                refreshStatus()
                return
            }
            askPauseOrStop()
            return
        }
        if (!requiredGranted()) {
            Toast.makeText(this, "Erst Nutzungszugriff und „Über anderen Apps“ erlauben.", Toast.LENGTH_LONG).show()
            refreshPermissions()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        prefs.monitoringEnabled = true
        Watchdog.schedule(this)
        MonitorService.start(this)
        handler.postDelayed({ refreshStatus(); refreshStats() }, 500)
    }

    /** Statt die Überwachung ganz zu stoppen, lässt sie sich befristet pausieren. Danach läuft sie von selbst weiter. */
    private fun askPauseOrStop() {
        val end = prefs.nightEndHour
        val options = listOf(
            "30 Minuten pausieren" to 30,
            "1 Stunde pausieren" to 60,
            "2 Stunden pausieren" to 120,
            "Bis $end Uhr pausieren" to Schedule.minutesUntilHour(System.currentTimeMillis(), end),
            "Ganz stoppen (läuft nicht von selbst wieder an)" to -1,
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Pausieren oder stoppen?")
            .setItems(options.map { it.first }.toTypedArray()) { _, i ->
                val minutes = options[i].second
                val change = if (minutes < 0) Guard.stopChange() else Guard.pauseChange(minutes)
                Guard.request(this, prefs, change) {
                    refreshPending()
                    handler.postDelayed({ refreshStatus() }, 300)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /**
     * Nachtmodus für die kommende bzw. laufende Nacht verschieben. Rechtzeitig vorher geplant ist das
     * frei (begrenzt pro Woche), spontan mitten in der Nacht kostet es einen abgetippten Satz.
     */
    private fun askNightDelay() {
        val now = System.currentTimeMillis()
        val (start, inside) = Schedule.nightWindow(prefs, now)
        val key = Analyzer.dayKey(start)
        val current = Schedule.nightDelayMin(prefs, key)
        val steps = (if (current > 0) listOf(0) else emptyList()) + listOf(30, 60, 90, 120, 180)
        val labels = steps.map { m ->
            val at = Schedule.clock(start + m * 60_000L)
            when {
                m == 0 -> "Aufschub zurücknehmen (wieder ab $at Uhr)"
                m == current -> "ab $at Uhr (so eingestellt)"
                else -> "ab $at Uhr (+$m min)"
            }
        }
        val left = (prefs.nightDelaysPerWeek - Schedule.plannedDelaysUsed(prefs, now)).coerceAtLeast(0)
        val title = if (inside) {
            "Nachtmodus läuft schon: Verschieben kostet einen Satz"
        } else {
            "Nachtmodus heute später (frei geplant: noch $left× diese Woche)"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, i ->
                val minutes = steps[i]
                val apply = { planned: Boolean ->
                    Schedule.setNightDelay(prefs, System.currentTimeMillis(), minutes, planned)
                    toast(if (minutes == 0) "Aufschub zurückgenommen." else "Nachtmodus heute erst ab ${Schedule.clock(start + minutes * 60_000L)} Uhr.")
                    refreshStatus()
                }
                when {
                    Schedule.delayIsFree(prefs, System.currentTimeMillis(), minutes) -> apply(true)
                    prefs.guardMode == Guard.OFF -> apply(false)
                    else -> Guard.typeSentence(
                        this, "Nachtmodus verschieben?",
                        "Nicht rechtzeitig geplant oder Wochenkontingent aufgebraucht. Tippe dafür diesen Satz ab:",
                        onOk = { apply(false) }, onCancel = {},
                    )
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun preview(level: Int) {
        if (!Permissions.canDrawOverlays(this)) {
            Toast.makeText(this, "Dafür braucht es „Über anderen Apps einblenden“.", Toast.LENGTH_LONG).show()
            return
        }
        val o = previewOverlay ?: UglyOverlay(this).also { previewOverlay = it }
        o.show(level, listOf(UglyView.INSULTS.random(), "Vorschau Stufe $level", "So sieht's aus, wenn du übertreibst"))
        handler.removeCallbacksAndMessages(PREVIEW_TOKEN)
        handler.postAtTime({ o.hide() }, PREVIEW_TOKEN, android.os.SystemClock.uptimeMillis() + 6_000)
    }

    private fun refreshStatus() {
        val snap = MonitorService.snapshot
        val now = System.currentTimeMillis()
        if (!MonitorService.running) {
            statusTitle.text = "😴 Überwachung aus"
            statusDetail.text = "Starte die Überwachung, damit Checks gezählt werden und das Handy bei zu langer Nutzung hässlich wird."
            toggle.text = "Überwachung starten"
        } else {
            toggle.text = if (prefs.pausedUntil > now) {
                "Pause beenden (noch ${fmtDuration(prefs.pausedUntil - now)})"
            } else {
                "Pausieren oder stoppen"
            }
            val s = snap
            statusTitle.text = when {
                s == null -> "👀 Passt auf"
                s.exempt != null -> "🍳 Pause: ${s.exempt}"
                s.night -> "🌙 Nachtmodus: Stufe 3"
                s.level > 0 -> "🤢 Hässlich-Stufe ${s.level}"
                else -> "👀 Passt auf"
            }
            val parts = ArrayList<String>()
            if (s != null) {
                parts += "Session: ${fmtClock(s.sessionMs)} (Limit ${prefs.sessionLimitMin} min)"
                val factor = if (s.dayStrict) prefs.dayStrictPct.coerceIn(10, 100) / 100.0 else 1.0
                if (s.exempt == null && !s.night && s.sessionMs < Escalation.sessionLimitMs(prefs, factor)) {
                    val next = Escalation.base(prefs) + 1
                    parts += "Noch ${Escalation.minutesToNextLevel(prefs, s.sessionMs, factor)} min bis Stufe $next"
                }
                if (s.nightLeftMs > 0) {
                    parts += "🌙 Nacht-Budget: noch ${fmtDuration(s.nightLeftMs + 59_000)}, dann Stufe 3 bis ${prefs.nightEndHour} Uhr"
                }
                if (s.dayStrict) parts += "☀️ Tagsüber strenger: Session-Limit nur ${prefs.dayStrictPct} %"
                if (s.doomscroll) parts += "📜 Endlos-Scrollen erkannt: Uhr läuft ${prefs.doomscrollFactor}× so schnell"
                if (s.morningBlockUntil > now) parts += "☀️ Morgensperre noch ${fmtDuration(s.morningBlockUntil - now)}"
                if (prefs.backchargedToday > 0) {
                    parts += "⚠️ ${fmtDuration(prefs.backchargedToday)} ohne Aufsicht nachgetragen"
                }
                parts += "Heute aktiv: ${fmtDuration(s.dailyMs)} von ${prefs.dailyLimitMin} min"
                if (YouTubeLimit.enabled(prefs)) {
                    val left = YouTubeLimit.limitMs(prefs) - s.youtubeMs
                    parts += if (left > 0) {
                        "📺 YouTube: ${fmtDuration(s.youtubeMs)} von ${prefs.youtubeLimitMin} min (noch ${fmtDuration(left)})"
                    } else {
                        "📺 YouTube: Limit für heute aufgebraucht"
                    }
                }
                if (s.penaltyUntil > now) parts += "🚨 Check-Strafe noch ${fmtDuration(s.penaltyUntil - now)}"
            }
            statusDetail.text = parts.joinToString("\n")
        }
        liftBtn.visibility =
            if (Schedule.morningBlocked(prefs, now)) android.view.View.VISIBLE else android.view.View.GONE
        refreshDiagnosis(snap, now)
        nightBtn.visibility = if (prefs.nightEnabled) android.view.View.VISIBLE else android.view.View.GONE
        val nightStart = Schedule.effectiveNightStart(prefs, now)
        nightBtn.text = if (Schedule.nightDelayMin(prefs, Schedule.state(prefs, now).nightKey) > 0 && now < nightStart) {
            "🌙 Nachtmodus heute erst ab ${Schedule.clock(nightStart)} Uhr (ändern)"
        } else {
            "🌙 Nachtmodus heute später"
        }
        val cooking = prefs.cookingUntil > now
        cookBtn.text = if (cooking) {
            "🍳 Kochmodus läuft (${fmtDuration(prefs.cookingUntil - now)})"
        } else {
            "🍳 Kochmodus ${prefs.cookingMinutes} min (heute noch ${(prefs.cookingPerDay - usedCookingToday()).coerceAtLeast(0)}×)"
        }
    }

    private fun usedCookingToday(): Int =
        if (prefs.dayKey == Analyzer.dayKey(System.currentTimeMillis())) prefs.cookingUsed else 0

    private fun refreshStats() {
        lastStatsRefresh = System.currentTimeMillis()
        val now = lastStatsRefresh
        val day = Analyzer.today(this, prefs, now)
        val week = Analyzer.weekAverage(this, now)
        verdict.text = Analyzer.verdict(day)
        tChecks.value.text = if (prefs.checkBudget > 0) "${day.checks}/${prefs.checkBudget}" else day.checks.toString()
        refreshWeaning(day.checks)
        tShort.value.text = day.shortChecks.toString()
        tScreen.value.text = fmtDuration(day.screenMs)
        tGap.value.text = if (day.avgGapMs > 0) fmtDuration(day.avgGapMs) else "–"
        tBreak.value.text = if (day.longestBreakMs > 0) fmtDuration(day.longestBreakMs) else "–"
        tLastHour.value.text = day.checksLastHour.toString()
        chart.values = day.perHour
        chart.currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        insights.text = Analyzer.insights(day, week, prefs).joinToString("\n\n") { "• $it" }
        refreshTriggers(now)
        val top = Analyzer.topApps(this, Analyzer.startOfDay(now), now)
        topApps.text = if (top.isEmpty()) {
            "Noch keine Daten (Nutzungszugriff nötig)."
        } else {
            top.mapIndexed { i, (name, ms) -> "${i + 1}. $name – ${fmtDuration(ms)}" }.joinToString("\n")
        }
    }

    /** Zeigt, was die Überwachung gerade sieht und warum welche Stufe gilt. */
    private fun refreshDiagnosis(s: MonitorService.Snapshot?, now: Long) {
        val l = ArrayList<String>()
        if (!MonitorService.running) {
            l += "Dienst: läuft nicht"
        } else {
            val age = if (s == null) "noch kein Takt" else "letzter Takt vor ${(now - s.updatedAt) / 1000} s"
            l += "Dienst: läuft ($age)"
        }
        val starts = prefs.serviceStartsToday
        l += "Heute ${starts}× gestartet" + if (starts > 3) {
            " ⚠️ Android beendet die App immer wieder. Akku-Optimierung ausschalten, Autostart erlauben " +
                "und die App in der Übersicht anpinnen (Schloss-Symbol)."
        } else ""
        if (s != null) {
            l += "Vordergrund-App: " + (s.foreground?.let { Analyzer.appLabel(this, it) } ?: "unbekannt")
            l += "Stufe ${s.level}: ${s.reason}"
            if (s.error.isNotEmpty()) l += "Letzter Fehler: ${s.error}"
        }
        fun mark(ok: Boolean) = if (ok) "an" else "aus"
        l += "Bedienungshilfe (hässlich auch in der Benachrichtigungsleiste, Rezept, Doomscroll): " +
            mark(RecipeAccessibilityService.instance != null)
        l += "Benachrichtigungszugriff (Auslöser): " + mark(NotificationTriggers.connected)
        l += "Riesenschrift: " + mark(Permissions.canWriteSettings(this)) +
            " · Graustufen/Animationen: " + mark(Permissions.hasSecureSettings(this))
        diagnosis.text = l.joinToString("\n")
    }

    private fun refreshTriggers(now: Long) {
        if (!Permissions.hasNotificationAccess(this)) {
            triggers.text = "Erlaube oben den Benachrichtigungszugriff, dann siehst du hier, welche Apps deine " +
                "Checks auslösen. Erfasst werden nur App und Zeitpunkt, nie der Inhalt."
            return
        }
        val t = Analyzer.triggers(this, now - 7 * 24 * 3600_000L)
        if (t.known == 0) {
            triggers.text = "Noch keine Daten. Ab jetzt wird bei jedem Check festgehalten, ob in den 90 Sekunden " +
                "davor eine Benachrichtigung kam."
            return
        }
        val lines = ArrayList<String>()
        lines += "${t.spontaneous * 100 / t.known} % ohne Anlass (${t.spontaneous} von ${t.known} Checks)"
        t.byApp.take(6).forEachIndexed { i, a ->
            val notif = if (a.notifications > 0) ", ${a.notifications} Benachrichtigungen" else ""
            lines += "${i + 1}. ${a.label} – ${a.checks * 100 / t.known} % (${a.checks} Checks$notif)"
        }
        val top = t.byApp.firstOrNull()
        val tip = when {
            t.spontaneous * 2 >= t.known ->
                "Mindestens die Hälfte deiner Checks kommt ohne jede Benachrichtigung. Stummschalten hilft da wenig, das ist Gewohnheit."
            top != null && top.checks * 5 >= t.known ->
                "Tipp: ${top.label} stummschalten oder bündeln. Das waren ${top.checks} Checks in 7 Tagen."
            else -> null
        }
        triggers.text = lines.joinToString("\n") + (if (tip != null) "\n\n$tip" else "")
    }

    private fun refreshPermissions() {
        permBox.removeViews(1, permBox.childCount - 1)
        permRow(
            "Nutzungszugriff", "Pflicht: erkennt, welche App offen ist.",
            Permissions.hasUsageAccess(this),
        ) { startActivity(Permissions.usageAccessIntent()) }
        permRow(
            "Über anderen Apps einblenden",
            "Pflicht: damit das Handy hässlich werden kann. Kommt „App was denied access“ bzw. " +
                "„Eingeschränkte Einstellung“: App-Info → ⋮ (oben rechts) → „Eingeschränkte Einstellungen " +
                "zulassen“, dann hier nochmal erlauben.",
            Permissions.canDrawOverlays(this),
            secondary = "App-Info" to { startActivity(Permissions.appDetailsIntent(this)) },
        ) { startActivity(Permissions.overlayIntent(this)) }
        permRow(
            "Benachrichtigungen", "Für Check-Alarme und den Kochmodus-Knopf.",
            Permissions.hasNotifications(this),
        ) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        permRow(
            "Akku-Optimierung aus", "Damit Android die Überwachung nicht abschießt.",
            Permissions.ignoresBatteryOptimizations(this),
        ) { runCatching { startActivity(Permissions.batteryIntent(this)) } }
        permRow(
            "Bedienungshilfe (empfohlen)",
            "Bedienungshilfe „Checker-Schreck Rezept-Erkennung“ aktivieren. Nur damit ist es auch in der " +
                "Benachrichtigungsleiste, den Schnelleinstellungen und auf dem Sperrbildschirm hässlich. " +
                "Außerdem erkennt sie Rezepte im Browser und Endlos-Scrollen. " +
                "Ist sie ausgegraut: App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.",
            Permissions.hasAccessibility(this),
            secondary = "App-Info" to { startActivity(Permissions.appDetailsIntent(this)) },
        ) { startActivity(Permissions.accessibilityIntent()) }
        permRow(
            "Auslöser-Analyse (optional)",
            "Benachrichtigungszugriff für „Checker-Schreck Auslöser-Analyse“ erlauben. Dann zeigt die App, welche " +
                "Benachrichtigungen deine Checks auslösen. Erfasst werden nur App und Zeitpunkt.",
            Permissions.hasNotificationAccess(this),
            secondary = "App-Info" to { startActivity(Permissions.appDetailsIntent(this)) },
        ) { runCatching { startActivity(Permissions.notificationAccessIntent()) } }
        permRow(
            "Riesige Schrift (optional)",
            "„Systemeinstellungen ändern“ erlauben. Ab Stufe 2 wird die Schrift auf dem ganzen Handy riesig.",
            Permissions.canWriteSettings(this),
        ) { runCatching { startActivity(Permissions.writeSettingsIntent(this)) } }
        permRow(
            "Graustufen & zähe Animationen (optional, adb)",
            "Ab Stufe 2 wird das ganze Handy grau und träge. Einmalig am PC ausführen:\n${Permissions.adbCommand(this)}",
            Permissions.hasSecureSettings(this),
            buttonLabel = "Befehl kopieren",
        ) {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("adb", Permissions.adbCommand(this)))
            Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
        }
    }

    private fun permRow(
        title: String,
        desc: String,
        granted: Boolean,
        buttonLabel: String = "Erlauben",
        secondary: Pair<String, () -> Unit>? = null,
        action: () -> Unit,
    ) {
        permBox.text((if (granted) "✅ " else "❌ ") + title, 15f, bold = true, topDp = 10)
        if (granted) return
        permBox.text(desc, 13f, alpha = 0.75f)
        val r = permBox.row()
        val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        b.text = buttonLabel
        b.isAllCaps = false
        b.setOnClickListener { action() }
        r.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        if (secondary != null) {
            val b2 = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b2.text = secondary.first
            b2.isAllCaps = false
            b2.setOnClickListener { secondary.second() }
            r.addView(b2, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
    }

    companion object {
        private val PREVIEW_TOKEN = Any()
        const val EXTRA_LIFT_MORNING = "lift_morning"
    }
}

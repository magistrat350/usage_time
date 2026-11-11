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

        permBox = col.card("Berechtigungen")

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

        val apps = col.card("Top-Apps heute")
        topApps = apps.text("", 14f, topDp = 4)

        val actions = col.card("Aktionen")
        cookBtn = actions.button("🍳 Kochmodus") {
            if (CookingMode.start(this)) refreshStatus()
        }
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

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Dienst wieder starten, falls er gewollt ist, aber nicht läuft
        if (prefs.monitoringEnabled && !MonitorService.running && requiredGranted()) {
            runCatching { MonitorService.start(this) }
        }
        refreshPermissions()
        refreshStats()
        handler.post(ticker)
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
            MonitorService.stop(this)
            handler.postDelayed({ refreshStatus() }, 300)
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
        MonitorService.start(this)
        handler.postDelayed({ refreshStatus(); refreshStats() }, 500)
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
            toggle.text = "Überwachung stoppen"
            val s = snap
            statusTitle.text = when {
                s == null -> "👀 Passt auf"
                s.exempt != null -> "🍳 Pause: ${s.exempt}"
                s.level > 0 -> "🤢 Hässlich-Stufe ${s.level}"
                else -> "👀 Passt auf"
            }
            val parts = ArrayList<String>()
            if (s != null) {
                parts += "Session: ${fmtClock(s.sessionMs)} (Limit ${prefs.sessionLimitMin} min)"
                if (s.exempt == null && s.level == 0) {
                    parts += "Noch ${Escalation.minutesToNextLevel(prefs, s.sessionMs)} min bis es hässlich wird"
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
        tChecks.value.text = day.checks.toString()
        tShort.value.text = day.shortChecks.toString()
        tScreen.value.text = fmtDuration(day.screenMs)
        tGap.value.text = if (day.avgGapMs > 0) fmtDuration(day.avgGapMs) else "–"
        tBreak.value.text = if (day.longestBreakMs > 0) fmtDuration(day.longestBreakMs) else "–"
        tLastHour.value.text = day.checksLastHour.toString()
        chart.values = day.perHour
        chart.currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        insights.text = Analyzer.insights(day, week, prefs).joinToString("\n\n") { "• $it" }
        val top = Analyzer.topApps(this, Analyzer.startOfDay(now), now)
        topApps.text = if (top.isEmpty()) {
            "Noch keine Daten (Nutzungszugriff nötig)."
        } else {
            top.mapIndexed { i, (name, ms) -> "${i + 1}. $name – ${fmtDuration(ms)}" }.joinToString("\n")
        }
    }

    private fun refreshPermissions() {
        permBox.removeViews(1, permBox.childCount - 1)
        permRow(
            "Nutzungszugriff", "Pflicht: erkennt, welche App offen ist.",
            Permissions.hasUsageAccess(this),
        ) { startActivity(Permissions.usageAccessIntent()) }
        permRow(
            "Über anderen Apps einblenden", "Pflicht: damit das Handy hässlich werden kann.",
            Permissions.canDrawOverlays(this),
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
            "Rezept-Erkennung (optional)",
            "Bedienungshilfe „Checker-Schreck Rezept-Erkennung“ aktivieren – erkennt Rezepte auch im Browser. " +
                "Ist sie ausgegraut: App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.",
            Permissions.hasAccessibility(this),
            secondary = "App-Info" to { startActivity(Permissions.appDetailsIntent(this)) },
        ) { startActivity(Permissions.accessibilityIntent()) }
        permRow(
            "Graustufen-Modus (optional, adb)",
            "Ab Stufe 2 wird das ganze Handy grau. Einmalig am PC ausführen:\n${Permissions.adbCommand(this)}",
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
    }
}

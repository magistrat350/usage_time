package app.checkerschreck

import java.util.Calendar

/** Zeitfenster: Nachtmodus, Morgensperre, tagsüber strenger. */
object Schedule {
    /**
     * [night]: Das Nachtfenster läuft (ein Aufschub ist schon eingerechnet).
     * [nightKey]: Kennung der laufenden bzw. nächsten Nacht (Datum ihres Beginns).
     */
    data class State(val night: Boolean, val dayStrict: Boolean, val nightKey: String)

    private const val HOUR_MS = 3600_000L

    private const val MORNING_FROM = 4
    private const val MORNING_TO = 12

    fun hour(now: Long) = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)

    private fun inWindow(h: Int, start: Int, end: Int) =
        if (start <= end) h in start until end else (h >= start || h < end)

    private fun atHour(now: Long, hour: Int): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Beginn des Nachtfensters, in dem [now] liegt (sonst des nächsten), und ob [now] darin liegt. */
    fun nightWindow(p: Prefs, now: Long): Pair<Long, Boolean> {
        val hours = ((p.nightEndHour - p.nightStartHour) % 24 + 24) % 24
        val len = (if (hours == 0) 24 else hours) * HOUR_MS
        val today = atHour(now, p.nightStartHour)
        val yesterday = today - 24 * HOUR_MS
        return when {
            now >= yesterday && now < yesterday + len -> yesterday to true
            now >= today && now < today + len -> today to true
            now < today -> today to false
            else -> today + 24 * HOUR_MS to false
        }
    }

    /** Für diese Nacht eingestellter Aufschub in Minuten. */
    fun nightDelayMin(p: Prefs, nightKey: String) = if (p.nightDelayKey == nightKey) p.nightDelayMin else 0

    /** Wann der Nachtmodus in der laufenden bzw. nächsten Nacht wirklich beginnt. */
    fun effectiveNightStart(p: Prefs, now: Long): Long {
        val (start, _) = nightWindow(p, now)
        return start + nightDelayMin(p, Analyzer.dayKey(start)) * 60_000L
    }

    fun state(p: Prefs, now: Long): State {
        val (start, inside) = nightWindow(p, now)
        val key = Analyzer.dayKey(start)
        return State(
            night = p.nightEnabled && inside && now >= start + nightDelayMin(p, key) * 60_000L,
            dayStrict = p.dayStrictEnabled && inWindow(hour(now), p.dayStartHour, p.dayEndHour),
            nightKey = key,
        )
    }

    // ---------------------------------------------------------------- Aufschub ("heute später")

    private fun delayLog(p: Prefs) = p.nightDelayLog.split(',').filter { it.isNotEmpty() }

    /** Im Voraus geplante Aufschübe der letzten 7 Tage. */
    fun plannedDelaysUsed(p: Prefs, now: Long): Int {
        val from = Analyzer.dayKey(now - 6 * 24 * HOUR_MS)
        return delayLog(p).count { it >= from }
    }

    /**
     * Darf der Nachtmodus ohne Hürde auf [minutes] verschoben werden?
     * Ja, wenn es eine Verkürzung ist oder wenn rechtzeitig vor Beginn der Nacht geplant wird
     * und das Wochenkontingent reicht. Spontan mitten in der Nacht kostet einen abgetippten Satz.
     */
    fun delayIsFree(p: Prefs, now: Long, minutes: Int): Boolean {
        val (start, inside) = nightWindow(p, now)
        val key = Analyzer.dayKey(start)
        if (minutes <= nightDelayMin(p, key)) return true
        if (inside) return false
        return key in delayLog(p) || plannedDelaysUsed(p, now) < p.nightDelaysPerWeek
    }

    fun setNightDelay(p: Prefs, now: Long, minutes: Int, planned: Boolean) {
        val key = Analyzer.dayKey(nightWindow(p, now).first)
        p.nightDelayKey = key
        p.nightDelayMin = minutes.coerceAtLeast(0)
        if (planned && minutes > 0 && key !in delayLog(p)) {
            p.nightDelayLog = (delayLog(p) + key).takeLast(30).joinToString(",")
        }
    }

    /** Minuten bis zur nächsten vollen Stunde [hour]. */
    fun minutesUntilHour(now: Long, hour: Int): Int {
        var t = atHour(now, hour)
        if (t <= now) t += 24 * HOUR_MS
        return ((t - now) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun clock(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** Beim ersten Entsperren am Morgen beginnt die Morgensperre. Gibt true zurück, wenn sie gestartet wurde. */
    fun maybeStartMorningBlock(p: Prefs, now: Long): Boolean {
        val day = Analyzer.dayKey(now)
        if (p.morningBlockDay == day) return false
        if (hour(now) !in MORNING_FROM until MORNING_TO) return false
        val first = p.morningBlockDay.isEmpty() // direkt nach der Installation nicht sofort sperren
        p.morningBlockDay = day
        if (first || !p.morningBlockEnabled || p.morningBlockMin <= 0) return false
        p.morningBlockUntil = now + p.morningBlockMin * 60_000L
        return true
    }

    fun morningBlocked(p: Prefs, now: Long) = p.morningBlockEnabled && p.morningBlockUntil > now
}

/** Zählt Scroll-Ereignisse aus der Bedienungshilfe (höchstens eins pro Sekunde). */
object ScrollMeter {
    private const val WINDOW_MS = 120_000L

    /** So viele Scroll-Sekunden in 2 Minuten gelten als Endlos-Scrollen. */
    private const val THRESHOLD = 45

    private val ticks = ArrayDeque<Long>()

    @Synchronized
    fun onScroll(now: Long) {
        val sec = now / 1000
        if (ticks.lastOrNull() == sec) return
        ticks.addLast(sec)
        while (ticks.isNotEmpty() && ticks.first() < sec - WINDOW_MS / 1000) ticks.removeFirst()
    }

    @Synchronized
    fun scrollSeconds(now: Long): Int {
        val from = (now - WINDOW_MS) / 1000
        return ticks.count { it >= from }
    }

    fun isDoomscrolling(now: Long) = scrollSeconds(now) >= THRESHOLD

    @Synchronized
    fun scrolledWithin(now: Long, ms: Long): Boolean {
        val last = ticks.lastOrNull() ?: return false
        return now / 1000 - last <= ms / 1000
    }
}

package app.checkerschreck

import java.util.Calendar

/** Zeitfenster: Nachtmodus, Morgensperre, tagsüber strenger. */
object Schedule {
    data class State(val night: Boolean, val dayStrict: Boolean)

    private const val MORNING_FROM = 4
    private const val MORNING_TO = 12

    fun hour(now: Long) = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)

    private fun inWindow(h: Int, start: Int, end: Int) =
        if (start <= end) h in start until end else (h >= start || h < end)

    fun state(p: Prefs, now: Long): State {
        val h = hour(now)
        return State(
            night = p.nightEnabled && inWindow(h, p.nightStartHour, p.nightEndHour),
            dayStrict = p.dayStrictEnabled && inWindow(h, p.dayStartHour, p.dayEndHour),
        )
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

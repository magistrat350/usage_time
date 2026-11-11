package app.checkerschreck

/** In welche Richtung eine Einstellung "lockerer" wird. */
enum class Loose { UP, DOWN, NONE }

class IntSetting(
    val id: String,
    val title: String,
    val unit: String,
    val loose: Loose,
    val get: (Prefs) -> Int,
    val set: (Prefs, Int) -> Unit,
    /** 0 bedeutet "aus": dorthin zu wechseln ist immer eine Lockerung. */
    val zeroIsOff: Boolean = false,
    val format: ((Int) -> String)? = null,
) {
    fun isLooser(old: Int, new: Int): Boolean {
        if (old == new) return false
        if (zeroIsOff) {
            if (new == 0) return true
            if (old == 0) return false
        }
        return when (loose) {
            Loose.UP -> new > old
            Loose.DOWN -> new < old
            Loose.NONE -> false
        }
    }

    fun show(v: Int): String = format?.invoke(v) ?: "$v $unit".trim()
}

class BoolSetting(
    val id: String,
    val title: String,
    /** Der Wert, der eine Lockerung ist (null = egal). */
    val looseValue: Boolean?,
    val get: (Prefs) -> Boolean,
    val set: (Prefs, Boolean) -> Unit,
) {
    fun isLooser(new: Boolean) = looseValue != null && new == looseValue
}

/**
 * Alle Einstellungen an einer Stelle, jeweils mit der Angabe, was "lockerer" bedeutet.
 * Der Schummel-Schutz ([Guard]) lässt Lockerungen nur mit Hürde zu, Verschärfungen sofort.
 */
object Rules {
    private fun hour(v: Int) = "$v Uhr"

    val SESSION_LIMIT = IntSetting("session_limit", "Nächste Stufe ab (am Stück)", "min", Loose.UP,
        { it.sessionLimitMin }, { p, v -> p.sessionLimitMin = v })
    val ESCALATION_STEP = IntSetting("escalation_step", "Eine Stufe hässlicher alle", "min", Loose.UP,
        { it.escalationStepMin }, { p, v -> p.escalationStepMin = v })
    val DAILY_LIMIT = IntSetting("daily_limit", "Tageslimit", "min", Loose.UP,
        { it.dailyLimitMin }, { p, v -> p.dailyLimitMin = v })
    val RESET_PAUSE = IntSetting("reset_pause", "Session zählt als beendet nach Bildschirm-aus von", "s", Loose.DOWN,
        { it.resetPauseSec }, { p, v -> p.resetPauseSec = v })
    val MAX_LEVEL = IntSetting("max_level", "Maximale Hässlich-Stufe", "", Loose.DOWN,
        { it.maxLevel }, { p, v -> p.maxLevel = v })
    val ADAPTIVE_PCT = IntSetting("adaptive_pct", "Ziel: unter meinem 7-Tage-Schnitt", "%", Loose.DOWN,
        { it.adaptiveReductionPct }, { p, v -> p.adaptiveReductionPct = v })
    val YOUTUBE_LIMIT = IntSetting("youtube_limit", "YouTube-Tageslimit (0 = aus)", "min", Loose.UP,
        { it.youtubeLimitMin }, { p, v -> p.youtubeLimitMin = v }, zeroIsOff = true)
    val CHECK_BUDGET = IntSetting("check_budget", "Check-Budget pro Tag (0 = aus)", "Checks", Loose.UP,
        { it.checkBudget }, { p, v -> p.checkBudget = v }, zeroIsOff = true)
    val BURST_COUNT = IntSetting("burst_count", "Check-Attacke ab", "Checks", Loose.UP,
        { it.burstCount }, { p, v -> p.burstCount = v })
    val BURST_WINDOW = IntSetting("burst_window", "… innerhalb von", "min", Loose.DOWN,
        { it.burstWindowMin }, { p, v -> p.burstWindowMin = v })
    val BURST_PENALTY = IntSetting("burst_penalty", "Strafe bei Check-Attacke", "min", Loose.DOWN,
        { it.burstPenaltyMin }, { p, v -> p.burstPenaltyMin = v })
    val SHORT_CHECK = IntSetting("short_check", "Reflex-Check, wenn kürzer als", "s", Loose.NONE,
        { it.shortCheckSec }, { p, v -> p.shortCheckSec = v })
    val COOKING_MIN = IntSetting("cooking_min", "Kochmodus-Dauer", "min", Loose.UP,
        { it.cookingMinutes }, { p, v -> p.cookingMinutes = v })
    val COOKING_PER_DAY = IntSetting("cooking_per_day", "Kochmodus pro Tag", "×", Loose.UP,
        { it.cookingPerDay }, { p, v -> p.cookingPerDay = v })
    val GUARD_MODE = IntSetting("guard_mode", "Schummel-Schutz", "", Loose.DOWN,
        { it.guardMode }, { p, v -> p.guardMode = v }, format = { Guard.MODE_NAMES.getOrElse(it) { "?" } })
    val NIGHT_START = IntSetting("night_start", "Nachtmodus ab", "", Loose.UP,
        { it.nightStartHour }, { p, v -> p.nightStartHour = v }, format = { hour(it) })
    val NIGHT_END = IntSetting("night_end", "Nachtmodus bis", "", Loose.DOWN,
        { it.nightEndHour }, { p, v -> p.nightEndHour = v }, format = { hour(it) })
    val MORNING_MIN = IntSetting("morning_min", "Morgensperre nach dem ersten Entsperren", "min", Loose.DOWN,
        { it.morningBlockMin }, { p, v -> p.morningBlockMin = v })
    val DAY_START = IntSetting("day_start", "Tagsüber strenger ab", "", Loose.UP,
        { it.dayStartHour }, { p, v -> p.dayStartHour = v }, format = { hour(it) })
    val DAY_END = IntSetting("day_end", "Tagsüber strenger bis", "", Loose.DOWN,
        { it.dayEndHour }, { p, v -> p.dayEndHour = v }, format = { hour(it) })
    val DAY_PCT = IntSetting("day_pct", "Tagsüber gilt vom Session-Limit nur", "%", Loose.UP,
        { it.dayStrictPct }, { p, v -> p.dayStrictPct = v })
    val DOOM_FACTOR = IntSetting("doom_factor", "Beim Endlos-Scrollen läuft die Session-Uhr", "", Loose.DOWN,
        { it.doomscrollFactor }, { p, v -> p.doomscrollFactor = v }, format = { "${it}× so schnell" })

    val ALWAYS_UGLY = BoolSetting("always_ugly", "Grundstufe 1: Handy ist immer hässlich", false,
        { it.alwaysUgly }, { p, v -> p.alwaysUgly = v })
    val ADAPTIVE = BoolSetting("adaptive", "Limits anhand meiner Nutzung immer weiter senken", false,
        { it.adaptiveEnabled }, { p, v -> p.adaptiveEnabled = v })
    val YOUTUBE_KICK = BoolSetting("youtube_kick", "Nach dem Limit YouTube schließen", false,
        { it.youtubeKick }, { p, v -> p.youtubeKick = v })
    val UNLOCK_NUDGE = BoolSetting("unlock_nudge", "Check-Zähler beim Entsperren anzeigen", null,
        { it.unlockNudge }, { p, v -> p.unlockNudge = v })
    val GRAYSCALE = BoolSetting("grayscale", "Graustufen ab Stufe 2 (braucht adb-Freigabe)", false,
        { it.useGrayscale }, { p, v -> p.useGrayscale = v })
    val INVERSION = BoolSetting("inversion", "Farben invertieren ab Stufe 3 (braucht adb-Freigabe)", false,
        { it.useInversion }, { p, v -> p.useInversion = v })
    val VIBRATE = BoolSetting("vibrate", "Nerv-Vibration ab Stufe 3", false,
        { it.vibrate }, { p, v -> p.vibrate = v })
    val UGLY_FONT = BoolSetting("ugly_font", "Riesige Schrift ab Stufe 2", false,
        { it.uglyFont }, { p, v -> p.uglyFont = v })
    val UGLY_ANIM = BoolSetting("ugly_anim", "Zähe Animationen ab Stufe 2 (braucht adb-Freigabe)", false,
        { it.uglyAnimations }, { p, v -> p.uglyAnimations = v })
    val SHRINK = BoolSetting("shrink", "Schrumpfender Bildschirm ab Stufe 2", false,
        { it.shrinkScreen }, { p, v -> p.shrinkScreen = v })
    val RECIPE_CONTENT = BoolSetting("recipe_content", "Rezepte auf dem Bildschirm erkennen (Bedienungshilfe)", null,
        { it.recipeContentDetection }, { p, v -> p.recipeContentDetection = v })
    val NIGHT = BoolSetting("night", "Nachtmodus: sofort Stufe 3 und Graustufen", false,
        { it.nightEnabled }, { p, v -> p.nightEnabled = v })
    val MORNING = BoolSetting("morning", "Morgensperre", false,
        { it.morningBlockEnabled }, { p, v -> p.morningBlockEnabled = v })
    val DAY_STRICT = BoolSetting("day_strict", "Tagsüber strenger als abends", false,
        { it.dayStrictEnabled }, { p, v -> p.dayStrictEnabled = v })
    val DOOMSCROLL = BoolSetting("doomscroll", "Doomscroll-Erkennung (braucht die Bedienungshilfe)", false,
        { it.doomscrollEnabled }, { p, v -> p.doomscrollEnabled = v })
    val WEEKLY_REPORT = BoolSetting("weekly_report", "Wochenbericht am Sonntagabend", null,
        { it.weeklyReport }, { p, v -> p.weeklyReport = v })

    val ints: Map<String, IntSetting> = listOf(
        SESSION_LIMIT, ESCALATION_STEP, DAILY_LIMIT, RESET_PAUSE, MAX_LEVEL, ADAPTIVE_PCT, YOUTUBE_LIMIT,
        CHECK_BUDGET, BURST_COUNT, BURST_WINDOW, BURST_PENALTY, SHORT_CHECK, COOKING_MIN, COOKING_PER_DAY,
        GUARD_MODE, NIGHT_START, NIGHT_END, MORNING_MIN, DAY_START, DAY_END, DAY_PCT, DOOM_FACTOR,
    ).associateBy { it.id }

    val bools: Map<String, BoolSetting> = listOf(
        ALWAYS_UGLY, ADAPTIVE, YOUTUBE_KICK, UNLOCK_NUDGE, GRAYSCALE, INVERSION, VIBRATE, UGLY_FONT, UGLY_ANIM,
        SHRINK, RECIPE_CONTENT, NIGHT, MORNING, DAY_STRICT, DOOMSCROLL, WEEKLY_REPORT,
    ).associateBy { it.id }
}

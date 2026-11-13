package app.checkerschreck

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Einstellungen (vom Nutzer) und Laufzeit-Zustand (vom Dienst), beides in SharedPreferences. */
class Prefs(context: Context) {
    private val s: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val st: SharedPreferences =
        context.applicationContext.getSharedPreferences("state", Context.MODE_PRIVATE)

    // ---------- Einstellungen ----------
    var monitoringEnabled by BoolPref(s, "monitoring_enabled", false)

    /** Ab so vielen Minuten am Stück wird es hässlich. */
    var sessionLimitMin by IntPref(s, "session_limit_min", 10)

    /** Alle X Minuten über dem Limit eine Stufe hässlicher. */
    var escalationStepMin by IntPref(s, "escalation_step_min", 5)

    /** Tageslimit: danach ist jede Nutzung sofort hässlich. */
    var dailyLimitMin by IntPref(s, "daily_limit_min", 120)

    /** So lange muss der Bildschirm aus sein, damit eine Session wirklich vorbei ist. */
    var resetPauseSec by IntPref(s, "reset_pause_sec", 120)

    /** Check-Attacke: so viele Checks ... */
    var burstCount by IntPref(s, "burst_count", 5)

    /** ... innerhalb so vieler Minuten ... */
    var burstWindowMin by IntPref(s, "burst_window_min", 15)

    /** ... geben so viele Minuten Strafe (sofort hässlich). */
    var burstPenaltyMin by IntPref(s, "burst_penalty_min", 10)

    /** Checks unter dieser Dauer zählen als Reflex-/Kurz-Check. */
    var shortCheckSec by IntPref(s, "short_check_sec", 60)

    var maxLevel by IntPref(s, "max_level", 3)

    /** Grundstufe 1: Das Handy ist bei jeder Nutzung mindestens Stufe 1 hässlich. */
    var alwaysUgly by BoolPref(s, "always_ugly", true)
    var useGrayscale by BoolPref(s, "use_grayscale", true)
    var useInversion by BoolPref(s, "use_inversion", false)
    var vibrate by BoolPref(s, "vibrate", true)
    var unlockNudge by BoolPref(s, "unlock_nudge", true)
    var recipeContentDetection by BoolPref(s, "recipe_content_detection", true)

    /** YouTube-Tageslimit in Minuten (0 = aus). */
    var youtubeLimitMin by IntPref(s, "youtube_limit_min", 20)

    /** Nach Überschreiten des YouTube-Limits zurück auf den Startbildschirm werfen. */
    var youtubeKick by BoolPref(s, "youtube_kick", true)

    /** Erlaubte Checks pro Tag (0 = aus). Darüber wird es eine Stufe hässlicher. */
    var checkBudget by IntPref(s, "check_budget", 0)

    /** Limits automatisch anhand der echten Nutzung zurückdrehen. */
    var adaptiveEnabled by BoolPref(s, "adaptive_enabled", true)

    /** Ziel: so viel Prozent unter dem 7-Tage-Schnitt. */
    var adaptiveReductionPct by IntPref(s, "adaptive_reduction_pct", 10)

    /** Schummel-Schutz: 0 = aus, 1 = Satz abtippen, 2 = 24 Stunden warten. */
    var guardMode by IntPref(s, "guard_mode", 1)

    // Zeitfenster
    var nightEnabled by BoolPref(s, "night_enabled", true)
    /** Alte Einstellung (ein Beginn für alle Tage); dient noch als Startwert für den Wochenplan. */
    var nightStartHour by IntPref(s, "night_start_hour", 22)

    /** So lange dauert der Nachtmodus ab seinem Beginn. */
    var nightHours by IntPref(s, "night_hours", 8)

    /**
     * Wochenplan: Beginn des Nachtmodus in Minuten ab 0 Uhr des Wochentags [day] (0 = Montag).
     * Werte über 1440 liegen nach Mitternacht, z. B. 1500 = 1:00 Uhr am Folgetag.
     */
    fun nightStartMin(day: Int): Int = s.getInt("night_start_min_$day", nightStartHour * 60)

    fun setNightStartMin(day: Int, minutes: Int) = s.edit().putInt("night_start_min_$day", minutes).apply()

    /** So viele Minuten normale Nutzung sind nachts noch drin, bevor Stufe 3 gilt (0 = sofort). */
    var nightBudgetMin by IntPref(s, "night_budget_min", 15)

    /** So oft pro Woche darf der Nachtmodus im Voraus ohne Hürde verschoben werden. */
    var nightDelaysPerWeek by IntPref(s, "night_delays_per_week", 2)
    var morningBlockEnabled by BoolPref(s, "morning_block_enabled", true)
    var morningBlockMin by IntPref(s, "morning_block_min", 30)
    var dayStrictEnabled by BoolPref(s, "day_strict_enabled", true)
    var dayStartHour by IntPref(s, "day_start_hour", 8)
    var dayEndHour by IntPref(s, "day_end_hour", 18)

    /** Tagsüber gilt nur dieser Prozentsatz des Session-Limits. */
    var dayStrictPct by IntPref(s, "day_strict_pct", 50)

    // Doomscroll
    var doomscrollEnabled by BoolPref(s, "doomscroll_enabled", true)

    /** Beim Endlos-Scrollen läuft die Session-Uhr so viel schneller. */
    var doomscrollFactor by IntPref(s, "doomscroll_factor", 2)

    // Noch hässlicher
    var uglyFont by BoolPref(s, "ugly_font", true)
    var uglyAnimations by BoolPref(s, "ugly_animations", true)
    var shrinkScreen by BoolPref(s, "shrink_screen", true)
    var stickers by BoolPref(s, "stickers", true)

    var weeklyReport by BoolPref(s, "weekly_report", true)

    var cookingMinutes by IntPref(s, "cooking_minutes", 45)
    var cookingPerDay by IntPref(s, "cooking_per_day", 2)

    /** Vom Nutzer gewählte Ausnahme-Apps (Rezept-Apps, Navi, ...). */
    var extraExempt by SetPref(s, "extra_exempt")

    /** Automatisch erkannte Rezept-Apps, die der Nutzer wieder abgewählt hat. */
    var autoRecipeDisabled by SetPref(s, "auto_recipe_disabled")

    // ---------- Zustand ----------
    var dayKey by StringPref(st, "day_key", "")
    var dailyActiveMs by LongPref(st, "daily_active_ms", 0L)
    var checksToday by IntPref(st, "checks_today", 0)
    var youtubeMsToday by LongPref(st, "youtube_ms_today", 0L)

    /** 0 = noch nicht gewarnt, 1 = "noch 5 min" gemeldet, 2 = Limit gemeldet. */
    var youtubeWarned by IntPref(st, "youtube_warned", 0)
    var sessionActiveMs by LongPref(st, "session_active_ms", 0L)
    var sessionStart by LongPref(st, "session_start", 0L)
    var unlockedAt by LongPref(st, "unlocked_at", 0L)
    var screenActiveMs by LongPref(st, "screen_active_ms", 0L)
    var lastScreenOff by LongPref(st, "last_screen_off", 0L)
    var lastCheckAt by LongPref(st, "last_check_at", 0L)
    var cookingUntil by LongPref(st, "cooking_until", 0L)
    var cookingUsed by IntPref(st, "cooking_used", 0)
    var penaltyUntil by LongPref(st, "penalty_until", 0L)
    var penaltyLevel by IntPref(st, "penalty_level", 0)
    var maxLevelToday by IntPref(st, "max_level_today", 0)
    var longestSessionToday by LongPref(st, "longest_session_today", 0L)

    /** Erster (unvollständiger) Messtag, zählt nicht für den Schnitt. */
    var firstDayKey by StringPref(st, "first_day_key", "")
    var historyRaw by StringPref(st, "history", "")
    var adaptiveLastDropDay by LongPref(st, "adaptive_last_drop_day", 0L)
    var adaptiveReport by StringPref(st, "adaptive_report", "")
    var adaptiveReportDay by StringPref(st, "adaptive_report_day", "")
    var adaptiveReportPending by BoolPref(st, "adaptive_report_pending", false)

    // Schummel-Schutz
    var pendingRaw by StringPref(st, "pending", "")
    var serviceAlive by BoolPref(st, "service_alive", false)
    var lastAliveAt by LongPref(st, "last_alive_at", 0L)
    var backchargedToday by LongPref(st, "backcharged_today", 0L)
    var permAlertDay by StringPref(st, "perm_alert_day", "")
    var serviceStartsToday by IntPref(st, "service_starts_today", 0)

    // Zeitfenster
    var nightKey by StringPref(st, "night_key", "")
    var nightUsedMs by LongPref(st, "night_used_ms", 0L)
    var nightAlertKey by StringPref(st, "night_alert_key", "")
    var nightDelayKey by StringPref(st, "night_delay_key", "")
    var nightDelayMin by IntPref(st, "night_delay_min", 0)
    var nightDelayLog by StringPref(st, "night_delay_log", "")

    /** Überwachung pausiert bis zu diesem Zeitpunkt (läuft danach von selbst weiter). */
    var pausedUntil by LongPref(st, "paused_until", 0L)

    var morningBlockDay by StringPref(st, "morning_block_day", "")
    var morningBlockUntil by LongPref(st, "morning_block_until", 0L)

    var scrollMsToday by LongPref(st, "scroll_ms_today", 0L)

    // Ursprüngliche Schriftgröße und Animationen
    var fontApplied by IntPref(st, "font_applied", 0)
    var fontOrig by StringPref(st, "font_orig", "")
    var animApplied by IntPref(st, "anim_applied", 0)
    var animOrig by StringPref(st, "anim_orig", "")

    var lastReportDay by StringPref(st, "last_report_day", "")

    // Updates
    var lastUpdateCheck by LongPref(st, "last_update_check", 0L)
    var updateDismissedVersion by IntPref(st, "update_dismissed_version", 0)

    // Ursprüngliche Systemfarben (für Graustufen/Invertierung)
    var colorOrigSaved by BoolPref(st, "color_orig_saved", false)
    var origDaltEnabled by IntPref(st, "orig_dalt_enabled", 0)
    var origDalt by IntPref(st, "orig_dalt", -1)
    var origInv by IntPref(st, "orig_inv", 0)
    var grayApplied by BoolPref(st, "gray_applied", false)
    var invApplied by BoolPref(st, "inv_applied", false)
}

private class IntPref(val sp: SharedPreferences, val key: String, val def: Int) :
    ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getInt(key, def)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
        sp.edit().putInt(key, value).apply()
}

private class LongPref(val sp: SharedPreferences, val key: String, val def: Long) :
    ReadWriteProperty<Any, Long> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getLong(key, def)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) =
        sp.edit().putLong(key, value).apply()
}

private class BoolPref(val sp: SharedPreferences, val key: String, val def: Boolean) :
    ReadWriteProperty<Any, Boolean> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getBoolean(key, def)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) =
        sp.edit().putBoolean(key, value).apply()
}

private class StringPref(val sp: SharedPreferences, val key: String, val def: String) :
    ReadWriteProperty<Any, String> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getString(key, def) ?: def
    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) =
        sp.edit().putString(key, value).apply()
}

private class SetPref(val sp: SharedPreferences, val key: String) :
    ReadWriteProperty<Any, Set<String>> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Set<String> =
        sp.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Set<String>) =
        sp.edit().putStringSet(key, HashSet(value)).apply()
}

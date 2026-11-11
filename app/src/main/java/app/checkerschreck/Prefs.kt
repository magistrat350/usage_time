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
    var useGrayscale by BoolPref(s, "use_grayscale", true)
    var useInversion by BoolPref(s, "use_inversion", false)
    var vibrate by BoolPref(s, "vibrate", true)
    var unlockNudge by BoolPref(s, "unlock_nudge", true)
    var recipeContentDetection by BoolPref(s, "recipe_content_detection", true)

    /** YouTube-Tageslimit in Minuten (0 = aus). */
    var youtubeLimitMin by IntPref(s, "youtube_limit_min", 20)

    /** Nach Überschreiten des YouTube-Limits zurück auf den Startbildschirm werfen. */
    var youtubeKick by BoolPref(s, "youtube_kick", true)

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

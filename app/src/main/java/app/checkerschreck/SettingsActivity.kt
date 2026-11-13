package app.checkerschreck

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Einstellungen. Verschärfungen gelten sofort, Lockerungen laufen über den Schummel-Schutz
 * ([Guard]): Satz abtippen oder 24 Stunden warten.
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var p: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        val (scroll, col) = screen()
        col.text("Einstellungen", 28f, bold = true)

        val guard = col.card("Schummel-Schutz")
        slider(guard, Rules.GUARD_MODE, 0, 2)
        note(
            guard,
            "Gilt für alles, was lockert: Limits hochsetzen, Funktionen ausschalten, Überwachung stoppen, " +
                "neue Ausnahme-Apps. „Satz abtippen“: vorher einen langen Satz fehlerfrei abtippen. " +
                "„24 Stunden warten“: die Lockerung wird eingeplant und wirkt erst einen Tag später. " +
                "Strenger stellen geht immer sofort.",
        )

        val limits = col.card("Limits")
        switch(limits, Rules.ALWAYS_UGLY)
        note(limits, "An: Jede Nutzung ist Stufe 1, nach dem Limit Stufe 2, dann Stufe 3. Aus: Bis zum Limit sieht das Handy normal aus.")
        slider(limits, Rules.SESSION_LIMIT, 1, 60)
        slider(limits, Rules.ESCALATION_STEP, 1, 30)
        slider(limits, Rules.DAILY_LIMIT, 15, 480, step = 15)
        slider(limits, Rules.RESET_PAUSE, 10, 600, step = 10)
        slider(limits, Rules.MAX_LEVEL, 1, 3)

        val times = col.card("Zeitfenster")
        switch(times, Rules.NIGHT)
        slider(times, Rules.NIGHT_START, 18, 23)
        slider(times, Rules.NIGHT_END, 4, 10)
        slider(times, Rules.NIGHT_BUDGET, 0, 60, step = 5)
        slider(times, Rules.NIGHT_DELAYS, 0, 7)
        note(
            times,
            "Nachts ist erst das Nacht-Budget an normaler Nutzung drin, danach gilt Stufe 3 bis zum Morgen " +
                "(0 = sofort Stufe 3). Telefonate und Ausnahmen zehren nicht am Budget. Für späte Abende gibt es " +
                "auf dem Hauptbildschirm „Nachtmodus heute später“: vor Beginn der Nacht geplant ist das so oft " +
                "pro Woche frei wie hier eingestellt, spontan kostet es einen abgetippten Satz.",
        )
        switch(times, Rules.MORNING)
        slider(times, Rules.MORNING_MIN, 5, 120, step = 5)
        note(
            times,
            "Morgensperre: Nach dem ersten Entsperren zwischen 4 und 12 Uhr liegt ein Vollbild über allem. " +
                "Telefon, Wecker und Ausnahme-Apps gehen weiter, für Notfälle gibt es eine Freigabe.",
        )
        switch(times, Rules.DAY_STRICT)
        slider(times, Rules.DAY_START, 5, 12)
        slider(times, Rules.DAY_END, 13, 22)
        slider(times, Rules.DAY_PCT, 25, 100, step = 5)

        val auto = col.card("Automatisch zurückdrehen")
        switch(auto, Rules.ADAPTIVE)
        slider(auto, Rules.ADAPTIVE_PCT, 5, 30, step = 5)
        note(
            auto,
            "Jeden Tag um Mitternacht wird deine echte Nutzung der letzten 7 Tage ausgewertet. Tageslimit, " +
                "Session-Limit, YouTube-Limit und Check-Budget werden auf deinen Schnitt minus diesen Prozentsatz " +
                "gesetzt, aber nur, wenn das strenger ist als bisher (höchstens 10 % pro Tag). Lockerer wird " +
                "nichts von allein; bewegt sich eine Woche nichts, wird trotzdem um 5 % gedreht. Untergrenzen: " +
                "${Adaptive.FLOOR_DAILY_MIN} min am Tag, ${Adaptive.FLOOR_SESSION_MIN} min Session, " +
                "${Adaptive.FLOOR_YOUTUBE_MIN} min YouTube, ${Adaptive.FLOOR_CHECKS} Checks.",
        )

        val yt = col.card("YouTube")
        slider(yt, Rules.YOUTUBE_LIMIT, 0, 120, step = 5)
        switch(yt, Rules.YOUTUBE_KICK)
        note(
            yt,
            "5 min vorher kommt eine Warnung. Ab dem Limit ist YouTube sofort Hässlich-Stufe 2, " +
                "alle weiteren 5 min eine Stufe mehr. Im Kochmodus oder bei erkanntem Rezept zählt die Zeit nicht.",
        )

        val checks = col.card("Check-Analyse")
        slider(checks, Rules.CHECK_BUDGET, 0, 200, step = 5)
        slider(checks, Rules.BURST_COUNT, 2, 20)
        slider(checks, Rules.BURST_WINDOW, 5, 60)
        slider(checks, Rules.BURST_PENALTY, 1, 60)
        slider(checks, Rules.SHORT_CHECK, 10, 180, step = 10)
        switch(checks, Rules.UNLOCK_NUDGE)
        switch(checks, Rules.WEEKLY_REPORT)

        val scrolling = col.card("Doomscroll")
        switch(scrolling, Rules.DOOMSCROLL)
        slider(scrolling, Rules.DOOM_FACTOR, 2, 4)
        note(
            scrolling,
            "Wer in 2 Minuten mehr als 45 Sekunden scrollt, scrollt endlos. Dann läuft die Session-Uhr schneller " +
                "und die nächste Stufe kommt früher. Ruhiges Lesen zählt normal.",
        )

        val ugly = col.card("Hässlichkeit")
        switch(ugly, Rules.SHRINK)
        note(ugly, "Schwarze Balken an allen Rändern, die pro Minute wachsen (bis 12 % oben und unten). Sie schlucken Berührungen.")
        switch(ugly, Rules.STICKERS)
        note(ugly, "Drei (Stufe 2) bzw. sechs (Stufe 3) Flecken, die alle 9 Sekunden woanders kleben und Berührungen schlucken.")
        switch(ugly, Rules.UGLY_FONT)
        note(ugly, "Braucht „Systemeinstellungen ändern“ (auf dem Hauptbildschirm erlauben, ohne PC).")
        switch(ugly, Rules.UGLY_ANIM)
        switch(ugly, Rules.GRAYSCALE)
        switch(ugly, Rules.INVERSION)
        switch(ugly, Rules.VIBRATE)

        val recipe = col.card("Rezepte & Kochen")
        switch(recipe, Rules.RECIPE_CONTENT)
        slider(recipe, Rules.COOKING_MIN, 10, 120, step = 5)
        slider(recipe, Rules.COOKING_PER_DAY, 0, 6)

        setContentView(scroll)
    }

    private fun note(parent: LinearLayout, s: String) {
        parent.text(s, 12f, topDp = 4, alpha = 0.7f)
    }

    private fun slider(parent: LinearLayout, setting: IntSetting, min: Int, max: Int, step: Int = 1) {
        val label = parent.text("", 14f, topDp = 12)
        fun show(v: Int) {
            label.text = "${setting.title}: ${setting.show(v)}"
        }
        var committed = setting.get(p)
        show(committed)
        fun toProgress(v: Int) = (v.coerceIn(min, max) - min) / step
        val sb = SeekBar(this)
        sb.max = (max - min) / step
        sb.progress = toProgress(committed)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) show(min + progress * step)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val v = min + sb.progress * step
                if (v == committed) return
                if (!setting.isLooser(committed, v)) {
                    setting.set(p, v)
                    committed = v
                    return
                }
                Guard.request(this@SettingsActivity, p, Guard.intChange(setting, committed, v)) { applied ->
                    if (applied) {
                        committed = v
                    } else {
                        sb.progress = toProgress(committed)
                        show(committed)
                    }
                }
            }
        })
        parent.addView(sb, LinearLayout.LayoutParams(-1, -2))
    }

    private fun switch(parent: LinearLayout, setting: BoolSetting) {
        val sw = MaterialSwitch(this)
        sw.text = setting.title
        sw.isChecked = setting.get(p)
        var internal = false
        sw.setOnCheckedChangeListener { _, checked ->
            if (internal) return@setOnCheckedChangeListener
            if (!setting.isLooser(checked)) {
                setting.set(p, checked)
                return@setOnCheckedChangeListener
            }
            // Lockerung: Schalter erst einmal zurück, bis der Schummel-Schutz zugestimmt hat
            internal = true
            sw.isChecked = !checked
            internal = false
            Guard.request(this, p, Guard.boolChange(setting, checked)) { applied ->
                if (applied) {
                    internal = true
                    sw.isChecked = checked
                    internal = false
                }
            }
        }
        parent.addView(sw, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
}

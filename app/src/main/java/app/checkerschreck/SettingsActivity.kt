package app.checkerschreck

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {
    private lateinit var p: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        val (scroll, col) = screen()
        col.text("Einstellungen", 28f, bold = true)

        val limits = col.card("Limits")
        slider(limits, "Hässlich ab (am Stück)", "min", 1, 60, { p.sessionLimitMin }) { p.sessionLimitMin = it }
        slider(limits, "Eine Stufe hässlicher alle", "min", 1, 30, { p.escalationStepMin }) { p.escalationStepMin = it }
        slider(limits, "Tageslimit (danach sofort hässlich)", "min", 15, 480, { p.dailyLimitMin }, step = 15) {
            p.dailyLimitMin = it
        }
        slider(limits, "Session zählt als beendet nach Bildschirm-aus von", "s", 10, 600, { p.resetPauseSec }, step = 10) {
            p.resetPauseSec = it
        }
        slider(limits, "Maximale Hässlich-Stufe", "", 1, 3, { p.maxLevel }) { p.maxLevel = it }

        val checks = col.card("Check-Analyse")
        slider(checks, "Check-Attacke ab", "Checks", 2, 20, { p.burstCount }) { p.burstCount = it }
        slider(checks, "… innerhalb von", "min", 5, 60, { p.burstWindowMin }) { p.burstWindowMin = it }
        slider(checks, "Strafe bei Check-Attacke", "min", 1, 60, { p.burstPenaltyMin }) { p.burstPenaltyMin = it }
        slider(checks, "Reflex-Check, wenn kürzer als", "s", 10, 180, { p.shortCheckSec }, step = 10) {
            p.shortCheckSec = it
        }
        switch(checks, "Check-Zähler beim Entsperren anzeigen", { p.unlockNudge }) { p.unlockNudge = it }

        val ugly = col.card("Hässlichkeit")
        switch(ugly, "Graustufen ab Stufe 2 (braucht adb-Freigabe)", { p.useGrayscale }) { p.useGrayscale = it }
        switch(ugly, "Farben invertieren ab Stufe 3 (braucht adb-Freigabe)", { p.useInversion }) { p.useInversion = it }
        switch(ugly, "Nerv-Vibration ab Stufe 3", { p.vibrate }) { p.vibrate = it }

        val recipe = col.card("Rezepte & Kochen")
        switch(recipe, "Rezepte auf dem Bildschirm erkennen (Bedienungshilfe)", { p.recipeContentDetection }) {
            p.recipeContentDetection = it
        }
        slider(recipe, "Kochmodus-Dauer", "min", 10, 120, { p.cookingMinutes }, step = 5) { p.cookingMinutes = it }
        slider(recipe, "Kochmodus pro Tag", "×", 0, 6, { p.cookingPerDay }) { p.cookingPerDay = it }

        setContentView(scroll)
    }

    private fun slider(
        parent: LinearLayout,
        title: String,
        unit: String,
        min: Int,
        max: Int,
        get: () -> Int,
        step: Int = 1,
        set: (Int) -> Unit,
    ) {
        val label = parent.text("", 14f, topDp = 12)
        fun show(v: Int) {
            label.text = "$title: $v $unit".trimEnd()
        }
        val start = get().coerceIn(min, max)
        show(start)
        val sb = SeekBar(this).apply {
            this.max = (max - min) / step
            progress = (start - min) / step
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = min + progress * step
                    show(v)
                    if (fromUser) set(v)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(sb, LinearLayout.LayoutParams(-1, -2))
    }

    private fun switch(parent: LinearLayout, title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = MaterialSwitch(this).apply {
            text = title
            isChecked = get()
            setOnCheckedChangeListener { _, checked -> set(checked) }
        }
        parent.addView(sw, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
}

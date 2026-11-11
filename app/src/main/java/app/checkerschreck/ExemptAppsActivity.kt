package app.checkerschreck

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.checkbox.MaterialCheckBox
import java.util.Locale

/** Auswahl der Apps, in denen nie gemeckert wird (Rezept-Apps, Navi, ...). */
class ExemptAppsActivity : AppCompatActivity() {
    private data class AppEntry(val pkg: String, val label: String, val auto: Boolean)

    private lateinit var p: Prefs
    private lateinit var list: LinearLayout
    private var apps: List<AppEntry> = emptyList()
    private var filter = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        val (scroll, col) = screen()
        col.text("Rezept- & Ausnahme-Apps", 26f, bold = true)
        col.text(
            "In diesen Apps wird die Zeit nicht gezählt und das Handy nie hässlich. " +
                "Rezept-Apps werden automatisch erkannt (z. B. Chefkoch, Kitchen Stories, Cookidoo). " +
                "Rezepte im Browser erkennt die optionale Bedienungshilfe.",
            14f, topDp = 4, alpha = 0.8f,
        )
        val search = EditText(this).apply {
            hint = "App suchen …"
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filter = s?.toString()?.lowercase(Locale.GERMAN) ?: ""
                    render()
                }
            })
        }
        col.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        list.text("Lade Apps …", 14f, topDp = 12)
        setContentView(scroll)

        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val loaded = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != packageName }
                .map { AppEntry(it, Analyzer.appLabel(this, it), RecipeDetector.isAutoRecipeApp(this, it)) }
            runOnUiThread {
                apps = loaded.sortedWith(
                    compareByDescending<AppEntry> { isChecked(it) }.thenBy { it.label.lowercase(Locale.GERMAN) },
                )
                render()
            }
        }.start()
    }

    private fun isChecked(a: AppEntry) =
        a.pkg in p.extraExempt || (a.auto && a.pkg !in p.autoRecipeDisabled)

    private fun render() {
        list.removeAllViews()
        for (a in apps) {
            if (filter.isNotEmpty() && !a.label.lowercase(Locale.GERMAN).contains(filter)) continue
            val cb = MaterialCheckBox(this).apply {
                text = if (a.auto) "${a.label}  🥕 automatisch als Rezept-App erkannt" else a.label
                isChecked = isChecked(a)
                setOnCheckedChangeListener { _, checked -> toggle(a, checked) }
            }
            list.addView(cb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
    }

    private fun toggle(a: AppEntry, checked: Boolean) {
        if (a.auto) {
            p.autoRecipeDisabled = if (checked) p.autoRecipeDisabled - a.pkg else p.autoRecipeDisabled + a.pkg
            if (checked) p.extraExempt = p.extraExempt - a.pkg
        } else {
            p.extraExempt = if (checked) p.extraExempt + a.pkg else p.extraExempt - a.pkg
        }
    }
}

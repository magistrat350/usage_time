package app.checkerschreck

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Eine Lockerung, die der Schummel-Schutz prüft. */
data class Change(val kind: String, val id: String, val value: String, val label: String)

data class Pending(val dueAt: Long, val change: Change)

/**
 * Schummel-Schutz: Lockerungen (Limit hoch, Überwachung aus, neue Ausnahme-App ...) gehen nur
 * mit Hürde durch. Je nach Modus muss man einen langen Satz abtippen oder 24 Stunden warten.
 * Verschärfungen gelten immer sofort.
 */
object Guard {
    const val OFF = 0
    const val SENTENCE = 1
    const val DELAY = 2
    const val DELAY_MS = 24 * 3600_000L
    val MODE_NAMES = listOf("Aus", "Satz abtippen", "24 Stunden warten")

    private const val SEP = '\u001F'

    private val SENTENCES = listOf(
        "Ich lockere jetzt mit voller Absicht meine eigenen Regeln, obwohl ich sie mir aus gutem Grund selbst gesetzt habe.",
        "Ich habe wirklich darüber nachgedacht und will das trotzdem aufweichen, auch wenn ich es morgen bereuen könnte.",
        "Das Handy läuft mir nicht weg, und trotzdem bin ich gerade dabei, mir selbst mehr Bildschirmzeit zu genehmigen.",
        "Niemand zwingt mich dazu, ich entscheide mich in diesem Moment ganz bewusst gegen meinen eigenen Vorsatz.",
    )

    fun intChange(s: IntSetting, old: Int, new: Int) =
        Change("int", s.id, new.toString(), "${s.title}: ${s.show(old)} → ${s.show(new)}")

    fun boolChange(s: BoolSetting, new: Boolean) =
        Change("bool", s.id, new.toString(), "${s.title}: ${if (new) "an" else "aus"}")

    fun stopChange() = Change("stop", "", "", "Überwachung stoppen")

    fun exemptChange(pkg: String, label: String) = Change("exempt", "", pkg, "Ausnahme-App hinzufügen: $label")

    fun autoChange(pkg: String, label: String) = Change("auto", "", pkg, "Rezept-App wieder zulassen: $label")

    /** Führt die Änderung wirklich aus. */
    fun perform(ctx: Context, p: Prefs, c: Change) {
        when (c.kind) {
            "int" -> c.value.toIntOrNull()?.let { v -> Rules.ints[c.id]?.set?.invoke(p, v) }
            "bool" -> Rules.bools[c.id]?.set?.invoke(p, c.value == "true")
            "exempt" -> p.extraExempt = p.extraExempt + c.value
            "auto" -> p.autoRecipeDisabled = p.autoRecipeDisabled - c.value
            "stop" -> MonitorService.stop(ctx)
        }
    }

    /**
     * Bittet um eine Lockerung. [onDone] bekommt true, wenn sie sofort ausgeführt wurde,
     * und false, wenn sie abgebrochen oder nur eingeplant wurde.
     */
    fun request(activity: Activity, p: Prefs, c: Change, onDone: (Boolean) -> Unit) {
        when (p.guardMode) {
            OFF -> {
                perform(activity, p, c)
                onDone(true)
            }
            SENTENCE -> typeSentence(
                activity,
                "Wirklich lockern?",
                "Du willst lockern:\n${c.label}\n\nTippe dafür diesen Satz ab:",
                onOk = {
                    perform(activity, p, c)
                    onDone(true)
                },
                onCancel = { onDone(false) },
            )
            else -> MaterialAlertDialogBuilder(activity)
                .setTitle("Erst in 24 Stunden")
                .setMessage(
                    "${c.label}\n\nDer Schummel-Schutz lässt Lockerungen erst nach 24 Stunden wirksam werden. " +
                        "Bis dahin kannst du sie auf dem Hauptbildschirm jederzeit verwerfen.",
                )
                .setPositiveButton("Einplanen") { _, _ ->
                    schedule(p, c, System.currentTimeMillis())
                    Toast.makeText(activity, "Eingeplant. Wirkt in 24 Stunden.", Toast.LENGTH_LONG).show()
                    onDone(false)
                }
                .setNegativeButton("Abbrechen") { _, _ -> onDone(false) }
                .setOnCancelListener { onDone(false) }
                .show()
        }
    }

    /** Dialog, der erst weitergeht, wenn ein zufälliger langer Satz korrekt abgetippt wurde. */
    fun typeSentence(activity: Activity, title: String, message: String, onOk: () -> Unit, onCancel: () -> Unit) {
        val sentence = SENTENCES.random()
        val target = normalize(sentence)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
        }
        box.text(message, 14f)
        box.text("„$sentence“", 15f, bold = true, topDp = 10)
        val noMenu = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 3
            hint = "Hier abtippen (Einfügen geht nicht)"
            isLongClickable = false
            customSelectionActionModeCallback = noMenu
            if (Build.VERSION.SDK_INT >= 23) customInsertionActionModeCallback = noMenu
        }
        box.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(8) })
        val status = box.text("Noch ${target.length} Zeichen", 12f, topDp = 4, alpha = 0.7f)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Bestätigen") { _, _ -> onOk() }
            .setNegativeButton("Abbrechen") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.show()
        val ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        ok.isEnabled = false
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val typed = normalize(s?.toString() ?: "")
                ok.isEnabled = typed == target
                status.text = when {
                    typed == target -> "✓ Stimmt"
                    target.startsWith(typed) -> "Noch ${target.length - typed.length} Zeichen"
                    else -> "✗ Tippfehler – vergleiche noch einmal"
                }
            }
        })
    }

    private fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase()

    // ---------------------------------------------------------------- eingeplante Lockerungen

    fun pending(p: Prefs): List<Pending> = p.pendingRaw.lines().mapNotNull { line ->
        val f = line.split(SEP)
        if (f.size != 5) return@mapNotNull null
        Pending(f[0].toLongOrNull() ?: return@mapNotNull null, Change(f[1], f[2], f[3], f[4]))
    }

    private fun save(p: Prefs, list: List<Pending>) {
        p.pendingRaw = list.joinToString("\n") {
            listOf(it.dueAt.toString(), it.change.kind, it.change.id, it.change.value, it.change.label)
                .joinToString(SEP.toString())
        }
    }

    fun schedule(p: Prefs, c: Change, now: Long) {
        // Eine neue Planung für dieselbe Einstellung ersetzt die alte
        val rest = pending(p).filterNot {
            it.change.kind == c.kind && it.change.id == c.id && (c.kind in setOf("int", "bool", "stop") || it.change.value == c.value)
        }
        save(p, rest + Pending(now + DELAY_MS, c))
    }

    fun cancel(p: Prefs, item: Pending) = save(p, pending(p).filterNot { it == item })

    /** Führt fällige Lockerungen aus und gibt ihre Beschreibungen zurück. */
    fun applyDue(ctx: Context, p: Prefs, now: Long): List<String> {
        val all = pending(p)
        val due = all.filter { it.dueAt <= now }
        if (due.isEmpty()) return emptyList()
        save(p, all - due.toSet())
        due.forEach { perform(ctx, p, it.change) }
        return due.map { it.change.label }
    }
}

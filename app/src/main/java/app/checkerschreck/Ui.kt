package app.checkerschreck

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

/** Hält Inhalte aus Status- und Navigationsleiste heraus (Android 15 erzwingt Edge-to-Edge). */
fun View.padForSystemBars() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
        insets
    }
}

/** Scrollbarer Bildschirm mit vertikaler Spalte. */
fun Context.screen(): Pair<ScrollView, LinearLayout> {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(24))
    }
    val scroll = ScrollView(this).apply {
        clipToPadding = false
        addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        padForSystemBars()
    }
    return scroll to col
}

fun LinearLayout.text(
    s: CharSequence = "",
    sizeSp: Float = 15f,
    bold: Boolean = false,
    topDp: Int = 0,
    alpha: Float = 1f,
): TextView {
    val tv = TextView(context).apply {
        text = s
        textSize = sizeSp
        if (bold) setTypeface(typeface, Typeface.BOLD)
        this.alpha = alpha
        setLineSpacing(0f, 1.15f)
    }
    addView(tv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(topDp) })
    return tv
}

fun LinearLayout.button(label: String, outlined: Boolean = false, onClick: () -> Unit): MaterialButton {
    val b = if (outlined) {
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    } else MaterialButton(context)
    b.text = label
    b.isAllCaps = false
    b.setOnClickListener { onClick() }
    addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
    return b
}

fun LinearLayout.card(title: String? = null): LinearLayout {
    val inner = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
    }
    if (title != null) inner.text(title, 18f, bold = true)
    val card = MaterialCardView(context).apply {
        radius = context.dp(16).toFloat()
        cardElevation = 0f
        strokeWidth = context.dp(1)
        strokeColor = Color.argb(70, 128, 128, 128)
        addView(inner)
    }
    addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
    return inner
}

fun LinearLayout.row(): LinearLayout {
    val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    addView(r, LinearLayout.LayoutParams(-1, -2))
    return r
}

/** Kachel mit großer Zahl und kleiner Beschriftung. */
class StatTile(parent: LinearLayout, label: String) {
    val value: TextView
    init {
        val box = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, parent.context.dp(8), parent.context.dp(8), parent.context.dp(8))
        }
        value = box.text("–", 24f, bold = true)
        box.text(label, 12f, alpha = 0.7f)
        parent.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
    }
}

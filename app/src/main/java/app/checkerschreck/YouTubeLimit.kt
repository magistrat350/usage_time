package app.checkerschreck

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent

/** Tageslimit für YouTube. */
object YouTubeLimit {
    val PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "app.revanced.android.youtube",
        "com.vanced.android.youtube",
    )

    /** Ab dem Limit: Stufe 2, alle weiteren 5 min eine Stufe mehr. */
    private const val STEP_MS = 5 * 60_000L

    fun isYouTube(pkg: String?) = pkg != null && pkg in PACKAGES

    fun limitMs(p: Prefs) = p.youtubeLimitMin * 60_000L

    fun enabled(p: Prefs) = p.youtubeLimitMin > 0

    fun isOver(p: Prefs, ytMs: Long) = enabled(p) && ytMs >= limitMs(p)

    fun level(p: Prefs, ytMs: Long): Int {
        if (!isOver(p, ytMs)) return 0
        return 2 + ((ytMs - limitMs(p)) / STEP_MS).toInt()
    }

    /** Zurück auf den Startbildschirm. Über die Bedienungshilfe, sonst per Home-Intent. */
    fun kick(ctx: Context) {
        val a11y = RecipeAccessibilityService.instance
        if (a11y != null && a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) return
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

package app.checkerschreck

import android.content.Context
import java.util.Locale

/** Entscheidet, ob gerade eine Ausnahme gilt (Rezept offen, Kochmodus, Telefonat, ...). */
object RecipeDetector {
    /** Taucht eines dieser Wörter im App-Namen oder Paketnamen auf, gilt die App als Rezept-App. */
    val APP_KEYWORDS = listOf(
        "rezept", "recipe", "chefkoch", "kochbuch", "cookbook", "kitchen stories", "küche",
        "cookidoo", "thermomix", "backrezept", "eat smarter", "lecker", "mealime", "paprika",
    )

    /** So viele verschiedene Rezept-Wörter müssen zu sehen sein. */
    const val HITS_TO_DETECT = 3

    /** So viele müssen zu sehen bleiben, damit ein erkanntes Rezept weiter gilt. */
    const val HITS_TO_KEEP = 2

    /**
     * Feeds, Videos und Chats: Dort stehen Rezept-Wörter ständig in Beschreibungen und Nachrichten,
     * ohne dass wirklich gekocht wird. Wer dort kocht, nimmt den Kochmodus oder die Ausnahme-Liste.
     */
    private val NO_CONTENT_DETECTION = YouTubeLimit.PACKAGES + setOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.twitter.android",
        "com.reddit.frontpage", "com.snapchat.android", "com.pinterest", "com.linkedin.android",
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.discord", "tv.twitch.android.app",
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.android.systemui",
    )

    fun allowsContentDetection(pkg: String) = pkg !in NO_CONTENT_DETECTION

    /** Wörter, die auf einer Rezeptseite stehen. */
    val CONTENT_KEYWORDS = listOf(
        "zutaten", "zubereitung", "portionen", "arbeitszeit", "kochzeit", "backzeit",
        "ruhezeit", "gesamtzeit", "schwierigkeitsgrad", "rezept", "esslöffel", "teelöffel",
        "ingredients", "servings", "prep time", "cook time",
        "recipe", "tablespoon", "teaspoon", "chefkoch.de",
    )

    /** Apps, bei denen nie gemeckert wird. */
    private val ALWAYS_EXEMPT = setOf(
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.android.incallui", "com.android.server.telecom",
        // Wecker
        "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
    )

    private val labelCache = HashMap<String, Boolean>()

    @Volatile private var contentRecipePkg: String? = null
    @Volatile private var contentRecipeAt = 0L
    private const val CONTENT_VALID_MS = 45_000L

    /** Vom Bedienungshilfe-Dienst: Rezept auf dem Bildschirm gesehen. */
    fun markContentRecipe(pkg: String, now: Long) {
        contentRecipePkg = pkg
        contentRecipeAt = now
    }

    fun clearContentRecipe(pkg: String) {
        if (contentRecipePkg == pkg) contentRecipeAt = 0L
    }

    fun isContentRecipeFresh(pkg: String, now: Long) =
        contentRecipePkg == pkg && now - contentRecipeAt < CONTENT_VALID_MS

    @Synchronized
    fun isAutoRecipeApp(ctx: Context, pkg: String): Boolean = labelCache.getOrPut(pkg) {
        val label = Analyzer.appLabel(ctx, pkg).lowercase(Locale.GERMAN)
        val p = pkg.lowercase(Locale.ROOT)
        APP_KEYWORDS.any { label.contains(it) } ||
            listOf("chefkoch", "recipe", "rezept", "cookidoo", "kitchenstories").any { p.contains(it) }
    }

    fun isRecipeApp(ctx: Context, prefs: Prefs, pkg: String) =
        isAutoRecipeApp(ctx, pkg) && pkg !in prefs.autoRecipeDisabled

    /** Feste Ausnahme (unabhängig vom Bildschirminhalt)? */
    fun isExemptPackage(ctx: Context, prefs: Prefs, pkg: String) =
        pkg == ctx.packageName || pkg in ALWAYS_EXEMPT || pkg in prefs.extraExempt || isRecipeApp(ctx, prefs, pkg)

    /** Grund für eine Ausnahme oder null, wenn ganz normal gezählt/bestraft wird. */
    fun exemptReason(ctx: Context, prefs: Prefs, fg: String?, now: Long): String? {
        if (prefs.cookingUntil > now) return "Kochmodus (noch ${fmtDuration(prefs.cookingUntil - now)})"
        if (fg == null) return null
        if (fg == ctx.packageName) return "Checker-Schreck selbst"
        if (fg in ALWAYS_EXEMPT) return "Telefon/Wecker"
        if (fg in prefs.extraExempt) return "Ausnahme-App"
        if (isRecipeApp(ctx, prefs, fg)) return "Rezept-App"
        if (prefs.recipeContentDetection && isContentRecipeFresh(fg, now)) return "Rezept geöffnet"
        return null
    }

    /** Zählt verschiedene Rezept-Wörter in einem Text. */
    fun countHits(text: String, into: MutableSet<String>) {
        val t = text.lowercase(Locale.GERMAN)
        for (k in CONTENT_KEYWORDS) if (t.contains(k)) into += k
    }
}

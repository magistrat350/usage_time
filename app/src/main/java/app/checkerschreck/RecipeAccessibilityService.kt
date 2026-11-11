package app.checkerschreck

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optionaler Dienst: schaut, ob auf dem Bildschirm ein Rezept zu sehen ist
 * (z. B. Chefkoch im Browser). Es wird nur gezählt, nichts gespeichert.
 */
class RecipeAccessibilityService : AccessibilityService() {
    private var lastScan = 0L

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) ScrollMeter.onScroll(now)
        if (now - lastScan < 2500) return
        scanNow()
    }

    fun scanNow() {
        val now = System.currentTimeMillis()
        lastScan = now
        runCatching {
            if (!Prefs(this).recipeContentDetection) return
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: return
            if (pkg == packageName || pkg == "com.android.systemui") return
            val hits = HashSet<String>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES && hits.size < 2) {
                val n = queue.removeFirst()
                visited++
                n.text?.let { RecipeDetector.countHits(it.toString(), hits) }
                n.contentDescription?.let { RecipeDetector.countHits(it.toString(), hits) }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            when {
                hits.size >= 2 -> RecipeDetector.markContentRecipe(pkg, now)
                // schon als Rezept erkannt und noch ein Treffer sichtbar (z. B. runtergescrollt): weiter gültig
                hits.size == 1 && RecipeDetector.isContentRecipeFresh(pkg, now) ->
                    RecipeDetector.markContentRecipe(pkg, now)
                hits.isEmpty() -> RecipeDetector.clearContentRecipe(pkg)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val MAX_NODES = 700

        @Volatile
        var instance: RecipeAccessibilityService? = null
    }
}

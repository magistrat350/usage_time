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
        Watchdog.ensureRunning(this)
        MonitorService.poke() // Overlay sofort über die Bedienungshilfe legen
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> ScrollMeter.onScroll(now)
            // App-Wechsel: sofort neu bewerten, statt auf den nächsten Takt zu warten
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> MonitorService.poke()
        }
        // Hat Android die Überwachung beendet, wird sie hier wieder gestartet
        Watchdog.ensureRunning(this)
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
            if (pkg == packageName || !RecipeDetector.allowsContentDetection(pkg)) return
            val hits = HashSet<String>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES && hits.size < RecipeDetector.HITS_TO_DETECT) {
                val n = queue.removeFirst()
                visited++
                n.text?.let { RecipeDetector.countHits(it.toString(), hits) }
                n.contentDescription?.let { RecipeDetector.countHits(it.toString(), hits) }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            when {
                hits.size >= RecipeDetector.HITS_TO_DETECT -> RecipeDetector.markContentRecipe(pkg, now)
                // schon als Rezept erkannt und noch genug davon sichtbar (z. B. runtergescrollt): weiter gültig
                hits.size >= RecipeDetector.HITS_TO_KEEP && RecipeDetector.isContentRecipeFresh(pkg, now) ->
                    RecipeDetector.markContentRecipe(pkg, now)
                hits.size < RecipeDetector.HITS_TO_KEEP -> RecipeDetector.clearContentRecipe(pkg)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        MonitorService.poke()
        return super.onUnbind(intent)
    }

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

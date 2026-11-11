package app.checkerschreck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val p = Prefs(context)
                // Falls der Dienst vorher abgestürzt ist: Farben zurücksetzen
                SystemColorHack.restore(context, p)
                SystemTweaks.restore(context, p)
                if (p.monitoringEnabled) runCatching { MonitorService.start(context) }
            }
        }
    }
}

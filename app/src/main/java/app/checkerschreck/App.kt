package app.checkerschreck

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Überwachung", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "Dauerhafte Anzeige, solange Checker-Schreck aufpasst"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Check-Alarme", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Meldet Check-Attacken und überschrittene Limits"
            },
        )
    }

    companion object {
        const val CH_MONITOR = "monitor"
        const val CH_ALERT = "alerts"
    }
}

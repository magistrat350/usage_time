package app.checkerschreck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Startbildschirm-Widget: Checks und Restzeit, ohne die App zu öffnen. */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context)
    }

    companion object {
        fun render(ctx: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx) ?: return
                val cn = ComponentName(ctx, WidgetProvider::class.java)
                if (mgr.getAppWidgetIds(cn).isEmpty()) return
                val p = Prefs(ctx)
                val now = System.currentTimeMillis()
                val today = p.dayKey == Analyzer.dayKey(now)
                val snap = MonitorService.snapshot
                val checks = if (today) p.checksToday else 0
                val dailyMs = snap?.dailyMs ?: if (today) p.dailyActiveMs else 0L
                val ytMs = snap?.youtubeMs ?: if (today) p.youtubeMsToday else 0L

                val v = RemoteViews(ctx.packageName, R.layout.widget)
                v.setTextViewText(
                    R.id.w_checks,
                    if (p.checkBudget > 0) "$checks / ${p.checkBudget} Checks" else "$checks Checks",
                )
                val dailyLeft = p.dailyLimitMin * 60_000L - dailyMs
                v.setTextViewText(
                    R.id.w_daily,
                    if (dailyLeft > 0) "Heute ${fmtDuration(dailyMs)} · noch ${fmtDuration(dailyLeft)}"
                    else "Heute ${fmtDuration(dailyMs)} · Tageslimit überschritten",
                )
                v.setTextViewText(
                    R.id.w_session,
                    when {
                        !MonitorService.running -> "Überwachung aus"
                        snap == null -> "Passt auf 👀"
                        snap.exempt != null -> "Pause: ${snap.exempt}"
                        snap.night -> "🌙 Nachtmodus: Stufe 3"
                        snap.nightLeftMs > 0 -> "🌙 Nacht-Budget: noch ${fmtDuration(snap.nightLeftMs + 59_000)}"
                        else -> "Session ${fmtClock(snap.sessionMs)} · Stufe ${snap.level}"
                    },
                )
                v.setTextViewText(
                    R.id.w_youtube,
                    if (p.youtubeLimitMin <= 0) "" else {
                        val left = p.youtubeLimitMin * 60_000L - ytMs
                        if (left > 0) "📺 YouTube: noch ${fmtDuration(left)}" else "📺 YouTube: aufgebraucht"
                    },
                )
                v.setOnClickPendingIntent(
                    R.id.w_root,
                    PendingIntent.getActivity(
                        ctx, 3, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                mgr.updateAppWidget(cn, v)
            }
        }
    }
}

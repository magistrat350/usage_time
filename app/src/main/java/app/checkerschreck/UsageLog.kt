package app.checkerschreck

import android.content.Context
import java.io.File

/** Eine Nutzungs-Session: vom Entsperren bis Bildschirm aus. */
data class Session(val start: Long, val end: Long, val activeMs: Long) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0)
}

/**
 * Einfaches Textprotokoll im App-Speicher:
 *   C <ts> [auslöser]         ein Check (Entsperren); Auslöser = Paketname der Benachrichtigung
 *                             davor, "-" = ohne Anlass, fehlt = unbekannt
 *   S <start> <end> <active>  eine beendete Session
 */
object UsageLog {
    private const val FILE = "usage_log.txt"
    private const val KEEP_DAYS = 30L

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun addCheck(ctx: Context, ts: Long, trigger: String? = null) =
        append(ctx, if (trigger == null) "C $ts" else "C $ts $trigger")

    @Synchronized
    fun addSession(ctx: Context, s: Session) = append(ctx, "S ${s.start} ${s.end} ${s.activeMs}")

    private fun append(ctx: Context, line: String) {
        runCatching { file(ctx).appendText(line + "\n") }
    }

    data class Data(val checks: List<Long>, val sessions: List<Session>, val triggers: Map<Long, String>)

    @Synchronized
    fun read(ctx: Context, since: Long): Data {
        val checks = ArrayList<Long>()
        val sessions = ArrayList<Session>()
        val triggers = HashMap<Long, String>()
        val f = file(ctx)
        if (!f.exists()) return Data(checks, sessions, triggers)
        runCatching {
            f.forEachLine { line ->
                val p = line.split(' ')
                when {
                    (p.size == 2 || p.size == 3) && p[0] == "C" -> p[1].toLongOrNull()?.let {
                        if (it >= since) {
                            checks += it
                            if (p.size == 3) triggers[it] = p[2]
                        }
                    }
                    p.size == 4 && p[0] == "S" -> {
                        val s = p[1].toLongOrNull()
                        val e = p[2].toLongOrNull()
                        val a = p[3].toLongOrNull()
                        if (s != null && e != null && a != null && e >= since) sessions += Session(s, e, a)
                    }
                }
            }
        }
        checks.sort()
        sessions.sortBy { it.start }
        return Data(checks, sessions, triggers)
    }

    @Synchronized
    fun checksSince(ctx: Context, since: Long): Int = read(ctx, since).checks.size

    /** Entfernt Einträge älter als [KEEP_DAYS] Tage. */
    @Synchronized
    fun prune(ctx: Context, now: Long) {
        val f = file(ctx)
        if (!f.exists()) return
        val cutoff = now - KEEP_DAYS * 24 * 3600_000L
        runCatching {
            val kept = f.readLines().filter { line ->
                val ts = line.split(' ').getOrNull(1)?.toLongOrNull() ?: return@filter false
                ts >= cutoff
            }
            f.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
        }
    }
}

package dev.pinch.app.data

import android.content.Context
import dev.pinch.app.Accounts
import java.io.File

/**
 * Usage over time, kept on this phone only: one point per fetch that changed something,
 * for the app's history charts. Anthropic's endpoint gives no history, so it starts the day
 * Pinch is installed. Plain numbers in app-private storage, excluded from backups.
 */
object History {

    data class Point(
        val t: Long,
        val session: Int?,
        val week: Int?,
        val sessionReset: Long?,
        val weekReset: Long?
    )

    /** One 5-hour window (or one week): when it ended (or ends) and the most it reached. */
    data class Window(val endsAt: Long, val peak: Int)

    private const val FILE = "history.csv"
    private const val KEEP_MS = 35L * 24 * 3_600_000
    /** An unchanged reading is still kept this often, so the chart knows the phone was watching. */
    private const val SAME_EVERY_MS = 30L * 60_000
    const val SESSION_MS = 5L * 3_600_000

    private val lock = Any()

    fun record(context: Context, account: String, u: Usage, now: Long = u.fetchedAt) = synchronized(lock) {
        val p = Point(now, u.session?.percent, u.weekly?.percent, u.session?.resetsAt, u.weekly?.resetsAt)
        val all = load(context, account)
        val last = all.lastOrNull()
        if (last != null && last.session == p.session && last.week == p.week &&
            last.sessionReset == p.sessionReset && now - last.t < SAME_EVERY_MS) return@synchronized
        val kept = all.filter { now - it.t < KEEP_MS } + p
        val f = file(context, account)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(kept.joinToString("\n") { line(it) })
        tmp.renameTo(f)
    }

    fun load(context: Context, account: String): List<Point> = synchronized(lock) {
        val f = file(context, account)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().mapNotNull(::parse).sortedBy { it.t }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context, account: String) = synchronized(lock) { file(context, account).delete() }

    /** Each 5-hour window seen, oldest first, with its peak. */
    fun sessions(points: List<Point>): List<Window> =
        points.filter { it.sessionReset != null && it.session != null }
            .groupBy { it.sessionReset!! / 60_000 } // the same window can come back a few seconds apart
            .map { (_, ps) -> Window(ps.first().sessionReset!!, ps.maxOf { it.session!! }) }
            .sortedBy { it.endsAt }

    /** The first account keeps the file from before there were several. */
    private fun file(context: Context, account: String) =
        File(context.noBackupFilesDir, if (account == Accounts.FIRST) FILE else "history_$account.csv")

    private fun line(p: Point) = listOf(p.t, p.session, p.week, p.sessionReset, p.weekReset).joinToString(",") { it?.toString() ?: "" }

    private fun parse(s: String): Point? {
        val c = s.split(',')
        if (c.size < 5) return null
        val t = c[0].toLongOrNull() ?: return null
        return Point(t, c[1].toIntOrNull(), c[2].toIntOrNull(), c[3].toLongOrNull(), c[4].toLongOrNull())
    }
}

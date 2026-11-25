package dev.pinch.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.pinch.app.data.Limit
import dev.pinch.app.data.Usage
import dev.pinch.app.ui.MainActivity

/**
 * Notifications: a warning when the session or the week passes 80% and again at 95%, and a
 * note when the 5-hour session resets. Checked after every successful fetch, wherever it came
 * from (the widget, the background, the app), so they work as long as refreshes do.
 *
 * Each warning fires once per window: what was last said is kept with the window's reset
 * time, so a new window starts quiet again and a refresh never repeats a warning.
 */
object Alerts {

    val THRESHOLDS = intArrayOf(80, 95)
    private const val CHANNEL = "usage"
    private const val PREFS = "alerts"

    /** The highest threshold [percent] has reached, or 0. */
    fun level(percent: Int): Int = THRESHOLDS.filter { percent >= it }.maxOrNull() ?: 0

    /**
     * The threshold to warn about now, or null. [said] is what was last said for this window
     * ("resetsAt:level"); a different window counts as nothing said yet.
     */
    fun warnFor(limit: Limit?, said: String?): Int? {
        if (limit == null) return null
        val lvl = level(limit.percent)
        if (lvl == 0) return null
        val parts = said?.split(':')
        val sameWindow = parts?.getOrNull(0) == limit.resetsAt.toString()
        val before = if (sameWindow) parts?.getOrNull(1)?.toIntOrNull() ?: 0 else 0
        return lvl.takeIf { it > before }
    }

    /**
     * After a fetch of [account]: [before] is what was cached, [now] what just came in.
     * @param sessionReset the 5-hour window reset since the last fetch.
     */
    fun check(context: Context, account: String, now: Usage, sessionReset: Boolean) {
        if (!allowed(context)) return
        val p = prefs(context)
        val edit = p.edit()
        val names = mapOf("session" to now.session, "week" to now.weekly)
        if (Settings.alertsWarn(context)) for ((name, limit) in names) {
            val key = "${name}_$account"
            val lvl = warnFor(limit, p.getString(key, null)) ?: continue
            edit.putString(key, "${limit!!.resetsAt}:$lvl")
            val title = context.getString(
                if (name == "session") R.string.alert_session_title else R.string.alert_week_title, lvl
            )
            val body = context.getString(
                if (name == "session") R.string.alert_session_body else R.string.alert_week_body,
                limit.percent, resetText(context, limit)
            )
            post(context, account, id(account, name), prefixed(context, account, title), body)
        }
        if (sessionReset && Settings.alertsReset(context)) {
            post(
                context, account, id(account, "reset"),
                prefixed(context, account, context.getString(R.string.alert_reset_title)),
                context.getString(R.string.alert_reset_body)
            )
        }
        edit.apply()
    }

    /** Signed out or removed: forget what was said, so a new sign-in starts fresh. */
    fun forget(context: Context, account: String) =
        prefs(context).edit().remove("session_$account").remove("week_$account").apply()

    /** Android 13+ asks the user first; before that notifications are on unless turned off. */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun allowed(context: Context) =
        (Settings.alertsWarn(context) || Settings.alertsReset(context)) && permitted(context) && !Settings.demo(context)

    private fun post(context: Context, account: String, id: Int, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.alert_channel_note) }
            )
        }
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_ACCOUNT, account),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_crab)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
            // The permission was taken back between the check and now.
        }
    }

    /** One notification per account and kind: a newer one replaces the older. */
    private fun id(account: String, kind: String) = "$account:$kind".hashCode()

    private fun prefixed(context: Context, account: String, title: String) =
        if (Accounts.multi(context)) "${Accounts.label(context, account)}: $title" else title

    private fun resetText(context: Context, l: Limit): String =
        dev.pinch.app.widget.WidgetRenderer.whenText(context, l.resetsAt)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

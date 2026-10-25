package dev.pinch.app.alerts

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pinch.app.R
import dev.pinch.app.Settings
import dev.pinch.app.data.Limit
import dev.pinch.app.data.Usage
import dev.pinch.app.ui.MainActivity
import dev.pinch.app.widget.WidgetRenderer
import kotlin.math.abs

/**
 * Usage alerts, all off until the user switches them on: the session or the week passing a
 * chosen percent (checked after every refresh, so as late as the refresh interval), and the
 * session resetting (an alarm set for the reset time Anthropic reports).
 */
object Alerts {

    private const val CHANNEL = "alerts"
    private const val ID_SESSION = 1
    private const val ID_WEEK = 2
    private const val ID_RESET = 3
    const val EXTRA_RESETS_AT = "resets_at"

    /** Reset times of one window can wobble between fetches; windows are hours apart. */
    private const val SESSION_SLACK_MS = 60 * 60_000L
    private const val WEEK_SLACK_MS = 24 * 60 * 60_000L

    /**
     * Whether a limit at [percent] should alert: it reached [threshold] and no alert went out
     * for this window yet ([firedFor] is the reset time of the window last alerted, 0 for none).
     */
    fun due(percent: Int, threshold: Int, resetsAt: Long?, firedFor: Long, slackMs: Long): Boolean =
        resetsAt != null && percent >= threshold && (firedFor == 0L || abs(resetsAt - firedFor) >= slackMs)

    /** After a refresh: post what is due and set the reset alarm. */
    fun check(context: Context, usage: Usage) {
        val app = context.applicationContext
        val session = usage.session
        if (Settings.sessionAlert(app) && session != null) {
            val at = Settings.sessionAlertAt(app)
            if (due(session.percent, at, session.resetsAt, Settings.alertFired(app, "session"), SESSION_SLACK_MS)) {
                Settings.setAlertFired(app, "session", session.resetsAt!!)
                post(app, ID_SESSION, app.getString(R.string.alert_session_title, session.percent),
                    app.getString(R.string.alert_session_text, (100 - session.percent).coerceAtLeast(0), WidgetRenderer.resetText(app, session)))
            }
        }
        val week = usage.weekly
        if (Settings.weekAlert(app) && week != null) {
            val at = Settings.weekAlertAt(app)
            if (due(week.percent, at, week.resetsAt, Settings.alertFired(app, "week"), WEEK_SLACK_MS)) {
                Settings.setAlertFired(app, "week", week.resetsAt!!)
                post(app, ID_WEEK, app.getString(R.string.alert_week_title, week.percent),
                    app.getString(R.string.alert_week_text, WidgetRenderer.resetText(app, week)))
            }
        }
        armReset(app, session)
    }

    /**
     * Sets the "session reset" alarm for the current window, or clears it when the alert is off
     * or nothing is used yet. One alarm only: setting it again replaces the last one.
     */
    fun armReset(context: Context, session: Limit?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = session?.resetsAt
        if (!Settings.resetAlert(context) || at == null || session.percent <= 0 || at <= System.currentTimeMillis()) {
            am.cancel(resetPending(context, 0L))
            return
        }
        // Inexact but allowed in Doze: lands at the reset or a few minutes after, without the
        // "exact alarms" permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, resetPending(context, at))
    }

    /** The reset alarm went off. */
    fun onReset(context: Context, resetsAt: Long) {
        if (!Settings.resetAlert(context)) return
        if (resetsAt != 0L && abs(Settings.alertFired(context, "reset") - resetsAt) < SESSION_SLACK_MS) return
        Settings.setAlertFired(context, "reset", resetsAt)
        post(context, ID_RESET, context.getString(R.string.alert_reset_title), context.getString(R.string.alert_reset_text))
    }

    /** Whether Android will show Pinch's notifications at all. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_crab)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun resetPending(context: Context, resetsAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, ID_RESET,
        Intent(context, AlertReceiver::class.java).putExtra(EXTRA_RESETS_AT, resetsAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

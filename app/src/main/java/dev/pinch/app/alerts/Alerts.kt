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
import android.graphics.Bitmap
import dev.pinch.app.Settings
import dev.pinch.app.art.Clawd
import dev.pinch.app.art.Frame
import dev.pinch.app.art.Pix
import dev.pinch.app.art.Scenes
import dev.pinch.app.art.Theme
import dev.pinch.app.art.Themes
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

    private const val CHANNEL_USAGE = "alerts"
    private const val CHANNEL_RESET = "alerts_reset"
    private const val ID_SESSION = 1
    private const val ID_WEEK = 2
    private const val ID_RESET = 3
    private const val KEY_SESSION = "session"
    private const val KEY_WEEK = "week"
    private const val KEY_RESET = "reset"
    const val EXTRA_RESETS_AT = "resets_at"
    const val EXTRA_KEY = "key"
    const val ACTION_TURN_OFF = "dev.pinch.app.action.ALERT_OFF"

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
                post(
                    app, ID_SESSION, CHANNEL_USAGE,
                    title = app.getString(R.string.alert_session_title, session.percent),
                    text = app.getString(R.string.alert_session_text, (100 - session.percent).coerceAtLeast(0), WidgetRenderer.resetText(app, session)),
                    sub = app.getString(R.string.session_5h),
                    percent = session.percent, until = session.resetsAt, off = KEY_SESSION
                )
            }
        }
        val week = usage.weekly
        if (Settings.weekAlert(app) && week != null) {
            val at = Settings.weekAlertAt(app)
            if (due(week.percent, at, week.resetsAt, Settings.alertFired(app, "week"), WEEK_SLACK_MS)) {
                Settings.setAlertFired(app, "week", week.resetsAt!!)
                post(
                    app, ID_WEEK, CHANNEL_USAGE,
                    title = app.getString(R.string.alert_week_title, week.percent),
                    text = app.getString(R.string.alert_week_text, WidgetRenderer.resetText(app, week)),
                    sub = app.getString(R.string.alert_week_sub),
                    percent = week.percent, until = week.resetsAt, off = KEY_WEEK
                )
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
        // The "session at N%" alert is old news now.
        NotificationManagerCompat.from(context).cancel(ID_SESSION)
        post(
            context, ID_RESET, CHANNEL_RESET,
            title = context.getString(R.string.alert_reset_title),
            text = context.getString(R.string.alert_reset_text),
            sub = context.getString(R.string.session_5h),
            percent = 0, until = null, off = KEY_RESET, happy = true
        )
    }

    /** The "Turn off" button on an alert: switches that alert off, as the app's switch would. */
    fun turnOff(context: Context, key: String) {
        when (key) {
            KEY_SESSION -> { Settings.setSessionAlert(context, false); NotificationManagerCompat.from(context).cancel(ID_SESSION) }
            KEY_WEEK -> { Settings.setWeekAlert(context, false); NotificationManagerCompat.from(context).cancel(ID_WEEK) }
            KEY_RESET -> { Settings.setResetAlert(context, false); armReset(context, null); NotificationManagerCompat.from(context).cancel(ID_RESET) }
        }
    }

    /** Whether Android will show Pinch's notifications at all. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * One alert, in the app's look: Clawd in today's skin, the level colour, and two buttons.
     * It pings once, and clears itself at [until] (the reset), when it stops being true.
     */
    private fun post(
        context: Context, id: Int, channel: String, title: String, text: String, sub: String,
        percent: Int, until: Long?, off: String, happy: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        channels(context)
        val theme = Settings.theme(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val turnOff = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlertReceiver::class.java).setAction(ACTION_TURN_OFF).putExtra(EXTRA_KEY, off),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_crab)
            .setLargeIcon(portrait(context, theme, happy))
            .setColor(if (happy) Themes.OK else WidgetRenderer.levelColor(percent))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(sub)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.alert_open), open)
            .addAction(0, context.getString(R.string.alert_turn_off), turnOff)
        // A one-off ping, not a live notification: no progress bar (it reads as a running task),
        // and it clears itself at the reset if it was not swiped away before.
        val left = (until ?: 0L) - System.currentTimeMillis()
        if (left > 0) b.setTimeoutAfter(left)
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    /** Two channels, so the phone's settings can silence limits and resets separately. */
    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_USAGE, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.alerts_channel_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESET, context.getString(R.string.alerts_channel_reset), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.alerts_channel_reset_desc) }
        )
    }

    /** Clawd in the skin he wears today, on the theme's card colour; arms up for a reset. */
    private fun portrait(context: Context, theme: Theme, happy: Boolean): Bitmap {
        // A square cut tight around him (the canvas is wide for his walks), so he fills the icon.
        val size = 24
        val p = Pix(size, size)
        p.rect(0, 0, size, size, theme.card)
        val frame = if (happy) Frame(eyes = "happy", arms = "up") else Frame()
        Clawd.draw(p, frame, Settings.skin(context, theme), Clawd.ORIGIN_X - 6, Clawd.ORIGIN_Y + 3, Scenes.sand(theme.scene))
        return p.toBitmap(4)
    }

    private fun resetPending(context: Context, resetsAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, ID_RESET,
        Intent(context, AlertReceiver::class.java).putExtra(EXTRA_RESETS_AT, resetsAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

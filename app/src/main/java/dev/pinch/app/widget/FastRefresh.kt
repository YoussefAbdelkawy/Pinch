package dev.pinch.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.pinch.app.Settings

/**
 * Refresh intervals under WorkManager's 15-minute floor. A non-waking alarm: it fires only
 * while the phone is awake (when someone might look at the widget) and never wakes it up,
 * so a sleeping phone pays nothing for it. WorkManager's 15-minute job stays underneath as
 * the backstop and re-arms this after a reboot.
 */
class FastRefresh : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESET) {
            val at = intent.getLongExtra(EXTRA_AT, 0L)
            // Not there yet: one more, shorter hop.
            if (at > System.currentTimeMillis()) return armReset(context, at)
            // A window just reset: show it at once from what we know (the numbers settle to 0%
            // on their own), then fetch to confirm and to let the crab celebrate. Anthropic's
            // numbers lag the reset by a moment, so the fetch waits a little.
            RefreshWorker.refreshNow(context, RESET_GRACE_MS)
            val app = context.applicationContext
            val pending = goAsync()
            Thread {
                try {
                    ClawdWidget.updateAll(app)
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        RefreshWorker.refreshNow(context)
        arm(context)
    }

    companion object {
        fun arm(context: Context) {
            val minutes = Settings.intervalMinutes(context)
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            if (minutes >= 15) {
                am.cancel(pending(context))
                return
            }
            // Inexact on purpose: Android may stretch it a few minutes to batch with other work,
            // which is the price of not asking for the "exact alarms" permission.
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + minutes * 60_000L, pending(context))
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.apply {
                cancel(pending(context))
                cancel(resetPending(context, 0L))
            }
        }

        /**
         * Wakes the widget at the next reset (session or week), even in Doze, so it never sits on
         * an old number or a countdown past zero until the next 15-minute job.
         *
         * Without the "exact alarms" permission Android may deliver an alarm up to about 3/4 of
         * its wait late, so one alarm set 4 hours ahead could land an hour late. Instead it hops:
         * each alarm aims halfway to the reset (so even late it lands before it) and the last
         * one, under [HOP_MS] away, aims at the reset itself and lands within about a minute.
         * Each hop only sets the next alarm.
         */
        fun armReset(context: Context, at: Long?) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val now = System.currentTimeMillis()
            if (at == null || at <= now) {
                am.cancel(resetPending(context, 0L))
                return
            }
            val left = at - now
            val fireAt = if (left <= HOP_MS) at else now + left / 2
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, resetPending(context, at))
        }

        private const val ACTION_RESET = "dev.pinch.app.action.RESET"
        private const val EXTRA_AT = "at"
        private const val HOP_MS = 90_000L
        /** Anthropic's numbers lag the reset by a moment; asking right on the second can still get the old ones. */
        private const val RESET_GRACE_MS = 45_000L

        private fun resetPending(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
            context, 1, Intent(context, FastRefresh::class.java).setAction(ACTION_RESET).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, FastRefresh::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

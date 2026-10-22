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
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
        }

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, FastRefresh::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

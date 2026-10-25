package dev.pinch.app.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pinch.app.widget.RefreshWorker

/**
 * The "session reset" alarm and the "Turn off" button on an alert. Not exported: only this
 * app's own alarm and notifications can reach it.
 */
class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Alerts.ACTION_TURN_OFF) {
            intent.getStringExtra(Alerts.EXTRA_KEY)?.let { Alerts.turnOff(app, it) }
            return
        }
        Alerts.onReset(app, intent.getLongExtra(Alerts.EXTRA_RESETS_AT, 0L))
        // Fresh numbers for the widget too: Clawd celebrates the reset.
        RefreshWorker.refreshNow(app)
    }
}

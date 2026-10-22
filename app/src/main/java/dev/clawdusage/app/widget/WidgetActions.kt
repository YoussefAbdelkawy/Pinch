package dev.clawdusage.app.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.clawdusage.app.art.Clawd
import dev.clawdusage.app.art.everyOther

/**
 * The widget's buttons. Not exported: only the widget's own PendingIntents (sent with this
 * app's identity) can reach it, so other apps cannot poke the refresh or the animations.
 */
class WidgetActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> RefreshWorker.refreshNow(context)
            ACTION_TAP -> {
                // Tapping Clawd plays one of Claude Code's click animations, then he goes back to his mood.
                val name = Clawd.TAP_ACTS.random()
                val frames = Clawd.SEQS.getValue(name).everyOther()
                val app = context.applicationContext
                val pending = goAsync()
                Thread {
                    try {
                        ClawdWidget.updateAll(app, act = frames)
                        Thread.sleep((frames.size * 120L + 200).coerceAtMost(8_000))
                        ClawdWidget.updateAll(app)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.clawdusage.app.action.REFRESH"
        const val ACTION_TAP = "dev.clawdusage.app.action.TAP"

        fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, WidgetActions::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

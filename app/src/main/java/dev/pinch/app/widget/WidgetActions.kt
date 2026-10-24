package dev.pinch.app.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pinch.app.art.Clawd

/**
 * The widget's buttons. Not exported: only the widget's own PendingIntents (sent with this
 * app's identity) can reach it, so other apps cannot poke the refresh or the animations.
 */
class WidgetActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                // Start fetching first, then nudge the arrow while the numbers are on their way.
                // The nudge rests where the arrow started, so it may land before or after the result.
                val app = context.applicationContext
                RefreshWorker.refreshFromTap(app)
                val pending = goAsync()
                Thread {
                    try {
                        ClawdWidget.updateAll(app, nudge = true)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            ACTION_TAP -> {
                // Tapping Clawd plays one of Claude Code's click animations, then he goes back to his mood.
                val name = Clawd.TAP_ACTS.random()
                val frames = Clawd.SEQS.getValue(name)
                val app = context.applicationContext
                val pending = goAsync()
                Thread {
                    try {
                        ClawdWidget.updateAll(app, act = frames)
                        Thread.sleep((frames.size * Clawd.FRAME_MS + 300).coerceAtMost(8_000))
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
        const val ACTION_REFRESH = "dev.pinch.app.action.REFRESH"
        const val ACTION_TAP = "dev.pinch.app.action.TAP"

        fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, WidgetActions::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

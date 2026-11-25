package dev.pinch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pinch.app.Accounts
import dev.pinch.app.Haptics
import dev.pinch.app.art.Clawd
import dev.pinch.app.auth.ClaudeAuth
import dev.pinch.app.data.UsageRepo
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * The widget's buttons. Not exported: only the widget's own PendingIntents (sent with this
 * app's identity) can reach it, so other apps cannot poke the refresh or the animations.
 */
class WidgetActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                // Fetch right here, while the tap keeps the app awake, instead of waiting for
                // WorkManager to get round to a job (which on Battery Saver, in Doze or on a
                // "sleeping" app could be many minutes). The arrow turns while the numbers are on
                // their way, and the last redraw is always the plain one, in this same thread, so
                // the arrow can never be left in its turning state (which took no taps).
                val app = context.applicationContext
                Haptics.tick(app)
                val pending = goAsync()
                Thread {
                    try {
                        if (!ClaudeAuth.isSignedIn(app)) {
                            ClawdWidget.updateAll(app)
                            return@Thread
                        }
                        val fetch = FutureTask { UsageRepo.refreshAll(app, waitMs = TAP_WAIT_MS) }
                        Thread(fetch).start()
                        try {
                            ClawdWidget.updateAll(app, nudge = true)
                        } catch (_: Exception) {
                        }
                        val states = try {
                            fetch.get(TAP_WAIT_MS + 3_000, TimeUnit.MILLISECONDS)
                        } catch (_: Exception) {
                            null
                        }
                        if (states != null) FastRefresh.armReset(app, RefreshWorker.nextReset(states))
                        ClawdWidget.updateAll(app, states)
                        // No way through from here (the system held the network back, or it was
                        // slow): hand over to a job, which gets the network as soon as it may.
                        if (states == null || states.values.any { it.error == "offline" }) RefreshWorker.refreshFromTap(app)
                    } catch (_: Exception) {
                        try {
                            ClawdWidget.updateAll(app)
                        } catch (_: Exception) {
                        }
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            ACTION_NEXT -> {
                // The name or the dots: this widget moves on to the next account, and stays there.
                val app = context.applicationContext
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                Accounts.setForWidget(app, id, Accounts.next(app, Accounts.forWidget(app, id)))
                Haptics.tick(app)
                val pending = goAsync()
                Thread {
                    try {
                        ClawdWidget.updateAll(app, only = id)
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
        const val ACTION_NEXT = "dev.pinch.app.action.NEXT_ACCOUNT"

        /**
         * How long a tap waits for the numbers before handing over to a job. A receiver may run
         * for about a minute after goAsync(); this stays well inside it.
         */
        private const val TAP_WAIT_MS = 20_000L

        fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, WidgetActions::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** "Next account" for one widget: each widget has its own, so each keeps its own page. */
        fun next(context: Context, widgetId: Int): PendingIntent = PendingIntent.getBroadcast(
            context, ACTION_NEXT.hashCode() + widgetId,
            Intent(context, WidgetActions::class.java).setAction(ACTION_NEXT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

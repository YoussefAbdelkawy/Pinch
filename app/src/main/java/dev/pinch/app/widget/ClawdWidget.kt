package dev.pinch.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import dev.pinch.app.art.Frame
import dev.pinch.app.auth.ClaudeAuth
import dev.pinch.app.data.UsageRepo

/**
 * The home screen widget. It only draws what is cached; the network work happens in
 * [RefreshWorker]. Its own receiver handles nothing but the system's widget broadcasts,
 * the widget's buttons go to the non-exported [WidgetActions].
 */
class ClawdWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        // The small widget may still be on the home screen and need the refreshes.
        if (MiniWidget.ids(context).isEmpty()) RefreshWorker.cancel(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshWorker.schedule(context)
        val state = UsageRepo.cached(context)
        val signedIn = ClaudeAuth.isSignedIn(context)
        for (id in ids) render(context, manager, id, state, signedIn, null)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, id, UsageRepo.cached(context), ClaudeAuth.isSignedIn(context), null)
    }

    companion object {
        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClawdWidget::class.java))

        /**
         * Redraws every Clawd widget (the small ones too), optionally playing a one-shot animation.
         * @param nudge gives the refresh arrow one quick turn (the answer to a tap on it).
         */
        fun updateAll(
            context: Context,
            state: UsageRepo.State? = null,
            act: List<Frame>? = null,
            signedIn: Boolean = ClaudeAuth.isSignedIn(context),
            nudge: Boolean = false
        ) = synchronized(lock) {
            // One redraw at a time, and the cached numbers read inside the lock: a redraw that
            // waited for a refresh's redraw draws the new numbers, never the old ones over them.
            val s = state ?: UsageRepo.cached(context)
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids(context)) render(context, manager, id, s, signedIn, act, nudge)
            // The small widget has no animations or arrow: it only needs new numbers and looks.
            if (act == null && !nudge) MiniWidget.renderAll(context, s, signedIn)
        }

        private val lock = Any()

        private fun render(
            context: Context, manager: AppWidgetManager, id: Int,
            state: UsageRepo.State, signedIn: Boolean, act: List<Frame>?, nudge: Boolean = false
        ) {
            val o = manager.getAppWidgetOptions(id)
            // Portrait: width is the minimum, height the maximum the launcher reports.
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 300
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 200
            try {
                manager.updateAppWidget(id, WidgetRenderer.build(context, state, signedIn, w, h, act, nudge = nudge))
            } catch (_: RuntimeException) {
                // Too much for the launcher (bitmap budget): fall back to a still Clawd.
                val still = (act ?: listOf(Frame())).take(1)
                manager.updateAppWidget(id, WidgetRenderer.build(context, state, signedIn, w, h, still, nudge = nudge))
            }
        }
    }
}

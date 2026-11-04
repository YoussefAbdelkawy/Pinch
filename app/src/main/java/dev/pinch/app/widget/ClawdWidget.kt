package dev.pinch.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import dev.pinch.app.Accounts
import dev.pinch.app.art.Frame
import dev.pinch.app.auth.ClaudeAuth
import dev.pinch.app.data.UsageRepo

/**
 * The home screen widget. It only draws what is cached; the network work happens in
 * [RefreshWorker]. Its own receiver handles nothing but the system's widget broadcasts,
 * the widget's buttons go to the non-exported [WidgetActions].
 *
 * With several accounts each widget shows one of them (see [Accounts.forWidget]); a tap on
 * its name or dots moves it to the next.
 */
class ClawdWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) = RefreshWorker.cancel(context)

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) Accounts.forgetWidget(context, id)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshWorker.schedule(context)
        for (id in ids) render(context, manager, id, null, null)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, id, null, null)
    }

    companion object {
        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClawdWidget::class.java))

        /**
         * Redraws every Clawd widget, optionally playing a one-shot animation.
         * @param states fresh numbers per account; any account missing is read from the cache.
         * @param actFor plays [act] only on widgets showing one of these accounts (null: all).
         * @param only redraws just this widget.
         * @param nudge gives the refresh arrow one quick turn (the answer to a tap on it).
         */
        fun updateAll(
            context: Context,
            states: Map<String, UsageRepo.State>? = null,
            act: List<Frame>? = null,
            actFor: Set<String>? = null,
            nudge: Boolean = false,
            only: Int? = null
        ) = synchronized(lock) {
            // One redraw at a time, and the cached numbers read inside the lock: a redraw that
            // waited for a refresh's redraw draws the new numbers, never the old ones over them.
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids(context)) {
                if (only != null && id != only) continue
                val account = Accounts.forWidget(context, id)
                val play = act?.takeIf { actFor == null || account in actFor }
                render(context, manager, id, states?.get(account), play, nudge)
            }
        }

        private val lock = Any()

        private fun render(
            context: Context, manager: AppWidgetManager, id: Int,
            fresh: UsageRepo.State?, act: List<Frame>?, nudge: Boolean = false
        ) {
            val account = Accounts.forWidget(context, id)
            val state = fresh ?: UsageRepo.cached(context, account)
            val signedIn = ClaudeAuth.hasTokens(context, account) && ClaudeAuth.isSignedIn(context)
            val page = WidgetRenderer.Page.of(context, account, id)
            val o = manager.getAppWidgetOptions(id)
            // Portrait: width is the minimum, height the maximum the launcher reports.
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 300
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 200
            val look = WidgetRenderer.look(context, account)
            try {
                manager.updateAppWidget(id, WidgetRenderer.build(context, state, signedIn, w, h, act, look, page = page, nudge = nudge))
            } catch (_: RuntimeException) {
                // Too much for the launcher (bitmap budget): fall back to a still Clawd.
                val still = (act ?: listOf(Frame())).take(1)
                manager.updateAppWidget(id, WidgetRenderer.build(context, state, signedIn, w, h, still, look, page = page, nudge = nudge))
            }
        }
    }
}

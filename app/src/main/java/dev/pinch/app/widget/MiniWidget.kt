package dev.pinch.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.pinch.app.R
import dev.pinch.app.art.Clawd
import dev.pinch.app.art.Frame
import dev.pinch.app.art.Scenes
import dev.pinch.app.art.Themes
import dev.pinch.app.auth.ClaudeAuth
import dev.pinch.app.data.UsageRepo
import dev.pinch.app.ui.MainActivity

/**
 * The small widget, for home screens without room for the 4x3: the session percent and the
 * countdown at 1x1, plus the bar and a still Clawd at 2x1. No animation, so it costs next to
 * nothing to draw. It shares the big widget's refresh job and cached numbers.
 */
class MiniWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        // The big widget may still be on the home screen and need the refreshes.
        if (ClawdWidget.ids(context).isEmpty()) RefreshWorker.cancel(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshWorker.schedule(context)
        val state = UsageRepo.cached(context)
        val signedIn = ClaudeAuth.isSignedIn(context)
        for (id in ids) render(context, manager, id, state, signedIn)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, id, UsageRepo.cached(context), ClaudeAuth.isSignedIn(context))
    }

    companion object {
        /** From this width (dp) the 2x1 layout fits. */
        private const val WIDE_DP = 110

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MiniWidget::class.java))

        fun renderAll(context: Context, state: UsageRepo.State, signedIn: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids(context)) render(context, manager, id, state, signedIn)
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, state: UsageRepo.State, signedIn: Boolean) {
            // Android 12+: hand over both layouts and let the launcher pick the one that fits.
            val views = if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(mapOf(
                    SizeF(40f, 40f) to build(context, state, signedIn, wide = false),
                    SizeF(WIDE_DP.toFloat(), 40f) to build(context, state, signedIn, wide = true)
                ))
            } else {
                val w = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                build(context, state, signedIn, wide = w >= WIDE_DP)
            }
            manager.updateAppWidget(id, views)
        }

        fun build(context: Context, state: UsageRepo.State, signedIn: Boolean, wide: Boolean): RemoteViews {
            val look = WidgetRenderer.look(context)
            val theme = look.theme
            val v = RemoteViews(context.packageName, if (wide) R.layout.widget_mini_2x1 else R.layout.widget_mini_1x1)
            val session = state.usage?.session
            val authProblem = !signedIn || state.error == "auth_error"

            v.setInt(R.id.bg, "setColorFilter", theme.bg)
            val caption = if (wide) theme.muted else theme.text
            v.setTextColor(R.id.session_reset, caption)
            v.setTextColor(R.id.session_note, caption)

            // ---- the number: green, yellow or red on its own at 1x1; the bar carries it at 2x1
            if (session != null && !authProblem) {
                v.setTextViewText(R.id.pct, "${session.percent}%")
                val pctColor = when {
                    !wide || session.percent >= 85 -> WidgetRenderer.levelColor(session.percent)
                    else -> theme.textHi
                }
                v.setTextColor(R.id.pct, pctColor)
                v.setContentDescription(R.id.content, context.getString(R.string.mini_described, session.percent))
            } else {
                v.setTextViewText(R.id.pct, context.getString(R.string.dash_pct))
                v.setTextColor(R.id.pct, theme.muted)
            }
            if (wide) {
                WidgetRenderer.bar(v, "session", session?.percent, authProblem)
                val crab = Clawd.render(Frame(), look.skin, Scenes.sand(theme.scene)).toBitmap(4)
                v.setImageViewBitmap(R.id.crab, crab)
            }

            // ---- the countdown, or a word on why there is none
            v.setViewVisibility(R.id.session_reset, View.GONE)
            v.setViewVisibility(R.id.session_note, View.VISIBLE)
            val note = when {
                !signedIn || state.error == "auth_error" -> R.string.mini_sign_in
                session == null -> R.string.loading
                session.resetsAt == null -> R.string.not_started
                session.resetsAt <= System.currentTimeMillis() -> R.string.resetting
                else -> null
            }
            if (note != null) {
                v.setTextViewText(R.id.session_note, context.getString(note))
                if (authProblem) v.setTextColor(R.id.session_note, Themes.WARN)
            } else {
                v.setViewVisibility(R.id.session_note, View.GONE)
                v.setViewVisibility(R.id.session_reset, View.VISIBLE)
                val base = SystemClock.elapsedRealtime() + (session!!.resetsAt!! - System.currentTimeMillis())
                val format = if (wide) context.getString(R.string.resets_in_chrono) else "%s"
                v.setChronometer(R.id.session_reset, base, format, true)
                v.setChronometerCountDown(R.id.session_reset, true)
            }

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.content, open)
            return v
        }
    }
}

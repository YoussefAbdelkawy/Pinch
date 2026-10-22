package dev.clawdusage.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import dev.clawdusage.app.R
import dev.clawdusage.app.Settings
import dev.clawdusage.app.art.Clawd
import dev.clawdusage.app.art.Frame
import dev.clawdusage.app.art.Pix
import dev.clawdusage.app.art.Scenes
import dev.clawdusage.app.art.Skin
import dev.clawdusage.app.art.Theme
import dev.clawdusage.app.art.Themes
import dev.clawdusage.app.data.Level
import dev.clawdusage.app.data.Limit
import dev.clawdusage.app.data.UsageRepo
import dev.clawdusage.app.ui.MainActivity
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Builds the widget's RemoteViews. Home screen widgets cannot run animation code, so every
 * moving thing is a ViewFlipper of pre-drawn frames that the launcher flips on its own:
 * Clawd (120 ms frames), the scene overlay and the hearts.
 */
object WidgetRenderer {

    /** dp per logical pixel of Clawd and the scenes. */
    private const val DP_PER_PX = 2
    private const val CRAB_SCALE = 4
    private const val STAGE_SCALE = 4
    private const val FX_SCALE = 2
    private const val LEFT_COLUMN_DP = 76
    private const val SIDE_PADDING_DP = 28

    class Look(val theme: Theme, val skin: Skin)

    fun look(context: Context): Look {
        val theme = Settings.theme(context)
        return Look(theme, Settings.skin(context, theme))
    }

    /**
     * @param act a one-shot animation to play instead of the mood loop (a tap, a celebration).
     */
    fun build(
        context: Context,
        state: UsageRepo.State,
        signedIn: Boolean,
        widthDp: Int,
        heightDp: Int,
        act: List<Frame>? = null,
        look: Look = look(context)
    ): RemoteViews {
        val theme = look.theme
        val v = RemoteViews(context.packageName, R.layout.widget)
        val usage = state.usage
        val session = usage?.session
        val authProblem = !signedIn || state.error == "auth_error"

        // ---- colours
        v.setInt(R.id.bg, "setColorFilter", theme.bg)
        val pattern = patternRes(theme.id)
        if (pattern != 0) {
            v.setImageViewResource(R.id.pattern, pattern)
            v.setViewVisibility(R.id.pattern, View.VISIBLE)
        } else v.setViewVisibility(R.id.pattern, View.GONE)
        for (id in intArrayOf(R.id.title, R.id.week_label, R.id.model_label)) v.setTextColor(id, theme.muted.brighter())
        for (id in intArrayOf(R.id.plan, R.id.left_text, R.id.session_label, R.id.week_reset)) v.setTextColor(id, theme.muted)
        for (id in intArrayOf(R.id.pct, R.id.week_pct)) v.setTextColor(id, theme.textHi)
        for (id in intArrayOf(R.id.session_reset, R.id.session_note, R.id.model_value, R.id.status)) v.setTextColor(id, theme.text)
        v.setTextColor(R.id.updated, theme.faint.brighter())
        v.setInt(R.id.refresh, "setColorFilter", theme.muted)

        // ---- header
        v.setTextViewText(R.id.plan, usage?.plan?.takeIf { it.isNotEmpty() }?.let { "· $it" } ?: "")

        // ---- big number: session used, and what is left
        if (session != null && !authProblem) {
            v.setTextViewText(R.id.pct, "${session.percent}%")
            v.setTextColor(R.id.pct, if (session.percent >= 85) levelColor(session.percent) else theme.textHi)
            v.setTextViewText(R.id.left_text, context.getString(R.string.pct_left, (100 - session.percent).coerceAtLeast(0)))
        } else {
            v.setTextViewText(R.id.pct, context.getString(R.string.dash_pct))
            v.setTextViewText(R.id.left_text, "")
        }

        // ---- hearts
        val halves = if (authProblem) null else usage?.heartHalves
        v.removeAllViews(R.id.hearts)
        if (halves != null) {
            v.setViewVisibility(R.id.hearts, View.VISIBLE)
            val still = hearts(halves, 1f)
            v.addView(R.id.hearts, frame(context, still))
            // Last bit of health: the hearts beat.
            if (halves in 1..2) {
                v.addView(R.id.hearts, frame(context, hearts(halves, 0.6f)))
                repeat(5) { v.addView(R.id.hearts, frame(context, still)) }
            }
        } else v.setViewVisibility(R.id.hearts, View.GONE)

        // ---- the stage: backdrop, animated overlay, Clawd
        val stageDp = (widthDp - SIDE_PADDING_DP - LEFT_COLUMN_DP).coerceAtLeast(80)
        val stageW = stageDp / DP_PER_PX
        val hour = LocalDateTime.now().hour
        v.setImageViewBitmap(R.id.stage_bg, Scenes.background(theme.scene, stageW, hour).toBitmap(STAGE_SCALE, Bitmap.Config.RGB_565))
        v.removeAllViews(R.id.stage_fx)
        for (k in 0 until Scenes.FX_FRAMES) {
            v.addView(R.id.stage_fx, frame(context, Scenes.fx(theme.scene, stageW, hour, k).toBitmap(FX_SCALE)))
        }

        val mood = when {
            authProblem || session == null -> Clawd.Mood.SLEEP
            session.percent >= 100 -> Clawd.Mood.DEAD
            session.resetsAt == null -> Clawd.Mood.SLEEP // the 5-hour window has not started
            session.percent >= 85 -> Clawd.Mood.CRIT
            else -> Clawd.Mood.IDLE
        }
        val frames = act ?: Clawd.moodLoop(mood, halves ?: 6)
        val sand = Scenes.sand(theme.scene)
        val cache = HashMap<Frame, Bitmap>()
        v.removeAllViews(R.id.crab)
        for (f in frames) {
            val bmp = cache.getOrPut(f) { crabBitmap(f, look.skin, sand) }
            v.addView(R.id.crab, frame(context, bmp))
        }
        // Clawd walks along the stage as the session fills up.
        val pct = session?.percent?.coerceIn(0, 100) ?: 15
        val travel = (stageW - Clawd.CANVAS_W).coerceAtLeast(0)
        val xDp = travel * pct / 100 * DP_PER_PX
        v.setViewPadding(R.id.crab_holder, dpToPx(context, xDp), 0, 0, 0)

        // ---- session bar and its reset countdown
        bar(v, "session", session?.percent, authProblem)
        v.setViewVisibility(R.id.session_reset, View.GONE)
        v.setViewVisibility(R.id.session_note, View.VISIBLE)
        when {
            !signedIn -> note(v, context.getString(R.string.tap_to_sign_in), Themes.WARN)
            state.error == "auth_error" -> note(v, context.getString(R.string.sign_in_again), Themes.CRIT)
            session == null -> note(v, context.getString(if (state.error != null) R.string.no_data_offline else R.string.loading), theme.text)
            session.resetsAt == null -> note(v, context.getString(R.string.window_not_started), theme.text)
            session.resetsAt <= System.currentTimeMillis() -> note(v, context.getString(R.string.resetting), theme.text)
            else -> {
                v.setViewVisibility(R.id.session_note, View.GONE)
                v.setViewVisibility(R.id.session_reset, View.VISIBLE)
                val base = SystemClock.elapsedRealtime() + (session.resetsAt - System.currentTimeMillis())
                v.setChronometer(R.id.session_reset, base, context.getString(R.string.resets_in_chrono), true)
                v.setChronometerCountDown(R.id.session_reset, true)
            }
        }

        // ---- week
        val week = usage?.weekly
        if (week != null && !authProblem) {
            v.setTextViewText(R.id.week_pct, "${week.percent}%")
            v.setTextViewText(R.id.week_reset, resetText(context, week))
        } else {
            v.setTextViewText(R.id.week_pct, "")
            v.setTextViewText(R.id.week_reset, context.getString(R.string.no_data))
        }
        bar(v, "week", week?.percent, authProblem)

        // ---- the model used most this week
        val top = usage?.topModel
        v.setTextViewText(
            R.id.model_value,
            if (top != null && !authProblem) "${top.label} · ${top.percent}%" else context.getString(R.string.no_data)
        )

        // ---- footer
        val status = state.status
        val (dot, statusText) = when (status?.indicator) {
            null -> null to ""
            "none" -> Themes.OK to context.getString(R.string.status_ok)
            "minor" -> Themes.WARN to status.description
            else -> Themes.CRIT to status.description
        }
        v.setTextViewText(R.id.status, if (dot != null) "●  $statusText" else statusText)
        if (dot != null) v.setTextColor(R.id.status, dot)
        v.setTextViewText(R.id.updated, updatedText(context, state))

        // ---- fit the height we have
        v.setViewVisibility(R.id.footer, if (heightDp >= 200 && status != null) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.model_row, if (heightDp >= 170) View.VISIBLE else View.GONE)
        val showWeek = heightDp >= 140
        v.setViewVisibility(R.id.week_row, if (showWeek) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.week_bars, if (showWeek) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.header, if (heightDp >= 110) View.VISIBLE else View.GONE)

        // ---- taps
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        v.setOnClickPendingIntent(R.id.content, open)
        v.setOnClickPendingIntent(R.id.refresh, WidgetActions.pending(context, WidgetActions.ACTION_REFRESH))
        v.setOnClickPendingIntent(
            R.id.stage,
            if (authProblem) open else WidgetActions.pending(context, WidgetActions.ACTION_TAP)
        )
        return v
    }

    /** Clawd's frame with the ground rows under him, so his feet land on the stage's ground. */
    fun crabBitmap(f: Frame, skin: Skin, sand: Int): Bitmap =
        Pix(Clawd.CANVAS_W, Clawd.CANVAS_H + Scenes.GROUND)
            .also { Clawd.draw(it, f, skin, Clawd.ORIGIN_X, Clawd.ORIGIN_Y, sand) }
            .toBitmap(CRAB_SCALE)

    // ---------------------------------------------------------------- pieces ----

    private fun frame(context: Context, bmp: Bitmap) =
        RemoteViews(context.packageName, R.layout.widget_frame).apply { setImageViewBitmap(R.id.frame, bmp) }

    private fun note(v: RemoteViews, text: String, color: Int) {
        v.setTextViewText(R.id.session_note, text)
        v.setTextColor(R.id.session_note, color)
    }

    private fun bar(v: RemoteViews, name: String, pct: Int?, off: Boolean) {
        val ids = barIds(name)
        val level = if (pct == null || off) null else Level.of(pct)
        val shown = when (level) {
            null, Level.OK -> 0
            Level.WARN -> 1
            Level.CRIT -> 2
            Level.DEAD -> 3
        }
        ids.forEachIndexed { i, id ->
            v.setViewVisibility(id, if (i == shown) View.VISIBLE else View.GONE)
            v.setProgressBar(id, 100, if (level == null) 0 else pct!!.coerceIn(0, 100), false)
        }
    }

    private fun barIds(name: String) = if (name == "session")
        intArrayOf(R.id.session_bar_ok, R.id.session_bar_warn, R.id.session_bar_crit, R.id.session_bar_dead)
    else intArrayOf(R.id.week_bar_ok, R.id.week_bar_warn, R.id.week_bar_crit, R.id.week_bar_dead)

    fun levelColor(pct: Int) = when (Level.of(pct)) {
        Level.OK -> Themes.OK
        Level.WARN -> Themes.WARN
        Level.CRIT -> Themes.CRIT
        Level.DEAD -> Themes.DEAD
    }

    private val HEART = listOf(".##.##.", "#######", "#######", ".#####.", "..###..", "...#...")
    private val HEART_ON = Pix.color("#F87171")
    private val HEART_OFF = Pix.color("#4D52525B")

    /** Three hearts with halves, as in Clawd. [glow] < 1 dims them for the heartbeat. */
    fun hearts(halves: Int, glow: Float): Bitmap {
        val p = Pix(23, 6)
        for (i in 0 until 3) {
            val h = halves - 2 * i
            for ((y, row) in HEART.withIndex()) for (x in row.indices) {
                if (row[x] != '#') continue
                val on = h >= 2 || (h == 1 && x <= 3)
                p.set(i * 8 + x, y, if (on) Pix.withAlpha(HEART_ON, glow) else HEART_OFF)
            }
        }
        return p.toBitmap(4)
    }

    private fun resetText(context: Context, l: Limit): String {
        val at = l.resetsAt ?: return context.getString(R.string.not_started)
        val ms = at - System.currentTimeMillis()
        if (ms <= 0) return context.getString(R.string.resetting)
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val span = if (h >= 24) "${h / 24}d ${h % 24}h" else if (h >= 1) "${h}h ${m}m" else "${m}m"
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val clock = time.format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
        val day = time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return context.getString(R.string.resets_span, span, "$day $clock")
    }

    private fun updatedText(context: Context, state: UsageRepo.State): String {
        val at = state.usage?.fetchedAt ?: return ""
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
        val sameDay = Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.DAY_OF_YEAR) ==
            Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val stamp = if (sameDay) time else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))
        return when (state.error) {
            null -> stamp
            "offline" -> context.getString(R.string.offline_since, stamp)
            else -> context.getString(R.string.stale_since, stamp)
        }
    }

    private fun patternRes(themeId: String): Int = when (themeId) {
        "halloween" -> R.drawable.pat_halloween
        "christmas" -> R.drawable.pat_christmas
        "valentine" -> R.drawable.pat_valentine
        "spring" -> R.drawable.pat_spring
        "summer" -> R.drawable.pat_summer
        "space" -> R.drawable.pat_space
        "ocean" -> R.drawable.pat_ocean
        "cyberpunk" -> R.drawable.pat_cyberpunk
        else -> 0
    }

    private fun dpToPx(context: Context, dp: Int) = (dp * context.resources.displayMetrics.density).toInt()

    /** A notch lighter, for labels that should read a bit stronger than captions. */
    private fun Int.brighter(): Int = Pix.mix(this, 0xFFFFFFFF.toInt(), 0.25f)
}

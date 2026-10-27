package dev.pinch.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import dev.pinch.app.R
import dev.pinch.app.Settings
import dev.pinch.app.art.Clawd
import dev.pinch.app.art.Frame
import dev.pinch.app.art.Pix
import dev.pinch.app.art.Scenes
import dev.pinch.app.art.Skin
import dev.pinch.app.art.Stage
import dev.pinch.app.art.Theme
import dev.pinch.app.art.Themes
import dev.pinch.app.data.Level
import dev.pinch.app.data.Limit
import dev.pinch.app.data.UsageRepo
import dev.pinch.app.ui.MainActivity
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
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
    private const val LEFT_COLUMN_DP = 76
    private const val SIDE_PADDING_DP = 28

    class Look(val theme: Theme, val skin: Skin)

    fun look(context: Context): Look {
        val theme = Settings.theme(context)
        return Look(theme, Settings.skin(context, theme))
    }

    /**
     * The widget's shapes. On Android 12+ the launcher is handed all of them and shows the one
     * closest to the widget's real size that fits, so every size from 1x1 to 5x5 gets a layout
     * made for it, and a launcher that briefly reports a small size gets a proper small widget
     * instead of a big one with its rows cut off. The percent and the reset time are in all of them.
     */
    private enum class Kind { MINI, STRIP, NARROW, WIDE }

    private class Variant(val kind: Kind, val w: Int, val h: Int)

    private val VARIANTS = listOf(
        Variant(Kind.MINI, 40, 40),       // 1x1: percent, bar, reset time
        Variant(Kind.MINI, 40, 110),      // 1x2: and the week
        Variant(Kind.STRIP, 110, 40),     // 2x1 to 5x1: one row, everything side by side
        Variant(Kind.NARROW, 110, 110),   // 2x2
        Variant(Kind.NARROW, 110, 200),   // 2x3 and taller: header, hearts, week reset
        Variant(Kind.WIDE, 200, 110),     // 3 wide and up: the crab's stage
        Variant(Kind.WIDE, 200, 130),
        Variant(Kind.WIDE, 200, 165),
        Variant(Kind.WIDE, 200, 195),
        Variant(Kind.WIDE, 200, 225)
    )

    /** Android's own rule: the variant closest to the size among those that fit, else the smallest. */
    private fun pick(w: Int, h: Int): Variant =
        VARIANTS.filter { it.w <= w && it.h <= h }.minByOrNull { (w - it.w) * (w - it.w) + (h - it.h) * (h - it.h) }
            ?: VARIANTS.first()

    /** Pictures shared by every size variant, drawn once (the launcher stores each only once). */
    private class Art(val stage: List<Bitmap>, val flipMs: Int, val hearts: List<Bitmap>)

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
        look: Look = look(context),
        /** False for the in-app preview, which has a fixed size and wants that one layout. */
        responsive: Boolean = true,
        /** The refresh arrow gives one quick turn: the answer to a tap on it. */
        nudge: Boolean = false
    ): RemoteViews {
        val art = art(context, state, signedIn, widthDp, act, look)
        if (responsive && Build.VERSION.SDK_INT >= 31) {
            return RemoteViews(VARIANTS.associate { k ->
                SizeF(k.w.toFloat(), k.h.toFloat()) to one(context, state, signedIn, k, look, art, nudge)
            })
        }
        return one(context, state, signedIn, pick(widthDp, heightDp), look, art, nudge)
    }

    private fun art(
        context: Context, state: UsageRepo.State, signedIn: Boolean, widthDp: Int, act: List<Frame>?, look: Look
    ): Art {
        val theme = look.theme
        val usage = state.usage
        val session = usage?.session
        val authProblem = !signedIn || state.error == "auth_error"
        val halves = if (authProblem) null else usage?.heartHalves
        val hearts = when {
            halves == null -> emptyList()
            // Last bit of health: the hearts beat.
            halves in 1..2 -> hearts(halves, 1f).let { still -> listOf(still, hearts(halves, 0.6f)) + List(5) { still } }
            else -> listOf(hearts(halves, 1f))
        }

        // The stage: one seamless loop of composed frames (scene + moving things + crab).
        val stageDp = (widthDp - SIDE_PADDING_DP - LEFT_COLUMN_DP).coerceAtLeast(80)
        val stageW = stageDp / DP_PER_PX
        val hour = LocalDateTime.now().hour
        val mood = moodOf(state, signedIn)
        val home = Stage.home(stageW, if (authProblem) null else session?.percent)
        val dm = context.resources.displayMetrics
        // Drawing the loop is the slow part of a redraw. A refresh that changed only text (or
        // nothing) reuses the last loop, so its numbers show at once. One-shot acts are not kept.
        val key = "${theme.id}|${look.skin.id}|$stageW|$hour|$mood|$halves|$home|${dm.widthPixels}x${dm.heightPixels}"
        if (act == null) lastArt?.let { (k, art) -> if (k == key) return art }
        val beats = if (act != null) Stage.act(act, home, stageW) else Stage.loop(mood, halves ?: 6, home, stageW, look.skin.id)
        // The launcher caps a widget's bitmaps at about 1.5 screens of pixels: pick the
        // sharpest scale that keeps the loop inside 70% of that, else skip every other frame.
        val budget = dm.widthPixels.toLong() * dm.heightPixels * 4 * 3 / 2 * 7 / 10
        fun cost(scale: Int, frames: Int) = stageW.toLong() * scale * Scenes.H * scale * 2 * frames
        val scale = (4 downTo 2).firstOrNull { cost(it, beats.size) <= budget } ?: 2
        val stride = if (cost(scale, beats.size) <= budget) 1 else 2
        val bg = Scenes.background(theme.scene, stageW, hour)
        val frames = beats.indices.step(stride).map { t ->
            Stage.compose(bg, theme.scene, hour, t, beats.size, beats[t], look.skin).toBitmap(scale, Bitmap.Config.RGB_565)
        }
        return Art(frames, (Stage.FRAME_MS * stride).toInt(), hearts).also { if (act == null) lastArt = key to it }
    }

    /** The last mood loop drawn, with what it was drawn from. */
    @Volatile
    private var lastArt: Pair<String, Art>? = null

    private fun one(
        context: Context, state: UsageRepo.State, signedIn: Boolean, k: Variant, look: Look, art: Art,
        nudge: Boolean
    ): RemoteViews {
        val theme = look.theme
        val v = RemoteViews(
            context.packageName,
            when (k.kind) {
                Kind.MINI, Kind.NARROW -> R.layout.widget_compact
                Kind.STRIP -> R.layout.widget_strip
                Kind.WIDE -> R.layout.widget
            }
        )
        // Everything but the full layout is short of width: notes there are the short ones.
        val small = k.kind != Kind.WIDE
        val usage = state.usage
        val session = usage?.session
        val week = usage?.weekly
        val authProblem = !signedIn || state.error == "auth_error"
        // The week is used up: that, not the session, is what says whether Claude can be used.
        val blocked = !authProblem && usage?.weekMaxed == true

        // ---- colours (a view a layout does not have is skipped by the launcher)
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
        v.setViewVisibility(R.id.refresh, if (nudge) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.refresh_nudge, if (nudge) View.VISIBLE else View.GONE)
        if (Build.VERSION.SDK_INT >= 31) {
            v.setColorStateList(R.id.refresh_nudge, "setIndeterminateTintList", ColorStateList.valueOf(theme.muted))
        }

        // ---- header
        v.setTextViewText(R.id.plan, usage?.plan?.takeIf { it.isNotEmpty() }?.let { "· $it" } ?: "")

        // ---- big number: the session used and what is left, or the week once it is used up
        when {
            blocked -> {
                v.setTextViewText(R.id.pct, "${week!!.percent}%")
                v.setTextColor(R.id.pct, Themes.CRIT)
                v.setTextViewText(R.id.left_text, context.getString(R.string.week_used_up))
            }
            session != null && !authProblem -> {
                v.setTextViewText(R.id.pct, "${session.percent}%")
                v.setTextColor(R.id.pct, if (session.percent >= 85) levelColor(session.percent) else theme.textHi)
                v.setTextViewText(R.id.left_text, context.getString(R.string.pct_left, (100 - session.percent).coerceAtLeast(0)))
            }
            else -> {
                v.setTextViewText(R.id.pct, context.getString(R.string.dash_pct))
                v.setTextViewText(R.id.left_text, "")
            }
        }
        when (k.kind) {
            Kind.MINI -> v.setTextViewTextSize(R.id.pct, TypedValue.COMPLEX_UNIT_SP, if (k.h >= 110) 26f else 22f)
            Kind.NARROW -> v.setTextViewTextSize(R.id.pct, TypedValue.COMPLEX_UNIT_SP, 30f)
            else -> {}
        }

        // ---- hearts and the stage
        v.removeAllViews(R.id.hearts)
        v.setViewVisibility(R.id.hearts, if (art.hearts.isEmpty()) View.GONE else View.VISIBLE)
        for (h in art.hearts) v.addView(R.id.hearts, frame(context, h))
        if (k.kind == Kind.WIDE) {
            v.removeAllViews(R.id.stage_anim)
            for (f in art.stage) v.addView(R.id.stage_anim, frame(context, f))
            v.setInt(R.id.stage_anim, "setFlipInterval", art.flipMs)
        }

        // ---- session bar and its reset countdown
        bar(v, "session", if (blocked) 100 else session?.percent, authProblem)
        v.setViewVisibility(R.id.session_reset, View.GONE)
        v.setViewVisibility(R.id.session_note, View.VISIBLE)
        fun say(long: Int, short: Int, color: Int, vararg args: Any) =
            note(v, context.getString(if (small) short else long, *args), color)
        when {
            !signedIn -> say(R.string.tap_to_sign_in, R.string.tap_to_sign_in_short, Themes.WARN)
            state.error == "auth_error" -> say(R.string.sign_in_again, R.string.sign_in_again_short, Themes.CRIT)
            state.error == "format_changed" -> say(R.string.api_changed, R.string.api_changed_short, Themes.WARN)
            session == null && week == null -> say(
                if (state.error != null) R.string.no_data_offline else R.string.loading,
                if (state.error != null) R.string.offline_short else R.string.loading, theme.text
            )
            blocked -> say(
                R.string.week_full, R.string.week_full_short, Themes.CRIT,
                if (small) dayText(context, week!!.resetsAt) else whenText(context, week!!.resetsAt)
            )
            session?.resetsAt == null -> say(R.string.window_not_started, R.string.not_started_short, theme.text)
            else -> {
                v.setViewVisibility(R.id.session_note, View.GONE)
                v.setViewVisibility(R.id.session_reset, View.VISIBLE)
                val base = SystemClock.elapsedRealtime() + (session.resetsAt - System.currentTimeMillis())
                v.setChronometer(
                    R.id.session_reset, base,
                    context.getString(if (k.kind == Kind.MINI) R.string.resets_in_chrono_short else R.string.resets_in_chrono), true
                )
                v.setChronometerCountDown(R.id.session_reset, true)
            }
        }

        // ---- week
        if (week != null && !authProblem) {
            v.setTextViewText(R.id.week_pct, "${week.percent}%")
            if (week.percent >= 85) v.setTextColor(R.id.week_pct, levelColor(week.percent))
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

        // ---- fit the space: rows drop away as it shrinks; the percent and reset time never do
        fun show(id: Int, on: Boolean) = v.setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
        val hasWeek = week != null && !authProblem
        when (k.kind) {
            Kind.MINI -> {
                show(R.id.header, false)
                show(R.id.hearts, false)
                show(R.id.left_text, k.h >= 110)
                show(R.id.week_row, k.h >= 110 && hasWeek)
                show(R.id.week_bars, false)
                show(R.id.week_reset, false)
                v.setInt(R.id.content, "setGravity", Gravity.CENTER)
            }
            Kind.NARROW -> {
                val tall = k.h >= 200
                show(R.id.header, tall)
                if (!tall) show(R.id.hearts, false)
                show(R.id.week_row, hasWeek)
                show(R.id.week_bars, tall && hasWeek)
                show(R.id.week_reset, tall && hasWeek)
                v.setInt(R.id.content, "setGravity", Gravity.CENTER_VERTICAL or Gravity.START)
            }
            Kind.STRIP -> show(R.id.week_row, hasWeek)
            Kind.WIDE -> {
                show(R.id.header, k.h >= 130)
                val showWeek = k.h >= 165
                show(R.id.week_row, showWeek)
                show(R.id.week_bars, showWeek)
                // Anthropic only reports per-model numbers on plans with per-model limits (Max). On Pro
                // every model shares one limit, so the row would have nothing true to say: hide it.
                val hasModels = usage == null || authProblem || usage.models.isNotEmpty()
                show(R.id.model_row, k.h >= 195 && hasModels)
                show(R.id.footer, k.h >= 225 && status != null)
            }
        }
        v.setContentDescription(R.id.content, spoken(context, state, signedIn))

        // ---- taps
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        v.setOnClickPendingIntent(R.id.content, open)
        v.setOnClickPendingIntent(R.id.refresh, WidgetActions.pending(context, WidgetActions.ACTION_REFRESH))
        if (k.kind == Kind.WIDE) v.setOnClickPendingIntent(
            R.id.stage,
            if (authProblem) open else WidgetActions.pending(context, WidgetActions.ACTION_TAP)
        )
        return v
    }

    /** The widget read out by a screen reader, in one sentence. */
    private fun spoken(context: Context, state: UsageRepo.State, signedIn: Boolean): String {
        val u = state.usage
        if (!signedIn || state.error == "auth_error" || u == null) return context.getString(R.string.widget_name)
        val parts = mutableListOf<String>()
        u.session?.let { s ->
            parts += context.getString(R.string.say_session, s.percent)
            s.resetsAt?.let { parts += context.getString(R.string.say_resets, whenText(context, it)) }
        }
        u.weekly?.let { parts += context.getString(R.string.say_week, it.percent) }
        return parts.joinToString(". ")
    }

    /** How the crab feels, from the numbers. */
    fun moodOf(state: UsageRepo.State, signedIn: Boolean): Clawd.Mood {
        val session = state.usage?.session
        return when {
            !signedIn || state.error == "auth_error" || session == null -> Clawd.Mood.SLEEP
            // Out of either limit, he is out: a maxed week stops Claude as surely as a maxed session.
            session.percent >= 100 || state.usage.weekMaxed -> Clawd.Mood.DEAD
            session.resetsAt == null -> Clawd.Mood.SLEEP // the 5-hour window has not started
            session.percent >= 85 -> Clawd.Mood.CRIT
            else -> Clawd.Mood.IDLE
        }
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
            // A used-up limit is red, the loudest level: a grey full bar read as an empty one.
            Level.CRIT, Level.DEAD -> 2
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
        Level.CRIT, Level.DEAD -> Themes.CRIT
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
        return context.getString(R.string.resets_span, span, whenText(context, at))
    }

    /** "Tue 3:00 PM", or just the time when it is today. */
    fun whenText(context: Context, at: Long?): String {
        if (at == null) return context.getString(R.string.not_started)
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        val clock = time.format(DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
        if (time.toLocalDate() == LocalDate.now()) return clock
        return "${time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $clock"
    }

    /** "Fri", or the time when it is today: for the small sizes. */
    private fun dayText(context: Context, at: Long?): String {
        if (at == null) return context.getString(R.string.not_started)
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        if (time.toLocalDate() == LocalDate.now()) return whenText(context, at)
        return time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
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


    /** A notch lighter, for labels that should read a bit stronger than captions. */
    private fun Int.brighter(): Int = Pix.mix(this, 0xFFFFFFFF.toInt(), 0.25f)
}

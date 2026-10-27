package dev.pinch.app.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.pinch.app.BuildConfig
import dev.pinch.app.R
import dev.pinch.app.Settings
import dev.pinch.app.art.Clawd
import dev.pinch.app.art.Frame
import dev.pinch.app.art.Pix
import dev.pinch.app.art.Scenes
import dev.pinch.app.art.Skins
import dev.pinch.app.art.Theme
import dev.pinch.app.art.Themes
import dev.pinch.app.auth.ClaudeAuth
import dev.pinch.app.data.History
import dev.pinch.app.data.UsageRepo
import dev.pinch.app.widget.ClawdWidget
import dev.pinch.app.widget.RefreshWorker
import dev.pinch.app.widget.WidgetRenderer
import java.util.concurrent.Executors

/**
 * The app behind the widget: sign in, pick a theme and Clawd's skin, see a live preview.
 * Built in code so the whole screen can recolour itself with the theme.
 */
class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var theme: Theme
    private var busy = false
    private var statusText: TextView? = null
    private var codeInput: EditText? = null
    private var widgetCount = -1
    private var scroll: ScrollView? = null
    /** Which numbers the screen shows, so a refresh made elsewhere (the widget, the background) is noticed. */
    private var shown: String? = null

    private val redraw = Runnable { if (!busy && !isFinishing && stamp(UsageRepo.cached(this)) != shown) build() }
    // One refresh writes several keys: wait for the last before redrawing.
    private val usageChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.removeCallbacks(redraw)
        main.postDelayed(redraw, 150)
    }

    private fun stamp(s: UsageRepo.State) = "${s.usage?.fetchedAt}|${s.error}|${s.status?.indicator}|${s.status?.description}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
        // Redraw every widget on the home screen: one the launcher kept across a reinstall, or
        // one that missed its first update, comes back to life just by opening the app.
        val app = applicationContext
        io.execute {
            RefreshWorker.schedule(app)
            ClawdWidget.updateAll(app)
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the browser with the code on the clipboard: point at the field.
        if (!ClaudeAuth.isSignedIn(this) && ClaudeAuth.hasPendingLogin(this)) codeInput?.requestFocus()
        // Opening the app is a good moment for fresh numbers, if the last ones are over a minute old.
        val age = System.currentTimeMillis() - (UsageRepo.cached(this).usage?.fetchedAt ?: 0L)
        if (ClaudeAuth.isSignedIn(this) && age > 60_000) refreshNow()
        // A widget was added or removed while we were away (from the launcher or the pin dialog),
        // or the widget's refresh button fetched new numbers.
        else if (ClawdWidget.ids(this).size != widgetCount || stamp(UsageRepo.cached(this)) != shown) build()
        // While on screen, show what a background refresh fetches.
        UsageRepo.listen(this, usageChanged)
        tick.run()
    }

    override fun onPause() {
        UsageRepo.unlisten(this, usageChanged)
        main.removeCallbacks(redraw)
        main.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ screen ----

    private fun build() {
        theme = Settings.theme(this)
        window.statusBarColor = theme.bg
        window.navigationBarColor = theme.bg
        val signedIn = ClaudeAuth.isSignedIn(this)
        val state = UsageRepo.cached(this)
        shown = stamp(state)
        ticks.clear()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }

        // Top bar: the name, the plan, and a refresh button.
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(getString(R.string.app_name), 22f, theme.textHi, bold = true))
        state.usage?.plan?.takeIf { signedIn && it.isNotEmpty() }?.let { plan ->
            top.addView(text(plan, 12f, theme.accent, bold = true).apply {
                background = rounded(Pix.withAlpha(theme.accent, 0.16f), 10)
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (signedIn) top.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_refresh)
            setColorFilter(theme.text)
            contentDescription = getString(R.string.refresh_now)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(theme.card, 20)
            setOnClickListener { refreshNow() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        col.addView(top, LinearLayout.LayoutParams(-1, dp(48)))

        // Clawd on his scene.
        val clawd = ClawdView(this).apply {
            this.theme = this@MainActivity.theme
            skin = Settings.skin(this@MainActivity, this@MainActivity.theme)
            halves = state.usage?.heartHalves ?: 6
            position = if (signedIn) state.usage?.session?.percent else null
            mood = WidgetRenderer.moodOf(state, signedIn)
            contentDescription = getString(R.string.clawd)
            clipToOutline = true
            background = rounded(theme.card, 16)
        }
        col.addView(clawd, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(text(getString(R.string.tap_clawd), 12f, theme.muted).apply { setPadding(0, dp(6), 0, 0); gravity = Gravity.CENTER })

        if (signedIn && state.usage != null) {
            col.addView(header(getString(R.string.now)))
            col.addView(card { nowSection(it, state) })
            col.addView(header(getString(R.string.history)))
            col.addView(card { historySection(it) })
        } else {
            // Not connected yet: signing in is the only thing that matters, so it comes first.
            col.addView(header(getString(R.string.account)))
            col.addView(card { accountSection(it, signedIn, state) })
        }

        // Live preview of the real widget, at the size picked.
        col.addView(header(getString(R.string.widget)))
        col.addView(card { widgetSection(it, state, signedIn) })

        // Look.
        col.addView(header(getString(R.string.theme)))
        col.addView(card { box ->
            val flow = FlowLayout(this, dp(8))
            val choice = Settings.themeChoice(this)
            val seasonal = Themes.byId(Themes.seasonal(java.time.LocalDate.now()))
            flow.addView(chip(getString(R.string.by_season, seasonal.name), seasonal.accent, choice == "auto") { setTheme("auto") })
            for (t in Themes.ALL) flow.addView(chip(t.name, t.accent, choice == t.id) { setTheme(t.id) })
            box.addView(flow)
        })
        col.addView(header(getString(R.string.skin)))
        col.addView(card { box -> skinGrid(box) })

        // Refresh interval.
        col.addView(header(getString(R.string.refresh_every)))
        col.addView(card { box ->
            val flow = FlowLayout(this, dp(8))
            val cur = Settings.intervalMinutes(this)
            for (m in intArrayOf(5, 15, 30, 60)) flow.addView(chip(getString(R.string.minutes, m), theme.accent, cur == m) {
                Settings.setIntervalMinutes(this, m)
                RefreshWorker.schedule(this, replace = true)
                build()
            })
            box.addView(flow)
            box.addView(text(getString(R.string.refresh_reset_note), 13f, theme.muted).apply { setPadding(0, dp(12), 0, 0) })
            // Battery optimisation (and Battery Saver on top of it) can hold background refreshes
            // back for an hour or more. "Unrestricted" lifts that; no permission needed to point there.
            if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
                box.addView(text(getString(R.string.battery_hint), 13f, theme.muted).apply { setPadding(0, dp(10), 0, 0) })
                box.addView(button(getString(R.string.battery_open), secondary = true) {
                    try {
                        startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        })

        if (signedIn && state.usage != null) {
            col.addView(header(getString(R.string.account)))
            col.addView(card { accountSection(it, signedIn, state) })
        }

        // Footer: version, not affiliated, and the credit the crab art's license asks for.
        col.addView(text(getString(R.string.disclaimer, BuildConfig.VERSION_NAME), 12f, theme.muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(28), dp(8), 0)
        })

        // Rebuilt on every choice: keep the reader where they were instead of jumping to the top.
        val keepY = scroll?.scrollY ?: 0
        val view = ScrollView(this).apply {
            setBackgroundColor(theme.bg)
            isFillViewport = true
            addView(col)
        }
        scroll = view
        setContentView(view)
        view.post { view.scrollTo(0, keepY) }
        tick.run()
    }

    // ------------------------------------------------------------- now card ----

    /** Countdowns on screen, redrawn every second while the app is open. */
    private val ticks = mutableListOf<() -> Unit>()
    private val tick = object : Runnable {
        override fun run() {
            main.removeCallbacks(this)
            if (isFinishing || ticks.isEmpty()) return
            ticks.forEach { it() }
            main.postDelayed(this, 1_000)
        }
    }

    private fun nowSection(box: LinearLayout, state: UsageRepo.State) {
        val u = state.usage ?: return
        val s = u.session
        val w = u.weekly

        // A used-up week stops everything: say it first, plainly.
        if (u.weekMaxed && w != null) {
            box.addView(text(getString(R.string.week_blocked, WidgetRenderer.whenText(this, w.resetsAt)), 14f, theme.textHi).apply {
                background = rounded(Pix.withAlpha(Themes.CRIT, 0.18f), 10, Pix.withAlpha(Themes.CRIT, 0.5f))
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }

        // Session: the big number.
        box.addView(label(getString(R.string.session_5h)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val pct = s?.percent
        row.addView(text(if (pct != null) "$pct%" else getString(R.string.dash_pct), 44f,
            if (pct != null && pct >= 85) WidgetRenderer.levelColor(pct) else theme.textHi, bold = true).apply { includeFontPadding = false })
        row.addView(text(getString(R.string.used), 14f, theme.muted).apply { setPadding(dp(6), 0, 0, dp(6)) })
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (pct != null) row.addView(text(getString(R.string.pct_left, (100 - pct).coerceAtLeast(0)), 14f, theme.text).apply { setPadding(0, 0, 0, dp(6)) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        box.addView(meter(pct ?: 0), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val sessionLine = text("", 13f, theme.text).apply { setPadding(0, dp(8), 0, 0) }
        box.addView(sessionLine)
        val reset = s?.resetsAt
        if (reset == null) sessionLine.text = getString(R.string.no_window)
        else ticks += {
            val left = reset - System.currentTimeMillis()
            if (left <= 0) build() // the window reset: the numbers settle to 0%
            else sessionLine.text = getString(R.string.resets_in_at, clock(left), WidgetRenderer.whenText(this, reset))
        }

        // Week.
        if (w != null) {
            val wr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            wr.addView(label(getString(R.string.week_long)))
            wr.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            wr.addView(text("${w.percent}%", 16f, if (w.percent >= 85) WidgetRenderer.levelColor(w.percent) else theme.textHi, bold = true))
            box.addView(wr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
            box.addView(meter(w.percent), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            val weekLine = text("", 13f, theme.text).apply { setPadding(0, dp(8), 0, 0) }
            box.addView(weekLine)
            val wReset = w.resetsAt
            if (wReset == null) weekLine.text = getString(R.string.not_started)
            else ticks += {
                val left = wReset - System.currentTimeMillis()
                if (left <= 0) build()
                else weekLine.text = getString(R.string.resets_in_at, span(left), WidgetRenderer.whenText(this, wReset))
            }
        }

        // The model with its own limit that is furthest along (Max plans only).
        u.topModel?.let { m ->
            val mr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            mr.addView(label(getString(R.string.top_model)))
            mr.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            mr.addView(text("${m.label} · ${m.percent}%", 14f, theme.text))
            box.addView(mr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        }

        // Service status and freshness.
        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        state.status?.let { st ->
            val (dot, msg) = when (st.indicator) {
                "none" -> Themes.OK to getString(R.string.status_ok)
                "minor" -> Themes.WARN to st.description
                else -> Themes.CRIT to st.description
            }
            foot.addView(text("●  $msg", 12f, dot).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, -2, 1f))
        } ?: foot.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        foot.addView(text(getString(R.string.updated_at, WidgetRenderer.whenText(this, u.fetchedAt)), 12f, theme.muted).apply { setPadding(dp(8), 0, 0, 0) })
        box.addView(foot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        if (state.error != null && state.error != "auth_error") {
            box.addView(text(errorText(state.error), 12f, Themes.WARN).apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun meter(pct: Int) = Meter(this).apply {
        this.pct = pct
        color = WidgetRenderer.levelColor(pct)
        track = Pix.withAlpha(theme.muted, 0.22f)
    }

    private fun label(s: String) = text(s.uppercase(), 11f, theme.muted, bold = true).apply { letterSpacing = 0.1f }

    /** 4:20:27 */
    private fun clock(ms: Long): String {
        val t = ms / 1000
        return "%d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60)
    }

    /** 2d 4h, 3h 12m, 12m */
    private fun span(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        return if (h >= 24) "${h / 24}d ${h % 24}h" else if (h >= 1) "${h}h ${m}m" else "${m}m"
    }

    // -------------------------------------------------------------- history ----

    private var historyMode = HistoryChart.Mode.DAY

    private fun historySection(box: LinearLayout) {
        val points = History.load(this)
        val chart = HistoryChart(this).apply { theme = this@MainActivity.theme }
        val caption = text("", 13f, theme.text).apply { setPadding(0, dp(10), 0, 0) }
        val empty = text(getString(R.string.history_empty), 13f, theme.muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(36), dp(16), dp(36))
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(theme.bg, 12)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val modes = listOf(
            HistoryChart.Mode.DAY to R.string.history_day,
            HistoryChart.Mode.SESSIONS to R.string.history_sessions,
            HistoryChart.Mode.WEEKS to R.string.history_weeks
        )
        val tabViews = mutableListOf<TextView>()
        fun select(mode: HistoryChart.Mode) {
            historyMode = mode
            modes.forEachIndexed { i, (m, _) ->
                val on = m == mode
                tabViews[i].background = if (on) rounded(theme.card, 10) else null
                tabViews[i].setTextColor(if (on) theme.textHi else theme.muted)
            }
            chart.show(mode, points)
            chart.visibility = if (chart.isEmpty) View.GONE else View.VISIBLE
            empty.visibility = if (chart.isEmpty) View.VISIBLE else View.GONE
            caption.text = historyCaption(mode, chart)
            caption.visibility = if (chart.isEmpty) View.GONE else View.VISIBLE
        }
        for ((m, label) in modes) {
            val tab = text(getString(label), 13f, theme.muted, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
                setOnClickListener { select(m) }
            }
            tabViews += tab
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(tabs)
        box.addView(chart, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        box.addView(empty)
        box.addView(caption)
        box.addView(text(getString(R.string.history_private), 11f, theme.muted).apply { setPadding(0, dp(10), 0, 0) })
        select(historyMode)
    }

    private fun historyCaption(mode: HistoryChart.Mode, chart: HistoryChart): String {
        val (peak, avg) = chart.stats() ?: return ""
        return when (mode) {
            HistoryChart.Mode.DAY -> getString(R.string.history_day_caption, peak)
            HistoryChart.Mode.SESSIONS -> resources.getQuantityString(R.plurals.history_sessions_caption, chart.count(), chart.count(), avg, chart.maxedCount())
            HistoryChart.Mode.WEEKS -> getString(R.string.history_weeks_caption, avg, peak)
        }
    }

    // --------------------------------------------------------------- widget ----

    /** Home screen sizes to preview, in cells. */
    private val previewSizes = listOf(1 to 1, 2 to 1, 2 to 2, 4 to 1, 4 to 2, 4 to 3)
    private var previewSize = 5

    private fun widgetSection(box: LinearLayout, state: UsageRepo.State, signedIn: Boolean) {
        val flow = FlowLayout(this, dp(6))
        for ((i, size) in previewSizes.withIndex()) {
            flow.addView(chip("${size.first}×${size.second}", theme.accent, i == previewSize, withDot = false) {
                previewSize = i
                build()
            })
        }
        box.addView(flow)
        val (cols, rows) = previewSizes[previewSize]
        // A home screen cell is about a quarter of the screen wide; the card is a little narrower.
        val cell = (pxToDp(resources.displayMetrics.widthPixels) - 32 - 28) / 4
        val wDp = cell * cols
        val hDp = (cell * rows).coerceAtLeast(cell)
        val holder = FrameLayout(this)
        box.addView(holder, LinearLayout.LayoutParams(dp(wDp), dp(hDp)).apply { topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL })
        try {
            val rv = WidgetRenderer.build(this, state, signedIn, wDp, hDp, responsive = false)
            holder.addView(rv.apply(applicationContext, holder))
        } catch (_: Exception) {
            holder.addView(text(getString(R.string.preview_unavailable), 13f, theme.muted))
        }
        box.addView(text(getString(R.string.any_size_hint), 12f, theme.muted).apply { setPadding(0, dp(12), 0, 0) })
        // Already on the home screen: say so, and keep "add" as a quiet option for a second one.
        val placed = ClawdWidget.ids(this).size
        widgetCount = placed
        if (placed > 0) {
            box.addView(text(resources.getQuantityString(R.plurals.widget_placed, placed, placed), 14f, Themes.OK)
                .apply { setPadding(0, dp(10), 0, 0) })
            box.addView(button(getString(R.string.add_another_widget), secondary = true) { pinWidget() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        } else {
            box.addView(button(getString(R.string.add_widget)) { pinWidget() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            box.addView(text(getString(R.string.add_widget_hint), 12f, theme.muted).apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    private fun accountSection(box: LinearLayout, signedIn: Boolean, state: UsageRepo.State) {
        val status = text("", 14f, theme.text)
        statusText = status
        box.addView(status)
        if (signedIn) {
            when (state.error) {
                "auth_error" -> say(getString(R.string.session_expired), Themes.CRIT)
                null -> say(getString(R.string.connected), Themes.OK)
                else -> say(getString(R.string.connected_but, errorText(state.error)), Themes.WARN)
            }
            box.addView(button(getString(R.string.sign_out), secondary = true) { signOut() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            if (state.error == "auth_error") box.addView(signInViews(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        } else {
            say(getString(R.string.not_connected), theme.text)
            box.addView(signInViews(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }

    private fun signInViews(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(getString(R.string.step1), 13f, theme.muted))
        box.addView(button(getString(R.string.sign_in)) { startSignIn() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        box.addView(text(getString(R.string.step2), 13f, theme.muted).apply { setPadding(0, dp(14), 0, 0) })
        val input = EditText(this).apply {
            hint = getString(R.string.paste_code)
            setHintTextColor(theme.faint)
            setTextColor(theme.textHi)
            textSize = 14f
            // No suggestions, no learning: the keyboard must not remember the code.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            // Incognito keyboard: nothing typed here may feed the keyboard's personal dictionary.
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            // Only real accessibility tools (screen readers) may read this field, not other apps.
            if (Build.VERSION.SDK_INT >= 34) setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
            background = rounded(theme.bg, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                // Keep the code out of screenshots and the recent-apps thumbnail while it is on screen.
                override fun afterTextChanged(s: Editable?) = secure(!s.isNullOrEmpty())
            })
        }
        codeInput = input
        box.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        box.addView(button(getString(R.string.connect)) { connect(input) }.apply { filterTouchesWhenObscured = true },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return box
    }

    private fun skinGrid(box: LinearLayout) {
        val flow = FlowLayout(this, dp(8))
        val choice = Settings.skinChoice(this)
        val sand = Scenes.sand(theme.scene)
        val cellW = (pxToDp(resources.displayMetrics.widthPixels) - 32 - 28 - 24) / 4
        val autoSkin = Skins.get(this, theme.skin)
        val items = listOf("auto" to autoSkin) + Skins.all(this).map { it.key to it.value }
        for ((id, skin) in items) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(6))
                background = rounded(if (id == choice) Pix.mix(theme.card, theme.accent, 0.25f) else theme.bg, 10, if (id == choice) theme.accent else 0)
                setOnClickListener { Settings.setSkinChoice(this@MainActivity, id); ClawdWidget.updateAll(this@MainActivity); build() }
            }
            val img = ImageView(this).apply {
                setImageBitmap(WidgetRenderer.crabBitmap(Frame(), skin, sand))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            cell.addView(img, LinearLayout.LayoutParams(dp(cellW - 12), dp((cellW - 12) * 23 / 36)))
            val name = if (id == "auto") getString(R.string.skin_auto) else skin.name
            cell.addView(text(name, 11f, theme.text).apply { gravity = Gravity.CENTER; maxLines = 2 })
            flow.addView(cell, ViewGroup.LayoutParams(dp(cellW), -2))
        }
        box.addView(flow)
    }

    // ----------------------------------------------------------------- actions ----

    private fun startSignIn() {
        val url = ClaudeAuth.buildLoginUrl(this)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                // Straight to the user's own browser: an installed app that claims claude.com
                // links can't put up a look-alike sign-in page and catch the password.
                defaultBrowser()?.let { setPackage(it) }
            })
            say(getString(R.string.after_approve), theme.text)
        } catch (_: ActivityNotFoundException) {
            say(getString(R.string.no_browser), Themes.CRIT)
        }
    }

    private fun connect(input: EditText) {
        if (busy) return
        val code = input.text.toString().trim()
        if (code.isEmpty()) {
            pasteFromClipboard(input)
            if (input.text.isEmpty()) { say(getString(R.string.paste_first), Themes.WARN); return }
            return connect(input)
        }
        if (!ClaudeAuth.hasPendingLogin(this)) { say(getString(R.string.start_first), Themes.WARN); return }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        busy = true
        say(getString(R.string.connecting), theme.text)
        val app = applicationContext
        io.execute {
            val err = ClaudeAuth.completeLogin(app, code)
            val state = if (err == null) UsageRepo.refresh(app) else null
            main.post {
                busy = false
                if (isFinishing) return@post
                if (err == null) {
                    input.text.clear()
                    clearClipboardIfCode(code)
                    secure(false)
                    RefreshWorker.schedule(app)
                    ClawdWidget.updateAll(app, state!!)
                    Toast.makeText(app, R.string.connected, Toast.LENGTH_SHORT).show()
                    build()
                } else say(errorText(err), Themes.CRIT)
            }
        }
    }

    private fun refreshNow() {
        if (busy) return
        busy = true
        say(getString(R.string.refreshing), theme.text)
        val app = applicationContext
        io.execute {
            val state = UsageRepo.refresh(app)
            ClawdWidget.updateAll(app, state)
            main.post { busy = false; if (!isFinishing) build() }
        }
    }

    private fun signOut() {
        ClaudeAuth.signOut(this)
        UsageRepo.clear(this)
        History.clear(this)
        ClawdWidget.updateAll(this)
        build()
    }

    private fun setTheme(id: String) {
        Settings.setThemeChoice(this, id)
        ClawdWidget.updateAll(this)
        build()
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, ClawdWidget::class.java), null, null)
        } else Toast.makeText(this, R.string.add_widget_manual, Toast.LENGTH_LONG).show()
    }

    private fun pasteFromClipboard(input: EditText) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (!clip.isNullOrEmpty() && clip.contains("#") && clip.length < 512) input.setText(clip)
    }

    /** The code is single-use, but there is no reason to leave it lying on the clipboard. */
    private fun clearClipboardIfCode(code: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
            if (clip == code && Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
        } catch (_: Exception) {
        }
    }

    /** The package of the browser that opens a plain web link, or null if there is no single default. */
    private fun defaultBrowser(): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val info = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val pkg = info.activityInfo?.packageName ?: return null
        // "android" means the system chooser: no default set, let Android ask as usual.
        return pkg.takeIf { it != "android" }
    }

    private fun secure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun say(msg: String, color: Int) {
        statusText?.text = msg
        statusText?.setTextColor(color)
    }

    private fun errorText(err: String?): String = when (err) {
        "auth_error" -> getString(R.string.err_auth)
        "state_mismatch" -> getString(R.string.err_state)
        "rate_limited" -> getString(R.string.err_rate)
        "offline" -> getString(R.string.err_offline)
        "format_changed" -> getString(R.string.err_format)
        else -> getString(R.string.err_other, err ?: "?")
    }

    // ------------------------------------------------------------------ pieces ----

    private fun header(s: String) = text(s.uppercase(), 12f, theme.muted, bold = true).apply {
        letterSpacing = 0.1f
        setPadding(dp(2), dp(22), 0, dp(8))
    }

    private fun card(fill: (LinearLayout) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(theme.card, 14)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        fill(this)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun button(label: String, secondary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (secondary) theme.text else readableOn(theme.accent))
        background = rounded(if (secondary) theme.bg else theme.accent, 10)
        stateListAnimator = null
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
    }

    private fun chip(label: String, dot: Int, selected: Boolean, withDot: Boolean = true, onClick: () -> Unit) = TextView(this).apply {
        text = if (withDot) "●  $label" else label
        textSize = 14f
        setTextColor(theme.text)
        background = rounded(if (selected) Pix.mix(theme.card, theme.accent, 0.3f) else theme.bg, 18, if (selected) theme.accent else 0)
        setPadding(dp(12), dp(7), dp(14), dp(7))
        if (withDot) {
            val span = android.text.SpannableString(text)
            span.setSpan(android.text.style.ForegroundColorSpan(dot), 0, 1, 0)
            text = span
        }
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radiusDp: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun readableOn(bg: Int): Int {
        val l = 0.299 * ((bg shr 16) and 0xFF) + 0.587 * ((bg shr 8) and 0xFF) + 0.114 * (bg and 0xFF)
        return if (l > 150) 0xFF111114.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun pxToDp(px: Int) = (px / resources.displayMetrics.density).toInt()
}

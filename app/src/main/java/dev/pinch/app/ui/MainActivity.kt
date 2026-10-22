package dev.pinch.app.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the browser with the code on the clipboard: point at the field.
        if (!ClaudeAuth.isSignedIn(this) && ClaudeAuth.hasPendingLogin(this)) codeInput?.requestFocus()
        // Opening the app is a good moment for fresh numbers, if the last ones are over a minute old.
        val age = System.currentTimeMillis() - (UsageRepo.cached(this).usage?.fetchedAt ?: 0L)
        if (ClaudeAuth.isSignedIn(this) && age > 60_000) refreshNow()
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

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        // Hero: Clawd on his scene.
        col.addView(text(getString(R.string.app_name).uppercase(), 13f, theme.muted, bold = true).apply { letterSpacing = 0.1f })
        col.addView(text(getString(R.string.tagline), 15f, theme.text).apply { setPadding(0, dp(4), 0, dp(12)) })
        val clawd = ClawdView(this).apply {
            this.theme = this@MainActivity.theme
            skin = Settings.skin(this@MainActivity, this@MainActivity.theme)
            halves = state.usage?.heartHalves ?: 6
            position = if (signedIn) state.usage?.session?.percent else null
            mood = WidgetRenderer.moodOf(state, signedIn)
            contentDescription = getString(R.string.clawd)
            clipToOutline = true
            background = rounded(theme.card, 14)
        }
        col.addView(clawd, LinearLayout.LayoutParams(-1, -2))
        col.addView(text(getString(R.string.tap_clawd), 12f, theme.muted).apply { setPadding(0, dp(6), 0, 0); gravity = Gravity.CENTER })

        // Account.
        col.addView(header(getString(R.string.account)))
        col.addView(card { accountSection(it, signedIn, state) })

        // Live preview of the real widget layout.
        col.addView(header(getString(R.string.widget)))
        col.addView(card { box ->
            val holder = FrameLayout(this)
            box.addView(holder, LinearLayout.LayoutParams(-1, dp(230)))
            try {
                val rv = WidgetRenderer.build(this, state, signedIn, pxToDp(resources.displayMetrics.widthPixels) - 64, 230)
                holder.addView(rv.apply(applicationContext, holder))
            } catch (_: Exception) {
                holder.addView(text(getString(R.string.preview_unavailable), 13f, theme.muted))
            }
            box.addView(button(getString(R.string.add_widget)) { pinWidget() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            box.addView(text(getString(R.string.add_widget_hint), 12f, theme.muted).apply { setPadding(0, dp(6), 0, 0) })
        })

        // Theme.
        col.addView(header(getString(R.string.theme)))
        col.addView(card { box ->
            val flow = FlowLayout(this, dp(8))
            val choice = Settings.themeChoice(this)
            val seasonal = Themes.byId(Themes.seasonal(java.time.LocalDate.now()))
            flow.addView(chip(getString(R.string.by_season, seasonal.name), seasonal.accent, choice == "auto") { setTheme("auto") })
            for (t in Themes.ALL) flow.addView(chip(t.name, t.accent, choice == t.id) { setTheme(t.id) })
            box.addView(flow)
        })

        // Skins.
        col.addView(header(getString(R.string.skin)))
        col.addView(card { box -> skinGrid(box) })

        // Refresh interval.
        col.addView(header(getString(R.string.refresh_every)))
        col.addView(card { box ->
            val flow = FlowLayout(this, dp(8))
            val cur = Settings.intervalMinutes(this)
            for (m in intArrayOf(15, 30, 60)) flow.addView(chip(getString(R.string.minutes, m), theme.accent, cur == m) {
                Settings.setIntervalMinutes(this, m)
                RefreshWorker.schedule(this, replace = true)
                build()
            })
            box.addView(flow)
        })

        // Privacy.
        col.addView(header(getString(R.string.privacy)))
        col.addView(card { box ->
            box.addView(text(getString(R.string.privacy_body), 13f, theme.text))
            box.addView(text(getString(R.string.disclaimer, BuildConfig.VERSION_NAME), 12f, theme.muted).apply { setPadding(0, dp(10), 0, 0) })
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.bg)
            isFillViewport = true
            addView(col)
        }
        setContentView(scroll)
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
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button(getString(R.string.refresh_now)) { refreshNow() }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            row.addView(button(getString(R.string.sign_out), secondary = true) { signOut() }, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
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
            if (Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
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
            cell.addView(img, LinearLayout.LayoutParams(dp(cellW - 12), dp((cellW - 12) * 23 / 30)))
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
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

    private fun chip(label: String, dot: Int, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = "●  $label"
        textSize = 14f
        setTextColor(theme.text)
        background = rounded(if (selected) Pix.mix(theme.card, theme.accent, 0.3f) else theme.bg, 18, if (selected) theme.accent else 0)
        setPadding(dp(12), dp(7), dp(14), dp(7))
        val span = android.text.SpannableString(text)
        span.setSpan(android.text.style.ForegroundColorSpan(dot), 0, 1, 0)
        text = span
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

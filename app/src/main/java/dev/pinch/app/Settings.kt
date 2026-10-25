package dev.pinch.app

import android.content.Context
import dev.pinch.app.art.Skin
import dev.pinch.app.art.Skins
import dev.pinch.app.art.Theme
import dev.pinch.app.art.Themes

/** The user's look choices. Not secret; plain app-private preferences. */
object Settings {
    private const val PREFS = "settings"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeChoice(context: Context): String = prefs(context).getString("theme", "auto") ?: "auto"
    fun setThemeChoice(context: Context, id: String) = prefs(context).edit().putString("theme", id).apply()

    fun skinChoice(context: Context): String = prefs(context).getString("skin", "auto") ?: "auto"
    fun setSkinChoice(context: Context, id: String) = prefs(context).edit().putString("skin", id).apply()

    fun theme(context: Context): Theme = Themes.resolve(themeChoice(context))

    /** "auto" dresses Clawd for the theme: a witch hat at Halloween, a Santa hat at Christmas... */
    fun skin(context: Context, theme: Theme = theme(context)): Skin {
        val choice = skinChoice(context)
        return Skins.get(context, if (choice == "auto") theme.skin else choice)
    }

    /**
     * Debug builds only: the app pretends to be signed in and shows the numbers the demo hook
     * stored, for screenshots. Always false in a release build.
     */
    fun demo(context: Context): Boolean = BuildConfig.DEBUG && prefs(context).getBoolean("demo", false)
    fun setDemo(context: Context, on: Boolean) = prefs(context).edit().putBoolean("demo", on).apply()

    /** Minutes between background refreshes; WorkManager's floor is 15. */
    fun intervalMinutes(context: Context): Int = prefs(context).getInt("interval", 15)
    fun setIntervalMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt("interval", minutes).apply()

    // ---- alerts: every one starts switched off

    fun sessionAlert(context: Context): Boolean = prefs(context).getBoolean("alert_session", false)
    fun setSessionAlert(context: Context, on: Boolean) = prefs(context).edit().putBoolean("alert_session", on).apply()

    /** Percent of the 5-hour session at which to alert. */
    fun sessionAlertAt(context: Context): Int = prefs(context).getInt("alert_session_at", 80)

    /** A new threshold may be worth an alert in the same window, so the "already told" mark goes. */
    fun setSessionAlertAt(context: Context, pct: Int) =
        prefs(context).edit().putInt("alert_session_at", pct).remove("alert_session_fired").apply()

    fun weekAlert(context: Context): Boolean = prefs(context).getBoolean("alert_week", false)
    fun setWeekAlert(context: Context, on: Boolean) = prefs(context).edit().putBoolean("alert_week", on).apply()

    fun weekAlertAt(context: Context): Int = prefs(context).getInt("alert_week_at", 90)
    fun setWeekAlertAt(context: Context, pct: Int) =
        prefs(context).edit().putInt("alert_week_at", pct).remove("alert_week_fired").apply()

    fun resetAlert(context: Context): Boolean = prefs(context).getBoolean("alert_reset", false)
    fun setResetAlert(context: Context, on: Boolean) = prefs(context).edit().putBoolean("alert_reset", on).apply()

    /** The reset time of the window an alert last went out for (0: none), so each fires once per window. */
    fun alertFired(context: Context, key: String): Long = prefs(context).getLong("alert_${key}_fired", 0L)
    fun setAlertFired(context: Context, key: String, resetsAt: Long) =
        prefs(context).edit().putLong("alert_${key}_fired", resetsAt).apply()
}

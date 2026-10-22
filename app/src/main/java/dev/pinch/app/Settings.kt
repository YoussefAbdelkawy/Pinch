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
}

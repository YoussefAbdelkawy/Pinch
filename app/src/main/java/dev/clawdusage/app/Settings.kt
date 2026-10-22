package dev.clawdusage.app

import android.content.Context
import dev.clawdusage.app.art.Skin
import dev.clawdusage.app.art.Skins
import dev.clawdusage.app.art.Theme
import dev.clawdusage.app.art.Themes

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

    /** Minutes between background refreshes; WorkManager's floor is 15. */
    fun intervalMinutes(context: Context): Int = prefs(context).getInt("interval", 15)
    fun setIntervalMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt("interval", minutes).apply()
}

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

    // ---- the look: each account has its own theme and skin

    /** The first account keeps the keys from before there were several. */
    private fun key(base: String, account: String) = if (account == Accounts.FIRST) base else "${base}_$account"

    fun themeChoice(context: Context, account: String): String = prefs(context).getString(key("theme", account), "auto") ?: "auto"
    fun setThemeChoice(context: Context, account: String, id: String) = prefs(context).edit().putString(key("theme", account), id).apply()

    fun skinChoice(context: Context, account: String): String = prefs(context).getString(key("skin", account), "auto") ?: "auto"
    fun setSkinChoice(context: Context, account: String, id: String) = prefs(context).edit().putString(key("skin", account), id).apply()

    /** A new account starts dressed like the one it was added from; after that each goes its own way. */
    fun copyLook(context: Context, from: String, to: String) = prefs(context).edit()
        .putString(key("theme", to), themeChoice(context, from))
        .putString(key("skin", to), skinChoice(context, from))
        .apply()

    fun forgetLook(context: Context, account: String) {
        if (account == Accounts.FIRST) return // its keys are the defaults; a new first account resets them
        prefs(context).edit().remove(key("theme", account)).remove(key("skin", account)).apply()
    }

    fun theme(context: Context, account: String): Theme = Themes.resolve(themeChoice(context, account))

    /** "auto" dresses Clawd for the theme: a witch hat at Halloween, a Santa hat at Christmas... */
    fun skin(context: Context, account: String, theme: Theme = theme(context, account)): Skin {
        val choice = skinChoice(context, account)
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

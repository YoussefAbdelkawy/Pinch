package dev.pinch.app

import android.content.Context

/**
 * The Claude accounts Pinch shows, up to [MAX], in the order of the widget's dots, with the
 * names the user gave them, the app's open tab and which account each widget is on.
 * Not secret; plain app-private preferences. The tokens live in the encrypted store.
 *
 * Accounts have ids "a1" to "a3". The first, "a1", keeps the storage names from before there
 * were several accounts, so an update just goes on with the sign-in that was there.
 */
object Accounts {
    const val MAX = 3
    const val FIRST = "a1"
    private val IDS = listOf("a1", "a2", "a3")
    private const val PREFS = "accounts"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The accounts in order. Before the first save, the one sign-in from earlier versions (if any). */
    fun list(context: Context): List<String> {
        val raw = prefs(context).getString("list", null)
        if (raw == null) {
            val legacy = Settings.demo(context) || dev.pinch.app.auth.ClaudeAuth.hasTokens(context, FIRST)
            return if (legacy) listOf(FIRST) else emptyList()
        }
        return raw.split(',').filter { it in IDS }.distinct()
    }

    private fun save(context: Context, ids: List<String>) = prefs(context).edit().putString("list", ids.joinToString(",")).commit()

    /** Several accounts: only then do the names, dots and tabs show. */
    fun multi(context: Context) = list(context).size > 1

    /** An id not in use, for the next account; null when there are [MAX] already. */
    fun freeId(context: Context): String? = (IDS - list(context).toSet()).firstOrNull()

    fun add(context: Context, id: String) {
        val now = list(context)
        if (id !in now && now.size < MAX) save(context, now + id)
    }

    /** Writes the list down: from then on it no longer follows the sign-in of earlier versions. */
    fun keep(context: Context) {
        if (prefs(context).getString("list", null) == null) save(context, list(context))
    }

    fun remove(context: Context, id: String) {
        save(context, list(context) - id)
        prefs(context).edit().remove("name_$id").apply()
    }

    fun name(context: Context, id: String): String = prefs(context).getString("name_$id", null) ?: ""
    fun setName(context: Context, id: String, name: String) =
        prefs(context).edit().putString("name_$id", name.trim().take(NAME_MAX)).apply()

    /** The name, or "Account 2" when it has none yet. */
    fun label(context: Context, id: String): String =
        name(context, id).ifEmpty { context.getString(R.string.account_n, list(context).indexOf(id).coerceAtLeast(0) + 1) }

    const val NAME_MAX = 20

    /** The tab the app shows. */
    fun current(context: Context): String = valid(context, prefs(context).getString("tab", null))
    fun setCurrent(context: Context, id: String) = prefs(context).edit().putString("tab", id).apply()

    /**
     * The id being signed in as a new account, while "Add account" is under way (kept here, as
     * the app may be closed during the trip to the browser); null otherwise.
     */
    fun adding(context: Context): String? = prefs(context).getString("adding", null)?.takeIf { it !in list(context) }
    fun setAdding(context: Context, id: String?) = prefs(context).edit().putString("adding", id).apply()

    /** The account a widget shows: the one last tapped to there, else the first. */
    fun forWidget(context: Context, widgetId: Int): String = valid(context, prefs(context).getString("widget_$widgetId", null))
    fun setForWidget(context: Context, widgetId: Int, id: String) = prefs(context).edit().putString("widget_$widgetId", id).apply()
    fun forgetWidget(context: Context, widgetId: Int) = prefs(context).edit().remove("widget_$widgetId").apply()

    /** The account after [id] in the dots' order, back to the first after the last. */
    fun next(context: Context, id: String): String = nextOf(list(context), id)

    fun nextOf(ids: List<String>, id: String): String {
        if (ids.isEmpty()) return FIRST
        val i = ids.indexOf(id)
        return ids[(i + 1).mod(ids.size)]
    }

    private fun valid(context: Context, id: String?): String {
        val ids = list(context)
        return if (id != null && id in ids) id else ids.firstOrNull() ?: FIRST
    }
}

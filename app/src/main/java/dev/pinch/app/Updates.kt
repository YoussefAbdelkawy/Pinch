package dev.pinch.app

import android.content.Context
import dev.pinch.app.data.Http
import org.json.JSONObject

/**
 * Pinch is installed from GitHub, not a store, so nothing tells people a new version is out.
 * Once a day at most, when the app opens, it asks GitHub for the latest release (a public
 * request: no account, no token, nothing about the user is sent) and the app shows a card
 * when it is newer than this build.
 */
object Updates {

    private const val LATEST_URL = "https://api.github.com/repos/YoussefAbdelkawy/Pinch/releases/latest"
    /** Only release pages of this repository are ever opened from the card. */
    const val RELEASES_PREFIX = "https://github.com/YoussefAbdelkawy/Pinch/releases/"
    private const val EVERY_MS = 24L * 3_600_000
    private const val PREFS = "updates"

    data class Release(val version: String, val url: String)

    /** The newer release already found, if any (no network). */
    fun available(context: Context): Release? {
        val p = prefs(context)
        val v = p.getString("version", null) ?: return null
        val url = p.getString("url", null) ?: return null
        if (!isNewer(v, BuildConfig.VERSION_NAME) || v == p.getString("dismissed", null)) return null
        return Release(v, url)
    }

    /** Asks GitHub, unless it was asked in the last day. Call off the main thread. True when something changed. */
    fun check(context: Context, force: Boolean = false): Boolean {
        if (!Settings.checkUpdates(context)) return false
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("checked", 0L) < EVERY_MS) return false
        val r = Http.get(LATEST_URL, mapOf("Accept" to "application/vnd.github+json"))
        if (r.code != 200 || r.body == null) return false
        val release = parse(r.body) ?: return false
        val before = p.getString("version", null)
        p.edit().putLong("checked", now).putString("version", release.version).putString("url", release.url).apply()
        return before != release.version
    }

    /** "Not now" on the card: this version is not shown again; a later one will be. */
    fun dismiss(context: Context, version: String) = prefs(context).edit().putString("dismissed", version).apply()

    fun parse(body: String): Release? = try {
        val o = JSONObject(body)
        val tag = o.optString("tag_name").removePrefix("v").trim()
        val url = o.optString("html_url")
        if (tag.isEmpty() || o.optBoolean("draft") || o.optBoolean("prerelease") || !url.startsWith(RELEASES_PREFIX)) null
        else Release(tag, url)
    } catch (_: Exception) {
        null
    }

    /** "1.10.0" is newer than "1.9.2"; anything after a "-" (like "-beta") is ignored. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

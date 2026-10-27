package dev.pinch.app.data

import android.content.Context
import android.content.SharedPreferences
import dev.pinch.app.Settings
import dev.pinch.app.auth.ClaudeAuth
import org.json.JSONObject
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Fetches usage and remembers the last good numbers. Only non-secret data lives here
 * (percentages, reset times, status text) in app-private storage.
 */
object UsageRepo {

    private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    private const val STATUS_URL = "https://status.claude.com/api/v2/summary.json"
    private const val PREFS = "usage_cache"
    private const val STATUS_WAIT_MS = 2_000L

    data class State(
        val usage: Usage?,
        /** null, or "auth_error" / "offline" / "rate_limited" / "http_nnn" from the last attempt. */
        val error: String?,
        val status: ServiceStatus?,
        /** The 5-hour window just reset: Clawd celebrates. */
        val justReset: Boolean = false
    )

    data class ServiceStatus(val indicator: String, val description: String)

    /** The last numbers, settled to now: a window whose reset time has passed shows as reset. */
    fun cached(context: Context): State = raw(context).let { it.copy(usage = it.usage?.settled()) }

    /** The last numbers exactly as fetched. */
    private fun raw(context: Context): State {
        val p = prefs(context)
        val usage = p.getString("usage", null)?.let { Usage.fromCache(it) }
        val status = p.getString("status_indicator", null)?.let {
            ServiceStatus(it, p.getString("status_desc", "") ?: "")
        }
        return State(usage, p.getString("error", null), status)
    }

    /** Stores numbers as if just fetched; used by the debug demo hook. */
    fun store(context: Context, usage: Usage, status: ServiceStatus?) {
        prefs(context).edit().putString("usage", usage.toJson()).remove("error")
            .putString("status_indicator", status?.indicator).putString("status_desc", status?.description).apply()
    }

    @Synchronized
    fun refresh(context: Context): State {
        if (Settings.demo(context)) return cached(context)
        val before = raw(context)
        // Both requests at once: the status page must not hold up the numbers, and a slow one
        // is given up on after a few seconds (the last status stays).
        val statusTask = FutureTask { fetchStatus() }.also { Thread(it).start() }
        val result = fetchUsage(context)
        val status = try {
            statusTask.get(STATUS_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        } ?: before.status
        val p = prefs(context).edit()
        status?.let { p.putString("status_indicator", it.indicator).putString("status_desc", it.description) }

        if (result.second != null) {
            p.putString("error", result.second).apply()
            return before.copy(usage = before.usage?.settled(), error = result.second, status = status)
        }
        val usage = result.first!!
        p.putString("usage", usage.toJson()).remove("error").apply()
        History.record(context, usage)

        // Same rule as Clawd: the reset time moved forward and the percent dropped. The week
        // resetting counts too: after a maxed-out week that is the bigger relief.
        fun reset(old: Limit?, new: Limit?) = old?.resetsAt != null && new != null &&
            (new.resetsAt == null || new.resetsAt > old.resetsAt) && new.percent < old.percent
        val justReset = reset(before.usage?.session, usage.session) || reset(before.usage?.weekly, usage.weekly)
        return State(usage.settled(), null, status, justReset = justReset)
    }

    fun clear(context: Context) = prefs(context).edit().clear().commit()

    /** Told whenever stored numbers change (a refresh from the widget or the background). Keep a reference: it is held weakly. */
    fun listen(context: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).registerOnSharedPreferenceChangeListener(l)

    fun unlisten(context: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).unregisterOnSharedPreferenceChangeListener(l)

    private fun fetchUsage(context: Context): Pair<Usage?, String?> {
        val token = ClaudeAuth.accessToken(context) ?: return null to (ClaudeAuth.lastError ?: "auth_error")
        var r = callUsage(token)
        if (r.code == 401 || r.code == 403) {
            val fresh = ClaudeAuth.refresh(context, force = true) ?: return null to (ClaudeAuth.lastError ?: "auth_error")
            r = callUsage(fresh)
        }
        return when {
            r.code == 200 && r.body != null -> try {
                val u = Usage.parse(JSONObject(r.body))
                // A 200 with none of the fields we know means Anthropic reshaped the endpoint:
                // say so plainly instead of showing old numbers as if nothing happened.
                if (u.session == null && u.weekly == null && u.models.isEmpty()) null to "format_changed" else u to null
            } catch (_: Exception) {
                null to "format_changed"
            }
            r.code == -1 -> null to "offline"
            r.code == 401 || r.code == 403 -> null to "auth_error"
            r.code == 429 || r.body?.contains("rate_limit_error") == true -> null to "rate_limited"
            else -> null to "http_${r.code}"
        }
    }

    private fun callUsage(token: String) = Http.get(
        USAGE_URL,
        mapOf("Authorization" to "Bearer $token", "anthropic-beta" to "oauth-2025-04-20")
    )

    private fun fetchStatus(): ServiceStatus? {
        val r = Http.get(STATUS_URL)
        if (r.code != 200 || r.body == null) return null
        return try {
            val s = JSONObject(r.body).getJSONObject("status")
            ServiceStatus(s.optString("indicator", "none"), s.optString("description", ""))
        } catch (_: Exception) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

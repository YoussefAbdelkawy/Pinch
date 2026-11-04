package dev.pinch.app.data

import android.content.Context
import android.content.SharedPreferences
import dev.pinch.app.Accounts
import dev.pinch.app.Settings
import dev.pinch.app.auth.ClaudeAuth
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Fetches usage and remembers the last good numbers, per account. Only non-secret data lives
 * here (percentages, reset times, status text) in app-private storage. The service status is
 * one for everyone.
 */
object UsageRepo {

    private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    private const val STATUS_URL = "https://status.claude.com/api/v2/summary.json"
    private const val PREFS = "usage_cache"
    private const val STATUS_WAIT_MS = 2_000L
    private const val USAGE_WAIT_MS = 30_000L

    data class State(
        val usage: Usage?,
        /** null, or "auth_error" / "offline" / "rate_limited" / "http_nnn" from the last attempt. */
        val error: String?,
        val status: ServiceStatus?,
        /** The 5-hour window just reset: Clawd celebrates. */
        val justReset: Boolean = false
    )

    data class ServiceStatus(val indicator: String, val description: String)

    /** The last numbers of [account], settled to now: a window whose reset time has passed shows as reset. */
    fun cached(context: Context, account: String): State = raw(context, account).let { it.copy(usage = it.usage?.settled()) }

    /** Every account's last numbers. */
    fun cachedAll(context: Context): Map<String, State> = Accounts.list(context).associateWith { cached(context, it) }

    /** The last numbers exactly as fetched. */
    private fun raw(context: Context, account: String): State {
        val p = prefs(context, account)
        val usage = p.getString("usage", null)?.let { Usage.fromCache(it) }
        return State(usage, p.getString("error", null), status(context))
    }

    private fun status(context: Context): ServiceStatus? {
        val p = prefs(context, Accounts.FIRST)
        return p.getString("status_indicator", null)?.let { ServiceStatus(it, p.getString("status_desc", "") ?: "") }
    }

    private fun saveStatus(context: Context, s: ServiceStatus) =
        prefs(context, Accounts.FIRST).edit().putString("status_indicator", s.indicator).putString("status_desc", s.description).apply()

    /** Stores numbers as if just fetched; used by the debug demo hook. */
    fun store(context: Context, account: String, usage: Usage, status: ServiceStatus?, error: String? = null) {
        prefs(context, account).edit().putString("usage", usage.toJson()).putString("error", error).apply()
        status?.let { saveStatus(context, it) }
    }

    /** Fetches one account (and the status page). */
    fun refresh(context: Context, account: String): State = refreshAll(context, listOf(account)).getValue(account)

    /**
     * Fetches every account side by side, and the status page once, so switching accounts on
     * the widget always shows numbers as fresh as the last refresh.
     */
    fun refreshAll(context: Context, accounts: List<String> = Accounts.list(context)): Map<String, State> {
        if (Settings.demo(context)) return accounts.associateWith { cached(context, it) }
        val before = status(context)
        // The status page must not hold up the numbers: a slow one is given up on after a few
        // seconds (the last status stays).
        val statusTask = FutureTask { fetchStatus() }.also { Thread(it).start() }
        val tasks = accounts.associateWith { a -> FutureTask { refreshOne(context, a) }.also { Thread(it).start() } }
        val results = tasks.mapValues { (a, t) ->
            try {
                t.get(USAGE_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                raw(context, a).let { it.copy(usage = it.usage?.settled(), error = "offline") }
            }
        }
        val status = try {
            statusTask.get(STATUS_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        }
        status?.let { saveStatus(context, it) }
        val shown = status ?: before
        return results.mapValues { it.value.copy(status = shown) }
    }

    private val locks = ConcurrentHashMap<String, Any>()

    private fun refreshOne(context: Context, account: String): State = synchronized(locks.getOrPut(account) { Any() }) {
        val before = raw(context, account)
        val result = fetchUsage(context, account)
        val p = prefs(context, account).edit()
        if (result.second != null) {
            p.putString("error", result.second).apply()
            return before.copy(usage = before.usage?.settled(), error = result.second)
        }
        val usage = result.first!!
        p.putString("usage", usage.toJson()).remove("error").apply()
        History.record(context, account, usage)

        // Same rule as Clawd: the reset time moved forward and the percent dropped. The week
        // resetting counts too: after a maxed-out week that is the bigger relief.
        val justReset = reset(before.usage?.session, usage.session) || reset(before.usage?.weekly, usage.weekly)
        return State(usage.settled(), null, null, justReset = justReset)
    }

    private fun reset(old: Limit?, new: Limit?) = old?.resetsAt != null && new != null &&
        (new.resetsAt == null || new.resetsAt > old.resetsAt) && new.percent < old.percent

    /** Forgets an account's numbers (it was removed). The shared status stays. */
    fun clear(context: Context, account: String) = prefs(context, account).edit().remove("usage").remove("error").commit()

    /**
     * Told whenever stored numbers change (a refresh from the widget or the background), for
     * any account. Keep a reference: it is held weakly.
     */
    fun listen(context: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        allPrefs(context).forEach { it.registerOnSharedPreferenceChangeListener(l) }

    fun unlisten(context: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        allPrefs(context).forEach { it.unregisterOnSharedPreferenceChangeListener(l) }

    private fun allPrefs(context: Context) = listOf("a1", "a2", "a3").map { prefs(context, it) }

    private fun fetchUsage(context: Context, account: String): Pair<Usage?, String?> {
        val token = ClaudeAuth.accessToken(context, account)
            ?: return null to (ClaudeAuth.lastError(account) ?: "auth_error")
        var r = callUsage(token)
        if (r.code == 401 || r.code == 403) {
            val fresh = ClaudeAuth.refresh(context, account, force = true)
                ?: return null to (ClaudeAuth.lastError(account) ?: "auth_error")
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

    /** The first account keeps the file from before there were several. */
    private fun prefs(context: Context, account: String) = context.applicationContext.getSharedPreferences(
        if (account == Accounts.FIRST) PREFS else "${PREFS}_$account", Context.MODE_PRIVATE
    )
}

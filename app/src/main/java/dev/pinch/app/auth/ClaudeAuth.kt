package dev.pinch.app.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import dev.pinch.app.Accounts
import dev.pinch.app.Settings
import dev.pinch.app.data.Http
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Sign-in with Claude, the same "paste the code" OAuth flow Claude Code uses (PKCE, public
 * client, no secret). The phone gets its own refresh token, so it never shares (and rotates
 * away) the one on your computer.
 *
 * Each account (see [Accounts]) has its own tokens. Results are null on success or one of:
 * "auth_error", "state_mismatch", "rate_limited", "offline", "http_<code>", "duplicate".
 */
object ClaudeAuth {

    private const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    private const val AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize"
    private const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
    private const val REDIRECT_URL = "https://platform.claude.com/oauth/code/callback"
    private const val SCOPES = "user:profile user:inference"

    private const val K_ACCESS = "access_token"
    private const val K_REFRESH = "refresh_token"
    private const val K_EXPIRES = "expires_at"
    private const val K_VERIFIER = "pending_verifier"
    private const val K_STATE = "pending_state"
    /** Who the tokens belong to (Anthropic's account and organisation ids), to spot an account added twice. */
    private const val K_WHO = "identity"

    /** Refresh a few minutes early so a fetch never races the expiry. */
    private const val EARLY_MS = 5 * 60_000L

    /** Why the last token request of each account failed, for the caller to report. */
    private val lastErrors = ConcurrentHashMap<String, String>()

    fun lastError(account: String): String? = lastErrors[account]

    private fun store(context: Context) = SecureStore(context)

    /** The first account keeps the key names from before there were several. */
    private fun k(account: String, base: String) = if (account == Accounts.FIRST) base else "$account:$base"

    /** Any account signed in. */
    fun isSignedIn(context: Context): Boolean = Accounts.list(context).isNotEmpty()

    fun hasTokens(context: Context, account: String): Boolean {
        if (Settings.demo(context)) return true
        val s = store(context)
        return s.get(k(account, K_REFRESH)) != null || s.get(k(account, K_ACCESS)) != null
    }

    fun signOut(context: Context, account: String) {
        val s = store(context)
        for (base in listOf(K_ACCESS, K_REFRESH, K_EXPIRES, K_VERIFIER, K_STATE, K_WHO)) s.put(k(account, base), null)
        lastErrors.remove(account)
    }

    private val locks = ConcurrentHashMap<String, Any>()
    private fun lock(account: String): Any = locks.getOrPut(account) { Any() }

    /** Starts a login: keeps verifier and state (they survive the trip to the browser) and returns the URL to open. */
    fun buildLoginUrl(context: Context, account: String): String {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val s = store(context)
        s.put(k(account, K_VERIFIER), verifier)
        s.put(k(account, K_STATE), state)
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("code", "true")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build().toString()
    }

    fun hasPendingLogin(context: Context, account: String) = store(context).get(k(account, K_VERIFIER)) != null

    /**
     * Exchanges the code from the callback page ("code#state") for tokens of [account].
     * "duplicate" when that Claude account is already one of the others: nothing is kept then.
     */
    fun completeLogin(context: Context, account: String, pasted: String): String? {
        val s = store(context)
        val verifier = s.get(k(account, K_VERIFIER)) ?: return "auth_error"
        val expected = s.get(k(account, K_STATE)) ?: return "auth_error"
        val clean = pasted.replace(Regex("\\s+"), "")
        val code = clean.substringBefore("#")
        val state = clean.substringAfter("#", "")
        // The state must match the login we started; anything else could be an injected code.
        if (code.isEmpty() || state.isEmpty() || !MessageDigest.isEqual(state.toByteArray(), expected.toByteArray())) {
            return "state_mismatch"
        }

        val body = JSONObject()
            .put("grant_type", "authorization_code")
            .put("code", code)
            .put("redirect_uri", REDIRECT_URL)
            .put("client_id", CLIENT_ID)
            .put("code_verifier", verifier)
            .put("state", state)
        val r = Http.postJson(TOKEN_URL, body)
        if (r.code != 200 || r.body == null) return tokenError(r)
        return try {
            val json = JSONObject(r.body)
            s.put(k(account, K_VERIFIER), null)
            s.put(k(account, K_STATE), null)
            val who = identity(json)
            if (who != null && Accounts.list(context).any { it != account && s.get(k(it, K_WHO)) == who }) return "duplicate"
            saveTokens(s, account, json, null)
            null
        } catch (_: Exception) {
            "auth_error"
        }
    }

    /** "account:organisation" from a token response, when Anthropic includes them. */
    fun identity(json: JSONObject): String? {
        val acc = json.optJSONObject("account")?.optString("uuid")?.takeIf { it.isNotEmpty() } ?: return null
        val org = json.optJSONObject("organization")?.optString("uuid") ?: ""
        return "$acc:$org"
    }

    /** A valid access token, refreshing it if it is (nearly) expired; null if that failed (see [lastError]). */
    fun accessToken(context: Context, account: String): String? {
        val s = store(context)
        val token = s.get(k(account, K_ACCESS))
        if (token != null && System.currentTimeMillis() < s.getLong(k(account, K_EXPIRES)) - EARLY_MS) return token
        return refresh(context, account, force = false)
    }

    /** One refresh at a time per account; different accounts refresh side by side. */
    fun refresh(context: Context, account: String, force: Boolean): String? = synchronized(lock(account)) {
        val s = store(context)
        // Another caller may have refreshed while we waited for the lock.
        val current = s.get(k(account, K_ACCESS))
        if (!force && current != null && System.currentTimeMillis() < s.getLong(k(account, K_EXPIRES)) - EARLY_MS) return current

        val refreshToken = s.get(k(account, K_REFRESH))
        if (refreshToken == null) {
            lastErrors[account] = "auth_error"
            return null
        }
        val body = JSONObject()
            .put("grant_type", "refresh_token")
            .put("refresh_token", refreshToken)
            .put("client_id", CLIENT_ID)
        val r = Http.postJson(TOKEN_URL, body)
        if (r.code != 200 || r.body == null) {
            val err = tokenError(r)
            lastErrors[account] = err
            // Only a rejected grant means the session is gone; network trouble keeps it.
            if (err == "auth_error") s.put(k(account, K_ACCESS), null)
            return null
        }
        return try {
            val json = JSONObject(r.body)
            saveTokens(s, account, json, refreshToken)
            lastErrors.remove(account)
            json.getString("access_token")
        } catch (_: Exception) {
            lastErrors[account] = "auth_error"
            null
        }
    }

    private fun saveTokens(s: SecureStore, account: String, json: JSONObject, oldRefresh: String?) {
        val access = json.getString("access_token")
        val refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() && it != "null" } ?: oldRefresh
        val expiresIn = json.optLong("expires_in", 28_800L)
        s.put(k(account, K_ACCESS), access)
        s.put(k(account, K_REFRESH), refresh)
        s.putLong(k(account, K_EXPIRES), System.currentTimeMillis() + expiresIn * 1000)
        // A sign-in from an earlier version learns who it is at its next refresh.
        identity(json)?.let { s.put(k(account, K_WHO), it) }
    }

    private fun tokenError(r: Http.Response): String = when {
        r.code == -1 -> "offline"
        r.code == 429 || r.body?.contains("rate_limit_error") == true -> "rate_limited"
        r.code == 400 || r.code == 401 || r.code == 403 -> "auth_error"
        else -> "http_${r.code}"
    }

    private fun randomUrlSafe(bytes: Int): String =
        base64Url(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    private fun base64Url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

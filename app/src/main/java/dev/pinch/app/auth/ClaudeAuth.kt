package dev.pinch.app.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import dev.pinch.app.Settings
import dev.pinch.app.data.Http
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Sign-in with Claude, the same "paste the code" OAuth flow Claude Code uses (PKCE, public
 * client, no secret). The phone gets its own refresh token, so it never shares (and rotates
 * away) the one on your computer.
 *
 * Results are null on success or one of: "auth_error", "state_mismatch", "rate_limited",
 * "offline", "http_<code>".
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

    /** Refresh a few minutes early so a fetch never races the expiry. */
    private const val EARLY_MS = 5 * 60_000L

    @Volatile var lastError: String? = null
        private set

    private fun store(context: Context) = SecureStore(context)

    fun isSignedIn(context: Context): Boolean {
        if (Settings.demo(context)) return true
        val s = store(context)
        return s.get(K_REFRESH) != null || s.get(K_ACCESS) != null
    }

    fun signOut(context: Context) = store(context).clear()

    /** Starts a login: keeps verifier and state (they survive the trip to the browser) and returns the URL to open. */
    fun buildLoginUrl(context: Context): String {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val s = store(context)
        s.put(K_VERIFIER, verifier)
        s.put(K_STATE, state)
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

    fun hasPendingLogin(context: Context) = store(context).get(K_VERIFIER) != null

    /** Exchanges the code from the callback page ("code#state") for tokens. */
    fun completeLogin(context: Context, pasted: String): String? {
        val s = store(context)
        val verifier = s.get(K_VERIFIER) ?: return "auth_error"
        val expected = s.get(K_STATE) ?: return "auth_error"
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
            saveTokens(s, JSONObject(r.body), null)
            s.put(K_VERIFIER, null)
            s.put(K_STATE, null)
            null
        } catch (_: Exception) {
            "auth_error"
        }
    }

    /** A valid access token, refreshing it if it is (nearly) expired; null if that failed (see [lastError]). */
    fun accessToken(context: Context): String? {
        val s = store(context)
        val token = s.get(K_ACCESS)
        if (token != null && System.currentTimeMillis() < s.getLong(K_EXPIRES) - EARLY_MS) return token
        return refresh(context, force = false)
    }

    @Synchronized
    fun refresh(context: Context, force: Boolean): String? {
        val s = store(context)
        // Another caller may have refreshed while we waited for the lock.
        val current = s.get(K_ACCESS)
        if (!force && current != null && System.currentTimeMillis() < s.getLong(K_EXPIRES) - EARLY_MS) return current

        val refreshToken = s.get(K_REFRESH)
        if (refreshToken == null) {
            lastError = "auth_error"
            return null
        }
        val body = JSONObject()
            .put("grant_type", "refresh_token")
            .put("refresh_token", refreshToken)
            .put("client_id", CLIENT_ID)
        val r = Http.postJson(TOKEN_URL, body)
        if (r.code != 200 || r.body == null) {
            lastError = tokenError(r)
            // Only a rejected grant means the session is gone; network trouble keeps it.
            if (lastError == "auth_error") s.put(K_ACCESS, null)
            return null
        }
        return try {
            val json = JSONObject(r.body)
            saveTokens(s, json, refreshToken)
            lastError = null
            json.getString("access_token")
        } catch (_: Exception) {
            lastError = "auth_error"
            null
        }
    }

    private fun saveTokens(s: SecureStore, json: JSONObject, oldRefresh: String?) {
        val access = json.getString("access_token")
        val refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() && it != "null" } ?: oldRefresh
        val expiresIn = json.optLong("expires_in", 28_800L)
        s.put(K_ACCESS, access)
        s.put(K_REFRESH, refresh)
        s.putLong(K_EXPIRES, System.currentTimeMillis() + expiresIn * 1000)
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

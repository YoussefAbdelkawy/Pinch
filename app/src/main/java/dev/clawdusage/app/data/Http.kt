package dev.clawdusage.app.data

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The only network code in the app. HTTPS only, to an allow-list of hosts; nothing else
 * can be reached even by mistake. Response bodies are size-capped.
 */
object Http {

    private val ALLOWED_HOSTS = setOf(
        "api.anthropic.com",       // usage numbers
        "platform.claude.com",     // OAuth token exchange / refresh
        "status.claude.com"        // public service status (no credentials sent)
    )
    private const val MAX_BODY = 256 * 1024
    private const val USER_AGENT = "ClawdUsageAndroid/1"

    class Response(val code: Int, val body: String?)

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response =
        request("GET", url, headers, null)

    fun postJson(url: String, json: JSONObject): Response =
        request("POST", url, mapOf("Content-Type" to "application/json"), json.toString())

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?): Response {
        val u = URL(url)
        require(u.protocol == "https" && u.host in ALLOWED_HOSTS) { "blocked host" }
        val conn = u.openConnection() as HttpsURLConnection
        return try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            Response(code, stream?.use { readCapped(it) })
        } catch (_: Exception) {
            Response(-1, null)
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val buf = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BODY) break
        }
        return out.toString("UTF-8")
    }
}

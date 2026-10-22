package dev.pinch.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One usage window: the 5-hour session, the week, or a per-model weekly limit. */
data class Limit(
    val label: String,
    val percent: Int,
    /** Epoch millis, or null when the window has not started (no usage yet). */
    val resetsAt: Long?
)

data class Usage(
    val session: Limit?,
    val weekly: Limit?,
    val models: List<Limit>,
    val plan: String,
    val fetchedAt: Long
) {
    val sessionLeft: Int? get() = session?.let { (100 - it.percent).coerceIn(0, 100) }

    /** The per-model limit that is furthest along: "the model you use most" this week. */
    val topModel: Limit? get() = models.maxByOrNull { it.percent }

    /** Clawd's health: three hearts with halves, ~16.7% of the session left per half. */
    val heartHalves: Int? get() = session?.let {
        ceil((100 - it.percent).coerceAtLeast(0) * 6 / 100.0).toInt().coerceIn(0, 6)
    }

    fun toJson(): String = JSONObject()
        .put("session", session?.let(::limitJson))
        .put("weekly", weekly?.let(::limitJson))
        .put("models", JSONArray().apply { models.forEach { put(limitJson(it)) } })
        .put("plan", plan)
        .put("fetched_at", fetchedAt)
        .toString()

    companion object {
        /**
         * Parses the /api/oauth/usage response. Claude Code reads it in two shapes, and both can
         * be present: a `limits` array (`session`, `weekly_all`, and `weekly_scoped` per model)
         * and fixed fields (`five_hour`, `seven_day`, `seven_day_<model>`). We merge them, so a
         * field moving from one shape to the other does not lose data.
         */
        fun parse(json: JSONObject, now: Long = System.currentTimeMillis()): Usage {
            var session: Limit? = null
            var weekly: Limit? = null
            val models = linkedMapOf<String, Limit>()

            val arr = json.optJSONArray("limits")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val pct = pctOf(e, "percent", "utilization") ?: continue
                val reset = parseTime(e.optStr("resets_at"))
                val kind = e.optString("kind")
                when {
                    kind == "session" || kind == "five_hour" -> session = Limit("Session", pct, reset)
                    kind == "weekly_all" || kind == "seven_day" -> weekly = Limit("Week", pct, reset)
                    else -> {
                        val scope = e.optJSONObject("scope")
                        val name = scope?.optJSONObject("model")?.optStr("display_name")
                            ?: scope?.optJSONObject("surface")?.optStr("display_name")
                            ?: continue // not tied to a model; nothing to show for it
                        models[name.lowercase()] = Limit(name, pct, reset)
                    }
                }
            }

            if (session == null) session = legacy(json, "five_hour", "Session")
            if (weekly == null) weekly = legacy(json, "seven_day", "Week")
            // Any "seven_day_<model>" field is a per-model weekly limit (opus, sonnet, ...);
            // the rest under that prefix are not models.
            val notModels = setOf("oauth_apps", "overage_included", "cowork")
            for (key in json.keys()) {
                if (!key.startsWith("seven_day_")) continue
                val suffix = key.removePrefix("seven_day_")
                if (suffix in notModels) continue
                val name = suffix.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                if (name.lowercase() in models) continue
                legacy(json, key, name)?.let { models[name.lowercase()] = it }
            }

            val extra = json.optJSONObject("extra_usage")
            val plan = when {
                extra != null && extra.optBoolean("is_enabled", false) ->
                    if (extra.optInt("monthly_limit", 0) >= 20000) "Max 20x" else "Max 5x"
                session == null && weekly == null -> "Free"
                else -> "Pro"
            }
            return Usage(session, weekly, models.values.toList(), plan, now)
        }

        /** A percentage from whichever field carries it; null if none does. */
        private fun pctOf(o: JSONObject, vararg keys: String): Int? {
            for (k in keys) {
                if (!o.has(k) || o.isNull(k)) continue
                val v = o.optDouble(k)
                if (!v.isNaN()) return v.roundToInt().coerceIn(0, 100)
            }
            return null
        }

        fun fromCache(raw: String): Usage? = try {
            val o = JSONObject(raw)
            val models = o.optJSONArray("models") ?: JSONArray()
            Usage(
                session = o.optJSONObject("session")?.let(::limitFrom),
                weekly = o.optJSONObject("weekly")?.let(::limitFrom),
                models = (0 until models.length()).mapNotNull { models.optJSONObject(it)?.let(::limitFrom) },
                plan = o.optString("plan"),
                fetchedAt = o.optLong("fetched_at")
            )
        } catch (_: Exception) {
            null
        }

        private fun legacy(json: JSONObject, key: String, label: String): Limit? {
            val o = json.optJSONObject(key) ?: return null
            val pct = pctOf(o, "utilization", "percent") ?: return null
            return Limit(label, pct, parseTime(o.optStr("resets_at")))
        }

        fun parseTime(iso: String?): Long? = try {
            iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
        } catch (_: Exception) {
            null
        }

        private fun limitJson(l: Limit) = JSONObject()
            .put("label", l.label).put("percent", l.percent).put("resets_at", l.resetsAt ?: JSONObject.NULL)

        private fun limitFrom(o: JSONObject) = Limit(
            o.optString("label"), o.optInt("percent"),
            if (o.isNull("resets_at")) null else o.optLong("resets_at")
        )
    }
}

/** optString gives "null" for JSON null; normalise that and "" to Kotlin null. */
internal fun JSONObject.optStr(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() && it != "null" }

/** Level colours as in Clawd: green, yellow from 60%, red from 85%. */
enum class Level { OK, WARN, CRIT, DEAD;
    companion object {
        fun of(pct: Int): Level = when {
            pct >= 100 -> DEAD
            pct >= 85 -> CRIT
            pct >= 60 -> WARN
            else -> OK
        }
    }
}

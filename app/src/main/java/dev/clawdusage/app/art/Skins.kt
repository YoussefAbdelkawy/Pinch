package dev.clawdusage.app.art

import android.content.Context

/** A colour and the pixels it covers, in Clawd's grid (rows above his head are negative). */
class SkinLayer(val color: Int, val px: List<Pair<Int, Int>>)

class Skin(val id: String, val name: String, val body: Int, val eyes: Int, val layers: List<SkinLayer>)

/**
 * Clawd's skins, in the same text format as clawd-usage-winwidget (assets/skins, MIT):
 *
 *     ---
 *     name: Top hat
 *     body: #D77757      (optional)
 *     eyes: #141416      (optional)
 *     top: -5            first art row, counted from Clawd's top row (optional)
 *     k: #18181B         a colour for each letter of the art
 *     ---
 *     .......kkkkkk.....
 */
object Skins {
    val CLASSIC = Skin("classic", "Classic", Pix.color("#D77757"), Pix.color("#141416"), emptyList())

    private val BOM = 0xFEFF.toChar().toString()

    @Volatile private var cache: Map<String, Skin>? = null

    fun all(context: Context): Map<String, Skin> {
        cache?.let { return it }
        val out = linkedMapOf("classic" to CLASSIC)
        val am = context.assets
        am.list("skins")?.sorted()?.forEach { file ->
            if (!file.endsWith(".txt")) return@forEach
            val text = am.open("skins/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(file.removeSuffix(".txt"), text)?.let { out[it.id] = it }
        }
        return out.also { cache = it }
    }

    fun get(context: Context, id: String): Skin = all(context)[id] ?: CLASSIC

    fun parse(id: String, raw: String): Skin? {
        val m = Regex("(?s)^\\s*---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?(.*)$").find(raw.removePrefix(BOM)) ?: return null
        var name = id
        var body = CLASSIC.body
        var eyes = CLASSIC.eyes
        var top: Int? = null
        val colors = HashMap<Char, Int>()
        val hex = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
        for (line in m.groupValues[1].lines()) {
            val kv = Regex("^\\s*(\\w+)\\s*:\\s*(.*?)\\s*$").find(line) ?: continue
            val (k, v) = kv.destructured
            val ok = hex.matches(v)
            when {
                k == "name" -> name = v
                k == "body" && ok -> body = Pix.color(v)
                k == "eyes" && ok -> eyes = Pix.color(v)
                k == "top" -> v.toIntOrNull()?.let { top = it }
                k.length == 1 && ok -> colors[k[0]] = Pix.color(v)
            }
        }
        val rows = m.groupValues[2].lines().map { it.trimEnd() }.dropLastWhile { it.isEmpty() }
        val start = top ?: -rows.size
        val byColor = LinkedHashMap<Char, MutableList<Pair<Int, Int>>>()
        rows.forEachIndexed { y, row ->
            for (x in 0 until minOf(row.length, Clawd.W)) {
                val c = row[x]
                if (c !in colors) continue
                byColor.getOrPut(c) { mutableListOf() } += x to (start + y)
            }
        }
        return Skin(id, name, body, eyes, byColor.map { (c, px) -> SkinLayer(colors.getValue(c), px) })
    }
}

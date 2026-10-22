package dev.clawdusage.app.art

import kotlin.math.min

/**
 * Clawd, the crab from the Claude Code welcome screen, ported from clawd-usage-winwidget
 * (lib/mascot.ps1, MIT). He is built from the same block glyphs as in the terminal:
 * 9 cells by 3 rows, each cell 2x4 pixels, so the quadrant glyphs map losslessly.
 */
object Clawd {
    const val W = 18          // 2 * 9 cells
    const val H = 12          // 4 * 3 rows; the lower half of the legs row is empty
    const val GROUND = H - 2  // first empty row under the feet

    /** The canvas a frame is drawn on: room for hats and bubbles above, dust at the sides. */
    const val CANVAS_W = 30
    const val CANVAS_H = 20
    const val ORIGIN_X = 6
    const val ORIGIN_Y = CANVAS_H - GROUND

    private val QUAD = mapOf(
        ' ' to 0, '█' to 15, '▀' to 3, '▄' to 12, '▌' to 5, '▐' to 10, '▘' to 1, '▝' to 2,
        '▖' to 4, '▗' to 8, '▛' to 7, '▜' to 11, '▙' to 13, '▟' to 14
    )

    private class Arms(val r1L: String, val r1R: String, val r2L: String, val r2R: String)
    private val ARMS = mapOf(
        "down" to Arms(" ▐", "", "▝▜", "█▀"),
        "up" to Arms("▗▟", "▄", " ▜", "█▘"),
        "right" to Arms(" ▐", "▄", "▝▜", "█▘"),
        "left" to Arms("▗▟", "", " ▜", "█▀")
    )
    private val FEET = mapOf("both" to " ▝▝   ▝▝ ", "left" to " ▝▝      ", "right" to "      ▝▝ ")

    private class Row(val s: String, val from: Int, val to: Int)
    private val FACING = mapOf(
        "right-12" to listOf(Row(" ▐█▜██▛█ ", 2, 8), Row("▝▜██████▀", 2, 7), Row(" ▝▝   ▝▝ ", 0, 0)),
        "right-30" to listOf(Row("  █▛██▛▌ ", 2, 7), Row(" ▝█████▛ ", 2, 7), Row("  ▘▘  ▘▘ ", 0, 0)),
        "right-55" to listOf(Row("  ▐█▛█▜  ", 3, 7), Row("  ▐████  ", 3, 7), Row("  ▝▝ ▝▝  ", 0, 0)),
        "right-75" to listOf(Row("   ██▛▌  ", 3, 6), Row("   ███▌  ", 3, 6), Row("   ▘  ▘  ", 0, 0)),
        "edge" to listOf(Row("   ▐██   ", 4, 6), Row("   ▐██   ", 4, 6), Row("   ▝ ▝   ", 0, 0)),
        "back-105" to listOf(Row("   ███▌  ", 3, 6), Row("   ███▌  ", 3, 6), Row("   ▘  ▘  ", 0, 0)),
        "back-125" to listOf(Row("  ▐████  ", 3, 7), Row("  ▐████  ", 3, 7), Row("  ▝▝ ▝▝  ", 0, 0)),
        "back-150" to listOf(Row("  █████▌ ", 2, 7), Row(" ▝█████▛ ", 2, 7), Row("  ▘▘  ▘▘ ", 0, 0)),
        "back" to listOf(Row(" ▐██████ ", 2, 8), Row("▝▜██████▀", 2, 7), Row(" ▝▝   ▝▝ ", 0, 0)),
        "left-75" to listOf(Row("   ▛██▌  ", 3, 6), Row("   ███▌  ", 3, 6), Row("   ▘  ▘  ", 0, 0)),
        "left-55" to listOf(Row("  ▐▜▛██  ", 3, 7), Row("  ▐████  ", 3, 7), Row("  ▝▝ ▝▝  ", 0, 0)),
        "left-30" to listOf(Row("  ▛██▛█▌ ", 2, 7), Row(" ▝█████▛ ", 2, 7), Row("  ▘▘  ▘▘ ", 0, 0)),
        "left-12" to listOf(Row(" ▐▛███▜█ ", 2, 8), Row("▝▜██████▀", 2, 7), Row(" ▝▝   ▝▝ ", 0, 0))
    )

    /** Black pixels "dx,dy" from each eye cell; ";" separates left and right eye. */
    private val EYES = mapOf(
        "open" to "1,2 1,3", "left" to "0,0 0,1", "right" to "2,0 2,1",
        "closed" to "0,3 1,3", "blink" to "0,3 1,3", "wink" to "1,2 1,3;0,3 1,3",
        "happy" to "0,3 1,2 2,3", "focus" to "1,3", "wide" to "0,1 1,1 0,2 1,2 0,3 1,3",
        "x" to "0,1 2,1 1,2 0,3 2,3", "yawn" to "0,3 1,3", "gasp" to "0,1 2,1 1,2 0,3 2,3"
    )

    private val DUST = Pix.color("#8B8B94")
    private val SWEAT = Pix.color("#93C5FD")
    private val ZZZ = Pix.color("#C4B5FD")
    private val BUBBLE = Pix.color("#FDE047")
    private val CONFETTI = listOf("#F87171", "#FBBF24", "#4ADE80", "#60A5FA", "#A78BFA", "#D77757").map(Pix::color)

    private val GLYPH_Q = listOf(".qq.", "q..q", "..q.", ".q..", "....", ".q..")
    private val GLYPH_EX = listOf(".q.", ".q.", ".q.", ".q.", "...", ".q.")
    private val GLYPH_Z = listOf("qqq", "..q", ".q.", "qqq")

    /**
     * Draws one frame of Clawd onto [dst] with his grid's top-left at ([ox], [oy]).
     * [sand] is the colour of the mound when he burrows.
     */
    fun draw(dst: Pix, f: Frame, skin: Skin, ox: Int, oy: Int, sand: Int) {
        val g = Array(W) { IntArray(H) } // 0 empty, 1 body, 2 eyes / face

        fun paint(glyphs: String, col: Int, row: Int, mode: String) {
            for ((i, c) in glyphs.withIndex()) {
                val fg = if (mode == "lid") 2 else 1
                val bg = when (mode) { "eyes" -> 2; "lid" -> 1; else -> 0 }
                for (sy in 0 until 4) for (sx in 0 until 2) {
                    val on = if (c == '▂') sy == 3 else {
                        val bit = (if (sy < 2) 1 else 4) * (if (sx == 1) 2 else 1)
                        ((QUAD[c] ?: 0) and bit) != 0
                    }
                    val v = if (on) fg else bg
                    val gx = 2 * (col + i) + sx
                    if (v != 0 && gx < W) g[gx][4 * row + sy] = v
                }
            }
        }

        if (f.facing.isNotEmpty()) {
            FACING.getValue(f.facing).forEachIndexed { r, row ->
                paint(row.s.substring(0, row.from), 0, r, "")
                paint(row.s.substring(row.from, row.to), row.from, r, "eyes")
                paint(row.s.substring(row.to), row.to, r, "")
            }
        } else {
            val a = ARMS.getValue(f.arms)
            paint(a.r1L, 0, 0, "")
            paint("██████", 2, 0, "eyes")
            paint(a.r1R, 8, 0, "")
            paint(a.r2L, 0, 1, "")
            paint("█████", 2, 1, "eyes")
            paint(a.r2R, 7, 1, "")
            paint(FEET.getValue(f.feet), 0, 2, "")

            var eyes = f.eyes
            var shift = 0
            if (eyes == "open" && f.look < 0) eyes = "left"
            else if (eyes == "open" && f.look > 0) eyes = "right"
            else if (eyes !in setOf("open", "left", "right", "wink")) shift = f.look
            val sides = EYES.getValue(eyes).split(";")
            val ex = intArrayOf(4, 12)
            for (k in 0..1) {
                for (pt in sides[min(k, sides.size - 1)].split(" ")) {
                    val (dx, dy) = pt.split(",").map(String::toInt)
                    val x = ex[k] + dx + shift
                    if (x in 0 until W) g[x][dy] = 2
                }
            }
            if (eyes == "yawn" || eyes == "gasp") for (x in 8..10) { g[x][4] = 2; g[x][5] = 2 }
        }

        val dy = 4 * f.off + f.dig
        val dx = 2 * f.x
        val lim = if (f.dig > 0) H - 2 else H
        val top = oy - f.lift
        val body = skin.body
        val eyeC = skin.eyes
        for (y in 0 until H) {
            var sy = y
            if (f.flip) { if (y >= H - 2) continue else sy = H - 3 - y }
            val yy = sy + dy
            if (yy < 0 || yy >= lim) continue
            for (x in 0 until W) {
                val c = g[x][y]
                if (c == 0) continue
                val xx = x + dx
                if (xx < 0 || xx >= W) continue
                dst.set(ox + xx, top + yy, if (c == 2) eyeC else body)
            }
        }

        // Accessories: above the head while the head is in frame, on the body like the body;
        // turning keeps only what is above the head, and on his back he loses them all.
        if (!f.flip) {
            for (layer in skin.layers) for ((x, y) in layer.px) {
                if (y >= 0 && f.facing.isNotEmpty()) continue
                val xx = x + dx
                val yy = y + dy
                if (xx < 0 || xx >= W || yy >= lim) continue
                if (if (y < 0) dy < 0 else yy < 0) continue
                dst.set(ox + xx, top + yy, layer.color)
            }
        }

        // Dust puffs at the sides while crouched.
        if (f.poof.isNotEmpty() && f.off > 0) {
            for (cx in intArrayOf(-1, W - 1)) {
                if (f.poof == "dot") dst.set(ox + cx + 1, oy + 9, DUST)
                else { dst.set(ox + cx, oy + 9, DUST); dst.set(ox + cx + 1, oy + 8, DUST); dst.set(ox + cx + 2, oy + 9, DUST) }
            }
        }

        // The sand mound over the burrow, and sand grains flying from the legs.
        if (f.dig >= 1) dst.rect(ox - 2, oy + H - 3, W + 4, 1, sand)
        if (f.dig >= 2) dst.rect(ox - 1, oy + H - 4, W + 2, 1, sand)
        if (f.dig >= 3) { dst.rect(ox - 1, oy + H - 5, 2, 1, sand); dst.rect(ox + W - 1, oy + H - 5, 2, 1, sand) }
        if (f.spray > 0) {
            for (k in 0..1) {
                val d = f.spray + 2 * k
                val sx = (1.5 + 0.9 * d).toInt()
                val sy = ((H - 4) - 0.9 * d * (5 - d) / 2).toInt()
                dst.set(ox - sx, oy + sy, sand)
                dst.set(ox + W + sx - 1, oy + sy, sand)
            }
        }

        // Sweat drop on the forehead when the limit runs low.
        if (f.sweat > 0) {
            val sy = top + dy - 1 + f.sweat
            dst.set(ox + W, sy, SWEAT); dst.set(ox + W, sy + 1, SWEAT); dst.set(ox + W + 1, sy + 1, SWEAT)
        }
        // Z's rising while asleep.
        if (f.zz > 0) {
            val zx = ox + W + 1 + (f.zz % 2)
            val zy = oy + dy - 4 - f.zz
            dst.art(GLYPH_Z, mapOf('q' to Pix.withAlpha(ZZZ, 1f - 0.18f * f.zz)), zx, zy)
        }
        // "?" or "!" over his head.
        f.bubble?.let { ch ->
            val art = if (ch == '?') GLYPH_Q else GLYPH_EX
            dst.art(art, mapOf('q' to BUBBLE), ox + W + 1, top + dy - 7)
        }
        // Confetti falling around him.
        if (f.confetti >= 0) {
            for (i in 0 until 12) {
                val cx = ox - 5 + (i * 37) % (W + 10)
                val cy = oy - 9 + ((i * 13) % 7) + f.confetti * (2 + i % 3) / 2
                if (cy < oy + GROUND) dst.set(cx + (f.confetti + i) % 3 - 1, cy, CONFETTI[i % CONFETTI.size])
            }
        }
    }

    /** A whole frame as a canvas of [CANVAS_W] x [CANVAS_H] logical pixels. */
    fun render(f: Frame, skin: Skin, sand: Int): Pix =
        Pix(CANVAS_W, CANVAS_H).also { draw(it, f, skin, ORIGIN_X, ORIGIN_Y, sand) }

    // ------------------------------------------------------------ animations ----

    /** Every animation runs on 60 ms frames, as in Claude Code. */
    const val FRAME_MS = 60L

    private val POSES = mapOf(
        "default" to "open/down/both", "arms-up" to "open/up/both",
        "look-left" to "left/down/both", "look-right" to "right/down/both"
    )

    private fun F(pose: String, n: Int = 1, off: Int = 0, x: Int = 0, poof: String = ""): List<Frame> {
        val fr = if (pose.startsWith("face:")) Frame(facing = pose.substring(5), off = off, x = x, poof = poof)
        else {
            val (e, a, ft) = (POSES[pose] ?: pose).split("/")
            Frame(eyes = e, arms = a, feet = ft, off = off, x = x, poof = poof)
        }
        return List(n) { fr }
    }

    private fun turn(vararg list: String) = list.flatMap { F("face:$it") }

    private val cr = F("default", 1, off = 1, poof = "dot") + F("default", 1, off = 1, poof = "wave")
    private val bo = F("default")
    private val ts = F("closed/down/both") + bo
    private val jumpSeq = cr + F("arms-up", 3) + bo + cr + F("arms-up", 3) + bo
    private val waddle = F("look-right", 1, x = -9) +
        (-6..-1).flatMap { u -> F("right/down/${if (u % 2 == 0) "left" else "right"}", 2, x = u) }

    /** Claude Code's own click animations. */
    val SEQS: Map<String, List<Frame>> = linkedMapOf(
        "jump" to jumpSeq,
        "look" to F("look-right", 5) + F("look-left", 5) + bo,
        "celebrate" to jumpSeq + F("default", 3, off = 1),
        "skip" to F("default", 1, off = 1, x = -9) + F("arms-up", 2, x = -6) + F("default", 1, x = -6) +
            F("default", 1, off = 1, x = -6) + F("arms-up", 2, x = -3) + F("default", 1, x = -3) +
            F("default", 1, off = 1, x = -3) + F("arms-up", 2) + cr + bo,
        "spin" to F("look-left", 2) + F("look-right", 2) + F("look-left", 2) + F("arms-up", 3) + bo,
        "peekaboo" to F("default", 1, off = 3) + F("default", 3, off = 2) + F("look-right", 3, off = 2) +
            F("look-left", 3, off = 2) + F("default", 2, off = 2) + F("default", 1, off = 1) +
            F("arms-up", 4) + F("default", 2) + ts,
        "drop" to F("arms-up", 1, off = -3) + F("arms-up", 2, off = -2) + F("arms-up", 2, off = -1) +
            F("arms-up", 1) + cr + F("default", 3) + ts,
        "waddle" to waddle + F("look-right", 2) + F("default", 2) + ts,
        "peek" to F("look-right", 1, x = -9) + F("look-right", 5, x = -6) + F("look-right", 3, x = -9) +
            F("look-right", 4, x = -5) + F("arms-up", 2, x = -3) + F("arms-up", 2) + cr + bo,
        "wink" to F("wink/down/both", 5) + bo,
        "boop" to F("closed/down/both", 1, off = 1, poof = "dot") + F("closed/down/both", 2, off = 1, poof = "wave") +
            F("look-left", 4) + F("default", 2) + ts,
        "tap" to F("open/down/left", 2) + F("open/down/right", 2) + F("open/down/left", 2) +
            F("open/down/right", 2) + F("open/down/left", 1) + F("open/down/right", 1) + F("arms-up", 3) + bo,
        "sneeze" to F("closed/up/both", 4) + F("closed/down/both", 2, off = 1, poof = "wave") +
            F("closed/down/both", 2) + F("default", 2) + ts,
        "turn" to turn("right-12", "right-30", "right-55", "right-75", "edge") +
            turn("back-105", "back-125", "back-150", "back", "back") +
            turn("back-150", "back-125", "back-105", "edge") +
            turn("left-75", "left-55", "left-30", "left-12") + bo,
        "wave" to (0 until 24).map { t -> Frame(eyes = "happy", arms = if ((t / 4) % 2 == 1) "down" else "right") }
    )

    /** What a tap can play: everything that starts in place (no slide-in), like Claude Code. */
    val TAP_ACTS: List<String> = SEQS.filter { (k, v) -> v[0].x == 0 && k != "celebrate" }.keys.toList()

    fun celebrate(): List<Frame> =
        SEQS.getValue("celebrate").mapIndexed { i, f -> f.copy(confetti = i / 2, bubble = '!') }

    enum class Mood { IDLE, CRIT, DEAD, SLEEP }

    /** The looping animation for a mood; [halves] is Clawd's health (0..6). */
    fun moodLoop(mood: Mood, halves: Int): List<Frame> = when (mood) {
        Mood.SLEEP -> (0 until 24).map { t ->
            Frame(eyes = if (t in 10..11) "blink" else "closed", dig = 4, zz = (t / 4) % 4 + 1)
        }
        Mood.DEAD -> (0 until 20).map { t ->
            Frame(eyes = "x", flip = true, feet = if (t in 8..11) (if (t % 2 == 0) "left" else "right") else "both")
        }
        Mood.CRIT -> {
            val tired = Frame(eyes = "focus", sweat = 1)
            List(6) { tired.copy(sweat = 1 + it % 3) } + F("closed/down/both").map { it.copy(sweat = 2) } +
                List(6) { tired.copy(sweat = 1 + it % 3) } +
                List(5) { Frame(eyes = "focus", look = -1, sweat = 1 + it % 3) } +
                List(5) { Frame(eyes = "focus", look = 1, sweat = 1 + it % 3) } +
                (if (halves <= 1) List(4) { Frame(eyes = "yawn", sweat = 2) } else emptyList())
        }
        Mood.IDLE -> {
            val rest = F("default", 10) + ts + F("default", 6) + F("look-left", 5) + F("look-right", 5) +
                F("default", 4) + ts + F("default", 4)
            val flourish = when {
                halves >= 6 -> F("arms-up", 3) + bo + F("arms-up", 3)
                halves >= 4 -> F("wink/down/both", 4)
                else -> F("yawn/down/both", 6)
            }
            rest + flourish + F("default", 4) + F("open/down/left", 2) + F("open/down/right", 2) + bo
        }
    }
}

/** One pose of Clawd. Mirrors the frame fields of the Windows widget. */
data class Frame(
    val eyes: String = "open",
    val arms: String = "down",
    val feet: String = "both",
    val look: Int = 0,
    val flip: Boolean = false,
    val off: Int = 0,
    val x: Int = 0,
    val facing: String = "",
    val poof: String = "",
    val dig: Int = 0,
    val spray: Int = 0,
    val lift: Int = 0,
    val sweat: Int = 0,
    val zz: Int = 0,
    val bubble: Char? = null,
    val confetti: Int = -1
)

/** Half the frames, for the widget's slower 120 ms frame clock. */
fun List<Frame>.everyOther(): List<Frame> = filterIndexed { i, _ -> i % 2 == 0 }.ifEmpty { this }

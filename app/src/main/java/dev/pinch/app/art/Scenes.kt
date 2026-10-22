package dev.pinch.app.art

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The strip Clawd walks on: a static backdrop plus a few frames of animated overlay
 * (bats, snow, twinkling stars...). Logical pixels; the ground is the last [GROUND] rows.
 * The Halloween scene follows lib/halloween.ps1 of clawd-usage-winwidget.
 */
object Scenes {
    const val H = 30
    const val GROUND = 3

    private fun c(hex: String) = Pix.color(hex)
    private fun m(vararg pairs: Pair<Char, String>) = pairs.associate { it.first to c(it.second) }

    /** Colour of the sand mound when Clawd burrows, per scene. */
    fun sand(scene: String): Int = when (scene) {
        "christmas" -> c("#E2E8F0")
        "halloween" -> c("#3F2F52")
        "space" -> c("#6B7280")
        "cyberpunk" -> c("#334155")
        "spring" -> c("#65A30D")
        "valentine" -> c("#DB2777")
        else -> c("#D6B98C")
    }

    // ---------------------------------------------------------------- sprites ----
    private val MOON = listOf("...mmmmm...", "..mmmmmmm..", ".mmmmmmcmm.", "mmccmmmmmmm", "mmccmmmmmmm",
        "mmmmmmmmccm", "mmmmmmmmccm", ".mmmcmmmmm.", "..mmmmmmm..", "...mmmmm...")
    private val SUN = listOf("..ssss..", ".ssssss.", "ssssssss", "ssssssss", "ssssssss", "ssssssss", ".ssssss.", "..ssss..")
    private val TREE_DEAD = listOf(".t.....t....", "..t...t..t..", "t..t.t..t...", ".t..tt.t....", "..t.tttt....",
        "...ttt......", "....tt......", "....tt......", "....tt......", "...tttt.....")
    private val TOMB = listOf("..sss..", ".sssss.", "sssdsss", "ssdddss", "sssdsss", "sssdsss", "sssssss", "sssssss")
    private val CROSS = listOf("..c..", "ccccc", "..c..", "..c..", "..c..", "..c..")
    private val PUMPKIN = listOf("....g....", ".ooogooo.", "oyyoooyyo", "ooyoooyoo", "ooooooooo", "oyoyyyoyo", ".oyyyyyo.")
    private val BAT_UP = listOf("b.........b", "bb..b.b..bb", ".bbbrbrbbb.", "....bbb....")
    private val BAT_DOWN = listOf("....b.b....", "..bbrbrbb..", ".bbbbbbbbb.", "bb.......bb")
    private val GHOST = listOf("..www..", ".wwwww.", "wkwwkww", "wwwwwww", "wwwwwww", "w.w.w.w")
    private val XMAS_TREE = listOf("....y....", "...ggg...", "..ggggg..", "...ggg...", "..ggggg..", ".ggggggg.",
        "..ggggg..", ".ggggggg.", "ggggggggg", "....b....", "....b....")
    private val SNOWMAN = listOf("..kkk..", "..kkk..", ".kkkkk.", "..www..", "..wkw..", "..wow..", ".wwwww.",
        "wwwkwww", "wwwwwww", "wwwkwww", ".wwwww.")
    private val PRESENT = listOf("..r.r..", "...r...", "ggrrrgg", "ggrrrgg", "rrrrrrr", "ggrrrgg", "ggrrrgg")
    private val HEART = listOf(".hh.hh.", "hhhhhhh", "hhhhhhh", ".hhhhh.", "..hhh..", "...h...")
    private val HEART_S = listOf("h.h", "hhh", ".h.")
    private val TULIP = listOf("p.p.p", "ppppp", ".ppp.", "..g..", "g.g..", ".gg..", "..g..")
    private val DAISY = listOf(".w.", "wyw", ".w.", ".g.", ".g.")
    private val BFLY_A = listOf("pp.pp", "ppkpp", ".pkp.", "pp.pp")
    private val BFLY_B = listOf(".....", "ppkpp", "ppkpp", ".....")
    private val PALM = listOf("..gg.gg..", ".g..g..g.", "g..ggg..g", "....t....", "....t....", "...t.....",
        "...t.....", "...t.....", "..t......", "..t......", "..tt.....")
    private val GULL_A = listOf("w...w", ".w.w.", "..w..")
    private val GULL_B = listOf(".....", "ww.ww", "..w..")
    private val PLANET = listOf("......pppp......", "....pppppppp....", "rr.pppppqqpppp.r", ".rrrrrrrrrrrrrr.",
        "...pppppppppp...", "....pppppppp....", "......pppp......")
    private val ROCKET = listOf(".w.", "www", "wbw", "www", "r.r")
    private val SEAWEED = listOf(".g", "g.", ".g", "g.", ".g", "g.", ".g", "g.")
    private val CORAL = listOf("p.p.p", "p.p.p", ".ppp.", "..p..", "..p..")
    private val FISH = listOf(".oo.o", "ooooo", ".oo.o")
    private val SKYLINE = listOf(
        "......bbb.............bbbb...........bbb......",
        "......bbb....bbbbb....bbbb...bbb.....bbb......",
        "..bb..bbb....bbbbb....bbbb...bbb..bb.bbb..bb..",
        "..bb..bbbbb..bbbbb.bbbbbbb...bbbbbbb.bbbbbbb..",
        "bbbbbbbbbbb..bbbbbbbbbbbbbbb.bbbbbbbbbbbbbbbbb",
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    )

    // --------------------------------------------------------------- backdrop ----

    /** The static part: sky, scenery and the ground. [hour] picks day or night where it matters. */
    fun background(scene: String, w: Int, hour: Int): Pix {
        val p = Pix(w, H)
        val r = Random(scene.hashCode())
        val night = hour < 7 || hour >= 19
        val gy = H - GROUND
        when (scene) {
            "halloween" -> {
                p.vgradient(0, gy, c("#120B1C"), c("#2A1D38"))
                stars(p, r, gy - 8, c("#C4B5FD"))
                p.glow(w - 22, 8, 12, c("#80FDBA74"))
                p.art(MOON, m('m' to "#FDE68A", 'c' to "#F5C66B"), w - 28, 3)
                p.art(TREE_DEAD, m('t' to "#5B4A66"), (w * 0.08).toInt(), gy - 10)
                p.art(TOMB, m('s' to "#6B6478", 'd' to "#453E52"), (w * 0.31).toInt(), gy - 8)
                p.art(CROSS, m('c' to "#6B6478"), (w * 0.40).toInt(), gy - 6)
                p.art(listOf(".sssss.", "sssssss", "sdsdsds", "sssssss", "sssssss"), m('s' to "#6B6478", 'd' to "#453E52"), (w * 0.66).toInt(), gy - 5)
                for (fx in doubleArrayOf(0.22, 0.56, 0.86)) {
                    val x = (w * fx).toInt()
                    p.glow(x + 4, gy - 4, 8, c("#70FB923C"))
                    p.art(PUMPKIN, m('o' to "#EA580C", 'g' to "#4D7C0F", 'y' to "#7C2D12"), x, gy - 7)
                }
                ground(p, r, c("#2E1F3D"), c("#3F2F52"))
            }
            "christmas" -> {
                p.vgradient(0, gy, c("#081226"), c("#1E3A5F"))
                stars(p, r, gy - 10, c("#E0F2FE"))
                p.glow(w - 20, 7, 9, c("#60E0F2FE"))
                p.art(MOON, m('m' to "#F1F5F9", 'c' to "#CBD5E1"), w - 26, 2)
                p.art(XMAS_TREE, m('g' to "#15803D", 'y' to "#FDE047", 'b' to "#78350F"), (w * 0.12).toInt(), gy - 11)
                p.art(PRESENT, m('r' to "#DC2626", 'g' to "#16A34A"), (w * 0.12).toInt() + 9, gy - 7)
                p.art(SNOWMAN, m('k' to "#111827", 'w' to "#F8FAFC", 'o' to "#F97316"), (w * 0.72).toInt(), gy - 11)
                p.art(PRESENT, m('r' to "#2563EB", 'g' to "#FBBF24"), (w * 0.84).toInt(), gy - 7)
                ground(p, r, c("#E2E8F0"), c("#CBD5E1"))
            }
            "valentine" -> {
                p.vgradient(0, gy, c("#2A0A1F"), c("#6B1D45"))
                stars(p, r, gy - 8, c("#FBCFE8"))
                p.glow(w - 20, 9, 11, c("#70F472B6"))
                p.art(HEART, m('h' to "#F43F5E"), w - 24, 5)
                for (fx in doubleArrayOf(0.1, 0.35, 0.62, 0.9)) p.art(DAISY, m('w' to "#FBCFE8", 'y' to "#F43F5E", 'g' to "#65A30D"), (w * fx).toInt(), gy - 5)
                ground(p, r, c("#9D174D"), c("#BE185D"))
            }
            "spring" -> {
                if (night) p.vgradient(0, gy, c("#0F1B2D"), c("#1F3B4D")) else p.vgradient(0, gy, c("#3B6E99"), c("#9CC8E0"))
                if (night) stars(p, r, gy - 8, c("#E0F2FE")) else {
                    p.glow(w - 18, 8, 10, c("#80FDE68A"))
                    p.art(SUN, m('s' to "#FDE047"), w - 22, 4)
                }
                for (fx in doubleArrayOf(0.06, 0.24, 0.48, 0.7, 0.92)) p.art(TULIP, m('p' to listOf("#F472B6", "#FB7185", "#C084FC")[(fx * 10).toInt() % 3], 'g' to "#16A34A"), (w * fx).toInt(), gy - 7)
                for (fx in doubleArrayOf(0.15, 0.38, 0.6, 0.82)) p.art(DAISY, m('w' to "#F8FAFC", 'y' to "#FDE047", 'g' to "#16A34A"), (w * fx).toInt(), gy - 5)
                ground(p, r, c("#4D7C0F"), c("#65A30D"))
            }
            "summer" -> {
                if (night) p.vgradient(0, gy - 5, c("#0B1530"), c("#3B2A55")) else p.vgradient(0, gy - 5, c("#F59E0B"), c("#FDBA74"))
                if (night) { stars(p, r, gy - 12, c("#FEF3C7")); p.art(MOON, m('m' to "#FDE68A", 'c' to "#F5C66B"), w - 26, 3) }
                else { p.glow(w / 2, gy - 6, 14, c("#90FDE047")); p.art(SUN, m('s' to "#FEF08A"), w / 2 - 4, gy - 11) }
                p.rect(0, gy - 5, w, 5, c(if (night) "#1E3A5F" else "#0E7490"))
                p.art(PALM, m('g' to "#15803D", 't' to "#92400E"), (w * 0.08).toInt(), gy - 11)
                p.art(PALM, m('g' to "#166534", 't' to "#78350F"), (w * 0.82).toInt(), gy - 11, flipX = true)
                ground(p, r, c("#E8C98E"), c("#D6B07A"))
            }
            "space" -> {
                p.vgradient(0, gy, c("#06030F"), c("#1C1436"))
                stars(p, r, gy - 2, c("#FFFFFF"), 0.06)
                p.art(PLANET, m('p' to "#A78BFA", 'q' to "#C4B5FD", 'r' to "#F9A8D4"), w - 28, 4)
                ground(p, r, c("#4B5563"), c("#6B7280"))
                for (i in 0 until w / 18) p.rect(r.nextInt(w), H - GROUND, 3, 1, c("#374151"))
            }
            "ocean" -> {
                p.vgradient(0, gy, c("#0E4A6E"), c("#06263D"))
                for (i in 0 until w step 9) p.set(i + r.nextInt(5), 1, c("#4022D3EE"))
                for (fx in doubleArrayOf(0.17, 0.66, 0.93)) p.art(CORAL, m('p' to "#F472B6"), (w * fx).toInt(), gy - 5)
                ground(p, r, c("#C2A878"), c("#A88F63"))
            }
            "cyberpunk" -> {
                p.vgradient(0, gy, c("#05040A"), c("#1A1033"))
                var x = 0
                while (x < w) { p.art(SKYLINE, m('b' to "#141024"), x, gy - SKYLINE.size); x += SKYLINE[0].length }
                for (i in 0 until w / 3) {
                    val wx = r.nextInt(w); val wy = gy - 1 - r.nextInt(5)
                    if (wy >= gy - 5) p.set(wx, wy, if (r.nextBoolean()) c("#FF00E5FF") else c("#FFF0ABFC"))
                }
                p.rect(0, gy, w, 1, c("#00E5FF"))
                ground(p, r, c("#0F172A"), c("#1E293B"), top = 1)
            }
            else -> { // "beach": Clawd's home, sky by the time of day
                val (top, bottom) = when (hour) {
                    in 5..6 -> c("#2E2350") to c("#B4615A")
                    in 7..17 -> c("#1E3A5F") to c("#4B7AA8")
                    in 18..19 -> c("#3B2A55") to c("#C2703D")
                    else -> c("#0B1222") to c("#1E293B")
                }
                p.vgradient(0, gy, top, bottom)
                if (night) {
                    stars(p, r, gy - 6, c("#E0F2FE"))
                    p.glow(w - 20, 8, 10, c("#60FDE68A"))
                    p.art(MOON, m('m' to "#FDE68A", 'c' to "#F5C66B"), w - 26, 3)
                } else {
                    p.glow(w - 18, 8, 11, c("#80FDE68A"))
                    p.art(SUN, m('s' to "#FDE047"), w - 22, 4)
                }
                p.rect(0, gy - 3, w, 3, c(if (night) "#1E3A5F" else "#2B6CA3"))
                ground(p, r, c("#D6B98C"), c("#C2A170"))
            }
        }
        return p
    }

    private fun stars(p: Pix, r: Random, maxY: Int, color: Int, density: Double = 0.03) {
        val n = (p.w * maxY * density / 3).toInt()
        repeat(n) { p.set(r.nextInt(p.w), r.nextInt(maxY.coerceAtLeast(1)), Pix.withAlpha(color, 0.35f + r.nextFloat() * 0.5f)) }
    }

    private fun ground(p: Pix, r: Random, base: Int, speck: Int, top: Int = 0) {
        val gy = H - GROUND + top
        p.rect(0, gy, p.w, H - gy, base)
        for (x in 0 until p.w) {
            if (r.nextInt(4) == 0) p.set(x, gy + 1 + r.nextInt(GROUND - 1 - top), speck)
            if (top == 0 && r.nextInt(6) == 0) p.set(x, gy, speck)
        }
    }

    // -------------------------------------------------------------- animation ----

    /**
     * What moves, at frame [t] of a loop of [n] frames, on a transparent canvas.
     *
     * Everything is periodic in [n]: a bat that leaves on the right comes back on the left
     * exactly as the loop starts over, snow falls a whole number of screens, twinkles and
     * flickers run on whole cycles. So the loop has no seam, and nothing snaps back.
     */
    fun fx(scene: String, w: Int, hour: Int, t: Int, n: Int): Pix {
        val p = Pix(w, H)
        val r = Random(scene.hashCode() * 31 + 7)
        val gy = H - GROUND
        val night = hour < 7 || hour >= 19
        val u = t.toDouble() / n // 0..1 through the loop

        /** Position along a track of [span] that wraps [laps] times per loop. */
        fun across(x0: Double, span: Double, laps: Int) = wrap(x0 + u * span * laps, span)
        fun wave(cycles: Int, ph: Double = 0.0) = sin(2 * PI * (u * cycles + ph))
        fun flap(period: Int, offset: Int = 0) = ((t + offset) / period) % 2 == 0

        when (scene) {
            "halloween" -> {
                // Two bats cross the sky at different speeds and heights, bobbing, wings beating.
                val bat = m('b' to "#7353A0", 'r' to "#F87171")
                for ((i, spec) in listOf(Triple(0.1, 4, 1), Triple(0.6, 9, 2)).withIndex()) {
                    val (x0, y0, laps) = spec
                    val x = across(x0 * (w + 22), w + 22.0, laps).toInt() - 11
                    val y = y0 + (2 * wave(laps * 3, i * 0.3)).roundToInt()
                    p.art(if (flap(2, i)) BAT_UP else BAT_DOWN, bat, x, y)
                }
                // The ghost drifts the other way, slowly, swaying up and down.
                val gx = (w + 8) - across(0.3 * (w + 8), w + 16.0, 1).toInt()
                p.art(GHOST, m('w' to "#C8F5F3FF", 'k' to "#1C1326"), gx, 3 + (1.5 * wave(2)).roundToInt())
                // Candles inside the jack-o-lanterns: mostly lit, now and then a flicker.
                for ((i, fx) in doubleArrayOf(0.22, 0.56, 0.86).withIndex()) {
                    val dim = noise(t / 2, i) % 7 == 0
                    p.art(PUMPKIN, m('y' to if (dim) "#FB923C" else "#FDE047"), (w * fx).toInt(), gy - 7)
                }
                // Will-o-the-wisps wander on slow loops and breathe.
                for (i in 0 until 4) {
                    val cx = w * (0.15 + 0.23 * i)
                    val x = (cx + 6 * wave(1, i * 0.27)).toInt()
                    val y = (gy - 9 + 3 * wave(2, i * 0.41)).toInt()
                    val a = 0.35f + 0.35f * ((wave(3, i * 0.5) + 1) / 2).toFloat()
                    p.glow(x, y, 3, Pix.withAlpha(c("#4ADE80"), a * 0.6f))
                    p.set(x, y, Pix.withAlpha(c("#BBF7D0"), a + 0.2f))
                }
            }
            "christmas" -> {
                // Snow: each flake falls one or two screens per loop and drifts side to side.
                for (i in 0 until w / 3) {
                    val laps = 1 + i % 2
                    val y = across(r.nextDouble() * gy, gy.toDouble(), laps).toInt()
                    val x = (r.nextDouble() * w + 1.5 * wave(2, r.nextDouble())).toInt()
                    p.set(x, y, c(if (laps == 2) "#F8FAFC" else "#B0E2E8F0"))
                }
                // The tree's lights chase round, and the star glows.
                val tx = (w * 0.12).toInt()
                val lights = listOf(3 to 2, 5 to 4, 2 to 5, 6 to 7, 1 to 8, 4 to 7, 7 to 8)
                val cols = listOf("#EF4444", "#FDE047", "#60A5FA", "#F472B6")
                lights.forEachIndexed { i, (lx, ly) -> p.set(tx + lx, gy - 11 + ly, c(cols[(i + t / 3) % cols.size])) }
                p.glow(tx + 4, gy - 11, 3, Pix.withAlpha(c("#FDE047"), 0.4f + 0.3f * wave(4).toFloat()))
            }
            "valentine" -> {
                // Hearts float up, swaying, and fade near the top.
                for (i in 0 until 7) {
                    val y = gy - 3 - across(r.nextDouble() * gy, gy.toDouble(), 1 + i % 2)
                    val x = (r.nextDouble() * w + 2 * wave(3, r.nextDouble())).toInt()
                    val a = (y / gy).toFloat().coerceIn(0.15f, 1f)
                    p.art(HEART_S, mapOf('h' to Pix.withAlpha(c(if (i % 2 == 0) "#F9A8D4" else "#FB7185"), a)), x, y.toInt())
                }
            }
            "spring" -> {
                // Butterflies flutter along figure-eights, wings beating.
                for (i in 0 until 3) {
                    val cx = w * (0.2 + 0.3 * i)
                    val x = (cx + 10 * wave(1, i * 0.33)).toInt()
                    val y = (8 + 3 * i + 3 * wave(2, i * 0.33)).toInt()
                    val col = listOf("#F9A8D4", "#FDE047", "#C4B5FD")[i]
                    p.art(if (flap(2, i)) BFLY_A else BFLY_B, m('p' to col, 'k' to "#1F2937"), x, y)
                }
                // Petals drift down and across.
                for (i in 0 until 6) {
                    val x = across(r.nextDouble() * w, w.toDouble(), 1).toInt()
                    val y = across(r.nextDouble() * gy, gy.toDouble(), 1).toInt()
                    p.set(x, y, c("#D0FBCFE8"))
                }
            }
            "summer" -> {
                // Glints run along the water, gulls glide across.
                for (x in 0 until w) if ((x + t) % 9 == 0) p.set(x, gy - 5 + (x / 9) % 2, c(if (night) "#6093C5FD" else "#C0E0F2FE"))
                if (!night) for (i in 0 until 2) {
                    val x = across(i * 0.5 * (w + 10), w + 10.0, 1).toInt() - 5
                    p.art(if (flap(3, i)) GULL_A else GULL_B, m('w' to "#F8FAFC"), x, 4 + i * 4 + wave(2, i * 0.5).roundToInt())
                }
            }
            "space" -> {
                // Stars twinkle on their own rhythms.
                for (i in 0 until w / 7) {
                    val x = r.nextInt(w)
                    val y = r.nextInt(gy - 2)
                    if (wave(1 + i % 3, r.nextDouble()) > 0.6) {
                        p.set(x, y, c("#FFFFFFFF"))
                        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) p.set(x + dx, y + dy, c("#70FFFFFF"))
                    }
                }
                // A rocket cruises across with a flickering flame; a shooting star once a loop.
                val rx = across(0.2 * (w + 10), w + 10.0, 1).toInt() - 5
                val ry = 7 + wave(2).roundToInt()
                p.art(listOf("..w..", ".www.", "wwbww", ".www.", "r...r"), m('w' to "#E5E7EB", 'b' to "#60A5FA", 'r' to "#F97316"), rx, ry)
                p.set(rx - 1, ry + 2, c(if (flap(1)) "#FDE047" else "#F97316"))
                p.set(rx - 2, ry + 2, c("#80F97316"))
                val s = t - n / 3
                if (s in 0..7) for (k in 0..3) p.set(w / 2 + (s - k) * 3, 2 + (s - k), Pix.withAlpha(c("#FFFFFF"), 1f - k * 0.25f))
            }
            "ocean" -> {
                // Seaweed sways, bubbles rise, two fish swim opposite ways.
                for ((i, fx) in doubleArrayOf(0.05, 0.3, 0.55, 0.78).withIndex()) {
                    val bx = (w * fx).toInt()
                    for (k in 0 until 8) {
                        val sway = (1.2 * wave(2, i * 0.25 + k * 0.06) * k / 8).roundToInt()
                        p.set(bx + sway + (k % 2), gy - 1 - k, c("#15803D"))
                    }
                }
                for (i in 0 until 7) {
                    val y = gy - 2 - across(r.nextDouble() * gy, gy.toDouble(), 1 + i % 2).toInt()
                    val x = (r.nextDouble() * w + wave(4, r.nextDouble())).toInt()
                    p.art(listOf(".b.", "b.b", ".b."), m('b' to "#B0E0F2FE"), x, y)
                }
                p.art(FISH, m('o' to "#FB923C"), across(0.0, w + 8.0, 1).toInt() - 4, 8 + wave(2).roundToInt(), flipX = true)
                p.art(FISH, m('o' to "#FDE047"), w + 4 - across(0.5 * w, w + 8.0, 1).toInt(), 14 + wave(3, 0.5).roundToInt())
            }
            "cyberpunk" -> {
                // Rain streaks, a neon "AI" that buzzes, a flying car on its route.
                for (i in 0 until w / 3) {
                    val x0 = r.nextDouble() * w
                    val y = across(r.nextDouble() * gy, gy.toDouble(), 3).toInt()
                    val x = wrap(x0 - y * 0.3, w.toDouble()).toInt()
                    p.set(x, y, c("#507DF9FF"))
                    p.set(x, y + 1, c("#307DF9FF"))
                }
                val off = noise(t, 9) % 11 == 0 || noise(t, 3) % 13 == 0
                if (!off) p.art(listOf(".c..ccc", "c.c..c.", "ccc..c.", "c.c..c.", "c.c.ccc"), m('c' to "#F0ABFC"), (w * 0.62).toInt(), 3)
                val cx = across(0.0, w + 12.0, 1).toInt() - 6
                p.art(listOf(".cccc.", "cyccyc", ".k..k."), m('c' to "#334155", 'y' to "#FDE047", 'k' to "#00E5FF"), cx, 10)
            }
            else -> {
                if (night) {
                    for (i in 0 until w / 8) {
                        val x = r.nextInt(w)
                        val y = r.nextInt(gy - 6)
                        if (wave(1 + i % 3, r.nextDouble()) > 0.7) {
                            p.set(x, y, c("#FFFFFFFF")); p.set(x + 1, y, c("#80FFFFFF")); p.set(x - 1, y, c("#80FFFFFF"))
                        }
                    }
                } else {
                    // Clouds drift across at two speeds, a gull glides by.
                    val cloud = listOf("..www....", ".wwwwww..", "wwwwwwwww")
                    p.art(cloud, m('w' to "#C0F8FAFC"), across(0.15 * (w + 10), w + 10.0, 1).toInt() - 9, 4)
                    p.art(cloud, m('w' to "#90F8FAFC"), across(0.6 * (w + 10), w + 10.0, 2).toInt() - 9, 9)
                    p.art(if (flap(3)) GULL_A else GULL_B, m('w' to "#F8FAFC"), w - across(0.4 * w, w + 10.0, 1).toInt(), 6 + wave(2).roundToInt())
                }
                // Little waves roll in on the shore line.
                for (x in 0 until w) if ((x + t) % 7 == 0) p.set(x, gy - 3, c("#A0E0F2FE"))
            }
        }
        return p
    }

    private fun wrap(v: Double, span: Double) = ((v % span) + span) % span

    /** Cheap deterministic noise for flickers. */
    private fun noise(a: Int, b: Int): Int {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }
}

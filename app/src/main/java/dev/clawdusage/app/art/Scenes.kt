package dev.clawdusage.app.art

import kotlin.random.Random

/**
 * The strip Clawd walks on: a static backdrop plus a few frames of animated overlay
 * (bats, snow, twinkling stars...). Logical pixels; the ground is the last [GROUND] rows.
 * The Halloween scene follows lib/halloween.ps1 of clawd-usage-winwidget.
 */
object Scenes {
    const val H = 30
    const val GROUND = 3
    const val FX_FRAMES = 3

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
                p.art(ROCKET, m('w' to "#E5E7EB", 'b' to "#60A5FA", 'r' to "#F97316"), (w * 0.2).toInt(), 6)
                ground(p, r, c("#4B5563"), c("#6B7280"))
                for (i in 0 until w / 18) p.rect(r.nextInt(w), H - GROUND, 3, 1, c("#374151"))
            }
            "ocean" -> {
                p.vgradient(0, gy, c("#0E4A6E"), c("#06263D"))
                for (i in 0 until w step 9) p.set(i + r.nextInt(5), 1, c("#4022D3EE"))
                for (fx in doubleArrayOf(0.05, 0.3, 0.55, 0.78)) p.art(SEAWEED, m('g' to "#15803D"), (w * fx).toInt(), gy - 8)
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

    /** Overlay frame [k] of [FX_FRAMES], transparent where nothing moves. */
    fun fx(scene: String, w: Int, hour: Int, k: Int): Pix {
        val p = Pix(w, H)
        val r = Random(scene.hashCode() * 31 + 7)
        val gy = H - GROUND
        val night = hour < 7 || hour >= 19
        when (scene) {
            "halloween" -> {
                // Bats flapping across, pumpkin candles flickering, a ghost bobbing.
                val bat = m('b' to "#7353A0", 'r' to "#F87171")
                for ((i, fx) in doubleArrayOf(0.2, 0.52).withIndex()) {
                    val x = (w * fx).toInt() + k * 4 + i * 3
                    p.art(if ((k + i) % 2 == 0) BAT_UP else BAT_DOWN, bat, x, 3 + i * 4 + (k % 2))
                }
                for ((i, fx) in doubleArrayOf(0.22, 0.56, 0.86).withIndex()) {
                    val lit = (k + i) % 3 != 0
                    p.art(PUMPKIN, m('y' to if (lit) "#FDE047" else "#FB923C"), (w * fx).toInt(), gy - 7)
                }
                p.art(GHOST, m('w' to "#C0F5F3FF", 'k' to "#1C1326"), (w * 0.47).toInt(), 4 + (if (k == 1) 1 else 0))
                for (i in 0 until 4) p.set(r.nextInt(w), 6 + r.nextInt(gy - 8), c("#A04ADE80").let { if ((i + k) % 3 == 0) 0 else it })
            }
            "christmas" -> {
                for (i in 0 until w / 5) {
                    val x0 = r.nextInt(w); val y0 = r.nextInt(gy)
                    p.set((x0 + k) % w, (y0 + k * 3) % gy, c("#E0F8FAFC"))
                }
                val tx = (w * 0.12).toInt()
                val lights = listOf(3 to 2, 5 to 4, 2 to 5, 6 to 7, 1 to 8, 4 to 7, 7 to 8)
                val cols = listOf("#EF4444", "#FDE047", "#60A5FA", "#F472B6")
                lights.forEachIndexed { i, (lx, ly) -> p.set(tx + lx, gy - 11 + ly, c(cols[(i + k) % cols.size])) }
                if (k == 0) p.glow(tx + 4, gy - 11, 3, c("#C0FDE047"))
            }
            "valentine" -> {
                for (i in 0 until 6) {
                    val x = (r.nextInt(w) + (k % 2)) % w
                    val y = (gy - 4 - ((r.nextInt(gy) + k * 4) % (gy - 4)))
                    p.art(HEART_S, m('h' to if (i % 2 == 0) "#F9A8D4" else "#FB7185"), x, y)
                }
            }
            "spring" -> {
                for (i in 0 until 3) {
                    val x = (r.nextInt(w - 10) + k * 3) % (w - 5)
                    val y = 4 + r.nextInt(gy - 12) + (if (k == 1) -1 else 0)
                    p.art(if ((k + i) % 2 == 0) BFLY_A else BFLY_B, m('p' to listOf("#F9A8D4", "#FDE047", "#C4B5FD")[i], 'k' to "#1F2937"), x, y)
                }
                for (i in 0 until 5) p.set((r.nextInt(w) + k * 2) % w, (r.nextInt(gy) + k * 2) % gy, c("#C0FBCFE8"))
            }
            "summer" -> {
                for (x in 0 until w) if ((x + k * 3) % 7 == 0) p.set(x, gy - 5 + (x % 2), c(if (night) "#6093C5FD" else "#B0E0F2FE"))
                if (!night) for (i in 0 until 2) p.art(if ((i + k) % 2 == 0) GULL_A else GULL_B, m('w' to "#F8FAFC"), (w * (0.3 + i * 0.25)).toInt() + k * 3, 4 + i * 3)
            }
            "space" -> {
                for (i in 0 until w / 8) {
                    val x = r.nextInt(w); val y = r.nextInt(gy - 2)
                    if ((i + k) % 3 == 0) { p.set(x, y, c("#FFFFFFFF")); p.set(x - 1, y, c("#80FFFFFF")); p.set(x + 1, y, c("#80FFFFFF")); p.set(x, y - 1, c("#80FFFFFF")); p.set(x, y + 1, c("#80FFFFFF")) }
                }
                if (k == 1) for (i in 0 until 6) p.set(w / 3 + i * 2, 3 + i, c(if (i == 5) "#FFFFFFFF" else "#60FFFFFF"))
            }
            "ocean" -> {
                for (i in 0 until 7) {
                    val x = r.nextInt(w); val y = (r.nextInt(gy) - k * 3 + gy) % gy
                    p.art(listOf(".b.", "b.b", ".b."), m('b' to "#B0E0F2FE"), x, y)
                }
                p.art(FISH, m('o' to "#FB923C"), (w * 0.3).toInt() + k * 3, 8)
                p.art(FISH, m('o' to "#FDE047"), (w * 0.7).toInt() - k * 3, 14, flipX = true)
            }
            "cyberpunk" -> {
                for (i in 0 until w / 3) {
                    val x = r.nextInt(w); val y = (r.nextInt(gy) + k * 5) % gy
                    p.set(x, y, c("#507DF9FF")); p.set(x, y + 1, c("#307DF9FF"))
                }
                // A neon "AI" sign that flickers.
                if (k != 2) p.art(listOf(".c..ccc", "c.c..c.", "ccc..c.", "c.c..c.", "c.c.ccc"), m('c' to "#F0ABFC"), (w * 0.62).toInt(), 3)
            }
            else -> {
                if (night) for (i in 0 until w / 10) {
                    val x = r.nextInt(w); val y = r.nextInt(gy - 6)
                    if ((i + k) % 3 == 0) { p.set(x, y, c("#FFFFFFFF")); p.set(x + 1, y, c("#80FFFFFF")); p.set(x - 1, y, c("#80FFFFFF")) }
                } else {
                    val cloud = listOf("..www....", ".wwwwww..", "wwwwwwwww")
                    p.art(cloud, m('w' to "#C0F8FAFC"), (w * 0.15).toInt() + k * 2, 4)
                    p.art(cloud, m('w' to "#90F8FAFC"), (w * 0.55).toInt() + k, 9)
                }
                for (x in 0 until w) if ((x + k * 2) % 9 == 0) p.set(x, gy - 3, c("#90E0F2FE"))
            }
        }
        return p
    }
}

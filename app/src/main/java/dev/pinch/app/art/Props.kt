package dev.pinch.app.art

/**
 * Things the crab holds, for the skins that come with one: the chef's pan, the programmer's
 * laptop and coffee, the wizard's wand, the detective's magnifying glass. Unlike a hat they
 * move, so they are drawn in code, frame by frame ([t] is the stage's frame number).
 *
 * Coordinates are in the crab's grid: his right hand is at about (17, 5), his feet end at
 * row 9, and the canvas leaves room on both sides.
 */
object Props {
    private fun c(hex: String) = Pix.color(hex)

    private val PAN_RIM = c("#52525B")
    private val PAN = c("#27272A")
    private val HANDLE = c("#3F3F46")
    private val PANCAKE = c("#FBBF24")
    private val STEAM = c("#D4D4D8")
    private val LID = c("#1F1F23")
    private val LID_EDGE = c("#3F3F46")
    private val LOGO = c("#E5E7EB")
    private val BASE = c("#71717A")
    private val MUG = c("#F5E6CC")
    private val COFFEE = c("#4A2C1A")
    private val WAND = c("#5B3A29")
    private val SPARK = c("#FDE047")
    private val SPARK_HI = c("#FFFBEB")
    private val LENS_RIM = c("#27272A")
    private val LENS = c("#C0E5E7EB")
    private val GLINT = c("#FFFFFF")
    private val LENS_HANDLE = c("#3F342D")

    /** True for skins whose idle loop is to sit and work instead of wandering. */
    fun sedentary(skinId: String) = skinId == "programmer"

    fun draw(p: Pix, skinId: String, t: Int, x: Int, y: Int) {
        when (skinId) {
            "chef" -> chef(p, t, x, y)
            "programmer" -> programmer(p, t, x, y)
            "wizard" -> wizard(p, t, x, y)
            "detective" -> detective(p, t, x, y)
        }
    }

    // Pan held out to the right; every 20 frames the pancake goes up, spins and lands.
    private val FLIP = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 5, 6, 6, 5, 3, 1, 0, 0)

    private fun chef(p: Pix, t: Int, x: Int, y: Int) {
        p.set(x + 18, y + 5, HANDLE)
        p.set(x + 19, y + 5, HANDLE)
        p.rect(x + 20, y + 4, 7, 1, PAN_RIM)
        p.rect(x + 21, y + 5, 5, 1, PAN)
        val k = t % FLIP.size
        val h = FLIP[k]
        if (k in 12..15) {
            // Edge-on at the top of the flip.
            p.set(x + 23, y + 2 - h, PANCAKE)
            p.set(x + 23, y + 3 - h, PANCAKE)
        } else {
            p.rect(x + 22, y + 3 - h, 3, 1, PANCAKE)
        }
        if (h == 0) {
            // Steam curls up while it cooks.
            for (i in 0..1) {
                val s = (t / 3 + i * 3) % 6
                p.set(x + 22 + i * 2 + (s / 2) % 2, y + 2 - s, Pix.withAlpha(STEAM, 0.8f - s * 0.12f))
            }
        }
    }

    // Laptop on the ground in front of him, a mug of coffee steaming on the other side.
    private fun programmer(p: Pix, t: Int, x: Int, y: Int) {
        p.rect(x + 19, y + 3, 7, 6, LID)
        p.rect(x + 19, y + 3, 7, 1, LID_EDGE)
        p.rect(x + 19, y + 3, 1, 6, LID_EDGE)
        p.set(x + 22, y + 5, LOGO)
        p.set(x + 23, y + 6, LOGO)
        p.rect(x + 16, y + 9, 11, 1, BASE)
        // The screen's light flickers on his face as the code scrolls.
        if ((t / 3) % 4 == 0) p.set(x + 18, y + 4, Pix.withAlpha(c("#60A5FA"), 0.6f))

        p.rect(x - 6, y + 5, 4, 4, MUG)
        p.rect(x - 5, y + 5, 2, 1, COFFEE)
        p.set(x - 7, y + 6, MUG)
        p.set(x - 7, y + 7, MUG)
        for (i in 0..2) {
            val s = (t / 3 + i * 3) % 7
            p.set(x - 5 + (s / 2) % 2, y + 4 - s, Pix.withAlpha(STEAM, 0.85f - s * 0.11f))
        }
    }

    // A wand raised in his right hand: sparkles twinkle at the tip, and now and then a spell.
    private val TWINKLES = listOf(22 to -1, 23 to 1, 21 to -2, 24 to 0, 22 to 2, 20 to 0)

    private fun wizard(p: Pix, t: Int, x: Int, y: Int) {
        p.set(x + 18, y + 4, WAND)
        p.set(x + 19, y + 3, WAND)
        p.set(x + 20, y + 2, WAND)
        p.set(x + 21, y + 1, SPARK_HI)
        TWINKLES.forEachIndexed { i, (dx, dy) ->
            val k = (t + i * 3) % 8
            if (k < 3) {
                p.set(x + dx, y + dy, if (k == 1) SPARK_HI else SPARK)
                if (k == 1 && i % 2 == 0) {
                    for ((ax, ay) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) p.set(x + dx + ax, y + dy + ay, Pix.withAlpha(SPARK, 0.6f))
                }
            }
        }
        val burst = t % 40
        if (burst < 6) {
            val r = 2 + burst
            val a = 1f - burst / 6f
            for (k in 0 until 8) {
                val ang = Math.PI * 2 * k / 8
                p.set(x + 21 + Math.round(r * Math.cos(ang)).toInt(), y + 1 + Math.round(r * Math.sin(ang)).toInt(),
                    Pix.withAlpha(if (k % 2 == 0) SPARK_HI else SPARK, a))
            }
        }
    }

    // A magnifying glass in his right hand; every so often he lifts it to take a closer look.
    private val RING = listOf(".rrr.", "r...r", "r...r", "r...r", ".rrr.")

    private fun detective(p: Pix, t: Int, x: Int, y: Int) {
        val lift = if (t % 40 in 20..33) -2 else 0
        val lx = x + 19
        val ly = y + 1 + lift
        p.set(x + 18, y + 6 + lift / 2, LENS_HANDLE)
        p.set(x + 19, y + 5 + lift / 2, LENS_HANDLE)
        p.rect(lx + 1, ly + 1, 3, 3, LENS)
        p.art(RING, mapOf('r' to LENS_RIM), lx, ly)
        // The glint slides across the glass.
        val g = (t / 5) % 6
        if (g < 3) p.set(lx + 1 + g, ly + 1 + (g / 2), GLINT)
    }
}

package dev.pinch.app.art

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What the crab holds or works with, for the skins that come with something: the chef's pan,
 * the detective's magnifying glass and pipe, the wizard's wand, the programmer's laptop.
 *
 * [Frame.prop] says what the prop is doing on this frame (set by the skin's routine in
 * [Stage]); [t] is the stage frame, for things that just tick along, like smoke.
 *
 * Coordinates are in the crab's grid: his face spans x 3..15, his eyes are at (5, 2..3) and
 * (13, 2..3), his right hand is at about (17, 5), his feet end at row 9.
 */
object Props {
    private fun c(hex: String) = Pix.color(hex)

    /** Skins that stay put and work instead of wandering. */
    fun sedentary(skinId: String) = skinId == "programmer" || skinId == "chef"

    /** Skins with a routine of their own in [Stage]. */
    fun hasRoutine(skinId: String) = skinId in setOf("chef", "programmer", "wizard", "detective")

    /**
     * @param y the crab's grid top as drawn (lowered when he crouches or sits)
     * @param rest the grid top when standing, for things that stay on the ground
     */
    fun draw(p: Pix, skinId: String, f: Frame, t: Int, x: Int, y: Int, rest: Int) {
        when (skinId) {
            "chef" -> chef(p, f, t, x, y)
            "programmer" -> programmer(p, f, t, x, rest)
            "wizard" -> wizard(p, f, t, x, y)
            "detective" -> detective(p, f, t, x, y)
        }
    }

    // ------------------------------------------------------------------- chef ----

    /** Pancake height above the pan for each step of a toss. */
    val TOSS = intArrayOf(2, 4, 6, 7, 8, 8, 7, 6, 4, 2, 1)

    private val PAN_RIM = c("#52525B")
    private val PAN = c("#27272A")
    private val HANDLE = c("#3F3F46")
    private val PANCAKE = c("#F59E0B")
    private val PANCAKE_TOP = c("#FCD34D")
    private val PANCAKE_UNDER = c("#B45309")
    private val STEAM = c("#E4E4E7")

    private fun chef(p: Pix, f: Frame, t: Int, x: Int, y: Int) {
        // The pan comes up a row as he tosses.
        val lift = if (f.arms == "up") 1 else 0
        val py = y - lift
        p.set(x + 18, py + 5, HANDLE)
        p.set(x + 19, py + 5, HANDLE)
        p.rect(x + 20, py + 4, 7, 1, PAN_RIM)
        p.rect(x + 21, py + 5, 5, 1, PAN)
        val step = f.prop
        if (step <= 0 || step > TOSS.size) {
            // Cooking: the pancake sits in the pan, steam curls up, the odd sizzle.
            p.rect(x + 21, py + 3, 5, 1, PANCAKE)
            p.rect(x + 22, py + 3, 3, 1, PANCAKE_TOP)
            for (i in 0..1) {
                val s = (t / 4 + i * 4) % 8
                p.set(x + 22 + i * 2 + (s / 3) % 2, py + 2 - s, Pix.withAlpha(STEAM, 0.75f - s * 0.09f))
            }
            if ((t / 3) % 7 == 0) p.set(x + 26, py + 3, PANCAKE_TOP)
        } else {
            // In the air: it turns over on the way up and comes down the other way round.
            val h = TOSS[step - 1]
            val cy = py + 3 - h
            when (step % 4) {
                1, 3 -> { p.set(x + 23, cy - 1, PANCAKE); p.set(x + 23, cy, PANCAKE_UNDER) } // edge-on
                2 -> p.rect(x + 21, cy, 5, 1, PANCAKE_UNDER)                                  // underside up
                else -> { p.rect(x + 21, cy, 5, 1, PANCAKE); p.rect(x + 22, cy, 3, 1, PANCAKE_TOP) }
            }
        }
    }

    // ------------------------------------------------------------- programmer ----

    private val LID = c("#1F1F23")
    private val LID_EDGE = c("#3F3F46")
    private val LOGO = c("#E5E7EB")
    private val BASE = c("#71717A")
    private val BASE_TOP = c("#A1A1AA")
    private val SCREEN_GLOW = c("#60A5FA")

    /** He sits on the ground behind an open laptop; we see the back of its lid. */
    private fun programmer(p: Pix, f: Frame, t: Int, x: Int, rest: Int) {
        val gy = rest + 9 // the ground row under him
        p.rect(x + 12, gy, 12, 1, BASE)
        p.rect(x + 12, gy - 1, 6, 1, BASE_TOP)
        p.rect(x + 17, gy - 7, 7, 7, LID)
        p.rect(x + 17, gy - 7, 1, 7, LID_EDGE)
        p.rect(x + 17, gy - 7, 7, 1, LID_EDGE)
        p.set(x + 20, gy - 4, LOGO)
        p.set(x + 21, gy - 4, LOGO)
        p.set(x + 21, gy - 3, LOGO)
        // Keys clicking under his hands while he types.
        if (f.arms == "right") p.set(x + 13 + (t * 3) % 4, gy - 1, LOGO)
        // The screen's light changes on his face as the code scrolls by.
        val glow = 0.18f + 0.12f * ((t / 3) % 3)
        for (dy in 2..5) p.set(x + 15, rest + 4 + dy, Pix.withAlpha(SCREEN_GLOW, glow))
    }

    // ----------------------------------------------------------------- wizard ----

    private val WAND = c("#5B3A29")
    private val SPARK = c("#FDE047")
    private val SPARK_HI = c("#FFFBEB")
    private val MAGIC = c("#C4B5FD")

    /** Wand angles: 0 resting, then up, up-forward, forward, down-forward. Degrees from up. */
    private val WAND_ANGLE = intArrayOf(45, 0, 30, 70, 100)

    /** Number of wand-wave steps before the spell goes off. */
    val WAVE = intArrayOf(1, 2, 3, 4, 3, 2, 1, 2, 3)
    const val CAST = 8

    private fun wandTip(x: Int, y: Int, angle: Int): Pair<Int, Int> {
        val a = Math.toRadians(angle.toDouble())
        return (x + 17 + 4 * sin(a)).roundToInt() to (y + 5 - 4 * cos(a)).roundToInt()
    }

    private fun wizard(p: Pix, f: Frame, t: Int, x: Int, y: Int) {
        val waving = f.prop in 1..WAVE.size
        val casting = f.prop > WAVE.size
        val angle = when {
            waving -> WAND_ANGLE[WAVE[f.prop - 1]]
            casting -> WAND_ANGLE[3]
            else -> WAND_ANGLE[0]
        }
        // The wand: three pixels from the hand towards the tip.
        val a = Math.toRadians(angle.toDouble())
        for (k in 1..3) p.set((x + 17 + k * sin(a)).roundToInt(), (y + 5 - k * cos(a)).roundToInt(), WAND)
        val (tx, ty) = wandTip(x, y, angle)
        p.set(tx, ty, SPARK_HI)

        if (waving) {
            // A trail of sparks where the tip has just been.
            for (back in 1..3) {
                val prev = f.prop - back
                if (prev < 1) break
                val (sx, sy) = wandTip(x, y, WAND_ANGLE[WAVE[prev - 1]])
                p.set(sx, sy, Pix.withAlpha(if (back == 1) SPARK else MAGIC, 1f - back * 0.28f))
            }
        } else if (casting) {
            // The spell: a ring of stars bursting out from the tip, with a few drifting off.
            val k = f.prop - WAVE.size
            val r = 1 + k
            val fade = 1f - k / (CAST + 1f)
            for (i in 0 until 10) {
                val ang = Math.PI * 2 * i / 10 + k * 0.2
                val sx = tx + (r * cos(ang)).roundToInt()
                val sy = ty + (r * 0.7 * sin(ang)).roundToInt()
                p.set(sx, sy, Pix.withAlpha(if (i % 3 == 0) MAGIC else if (i % 2 == 0) SPARK_HI else SPARK, fade))
            }
            if (k < 3) for ((ax, ay) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) p.set(tx + ax, ty + ay, SPARK_HI)
        } else {
            // Resting: the tip twinkles now and then.
            val k = (t / 3) % 8
            if (k < 2) for ((ax, ay) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) p.set(tx + ax, ty + ay, Pix.withAlpha(SPARK, 0.7f))
            if ((t / 5) % 6 == 0) p.set(tx + 1, ty - 2, MAGIC)
        }
    }

    // -------------------------------------------------------------- detective ----

    private val LENS_RIM = c("#27272A")
    private val LENS = c("#B3E0F2FE")
    private val GLINT = c("#FFFFFF")
    private val LENS_HANDLE = c("#3F342D")
    private val PIPE = c("#7C4A2D")
    private val PIPE_STEM = c("#3F2A1D")
    private val SMOKE = c("#D4D4D8")
    private val PUPIL = c("#141416")

    const val LENS_DOWN = 0
    const val LENS_EYE = 1
    const val LENS_GROUND = 2

    private val RING = listOf(".rrr.", "r...r", "r...r", "r...r", ".rrr.")

    private fun lens(p: Pix, lx: Int, ly: Int, t: Int) {
        p.rect(lx + 1, ly + 1, 3, 3, LENS)
        p.art(RING, mapOf('r' to LENS_RIM), lx, ly)
        val g = (t / 6) % 8
        if (g < 3) p.set(lx + 1 + g / 2, ly + 1 + g / 2, GLINT)
    }

    private fun detective(p: Pix, f: Frame, t: Int, x: Int, y: Int) {
        // The pipe, held in the corner of his mouth, smoke curling up slowly.
        p.set(x + 6, y + 5, PIPE_STEM)
        p.set(x + 5, y + 6, PIPE_STEM)
        p.rect(x + 2, y + 6, 3, 2, PIPE)
        p.set(x + 2, y + 5, PIPE)
        for (i in 0..2) {
            val s = (t / 4 + i * 5) % 14
            val sx = x + 3 - (s / 4) + (if ((s / 2) % 2 == 0) 0 else 1)
            p.set(sx, y + 4 - s, Pix.withAlpha(SMOKE, (0.7f - s * 0.05f).coerceAtLeast(0f)))
        }

        when (f.prop) {
            LENS_EYE -> {
                // Up to his right eye: we see the eye through it, big, looking where he looks.
                val lx = x + 11
                val ly = y
                p.set(x + 16, y + 5, LENS_HANDLE)
                p.set(x + 17, y + 6, LENS_HANDLE)
                lens(p, lx, ly, t)
                val px = lx + 1 + (f.look + 1).coerceIn(0, 2) / 2
                p.rect(px, ly + 2, 2, 2, PUPIL)
            }
            LENS_GROUND -> {
                // Held out low, over something on the ground in front of him.
                p.set(x + 18, y + 6, LENS_HANDLE)
                p.set(x + 19, y + 6, LENS_HANDLE)
                lens(p, x + 20, y + 5, t)
            }
            else -> {
                p.set(x + 18, y + 6, LENS_HANDLE)
                p.set(x + 19, y + 5, LENS_HANDLE)
                lens(p, x + 19, y + 1, t)
            }
        }
    }
}

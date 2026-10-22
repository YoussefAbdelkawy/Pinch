package dev.pinch.app.art

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * The stage: the scene with the crab on it, as one seamless loop of frames.
 *
 * The crab follows a choreography that always ends where and how it began, and every
 * moving thing in the scene is periodic over the same loop, so when the loop wraps
 * nothing jumps. Used by both the widget (pre-rendered frames) and the app (live).
 */
object Stage {
    const val FRAME_MS = 100L
    /** 12 seconds: long enough that things drift across the scene at a calm pace. */
    const val LOOP = 120

    /** Where the crab is (left edge of his canvas, in logical pixels) and how he looks. */
    data class Beat(val x: Int, val f: Frame)

    /** The crab's grid top so that his feet stand on the scene's ground. */
    private const val CRAB_TOP = Scenes.H - Scenes.GROUND - Clawd.GROUND

    /** Furthest left edge of the crab's canvas that keeps him on stage. */
    fun maxX(w: Int) = max(0, w - Clawd.CANVAS_W)

    /** Where he lives: further along the stage the more of the session is used. */
    fun home(w: Int, sessionPct: Int?) = maxX(w) * (sessionPct ?: 15).coerceIn(0, 100) / 100

    // ------------------------------------------------------------ choreography ----

    /** Walk cycle: legs alternate with a "both down" in between, so no leg ever blinks out. */
    private val STRIDE = listOf("both", "left", "both", "right")

    fun loop(mood: Clawd.Mood, halves: Int, home: Int, w: Int, skinId: String = "", n: Int = LOOP): List<Beat> {
        val b = Builder(home.coerceIn(0, maxX(w)))
        if (Props.hasRoutine(skinId) && (mood == Clawd.Mood.IDLE || mood == Clawd.Mood.CRIT)) {
            routine(b, skinId, mood == Clawd.Mood.CRIT, w, n)
            return b.out.take(n)
        }
        when (mood) {
            Clawd.Mood.SLEEP -> {
                // Burrowed, "z"s rising; halfway through he peeks out, looks around, sinks back.
                for (t in 0 until n) {
                    val stir = t in n / 2 until n / 2 + 10
                    val f = if (stir) {
                        val k = t - n / 2
                        Frame(dig = 3, eyes = if (k < 2 || k >= 8) "blink" else "open", look = if (k < 5) -1 else 1)
                    } else Frame(dig = 4, eyes = "closed", zz = 1 + (t % 16) / 2)
                    b.hold(1, f)
                }
            }
            Clawd.Mood.DEAD -> {
                // On his back; now and then his legs twitch.
                for (t in 0 until n) {
                    val k = t % 40
                    val feet = if (k in 30..35) (if (k % 2 == 0) "left" else "right") else "both"
                    b.hold(1, Frame(eyes = "x", flip = true, feet = feet))
                }
            }
            Clawd.Mood.CRIT -> {
                // Tired: short, slow walks, eyes darting, sweating.
                val dir = b.roomyDir(w)
                val d = min(8, b.room(w, dir))
                val tired = { t: Int -> Frame(eyes = "focus", sweat = 1 + (t / 2) % 3) }
                b.holdFn(8) { t -> tired(t).copy(look = if ((t / 3) % 2 == 0) -1 else 1) }
                b.walk(dir * d, every = 2, base = tired)
                b.holdFn(6) { t -> tired(t).copy(look = -dir) }
                b.hold(5, Frame(eyes = "yawn", sweat = 2))
                b.walk(-dir * d, every = 2, base = tired)
                b.fill(n) { t -> tired(t).copy(look = if ((t / 4) % 2 == 0) -1 else 1) }
            }
            Clawd.Mood.IDLE -> {
                val dir = b.roomyDir(w)
                val d = min(14, b.room(w, dir))
                val calm = { _: Int -> Frame() }
                b.hold(6, Frame())
                b.hold(1, Frame(eyes = "blink"))
                b.hold(3, Frame())
                b.hold(3, Frame(look = dir))
                b.walk(dir * d, every = 1, base = calm)
                b.hold(3, Frame(look = dir))
                b.hold(4, Frame(look = -dir))
                b.hold(1, Frame(eyes = "blink"))
                when {
                    halves >= 5 -> { // full of beans: a little hop, arms up
                        for (lift in intArrayOf(1, 3, 4, 3, 1)) b.hold(1, Frame(arms = "up", lift = lift))
                        b.hold(2, Frame(eyes = "happy"))
                    }
                    halves >= 3 -> for (k in 0 until 8) b.hold(1, Frame(eyes = "happy", arms = if ((k / 2) % 2 == 0) "right" else "down"))
                    else -> { b.hold(6, Frame(eyes = "yawn")); b.hold(1, Frame(eyes = "blink")) }
                }
                b.walk(-dir * d, every = 1, base = calm)
                b.fill(n) { t ->
                    when (t % 12) {
                        5 -> Frame(eyes = "blink")
                        in 7..9 -> Frame(look = -dir)
                        else -> Frame()
                    }
                }
            }
        }
        return b.out.take(n)
    }

    /** The skin's own routine: cooking, typing, spell-casting, investigating. Ends where it began. */
    private fun routine(b: Builder, skinId: String, tired: Boolean, w: Int, n: Int) {
        val sweat = { t: Int -> if (tired) 1 + (t / 2) % 3 else 0 }
        val eyes = { e: String -> if (tired && e == "open") "focus" else e }
        when (skinId) {
            "chef" -> {
                // Watches the pan, then tosses: the pan comes up, the pancake flips, he catches it.
                val cycle = 40
                while (b.out.size + cycle <= n) {
                    b.holdFn(8) { t -> Frame(eyes = eyes("open"), look = 1, sweat = sweat(t)) }
                    b.hold(1, Frame(eyes = "blink", look = 1))
                    b.holdFn(9) { t -> Frame(eyes = eyes("open"), look = 1, sweat = sweat(t)) }
                    b.hold(2, Frame(eyes = "focus", look = 1, off = 0, arms = "down"))
                    for (step in 1..Props.TOSS.size) {
                        b.hold(1, Frame(eyes = if (step in 4..7) "wide" else "focus", look = 1,
                            arms = if (step <= 2) "up" else "right", prop = step))
                    }
                    b.hold(9, Frame(eyes = "happy", arms = "right"))
                }
                b.fill(n) { t -> Frame(eyes = eyes("open"), look = 1, sweat = sweat(t)) }
            }
            "programmer" -> {
                // Sitting on the ground (legs tucked, body down on the sand) at his laptop:
                // typing, glancing away to think, the odd stretch.
                val sit = Frame(feet = "tucked", lift = -2)
                for (t in 0 until n) {
                    val k = t % 60
                    val f = when {
                        k in 50..55 -> sit.copy(arms = "up", eyes = "closed")
                        k % 23 == 11 -> sit.copy(eyes = "blink", look = 1)
                        k in 30..35 -> sit.copy(eyes = eyes("open"), look = -1)
                        else -> sit.copy(eyes = if (tired) "focus" else "happy", look = 1,
                            arms = if ((t / 2) % 2 == 0) "down" else "right")
                    }
                    b.hold(1, f.copy(sweat = sweat(t)))
                }
            }
            "wizard" -> {
                // Strolls, waves the wand in an arc, and casts; twice per loop.
                val dir = b.roomyDir(w)
                val d = min(10, b.room(w, dir))
                fun cast() {
                    for (step in 1..Props.WAVE.size) {
                        b.hold(1, Frame(eyes = "focus", arms = if (Props.WAVE[step - 1] == 1) "up" else "right", prop = step))
                    }
                    for (k in 1..Props.CAST) b.hold(1, Frame(eyes = "happy", arms = "right", prop = Props.WAVE.size + k))
                }
                b.holdFn(8) { t -> Frame(eyes = eyes("open"), sweat = sweat(t)) }
                cast()
                b.hold(4, Frame(look = dir))
                b.walk(dir * d, every = 1) { t -> Frame(sweat = sweat(t)) }
                b.hold(3, Frame(eyes = "blink"))
                cast()
                b.walk(-dir * d, every = 1) { t -> Frame(sweat = sweat(t)) }
                b.fill(n) { t -> Frame(eyes = if (t % 13 == 6) "blink" else eyes("open"), sweat = sweat(t)) }
            }
            "detective" -> {
                // Paces slowly, studies the ground, then holds the glass up to his eye.
                val dir = b.roomyDir(w)
                val d = min(8, b.room(w, dir))
                val pace = { t: Int -> Frame(eyes = eyes("open"), sweat = sweat(t)) }
                b.holdFn(6, pace)
                b.walk(dir * d, every = 2, base = pace)
                b.hold(14, Frame(eyes = "focus", look = dir, prop = Props.LENS_GROUND))
                b.hold(3, Frame(look = dir))
                for (k in 0 until 18) {
                    b.hold(1, Frame(eyes = "open", look = if (k < 6) 0 else if (k < 12) -1 else 1, prop = Props.LENS_EYE))
                }
                b.hold(3, Frame(eyes = "wide"))
                b.walk(-dir * d, every = 2, base = pace)
                b.fill(n) { t -> Frame(eyes = if (t % 17 == 8) "blink" else eyes("open"), sweat = sweat(t)) }
            }
        }
    }

    /** A one-shot animation (a tap, a celebration) played where he stands, at the stage's frame rate. */
    fun act(seq: List<Frame>, home: Int, w: Int): List<Beat> {
        val x = home.coerceIn(0, maxX(w))
        val len = (seq.size * Clawd.FRAME_MS / FRAME_MS).toInt().coerceAtLeast(1)
        return (0 until len).map { i -> Beat(x, seq[min(seq.size - 1, (i * FRAME_MS / Clawd.FRAME_MS).toInt())]) }
    }

    private class Builder(start: Int) {
        val out = mutableListOf<Beat>()
        var x = start

        fun hold(k: Int, f: Frame) = repeat(k) { out += Beat(x, f) }
        fun holdFn(k: Int, f: (Int) -> Frame) = repeat(k) { out += Beat(x, f(out.size)) }

        /** Walks [dx] pixels, one pixel every [every] frames, facing the way he goes. */
        fun walk(dx: Int, every: Int, base: (Int) -> Frame) {
            val dir = sign(dx.toFloat()).toInt()
            for (s in 0 until abs(dx) * every) {
                if (s % every == every - 1) x += dir
                val step = STRIDE[s % STRIDE.size] // a lifted leg never stays up two frames
                out += Beat(x, base(out.size).copy(look = dir, feet = step))
            }
        }

        fun fill(n: Int, f: (Int) -> Frame) { while (out.size < n) out += Beat(x, f(out.size)) }

        fun room(w: Int, dir: Int) = if (dir > 0) maxX(w) - x else x
        fun roomyDir(w: Int) = if (maxX(w) - x >= x) 1 else -1
    }

    // ------------------------------------------------------------- composition ----

    /** One finished frame: backdrop, the scene's moving things, and the crab. */
    fun compose(bg: Pix, scene: String, hour: Int, t: Int, n: Int, beat: Beat, skin: Skin): Pix {
        val p = Pix(bg.w, bg.h)
        System.arraycopy(bg.px, 0, p.px, 0, bg.px.size)
        Scenes.fx(scene, bg.w, hour, t, n).drawInto(p, 0, 0)
        Clawd.draw(p, beat.f, skin, beat.x + Clawd.ORIGIN_X, CRAB_TOP, Scenes.sand(scene), t)
        return p
    }
}

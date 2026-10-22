package dev.pinch.app.art

import android.graphics.Bitmap

/**
 * A tiny software canvas of logical pixels. Everything Clawd and the scenes draw goes here,
 * then gets blown up with nearest-neighbour scaling so the pixels stay crisp.
 */
class Pix(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    /** Paints one pixel, alpha-blending over what is there. */
    fun set(x: Int, y: Int, argb: Int) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        val a = argb ushr 24
        if (a == 0) return
        val i = y * w + x
        if (a == 255) { px[i] = argb; return }
        px[i] = blend(px[i], argb)
    }

    fun rect(x: Int, y: Int, rw: Int, rh: Int, argb: Int) {
        for (yy in y until y + rh) for (xx in x until x + rw) set(xx, yy, argb)
    }

    fun fill(argb: Int) = px.fill(argb)

    /** Vertical gradient from [top] to [bottom] over the rows [y0]..[y1]. */
    fun vgradient(y0: Int, y1: Int, top: Int, bottom: Int) {
        val n = (y1 - y0).coerceAtLeast(1)
        for (y in y0 until y1) {
            val c = mix(top, bottom, (y - y0).toFloat() / n)
            for (x in 0 until w) set(x, y, c)
        }
    }

    /** Pixel art: rows of characters, "." empty, each other letter a colour from [colors]. */
    fun art(rows: List<String>, colors: Map<Char, Int>, x: Int, y: Int, flipX: Boolean = false) {
        for ((ry, row) in rows.withIndex()) {
            for (rx in row.indices) {
                val c = colors[row[rx]] ?: continue
                val dx = if (flipX) row.length - 1 - rx else rx
                set(x + dx, y + ry, c)
            }
        }
    }

    /** A soft round glow made of pixels, strongest in the middle. */
    fun glow(cx: Int, cy: Int, r: Int, argb: Int) {
        val a0 = argb ushr 24
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()) / r
            if (d >= 1) continue
            val a = (a0 * (1 - d) * (1 - d)).toInt()
            set(x, y, (a shl 24) or (argb and 0xFFFFFF))
        }
    }

    /** Draws this canvas into another one at (x, y). */
    fun drawInto(dst: Pix, x: Int, y: Int) {
        for (yy in 0 until h) for (xx in 0 until w) dst.set(x + xx, y + yy, px[yy * w + xx])
    }

    fun toBitmap(scale: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val bw = w * scale
        val row = IntArray(bw)
        val bmp = Bitmap.createBitmap(bw, h * scale, config)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                for (k in 0 until scale) row[x * scale + k] = c
            }
            for (k in 0 until scale) bmp.setPixels(row, 0, bw, 0, y * scale + k, bw, 1)
        }
        return bmp
    }

    companion object {
        fun blend(dst: Int, src: Int): Int {
            val sa = (src ushr 24) / 255f
            val da = (dst ushr 24) / 255f
            val oa = sa + da * (1 - sa)
            if (oa <= 0f) return 0
            fun ch(shift: Int): Int {
                val s = (src shr shift) and 0xFF
                val d = (dst shr shift) and 0xFF
                return ((s * sa + d * da * (1 - sa)) / oa).toInt().coerceIn(0, 255)
            }
            return ((oa * 255).toInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun withAlpha(argb: Int, a: Float): Int = ((255 * a).toInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)

        /** "#RRGGBB" or "#AARRGGBB" to ARGB. */
        fun color(hex: String): Int {
            val h = hex.removePrefix("#")
            return when (h.length) {
                6 -> (0xFF000000 or h.toLong(16)).toInt()
                8 -> h.toLong(16).toInt()
                else -> throw IllegalArgumentException(hex)
            }
        }
    }
}

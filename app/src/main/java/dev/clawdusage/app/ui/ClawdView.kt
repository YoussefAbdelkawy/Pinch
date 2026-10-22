package dev.clawdusage.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import dev.clawdusage.app.art.Clawd
import dev.clawdusage.app.art.Frame
import dev.clawdusage.app.art.Pix
import dev.clawdusage.app.art.Scenes
import dev.clawdusage.app.art.Skin
import dev.clawdusage.app.art.Skins
import dev.clawdusage.app.art.Theme
import dev.clawdusage.app.art.Themes
import java.time.LocalDateTime

/**
 * The big Clawd in the app: the theme's scene with Clawd on it at the full 60 ms frame rate.
 * Tap him for one of Claude Code's click animations.
 */
class ClawdView(context: Context) : View(context) {

    var theme: Theme = Themes.byId("classic")
        set(value) { field = value; sceneKey = "" ; invalidate() }
    var skin: Skin = Skins.CLASSIC
        set(value) { field = value; crabCache.clear(); invalidate() }
    var mood: Clawd.Mood = Clawd.Mood.IDLE
        set(value) { field = value; loop = Clawd.moodLoop(value, halves); t = 0 }
    var halves: Int = 6
    /** 0..100: how far along the stage he stands. */
    var position: Int = 30

    private var loop: List<Frame> = Clawd.moodLoop(Clawd.Mood.IDLE, 6)
    private var act: List<Frame>? = null
    private var t = 0
    private var tick = 0

    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private var sceneKey = ""
    private var bg: Bitmap? = null
    private var fx: List<Bitmap> = emptyList()
    private val crabCache = HashMap<Frame, Bitmap>()
    private val src = Rect()
    private val dst = Rect()

    private val ticker = object : Runnable {
        override fun run() {
            tick++
            val seq = act ?: loop
            t++
            if (t >= seq.size) {
                t = 0
                act = null
            }
            invalidate()
            postDelayed(this, Clawd.FRAME_MS)
        }
    }

    fun play(seq: List<Frame>) {
        act = seq
        t = 0
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(ticker, Clawd.FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (act == null) {
            play(if (mood == Clawd.Mood.SLEEP || mood == Clawd.Mood.DEAD) Clawd.SEQS.getValue("wink")
                 else Clawd.SEQS.getValue(Clawd.TAP_ACTS.random()))
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * Scenes.H / logicalWidth(w))
    }

    /** Logical pixels across: big chunky pixels, about 6 dp each. */
    private fun logicalWidth(w: Int) = (w / (6 * resources.displayMetrics.density)).toInt().coerceAtLeast(60)

    override fun onDraw(canvas: Canvas) {
        val lw = logicalWidth(width)
        val px = width.toFloat() / lw
        val hour = LocalDateTime.now().hour
        val key = "${theme.id}|$lw|$hour"
        if (key != sceneKey) {
            sceneKey = key
            bg = Scenes.background(theme.scene, lw, hour).toBitmap(1)
            fx = (0 until Scenes.FX_FRAMES).map { Scenes.fx(theme.scene, lw, hour, it).toBitmap(1) }
            crabCache.clear()
        }
        blit(canvas, bg!!, 0f, 0f, px)
        blit(canvas, fx[(tick / 8) % fx.size], 0f, 0f, px)

        val seq = act ?: loop
        val f = seq[t.coerceIn(0, seq.size - 1)]
        val crab = crabCache.getOrPut(f) {
            Pix(Clawd.CANVAS_W, Clawd.CANVAS_H + Scenes.GROUND)
                .also { Clawd.draw(it, f, skin, Clawd.ORIGIN_X, Clawd.ORIGIN_Y, Scenes.sand(theme.scene)) }
                .toBitmap(1)
        }
        val travel = (lw - Clawd.CANVAS_W).coerceAtLeast(0)
        val x = travel * position.coerceIn(0, 100) / 100
        blit(canvas, crab, x * px, (Scenes.H - crab.height) * px, px)
    }

    private fun blit(canvas: Canvas, b: Bitmap, x: Float, y: Float, scale: Float) {
        src.set(0, 0, b.width, b.height)
        dst.set(x.toInt(), y.toInt(), (x + b.width * scale).toInt(), (y + b.height * scale).toInt())
        canvas.drawBitmap(b, src, dst, paint)
    }
}

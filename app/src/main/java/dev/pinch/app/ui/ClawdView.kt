package dev.pinch.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import dev.pinch.app.art.Clawd
import dev.pinch.app.art.Pix
import dev.pinch.app.art.Scenes
import dev.pinch.app.art.Skin
import dev.pinch.app.art.Skins
import dev.pinch.app.art.Stage
import dev.pinch.app.art.Theme
import dev.pinch.app.art.Themes
import java.time.LocalDateTime

/**
 * The big crab in the app: the same stage loop as the widget, played live.
 * Tap him for one of Claude Code's click animations.
 */
class ClawdView(context: Context) : View(context) {

    var theme: Theme = Themes.byId("classic")
        set(value) { field = value; reset() }
    var skin: Skin = Skins.CLASSIC
        set(value) { field = value; reset() }
    var mood: Clawd.Mood = Clawd.Mood.IDLE
        set(value) { field = value; reset() }
    var halves: Int = 6
        set(value) { field = value; reset() }
    /** 0..100 of the session used: where along the stage he lives. */
    var position: Int? = 30
        set(value) { field = value; reset() }

    private var t = 0
    private var act: List<Stage.Beat>? = null
    private var actT = 0
    private var key = ""
    private var bg: Pix? = null
    private var loop: List<Stage.Beat> = emptyList()
    private var stageW = 0

    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private var frame: Bitmap? = null
    private val src = Rect()
    private val dst = Rect()

    private val ticker = object : Runnable {
        override fun run() {
            if (act != null) {
                actT++
                if (actT >= act!!.size) act = null
            }
            t = (t + 1) % Stage.LOOP
            invalidate()
            postDelayed(this, Stage.FRAME_MS)
        }
    }

    private fun reset() { key = ""; invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(ticker, Stage.FRAME_MS)
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
        if (act == null && stageW > 0) {
            val seq = if (mood == Clawd.Mood.SLEEP || mood == Clawd.Mood.DEAD) Clawd.SEQS.getValue("wink")
            else Clawd.SEQS.getValue(Clawd.TAP_ACTS.random())
            // He does his trick right where he is in his walk.
            val here = loop[t].x
            act = Stage.act(seq, here, stageW).map { it.copy(x = here) }
            actT = 0
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * Scenes.H / logicalWidth(w))
    }

    /** Logical pixels across: chunky pixels, about 5 dp each. */
    private fun logicalWidth(w: Int) = (w / (5 * resources.displayMetrics.density)).toInt().coerceAtLeast(60)

    override fun onDraw(canvas: Canvas) {
        val lw = logicalWidth(width)
        val hour = LocalDateTime.now().hour
        val k = "${theme.id}|${skin.id}|$mood|$halves|$position|$lw|$hour"
        if (k != key) {
            key = k
            stageW = lw
            bg = Scenes.background(theme.scene, lw, hour)
            loop = Stage.loop(mood, halves, Stage.home(lw, position), lw, skin.id)
        }
        val beat = act?.getOrNull(actT) ?: loop[t % loop.size]
        val pix = Stage.compose(bg!!, theme.scene, hour, t, Stage.LOOP, beat, skin)
        val bmp = frame?.takeIf { it.width == pix.w && it.height == pix.h }
            ?: Bitmap.createBitmap(pix.w, pix.h, Bitmap.Config.ARGB_8888).also { frame = it }
        bmp.setPixels(pix.px, 0, pix.w, 0, 0, pix.w, pix.h)
        src.set(0, 0, pix.w, pix.h)
        dst.set(0, 0, width, height)
        canvas.drawBitmap(bmp, src, dst, paint)
    }
}

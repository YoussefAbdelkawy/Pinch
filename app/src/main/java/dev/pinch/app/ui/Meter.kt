package dev.pinch.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A rounded usage bar: a track, and a fill in the level's colour. */
class Meter(context: Context) : View(context) {

    var pct: Int = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var color: Int = 0
        set(value) { field = value; invalidate() }
    var track: Int = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (8 * resources.displayMetrics.density).toInt())

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        paint.color = track
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, r, r, paint)
        if (pct == 0) return
        paint.color = color
        // Never thinner than a dot, so 1% still shows.
        rect.right = (width * pct / 100f).coerceAtLeast(height.toFloat())
        c.drawRoundRect(rect, r, r, paint)
    }
}

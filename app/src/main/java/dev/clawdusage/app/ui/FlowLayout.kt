package dev.clawdusage.app.ui

import android.content.Context
import android.view.ViewGroup

/** Lays children out left to right and wraps to a new line, like chips. */
class FlowLayout(context: Context, private val gapPx: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (x > 0 && x + c.measuredWidth > maxW) {
                x = 0
                y += lineH + gapPx
                lineH = 0
            }
            x += c.measuredWidth + gapPx
            lineH = maxOf(lineH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + lineH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x > 0 && x + c.measuredWidth > maxW) {
                x = 0
                y += lineH + gapPx
                lineH = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gapPx
            lineH = maxOf(lineH, c.measuredHeight)
        }
    }
}

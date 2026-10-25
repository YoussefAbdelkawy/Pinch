package dev.pinch.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Switch
import dev.pinch.app.art.Pix

/**
 * An on/off switch drawn as a pill: a rounded track with a round knob that slides right and
 * takes the accent colour when on. Drawn by hand so it recolours with the theme.
 */
class PillSwitch(context: Context, private val onColor: Int, private val offColor: Int, private val knobColor: Int) : View(context) {

    var onChange: ((Boolean) -> Unit)? = null

    var checked = false
        private set

    /** 0 = off, 1 = on; animates between them. */
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            set(!checked, animate = true)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onChange?.invoke(checked)
        }
    }

    fun set(on: Boolean, animate: Boolean = false) {
        checked = on
        anim?.cancel()
        val target = if (on) 1f else 0f
        if (!animate) {
            pos = target
            invalidate()
        } else anim = ValueAnimator.ofFloat(pos, target).apply {
            duration = 150
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
        refreshDrawableState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((48 * d).toInt(), (28 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val r = h / 2
        track.set(0f, 0f, width.toFloat(), h)
        paint.color = Pix.mix(offColor, onColor, pos)
        canvas.drawRoundRect(track, r, r, paint)
        val inset = 3 * resources.displayMetrics.density
        val cx = r + (width - h) * pos
        paint.color = knobColor
        canvas.drawCircle(cx, r, r - inset, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Switch::class.java.name
        info.isCheckable = true
        info.isChecked = checked
    }
}

package dev.pinch.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import dev.pinch.app.art.Pix
import dev.pinch.app.art.Theme
import dev.pinch.app.art.Themes
import dev.pinch.app.data.History
import dev.pinch.app.widget.WidgetRenderer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The app's usage history chart. Two views of the same points: the session over the last day
 * (a line) and each 5-hour window of the last week (bars). Time runs towards the reading
 * direction, so on a right-to-left phone now is on the left. Touch and drag to read a value.
 */
class HistoryChart(context: Context) : View(context) {

    enum class Mode { DAY, SESSIONS }

    /** A bar or a line point: x is a time, y a percent. */
    private class Item(val t: Long, val pct: Int)

    var theme: Theme = Themes.byId("classic")
        set(value) { field = value; invalidate() }

    private var mode = Mode.DAY
    private var items: List<Item> = emptyList()
    private var from = 0L
    private var to = 0L
    private var touched: Int? = null

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * d; pathEffect = DashPathEffect(floatArrayOf(3 * d, 4 * d), 0f) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f * d }
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * d; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val padL = 4 * d
    private val padR = 34 * d
    private val padT = 26 * d
    private val padB = 22 * d

    fun show(mode: Mode, points: List<History.Point>, now: Long = System.currentTimeMillis()) {
        this.mode = mode
        touched = null
        when (mode) {
            Mode.DAY -> {
                from = now - 24 * 3_600_000L
                to = now
                // Inside the day, plus the last point before it so the line starts at the edge.
                val before = points.lastOrNull { it.t < from && it.session != null }
                items = (listOfNotNull(before) + points.filter { it.t >= from && it.session != null })
                    .map { Item(max(it.t, from), sessionAt(it)) }
            }
            Mode.SESSIONS -> {
                from = now - 7 * 24 * 3_600_000L
                to = now + History.SESSION_MS
                items = History.sessions(points).filter { it.endsAt > from }.map { Item(it.endsAt, it.peak) }
            }
        }
        contentDescription = summary()
        invalidate()
    }

    /** A point taken after its window reset reads 0%, as the widget shows it. */
    private fun sessionAt(p: History.Point) =
        if (p.sessionReset != null && p.sessionReset <= p.t) 0 else p.session ?: 0

    val isEmpty get() = items.isEmpty()

    private fun summary(): String = when {
        items.isEmpty() -> ""
        else -> "Peak ${items.maxOf { it.pct }} percent"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (180 * d).toInt())
    }

    private val rtl get() = layoutDirection == LAYOUT_DIRECTION_RTL

    /** Mirrors a left-to-right x for a right-to-left phone: the percent labels move to the left too. */
    private fun flip(xx: Float): Float = if (rtl) width - xx else xx

    private fun x(t: Long): Float {
        val span = (to - from).coerceAtLeast(1)
        return flip(padL + (width - padL - padR) * ((t - from).toFloat() / span))
    }

    private fun y(pct: Int): Float = padT + (height - padT - padB) * (1f - pct.coerceIn(0, 100) / 100f)

    override fun onDraw(c: Canvas) {
        val accent = theme.accent
        grid.color = Pix.withAlpha(theme.muted, 0.35f)
        label.color = theme.muted
        // Grid at 0, 50 and 100 percent, labelled on the right.
        label.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        for (p in intArrayOf(0, 50, 100)) {
            val yy = y(p)
            c.drawLine(flip(padL), yy, flip(width - padR + 4 * d), yy, grid)
            c.drawText("$p%", flip(width - padR + 8 * d), yy + label.textSize / 3, label)
        }
        if (items.isEmpty()) return

        when (mode) {
            Mode.DAY -> drawLine(c, accent)
            Mode.SESSIONS -> drawBars(c)
        }
        drawAxis(c)
        touched?.let { drawTip(c, it) }
    }

    private fun drawLine(c: Canvas, accent: Int) {
        // A step line: usage only changes when a fetch sees it change, and a reset is a drop.
        val path = Path()
        val area = Path()
        val bottom = y(0)
        items.forEachIndexed { i, it ->
            val xx = x(it.t)
            val yy = y(it.pct)
            if (i == 0) {
                path.moveTo(xx, yy); area.moveTo(xx, bottom); area.lineTo(xx, yy)
            } else {
                val prevY = y(items[i - 1].pct)
                path.lineTo(xx, prevY); path.lineTo(xx, yy)
                area.lineTo(xx, prevY); area.lineTo(xx, yy)
            }
        }
        val lastX = x(to)
        val lastY = y(items.last().pct)
        path.lineTo(lastX, lastY)
        area.lineTo(lastX, lastY); area.lineTo(lastX, bottom); area.close()
        fill.shader = LinearGradient(0f, padT, 0f, bottom, Pix.withAlpha(accent, 0.38f), Pix.withAlpha(accent, 0.02f), Shader.TileMode.CLAMP)
        c.drawPath(area, fill)
        line.color = accent
        c.drawPath(path, line)
        // Now: a dot at the end of the line.
        bar.color = accent
        c.drawCircle(lastX, lastY, 4 * d, bar)
        bar.color = theme.card
        c.drawCircle(lastX, lastY, 1.8f * d, bar)
    }

    // A 5-hour window is 5/168 of the week; leave a little air between neighbours.
    private fun barWidth(): Float = max(3 * d, (width - padL - padR) * (History.SESSION_MS.toFloat() / (to - from)) * 0.8f)

    private fun barX(i: Int): Float = x(items[i].t - History.SESSION_MS / 2)

    private fun drawBars(c: Canvas) {
        val w = barWidth()
        val r = min(w / 2, 4 * d)
        items.forEachIndexed { i, it ->
            val cx = barX(i)
            val top = min(y(it.pct), y(0) - 2 * d)
            rect.set(cx - w / 2, top, cx + w / 2, y(0))
            val col = WidgetRenderer.levelColor(it.pct)
            bar.color = if (touched == null || touched == i) col else Pix.withAlpha(col, 0.45f)
            c.drawRoundRect(rect, r, r, bar)
        }
    }

    private fun drawAxis(c: Canvas) {
        val zone = ZoneId.systemDefault()
        val yy = height - 6 * d
        label.textAlign = Paint.Align.CENTER
        when (mode) {
            Mode.DAY -> {
                // Every 6 hours, on the hour.
                val hour = 3_600_000L
                var t = (from / hour + 1) * hour
                while (t < to) {
                    val h = Instant.ofEpochMilli(t).atZone(zone).hour
                    if (h % 6 == 0) c.drawText(hourText(t), x(t), yy, label)
                    t += hour
                }
            }
            Mode.SESSIONS -> {
                // Each midnight, as the day's name.
                var day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().plusDays(1)
                while (true) {
                    val t = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    if (t >= to) break
                    val name = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    c.drawText(name, x(t + 12 * 3_600_000L), yy, label)
                    day = day.plusDays(1)
                }
            }
        }
        label.textAlign = Paint.Align.LEFT
    }

    private fun hourText(t: Long): String {
        val z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        val p = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h a"
        return z.format(DateTimeFormatter.ofPattern(p))
    }

    private fun drawTip(c: Canvas, i: Int) {
        val it = items[i]
        val (xx, text) = when (mode) {
            Mode.DAY -> x(it.t) to "${it.pct}% · ${WidgetRenderer.whenText(context, it.t)}"
            Mode.SESSIONS -> barX(i) to "${it.pct}% peak · ended ${WidgetRenderer.whenText(context, it.t)}"
        }
        grid.color = Pix.withAlpha(theme.text, 0.6f)
        c.drawLine(xx, padT - 4 * d, xx, y(0), grid)
        if (mode == Mode.DAY) {
            bar.color = theme.accent
            c.drawCircle(xx, y(it.pct), 4 * d, bar)
        }
        tip.color = theme.textHi
        val tw = tip.measureText(text)
        val tx = (xx - tw / 2).coerceIn(6 * d, width - tw - 6 * d)
        bar.color = theme.bg
        rect.set(tx - 6 * d, 2 * d, tx + tw + 6 * d, padT - 6 * d)
        c.drawRoundRect(rect, 6 * d, 6 * d, bar)
        c.drawText(text, tx, padT - 11 * d, tip)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = items.indices.minByOrNull { i ->
                    val xx = if (mode == Mode.DAY) x(items[i].t) else barX(i)
                    kotlin.math.abs(xx - e.x)
                }
                if (i != touched) { touched = i; invalidate() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                touched = null
                invalidate()
            }
        }
        return true
    }

    /** Peak and average of what the chart shows, for the line under it. */
    fun stats(): Pair<Int, Int>? =
        if (items.isEmpty()) null else items.maxOf { it.pct } to items.map { it.pct }.average().roundToInt()

    /** How many of the shown windows hit the limit. */
    fun maxedCount(): Int = items.count { it.pct >= 100 }

    fun count(): Int = items.size
}

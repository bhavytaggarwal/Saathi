package com.saathi.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Mood { RESTING, LISTENING, THINKING, SPEAKING, HAPPY }

/** Saathi's face, drawn in code so the app carries no image files. */
class CharacterView(context: Context) : View(context) {
    var mood: Mood = Mood.RESTING
        set(value) {
            field = value
            invalidate()
        }
    var dot: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var showClose: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private val blink = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 900)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(blink)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    /** True if a touch at this point lands on the × badge. */
    fun isOnClose(x: Float, y: Float): Boolean =
        showClose && !dot && x > width - 26 * density && y < 26 * density

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        if (dot) {
            drawDot(canvas, cx, cy, size)
            return
        }
        val margin = 7 * density
        val radius = size / 2f - margin

        // Coloured ring shows the state: green listens, blue thinks, orange speaks.
        val ringColor = when (mood) {
            Mood.LISTENING -> 0xFF23A455.toInt()
            Mood.THINKING -> 0xFF2F7DE1.toInt()
            Mood.SPEAKING, Mood.HAPPY -> 0xFFE8730C.toInt()
            Mood.RESTING -> 0xFFFFFFFF.toInt()
        }
        val pulse = if (mood == Mood.RESTING) 0f else (sin(now / 260.0) * 0.5 + 0.5).toFloat()
        if (mood != Mood.RESTING) {
            paint.style = Paint.Style.FILL
            paint.color = ringColor
            paint.alpha = (70 + 70 * pulse).toInt()
            canvas.drawCircle(cx, cy, radius + margin * pulse, paint)
            paint.alpha = 255
        }
        paint.style = Paint.Style.FILL
        paint.color = ringColor
        paint.setShadowLayer(4 * density, 0f, 2 * density, 0x55000000)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.clearShadowLayer()
        paint.color = 0xFFFFB547.toInt()
        val inner = radius - 3 * density
        canvas.drawCircle(cx, cy, inner, paint)

        val unit = inner / 24f

        // Hair and bun.
        paint.color = 0xFF2E1C14.toInt()
        canvas.drawCircle(cx, cy - 19.5f * unit, 4.3f * unit, paint)
        canvas.drawCircle(cx, cy - 3f * unit, 15.5f * unit, paint)

        // Face.
        paint.color = 0xFFC98A5E.toInt()
        arc.set(cx - 13f * unit, cy - 12f * unit, cx + 13f * unit, cy + 18f * unit)
        canvas.drawOval(arc, paint)

        // Bindi.
        paint.color = 0xFFC8102E.toInt()
        canvas.drawCircle(cx, cy - 6f * unit, 1.3f * unit, paint)

        // Eyes blink for a moment every few seconds.
        val eyeY = cy - 0.5f * unit
        val blinking = now % 4200 < 160
        paint.color = 0xFF2E1C14.toInt()
        if (blinking || mood == Mood.HAPPY) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * unit
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx - 7f * unit, eyeY, cx - 3f * unit, eyeY, paint)
            canvas.drawLine(cx + 3f * unit, eyeY, cx + 7f * unit, eyeY, paint)
            paint.style = Paint.Style.FILL
        } else {
            canvas.drawCircle(cx - 5f * unit, eyeY, 1.9f * unit, paint)
            canvas.drawCircle(cx + 5f * unit, eyeY, 1.9f * unit, paint)
        }

        // Mouth moves while she talks, otherwise a smile.
        val mouthY = cy + 8f * unit
        if (mood == Mood.SPEAKING) {
            val open = (abs(sin(now / 90.0)) * 3.2 + 0.8).toFloat() * unit
            paint.color = 0xFF7A2E1D.toInt()
            arc.set(cx - 3.2f * unit, mouthY - open / 2, cx + 3.2f * unit, mouthY + open / 2)
            canvas.drawOval(arc, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.3f * unit
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = 0xFF7A2E1D.toInt()
            arc.set(cx - 5f * unit, mouthY - 5f * unit, cx + 5f * unit, mouthY + 3f * unit)
            canvas.drawArc(arc, 25f, 130f, false, paint)
            paint.style = Paint.Style.FILL
        }

        // Three moving dots while thinking.
        if (mood == Mood.THINKING) {
            paint.color = Color.WHITE
            for (index in 0..2) {
                val bounce = sin(now / 180.0 + index).toFloat() * 1.3f * unit
                canvas.drawCircle(cx + (index - 1) * 5f * unit, cy + 18.5f * unit + bounce, 1.5f * unit, paint)
            }
        }

        if (showClose) drawClose(canvas)
        if (mood != Mood.RESTING) postInvalidateOnAnimation()
    }

    private fun drawDot(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawCircle(cx, cy, size / 2f - density, paint)
        paint.color = 0xE6E8730C.toInt()
        canvas.drawCircle(cx, cy, size / 2f - 3 * density, paint)
    }

    private fun drawClose(canvas: Canvas) {
        val badge = 10f * density
        val x = width - badge - density
        val y = badge + density
        paint.style = Paint.Style.FILL
        paint.color = 0xFF333333.toInt()
        canvas.drawCircle(x, y, badge, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2 * density
        paint.strokeCap = Paint.Cap.ROUND
        val arm = badge * 0.42f
        canvas.drawLine(x - arm, y - arm, x + arm, y + arm, paint)
        canvas.drawLine(x - arm, y + arm, x + arm, y - arm, paint)
        paint.style = Paint.Style.FILL
    }
}

/**
 * Full-screen, untouchable layer: a soft spotlight, a pulsing ring around the target, an arrow from
 * the character, and a moving finger that shows how to do a swipe. It only draws. Every touch goes
 * straight through to the app, so she does the tapping herself.
 */
class PointerView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val hole = RectF()
    private val location = IntArray(2)

    private var target: Rect? = null
    private var gesture = "none"
    private var gestureNav = false
    private var fromX = -1f
    private var fromY = -1f

    fun point(target: Rect?, gesture: String, usesGestureNavigation: Boolean) {
        this.target = target?.let(::Rect)
        this.gesture = gesture
        gestureNav = usesGestureNavigation
        visibility = if (target != null || gesture.startsWith("nav_")) VISIBLE else GONE
        invalidate()
    }

    fun setArrowStart(x: Float, y: Float) {
        fromX = x
        fromY = y
        invalidate()
    }

    fun clear() {
        target = null
        gesture = "none"
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        getLocationOnScreen(location)
        val w = width.toFloat()
        val h = height.toFloat()
        val rect = target
        if (rect != null) {
            drawSpotlight(canvas, rect, now, w, h)
            val left = (rect.left - location[0]).toFloat()
            val top = (rect.top - location[1]).toFloat()
            val cx = left + rect.width() / 2f
            val cy = top + rect.height() / 2f
            drawArrow(canvas, now, cx, cy, rect)
            when (gesture) {
                "swipe_up" -> drawFinger(canvas, now, cx, cy, cx, cy - h * 0.3f)
                "swipe_down" -> drawFinger(canvas, now, cx, cy, cx, cy + h * 0.3f)
                "swipe_left" -> drawFinger(canvas, now, cx, cy, cx - w * 0.35f, cy)
                "swipe_right" -> drawFinger(canvas, now, cx, cy, cx + w * 0.35f, cy)
            }
        }
        when (gesture) {
            "nav_recents" -> if (gestureNav) {
                drawFinger(canvas, now, w / 2, h - 12 * density, w / 2, h * 0.55f)
            } else {
                drawNavBand(canvas, now, w, h)
            }
            "nav_home" -> if (gestureNav) {
                drawFinger(canvas, now, w / 2, h - 12 * density, w / 2, h * 0.75f)
            } else {
                drawNavBand(canvas, now, w, h)
            }
            "nav_back" -> if (gestureNav) {
                drawFinger(canvas, now, 6 * density, h * 0.6f, w * 0.4f, h * 0.6f)
            } else {
                drawNavBand(canvas, now, w, h)
            }
        }
        postInvalidateOnAnimation()
    }

    private fun drawSpotlight(canvas: Canvas, rect: Rect, now: Long, w: Float, h: Float) {
        val pad = 6 * density
        hole.set(
            rect.left - location[0] - pad,
            rect.top - location[1] - pad,
            rect.right - location[0] + pad,
            rect.bottom - location[1] + pad,
        )
        val corner = 14 * density
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, w, h, Path.Direction.CW)
        path.addRoundRect(hole, corner, corner, Path.Direction.CW)
        canvas.drawPath(path, scrim)

        val pulse = (sin(now / 280.0) * 0.5 + 0.5).toFloat()
        // A white edge keeps the ring visible on any app's colours.
        ring.color = Color.WHITE
        ring.strokeWidth = 7 * density
        canvas.drawRoundRect(hole, corner, corner, ring)
        ring.color = 0xFFE8730C.toInt()
        ring.strokeWidth = 4 * density
        canvas.drawRoundRect(hole, corner, corner, ring)

        val grow = 3 * density + 11 * density * pulse
        ring.color = 0xFFE8730C.toInt()
        ring.alpha = (170 * (1 - pulse)).toInt()
        ring.strokeWidth = 3 * density
        canvas.drawRoundRect(
            hole.left - grow, hole.top - grow, hole.right + grow, hole.bottom + grow,
            corner + grow, corner + grow, ring,
        )
        ring.alpha = 255
    }

    private fun drawArrow(canvas: Canvas, now: Long, cx: Float, cy: Float, rect: Rect) {
        if (fromX < 0) return
        val startX = fromX - location[0]
        val startY = fromY - location[1]
        val angle = atan2(cy - startY, cx - startX)
        val halfW = rect.width() / 2f + 12 * density
        val halfH = rect.height() / 2f + 12 * density
        // Stop the arrow at the edge of the ring, not its centre.
        val toEdge = min(
            if (abs(cos(angle)) < 0.001f) Float.MAX_VALUE else halfW / abs(cos(angle)),
            if (abs(sin(angle)) < 0.001f) Float.MAX_VALUE else halfH / abs(sin(angle)),
        )
        val nudge = (sin(now / 200.0) * 4 * density).toFloat()
        val endX = cx - cos(angle) * (toEdge + nudge)
        val endY = cy - sin(angle) * (toEdge + nudge)
        val faceGap = 40 * density
        val length = hypot(endX - startX, endY - startY)
        if (length < 6 * density) return
        val lineStart = min(faceGap, max(length - 24 * density, 0f))
        val beginX = startX + cos(angle) * lineStart
        val beginY = startY + sin(angle) * lineStart

        path.reset()
        path.fillType = Path.FillType.WINDING
        val head = 16 * density
        path.moveTo(endX, endY)
        path.lineTo(endX - cos(angle - 0.5f) * head, endY - sin(angle - 0.5f) * head)
        path.lineTo(endX - cos(angle + 0.5f) * head, endY - sin(angle + 0.5f) * head)
        path.close()

        ring.color = Color.WHITE
        ring.strokeWidth = 8 * density
        canvas.drawLine(beginX, beginY, endX, endY, ring)
        canvas.drawPath(path, ring)
        ring.color = 0xFFE8730C.toInt()
        ring.strokeWidth = 4.5f * density
        canvas.drawLine(beginX, beginY, endX, endY, ring)
        fill.color = 0xFFE8730C.toInt()
        canvas.drawPath(path, fill)
    }

    private fun drawFinger(canvas: Canvas, now: Long, x0: Float, y0: Float, x1: Float, y1: Float) {
        val cycle = 1500L
        val t = ((now % cycle).toFloat() / cycle).coerceIn(0f, 1f)
        val travel = ((1 - cos(min(t / 0.75f, 1f) * PI)) / 2).toFloat()
        val x = x0 + (x1 - x0) * travel
        val y = y0 + (y1 - y0) * travel
        ring.color = 0xFFE8730C.toInt()
        ring.alpha = 130
        ring.strokeWidth = 8 * density
        canvas.drawLine(x0, y0, x, y, ring)
        ring.alpha = 255
        fill.color = Color.WHITE
        fill.alpha = 235
        canvas.drawCircle(x, y, 20 * density, fill)
        fill.alpha = 255
        fill.color = 0xFFE8730C.toInt()
        canvas.drawCircle(x, y, 14 * density, fill)
    }

    private fun drawNavBand(canvas: Canvas, now: Long, w: Float, h: Float) {
        val pulse = (sin(now / 280.0) * 0.5 + 0.5).toFloat()
        fill.color = 0xFFE8730C.toInt()
        fill.alpha = (70 + 110 * pulse).toInt()
        canvas.drawRect(0f, h - 56 * density, w, h, fill)
        fill.alpha = 255
    }
}

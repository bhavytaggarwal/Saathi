package com.saathi.app

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * All of Saathi's windows, drawn with the accessibility overlay type so she needs no separate
 * "Display over other apps" permission. Must be created with the service's own context.
 */
class OverlayController(private val service: AccessibilityService, private val prefs: Prefs, private val cb: Callbacks) {
    interface Callbacks {
        fun onBubbleTap()
        fun onStopTapped()
        fun onListenTapped()
        fun onRepeatTapped()
        fun onTextSubmitted(text: String)
        fun onTypingStarted()
        fun onBubbleDismissed()
        fun onReminderDone()
        fun onReminderSnooze()
    }

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ui = ContextThemeWrapper(service, android.R.style.Theme_Material_Light_NoActionBar)
    private val density = service.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private val pointer = PointerView(ui).apply { visibility = View.GONE }
    private val bubble = CharacterView(ui)
    private var attached = false
    private var panelAttached = false
    private var isDot = false
    private var panelAtTop = false
    private var mover: ValueAnimator? = null

    private val bubbleSize get() = dp(if (isDot) DOT_DP else BUBBLE_DP)

    private fun dp(value: Int) = (value * density).toInt()

    private fun screen(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also { wm.defaultDisplay.getRealSize(it) }
        }
    }

    fun screenSize(): Point = screen()

    private fun baseParams(width: Int, height: Int, extraFlags: Int = 0) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val pointerParams = screen().let { baseParams(it.x, it.y, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) }
    private val bubbleParams = baseParams(dp(BUBBLE_DP), dp(BUBBLE_DP))

    // Panel views

    private val heard = TextView(ui).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(0xFF6A5D4D.toInt())
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    private val message = TextView(ui).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FULL_TEXT_SP)
        setTextColor(0xFF1F1A14.toInt())
        maxLines = FULL_LINES
        ellipsize = TextUtils.TruncateAt.END
    }
    private val input = EditText(ui).apply {
        hint = "Yahan likhiye..."
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        imeOptions = EditorInfo.IME_ACTION_SEND
        setSingleLine(true)
        setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                submitTyped()
                true
            } else {
                false
            }
        }
    }
    private val inputRow = LinearLayout(ui).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(pillButton("Bhejo", 0xFF0F5E5C.toInt()) { submitTyped() })
    }
    private val actionRow = LinearLayout(ui).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(pillButton("Bolo", 0xFF0F5E5C.toInt()) { cb.onListenTapped() }, rowWeight())
        addView(pillButton("Likho", 0xFF5C6B73.toInt()) { showInput() }, rowWeight())
        addView(pillButton("Phir se", 0xFF5C6B73.toInt()) { cb.onRepeatTapped() }, rowWeight())
        addView(
            pillButton("✕", 0xFF8A7F73.toInt()) { cb.onStopTapped() }.apply { contentDescription = "Band karo" },
            LinearLayout.LayoutParams(dp(46), dp(BUTTON_DP)).apply { setMargins(dp(3), 0, 0, 0) },
        )
    }
    private val reminderRow = LinearLayout(ui).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        addView(pillButton("Ho gaya", 0xFF23A455.toInt()) { cb.onReminderDone() }, rowWeight())
        addView(pillButton("10 minute baad", 0xFFE8730C.toInt()) { cb.onReminderSnooze() }, rowWeight())
    }
    private val panel = object : FrameLayout(ui) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && inputRow.visibility == VISIBLE) {
                hideInput()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }.apply {
        val card = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFFFFF8EF.toInt())
                cornerRadius = 22 * density
                setStroke(dp(2), 0xFFE8730C.toInt())
            }
            elevation = 10 * density
            // A slim strip expands when she taps it.
            setOnClickListener { setCompact(false) }
            addView(heard)
            addView(message)
            addView(inputRow, topGap(6))
            addView(reminderRow, topGap(6))
            addView(actionRow, topGap(6))
        }
        addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(10), dp(4), dp(10), dp(4))
            },
        )
    }
    private val panelParams = baseParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    )
    private var compact = false
    private var reminderShown = false

    private fun rowWeight() = LinearLayout.LayoutParams(0, dp(BUTTON_DP), 1f).apply {
        setMargins(dp(3), 0, dp(3), 0)
    }

    private fun topGap(gap: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(gap) }

    private fun pillButton(label: String, color: Int, onClick: () -> Unit) = Button(ui, null, 0, 0).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 26 * density
        }
        setOnClickListener { onClick() }
    }

    // Lifecycle

    fun attach() {
        if (attached) return
        attached = true
        // The order sets the stacking: spotlight lowest, then the face, then the panel.
        wm.addView(pointer, pointerParams)
        restoreBubbleHome()
        wm.addView(bubble, bubbleParams)
        installBubbleTouch()
        // Touching the app beneath shrinks the panel. The touch itself still reaches the app.
        panel.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && inputRow.visibility != View.VISIBLE) {
                setCompact(true)
            }
            false
        }
    }

    fun detach() {
        if (!attached) return
        attached = false
        mover?.cancel()
        hidePanel()
        runCatching { wm.removeView(pointer) }
        runCatching { wm.removeView(bubble) }
    }

    fun onScreenChanged() {
        if (!attached) return
        val size = screen()
        pointerParams.width = size.x
        pointerParams.height = size.y
        wm.updateViewLayout(pointer, pointerParams)
        restoreBubbleHome()
        wm.updateViewLayout(bubble, bubbleParams)
    }

    private fun restoreBubbleHome() {
        val size = screen()
        val saved = prefs.bubbleX
        bubbleParams.x = if (saved >= 0) saved.coerceIn(0, size.x - bubbleSize) else size.x - bubbleSize - dp(4)
        val savedY = prefs.bubbleY
        bubbleParams.y = if (savedY >= 0) savedY.coerceIn(0, size.y - bubbleSize) else size.y * 2 / 3
    }

    // State

    fun setMood(mood: Mood) {
        bubble.mood = mood
    }

    fun setSessionBadge(active: Boolean) {
        bubble.showClose = active
    }

    /** Removes everything from the screen at once, used for PIN, OTP and password screens. */
    fun hideAll() {
        if (!attached) return
        mover?.cancel()
        pointer.clear()
        bubble.visibility = View.GONE
        hideInput()
        if (panelAttached) panel.visibility = View.GONE
    }

    fun show(dot: Boolean, withPanel: Boolean) {
        if (!attached) return
        if (isDot != dot) {
            isDot = dot
            bubble.dot = dot
            bubbleParams.width = bubbleSize
            bubbleParams.height = bubbleSize
            bubbleParams.x = bubbleParams.x.coerceAtMost(screen().x - bubbleSize)
            wm.updateViewLayout(bubble, bubbleParams)
        }
        bubble.visibility = View.VISIBLE
        if (withPanel) showPanel() else hidePanel()
    }

    fun hideBubbleOnly() {
        bubble.visibility = View.GONE
    }

    private fun showPanel() {
        if (panelAttached) {
            panel.visibility = View.VISIBLE
            return
        }
        panelParams.gravity = (if (panelAtTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
        panelParams.y = panelOffset()
        wm.addView(panel, panelParams)
        panelAttached = true
    }

    private fun hidePanel() {
        if (!panelAttached) return
        hideInput()
        runCatching { wm.removeView(panel) }
        panelAttached = false
    }

    private fun panelOffset() = dp(if (panelAtTop) 30 else 42)

    private fun placePanel(atTop: Boolean) {
        if (panelAtTop == atTop) return
        panelAtTop = atTop
        if (panelAttached) {
            panelParams.gravity = (if (atTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            panelParams.y = panelOffset()
            wm.updateViewLayout(panel, panelParams)
        }
    }

    fun setMessage(text: String) {
        message.text = text
    }

    fun setHeard(text: String?) {
        heard.text = text.orEmpty()
        updatePanelRows()
    }

    fun showReminderButtons(show: Boolean) {
        reminderShown = show
        if (show) compact = false
        updatePanelRows()
    }

    /** Slim strip of up to two lines, so the app beneath stays visible while she follows a step. */
    fun setCompact(value: Boolean) {
        compact = value && !reminderShown
        updatePanelRows()
    }

    private fun updatePanelRows() {
        heard.visibility = if (compact || heard.text.isNullOrBlank()) View.GONE else View.VISIBLE
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) COMPACT_TEXT_SP else FULL_TEXT_SP)
        message.maxLines = if (compact) COMPACT_LINES else FULL_LINES
        reminderRow.visibility = if (reminderShown) View.VISIBLE else View.GONE
        actionRow.visibility = if (compact || reminderShown) View.GONE else View.VISIBLE
    }

    // Typing

    private fun showInput() {
        cb.onTypingStarted()
        setCompact(false)
        inputRow.visibility = View.VISIBLE
        placePanel(true)
        panelParams.flags = panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        panelParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        if (panelAttached) wm.updateViewLayout(panel, panelParams)
        input.requestFocus()
        handler.postDelayed({
            ui.getSystemService(InputMethodManager::class.java).showSoftInput(input, 0)
        }, 150)
    }

    private fun hideInput() {
        if (inputRow.visibility != View.VISIBLE) return
        ui.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        inputRow.visibility = View.GONE
        input.text.clear()
        // Give focus back, or the app beneath stops receiving its keyboard and the screen reads as ours.
        panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        panelParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        if (panelAttached) wm.updateViewLayout(panel, panelParams)
        // The panel only moved up to clear the keyboard, so it goes back down afterwards.
        placePanel(false)
    }

    private fun submitTyped() {
        val text = input.text.toString().trim()
        hideInput()
        if (text.isNotEmpty()) cb.onTextSubmitted(text)
    }

    // Pointing

    /** Puts the ring on the target and moves the character beside it, with the panel out of the way. */
    fun pointAt(target: Rect?, gesture: String, usesGestureNavigation: Boolean) {
        if (!attached) return
        val size = screen()
        setCompact(true)
        if (target != null) {
            placePanel(target.centerY() > size.y / 2)
            val destination = besideTarget(target, size)
            moveBubble(destination.x, destination.y)
            pointer.setArrowStart(destination.x + bubbleSize / 2f, destination.y + bubbleSize / 2f)
        } else {
            // System gestures happen at the bottom edge, so only they move the panel up.
            placePanel(gesture.startsWith("nav_"))
            pointer.setArrowStart(-1f, -1f)
        }
        pointer.point(target, gesture, usesGestureNavigation)
    }

    private fun besideTarget(target: Rect, size: Point): Point {
        val s = bubbleSize
        val gap = dp(10)
        val narrow = target.width() < size.x * 2 / 5
        val y = target.centerY() - s / 2
        if (narrow) {
            if (target.left - gap - s >= 0) return Point(target.left - gap - s, y.coerceIn(0, size.y - s))
            if (target.right + gap + s <= size.x) return Point(target.right + gap, y.coerceIn(0, size.y - s))
        }
        val x = (target.right - s).coerceIn(0, size.x - s)
        // The panel takes the far half of the screen, so she stays on the near side of the target.
        val above = target.top - gap - s
        val below = target.bottom + gap
        val preferAbove = target.centerY() > size.y / 2
        return when {
            preferAbove && above >= 0 -> Point(x, above)
            !preferAbove && below + s <= size.y -> Point(x, below)
            above >= 0 -> Point(x, above)
            else -> Point(x, below.coerceAtMost(size.y - s))
        }
    }

    fun clearPointer() {
        pointer.clear()
    }

    fun returnHome() {
        if (!attached) return
        pointer.clear()
        compact = false
        placePanel(false)
        val size = screen()
        val x = if (prefs.bubbleX >= 0) prefs.bubbleX.coerceIn(0, size.x - bubbleSize) else size.x - bubbleSize - dp(4)
        val y = if (prefs.bubbleY >= 0) prefs.bubbleY.coerceIn(0, size.y - bubbleSize) else size.y * 2 / 3
        moveBubble(x, y)
    }

    private fun moveBubble(x: Int, y: Int) {
        mover?.cancel()
        val startX = bubbleParams.x
        val startY = bubbleParams.y
        mover = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            addUpdateListener { animation ->
                if (!attached) return@addUpdateListener
                val t = animation.animatedValue as Float
                bubbleParams.x = (startX + (x - startX) * t).toInt()
                bubbleParams.y = (startY + (y - startY) * t).toInt()
                runCatching { wm.updateViewLayout(bubble, bubbleParams) }
            }
            start()
        }
    }

    // Dragging

    private fun installBubbleTouch() {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var hint: TextView? = null
        val slop = dp(9)

        bubble.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mover?.cancel()
                    downX = event.rawX
                    downY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        hint = addDismissHint()
                    }
                    if (dragging) {
                        val size = screen()
                        bubbleParams.x = (startX + dx).toInt().coerceIn(-bubbleSize / 2, size.x - bubbleSize / 2)
                        bubbleParams.y = (startY + dy).toInt().coerceIn(0, size.y - bubbleSize)
                        wm.updateViewLayout(bubble, bubbleParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hint?.let { runCatching { wm.removeView(it) } }
                    hint = null
                    if (!dragging) {
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            view.performClick()
                            if (bubble.isOnClose(event.x, event.y)) cb.onStopTapped() else cb.onBubbleTap()
                        }
                    } else {
                        finishDrag()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun finishDrag() {
        val size = screen()
        val centerX = bubbleParams.x + bubbleSize / 2
        val nearBottom = bubbleParams.y + bubbleSize > size.y - dp(DISMISS_ZONE_DP)
        if (nearBottom && abs(centerX - size.x / 2) < size.x / 3) {
            cb.onBubbleDismissed()
            return
        }
        // Rest against the nearer edge so it covers as little as possible.
        val x = if (centerX < size.x / 2) dp(4) else size.x - bubbleSize - dp(4)
        prefs.bubbleX = x
        prefs.bubbleY = bubbleParams.y
        moveBubble(x, bubbleParams.y)
    }

    private fun addDismissHint(): TextView {
        val view = TextView(ui).apply {
            text = "Yahan chhodo, chhup jaungi"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(0xCC222222.toInt())
                cornerRadius = 24 * density
            }
        }
        val params = baseParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        wm.addView(view, params)
        return view
    }

    private companion object {
        const val BUBBLE_DP = 72
        const val DOT_DP = 30
        const val DISMISS_ZONE_DP = 110
        const val BUTTON_DP = 44
        const val FULL_TEXT_SP = 19f
        const val COMPACT_TEXT_SP = 17f
        const val FULL_LINES = 4
        const val COMPACT_LINES = 2
    }
}

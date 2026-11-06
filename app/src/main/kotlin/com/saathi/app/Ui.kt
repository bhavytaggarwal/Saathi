package com.saathi.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small helpers for screens built with plain Android views. */
internal fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** A scrolling page that keeps its content clear of the status and navigation bars. */
internal fun Activity.page(): Pair<ScrollView, LinearLayout> {
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(24))
    }
    val scroll = ScrollView(this).apply {
        addView(content)
        isFillViewport = true
        setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
    }
    return scroll to content
}

internal fun Activity.title(text: String) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
    setTextColor(0xFF0F5E5C.toInt())
    typeface = Typeface.DEFAULT_BOLD
}

internal fun Activity.heading(text: String) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    setTextColor(0xFF1F1A14.toInt())
    typeface = Typeface.DEFAULT_BOLD
    setPadding(0, dp(18), 0, dp(6))
}

internal fun Activity.body(text: String) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTextColor(0xFF5A5046.toInt())
    setLineSpacing(0f, 1.15f)
}

internal fun Activity.primaryButton(text: String, color: Int = 0xFF0F5E5C.toInt(), onClick: () -> Unit) =
    Button(this, null, 0, 0).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        minimumHeight = dp(44)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 22f * resources.displayMetrics.density
        }
        setOnClickListener { onClick() }
    }

internal fun Activity.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(14), dp(12), dp(14), dp(12))
    background = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = 16f * resources.displayMetrics.density
        setStroke(dp(1), 0xFFEADFD0.toInt())
    }
}

internal fun Activity.wrapWithGap(top: Int = 10) = LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT,
).apply { topMargin = dp(top) }

internal fun View.visibleIf(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

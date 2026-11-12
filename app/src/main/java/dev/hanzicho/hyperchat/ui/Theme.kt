package dev.hanzicho.hyperchat.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import dev.hanzicho.hyperchat.R

internal class Theme(private val context: Context) {
    val background = context.getColor(R.color.hc_background)
    val surface = context.getColor(R.color.hc_surface)
    val elevated = context.getColor(R.color.hc_elevated)
    val outline = context.getColor(R.color.hc_outline)
    val text = context.getColor(R.color.hc_text)
    val muted = context.getColor(R.color.hc_muted)
    val accent = context.getColor(R.color.hc_accent)
    val onAccent = context.getColor(R.color.hc_on_accent)
    val success = context.getColor(R.color.hc_success)
    val warning = context.getColor(R.color.hc_warning)
    val error = context.getColor(R.color.hc_error)

    fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun round(color: Int, radius: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), outline)
    }

    fun label(value: CharSequence, size: Float = 14f, color: Int = text, bold: Boolean = false) =
        TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun iconButton(icon: Int, label: CharSequence, primary: Boolean = false, click: () -> Unit) =
        ImageButton(context).apply {
            setImageResource(icon)
            contentDescription = label
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = ColorStateList.valueOf(if (primary) onAccent else text)
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            background = RippleDrawable(
                ColorStateList.valueOf(if (primary) 0x24000000 else 0x26FFFFFF),
                round(if (primary) accent else elevated, 24),
                null,
            )
            setOnClickListener { click() }
        }

    fun button(value: String, primary: Boolean = false, click: () -> Unit) =
        android.widget.Button(context).apply {
            text = value
            textSize = 14f
            setTextColor(if (primary) onAccent else this@Theme.text)
            minHeight = dp(48)
            minimumHeight = dp(48)
            minWidth = 0
            minimumWidth = 0
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            isAllCaps = false
            stateListAnimator = null
            background = RippleDrawable(
                ColorStateList.valueOf(0x24000000),
                round(if (primary) accent else elevated, 12),
                null,
            )
            setOnClickListener { click() }
        }
}

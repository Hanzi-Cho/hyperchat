package dev.hanzicho.hyperchat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns the position and touch gestures of the collapsed accessibility window. */
internal class FloatingLauncher(
    context: Context,
    private val wm: WindowManager,
    private val theme: Theme,
) {
    private val saved = context.getSharedPreferences("floating_launcher", Context.MODE_PRIVATE)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val holdTime = ViewConfiguration.getLongPressTimeout()

    @SuppressLint("ClickableViewAccessibility") // A tap and a hold use the view's accessibility actions.
    fun configure(
        button: View,
        layout: WindowManager.LayoutParams,
        swipeUp: () -> Unit,
        hold: (() -> Unit)?,
        cancelHold: () -> Unit,
    ) {
        layout.width = theme.dp(48)
        layout.height = layout.width
        // Absolute left keeps physical drag directions consistent in RTL layouts.
        @SuppressLint("RtlHardcoded")
        val physicalOrigin = Gravity.TOP or Gravity.LEFT
        layout.gravity = physicalOrigin
        if (Build.VERSION.SDK_INT >= 30) {
            layout.fitInsetsTypes = protectedInsets()
            layout.isFitInsetsIgnoringVisibility = true
            layout.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        restorePosition(layout)
        if (hold != null) button.setOnLongClickListener { hold(); true }
        button.setOnTouchListener(DragGesture(button, layout, swipeUp, cancelHold))
    }

    fun reposition(button: View) {
        val layout = button.layoutParams as? WindowManager.LayoutParams ?: return
        layout.width = theme.dp(48)
        layout.height = layout.width
        restorePosition(layout)
        wm.updateViewLayout(button, layout)
    }

    private fun restorePosition(layout: WindowManager.LayoutParams) {
        val area = movementArea(layout)
        layout.x = area.left + (area.width() * saved.getFloat("x", 1f).coerceIn(0f, 1f)).roundToInt()
        layout.y = area.top + (area.height() * saved.getFloat("y", 1f).coerceIn(0f, 1f)).roundToInt()
    }

    private fun persistPosition(layout: WindowManager.LayoutParams) {
        val area = movementArea(layout)
        val editor = saved.edit()
        if (area.width() > 0) editor.putFloat("x", (layout.x - area.left).toFloat() / area.width())
        if (area.height() > 0) editor.putFloat("y", (layout.y - area.top).toFloat() / area.height())
        editor.apply()
    }

    private fun movementArea(layout: WindowManager.LayoutParams): Rect {
        val size = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(protectedInsets())
            Point(metrics.bounds.width() - insets.left - insets.right, metrics.bounds.height() - insets.top - insets.bottom)
        } else {
            @Suppress("DEPRECATION")
            Point().also { wm.defaultDisplay.getSize(it) }
        }
        val margin = theme.dp(12)
        return Rect(margin, margin, maxOf(margin, size.x - layout.width - margin),
            maxOf(margin, size.y - layout.height - margin))
    }

    @androidx.annotation.RequiresApi(30)
    private fun protectedInsets() =
        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.mandatorySystemGestures()

    private inner class DragGesture(
        private val button: View,
        private val layout: WindowManager.LayoutParams,
        private val swipeUp: () -> Unit,
        private val cancelHold: () -> Unit,
    ) : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var windowX = 0
        private var windowY = 0
        private var touching = false
        private var dragging = false
        private var movedBeforeHold = false
        private var held = false
        private val notifyHold = Runnable {
            if (touching && button.isAttachedToWindow && !dragging && !movedBeforeHold) {
                held = button.performLongClick()
            }
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                startX = event.rawX
                startY = event.rawY
                windowX = layout.x
                windowY = layout.y
                touching = true
                dragging = false
                movedBeforeHold = false
                held = false
                view.isPressed = true
                if (view.isLongClickable) view.postDelayed(notifyHold, holdTime.toLong())
                return true
            }
            if (!touching) return true
            val dx = event.rawX - startX
            val dy = event.rawY - startY
            val elapsed = event.eventTime - event.downTime
            val moved = dx * dx + dy * dy > slop * slop
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (moved && !dragging && !movedBeforeHold) {
                        view.removeCallbacks(notifyHold)
                        if (elapsed >= holdTime) {
                            dragging = true
                            if (held) cancelHold() else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            movedBeforeHold = true
                        }
                        view.isPressed = false
                    }
                    if (dragging) move(view, dx, dy)
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(notifyHold)
                    touching = false
                    view.isPressed = false
                    when {
                        dragging -> {
                            move(view, dx, dy)
                            persistPosition(layout)
                        }
                        held -> Unit
                        elapsed >= holdTime && !moved && !movedBeforeHold -> view.performLongClick()
                        elapsed < holdTime && -dy >= theme.dp(32) && -dy > 1.4f * abs(dx) -> swipeUp()
                        elapsed < holdTime && !moved && !movedBeforeHold -> view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    view.removeCallbacks(notifyHold)
                    touching = false
                    view.isPressed = false
                    if (held) cancelHold()
                    if (dragging) move(view, 0f, 0f)
                }
            }
            return true
        }

        private fun move(view: View, dx: Float, dy: Float) {
            val area = movementArea(layout)
            val x = (windowX + dx.roundToInt()).coerceIn(area.left, area.right)
            val y = (windowY + dy.roundToInt()).coerceIn(area.top, area.bottom)
            if (layout.x == x && layout.y == y) return
            layout.x = x
            layout.y = y
            wm.updateViewLayout(view, layout)
        }
    }
}

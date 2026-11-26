package com.example.friendadder.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus

/**
 * Compacte commande flottante de l'accessibility service.
 * TYPE_ACCESSIBILITY_OVERLAY ne demande pas l'autorisation « afficher par-dessus ».
 */
class FloatingControlPanel(
    private val service: AccessibilityService,
    private val onConfirm: () -> Unit,
    private val onStop: () -> Unit
) {
    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager

    private var panel: LinearLayout? = null
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var confirmButton: Button
    private lateinit var stopButton: Button

    fun render(status: BotStatus) {
        if (status.state == BotState.RUNNING) show()
        val root = panel ?: return

        statusText.text = when {
            status.state != BotState.RUNNING -> "STOPPED"
            status.waitingForFacebook -> "WAITING FOR FACEBOOK"
            status.awaitingConfirmation ->
                "FOUND: « ${status.detectedLabel ?: "Ajouter"} »"
            else -> "SCANNING FOR « AJOUTER »"
        }

        statsText.text =
            "CONFIRMED  ${status.adds}      FOUND  ${status.detectedButtons}\n" +
            "ERRORS  ${status.dialogs}          SCROLLS  ${status.scrolls}"

        confirmButton.isEnabled =
            status.state == BotState.RUNNING && status.awaitingConfirmation
        confirmButton.alpha = if (confirmButton.isEnabled) 1f else 0.45f
        stopButton.isEnabled = status.state == BotState.RUNNING
        stopButton.alpha = if (stopButton.isEnabled) 1f else 0.45f

        // Keep the view attached while stopped so the final counters remain visible.
        root.contentDescription = statusText.text
    }

    private fun show() {
        if (panel != null) return

        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(BG, CRIMSON, dp(16).toFloat())
            elevation = dp(10).toFloat()
        }

        val title = TextView(service).apply {
            text = "EAGLE ASSIST  •  LIVE"
            setTextColor(WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        statusText = TextView(service).apply {
            text = "SCANNING FOR « AJOUTER »"
            setTextColor(CRIMSON_LIGHT)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(9), 0, dp(8))
        }
        root.addView(statusText)

        statsText = TextView(service).apply {
            setTextColor(WHITE)
            textSize = 12f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(statsText)

        val actions = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        confirmButton = Button(service).apply {
            text = "CONFIRM ONE"
            isAllCaps = false
            textSize = 11f
            setTextColor(WHITE)
            background = roundedBackground(CRIMSON, CRIMSON_LIGHT, dp(9).toFloat())
            setPadding(dp(7), 0, dp(7), 0)
            setOnClickListener { onConfirm() }
        }
        actions.addView(confirmButton, LinearLayout.LayoutParams(0, dp(44), 1f))

        val gap = android.view.View(service)
        actions.addView(gap, LinearLayout.LayoutParams(dp(8), 1))

        stopButton = Button(service).apply {
            text = "STOP"
            isAllCaps = false
            textSize = 11f
            setTextColor(WHITE)
            background = roundedBackground(STOP_RED, STOP_RED, dp(9).toFloat())
            setPadding(dp(7), 0, dp(7), 0)
            setOnClickListener { onStop() }
        }
        actions.addView(stopButton, LinearLayout.LayoutParams(0, dp(44), 0.72f))
        root.addView(actions)

        val type = if (Build.VERSION.SDK_INT >= 22) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dp(286),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(92)
        }

        try {
            windowManager.addView(root, params)
            panel = root
        } catch (_: Exception) {
            panel = null
        }
    }

    fun remove() {
        val current = panel ?: return
        try {
            windowManager.removeView(current)
        } catch (_: Exception) {
            // The Android window may already have been removed by the system.
        }
        panel = null
    }

    private fun roundedBackground(color: Int, stroke: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val BG = 0xFF111114.toInt()
        const val CRIMSON = 0xFF950606.toInt()
        const val CRIMSON_LIGHT = 0xFFFF5555.toInt()
        const val STOP_RED = 0xFF6E0808.toInt()
        const val WHITE = Color.WHITE
    }
}

package com.example.friendadder.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.example.friendadder.R
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.engine.BotController

/**
 * Petit panneau Démarrer/Stop déplaçable, affiché PAR-DESSUS Facebook.
 * Utilise TYPE_ACCESSIBILITY_OVERLAY : aucune permission « affichage par-dessus » n'est requise.
 * Disponible à partir d'Android 5.1 (API 22) ; sur Android 5.0, utiliser l'app ou la touche volume bas.
 */
class FloatingControlPanel(
    private val service: AccessibilityService,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: LinearLayout? = null
    private var statsView: TextView? = null
    private var toggleView: TextView? = null
    private var running = false

    fun show() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 || container != null) return
        addOverlay()
    }

    fun remove() {
        container?.let { runCatching { windowManager.removeView(it) } }
        container = null
    }

    fun render(status: BotStatus) {
        running = status.state == BotState.RUNNING
        statsView?.text = service.getString(R.string.panel_stats, status.adds, status.failedClicks)
        toggleView?.apply {
            setText(if (running) R.string.panel_stop else R.string.panel_start)
            background = rounded(if (running) COLOR_STOP else COLOR_START, dp(18))
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun addOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(140)
        }

        val stats = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnTouchListener(DragListener(params)) // la zone de texte sert de poignée
        }
        val toggle = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { if (running) onStop() else onStart() }
        }
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(COLOR_PANEL, dp(24))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(stats)
            addView(toggle)
        }

        windowManager.addView(row, params)
        container = row
        statsView = stats
        toggleView = toggle
        render(BotController.currentStatus())
    }

    private inner class DragListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    container?.let { windowManager.updateViewLayout(it, params) }
                }
            }
            return true
        }
    }

    private fun rounded(color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        val COLOR_PANEL = 0xCC202124.toInt()
        val COLOR_START = 0xFF2E7D32.toInt()
        val COLOR_STOP = 0xFFC62828.toInt()
    }
}

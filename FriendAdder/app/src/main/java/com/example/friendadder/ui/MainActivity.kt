package com.example.friendadder.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.friendadder.R
import com.example.friendadder.core.BotConfig
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.core.Pause
import com.example.friendadder.core.StopReason
import com.example.friendadder.engine.BotController

class MainActivity : AppCompatActivity() {

    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusHint: TextView
    private lateinit var statAdds: TextView
    private lateinit var statFailed: TextView
    private lateinit var statDialogs: TextView
    private lateinit var statScrolls: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var pulse: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusHint = findViewById(R.id.statusHint)
        statAdds = findViewById(R.id.statAdds)
        statFailed = findViewById(R.id.statFailed)
        statDialogs = findViewById(R.id.statDialogs)
        statScrolls = findViewById(R.id.statScrolls)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener { openAccessibilitySettings() }
        startButton.setOnClickListener { startBot() }
        stopButton.setOnClickListener { BotController.stop() }
    }

    override fun onStart() {
        super.onStart()
        BotController.listener = ::render
    }

    override fun onStop() {
        BotController.listener = null
        pulse?.cancel()
        pulse = null
        super.onStop()
    }

    private fun startBot() {
        if (!BotController.isServiceConnected) {
            Toast.makeText(this, R.string.enable_service_first, Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        val launchIntent = BotConfig().facebookPackages
            .firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent == null) {
            Toast.makeText(this, R.string.facebook_not_found, Toast.LENGTH_LONG).show()
            return
        }
        BotController.start()
        startActivity(launchIntent)
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    // ------------------------------------------------------------------ rendu

    private fun render(status: BotStatus) {
        val text = statusText(status)
        statusTitle.setText(text.title)
        statusHint.setText(text.hint)

        statAdds.text = status.adds.toString()
        statFailed.text = status.failedClicks.toString()
        statDialogs.text = status.dialogs.toString()
        statScrolls.text = status.scrolls.toString()

        val running = status.state == BotState.RUNNING
        startButton.isEnabled = !running
        startButton.alpha = if (running) 0.35f else 1f
        stopButton.isEnabled = running
        stopButton.alpha = if (running) 1f else 0.35f

        renderDot(status)
    }

    /** Point d'état : rouge qui pulse = travaille ; gris qui pulse lentement = en pause. */
    private fun renderDot(status: BotStatus) {
        val running = status.state == BotState.RUNNING
        val alarming = status.state == BotState.STOPPED &&
            (status.stopReason == StopReason.TOO_MANY_FAILURES || status.stopReason == StopReason.ADD_LIMIT_REACHED)

        @ColorRes val color = when {
            running && status.pause == Pause.NONE -> R.color.red
            running -> R.color.text_secondary
            alarming -> R.color.red
            else -> R.color.text_tertiary
        }
        (statusDot.background.mutate() as GradientDrawable).setColor(ContextCompat.getColor(this, color))

        pulse?.cancel()
        statusDot.alpha = 1f
        pulse = if (running) {
            ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0.2f).apply {
                duration = if (status.pause == Pause.NONE) 650L else 1_400L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else null
    }

    private class StatusText(@StringRes val title: Int, @StringRes val hint: Int)

    private fun statusText(s: BotStatus): StatusText = when (s.state) {
        BotState.DISCONNECTED -> StatusText(R.string.status_disconnected, R.string.hint_disconnected)
        BotState.IDLE -> StatusText(R.string.status_idle, R.string.hint_idle)
        BotState.RUNNING -> when (s.pause) {
            Pause.NONE -> StatusText(R.string.status_running, R.string.hint_running)
            Pause.WAITING_FACEBOOK -> StatusText(R.string.status_paused, R.string.hint_waiting_facebook)
            Pause.WRONG_SCREEN -> StatusText(R.string.status_paused, R.string.hint_wrong_screen)
        }
        BotState.STOPPED -> when (s.stopReason) {
            StopReason.ADD_LIMIT_REACHED -> StatusText(R.string.status_limit, R.string.hint_limit)
            StopReason.TOO_MANY_FAILURES -> StatusText(R.string.status_safety, R.string.hint_safety)
            StopReason.END_OF_LIST -> StatusText(R.string.status_done, R.string.hint_done)
            StopReason.USER, null -> StatusText(R.string.status_stopped, R.string.hint_stopped)
        }
    }
}

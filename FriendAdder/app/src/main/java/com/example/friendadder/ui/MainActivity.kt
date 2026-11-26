package com.example.friendadder.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.example.friendadder.R
import com.example.friendadder.core.BotConfig
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.core.StopReason
import com.example.friendadder.engine.BotController

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
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

    private fun render(status: BotStatus) {
        statusText.setText(statusMessage(status))
        statsText.text = getString(R.string.stats, status.adds, status.dialogs, status.scrolls)
        startButton.isEnabled = status.state != BotState.RUNNING
        stopButton.isEnabled = status.state == BotState.RUNNING
    }

    @StringRes
    private fun statusMessage(s: BotStatus): Int = when (s.state) {
        BotState.DISCONNECTED -> R.string.status_disconnected
        BotState.IDLE -> R.string.status_idle
        BotState.RUNNING -> when {
            s.waitingForFacebook -> R.string.status_waiting_facebook
            s.awaitingConfirmation -> R.string.status_waiting_confirmation
            else -> R.string.status_running
        }
        BotState.STOPPED -> when (s.stopReason) {
            StopReason.ADD_LIMIT_REACHED -> R.string.stop_add_limit
            StopReason.TOO_MANY_FAILURES -> R.string.stop_failures
            StopReason.END_OF_LIST -> R.string.stop_end_of_list
            StopReason.USER, null -> R.string.stop_user
        }
    }
}

package com.example.friendadder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.example.friendadder.core.BotConfig
import com.example.friendadder.engine.AutomationEngine
import com.example.friendadder.engine.BotController
import com.example.friendadder.engine.ScreenGateway

class FriendAdderService : AccessibilityService(), ScreenGateway {

    private val config = BotConfig()
    private var engine: AutomationEngine? = null
    private var floatingPanel: FloatingControlPanel? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        val automation = AutomationEngine(gateway = this, config = config)
        engine = automation

        floatingPanel = FloatingControlPanel(
            service = this,
            onConfirm = { automation.confirmPendingAdd() },
            onStop = { automation.stop() }
        )

        BotController.floatingListener = { status -> floatingPanel?.render(status) }
        BotController.attach(automation)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName in config.facebookPackages) {
            engine?.onScreenChanged()
        }
    }

    override fun onInterrupt() {
        engine?.stop()
    }

    /**
     * Volume + confirme une seule demande détectée.
     * Volume - reste un arrêt d'urgence physique.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val current = engine ?: return false
        if (!current.isRunning) return false

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) current.stop()
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && current.status.awaitingConfirmation) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                current.confirmPendingAdd()
            }
            return true
        }
        return false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        engine?.stop()
        BotController.floatingListener = null
        BotController.detach()
        floatingPanel?.remove()
        floatingPanel = null
        engine = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        floatingPanel?.remove()
        floatingPanel = null
        super.onDestroy()
    }

    // -------------------------------------------------------- ScreenGateway

    override fun activeRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    override fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops <= MAX_ANCESTOR_HOPS) {
            if (current.isClickable && current.isEnabled &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) return true
            current = current.parent
            hops++
        }
        return tapCenterOf(node)
    }

    override fun scrollForward(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

    override fun swipeUp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, m.heightPixels * 0.75f)
            lineTo(x, m.heightPixels * 0.35f)
        }
        return dispatch(path, 350L)
    }

    override fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    // --------------------------------------------------------------- gestes

    private fun tapCenterOf(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatch(path, 50L)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatch(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private companion object {
        const val MAX_ANCESTOR_HOPS = 8
    }
}

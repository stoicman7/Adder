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
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.example.friendadder.core.BotConfig
import com.example.friendadder.engine.AutomationEngine
import com.example.friendadder.engine.BotController
import com.example.friendadder.engine.ScreenGateway

class FriendAdderService : AccessibilityService(), ScreenGateway {

    private val config = BotConfig()
    private var engine: AutomationEngine? = null
    private var panel: FloatingControlPanel? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val newEngine = AutomationEngine(gateway = this, config = config)
        engine = newEngine
        BotController.attach(newEngine)

        panel = FloatingControlPanel(
            service = this,
            onStart = { BotController.start() },
            onStop = { BotController.stop() }
        ).also {
            BotController.floatingListener = it::render
            it.show()
        }
    }

    // Le moteur interroge l'écran lui-même : les événements ne sont pas nécessaires.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Volume bas = arrêt d'urgence, même quand Facebook est au premier plan. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val running = engine?.isRunning == true
        if (running && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) engine?.stop()
            return true
        }
        return false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        engine?.stop()
        panel?.remove()
        panel = null
        BotController.floatingListener = null
        BotController.detach()
        engine = null
    }

    // -------------------------------------------------------- ScreenGateway

    /**
     * Le panneau flottant est lui aussi une fenêtre : rootInActiveWindow peut donc désigner
     * notre propre overlay. On cherche explicitement la fenêtre Facebook.
     */
    override fun activeRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && isFacebook(active)) return active
        val facebookRoot = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .firstNotNullOfOrNull { window -> window.root?.takeIf { isFacebook(it) } }
        return facebookRoot ?: active
    }

    private fun isFacebook(node: AccessibilityNodeInfo): Boolean {
        val pkg = node.packageName?.toString() ?: return false
        return pkg in config.facebookPackages
    }

    override fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops <= MAX_ANCESTOR_HOPS) {
            // Facebook n'active pas toujours isClickable : on regarde aussi la liste des actions.
            val canClick = current.isClickable ||
                current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            if (canClick && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            hops++
        }
        // Dernier recours : action directe sur le nœud lui-même.
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun tap(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatch(path, 60L)
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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatch(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private companion object {
        const val MAX_ANCESTOR_HOPS = 6
    }
}

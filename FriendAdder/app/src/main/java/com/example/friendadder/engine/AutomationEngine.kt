package com.example.friendadder.engine

import android.os.Handler
import android.os.Looper
import com.example.friendadder.core.BotConfig
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.core.StopReason
import com.example.friendadder.core.TextMatcher

/**
 * Navigation automatique avec une confirmation explicite pour CHAQUE demande.
 * La détection du libellé et du conteneur cliquable est faite par ScreenScanner.
 */
class AutomationEngine(
    private val gateway: ScreenGateway,
    private val config: BotConfig = BotConfig(),
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val scanner = ScreenScanner(TextMatcher(config.labels))
    private val tick = Runnable { onTick() }

    /** Boutons déjà confirmés depuis le dernier défilement. */
    private val clickedKeys = HashSet<String>()
    private var pendingAdd = false
    private var pendingTarget: ButtonRef? = null
    private var consecutiveFailures = 0
    private var staleScrolls = 0
    private var lastScrollSignature: Int? = null

    var status = BotStatus()
        private set
    var onStatusChanged: ((BotStatus) -> Unit)? = null

    val isRunning: Boolean get() = status.state == BotState.RUNNING

    fun start() {
        if (isRunning) return
        clickedKeys.clear()
        pendingAdd = false
        pendingTarget = null
        consecutiveFailures = 0
        staleScrolls = 0
        lastScrollSignature = null
        publish(BotStatus(state = BotState.RUNNING))
        scheduleIn(config.startDelayMs)
    }

    fun stop(reason: StopReason = StopReason.USER) {
        if (!isRunning) return
        handler.removeCallbacks(tick)
        pendingTarget = null
        publish(
            status.copy(
                state = BotState.STOPPED,
                stopReason = reason,
                waitingForFacebook = false,
                awaitingConfirmation = false,
                detectedButtons = 0,
                detectedLabel = null
            )
        )
    }

    /** Appelé par le bouton flottant CONFIRM ONE ou par Volume +. */
    fun confirmPendingAdd(): Boolean {
        if (!isRunning || !status.awaitingConfirmation) return false
        val target = pendingTarget ?: return false
        val root = gateway.activeRoot() ?: return false
        if (root.packageName?.toString() !in config.facebookPackages) return false

        val freshButtons = scanner.scan(root).addButtons
        val freshTarget = freshButtons.firstOrNull { it.key == target.key }
            ?: freshButtons.firstOrNull {
                it.label.equals(target.label, ignoreCase = true) &&
                    kotlin.math.abs(it.top - target.top) <= 12
            }
            ?: run {
                pendingTarget = null
                publish(status.copy(
                    awaitingConfirmation = false,
                    detectedButtons = freshButtons.size,
                    detectedLabel = null
                ))
                scheduleIn(config.idleDelayMs)
                return false
            }

        if (!gateway.click(freshTarget.node)) return false

        clickedKeys += freshTarget.key
        pendingTarget = null
        pendingAdd = true
        publish(status.copy(
            awaitingConfirmation = false,
            detectedButtons = 0,
            detectedLabel = null,
            adds = status.adds + 1
        ))
        scheduleIn(config.afterAddDelayMs)
        return true
    }

    /** Accélère la lecture après une modification de l'interface Facebook. */
    fun onScreenChanged() {
        if (!isRunning || status.awaitingConfirmation) return
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, SCREEN_CHANGE_DEBOUNCE_MS)
    }

    // ---------------------------------------------------------------- tick

    private fun onTick() {
        if (!isRunning || status.awaitingConfirmation) return

        val root = gateway.activeRoot()
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || pkg !in config.facebookPackages) {
            if (!status.waitingForFacebook) {
                publish(status.copy(
                    waitingForFacebook = true,
                    detectedButtons = 0,
                    detectedLabel = null
                ))
            }
            scheduleIn(config.idleDelayMs)
            return
        }

        if (status.waitingForFacebook) publish(status.copy(waitingForFacebook = false))

        val screen = scanner.scan(root)
        if (screen.errorDialogVisible) {
            handleErrorDialog(screen)
            return
        }

        resolvePendingAdd(success = true)
        if (screen.addButtons.isNotEmpty()) {
            handleAddButtons(screen)
        } else {
            scroll(screen)
        }
    }

    private fun handleErrorDialog(screen: ScreenSnapshot) {
        resolvePendingAdd(success = false)
        val ok = screen.okButtons.firstOrNull()
        if (ok == null || !gateway.click(ok)) gateway.pressBack()
        publish(status.copy(
            dialogs = status.dialogs + 1,
            detectedButtons = screen.addButtons.size,
            detectedLabel = null,
            awaitingConfirmation = false
        ))

        if (consecutiveFailures >= config.maxConsecutiveFailures) {
            stop(StopReason.TOO_MANY_FAILURES)
            return
        }
        scheduleIn(config.afterDialogDelayMs)
    }

    private fun handleAddButtons(screen: ScreenSnapshot) {
        if (status.adds >= config.maxAddsPerSession) {
            stop(StopReason.ADD_LIMIT_REACHED)
            return
        }

        val target = screen.addButtons.firstOrNull { it.key !in clickedKeys }
        if (target == null) {
            scroll(screen)
            return
        }

        // Pause : le bouton flottant indique le libellé détecté et attend CONFIRM ONE.
        pendingTarget = target
        publish(status.copy(
            awaitingConfirmation = true,
            detectedButtons = screen.addButtons.size,
            detectedLabel = target.label
        ))
    }

    private fun scroll(screen: ScreenSnapshot) {
        staleScrolls = if (screen.signature == lastScrollSignature) staleScrolls + 1 else 0
        lastScrollSignature = screen.signature
        if (staleScrolls >= config.maxStaleScrolls) {
            stop(StopReason.END_OF_LIST)
            return
        }

        val container = screen.scrollContainer
        if (container == null || !gateway.scrollForward(container)) gateway.swipeUp()

        clickedKeys.clear()
        publish(status.copy(
            scrolls = status.scrolls + 1,
            detectedButtons = screen.addButtons.size,
            detectedLabel = null,
            awaitingConfirmation = false
        ))
        scheduleIn(config.afterScrollDelayMs)
    }

    // ------------------------------------------------------------- helpers

    private fun resolvePendingAdd(success: Boolean) {
        if (!pendingAdd) return
        pendingAdd = false
        consecutiveFailures = if (success) 0 else consecutiveFailures + 1
    }

    private fun scheduleIn(range: LongRange) = scheduleIn(range.random())

    private fun scheduleIn(delayMs: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    private fun publish(newStatus: BotStatus) {
        status = newStatus
        onStatusChanged?.invoke(newStatus)
    }

    private companion object {
        const val SCREEN_CHANGE_DEBOUNCE_MS = 250L
    }
}

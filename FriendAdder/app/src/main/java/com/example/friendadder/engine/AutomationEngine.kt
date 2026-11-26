package com.example.friendadder.engine

import android.os.Handler
import android.os.Looper
import com.example.friendadder.core.BotConfig
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.core.StopReason
import com.example.friendadder.core.TextMatcher

/**
 * Navigation automatique, mais aucun ajout n'est envoyé sans une confirmation
 * physique de l'utilisateur (touche Volume +).
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
                awaitingConfirmation = false
            )
        )
    }

    /**
     * Envoie au maximum UNE demande, uniquement à la suite d'une pression
     * volontaire sur Volume +. Le bouton est rescanné avant le clic.
     */
    fun confirmPendingAdd(): Boolean {
        if (!isRunning || !status.awaitingConfirmation) return false
        val target = pendingTarget ?: return false
        val root = gateway.activeRoot() ?: return false
        val packageName = root.packageName?.toString()
        if (packageName !in config.facebookPackages) return false

        val freshTarget = scanner.scan(root).addButtons.firstOrNull { it.key == target.key }
            ?: run {
                pendingTarget = null
                publish(status.copy(awaitingConfirmation = false))
                scheduleIn(config.idleDelayMs)
                return false
            }

        if (!gateway.click(freshTarget.node)) return false

        clickedKeys += freshTarget.key
        pendingTarget = null
        pendingAdd = true
        publish(status.copy(awaitingConfirmation = false, adds = status.adds + 1))
        scheduleIn(config.afterAddDelayMs)
        return true
    }

    // ---------------------------------------------------------------- tick

    private fun onTick() {
        if (!isRunning || status.awaitingConfirmation) return

        val root = gateway.activeRoot()
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || pkg !in config.facebookPackages) {
            if (!status.waitingForFacebook) publish(status.copy(waitingForFacebook = true))
            scheduleIn(config.idleDelayMs)
            return
        }
        if (status.waitingForFacebook) publish(status.copy(waitingForFacebook = false))

        val screen = scanner.scan(root)
        if (screen.errorDialogVisible) {
            handleErrorDialog(screen)
            return
        }

        // Le clic précédent n'a pas été suivi d'une fenêtre d'erreur.
        resolvePendingAdd(success = true)
        if (screen.addButtons.isNotEmpty()) handleAddButtons(screen) else scroll(screen)
    }

    private fun handleErrorDialog(screen: ScreenSnapshot) {
        resolvePendingAdd(success = false)
        val ok = screen.okButtons.firstOrNull()
        if (ok == null || !gateway.click(ok)) gateway.pressBack()
        publish(status.copy(dialogs = status.dialogs + 1))

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

        // Pause ici : aucun clic tant que l'utilisateur n'appuie pas sur Volume +.
        pendingTarget = target
        publish(status.copy(awaitingConfirmation = true))
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
        publish(status.copy(scrolls = status.scrolls + 1))
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
}

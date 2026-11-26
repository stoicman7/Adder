package com.example.friendadder.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.friendadder.core.BotConfig
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.core.StopReason
import com.example.friendadder.core.TextMatcher

/**
 * Machine à états 100 % automatique (aucune confirmation manuelle).
 * À chaque « tick » (délai aléatoire) elle lit l'écran puis fait UNE action :
 *   1. fenêtre d'erreur visible      -> clic sur OK
 *   2. bouton « Ajouter » visible    -> clic dessus (puis vérification au tick suivant)
 *   3. sinon                         -> défilement
 * Tout s'exécute sur le thread principal.
 */
class AutomationEngine(
    private val gateway: ScreenGateway,
    private val config: BotConfig = BotConfig(),
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val scanner = ScreenScanner(TextMatcher(config.labels))
    private val tick = Runnable { onTick() }

    /** Boutons déjà traités depuis le dernier défilement (évite un double clic = annulation). */
    private val clickedKeys = HashSet<String>()
    private var lastClickedKey: String? = null
    private var pendingAdd = false
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
        lastClickedKey = null
        pendingAdd = false
        consecutiveFailures = 0
        staleScrolls = 0
        lastScrollSignature = null
        publish(BotStatus(state = BotState.RUNNING))
        scheduleIn(config.startDelayMs)
    }

    fun stop(reason: StopReason = StopReason.USER) {
        if (!isRunning) return
        handler.removeCallbacks(tick)
        publish(status.copy(state = BotState.STOPPED, stopReason = reason, waitingForFacebook = false))
    }

    // ---------------------------------------------------------------- tick

    private fun onTick() {
        if (!isRunning) return

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
        // Pas de fenêtre d'erreur : le clic précédent (s'il y en a un) a réussi.
        resolvePendingAdd(success = true)
        if (screen.addButtons.isNotEmpty()) handleAddButtons(screen) else scroll(screen)
    }

    private fun handleErrorDialog(screen: ScreenSnapshot) {
        lastClickedKey = null
        resolvePendingAdd(success = false)
        val ok = screen.okButtons.firstOrNull()
        val closed = ok != null && (gateway.click(ok) || gateway.tap(ok))
        if (!closed) gateway.pressBack()
        Log.d(TAG, "Fenêtre d'erreur détectée, fermée=$closed")
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
        verifyLastClick(screen)

        val target = screen.addButtons.firstOrNull { it.key !in clickedKeys }
        if (target == null) {
            scroll(screen) // tous les boutons visibles sont déjà traités
            return
        }
        clickedKeys += target.key

        val clicked = gateway.click(target.node) || gateway.tap(target.node)
        Log.d(TAG, "Clic sur « Ajouter » (${target.key}) -> $clicked")
        if (clicked) {
            lastClickedKey = target.key
            pendingAdd = true
            publish(status.copy(adds = status.adds + 1))
            scheduleIn(config.afterAddDelayMs)
        } else {
            publish(status.copy(failedClicks = status.failedClicks + 1))
            scheduleIn(config.afterDialogDelayMs) // on passe au bouton suivant
        }
    }

    /**
     * Si le bouton cliqué affiche toujours « Ajouter », le clic n'a eu aucun effet
     * (ACTION_CLICK « accepté » mais ignoré par Facebook) : second essai par geste.
     */
    private fun verifyLastClick(screen: ScreenSnapshot) {
        val key = lastClickedKey ?: return
        lastClickedKey = null
        val stillThere = screen.addButtons.firstOrNull { it.key == key } ?: return
        val retried = gateway.tap(stillThere.node)
        Log.d(TAG, "Le bouton est toujours là : nouvel essai par geste -> $retried")
        publish(status.copy(failedClicks = status.failedClicks + 1))
    }

    private fun scroll(screen: ScreenSnapshot) {
        staleScrolls = if (screen.signature == lastScrollSignature) staleScrolls + 1 else 0
        lastScrollSignature = screen.signature
        if (staleScrolls >= config.maxStaleScrolls) {
            stop(StopReason.END_OF_LIST)
            return
        }
        val container = screen.scrollContainer
        val viaAction = { container != null && gateway.scrollForward(container) }
        // Si le défilement « sémantique » n'a rien changé, on bascule sur un vrai balayage.
        val scrolled =
            if (staleScrolls == 0) viaAction() || gateway.swipeUp()
            else gateway.swipeUp() || viaAction()
        Log.d(TAG, "Défilement -> $scrolled (stale=$staleScrolls)")

        clickedKeys.clear()
        lastClickedKey = null
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

    private companion object {
        const val TAG = "FriendAdder"
    }
}

package com.example.friendadder.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.friendadder.core.BotConfig
import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus
import com.example.friendadder.core.Pause
import com.example.friendadder.core.StopReason
import com.example.friendadder.core.TextMatcher

/**
 * Machine à états 100 % automatique, limitée à l'écran choisi par l'utilisateur
 * (liste des réactions d'un post / des membres d'un groupe).
 *
 * À chaque « tick » (délai aléatoire) elle lit l'écran puis fait UNE action :
 *   1. fenêtre d'erreur visible       -> clic sur OK
 *   2. hors liste cible               -> pause (aucun clic, aucun défilement)
 *   3. bouton « Ajouter » non traité  -> clic dessus (puis vérification au tick suivant)
 *   4. sinon                          -> défilement ; fin de liste = arrêt (jamais de navigation)
 * Tout s'exécute sur le thread principal.
 */
class AutomationEngine(
    private val gateway: ScreenGateway,
    private val config: BotConfig = BotConfig(),
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val scanner = ScreenScanner(TextMatcher(config.labels), config.titleZoneRatio)
    private val tick = Runnable { onTick() }

    /** Personnes déjà traitées pendant la session (succès OU échec) : jamais recliquées. */
    private val handledCards = HashSet<Int>()
    /** Repli quand une ligne n'a pas d'empreinte : emplacements déjà cliqués depuis le dernier défilement. */
    private val clickedPositions = HashSet<String>()

    private var lastClicked: ButtonRef? = null
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
        handledCards.clear()
        clickedPositions.clear()
        lastClicked = null
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
        publish(status.copy(state = BotState.STOPPED, stopReason = reason, pause = Pause.NONE))
    }

    // ---------------------------------------------------------------- tick

    private fun onTick() {
        if (!isRunning) return

        val root = gateway.activeRoot()
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || pkg !in config.facebookPackages) {
            setPause(Pause.WAITING_FACEBOOK)
            scheduleIn(config.idleDelayMs)
            return
        }

        val screen = scanner.scan(root)

        // 1. Fenêtre d'erreur (« Impossible d'envoyer l'invitation »…) : toujours fermée en premier.
        if (screen.errorDialogVisible) {
            setPause(Pause.NONE)
            handleErrorDialog(screen)
            return
        }

        // 2. Hors de la liste choisie : on ne touche à rien.
        if (config.requireTargetScreen && !screen.onTargetScreen) {
            Log.d(TAG, "Écran hors cible. Textes du haut : ${screen.topTexts}")
            setPause(Pause.WRONG_SCREEN)
            scheduleIn(config.idleDelayMs)
            return
        }
        setPause(Pause.NONE)

        // Pas de fenêtre d'erreur : le clic précédent (s'il y en a un) a réussi.
        resolvePendingAdd(success = true)
        if (screen.addButtons.isNotEmpty()) handleAddButtons(screen) else scroll(screen)
    }

    private fun handleErrorDialog(screen: ScreenSnapshot) {
        lastClicked = null
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

        val target = screen.addButtons.firstOrNull { !isHandled(it) }
        if (target == null) {
            scroll(screen) // tout ce qui est visible est déjà traité
            return
        }
        markHandled(target)

        val clicked = gateway.click(target.node) || gateway.tap(target.node)
        Log.d(TAG, "Clic sur « Ajouter » (carte=${target.cardId}) -> $clicked")
        if (clicked) {
            lastClicked = target
            pendingAdd = true
            publish(status.copy(adds = status.adds + 1))
            scheduleIn(config.afterAddDelayMs)
        } else {
            publish(status.copy(failedClicks = status.failedClicks + 1))
            scheduleIn(config.afterDialogDelayMs) // on passe à la personne suivante
        }
    }

    /**
     * Si le bouton cliqué affiche toujours « Ajouter », le clic n'a eu aucun effet
     * (ACTION_CLICK « accepté » mais ignoré par Facebook) : second essai par geste.
     */
    private fun verifyLastClick(screen: ScreenSnapshot) {
        val last = lastClicked ?: return
        lastClicked = null
        val stillThere = screen.addButtons.firstOrNull { sameButton(it, last) } ?: return
        val retried = gateway.tap(stillThere.node)
        Log.d(TAG, "Le bouton est toujours là : nouvel essai par geste -> $retried")
        publish(status.copy(failedClicks = status.failedClicks + 1))
    }

    private fun scroll(screen: ScreenSnapshot) {
        staleScrolls = if (screen.signature == lastScrollSignature) staleScrolls + 1 else 0
        lastScrollSignature = screen.signature
        if (staleScrolls >= config.maxStaleScrolls) {
            // Fin de la liste : on s'arrête. Le bot ne navigue JAMAIS vers un autre post/groupe.
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

        clickedPositions.clear()
        lastClicked = null
        publish(status.copy(scrolls = status.scrolls + 1))
        scheduleIn(config.afterScrollDelayMs)
    }

    // ------------------------------------------------------------- helpers

    private fun isHandled(button: ButtonRef): Boolean =
        if (button.cardId != 0) button.cardId in handledCards else button.positionKey in clickedPositions

    private fun markHandled(button: ButtonRef) {
        if (button.cardId != 0) handledCards += button.cardId else clickedPositions += button.positionKey
    }

    private fun sameButton(a: ButtonRef, b: ButtonRef): Boolean =
        if (a.cardId != 0 && b.cardId != 0) a.cardId == b.cardId else a.positionKey == b.positionKey

    private fun resolvePendingAdd(success: Boolean) {
        if (!pendingAdd) return
        pendingAdd = false
        consecutiveFailures = if (success) 0 else consecutiveFailures + 1
    }

    private fun setPause(pause: Pause) {
        if (status.pause != pause) publish(status.copy(pause = pause))
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

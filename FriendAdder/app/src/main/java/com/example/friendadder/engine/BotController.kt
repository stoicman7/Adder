package com.example.friendadder.engine

import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus

/** Pont entre l'UI / le panneau flottant et le service d'accessibilité (même processus, thread principal). */
object BotController {
    private var engine: AutomationEngine? = null

    var listener: ((BotStatus) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(currentStatus())
        }

    /** Écouteur du panneau flottant (géré par le service). */
    var floatingListener: ((BotStatus) -> Unit)? = null

    val isServiceConnected: Boolean get() = engine != null

    fun attach(engine: AutomationEngine) {
        this.engine = engine
        engine.onStatusChanged = { status ->
            listener?.invoke(status)
            floatingListener?.invoke(status)
        }
        listener?.invoke(engine.status)
    }

    fun detach() {
        engine?.onStatusChanged = null
        engine = null
        listener?.invoke(currentStatus())
    }

    fun start() {
        engine?.start()
    }

    fun stop() {
        engine?.stop()
    }

    fun currentStatus(): BotStatus =
        engine?.status ?: BotStatus(state = BotState.DISCONNECTED)
}

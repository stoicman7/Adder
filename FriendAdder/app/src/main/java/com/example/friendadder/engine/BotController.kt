package com.example.friendadder.engine

import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus

/** Pont entre l'UI et le service d'accessibilité (même processus, thread principal). */
object BotController {
    private var engine: AutomationEngine? = null

    var listener: ((BotStatus) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(currentStatus())
        }

    val isServiceConnected: Boolean get() = engine != null

    fun attach(engine: AutomationEngine) {
        this.engine = engine
        engine.onStatusChanged = { listener?.invoke(it) }
        listener?.invoke(engine.status)
    }

    fun detach() {
        engine?.onStatusChanged = null
        engine = null
        listener?.invoke(currentStatus())
    }

    fun start() = engine?.start()
    fun stop() = engine?.stop()

    fun currentStatus(): BotStatus =
        engine?.status ?: BotStatus(state = BotState.DISCONNECTED)
}

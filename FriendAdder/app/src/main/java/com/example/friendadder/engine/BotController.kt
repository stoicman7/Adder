package com.example.friendadder.engine

import com.example.friendadder.core.BotState
import com.example.friendadder.core.BotStatus

/** Pont entre l'UI principale, le panneau flottant et le service d'accessibilité. */
object BotController {
    private var engine: AutomationEngine? = null

    var listener: ((BotStatus) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(currentStatus())
        }

    var floatingListener: ((BotStatus) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(currentStatus())
        }

    val isServiceConnected: Boolean get() = engine != null

    fun attach(engine: AutomationEngine) {
        this.engine = engine
        engine.onStatusChanged = { status ->
            listener?.invoke(status)
            floatingListener?.invoke(status)
        }
        listener?.invoke(engine.status)
        floatingListener?.invoke(engine.status)
    }

    fun detach() {
        engine?.onStatusChanged = null
        engine = null
        listener?.invoke(currentStatus())
        floatingListener?.invoke(currentStatus())
    }

    fun start() = engine?.start()
    fun stop() = engine?.stop()
    fun confirmPendingAdd(): Boolean = engine?.confirmPendingAdd() == true

    fun currentStatus(): BotStatus =
        engine?.status ?: BotStatus(state = BotState.DISCONNECTED)
}

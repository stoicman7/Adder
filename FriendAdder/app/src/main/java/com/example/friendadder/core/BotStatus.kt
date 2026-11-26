package com.example.friendadder.core

enum class BotState { DISCONNECTED, IDLE, RUNNING, STOPPED }

enum class StopReason { USER, ADD_LIMIT_REACHED, TOO_MANY_FAILURES, END_OF_LIST }

data class BotStatus(
    val state: BotState = BotState.IDLE,
    val stopReason: StopReason? = null,
    val waitingForFacebook: Boolean = false,
    val adds: Int = 0,
    val dialogs: Int = 0,
    val scrolls: Int = 0
)

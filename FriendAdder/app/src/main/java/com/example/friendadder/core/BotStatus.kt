package com.example.friendadder.core

enum class BotState { DISCONNECTED, IDLE, RUNNING, STOPPED }

enum class StopReason { USER, ADD_LIMIT_REACHED, TOO_MANY_FAILURES, END_OF_LIST }

/** Raison pour laquelle un bot « en cours » n'agit pas pour l'instant. */
enum class Pause { NONE, WAITING_FACEBOOK, WRONG_SCREEN }

data class BotStatus(
    val state: BotState = BotState.IDLE,
    val stopReason: StopReason? = null,
    val pause: Pause = Pause.NONE,
    val adds: Int = 0,
    val failedClicks: Int = 0,
    val dialogs: Int = 0,
    val scrolls: Int = 0
)

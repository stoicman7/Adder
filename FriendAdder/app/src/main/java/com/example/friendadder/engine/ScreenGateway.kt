package com.example.friendadder.engine

import android.view.accessibility.AccessibilityNodeInfo

/** Abstraction des actions système : l'engine ne dépend pas d'AccessibilityService. */
interface ScreenGateway {
    fun activeRoot(): AccessibilityNodeInfo?
    fun click(node: AccessibilityNodeInfo): Boolean
    fun scrollForward(node: AccessibilityNodeInfo): Boolean
    fun swipeUp(): Boolean
    fun pressBack(): Boolean
}

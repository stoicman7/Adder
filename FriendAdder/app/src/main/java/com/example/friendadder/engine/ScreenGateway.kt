package com.example.friendadder.engine

import android.view.accessibility.AccessibilityNodeInfo

/** Abstraction des actions système : l'engine ne dépend pas d'AccessibilityService. */
interface ScreenGateway {
    /** Racine de la fenêtre Facebook (ou, à défaut, de la fenêtre active). */
    fun activeRoot(): AccessibilityNodeInfo?

    /** Clic « sémantique » (ACTION_CLICK) sur le nœud ou l'un de ses parents. Marche dès Android 5. */
    fun click(node: AccessibilityNodeInfo): Boolean

    /** Tap par geste au centre du nœud. Android 7+ uniquement. */
    fun tap(node: AccessibilityNodeInfo): Boolean

    fun scrollForward(node: AccessibilityNodeInfo): Boolean
    fun swipeUp(): Boolean
    fun pressBack(): Boolean
}

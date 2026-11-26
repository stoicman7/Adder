package com.example.friendadder.engine

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.friendadder.core.TextMatcher

data class ButtonRef(
    val node: AccessibilityNodeInfo,
    val key: String,
    val top: Int,
    val left: Int,
    val label: String
)

class ScreenSnapshot(
    val addButtons: List<ButtonRef>,
    val okButtons: List<AccessibilityNodeInfo>,
    val errorDialogVisible: Boolean,
    val scrollContainer: AccessibilityNodeInfo?,
    /** Empreinte du texte visible : sert à détecter qu'un défilement n'a rien changé. */
    val signature: Int
)

/**
 * Parcourt l'arbre d'accessibilité.
 *
 * Facebook place fréquemment le texte « Ajouter » dans un TextView enfant, tandis
 * que le clic est porté par son parent. On remonte donc jusqu'au conteneur cliquable
 * et on utilise ses coordonnées, au lieu d'exiger que le TextView soit lui-même
 * cliquable.
 */
class ScreenScanner(private val matcher: TextMatcher) {

    private class Accumulator {
        val adds = ArrayList<ButtonRef>()
        val oks = ArrayList<AccessibilityNodeInfo>()
        var errorTextSeen = false
        var scrollContainer: AccessibilityNodeInfo? = null
        var scrollArea = 0L
        var signature = 17
    }

    fun scan(root: AccessibilityNodeInfo): ScreenSnapshot {
        val acc = Accumulator()
        visit(root, 0, acc)

        val adds = acc.adds
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.top }, { it.left }))

        return ScreenSnapshot(
            addButtons = adds,
            okButtons = acc.oks.distinctBy { node ->
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                bounds.flattenToString()
            },
            errorDialogVisible = acc.errorTextSeen && acc.oks.isNotEmpty(),
            scrollContainer = acc.scrollContainer,
            signature = acc.signature
        )
    }

    private fun visit(node: AccessibilityNodeInfo, depth: Int, acc: Accumulator) {
        if (depth > MAX_DEPTH) return

        // Certains parents ne sont pas marqués visibles alors que leurs enfants le sont.
        if (node.isVisibleToUser) {
            val bounds = Rect().also { node.getBoundsInScreen(it) }

            inspect(node, node.text, acc)
            inspect(node, node.contentDescription, acc)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                inspect(node, node.hintText, acc)
            }

            if (node.isScrollable) {
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (area > acc.scrollArea) {
                    acc.scrollArea = area
                    acc.scrollContainer = node
                }
            }
        }

        // Ne pas interrompre la visite uniquement parce qu'un parent est invisible.
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { visit(it, depth + 1, acc) }
        }
    }

    private fun inspect(
        node: AccessibilityNodeInfo,
        raw: CharSequence?,
        acc: Accumulator
    ) {
        val text = TextMatcher.normalize(raw)
        if (text.isEmpty()) return

        acc.signature = 31 * acc.signature + text.hashCode()

        when {
            matcher.isAddButton(text) -> {
                val target = findClickableAncestor(node)
                if (target.isEnabled) {
                    val bounds = Rect().also { target.getBoundsInScreen(it) }
                    if (!bounds.isEmpty) {
                        acc.adds += ButtonRef(
                            node = target,
                            key = bounds.flattenToString(),
                            top = bounds.top,
                            left = bounds.left,
                            label = raw?.toString()?.trim().orEmpty().ifBlank { "Ajouter" }
                        )
                    }
                }
            }
            matcher.isOkButton(text) && node.isEnabled -> acc.oks += node
            matcher.isErrorText(text) -> acc.errorTextSeen = true
        }
    }

    private fun findClickableAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = start
        var best = start
        var hops = 0

        while (hops <= MAX_CLICKABLE_ANCESTOR_HOPS) {
            if (current.isEnabled) best = current
            if (current.isClickable && current.isEnabled) return current

            val parent = current.parent ?: break
            current = parent
            hops++
        }

        // Le service garde un fallback par geste tactile si aucun parent cliquable n'existe.
        return best
    }

    private companion object {
        const val MAX_DEPTH = 60
        const val MAX_CLICKABLE_ANCESTOR_HOPS = 8
    }
}

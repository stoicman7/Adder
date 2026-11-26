package com.example.friendadder.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.friendadder.core.TextMatcher

data class ButtonRef(val node: AccessibilityNodeInfo, val key: String, val top: Int, val left: Int)

class ScreenSnapshot(
    val addButtons: List<ButtonRef>,
    val okButtons: List<AccessibilityNodeInfo>,
    val errorDialogVisible: Boolean,
    val scrollContainer: AccessibilityNodeInfo?,
    /** Empreinte du texte visible : sert à détecter qu'un défilement n'a rien changé. */
    val signature: Int
)

/** Parcourt l'arbre d'accessibilité en UNE passe et en extrait ce qui intéresse le bot. */
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
            okButtons = acc.oks,
            // Une simple phrase dans un post ne suffit pas : il faut aussi un bouton OK.
            errorDialogVisible = acc.errorTextSeen && acc.oks.isNotEmpty(),
            scrollContainer = acc.scrollContainer,
            signature = acc.signature
        )
    }

    private fun visit(node: AccessibilityNodeInfo, depth: Int, acc: Accumulator) {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        inspect(node, node.text, bounds, acc)
        inspect(node, node.contentDescription, bounds, acc)

        if (node.isScrollable) {
            val area = bounds.width().toLong() * bounds.height().toLong()
            if (area > acc.scrollArea) {
                acc.scrollArea = area
                acc.scrollContainer = node
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { visit(it, depth + 1, acc) }
        }
    }

    private fun inspect(node: AccessibilityNodeInfo, raw: CharSequence?, bounds: Rect, acc: Accumulator) {
        val text = TextMatcher.normalize(raw)
        if (text.isEmpty()) return
        acc.signature = 31 * acc.signature + text.hashCode()
        when {
            matcher.isAddButton(text) && node.isEnabled ->
                acc.adds += ButtonRef(node, bounds.flattenToString(), bounds.top, bounds.left)
            matcher.isOkButton(text) -> acc.oks += node
            matcher.isErrorText(text) -> acc.errorTextSeen = true
        }
    }

    private companion object {
        const val MAX_DEPTH = 60
    }
}

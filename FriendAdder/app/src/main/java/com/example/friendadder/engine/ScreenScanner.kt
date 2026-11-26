package com.example.friendadder.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.friendadder.core.TextMatcher

/** [key] = position + empreinte de la « carte » (nom de la personne) : identifie CE bouton précis. */
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
            matcher.isAddButton(text) && node.isEnabled -> {
                val key = "${bounds.flattenToString()}#${cardFingerprint(node)}"
                acc.adds += ButtonRef(node, key, bounds.top, bounds.left)
            }
            matcher.isOkButton(text) -> acc.oks += node
            matcher.isErrorText(text) -> acc.errorTextSeen = true
        }
    }

    /**
     * Remonte de quelques niveaux depuis le bouton jusqu'à trouver au moins deux textes
     * (typiquement le nom + « Supprimer ») : sert à distinguer deux cartes qui, après
     * suppression de la première, se retrouvent à la même position.
     */
    private fun cardFingerprint(button: AccessibilityNodeInfo): Int {
        var ancestor = button.parent
        var hops = 0
        var best = 0
        while (ancestor != null && hops < MAX_CARD_HOPS) {
            val texts = LinkedHashSet<String>()
            collectTexts(ancestor, 0, texts)
            texts.removeAll { matcher.isAddButton(it) }
            if (texts.isNotEmpty()) best = texts.hashCode()
            if (texts.size >= 2) return best
            ancestor = ancestor.parent
            hops++
        }
        return best
    }

    private fun collectTexts(node: AccessibilityNodeInfo, depth: Int, out: MutableSet<String>) {
        if (depth > 4 || out.size >= 12) return
        TextMatcher.normalize(node.text).takeIf { it.isNotEmpty() }?.let { out.add(it) }
        TextMatcher.normalize(node.contentDescription).takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, depth + 1, out) }
        }
    }

    private companion object {
        const val MAX_DEPTH = 60
        const val MAX_CARD_HOPS = 4
    }
}

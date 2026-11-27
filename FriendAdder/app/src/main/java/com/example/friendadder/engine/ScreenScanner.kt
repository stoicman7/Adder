package com.example.friendadder.engine

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.friendadder.core.TextMatcher

/**
 * Un bouton « Ajouter ».
 * [cardId] identifie la PERSONNE (empreinte de sa ligne, indépendante du défilement) ; 0 si inconnue.
 * [positionKey] n'identifie que l'emplacement à l'écran.
 */
class ButtonRef(
    val node: AccessibilityNodeInfo,
    val bounds: Rect,
    val cardId: Int
) {
    val positionKey: String = bounds.flattenToString()
    val top: Int get() = bounds.top
    val left: Int get() = bounds.left
}

class ScreenSnapshot(
    val addButtons: List<ButtonRef>,
    val okButtons: List<AccessibilityNodeInfo>,
    val errorDialogVisible: Boolean,
    /** true si le titre de la page est « Réactions » / « Membres » (écrans autorisés). */
    val onTargetScreen: Boolean,
    val topTexts: List<String>,
    val scrollContainer: AccessibilityNodeInfo?,
    /** Empreinte du texte visible : sert à détecter qu'un défilement n'a rien changé. */
    val signature: Int
)

/** Parcourt l'arbre d'accessibilité en UNE passe et en extrait ce qui intéresse le bot. */
class ScreenScanner(
    private val matcher: TextMatcher,
    private val titleZoneRatio: Float = 0.18f
) {

    private class Accumulator(val titleZoneBottom: Int) {
        val adds = ArrayList<ButtonRef>()
        val oks = ArrayList<AccessibilityNodeInfo>()
        val topTexts = ArrayList<String>()
        var targetTitleSeen = false
        var errorTextSeen = false
        var scrollContainer: AccessibilityNodeInfo? = null
        var scrollArea = 0L
        var signature = 17
    }

    fun scan(root: AccessibilityNodeInfo): ScreenSnapshot {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val zoneBottom = rootBounds.top + (rootBounds.height() * titleZoneRatio).toInt()
        val acc = Accumulator(zoneBottom)
        visit(root, 0, acc)

        // Tri haut -> bas, puis suppression des doublons (texte + conteneur du même bouton).
        val adds = ArrayList<ButtonRef>()
        for (candidate in acc.adds.sortedWith(compareBy({ it.top }, { it.left }))) {
            if (adds.none { Rect.intersects(it.bounds, candidate.bounds) }) adds += candidate
        }
        return ScreenSnapshot(
            addButtons = adds,
            okButtons = acc.oks,
            // Une simple phrase dans un post ne suffit pas : il faut aussi un bouton OK.
            errorDialogVisible = acc.errorTextSeen && acc.oks.isNotEmpty(),
            onTargetScreen = acc.targetTitleSeen,
            topTexts = acc.topTexts,
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

        if (bounds.top < acc.titleZoneBottom) {
            if (acc.topTexts.size < MAX_TOP_TEXTS) acc.topTexts += text
            if (matcher.isTargetTitle(text)) acc.targetTitleSeen = true
        }

        when {
            matcher.isAddButton(text) ->
                acc.adds += ButtonRef(node, Rect(bounds), cardFingerprint(node, bounds))
            matcher.isOkButton(text) -> acc.oks += node
            matcher.isErrorText(text) -> acc.errorTextSeen = true
            // Aide au diagnostic : texte ressemblant à « Ajouter… » mais non reconnu.
            text.contains("ajouter") && text.length <= 80 -> Log.d(TAG, "Texte non reconnu : '$text'")
        }
    }

    /**
     * Empreinte de la LIGNE de la personne : on remonte depuis le bouton tant que le conteneur
     * reste de la taille d'une ligne et ne contient qu'un seul bouton « Ajouter », puis on hache
     * ses textes (nom, amis en commun…). Contrairement à la position, elle ne change pas quand
     * la liste défile : c'est ce qui évite de recliquer une personne déjà traitée.
     */
    private fun cardFingerprint(button: AccessibilityNodeInfo, buttonBounds: Rect): Int {
        val maxHeight = buttonBounds.height() * MAX_ROW_HEIGHT_FACTOR
        val box = Rect()
        var best = 0
        var ancestor = button.parent
        var hops = 0
        while (ancestor != null && hops < MAX_CARD_HOPS) {
            ancestor.getBoundsInScreen(box)
            if (box.height() > maxHeight) break
            val texts = LinkedHashSet<String>()
            if (collectTexts(ancestor, 0, texts) > 1) break
            texts.removeAll { matcher.isAddButton(it) }
            if (texts.isNotEmpty()) best = texts.hashCode()
            ancestor = ancestor.parent
            hops++
        }
        return best
    }

    /** Collecte les textes du sous-arbre ; renvoie le nombre de boutons « Ajouter » rencontrés. */
    private fun collectTexts(node: AccessibilityNodeInfo, depth: Int, out: MutableSet<String>): Int {
        if (depth > 5 || out.size >= 16) return 0
        var adds = 0
        var isAddNode = false
        for (raw in arrayOf(node.text, node.contentDescription)) {
            val t = TextMatcher.normalize(raw)
            if (t.isEmpty()) continue
            out.add(t)
            if (matcher.isAddButton(t)) isAddNode = true
        }
        if (isAddNode) adds++
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { adds += collectTexts(it, depth + 1, out) }
        }
        return adds
    }

    private companion object {
        const val MAX_DEPTH = 60
        const val MAX_CARD_HOPS = 5
        const val MAX_ROW_HEIGHT_FACTOR = 6
        const val MAX_TOP_TEXTS = 10
        const val TAG = "FriendAdder"
    }
}

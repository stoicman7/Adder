package com.example.friendadder.core

import java.text.Normalizer

/** Compare des textes d'interface après normalisation (casse, accents, ponctuation). */
class TextMatcher(labels: Labels) {

    private val addButtons = labels.addButton.map(::normalize).toSet()
    private val addPatterns = labels.addPatterns.map { Regex(it) }
    private val targetTitles = labels.targetTitles.map(::normalize).toSet()
    private val okButtons = labels.okButton.map(::normalize).toSet()
    private val errorFragments = labels.errorDialog.map(::normalize).filter { it.isNotEmpty() }

    // Les méthodes ci-dessous attendent un texte DÉJÀ normalisé.
    fun isAddButton(normalized: String): Boolean =
        normalized in addButtons ||
            (normalized.length <= MAX_LABEL_LENGTH && addPatterns.any { it.matches(normalized) })

    fun isTargetTitle(normalized: String) = normalized in targetTitles
    fun isOkButton(normalized: String) = normalized in okButtons
    fun isErrorText(normalized: String) = errorFragments.any { normalized.contains(it) }

    companion object {
        private const val MAX_LABEL_LENGTH = 80
        private val combiningMarks = Regex("\\p{Mn}+")
        private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")

        fun normalize(raw: CharSequence?): String {
            if (raw.isNullOrEmpty()) return ""
            return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(combiningMarks, "")
                .lowercase()
                .replace(nonAlphanumeric, " ")
                .trim()
        }
    }
}

package com.example.friendadder.core

import java.text.Normalizer

/** Compare des textes d'interface après normalisation (casse, accents, ponctuation). */
class TextMatcher(labels: Labels) {

    private val addButtons = labels.addButton.map(::normalize).toSet()
    private val okButtons = labels.okButton.map(::normalize).toSet()
    private val errorFragments = labels.errorDialog.map(::normalize).filter { it.isNotEmpty() }

    // Les méthodes ci-dessous attendent un texte DÉJÀ normalisé.
    fun isAddButton(normalized: String): Boolean {
        if (normalized in addButtons) return true

        // Facebook affiche parfois seulement « Ajouter » ou ajoute un petit complément.
        return normalized == "ajouter" ||
            normalized == "ajouter un ami" ||
            normalized == "ajouter une amie" ||
            normalized == "ajouter comme ami" ||
            normalized == "ajouter comme amie"
    }

    fun isOkButton(normalized: String) = normalized in okButtons
    fun isErrorText(normalized: String) = errorFragments.any { normalized.contains(it) }

    companion object {
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

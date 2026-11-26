package com.example.friendadder.core

/** Libellés reconnus à l'écran. Étendez ces ensembles pour ajouter des langues. */
data class Labels(
    /** Libellés EXACTS (insensibles à la casse/accents) des boutons « Ajouter ». */
    val addButton: Set<String> = setOf(
        "Ajouter", "Ajouter ami", "Ajouter en ami", "Ajouter comme ami", "Ajouter comme amie",
        "Add friend", "Agregar", "Agregar a mis amigos",
        "إضافة صديق"
    ),
    /** Fragments de texte signalant la fenêtre « vous ne pouvez pas ajouter cette personne ». */
    val errorDialog: Set<String> = setOf(
        "ne peux pas ajouter", "ne pouvez pas ajouter", "impossible d'ajouter",
        "can't add", "cannot add", "unable to add",
        "no puedes agregar",
        "لا يمكنك إضافة"
    ),
    /** Libellés EXACTS du bouton qui ferme cette fenêtre. */
    val okButton: Set<String> = setOf(
        "OK", "Okay", "D'accord", "Fermer", "Close", "Aceptar", "موافق", "حسنًا"
    )
)

data class BotConfig(
    val facebookPackages: Set<String> = setOf("com.facebook.katana", "com.facebook.lite"),

    // Délais (ms) : tirés au hasard dans la plage pour un rythme non mécanique.
    val startDelayMs: Long = 1_500L,
    val idleDelayMs: Long = 1_500L,
    val afterAddDelayMs: LongRange = 1_500L..2_500L,
    val afterDialogDelayMs: LongRange = 800L..1_400L,
    val afterScrollDelayMs: LongRange = 1_500L..2_500L,

    // Garde-fous.
    val maxAddsPerSession: Int = 100,
    val maxConsecutiveFailures: Int = 5,
    val maxStaleScrolls: Int = 5,

    val labels: Labels = Labels()
)

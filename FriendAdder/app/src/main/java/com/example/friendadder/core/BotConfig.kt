package com.example.friendadder.core

/** Libellés reconnus à l'écran. Étendez ces ensembles pour ajouter des langues. */
data class Labels(
    /** Libellés EXACTS (insensibles à la casse, aux accents et à la ponctuation). */
    val addButton: Set<String> = setOf(
        "Ajouter", "Ajouter ami", "Ajouter comme ami(e)", "Ajouter comme ami", "Ajouter en ami",
        "Add friend", "Agregar", "Agregar a mis amigos",
        "إضافة صديق"
    ),
    /**
     * Motifs (regex, appliqués au texte normalisé : minuscules, sans accents, ponctuation -> espace).
     * Couvrent les variantes avec le nom de la personne, ex. « Ajouter Isam Hicher comme ami(e) ».
     */
    val addPatterns: List<String> = listOf(
        "ajouter ami( ?e)?s?",
        "ajouter( .{1,40})? (comme|en|a mes|a vos) ami( ?e)?s?",
        "add( .{1,40})? as (a )?friend",
        "agregar( .{1,40})? como amigo",
        "اضافة .{1,40} كصديق",
        "اضافة كصديق"
    ),
    /**
     * Titres (barre du haut) des SEULS écrans où le bot a le droit d'agir :
     * liste des réactions d'un post, liste des membres d'un groupe.
     */
    val targetTitles: Set<String> = setOf(
        "Réactions", "Reactions", "Membres", "Members",
        "Reacciones", "Miembros", "التفاعلات", "الأعضاء"
    ),
    /** Fragments de texte signalant une fenêtre d'erreur d'invitation. */
    val errorDialog: Set<String> = setOf(
        "Impossible d'envoyer l'invitation", "Impossible d'envoyer",
        "ne connaissez peut-être pas cette personne", "ne connaissiez peut-être pas cette personne",
        "ne peux pas ajouter", "ne pouvez pas ajouter", "impossible d'ajouter",
        "can't add", "cannot add", "unable to add", "can't send", "unable to send",
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

    /** true = le bot n'agit que sur la liste des réactions d'un post / des membres d'un groupe. */
    val requireTargetScreen: Boolean = true,
    /** Portion haute de l'écran (0..1) où l'on cherche le titre de la page. */
    val titleZoneRatio: Float = 0.18f,

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

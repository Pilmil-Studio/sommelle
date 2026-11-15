package fr.sommelle.feature.cognitive

/** Clés du bloc-notes du soir. Le fichier contient aussi les habitudes cochées (« habit_N »), à ne pas effacer. */
internal object EveningNotepad {
    const val WORRY = "worry"
    const val ACTION = "action"
    const val PUT_AWAY = "put_away"

    /** Ce que « effacer l'inquiétude » supprime, et rien d'autre. */
    val WORRY_KEYS = listOf(WORRY, ACTION, PUT_AWAY)

    /** Clés à supprimer parmi celles du fichier. */
    fun keysToClear(allKeys: Set<String>): List<String> = allKeys.filter { it in WORRY_KEYS }
}

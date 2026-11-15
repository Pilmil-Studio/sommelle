package fr.sommelle.core.model

import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Fenêtre de sommeil prescrite par la restriction du temps au lit.
 * L'heure de lever est l'ancre fixe ; l'heure de coucher en découle.
 */
data class SleepWindow(
    val bedTime: LocalTime,
    val wakeTime: LocalTime,
    val duration: Duration,
    /** Premier jour d'application de cette fenêtre. */
    val effectiveFrom: LocalDate,
    /** Version du protocole qui a produit cette fenêtre, pour la traçabilité clinique. */
    val protocolVersion: Int,
)

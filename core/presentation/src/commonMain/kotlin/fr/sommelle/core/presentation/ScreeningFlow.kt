package fr.sommelle.core.presentation

import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.ScreeningAnswers

/**
 * Questions oui/non du questionnaire d'entrée, posées une par écran après la tranche d'âge.
 * Les libellés sont dans les ressources du module d'interface ; chaque question sait
 * si elle s'applique et comment elle modifie les réponses.
 */
enum class ScreeningQuestion(
    /** Vrai si « Je ne sais pas » est proposé : la réponse est alors traitée comme un « oui » prudent. */
    val allowsUnsure: Boolean = false,
    private val appliesTo: (ScreeningAnswers) -> Boolean = { true },
    private val set: (ScreeningAnswers, Boolean) -> ScreeningAnswers,
) {
    SHIFT_WORK(set = { a, v -> a.copy(shiftOrNightWork = v) }),
    EXTERNAL_DISRUPTION(set = { a, v -> a.copy(externalNightDisruption = v) }),
    SNORING_PAUSES(allowsUnsure = true, set = { a, v -> a.copy(loudSnoringWithBreathingPauses = v) }),
    DAYTIME_SLEEPINESS(allowsUnsure = true, set = { a, v -> a.copy(markedDaytimeSleepiness = v) }),
    BIPOLAR(set = { a, v -> a.copy(bipolarDisorder = v) }),
    EPILEPSY(set = { a, v -> a.copy(epilepsy = v) }),
    PARASOMNIA(set = { a, v -> a.copy(parasomnia = v) }),
    MOOD_DISORDER(set = { a, v -> a.copy(moodDisorder = v) }),
    FALL_RISK_OR_EVENING_SEDATIVES(set = { a, v -> a.copy(fallRiskOrEveningSedatives = v) }),
    UNSTABLE_OR_SLEEP_SENSITIVE_CONDITION(set = { a, v -> a.copy(unstableOrSleepSensitiveCondition = v) }),
    PREGNANCY(set = { a, v -> a.copy(pregnancy = v) }),
    SAFETY_CRITICAL(set = { a, v -> a.copy(safetyCriticalActivity = v) }),
    LATE_SCHEDULE(set = { a, v -> a.copy(lateScheduleSleepsWellOnceAsleep = v) }),
    DISTRESS(set = { a, v -> a.copy(expressedDistress = v) });

    fun applies(answers: ScreeningAnswers): Boolean = appliesTo(answers)
    fun answer(answers: ScreeningAnswers, yes: Boolean): ScreeningAnswers = set(answers, yes)

    companion object {
        /**
         * Situations médicales, présentées ensemble sur un seul écran à cocher plutôt qu'en
         * autant d'écrans oui/non. L'écran est repéré par la première question du groupe.
         * Le groupe doit rester contigu et dans l'ordre de l'énumération : la question suivante
         * est cherchée après la dernière du groupe.
         */
        val MEDICAL_GROUP: List<ScreeningQuestion> = listOf(
            BIPOLAR,
            EPILEPSY,
            PARASOMNIA,
            MOOD_DISORDER,
            FALL_RISK_OR_EVENING_SEDATIVES,
            UNSTABLE_OR_SLEEP_SENSITIVE_CONDITION,
            PREGNANCY,
        )
    }
}

/** Progression dans le questionnaire : l'âge d'abord, puis les questions qui s'appliquent. */
data class ScreeningFlow(
    val answers: ScreeningAnswers? = null,
    val current: ScreeningQuestion? = null,
    val finished: Boolean = false,
) {
    /** Étape actuelle (1 = âge) et nombre total estimé d'écrans, pour la barre de progression. */
    val stepNumber: Int get() = when {
        finished -> totalSteps
        current == null -> 1
        else -> 2 + SCREENS.indexOf(current)
    }
    val totalSteps: Int get() = 1 + SCREENS.size

    val isAgeStep: Boolean get() = !finished && current == null

    /** Vrai sur l'écran qui regroupe les situations médicales : on y répond avec [answerMedical]. */
    val isMedicalStep: Boolean get() = !finished && current == MEDICAL_HEAD

    /** Situations déjà cochées, pour retrouver ses réponses en revenant en arrière. */
    val medicalSelection: Set<ScreeningQuestion> get() {
        val a = answers ?: return emptySet()
        return buildSet {
            if (a.bipolarDisorder) add(ScreeningQuestion.BIPOLAR)
            if (a.epilepsy) add(ScreeningQuestion.EPILEPSY)
            if (a.parasomnia) add(ScreeningQuestion.PARASOMNIA)
            if (a.moodDisorder) add(ScreeningQuestion.MOOD_DISORDER)
            if (a.fallRiskOrEveningSedatives) add(ScreeningQuestion.FALL_RISK_OR_EVENING_SEDATIVES)
            if (a.unstableOrSleepSensitiveCondition) add(ScreeningQuestion.UNSTABLE_OR_SLEEP_SENSITIVE_CONDITION)
            if (a.pregnancy) add(ScreeningQuestion.PREGNANCY)
        }
    }

    fun chooseAge(ageBand: AgeBand): ScreeningFlow {
        val a = answers?.copy(ageBand = ageBand) ?: ScreeningAnswers(ageBand = ageBand)
        return copy(answers = a).advanceFrom(null)
    }

    fun answer(yes: Boolean): ScreeningFlow {
        val q = checkNotNull(current) { "aucune question en cours" }
        check(!isMedicalStep) { "l'écran des situations médicales se valide avec answerMedical" }
        val a = q.answer(checkNotNull(answers), yes)
        return copy(answers = a).advanceFrom(q)
    }

    /**
     * « Je ne sais pas » : réponse prudente, enregistrée comme un « oui » (parcours sans restriction du temps
     * au lit et orientation vers un médecin). Sans effet sur les questions qui ne la proposent pas.
     */
    fun answerUnsure(): ScreeningFlow {
        val q = checkNotNull(current) { "aucune question en cours" }
        return answer(yes = q.allowsUnsure)
    }

    /**
     * Valide l'écran des situations médicales : chaque situation du groupe est enregistrée, cochée
     * ou non. Ne rien cocher revient à répondre non à toutes.
     */
    fun answerMedical(selected: Set<ScreeningQuestion>): ScreeningFlow {
        check(isMedicalStep) { "l'écran des situations médicales n'est pas en cours" }
        var a = checkNotNull(answers)
        for (question in ScreeningQuestion.MEDICAL_GROUP) a = question.answer(a, question in selected)
        return copy(answers = a).advanceFrom(ScreeningQuestion.MEDICAL_GROUP.last())
    }

    /** Retour à la question précédente, ou à l'âge. Les réponses déjà données sont conservées. */
    fun back(): ScreeningFlow {
        if (isAgeStep) return this
        val a = checkNotNull(answers)
        val from = if (finished) ScreeningQuestion.entries.size else checkNotNull(current).ordinal
        val previous = ScreeningQuestion.entries.take(from).lastOrNull { it.applies(a) }
        // Revenir sur une question du groupe médical ramène à son écran commun.
        return copy(current = previous?.let(::screenOf), finished = false)
    }

    private fun advanceFrom(question: ScreeningQuestion?): ScreeningFlow {
        val a = checkNotNull(answers)
        val start = question?.ordinal?.plus(1) ?: 0
        val next = ScreeningQuestion.entries.drop(start).firstOrNull { it.applies(a) }
        return if (next == null) copy(current = null, finished = true) else copy(current = screenOf(next), finished = false)
    }

    private fun screenOf(question: ScreeningQuestion): ScreeningQuestion =
        if (question in ScreeningQuestion.MEDICAL_GROUP) MEDICAL_HEAD else question

    private companion object {
        val MEDICAL_HEAD = ScreeningQuestion.MEDICAL_GROUP.first()

        /** Un écran par question, sauf le groupe médical qui n'en occupe qu'un. */
        val SCREENS = ScreeningQuestion.entries.filter { it !in ScreeningQuestion.MEDICAL_GROUP || it == MEDICAL_HEAD }
    }
}

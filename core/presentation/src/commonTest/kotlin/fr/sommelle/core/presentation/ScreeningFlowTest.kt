package fr.sommelle.core.presentation

import fr.sommelle.core.model.AgeBand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreeningFlowTest {

    private fun answerAll(flow: ScreeningFlow, yes: (ScreeningQuestion) -> Boolean): ScreeningFlow {
        var f = flow
        while (!f.finished) {
            f = if (f.isMedicalStep) {
                f.answerMedical(ScreeningQuestion.MEDICAL_GROUP.filter(yes).toSet())
            } else {
                f.answer(yes(f.current!!))
            }
        }
        return f
    }

    @Test
    fun `l age vient en premier puis toutes les questions`() {
        val start = ScreeningFlow()
        assertTrue(start.isAgeStep)
        assertEquals(1, start.stepNumber)
        val afterAge = start.chooseAge(AgeBand.FROM_25_TO_44)
        assertEquals(ScreeningQuestion.SHIFT_WORK, afterAge.current)
        assertEquals(2, afterAge.stepNumber)
        val done = answerAll(afterAge) { false }
        assertTrue(done.finished)
        assertFalse(done.answers!!.safetyCriticalActivity)
    }

    @Test
    fun `une activite a risque ne pose plus de question de preference`() {
        val asked = mutableListOf<ScreeningQuestion>()
        val done = answerAll(ScreeningFlow().chooseAge(AgeBand.FROM_45_TO_64)) {
            asked += it
            it == ScreeningQuestion.SAFETY_CRITICAL
        }
        assertTrue(done.answers!!.safetyCriticalActivity)
        assertEquals(ScreeningQuestion.entries.toList(), asked)
    }

    @Test
    fun `les quatre precautions de l accueil sont posees et enregistrees`() {
        val expected = mapOf(
            ScreeningQuestion.PARASOMNIA to { a: fr.sommelle.core.model.ScreeningAnswers -> a.parasomnia },
            ScreeningQuestion.MOOD_DISORDER to { a: fr.sommelle.core.model.ScreeningAnswers -> a.moodDisorder },
            ScreeningQuestion.FALL_RISK_OR_EVENING_SEDATIVES to { a: fr.sommelle.core.model.ScreeningAnswers -> a.fallRiskOrEveningSedatives },
            ScreeningQuestion.UNSTABLE_OR_SLEEP_SENSITIVE_CONDITION to { a: fr.sommelle.core.model.ScreeningAnswers -> a.unstableOrSleepSensitiveCondition },
        )
        expected.forEach { (question, read) ->
            val asked = mutableListOf<ScreeningQuestion>()
            val done = answerAll(ScreeningFlow().chooseAge(AgeBand.FROM_25_TO_44)) {
                asked += it
                it == question
            }
            assertTrue(question in asked, "question posée : $question")
            assertTrue(read(done.answers!!), "réponse oui enregistrée : $question")
            assertFalse(question.allowsUnsure, "pas de « je ne sais pas » : $question")
            val result = fr.sommelle.core.protocol.ScreeningEvaluator().evaluate(done.answers!!)
            assertEquals(fr.sommelle.core.model.Pathway.WITHOUT_RESTRICTION, result.pathway, "parcours : $question")
            assertTrue(result.referrals.isNotEmpty(), "invitation à consulter : $question")
        }
    }

    @Test
    fun `repondre non a toutes les questions garde le programme complet`() {
        val done = answerAll(ScreeningFlow().chooseAge(AgeBand.FROM_25_TO_44)) { false }
        val result = fr.sommelle.core.protocol.ScreeningEvaluator().evaluate(done.answers!!)
        assertEquals(fr.sommelle.core.model.Pathway.FULL, result.pathway)
        assertTrue(result.referrals.isEmpty())
    }

    @Test
    fun `retour en arriere`() {
        val q2 = ScreeningFlow().chooseAge(AgeBand.FROM_65).answer(true)
        assertEquals(ScreeningQuestion.EXTERNAL_DISRUPTION, q2.current)
        val back = q2.back()
        assertEquals(ScreeningQuestion.SHIFT_WORK, back.current)
        assertTrue(back.answers!!.shiftOrNightWork)
        assertTrue(back.back().isAgeStep)

        val done = answerAll(ScreeningFlow().chooseAge(AgeBand.FROM_65)) { false }
        assertEquals(ScreeningQuestion.DISTRESS, done.back().current)
    }

    @Test
    fun `je ne sais pas est une reponse prudente pour le ronflement et la somnolence`() {
        val evaluator = fr.sommelle.core.protocol.ScreeningEvaluator()
        listOf(ScreeningQuestion.SNORING_PAUSES, ScreeningQuestion.DAYTIME_SLEEPINESS).forEach { unsureOn ->
            assertTrue(unsureOn.allowsUnsure)
            var flow = ScreeningFlow().chooseAge(AgeBand.FROM_25_TO_44)
            while (!flow.finished) {
                flow = when {
                    flow.isMedicalStep -> flow.answerMedical(emptySet())
                    flow.current == unsureOn -> flow.answerUnsure()
                    else -> flow.answer(false)
                }
            }
            val result = evaluator.evaluate(flow.answers!!)
            assertEquals(fr.sommelle.core.model.Pathway.WITHOUT_RESTRICTION, result.pathway, "doute sur $unsureOn")
            assertTrue(result.referrals.isNotEmpty(), "invitation à consulter : $unsureOn")
        }
    }

    @Test
    fun `je ne sais pas n existe pas pour les autres questions`() {
        assertFalse(ScreeningQuestion.EPILEPSY.allowsUnsure)
        val q = ScreeningFlow().chooseAge(AgeBand.FROM_25_TO_44)
        assertEquals(ScreeningQuestion.SHIFT_WORK, q.current)
        assertFalse(q.answerUnsure().answers!!.shiftOrNightWork)
    }

    private fun atMedicalStep(): ScreeningFlow {
        var f = ScreeningFlow().chooseAge(AgeBand.FROM_25_TO_44)
        while (!f.isMedicalStep) f = f.answer(false)
        return f
    }

    @Test
    fun `les trois situations medicales tiennent sur un seul ecran`() {
        val medical = atMedicalStep()
        assertEquals(ScreeningQuestion.BIPOLAR, medical.current)
        // Âge, quatre questions, puis l'écran médical : sixième écran.
        assertEquals(6, medical.stepNumber)
        val after = medical.answerMedical(emptySet())
        assertEquals(ScreeningQuestion.SAFETY_CRITICAL, after.current)
        assertEquals(7, after.stepNumber)
        // Deux écrans de moins qu'avec une question par écran.
        assertEquals(ScreeningQuestion.entries.size - 1, after.totalSteps)
    }

    @Test
    fun `ne rien cocher repond non aux trois, cocher enregistre chaque situation`() {
        val none = atMedicalStep().answerMedical(emptySet()).answers!!
        assertFalse(none.bipolarDisorder)
        assertFalse(none.epilepsy)
        assertFalse(none.pregnancy)

        val some = atMedicalStep().answerMedical(setOf(ScreeningQuestion.EPILEPSY, ScreeningQuestion.PREGNANCY)).answers!!
        assertFalse(some.bipolarDisorder)
        assertTrue(some.epilepsy)
        assertTrue(some.pregnancy)
    }

    @Test
    fun `revenir sur l ecran medical retrouve les cases cochees et permet de les corriger`() {
        val after = atMedicalStep().answerMedical(setOf(ScreeningQuestion.BIPOLAR))
        val back = after.back()
        assertTrue(back.isMedicalStep)
        assertEquals(setOf(ScreeningQuestion.BIPOLAR), back.medicalSelection)
        // Décocher efface bien la réponse précédente.
        val corrected = back.answerMedical(emptySet())
        assertFalse(corrected.answers!!.bipolarDisorder)
        assertEquals(emptySet(), corrected.back().medicalSelection)
        // Depuis l'écran médical, le retour mène à la question d'avant.
        assertEquals(ScreeningQuestion.DAYTIME_SLEEPINESS, back.back().current)
    }

    @Test
    fun `l ecran medical ne se valide pas comme une question oui ou non`() {
        val medical = atMedicalStep()
        assertTrue(runCatching { medical.answer(true) }.isFailure)
        assertTrue(runCatching { ScreeningFlow().chooseAge(AgeBand.FROM_65).answerMedical(emptySet()) }.isFailure)
    }

    @Test
    fun `la progression avance d un ecran a la fois jusqu au bout`() {
        var f = ScreeningFlow().chooseAge(AgeBand.FROM_45_TO_64)
        var expected = 2
        while (!f.finished) {
            assertEquals(expected, f.stepNumber)
            f = if (f.isMedicalStep) f.answerMedical(emptySet()) else f.answer(true)
            expected++
        }
        // Toutes les questions s'appliquent quand on répond oui à l'activité à risque.
        assertEquals(f.totalSteps, expected - 1)
    }
}

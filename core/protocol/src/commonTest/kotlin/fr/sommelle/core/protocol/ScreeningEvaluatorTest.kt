package fr.sommelle.core.protocol

import fr.sommelle.core.model.*

import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.Notice
import fr.sommelle.core.model.Pathway
import fr.sommelle.core.model.Referral
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreeningEvaluatorTest {

    private val evaluator = ScreeningEvaluator()

    @Test
    fun `sans contre-indication le programme est complet avec le plancher par defaut`() {
        val r = evaluator.evaluate(answers())
        assertEquals(Pathway.FULL, r.pathway)
        assertEquals(Duration.ofHours(6), r.minimumSleepWindow)
        assertTrue(r.restrictionAllowed)
        assertTrue(r.referrals.isEmpty())
    }

    @Test
    fun `les mineurs sont hors perimetre et orientes`() {
        val r = evaluator.evaluate(answers(AgeBand.UNDER_18))
        assertEquals(Pathway.OUT_OF_SCOPE, r.pathway)
        assertEquals(setOf(Referral.MINOR), r.referrals)
        assertFalse(r.restrictionAllowed)
    }

    @Test
    fun `travail poste et cause externe sont hors perimetre`() {
        assertEquals(Pathway.OUT_OF_SCOPE, evaluator.evaluate(answers().copy(shiftOrNightWork = true)).pathway)
        val external = evaluator.evaluate(answers().copy(externalNightDisruption = true))
        assertEquals(Pathway.OUT_OF_SCOPE, external.pathway)
        assertTrue(Notice.EXTERNAL_DISRUPTION in external.notices)
    }

    @Test
    fun `apnee suspectee donne un parcours sans restriction et une orientation`() {
        val r = evaluator.evaluate(answers().copy(loudSnoringWithBreathingPauses = true))
        assertEquals(Pathway.WITHOUT_RESTRICTION, r.pathway)
        assertNull(r.minimumSleepWindow)
        assertEquals(setOf(Referral.SLEEP_APNEA_SUSPECTED), r.referrals)
    }

    @Test
    fun `chaque contre-indication bloque la restriction`() {
        val cases = listOf(
            answers().copy(markedDaytimeSleepiness = true),
            answers().copy(bipolarDisorder = true),
            answers().copy(epilepsy = true),
            answers().copy(pregnancy = true),
            answers().copy(lateScheduleSleepsWellOnceAsleep = true),
            answers().copy(safetyCriticalActivity = true),
            answers().copy(expressedDistress = true),
        )
        cases.forEach { assertFalse(evaluator.evaluate(it).restrictionAllowed, "cas : $it") }
    }

    @Test
    fun `activite a risque donne un parcours sans restriction avec son message`() {
        val r = evaluator.evaluate(answers().copy(safetyCriticalActivity = true))
        assertEquals(Pathway.WITHOUT_RESTRICTION, r.pathway)
        assertNull(r.minimumSleepWindow)
        assertFalse(r.restrictionAllowed)
        assertTrue(Notice.SAFETY_CRITICAL_ACTIVITY in r.notices)
        assertNull(RestrictionProfile.from(ProtocolConfig.DEFAULT, r))
    }

    @Test
    fun `detresse exprimee donne un parcours sans restriction avec le 3114`() {
        val r = evaluator.evaluate(answers().copy(expressedDistress = true))
        assertEquals(Pathway.WITHOUT_RESTRICTION, r.pathway)
        assertNull(r.minimumSleepWindow)
        assertTrue(Notice.CRISIS_RESOURCES in r.notices)
        assertNull(RestrictionProfile.from(ProtocolConfig.DEFAULT, r))
    }

    @Test
    fun `l avertissement somnolence est emis pour tout parcours avec restriction`() {
        AgeBand.entries.filter { it != AgeBand.UNDER_18 }.forEach { age ->
            val r = evaluator.evaluate(answers(age))
            assertEquals(Pathway.FULL, r.pathway)
            assertTrue(Notice.DROWSINESS_WARNING in r.notices, "tranche d'âge : $age")
        }
    }

    @Test
    fun `pas d avertissement de restriction quand la restriction n est pas proposee`() {
        assertFalse(Notice.DROWSINESS_WARNING in evaluator.evaluate(answers().copy(epilepsy = true)).notices)
    }

    @Test
    fun `la detresse exprimee affiche les ressources d aide quel que soit le parcours`() {
        assertTrue(Notice.CRISIS_RESOURCES in evaluator.evaluate(answers().copy(expressedDistress = true)).notices)
        val outOfScope = answers(AgeBand.UNDER_18).copy(expressedDistress = true)
        assertTrue(Notice.CRISIS_RESOURCES in evaluator.evaluate(outOfScope).notices)
    }
}

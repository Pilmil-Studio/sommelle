package fr.sommelle.core.protocol

import fr.sommelle.core.model.*

import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.SleepWindow
import kotlin.time.Duration
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SleepWindowCalculatorTest {

    private val config = ProtocolConfig.DEFAULT
    private val calculator = SleepWindowCalculator(config)
    private val evaluator = ScreeningEvaluator(config)
    private val adult = RestrictionProfile.from(config, evaluator.evaluate(answers()))!!
    private val senior = RestrictionProfile.from(config, evaluator.evaluate(answers(AgeBand.FROM_65)))!!

    private fun windowOf(minutes: Long) = SleepWindow(
        bedTime = WAKE.minusMinutes(minutes),
        wakeTime = WAKE,
        duration = Duration.ofMinutes(minutes),
        effectiveFrom = MONDAY.minusDays(7),
        protocolVersion = config.version,
    )

    @Test
    fun `fenetre initiale egale au sommeil moyen arrondi au quart d heure superieur`() {
        val baseline = week(MONDAY, nights = 7, timeInBedMin = 510, totalSleepMin = 370)
        val result = calculator.initialWindow(baseline, WAKE, adult, MONDAY)
        assertIs<InitialWindowResult.Ready>(result)
        assertEquals(Duration.ofMinutes(375), result.window.duration)
        assertEquals(LocalTime.of(0, 30), result.window.bedTime)
        assertEquals(WAKE, result.window.wakeTime)
        assertEquals(config.version, result.window.protocolVersion)
    }

    @Test
    fun `fenetre initiale jamais sous le plancher`() {
        val baseline = week(MONDAY, nights = 7, timeInBedMin = 480, totalSleepMin = 290)
        val result = calculator.initialWindow(baseline, WAKE, adult, MONDAY)
        assertIs<InitialWindowResult.Ready>(result)
        assertEquals(Duration.ofHours(6), result.window.duration)
        assertEquals(LocalTime.of(0, 45), result.window.bedTime)
    }

    @Test
    fun `le plancher est de 6 h meme pour un sommeil tres court`() {
        assertEquals(Duration.ofHours(6), adult.minimumWindow)
        val baseline = week(MONDAY, nights = 7, timeInBedMin = 420, totalSleepMin = 240)
        val result = calculator.initialWindow(baseline, WAKE, adult, MONDAY)
        assertIs<InitialWindowResult.Ready>(result)
        assertEquals(Duration.ofHours(6), result.window.duration)
    }

    @Test
    fun `une fenetre en cours de 5 h 30 est relevee a 6 h au prochain calcul`() {
        // Fenêtre créée avec l'ancien plancher (5 h 30), quelle que soit la décision de la semaine.
        val low = windowOf(330)
        val atFloorEfficiency = calculator.weeklyAdjustment(low, week(MONDAY, 7, 330, 200), adult, MONDAY)
        assertEquals(Duration.ofHours(6), atFloorEfficiency.window.duration)
        assertEquals(LocalTime.of(0, 45), atFloorEfficiency.window.bedTime)

        val steady = calculator.weeklyAdjustment(low, week(MONDAY, 7, 330, 272), adult, MONDAY)
        assertEquals(Duration.ofHours(6), steady.window.duration)

        val longer = calculator.weeklyAdjustment(low, week(MONDAY, 7, 330, 320), adult, MONDAY)
        assertEquals(WindowChange.INCREASED, longer.change)
        assertEquals(Duration.ofHours(6), longer.window.duration)

        val missing = calculator.weeklyAdjustment(low, week(MONDAY, 3, 330, 320), adult, MONDAY)
        assertEquals(WindowChange.UNCHANGED_INSUFFICIENT_DATA, missing.change)
        assertEquals(Duration.ofHours(6), missing.window.duration)
    }

    @Test
    fun `somnolence declaree pas de reduction cette semaine la`() {
        val low = week(MONDAY, 7, 390, 290)
        val held = calculator.weeklyAdjustment(windowOf(390), low, adult, MONDAY, holdReduction = true)
        assertEquals(WindowChange.UNCHANGED_SLEEPINESS, held.change)
        assertEquals(Duration.ofMinutes(390), held.window.duration)
        // Sans somnolence, la même semaine réduit la fenêtre.
        assertEquals(WindowChange.DECREASED, calculator.weeklyAdjustment(windowOf(390), low, adult, MONDAY).change)
    }

    @Test
    fun `somnolence declaree l elargissement reste permis`() {
        val d = calculator.weeklyAdjustment(windowOf(360), week(MONDAY, 7, 360, 330), adult, MONDAY, holdReduction = true)
        assertEquals(WindowChange.INCREASED, d.change)
        assertEquals(Duration.ofMinutes(375), d.window.duration)
    }

    @Test
    fun `somnolence declaree et fenetre sous le plancher la fenetre est quand meme relevee`() {
        val d = calculator.weeklyAdjustment(windowOf(330), week(MONDAY, 7, 330, 200), adult, MONDAY, holdReduction = true)
        assertEquals(Duration.ofHours(6), d.window.duration)
    }

    @Test
    fun `pas de fenetre initiale sans assez de nuits`() {
        val result = calculator.initialWindow(week(MONDAY, 4, 480, 360), WAKE, adult, MONDAY)
        assertEquals(InitialWindowResult.InsufficientData(loggedNights = 4, requiredNights = 5), result)
    }

    @Test
    fun `efficacite elevee allonge la fenetre`() {
        val d = calculator.weeklyAdjustment(windowOf(360), week(MONDAY, 7, 360, 330), adult, MONDAY)
        assertEquals(WindowChange.INCREASED, d.change)
        assertEquals(Duration.ofMinutes(375), d.window.duration)
        assertEquals(LocalTime.of(0, 30), d.window.bedTime)
        assertEquals(MONDAY, d.window.effectiveFrom)
    }

    @Test
    fun `efficacite faible raccourcit la fenetre`() {
        val d = calculator.weeklyAdjustment(windowOf(390), week(MONDAY, 7, 390, 290), adult, MONDAY)
        assertEquals(WindowChange.DECREASED, d.change)
        assertEquals(Duration.ofMinutes(375), d.window.duration)
    }

    @Test
    fun `efficacite intermediaire maintient la fenetre`() {
        val d = calculator.weeklyAdjustment(windowOf(360), week(MONDAY, 7, 360, 297), adult, MONDAY)
        assertEquals(WindowChange.UNCHANGED_IN_TARGET_RANGE, d.change)
        assertEquals(Duration.ofMinutes(360), d.window.duration)
    }

    @Test
    fun `la reduction s arrete au plancher`() {
        val atFloor = calculator.weeklyAdjustment(windowOf(360), week(MONDAY, 7, 360, 200), adult, MONDAY)
        assertEquals(WindowChange.UNCHANGED_AT_FLOOR, atFloor.change)
        assertEquals(Duration.ofMinutes(360), atFloor.window.duration)

        val nearFloor = calculator.weeklyAdjustment(windowOf(370), week(MONDAY, 7, 370, 200), adult, MONDAY)
        assertEquals(WindowChange.DECREASED_TO_FLOOR, nearFloor.change)
        assertEquals(Duration.ofMinutes(360), nearFloor.window.duration)
    }

    @Test
    fun `l allongement s arrete au maximum`() {
        val d = calculator.weeklyAdjustment(windowOf(540), week(MONDAY, 7, 540, 530), adult, MONDAY)
        assertEquals(WindowChange.UNCHANGED_AT_MAXIMUM, d.change)
        assertEquals(Duration.ofHours(9), d.window.duration)
    }

    @Test
    fun `semaine incomplete la fenetre est maintenue`() {
        val d = calculator.weeklyAdjustment(windowOf(360), week(MONDAY, 3, 360, 350), adult, MONDAY)
        assertEquals(WindowChange.UNCHANGED_INSUFFICIENT_DATA, d.change)
        assertEquals(Duration.ofMinutes(360), d.window.duration)
        assertEquals(MONDAY, d.window.effectiveFrom)
    }

    @Test
    fun `seuils abaisses pour les 65 ans et plus`() {
        // Efficacité de 82 % : maintien pour un adulte, allongement pour un senior.
        val nights = week(MONDAY, 7, 400, 328)
        assertEquals(WindowChange.UNCHANGED_IN_TARGET_RANGE, calculator.weeklyAdjustment(windowOf(400), nights, adult, MONDAY).change)
        assertEquals(WindowChange.INCREASED, calculator.weeklyAdjustment(windowOf(400), nights, senior, MONDAY).change)

        // Efficacité de 77 % : réduction pour un adulte, maintien pour un senior.
        val lower = week(MONDAY, 7, 400, 308)
        assertEquals(WindowChange.DECREASED, calculator.weeklyAdjustment(windowOf(400), lower, adult, MONDAY).change)
        assertEquals(WindowChange.UNCHANGED_IN_TARGET_RANGE, calculator.weeklyAdjustment(windowOf(400), lower, senior, MONDAY).change)
    }

    @Test
    fun `pas de profil de restriction si la restriction est contre-indiquee`() {
        assertNull(RestrictionProfile.from(config, evaluator.evaluate(answers().copy(epilepsy = true))))
    }

    @Test
    fun `heure de lever suggeree a partir de la mediane arrondie`() {
        val baseline = listOf("06:40", "06:50", "07:05", "06:20", "06:55").mapIndexed { i, wake ->
            entry(nightOf = MONDAY.minusDays(i.toLong()), finalWake = wake, outOfBed = wake)
        }
        assertEquals(LocalTime.of(6, 45), calculator.suggestWakeTime(baseline))
        assertNull(calculator.suggestWakeTime(emptyList()))
    }
}

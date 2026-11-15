package fr.sommelle.core.protocol

import fr.sommelle.core.model.*

import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.SleepQuality
import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepInsightsAnalyzerTest {

    private fun entry(
        dayOffset: Long,
        wakeHour: Int = 7,
        wakeMinute: Int = 0,
        solMinutes: Long = 15,
        lingerMorningMinutes: Long = 10,
        bedHour: Int = 23,
    ): DiaryEntry {
        val morning = LocalDate.of(2026, 5, 1).plusDays(dayOffset)
        val bed = LocalDateTime.of(morning.minusDays(1), LocalTime.of(bedHour, 0))
        val lightsOut = bed.plusMinutes(10)
        val wake = LocalDateTime.of(morning, LocalTime.of(wakeHour, wakeMinute))
        val outOfBed = wake.plusMinutes(lingerMorningMinutes)
        return DiaryEntry(
            nightOf = morning,
            bedTime = bed,
            lightsOutTime = lightsOut,
            sleepOnsetLatency = Duration.ofMinutes(solMinutes),
            awakeningCount = 0,
            wakeAfterSleepOnset = Duration.ZERO,
            finalWakeTime = wake,
            outOfBedTime = outOfBed,
            quality = SleepQuality.GOOD,
        )
    }

    @Test
    fun `moins de 14 nuits ne produit pas de facteurs artificiels`() {
        val entries = (0L until 10L).map { entry(it) }
        val report = SleepInsightsAnalyzer().analyze(entries)

        assertFalse(report.hasEnoughDataForFactors)
        assertTrue(report.factors.isEmpty())
    }

    @Test
    fun `detecte la variabilite du lever associee aux endormissements longs`() {
        val entries = (0L until 18L).map { day ->
            if (day % 4L == 0L) {
                entry(day, wakeHour = 9, wakeMinute = 30, solMinutes = 50)
            } else {
                entry(day, wakeHour = 7, wakeMinute = 0, solMinutes = 12)
            }
        }
        val report = SleepInsightsAnalyzer().analyze(entries)

        assertTrue(report.hasEnoughDataForFactors)
        val wakeFactor = report.factors.firstOrNull { it.type == SleepFactorType.WAKE_VARIABILITY }
        assertTrue(wakeFactor != null, "WAKE_VARIABILITY attendu dans ${report.factors}")
        assertTrue(wakeFactor.exposedDifficultRate > wakeFactor.baselineDifficultRate)
    }

    @Test
    fun `detecte amelioration de efficacite entre deux semaines`() {
        val week1 = (0L until 7L).map { entry(it, solMinutes = 60, lingerMorningMinutes = 45) }
        val week2 = (7L until 14L).map { entry(it, solMinutes = 12, lingerMorningMinutes = 5) }
        val report = SleepInsightsAnalyzer().analyze(week1 + week2)

        assertEquals(EfficiencyTrend.IMPROVING, report.efficiencyTrend)
    }
}

package fr.sommelle.core.protocol

import fr.sommelle.core.model.*

import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.SleepWindow
import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlin.math.abs

enum class EfficiencyTrend {
    IMPROVING,
    STABLE,
    DECLINING,
}

enum class SleepFactorType {
    /** Écart de l'heure de lever > 60 min par rapport à la médiane personnelle. */
    WAKE_VARIABILITY,

    /** Temps passé au lit éveillé avant d'essayer de dormir (Q2 - Q1 >= 30 min). */
    PRE_SLEEP_AWAKE_IN_BED,

    /** Temps resté au lit après le réveil final (Q7 - Q6 >= 30 min). */
    MORNING_LINGER_IN_BED,
}

data class SleepFactorObservation(
    val type: SleepFactorType,
    val affectedNights: Int,
    val exposedNights: Int,
    val exposedDifficultRate: Double,
    val baselineDifficultRate: Double,
)

data class SleepInsightsReport(
    val analyzedNights: Int,
    val hasEnoughDataForFactors: Boolean,
    val recentEfficiency: Double?,
    val previousEfficiency: Double?,
    val efficiencyTrend: EfficiencyTrend?,
    val factors: List<SleepFactorObservation>,
    val windowDecision: WindowDecision? = null,
)

/**
 * Moteur d'analyse locale et explicable de l'agenda du sommeil.
 * Exécuté intégralement sur l'appareil, sans serveur ni boîte noire.
 */
class SleepInsightsAnalyzer(
    private val windowCalculator: SleepWindowCalculator = SleepWindowCalculator(),
) {
    companion object {
        const val MIN_NIGHTS_FOR_FACTORS = 14
        private const val DIFFICULT_SOL_MINUTES = 30L
        private const val DIFFICULT_EFFICIENCY = 0.80
        private const val WAKE_DEVIATION_MINUTES = 60L
        private const val BED_LINGER_MINUTES = 30L
        private const val MIN_EXPOSED_NIGHTS = 3
        private const val MIN_AFFECTED_NIGHTS = 2
        private const val MIN_RATE_LIFT = 0.25
        private const val TREND_DELTA = 0.03
    }

    fun analyze(
        entries: Collection<DiaryEntry>,
        currentWindow: SleepWindow? = null,
        profile: RestrictionProfile? = null,
        effectiveFrom: LocalDate? = null,
    ): SleepInsightsReport {
        val sorted = entries
            .filter(DiaryValidator::isSavable)
            .distinctBy { it.nightOf }
            .sortedBy { it.nightOf }

        val last7 = sorted.takeLast(7)
        val prev7 = if (sorted.size >= 14) sorted.dropLast(7).takeLast(7) else emptyList()

        val recentSummary = PeriodSummary.of(last7)
        val prevSummary = PeriodSummary.of(prev7)

        val trend = if (recentSummary != null && prevSummary != null) {
            val delta = recentSummary.sleepEfficiency - prevSummary.sleepEfficiency
            when {
                delta >= TREND_DELTA -> EfficiencyTrend.IMPROVING
                delta <= -TREND_DELTA -> EfficiencyTrend.DECLINING
                else -> EfficiencyTrend.STABLE
            }
        } else {
            null
        }

        val windowDecision = if (currentWindow != null && profile != null && effectiveFrom != null) {
            windowCalculator.weeklyAdjustment(currentWindow, last7, profile, effectiveFrom)
        } else {
            null
        }

        if (sorted.size < MIN_NIGHTS_FOR_FACTORS) {
            return SleepInsightsReport(
                analyzedNights = sorted.size,
                hasEnoughDataForFactors = false,
                recentEfficiency = recentSummary?.sleepEfficiency,
                previousEfficiency = prevSummary?.sleepEfficiency,
                efficiencyTrend = trend,
                factors = emptyList(),
                windowDecision = windowDecision,
            )
        }

        val windowNights = sorted.takeLast(60)
        val wakeMinutes = windowNights.map { it.finalWakeTime.toLocalTime().toSecondOfDay() / 60L }.sorted()
        val medianWakeMin = wakeMinutes[wakeMinutes.size / 2]

        fun isDifficult(e: DiaryEntry): Boolean {
            val m = NightMetrics.of(e)
            return e.sleepOnsetLatency.toMinutes() >= DIFFICULT_SOL_MINUTES || m.sleepEfficiency < DIFFICULT_EFFICIENCY
        }

        val observations = listOfNotNull(
            evaluateFactor(
                type = SleepFactorType.WAKE_VARIABILITY,
                nights = windowNights,
                isExposed = { e ->
                    val wakeMin = e.finalWakeTime.toLocalTime().toSecondOfDay() / 60L
                    val diff = abs(wakeMin - medianWakeMin)
                    minOf(diff, 1440L - diff) > WAKE_DEVIATION_MINUTES
                },
                isDifficult = ::isDifficult,
            ),
            evaluateFactor(
                type = SleepFactorType.PRE_SLEEP_AWAKE_IN_BED,
                nights = windowNights,
                isExposed = { e ->
                    Duration.between(e.bedTime, e.lightsOutTime).toMinutes() >= BED_LINGER_MINUTES
                },
                isDifficult = ::isDifficult,
            ),
            evaluateFactor(
                type = SleepFactorType.MORNING_LINGER_IN_BED,
                nights = windowNights,
                isExposed = { e ->
                    Duration.between(e.finalWakeTime, e.outOfBedTime).toMinutes() >= BED_LINGER_MINUTES
                },
                isDifficult = { e -> NightMetrics.of(e).sleepEfficiency < DIFFICULT_EFFICIENCY },
            ),
        )
            .sortedByDescending { it.exposedDifficultRate - it.baselineDifficultRate }
            .take(3)

        return SleepInsightsReport(
            analyzedNights = sorted.size,
            hasEnoughDataForFactors = true,
            recentEfficiency = recentSummary?.sleepEfficiency,
            previousEfficiency = prevSummary?.sleepEfficiency,
            efficiencyTrend = trend,
            factors = observations,
            windowDecision = windowDecision,
        )
    }

    private fun evaluateFactor(
        type: SleepFactorType,
        nights: List<DiaryEntry>,
        isExposed: (DiaryEntry) -> Boolean,
        isDifficult: (DiaryEntry) -> Boolean,
    ): SleepFactorObservation? {
        val exposed = nights.filter(isExposed)
        val unexposed = nights.filterNot(isExposed)
        if (exposed.size < MIN_EXPOSED_NIGHTS || unexposed.size < MIN_EXPOSED_NIGHTS) return null

        val exposedDifficult = exposed.count(isDifficult)
        if (exposedDifficult < MIN_AFFECTED_NIGHTS) return null

        val exposedRate = exposedDifficult.toDouble() / exposed.size
        val baselineRate = unexposed.count(isDifficult).toDouble() / unexposed.size
        if (exposedRate - baselineRate < MIN_RATE_LIFT) return null

        return SleepFactorObservation(
            type = type,
            affectedNights = exposedDifficult,
            exposedNights = exposed.size,
            exposedDifficultRate = exposedRate,
            baselineDifficultRate = baselineRate,
        )
    }
}

package fr.sommelle.feature.home

import fr.sommelle.core.model.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.sommelle.core.data.SommelleRepository
import fr.sommelle.core.model.Pathway
import fr.sommelle.core.presentation.Texts
import fr.sommelle.core.presentation.ProgramOutcome
import fr.sommelle.core.presentation.WeekBadge
import fr.sommelle.core.presentation.WindowStep
import fr.sommelle.core.presentation.RecentNightUi
import fr.sommelle.core.presentation.ProgramEngine
import fr.sommelle.core.presentation.nightsToFill
import fr.sommelle.core.presentation.recentNights
import fr.sommelle.core.presentation.weekBadge
import fr.sommelle.core.protocol.ProgramPlanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class HomeState(
    val date: String,
    val badge: WeekBadge,
    val week: Int,
    val pathway: Pathway,
    val step: WindowStep?,
    /** Nuits encore à remplir, la plus récente d'abord. */
    val nightsToFill: List<LocalDate>,
    val lastNight: LocalDate,
    /** Vrai quand il faut demander le ressenti de la journée : au début, puis à partir de la semaine 6. */
    val askDaytimeImpact: Boolean = false,
    val recentNights: List<RecentNightUi> = emptyList(),
    /** Date du bilan hebdomadaire en attente de la réponse sur la somnolence en journée, sinon null. */
    val pendingSleepinessCheck: LocalDate? = null,
)

class HomeViewModel(private val repository: SommelleRepository) : ViewModel() {

    private val planner = ProgramPlanner()
    private val engine = ProgramEngine()

    init {
        synchronize()
    }

    /** Enregistre les fenêtres dues (à l'initialisation ou sur action explicite). */
    fun synchronize() {
        viewModelScope.launch {
            repository.synchronize()
        }
    }

    val state: StateFlow<HomeState?> = combine(repository.snapshot.filterNotNull(), repository.daytimeImpacts) { snapshot, impacts ->
        val today = repository.today()
        val rawStep = engine.windowStep(
            today = today,
            startDate = snapshot.program.startDate,
            screening = snapshot.program.screening,
            entries = snapshot.entries,
            windows = snapshot.windows.map { it.window },
            wakeTime = snapshot.program.wakeTime,
            sleepinessChecks = snapshot.sleepinessChecks,
        )
        val step = when (rawStep) {
            is WindowStep.AwaitingSleepinessCheck -> WindowStep.Current(rawStep.current)
            is WindowStep.NewInitialWindow -> WindowStep.Current(rawStep.window)
            is WindowStep.NewWeeklyDecision -> WindowStep.Current(rawStep.decision.window)
            else -> rawStep
        }
        val programWeek = planner.programWeek(snapshot.program.startDate, today)
        val (badge, week) = weekBadge(programWeek)
        val endImpactDone = impacts.any { (planner.programWeek(snapshot.program.startDate, it.date) ?: 0) >= ProgramOutcome.AVAILABLE_FROM_WEEK }
        val inProgram = snapshot.program.screening.pathway != Pathway.OUT_OF_SCOPE
        val toFill = nightsToFill(today, snapshot.entries.map { it.nightOf }.toSet())
        HomeState(
            date = Texts.current().longDate(today),
            badge = badge,
            week = week,
            pathway = snapshot.program.screening.pathway,
            step = step,
            nightsToFill = toFill,
            lastNight = today,
            askDaytimeImpact = inProgram &&
                (impacts.isEmpty() || ((programWeek ?: 0) >= ProgramOutcome.AVAILABLE_FROM_WEEK && !endImpactDone)),
            recentNights = recentNights(today, snapshot.entries, toFill),
            pendingSleepinessCheck = (rawStep as? WindowStep.AwaitingSleepinessCheck)?.reviewDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Rappel du matin pour le journal, et du soir avant la fenêtre. */
    fun enableReminders() {
        viewModelScope.launch {
            repository.saveReminderSettings(repository.reminderSettingsOnce().copy(morningEnabled = true, eveningEnabled = true))
        }
    }

    /** Réponse à la question hebdomadaire : enregistre puis calcule le bilan de la semaine. */
    fun answerSleepiness(reviewDate: LocalDate, reported: Boolean) {
        viewModelScope.launch { repository.answerSleepinessCheck(reviewDate, reported) }
    }

    fun saveDaytimeImpact(score: Int) {
        viewModelScope.launch { repository.saveDaytimeImpact(score) }
    }
}

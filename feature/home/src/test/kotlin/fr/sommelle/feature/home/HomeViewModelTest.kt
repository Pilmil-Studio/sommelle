package fr.sommelle.feature.home

import fr.sommelle.core.model.*

import fr.sommelle.core.data.SommelleRepository
import fr.sommelle.core.data.db.DaytimeImpactEntity
import fr.sommelle.core.data.db.DiaryEntryEntity
import fr.sommelle.core.data.db.ModuleProgressEntity
import fr.sommelle.core.data.db.ProgramEntity
import fr.sommelle.core.data.db.SettingsEntity
import fr.sommelle.core.data.db.SleepWindowEntity
import fr.sommelle.core.data.db.SommelleDao
import fr.sommelle.core.data.toEntity
import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.Pathway
import fr.sommelle.core.model.ScreeningAnswers
import fr.sommelle.core.model.SleepQuality
import fr.sommelle.core.presentation.WindowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Clock
import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneOffset.UTC

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun clockAt(date: LocalDate): Clock =
        Clock.fixed(java.time.LocalDateTime.of(date.year, date.monthNumber, date.dayOfMonth, 9, 0).toInstant(zone), zone)

    private fun entry(nightOf: LocalDate): DiaryEntry = DiaryEntry(
        nightOf = nightOf,
        bedTime = nightOf.minusDays(1).atTime(23, 0),
        lightsOutTime = nightOf.minusDays(1).atTime(23, 15),
        sleepOnsetLatency = Duration.ofMinutes(20),
        awakeningCount = 1,
        wakeAfterSleepOnset = Duration.ofMinutes(15),
        finalWakeTime = nightOf.atTime(6, 45),
        outOfBedTime = nightOf.atTime(7, 0),
        quality = SleepQuality.GOOD,
    )

    @Test
    fun `state ne relance pas synchronize en boucle lors des emissions du snapshot`() = runTest {
        val start = LocalDate.of(2026, 10, 1)
        val today = start.plusDays(7)
        val dao = TrackingFakeDao()
        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(
                startDate = start,
                wakeTime = LocalTime.of(7, 0),
            ),
        )
        (0L..6L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }

        val repository = SommelleRepository(dao, clockAt(today))
        val viewModel = HomeViewModel(repository)

        val collectJob = backgroundScope.launch(testDispatcher) {
            viewModel.state.collect {}
        }

        val state = viewModel.state.filterNotNull().first()
        assertIs<WindowStep.Current>(state.step)
        assertEquals(Pathway.FULL, state.pathway)
        // L'initialisation synchronise une seule fois la fenêtre due (pas de boucle write -> emit -> cancel).
        assertEquals(1, dao.upsertWindowCalls)

        // Une nouvelle écriture de nuit ne déclenche pas de synchronisation implicite depuis state.
        repository.saveEntry(entry(today))
        assertEquals(1, dao.upsertWindowCalls)

        collectJob.cancel()
    }

    @Test
    fun `enableReminders et saveDaytimeImpact mettent a jour les donnees et askDaytimeImpact`() = runTest {
        val start = LocalDate.of(2026, 10, 1)
        val today = start.plusDays(2)
        val dao = TrackingFakeDao()
        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(
                startDate = start,
                wakeTime = null,
            ),
        )
        val repository = SommelleRepository(dao, clockAt(today))
        val viewModel = HomeViewModel(repository)

        val collectJob = backgroundScope.launch(testDispatcher) {
            viewModel.state.collect {}
        }

        val initial = viewModel.state.filterNotNull().first()
        assertTrue(initial.askDaytimeImpact)

        viewModel.saveDaytimeImpact(2)
        val afterImpact = viewModel.state.filterNotNull().first { !it.askDaytimeImpact }
        assertFalse(afterImpact.askDaytimeImpact)

        viewModel.enableReminders()
        val settings = repository.reminderSettingsOnce()
        assertTrue(settings.morningEnabled)
        assertTrue(settings.eveningEnabled)

        collectJob.cancel()
    }
}

private class TrackingFakeDao : SommelleDao {
    private val programFlow = MutableStateFlow<ProgramEntity?>(null)
    private val entriesFlow = MutableStateFlow<Map<LocalDate, DiaryEntryEntity>>(emptyMap())
    private val windowsFlow = MutableStateFlow<Map<LocalDate, SleepWindowEntity>>(emptyMap())
    private val settingsFlow = MutableStateFlow<SettingsEntity?>(null)
    private val modulesFlow = MutableStateFlow<Map<String, ModuleProgressEntity>>(emptyMap())
    private val impactsFlow = MutableStateFlow<Map<LocalDate, DaytimeImpactEntity>>(emptyMap())

    var upsertWindowCalls = 0
        private set

    override fun program(): Flow<ProgramEntity?> = programFlow
    override suspend fun programOnce(): ProgramEntity? = programFlow.value
    override suspend fun upsertProgram(program: ProgramEntity) {
        programFlow.value = program
    }

    override fun entries(): Flow<List<DiaryEntryEntity>> =
        entriesFlow.map { it.values.sortedBy { e -> e.nightOf } }

    override suspend fun entriesOnce(): List<DiaryEntryEntity> =
        entriesFlow.value.values.sortedBy { it.nightOf }

    override suspend fun entry(nightOf: LocalDate): DiaryEntryEntity? = entriesFlow.value[nightOf]

    override suspend fun entryBefore(nightOf: LocalDate): DiaryEntryEntity? =
        entriesFlow.value.values.filter { it.nightOf < nightOf }.maxByOrNull { it.nightOf }

    override suspend fun upsertEntry(entry: DiaryEntryEntity) {
        entriesFlow.value = entriesFlow.value + (entry.nightOf to entry)
    }

    override fun windows(): Flow<List<SleepWindowEntity>> =
        windowsFlow.map { it.values.sortedBy { w -> w.effectiveFrom } }

    override suspend fun windowsOnce(): List<SleepWindowEntity> =
        windowsFlow.value.values.sortedBy { it.effectiveFrom }

    override suspend fun upsertWindow(window: SleepWindowEntity) {
        upsertWindowCalls++
        windowsFlow.value = windowsFlow.value + (window.effectiveFrom to window)
    }

    override suspend fun deleteProgram() {
        programFlow.value = null
    }

    override suspend fun deleteEntries() {
        entriesFlow.value = emptyMap()
    }

    override suspend fun deleteWindows() {
        windowsFlow.value = emptyMap()
    }

    override fun settings(): Flow<SettingsEntity?> = settingsFlow
    override suspend fun settingsOnce(): SettingsEntity? = settingsFlow.value
    override suspend fun upsertSettings(settings: SettingsEntity) {
        settingsFlow.value = settings
    }

    override fun moduleProgress(): Flow<List<ModuleProgressEntity>> =
        modulesFlow.map { it.values.toList() }

    override suspend fun upsertModuleProgress(progress: ModuleProgressEntity) {
        modulesFlow.value = modulesFlow.value + (progress.module to progress)
    }

    override fun daytimeImpacts(): Flow<List<DaytimeImpactEntity>> =
        impactsFlow.map { it.values.sortedBy { i -> i.date } }

    override suspend fun daytimeImpactsOnce(): List<DaytimeImpactEntity> =
        impactsFlow.value.values.sortedBy { it.date }

    override suspend fun upsertDaytimeImpact(impact: DaytimeImpactEntity) {
        impactsFlow.value = impactsFlow.value + (impact.date to impact)
    }

    override suspend fun deleteSettings() {
        settingsFlow.value = null
    }

    override suspend fun deleteModuleProgress() {
        modulesFlow.value = emptyMap()
    }

    override suspend fun deleteDaytimeImpacts() {
        impactsFlow.value = emptyMap()
    }
}

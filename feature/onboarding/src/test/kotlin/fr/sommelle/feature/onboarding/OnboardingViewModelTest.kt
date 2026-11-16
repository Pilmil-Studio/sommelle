package fr.sommelle.feature.onboarding

import fr.sommelle.core.data.SommelleRepository
import fr.sommelle.core.data.db.DaytimeImpactEntity
import fr.sommelle.core.data.db.DiaryEntryEntity
import fr.sommelle.core.data.db.ModuleProgressEntity
import fr.sommelle.core.data.db.ProgramEntity
import fr.sommelle.core.data.db.SettingsEntity
import fr.sommelle.core.data.db.SleepWindowEntity
import fr.sommelle.core.data.db.SommelleDao
import fr.sommelle.core.data.db.WeeklyCheckEntity
import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.Pathway
import fr.sommelle.core.model.atTime
import fr.sommelle.core.model.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun clockAt(date: LocalDate): Clock = object : Clock {
        override fun now(): Instant = date.atTime(9, 0).toInstant(TimeZone.currentSystemDefault())
    }

    @Test
    fun `parcours complet propose les rappels et active matin et soir`() = runTest {
        val dao = FakeOnboardingDao()
        val repo = SommelleRepository(dao, clockAt(LocalDate.of(2026, 10, 1)))
        val vm = OnboardingViewModel(repo)

        assertIs<OnboardingState.Welcome>(vm.state.value)
        vm.start()
        assertIs<OnboardingState.Questions>(vm.state.value)

        // Retour depuis l'age ramene a Welcome.
        vm.back()
        assertIs<OnboardingState.Welcome>(vm.state.value)

        vm.start()
        vm.chooseAge(AgeBand.FROM_25_TO_44)
        while (true) {
            val questions = vm.state.value as? OnboardingState.Questions ?: break
            if (questions.flow.isMedicalStep) vm.answerMedical(emptySet()) else vm.answer(false)
        }

        val result = assertIs<OnboardingState.Result>(vm.state.value)
        assertEquals(Pathway.FULL, result.result.pathway)

        var done = false
        vm.confirm { done = true }
        assertEquals(false, done)
        assertIs<OnboardingState.Reminders>(vm.state.value)
        assertNotNull(dao.programOnce())

        vm.chooseReminders(enabled = true) { done = true }
        assertTrue(done)
        val settings = dao.settingsOnce()
        assertNotNull(settings)
        assertTrue(settings.morningReminder)
        assertTrue(settings.eveningReminder)
    }

    @Test
    fun `parcours hors champ termine directement sans etape rappels`() = runTest {
        val dao = FakeOnboardingDao()
        val repo = SommelleRepository(dao, clockAt(LocalDate.of(2026, 10, 1)))
        val vm = OnboardingViewModel(repo)

        vm.start()
        vm.chooseAge(AgeBand.UNDER_18)
        while (true) {
            val questions = vm.state.value as? OnboardingState.Questions ?: break
            if (questions.flow.isMedicalStep) vm.answerMedical(emptySet()) else vm.answer(false)
        }

        val result = assertIs<OnboardingState.Result>(vm.state.value)
        assertEquals(Pathway.OUT_OF_SCOPE, result.result.pathway)

        var done = false
        vm.confirm { done = true }
        assertTrue(done)
        assertNotNull(dao.programOnce())
    }
}

private class FakeOnboardingDao : SommelleDao {
    private val programFlow = MutableStateFlow<ProgramEntity?>(null)
    private val entriesFlow = MutableStateFlow<Map<LocalDate, DiaryEntryEntity>>(emptyMap())
    private val windowsFlow = MutableStateFlow<Map<LocalDate, SleepWindowEntity>>(emptyMap())
    private val settingsFlow = MutableStateFlow<SettingsEntity?>(null)
    private val modulesFlow = MutableStateFlow<Map<String, ModuleProgressEntity>>(emptyMap())
    private val impactsFlow = MutableStateFlow<Map<LocalDate, DaytimeImpactEntity>>(emptyMap())
    private val weeklyChecksFlow = MutableStateFlow<Map<LocalDate, WeeklyCheckEntity>>(emptyMap())

    override fun program(): Flow<ProgramEntity?> = programFlow
    override suspend fun programOnce(): ProgramEntity? = programFlow.value
    override suspend fun upsertProgram(program: ProgramEntity) { programFlow.value = program }
    override suspend fun deleteProgram() { programFlow.value = null }

    override fun entries(): Flow<List<DiaryEntryEntity>> = entriesFlow.map { it.values.sortedBy { e -> e.nightOf } }
    override suspend fun entriesOnce(): List<DiaryEntryEntity> = entriesFlow.value.values.sortedBy { it.nightOf }
    override suspend fun entry(nightOf: LocalDate): DiaryEntryEntity? = entriesFlow.value[nightOf]
    override suspend fun entryBefore(nightOf: LocalDate): DiaryEntryEntity? = null
    override suspend fun upsertEntry(entry: DiaryEntryEntity) { entriesFlow.value = entriesFlow.value + (entry.nightOf to entry) }
    override suspend fun deleteEntries() { entriesFlow.value = emptyMap() }

    override fun windows(): Flow<List<SleepWindowEntity>> = windowsFlow.map { it.values.sortedBy { w -> w.effectiveFrom } }
    override suspend fun windowsOnce(): List<SleepWindowEntity> = windowsFlow.value.values.sortedBy { it.effectiveFrom }
    override suspend fun upsertWindow(window: SleepWindowEntity) { windowsFlow.value = windowsFlow.value + (window.effectiveFrom to window) }
    override suspend fun deleteWindows() { windowsFlow.value = emptyMap() }

    override fun settings(): Flow<SettingsEntity?> = settingsFlow
    override suspend fun settingsOnce(): SettingsEntity? = settingsFlow.value
    override suspend fun upsertSettings(settings: SettingsEntity) { settingsFlow.value = settings }
    override suspend fun deleteSettings() { settingsFlow.value = null }

    override fun moduleProgress(): Flow<List<ModuleProgressEntity>> = modulesFlow.map { it.values.toList() }
    override suspend fun upsertModuleProgress(progress: ModuleProgressEntity) { modulesFlow.value = modulesFlow.value + (progress.module to progress) }
    override suspend fun deleteModuleProgress() { modulesFlow.value = emptyMap() }

    override fun daytimeImpacts(): Flow<List<DaytimeImpactEntity>> = impactsFlow.map { it.values.sortedBy { i -> i.date } }
    override suspend fun daytimeImpactsOnce(): List<DaytimeImpactEntity> = impactsFlow.value.values.sortedBy { it.date }
    override suspend fun upsertDaytimeImpact(impact: DaytimeImpactEntity) { impactsFlow.value = impactsFlow.value + (impact.date to impact) }
    override suspend fun deleteDaytimeImpacts() { impactsFlow.value = emptyMap() }

    override fun weeklyChecks(): Flow<List<WeeklyCheckEntity>> = weeklyChecksFlow.map { it.values.sortedBy { c -> c.reviewDate } }
    override suspend fun weeklyChecksOnce(): List<WeeklyCheckEntity> = weeklyChecksFlow.value.values.sortedBy { it.reviewDate }
    override suspend fun upsertWeeklyCheck(check: WeeklyCheckEntity) { weeklyChecksFlow.value = weeklyChecksFlow.value + (check.reviewDate to check) }
    override suspend fun deleteWeeklyChecks() { weeklyChecksFlow.value = emptyMap() }
}

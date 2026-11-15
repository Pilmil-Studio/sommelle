package fr.sommelle.feature.diary

import fr.sommelle.core.data.SommelleRepository
import fr.sommelle.core.data.db.DaytimeImpactEntity
import fr.sommelle.core.data.db.DiaryEntryEntity
import fr.sommelle.core.data.db.ModuleProgressEntity
import fr.sommelle.core.data.db.ProgramEntity
import fr.sommelle.core.data.db.SettingsEntity
import fr.sommelle.core.data.db.SleepWindowEntity
import fr.sommelle.core.data.db.SommelleDao
import fr.sommelle.core.data.db.WeeklyCheckEntity
import fr.sommelle.core.data.toEntity
import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.ScreeningAnswers
import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.SleepQuality
import fr.sommelle.core.model.atTime
import fr.sommelle.core.model.minusDays
import fr.sommelle.core.model.of
import fr.sommelle.core.model.plusDays
import fr.sommelle.core.presentation.DiaryStep
import fr.sommelle.core.presentation.TimeField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {

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

    private fun entry(nightOf: LocalDate): DiaryEntry = DiaryEntry(
        nightOf = nightOf,
        bedTime = nightOf.minusDays(1).atTime(23, 0),
        lightsOutTime = nightOf.minusDays(1).atTime(23, 15),
        sleepOnsetLatency = 20.minutes,
        awakeningCount = 1,
        wakeAfterSleepOnset = 15.minutes,
        finalWakeTime = nightOf.atTime(6, 45),
        outOfBedTime = nightOf.atTime(7, 0),
        quality = SleepQuality.GOOD,
    )

    @Test
    fun `mode initial est GUIDED sous 14 nuits puis SUMMARY a partir de 14 nuits ou edition`() = runTest {
        val today = LocalDate.of(2026, 10, 20)
        val dao = FakeDiaryDao()
        val repo = SommelleRepository(dao, clockAt(today))

        // L'agenda n'est lu qu'une fois le programme commencé.
        dao.upsertProgram(ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(today.minusDays(30), null))
        val vmGuided = DiaryViewModel(repo, today)
        assertEquals(DiaryMode.GUIDED, vmGuided.state.filterNotNull().first().mode)
        assertFalse(vmGuided.state.filterNotNull().first().canGoFaster)

        (1L..2L).forEach { dao.upsertEntry(entry(today.minusDays(it)).toEntity()) }
        dao.upsertEntry(entry(today.minusDays(3)).toEntity())
        val vmFaster = DiaryViewModel(repo, today)
        val fasterState = vmFaster.state.filterNotNull().first()
        assertEquals(DiaryMode.GUIDED, fasterState.mode)
        assertTrue(fasterState.canGoFaster, "« Aller plus vite » est proposé dès la 3e nuit saisie")
        vmFaster.goFaster()
        assertEquals(DiaryMode.SUMMARY, vmFaster.state.filterNotNull().first().mode)

        (4L..14L).forEach { dao.upsertEntry(entry(today.minusDays(it)).toEntity()) }
        val vmHabit = DiaryViewModel(repo, today)
        assertEquals(DiaryMode.SUMMARY, vmHabit.state.filterNotNull().first().mode)

        val existingDate = today.minusDays(1)
        val vmEdit = DiaryViewModel(repo, existingDate)
        assertEquals(DiaryMode.SUMMARY, vmEdit.state.filterNotNull().first().mode)
    }

    @Test
    fun `navigation guidee, editFromSummary et sauvegarde valide`() = runTest {
        val today = LocalDate.of(2026, 10, 20)
        val dao = FakeDiaryDao()
        val repo = SommelleRepository(dao, clockAt(today))
        val vm = DiaryViewModel(repo, today)

        vm.state.filterNotNull().first()
        assertFalse(vm.back())

        vm.next {}
        assertEquals(DiaryStep.LIGHTS_OUT, vm.state.value?.step)
        assertTrue(vm.back())
        assertEquals(DiaryStep.BED_TIME, vm.state.value?.step)

        vm.editFromSummary(DiaryStep.QUALITY)
        assertTrue(vm.state.value?.returnToSummary == true)
        vm.next {}
        assertEquals(DiaryMode.SUMMARY, vm.state.value?.mode)

        vm.guidedFromStart()
        assertEquals(DiaryMode.GUIDED, vm.state.value?.mode)
        assertEquals(DiaryStep.BED_TIME, vm.state.value?.step)

        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertNotNull(dao.entry(today))
    }

    @Test
    fun `save bloque sur coherence invalide puis se debloque apres correction`() = runTest {
        val today = LocalDate.of(2026, 10, 20)
        val dao = FakeDiaryDao()
        val repo = SommelleRepository(dao, clockAt(today))
        val vm = DiaryViewModel(repo, today)
        vm.state.filterNotNull().first()

        // Avance lightsOut apres outOfBed pour provoquer un probleme bloquant.
        vm.shift(TimeField.LIGHTS_OUT, 12 * 60)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertNotNull(vm.state.value?.blockingIssue)

        vm.dismissIssue()
        assertNull(vm.state.value?.blockingIssue)

        vm.shift(TimeField.LIGHTS_OUT, -12 * 60)
        vm.sleepOnset(15)
        vm.awakeningCount(1)
        vm.awakeMinutes(10)
        vm.quality(SleepQuality.GOOD)
        vm.save { saved = true }
        assertTrue(saved)
    }

    @Test
    fun `le brouillon survit au retour et disparait a l enregistrement`() = runTest {
        val today = LocalDate.of(2026, 10, 20)
        val dao = FakeDiaryDao()
        val repo = SommelleRepository(dao, object : kotlinx.datetime.Clock {
            override fun now() = today.atTime(9, 0).toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
        })
        val store = MemoryStore()

        val first = DiaryViewModel(repo, today, store)
        first.state.filterNotNull().first()
        first.quality(SleepQuality.VERY_GOOD)
        first.sleepOnset(45)

        // Retour à l'écran (ou processus tué) : un nouveau ViewModel relit le brouillon.
        val second = DiaryViewModel(repo, today, store).state.filterNotNull().first()
        assertEquals(SleepQuality.VERY_GOOD, second.draft.quality)
        assertEquals(45L, second.draft.sleepOnsetMinutes)

        var saved = false
        val vm = DiaryViewModel(repo, today, store)
        vm.state.filterNotNull().first()
        vm.save { saved = true }
        kotlinx.coroutines.yield()
        assertTrue(saved)
        assertEquals(null, store.load(today))
    }

}

private class MemoryStore : DraftStore {
    val values = mutableMapOf<LocalDate, String>()
    override fun load(nightOf: LocalDate) = values[nightOf]
    override fun save(nightOf: LocalDate, draft: String) { values[nightOf] = draft }
    override fun clear(nightOf: LocalDate) { values.remove(nightOf) }
}

private class FakeDiaryDao : SommelleDao {
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
    override suspend fun entryBefore(nightOf: LocalDate): DiaryEntryEntity? =
        entriesFlow.value.values.filter { it.nightOf < nightOf }.maxByOrNull { it.nightOf }
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

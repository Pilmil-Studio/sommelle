package fr.sommelle.feature.week

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
import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.ScreeningAnswers
import fr.sommelle.core.model.SleepQuality
import fr.sommelle.core.model.atTime
import fr.sommelle.core.model.minusDays
import fr.sommelle.core.model.of
import fr.sommelle.core.model.plusDays
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModelTest {

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
    fun `expose la fenetre active et garde outcome vide avant la semaine 6`() = runTest {
        val start = LocalDate.of(2026, 10, 1)
        val today = start.plusDays(10)
        val dao = FakeWeekDao()
        dao.upsertProgram(ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(start, LocalTime.of(7, 0)))
        (0L..9L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }
        dao.upsertWindow(SleepWindowEntity(start.plusDays(7), LocalTime.of(23, 30), LocalTime.of(7, 0), 450L, 1, null))

        val vm = WeekViewModel(SommelleRepository(dao, clockAt(today)))
        val job = backgroundScope.launch(testDispatcher) { vm.state.collect {} }

        val state = vm.state.filterNotNull().first()
        assertTrue(state.restriction)
        assertNotNull(state.window)
        assertNotNull(state.bedTime)
        assertNotNull(state.wakeTime)
        assertTrue(state.bars.isNotEmpty())
        assertTrue(state.outcome.isEmpty())

        job.cancel()
    }

    @Test
    fun `calcule outcome a partir de la semaine 6`() = runTest {
        val start = LocalDate.of(2026, 9, 1)
        val today = start.plusDays(49) // semaine 6 du programme : 7 jours d'observation puis 6 semaines
        val dao = FakeWeekDao()
        dao.upsertProgram(ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(start, LocalTime.of(7, 0)))
        (0L..48L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }
        dao.upsertWindow(SleepWindowEntity(start.plusDays(7), LocalTime.of(23, 30), LocalTime.of(7, 0), 450L, 1, null))

        val vm = WeekViewModel(SommelleRepository(dao, clockAt(today)))
        val job = backgroundScope.launch(testDispatcher) { vm.state.collect {} }

        val state = vm.state.filterNotNull().first()
        assertTrue(state.outcome.isNotEmpty())
        assertEquals(2, state.bars.size.coerceAtLeast(2))

        job.cancel()
    }
}

private class FakeWeekDao : SommelleDao {
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

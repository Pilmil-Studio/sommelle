package fr.sommelle.core.data

import fr.sommelle.core.model.*

import fr.sommelle.core.data.db.DaytimeImpactEntity
import fr.sommelle.core.data.db.DiaryEntryEntity
import fr.sommelle.core.data.db.ModuleProgressEntity
import fr.sommelle.core.data.db.ProgramEntity
import fr.sommelle.core.data.db.SettingsEntity
import fr.sommelle.core.data.db.SleepWindowEntity
import fr.sommelle.core.data.db.SommelleDao
import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.ScreeningAnswers
import fr.sommelle.core.model.SleepQuality
import fr.sommelle.core.presentation.DiaryCsv
import fr.sommelle.core.presentation.WindowStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import java.time.Clock
import kotlin.time.Duration
import java.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SommelleRepositoryTest {

    private val zone = ZoneOffset.UTC

    private fun clockAt(date: LocalDate): Clock =
        Clock.fixed(date.atTime(9, 0).toJavaLocalDateTime().toInstant(zone), zone)

    private fun entry(nightOf: LocalDate): DiaryEntry = DiaryEntry(
        nightOf = nightOf,
        bedTime = nightOf.minusDays(1).atTime(23, 0),
        lightsOutTime = nightOf.minusDays(1).atTime(23, 15),
        sleepOnsetLatency = Duration.ofMinutes(25),
        awakeningCount = 1,
        wakeAfterSleepOnset = Duration.ofMinutes(20),
        finalWakeTime = nightOf.atTime(6, 45),
        outOfBedTime = nightOf.atTime(7, 0),
        quality = SleepQuality.FAIR,
    )

    @Test
    fun `importCsv utilise une transaction batch et synchronise les fenetres`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        val today = start.plusDays(7)
        val repo = SommelleRepository(dao, clockAt(today))
        repo.startProgram(ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44))
        // Reculer startDate à J-7 avec une heure de lever choisie
        dao.upsertProgram(dao.programOnce()!!.copy(startDate = start, wakeTime = LocalTime.of(7, 0)))

        val entries = (1L..7L).map { entry(start.plusDays(it)) }
        val csv = DiaryCsv.export(entries)

        val count = repo.importCsv(csv)

        assertEquals(7, count)
        assertEquals(1, dao.batchUpsertCalls)
        assertEquals(7, dao.entriesOnce().size)
        assertEquals(1, dao.windowsOnce().size)
    }

    @Test
    fun `importCsv vide ne declenche ni upsert ni synchronisation`() = runTest {
        val dao = FakeSommelleDao()
        val repo = SommelleRepository(dao, clockAt(LocalDate.of(2026, 10, 8)))

        val count = repo.importCsv("")

        assertEquals(0, count)
        assertEquals(0, dao.batchUpsertCalls)
    }

    @Test
    fun `synchronize rattrape la fenetre initiale et les ajustements hebdomadaires`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(
                startDate = start,
                wakeTime = LocalTime.of(7, 0),
            ),
        )
        (0L..14L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }

        SommelleRepository(dao, clockAt(start.plusDays(7))).synchronize()
        val step = SommelleRepository(dao, clockAt(start.plusDays(15))).synchronize()

        assertIs<WindowStep.Current>(step)
        assertEquals(2, dao.windowsOnce().size)
    }

    @Test
    fun `baselineTransitionInfo calcule la duree prescrite et l heure de lever suggeree`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        val repo = SommelleRepository(dao, clockAt(start.plusDays(7)))
        assertNull(repo.baselineTransitionInfo())

        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(
                startDate = start,
                wakeTime = null,
            ),
        )
        (0L..6L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }

        val info = repo.baselineTransitionInfo()
        assertNotNull(info)
        assertEquals(LocalTime.of(6, 45), info.suggestedWakeTime)
        assertTrue(info.prescribedDuration >= Duration.ofHours(5))
    }

    @Test
    fun `eraseEverything supprime toutes les tables`() = runTest {
        val dao = FakeSommelleDao()
        val today = LocalDate.of(2026, 10, 10)
        val repo = SommelleRepository(dao, clockAt(today))
        repo.startProgram(ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44))
        repo.saveEntry(entry(today))
        repo.saveDaytimeImpact(3)

        repo.eraseEverything()

        assertNull(repo.snapshot.first())
        assertTrue(repo.daytimeImpactsOnce().isEmpty())
    }
}

private class FakeSommelleDao : SommelleDao {
    private val programFlow = MutableStateFlow<ProgramEntity?>(null)
    private val entriesFlow = MutableStateFlow<Map<LocalDate, DiaryEntryEntity>>(emptyMap())
    private val windowsFlow = MutableStateFlow<Map<LocalDate, SleepWindowEntity>>(emptyMap())
    private val settingsFlow = MutableStateFlow<SettingsEntity?>(null)
    private val modulesFlow = MutableStateFlow<Map<String, ModuleProgressEntity>>(emptyMap())
    private val impactsFlow = MutableStateFlow<Map<LocalDate, DaytimeImpactEntity>>(emptyMap())

    var batchUpsertCalls = 0
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

    override suspend fun upsertEntries(entries: List<DiaryEntryEntity>) {
        batchUpsertCalls++
        entriesFlow.value = entriesFlow.value + entries.associateBy { it.nightOf }
    }

    override fun windows(): Flow<List<SleepWindowEntity>> =
        windowsFlow.map { it.values.sortedBy { w -> w.effectiveFrom } }

    override suspend fun windowsOnce(): List<SleepWindowEntity> =
        windowsFlow.value.values.sortedBy { it.effectiveFrom }

    override suspend fun upsertWindow(window: SleepWindowEntity) {
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

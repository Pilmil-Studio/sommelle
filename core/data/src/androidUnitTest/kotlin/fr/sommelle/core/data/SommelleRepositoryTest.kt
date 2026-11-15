package fr.sommelle.core.data

import fr.sommelle.core.model.*

import fr.sommelle.core.data.db.DaytimeImpactEntity
import fr.sommelle.core.data.db.DiaryEntryEntity
import fr.sommelle.core.data.db.ModuleProgressEntity
import fr.sommelle.core.data.db.ProgramEntity
import fr.sommelle.core.data.db.SettingsEntity
import fr.sommelle.core.data.db.SleepWindowEntity
import fr.sommelle.core.data.db.SommelleDao
import fr.sommelle.core.data.db.WeeklyCheckEntity
import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.DiaryEntry
import fr.sommelle.core.model.ScreeningAnswers
import fr.sommelle.core.model.SleepQuality
import fr.sommelle.core.data.backup.InvalidBackupException
import fr.sommelle.core.data.backup.SommelleBackup
import fr.sommelle.core.model.PrefillSource
import fr.sommelle.core.presentation.CognitiveModule
import fr.sommelle.core.presentation.DiaryCsv
import fr.sommelle.core.presentation.WindowStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SommelleRepositoryTest {

    private fun clockAt(date: LocalDate): Clock = object : Clock {
        override fun now(): Instant = date.atTime(9, 0).toInstant(TimeZone.currentSystemDefault())
    }

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
        dao.upsertWeeklyCheck(WeeklyCheckEntity(reviewDate = start.plusDays(14), sleepinessReported = false))
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


    // Bilan hebdomadaire : question sur la somnolence

    private suspend fun programWithWeek(dao: FakeSommelleDao, start: LocalDate) {
        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(startDate = start, wakeTime = LocalTime.of(7, 0)),
        )
        // Ligne de base correcte (fenêtre de 6 h 45), puis une semaine de mauvais sommeil qui appellerait une réduction.
        (0L..6L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }
        (7L..13L).forEach {
            dao.upsertEntry(entry(start.plusDays(it)).copy(wakeAfterSleepOnset = Duration.ofMinutes(90)).toEntity())
        }
    }

    @Test
    fun `le bilan hebdomadaire attend la reponse sur la somnolence`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        programWithWeek(dao, start)
        SommelleRepository(dao, clockAt(start.plusDays(7))).synchronize()

        val step = SommelleRepository(dao, clockAt(start.plusDays(14))).synchronize()

        assertEquals(WindowStep.AwaitingSleepinessCheck::class, step!!::class)
        assertEquals(1, dao.windowsOnce().size)
    }

    @Test
    fun `somnolence declaree la fenetre n est pas reduite et la reponse est enregistree`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        programWithWeek(dao, start)
        SommelleRepository(dao, clockAt(start.plusDays(7))).synchronize()
        val repo = SommelleRepository(dao, clockAt(start.plusDays(14)))
        val before = dao.windowsOnce().single().durationMinutes

        repo.answerSleepinessCheck(start.plusDays(14), sleepinessReported = true)

        val windows = dao.windowsOnce()
        assertEquals(2, windows.size)
        assertEquals(before, windows.last().durationMinutes)
        assertEquals("UNCHANGED_SLEEPINESS", windows.last().change)
        assertEquals(true, dao.weeklyChecksOnce().single().sleepinessReported)
        val snapshot = repo.snapshot.first()!!
        assertTrue(snapshot.windows.last().sleepinessReported)
    }

    @Test
    fun `sans somnolence la meme semaine reduit la fenetre`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        programWithWeek(dao, start)
        SommelleRepository(dao, clockAt(start.plusDays(7))).synchronize()
        val before = dao.windowsOnce().single().durationMinutes

        SommelleRepository(dao, clockAt(start.plusDays(14))).answerSleepinessCheck(start.plusDays(14), sleepinessReported = false)

        val last = dao.windowsOnce().last()
        assertEquals("DECREASED", last.change)
        assertEquals(before - 15, last.durationMinutes)
    }

    @Test
    fun `une fenetre existante de 5 h 30 est relevee a 6 h au prochain bilan`() = runTest {
        val dao = FakeSommelleDao()
        val start = LocalDate.of(2026, 10, 1)
        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44).toEntity(startDate = start, wakeTime = LocalTime.of(7, 0)),
        )
        (0L..13L).forEach { dao.upsertEntry(entry(start.plusDays(it)).toEntity()) }
        // Fenêtre créée avant le changement de plancher (protocole 1, 5 h 30).
        dao.upsertWindow(SleepWindowEntity(start.plusDays(7), LocalTime.of(1, 30), LocalTime.of(7, 0), 330, 1, null))
        dao.upsertWeeklyCheck(WeeklyCheckEntity(start.plusDays(14), sleepinessReported = false))

        SommelleRepository(dao, clockAt(start.plusDays(14))).synchronize()

        val raised = dao.windowsOnce().last()
        assertEquals(360, raised.durationMinutes)
        assertEquals(LocalTime.of(1, 0), raised.bedTime)
        assertEquals(1, dao.windowsOnce().first().protocolVersion)
    }

    // Sauvegarde complète

    private suspend fun fillAllTables(dao: FakeSommelleDao) {
        val start = LocalDate.of(2026, 10, 1)
        dao.upsertProgram(
            ScreeningAnswers(ageBand = AgeBand.FROM_45_TO_64, expressedDistress = true).toEntity(startDate = start, wakeTime = LocalTime.of(6, 45)),
        )
        dao.upsertEntry(entry(start.plusDays(1)).toEntity())
        dao.upsertEntry(entry(start.plusDays(2)).copy(notes = "Nuit \"agitée\"\nsecond ligne", prefillSource = PrefillSource.PREVIOUS_NIGHT).toEntity())
        dao.upsertWindow(SleepWindowEntity(start.plusDays(7), LocalTime.of(0, 15), LocalTime.of(6, 45), 390, 2, null))
        dao.upsertWindow(SleepWindowEntity(start.plusDays(14), LocalTime.of(0, 0), LocalTime.of(6, 45), 405, 2, "INCREASED"))
        dao.upsertSettings(SettingsEntity(morningReminder = true, morningTime = LocalTime.of(7, 5), eveningReminder = false))
        dao.upsertModuleProgress(ModuleProgressEntity(CognitiveModule.entries[0].name, start.plusDays(10)))
        dao.upsertModuleProgress(ModuleProgressEntity(CognitiveModule.entries[1].name, start.plusDays(12)))
        dao.upsertDaytimeImpact(DaytimeImpactEntity(start, 3))
        dao.upsertDaytimeImpact(DaytimeImpactEntity(start.plusDays(40), 1))
        dao.upsertWeeklyCheck(WeeklyCheckEntity(start.plusDays(14), true))
        dao.upsertWeeklyCheck(WeeklyCheckEntity(start.plusDays(21), false))
    }

    @Test
    fun `sauvegarde complete aller-retour retrouve toutes les tables`() = runTest {
        val source = FakeSommelleDao()
        fillAllTables(source)
        val json = SommelleRepository(source, clockAt(LocalDate.of(2026, 11, 15)))
            .exportBackup(mapOf("nightMode" to "ALWAYS_NIGHT", "nightStartHour" to "22"))

        assertTrue(json.contains("\"format\":\"sommelle-backup\""))
        assertTrue(json.contains("\"version\":1"))

        val target = FakeSommelleDao()
        target.upsertEntry(entry(LocalDate.of(2025, 1, 5)).toEntity()) // donnée à remplacer
        val repo = SommelleRepository(target, clockAt(LocalDate.of(2026, 11, 15)))
        val data = SommelleBackup.decode(json)
        repo.restoreBackup(data)

        assertEquals(source.programOnce(), target.programOnce())
        assertEquals(source.entriesOnce(), target.entriesOnce())
        assertEquals(2, target.entriesOnce().size, "la nuit préexistante est remplacée")
        assertEquals(source.windowsOnce(), target.windowsOnce())
        assertEquals(source.settingsOnce(), target.settingsOnce())
        assertEquals(source.moduleProgress().first().toSet(), target.moduleProgress().first().toSet())
        assertEquals(source.daytimeImpactsOnce(), target.daytimeImpactsOnce())
        assertEquals(source.weeklyChecksOnce(), target.weeklyChecksOnce())
        assertEquals(mapOf("nightMode" to "ALWAYS_NIGHT", "nightStartHour" to "22"), data.preferences)
        assertEquals("Nuit \"agitée\"\nsecond ligne", target.entriesOnce().last().notes)
    }

    @Test
    fun `sauvegarde d une base vide aller-retour`() = runTest {
        val json = SommelleRepository(FakeSommelleDao(), clockAt(LocalDate.of(2026, 11, 15))).exportBackup()
        val data = SommelleBackup.decode(json)
        assertNull(data.program)
        assertTrue(data.diaryEntries.isEmpty())
    }

    @Test
    fun `version inconnue refusee sans rien modifier`() = runTest {
        val dao = FakeSommelleDao()
        fillAllTables(dao)
        val repo = SommelleRepository(dao, clockAt(LocalDate.of(2026, 11, 15)))
        val json = repo.exportBackup().replace("\"version\":1", "\"version\":2")
        val entriesBefore = dao.entriesOnce()

        val refused = assertFailsWith<InvalidBackupException> { SommelleBackup.decode(json) }

        assertEquals(InvalidBackupException.Reason.UNSUPPORTED_VERSION, refused.reason)
        assertEquals(entriesBefore, dao.entriesOnce())
    }

    @Test
    fun `document d un autre format refuse`() {
        val refused = assertFailsWith<InvalidBackupException> { SommelleBackup.decode("""{"app":"lunaison","version":1}""") }
        assertEquals(InvalidBackupException.Reason.NOT_A_BACKUP, refused.reason)
    }

    @Test
    fun `json tronque refuse sans modification des donnees`() = runTest {
        val dao = FakeSommelleDao()
        fillAllTables(dao)
        val repo = SommelleRepository(dao, clockAt(LocalDate.of(2026, 11, 15)))
        val json = repo.exportBackup()
        val before = listOf(dao.programOnce(), dao.entriesOnce(), dao.windowsOnce(), dao.weeklyChecksOnce(), dao.settingsOnce())

        // Tronqué en plusieurs endroits : au milieu d'un texte, d'une liste, juste avant la fin.
        listOf(json.length / 3, json.length / 2, json.length - 1).forEach { cut ->
            val refused = assertFailsWith<InvalidBackupException> { SommelleBackup.decode(json.take(cut)) }
            assertEquals(InvalidBackupException.Reason.CORRUPTED, refused.reason)
        }
        assertEquals(before, listOf(dao.programOnce(), dao.entriesOnce(), dao.windowsOnce(), dao.weeklyChecksOnce(), dao.settingsOnce()))
    }

    @Test
    fun `valeurs impossibles refusees`() = runTest {
        val dao = FakeSommelleDao()
        fillAllTables(dao)
        val json = SommelleRepository(dao, clockAt(LocalDate.of(2026, 11, 15))).exportBackup()
        listOf(
            json.replace("FROM_45_TO_64", "FROM_100"),
            json.replace("\"quality\":\"FAIR\"", "\"quality\":\"EXCELLENT\""),
            json.replace("\"startDate\":\"2026-10-01\"", "\"startDate\":\"pas-une-date\""),
            json.replace("\"score\":3", "\"score\":9"),
            json.replace("\"diaryEntries\":[", "\"diaryEntries\":{\"x\":["),
        ).forEach { bad ->
            val refused = assertFailsWith<InvalidBackupException> { SommelleBackup.decode(bad) }
            assertEquals(InvalidBackupException.Reason.CORRUPTED, refused.reason)
        }
    }

    @Test
    fun `eraseEverything supprime toutes les tables`() = runTest {
        val dao = FakeSommelleDao()
        val today = LocalDate.of(2026, 10, 10)
        val repo = SommelleRepository(dao, clockAt(today))
        repo.startProgram(ScreeningAnswers(ageBand = AgeBand.FROM_25_TO_44))
        repo.saveEntry(entry(today))
        repo.saveDaytimeImpact(3)
        dao.upsertWeeklyCheck(WeeklyCheckEntity(today, true))

        repo.eraseEverything()

        assertTrue(dao.weeklyChecksOnce().isEmpty())
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
    private val weeklyChecksFlow = MutableStateFlow<Map<LocalDate, WeeklyCheckEntity>>(emptyMap())

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

    override fun weeklyChecks(): Flow<List<WeeklyCheckEntity>> =
        weeklyChecksFlow.map { it.values.sortedBy { c -> c.reviewDate } }

    override suspend fun weeklyChecksOnce(): List<WeeklyCheckEntity> =
        weeklyChecksFlow.value.values.sortedBy { it.reviewDate }

    override suspend fun upsertWeeklyCheck(check: WeeklyCheckEntity) {
        weeklyChecksFlow.value = weeklyChecksFlow.value + (check.reviewDate to check)
    }

    override suspend fun deleteWeeklyChecks() {
        weeklyChecksFlow.value = emptyMap()
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

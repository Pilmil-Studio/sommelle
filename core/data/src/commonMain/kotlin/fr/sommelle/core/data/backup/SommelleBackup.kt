package fr.sommelle.core.data.backup

import fr.sommelle.core.data.db.DaytimeImpactEntity
import fr.sommelle.core.data.db.DiaryEntryEntity
import fr.sommelle.core.data.db.ModuleProgressEntity
import fr.sommelle.core.data.db.ProgramEntity
import fr.sommelle.core.data.db.SettingsEntity
import fr.sommelle.core.data.db.SleepWindowEntity
import fr.sommelle.core.data.db.WeeklyCheckEntity
import fr.sommelle.core.model.AgeBand
import fr.sommelle.core.model.PrefillSource
import fr.sommelle.core.model.SleepQuality
import fr.sommelle.core.presentation.CognitiveModule
import fr.sommelle.core.protocol.WindowChange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Refus d'un document de sauvegarde : la base n'est alors jamais touchée. */
class InvalidBackupException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        /** Ce n'est pas un document Sommelle. */
        NOT_A_BACKUP,
        /** Document tronqué, mal formé ou aux valeurs impossibles. */
        CORRUPTED,
        /** Version du document inconnue de cette appli (sauvegarde plus récente, par exemple). */
        UNSUPPORTED_VERSION,
    }
}

/** Tout le contenu de la base, plus les préférences utiles (apparence). */
data class BackupData(
    val program: ProgramEntity?,
    val diaryEntries: List<DiaryEntryEntity>,
    val sleepWindows: List<SleepWindowEntity>,
    val settings: SettingsEntity?,
    val moduleProgress: List<ModuleProgressEntity>,
    val daytimeImpacts: List<DaytimeImpactEntity>,
    val weeklyChecks: List<WeeklyCheckEntity>,
    /** Préférences de l'appli hors base (thème de nuit), en clé / valeur texte. */
    val preferences: Map<String, String> = emptyMap(),
)

/**
 * Document JSON versionné de la sauvegarde complète, avant chiffrement par le coffre `.pilmil`.
 * Les dates sont au format ISO 8601, comme dans la base.
 */
object SommelleBackup {
    const val FORMAT = "sommelle-backup"
    const val VERSION = 1

    fun encode(data: BackupData, exportedAt: String): String = MiniJson.write(
        linkedMapOf(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to exportedAt,
            "program" to data.program?.let(::programToJson),
            "diaryEntries" to data.diaryEntries.map(::entryToJson),
            "sleepWindows" to data.sleepWindows.map(::windowToJson),
            "settings" to data.settings?.let(::settingsToJson),
            "moduleProgress" to data.moduleProgress.map { mapOf("module" to it.module, "completedOn" to it.completedOn.toString()) },
            "daytimeImpacts" to data.daytimeImpacts.map { mapOf("date" to it.date.toString(), "score" to it.score) },
            "weeklyChecks" to data.weeklyChecks.map { mapOf("reviewDate" to it.reviewDate.toString(), "sleepinessReported" to it.sleepinessReported) },
            "preferences" to data.preferences,
        ),
    )

    /** @throws InvalidBackupException si le document n'est pas une sauvegarde Sommelle lisible dans son intégralité. */
    fun decode(json: String): BackupData {
        val root = try {
            MiniJson.parse(json) as? Map<*, *>
        } catch (e: MiniJson.ParseException) {
            throw corrupted("JSON illisible : ${e.message}")
        } ?: throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, "document inattendu")
        if (root["format"] != FORMAT) {
            throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, "format inconnu")
        }
        val version = root["version"]
        if (version != VERSION.toLong()) {
            throw InvalidBackupException(InvalidBackupException.Reason.UNSUPPORTED_VERSION, "version $version non prise en charge")
        }
        return try {
            BackupData(
                program = (root["program"] as Map<*, *>?)?.let(::programFromJson),
                diaryEntries = list(root, "diaryEntries").map(::entryFromJson),
                sleepWindows = list(root, "sleepWindows").map(::windowFromJson),
                settings = (root["settings"] as Map<*, *>?)?.let(::settingsFromJson),
                moduleProgress = list(root, "moduleProgress").map {
                    val module = it.str("module")
                    require(CognitiveModule.entries.any { m -> m.name == module }) { "module inconnu" }
                    ModuleProgressEntity(module, it.date("completedOn"))
                },
                daytimeImpacts = list(root, "daytimeImpacts").map {
                    val score = it.long("score").toInt()
                    require(score in 0..4) { "score hors échelle" }
                    DaytimeImpactEntity(it.date("date"), score)
                },
                weeklyChecks = list(root, "weeklyChecks").map { WeeklyCheckEntity(it.date("reviewDate"), it.bool("sleepinessReported")) },
                preferences = (root["preferences"] as? Map<*, *>)?.entries?.associate { (k, v) -> k as String to v as String }.orEmpty(),
            )
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: Exception) {
            // Valeur manquante, mauvais type, date ou énumération invalide : rien n'est restauré.
            throw corrupted(e.message ?: "valeur invalide")
        }
    }

    private fun corrupted(message: String) = InvalidBackupException(InvalidBackupException.Reason.CORRUPTED, message)

    private fun list(root: Map<*, *>, key: String): List<Map<*, *>> =
        (root[key] as? List<*>)?.map { it as? Map<*, *> ?: error("$key : élément invalide") } ?: error("$key manquant")

    // Lecture typée : toute absence ou tout mauvais type lève une exception, convertie en document corrompu.
    private fun Map<*, *>.str(key: String): String = this[key] as? String ?: error("$key manquant ou invalide")
    private fun Map<*, *>.long(key: String): Long = this[key] as? Long ?: error("$key manquant ou invalide")
    private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: error("$key manquant ou invalide")
    private fun Map<*, *>.date(key: String): LocalDate = LocalDate.parse(str(key))
    private fun Map<*, *>.dateTime(key: String): LocalDateTime = LocalDateTime.parse(str(key))
    private fun Map<*, *>.time(key: String): LocalTime = LocalTime.parse(str(key))
    private fun Map<*, *>.optStr(key: String): String? = if (this[key] == null) null else str(key)

    private fun programToJson(p: ProgramEntity) = linkedMapOf(
        "startDate" to p.startDate.toString(),
        "wakeTime" to p.wakeTime?.toString(),
        "ageBand" to p.ageBand,
        "loudSnoringWithBreathingPauses" to p.loudSnoringWithBreathingPauses,
        "markedDaytimeSleepiness" to p.markedDaytimeSleepiness,
        "bipolarDisorder" to p.bipolarDisorder,
        "epilepsy" to p.epilepsy,
        "pregnancy" to p.pregnancy,
        "safetyCriticalActivity" to p.safetyCriticalActivity,
        "shiftOrNightWork" to p.shiftOrNightWork,
        "externalNightDisruption" to p.externalNightDisruption,
        "lateScheduleSleepsWellOnceAsleep" to p.lateScheduleSleepsWellOnceAsleep,
        "expressedDistress" to p.expressedDistress,
    )

    private fun programFromJson(m: Map<*, *>): ProgramEntity {
        val ageBand = m.str("ageBand")
        require(AgeBand.entries.any { it.name == ageBand }) { "tranche d'âge inconnue" }
        return ProgramEntity(
            startDate = m.date("startDate"),
            wakeTime = if (m["wakeTime"] == null) null else m.time("wakeTime"),
            ageBand = ageBand,
            loudSnoringWithBreathingPauses = m.bool("loudSnoringWithBreathingPauses"),
            markedDaytimeSleepiness = m.bool("markedDaytimeSleepiness"),
            bipolarDisorder = m.bool("bipolarDisorder"),
            epilepsy = m.bool("epilepsy"),
            pregnancy = m.bool("pregnancy"),
            safetyCriticalActivity = m.bool("safetyCriticalActivity"),
            shiftOrNightWork = m.bool("shiftOrNightWork"),
            externalNightDisruption = m.bool("externalNightDisruption"),
            lateScheduleSleepsWellOnceAsleep = m.bool("lateScheduleSleepsWellOnceAsleep"),
            expressedDistress = m.bool("expressedDistress"),
        )
    }

    private fun entryToJson(e: DiaryEntryEntity) = linkedMapOf(
        "nightOf" to e.nightOf.toString(),
        "bedTime" to e.bedTime.toString(),
        "lightsOutTime" to e.lightsOutTime.toString(),
        "sleepOnsetMinutes" to e.sleepOnsetMinutes,
        "awakeningCount" to e.awakeningCount,
        "awakeMinutes" to e.awakeMinutes,
        "finalWakeTime" to e.finalWakeTime.toString(),
        "outOfBedTime" to e.outOfBedTime.toString(),
        "quality" to e.quality,
        "notes" to e.notes,
        "prefillSource" to e.prefillSource,
    )

    private fun entryFromJson(m: Map<*, *>): DiaryEntryEntity {
        val quality = m.str("quality")
        require(SleepQuality.entries.any { it.name == quality }) { "qualité inconnue" }
        val prefill = m.optStr("prefillSource")
        require(prefill == null || PrefillSource.entries.any { it.name == prefill }) { "origine inconnue" }
        return DiaryEntryEntity(
            nightOf = m.date("nightOf"),
            bedTime = m.dateTime("bedTime"),
            lightsOutTime = m.dateTime("lightsOutTime"),
            sleepOnsetMinutes = m.long("sleepOnsetMinutes"),
            awakeningCount = m.long("awakeningCount").toInt(),
            awakeMinutes = m.long("awakeMinutes"),
            finalWakeTime = m.dateTime("finalWakeTime"),
            outOfBedTime = m.dateTime("outOfBedTime"),
            quality = quality,
            notes = m.str("notes"),
            prefillSource = prefill,
        )
    }

    private fun windowToJson(w: SleepWindowEntity) = linkedMapOf(
        "effectiveFrom" to w.effectiveFrom.toString(),
        "bedTime" to w.bedTime.toString(),
        "wakeTime" to w.wakeTime.toString(),
        "durationMinutes" to w.durationMinutes,
        "protocolVersion" to w.protocolVersion,
        "change" to w.change,
    )

    private fun windowFromJson(m: Map<*, *>): SleepWindowEntity {
        val change = m.optStr("change")
        require(change == null || WindowChange.entries.any { it.name == change }) { "décision inconnue" }
        return SleepWindowEntity(
            effectiveFrom = m.date("effectiveFrom"),
            bedTime = m.time("bedTime"),
            wakeTime = m.time("wakeTime"),
            durationMinutes = m.long("durationMinutes"),
            protocolVersion = m.long("protocolVersion").toInt(),
            change = change,
        )
    }

    private fun settingsToJson(s: SettingsEntity) = linkedMapOf(
        "morningReminder" to s.morningReminder,
        "morningTime" to s.morningTime?.toString(),
        "eveningReminder" to s.eveningReminder,
    )

    private fun settingsFromJson(m: Map<*, *>) = SettingsEntity(
        morningReminder = m.bool("morningReminder"),
        morningTime = if (m["morningTime"] == null) null else m.time("morningTime"),
        eveningReminder = m.bool("eveningReminder"),
    )
}

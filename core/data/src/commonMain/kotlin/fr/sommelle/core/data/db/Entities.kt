package fr.sommelle.core.data.db

import fr.sommelle.core.model.*

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Une seule ligne : le programme en cours et les réponses au questionnaire d'entrée. */
@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey val id: Int = 0,
    val startDate: LocalDate,
    val wakeTime: LocalTime?,
    val ageBand: String,
    val loudSnoringWithBreathingPauses: Boolean,
    val markedDaytimeSleepiness: Boolean,
    val bipolarDisorder: Boolean,
    val epilepsy: Boolean,
    val pregnancy: Boolean,
    val safetyCriticalActivity: Boolean,
    /** Colonne conservée pour la compatibilité (version 1) : la question n'est plus posée, toujours false. */
    val prefersNoRestriction: Boolean = false,
    val shiftOrNightWork: Boolean,
    val externalNightDisruption: Boolean,
    val lateScheduleSleepsWellOnceAsleep: Boolean,
    val expressedDistress: Boolean,
    /** Colonnes ajoutées en version 4 (précautions de l'accueil) ; false pour les programmes existants. */
    val parasomnia: Boolean = false,
    val moodDisorder: Boolean = false,
    val fallRiskOrEveningSedatives: Boolean = false,
    val unstableOrSleepSensitiveCondition: Boolean = false,
)

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val nightOf: LocalDate,
    val bedTime: LocalDateTime,
    val lightsOutTime: LocalDateTime,
    val sleepOnsetMinutes: Long,
    val awakeningCount: Int,
    val awakeMinutes: Long,
    val finalWakeTime: LocalDateTime,
    val outOfBedTime: LocalDateTime,
    val quality: String,
    val notes: String,
    val prefillSource: String?,
)

@Entity(tableName = "sleep_windows")
data class SleepWindowEntity(
    @PrimaryKey val effectiveFrom: LocalDate,
    val bedTime: LocalTime,
    val wakeTime: LocalTime,
    val durationMinutes: Long,
    val protocolVersion: Int,
    /** Décision hebdomadaire qui a produit la fenêtre ; null pour la fenêtre initiale. */
    val change: String?,
)

/** Une seule ligne : les réglages de la personne. Ajoutée en version 2. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val morningReminder: Boolean,
    val morningTime: LocalTime?,
    val eveningReminder: Boolean,
)

/** Modules cognitifs terminés. Ajoutée en version 2. */
@Entity(tableName = "module_progress")
data class ModuleProgressEntity(
    @PrimaryKey val module: String,
    val completedOn: LocalDate,
)

/** Ressenti de la journée (0 à 4), au début et à la fin du programme. Ajoutée en version 2. */
@Entity(tableName = "daytime_impacts")
data class DaytimeImpactEntity(
    @PrimaryKey val date: LocalDate,
    val score: Int,
)

/**
 * Réponse à la question hebdomadaire sur la somnolence en journée, par date de bilan
 * (jour d'effet de la fenêtre qui en résulte). Ajoutée en version 3.
 */
@Entity(tableName = "weekly_checks")
data class WeeklyCheckEntity(
    @PrimaryKey val reviewDate: LocalDate,
    val sleepinessReported: Boolean,
)

/** Les dates et heures sont stockées au format ISO 8601, lisible dans un export. */
class Converters {
    @TypeConverter fun fromDate(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
    @TypeConverter fun fromTime(value: LocalTime?): String? = value?.toString()
    @TypeConverter fun toTime(value: String?): LocalTime? = value?.let(LocalTime::parse)
}

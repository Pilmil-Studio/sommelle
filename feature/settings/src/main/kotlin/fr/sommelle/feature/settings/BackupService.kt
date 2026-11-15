package fr.sommelle.feature.settings

import android.content.Context
import fr.pilmil.core.platform.backup.AndroidBackupVault
import fr.pilmil.core.platform.backup.BackupException
import fr.pilmil.core.platform.backup.BackupVault
import fr.pilmil.core.platform.backup.InvalidPassphraseException
import fr.sommelle.core.data.SommelleRepository
import fr.sommelle.core.data.backup.BackupData
import fr.sommelle.core.data.backup.InvalidBackupException
import fr.sommelle.core.data.backup.SommelleBackup
import fr.sommelle.core.designsystem.NightThemeConfig
import fr.sommelle.core.designsystem.NightThemeMode
import fr.sommelle.core.designsystem.NightThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pourquoi une sauvegarde est refusée. Dans tous les cas, les données du téléphone n'ont pas été touchées. */
enum class BackupFailure { WRONG_PASSWORD, CORRUPTED, UNSUPPORTED_VERSION, NOT_A_BACKUP }

class BackupRefused(val failure: BackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** Préférences hors base à embarquer dans la sauvegarde (apparence). */
interface BackupPreferences {
    fun read(): Map<String, String>
    fun apply(values: Map<String, String>)
}

/** Contenu d'un fichier déchiffré, en attente de confirmation. */
sealed interface OpenedBackup {
    /** Sauvegarde complète (JSON versionné). */
    data class Full(val data: BackupData) : OpenedBackup {
        val nights: Int get() = data.diaryEntries.size
    }

    /** Ancien format : l'agenda seul, au format CSV. Il s'ajoute à l'agenda actuel au lieu de le remplacer. */
    data class LegacyCsv(val csv: String) : OpenedBackup
}

/**
 * Sauvegarde et restauration chiffrées (.pilmil) de toutes les données de l'appli.
 * Le coffre est injecté ; l'interface n'en connaît pas l'implémentation.
 */
class BackupService(
    private val repository: SommelleRepository,
    private val vault: BackupVault,
    private val preferences: BackupPreferences,
) {
    /** Conteneur chiffré prêt à être écrit dans un fichier. */
    suspend fun create(passphrase: CharArray): ByteArray {
        val json = repository.exportBackup(preferences.read())
        return withContext(Dispatchers.Default) { vault.encrypt(json, passphrase) }
    }

    /** Déchiffre et vérifie le fichier sans rien écrire. @throws BackupRefused */
    suspend fun open(container: ByteArray, passphrase: CharArray): OpenedBackup {
        val payload = try {
            withContext(Dispatchers.Default) { vault.decrypt(container, passphrase) }
        } catch (e: InvalidPassphraseException) {
            throw BackupRefused(BackupFailure.WRONG_PASSWORD, e)
        } catch (e: BackupException) {
            throw BackupRefused(BackupFailure.CORRUPTED, e)
        }
        if (!payload.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("{")) return OpenedBackup.LegacyCsv(payload)
        return try {
            OpenedBackup.Full(SommelleBackup.decode(payload))
        } catch (e: InvalidBackupException) {
            throw BackupRefused(
                when (e.reason) {
                    InvalidBackupException.Reason.NOT_A_BACKUP -> BackupFailure.NOT_A_BACKUP
                    InvalidBackupException.Reason.CORRUPTED -> BackupFailure.CORRUPTED
                    InvalidBackupException.Reason.UNSUPPORTED_VERSION -> BackupFailure.UNSUPPORTED_VERSION
                },
                e,
            )
        }
    }

    /** Remplace toutes les données en une transaction, puis réapplique les préférences. */
    suspend fun restore(backup: OpenedBackup.Full) {
        repository.restoreBackup(backup.data)
        preferences.apply(backup.data.preferences)
    }

    companion object {
        fun create(repository: SommelleRepository, context: Context) =
            BackupService(repository, AndroidBackupVault(), NightThemePreferences(context.applicationContext))
    }
}

/** Thème de nuit : mode et plage horaire, écrits sous forme de texte dans la sauvegarde. */
internal class NightThemePreferences(private val context: Context) : BackupPreferences {
    override fun read(): Map<String, String> {
        val c = NightThemeStore.load(context)
        return mapOf(KEY_MODE to c.mode.name, KEY_START to c.startHour.toString(), KEY_END to c.endHour.toString())
    }

    override fun apply(values: Map<String, String>) {
        val mode = NightThemeMode.entries.firstOrNull { it.name == values[KEY_MODE] } ?: return
        val start = values[KEY_START]?.toIntOrNull()?.coerceIn(0, 23) ?: return
        val end = values[KEY_END]?.toIntOrNull()?.coerceIn(0, 23) ?: return
        NightThemeStore.update(context) { NightThemeConfig(mode, start, end) }
    }

    private companion object {
        const val KEY_MODE = "nightMode"
        const val KEY_START = "nightStartHour"
        const val KEY_END = "nightEndHour"
    }
}

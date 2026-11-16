package fr.sommelle.feature.settings

import fr.sommelle.core.model.*

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import fr.sommelle.core.designsystem.openSafely
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.sommelle.core.data.SommelleRepository
import fr.sommelle.core.data.backup.BackupPolicy
import fr.sommelle.core.data.backup.CloudBackupConsent
import fr.sommelle.core.designsystem.InfoLine
import fr.sommelle.core.designsystem.NightThemeMode
import fr.sommelle.core.designsystem.NightThemeStore
import fr.sommelle.core.designsystem.ScreenScaffold
import fr.sommelle.core.designsystem.ScreenTitle
import fr.sommelle.core.designsystem.SommelleButton
import fr.sommelle.core.designsystem.SommelleButtonStyle
import fr.sommelle.core.designsystem.SommelleIcons
import fr.sommelle.core.designsystem.TimeStepper
import fr.sommelle.core.presentation.Texts
import fr.sommelle.core.presentation.ReminderPlanner
import fr.sommelle.core.presentation.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime

private const val CONTACT_EMAIL = "pilmil.studio@proton.me"

data class SettingsState(
    val reminders: ReminderSettings,
    /** Heure effective du rappel du matin (réglée ou calculée). */
    val morningTime: LocalTime,
    /** Vrai quand une fenêtre de sommeil existe : le rappel du soir a un sens. */
    val hasWindow: Boolean,
)

class SettingsViewModel(private val repository: SommelleRepository, val backup: BackupService) : ViewModel() {

    val state: StateFlow<SettingsState?> = combine(repository.reminderSettings, repository.snapshot) { settings, snapshot ->
        SettingsState(
            reminders = settings,
            morningTime = ReminderPlanner.morningTime(settings, snapshot?.program?.wakeTime),
            hasWindow = snapshot?.windows?.isNotEmpty() == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (ReminderSettings, LocalTime) -> ReminderSettings) {
        val current = state.value ?: return
        viewModelScope.launch { repository.saveReminderSettings(change(current.reminders, current.morningTime)) }
    }

    fun erase(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.eraseEverything()
            onDone()
        }
    }

    fun importCsv(content: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importCsv(content)
            onDone(count)
        }
    }

}

@Composable
fun SettingsScreen(repository: SommelleRepository, onOpenReport: () -> Unit, onOpenIntro: () -> Unit, onErased: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(repository, BackupService.create(repository, context)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val cloudConsent = remember { CloudBackupConsent(context.noBackupFilesDir) }
    var cloudBackupOn by remember { mutableStateOf(cloudConsent.enabled) }
    val deviceSecure = remember {
        (context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true
    }
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var importedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    val scope = rememberCoroutineScope()
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingContainer by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFull by remember { mutableStateOf<OpenedBackup.Full?>(null) }
    var backupMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    var passphraseInput by rememberSaveable { mutableStateOf("") }
    var passphraseConfirm by rememberSaveable { mutableStateOf("") }
    var passphraseError by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }

    val pilmilExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pwd = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && pwd != null) {
            scope.launch {
                try {
                    val encrypted = viewModel.backup.create(pwd)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted) }
                    }
                    backupMessage = R.string.backup_saved
                } catch (_: Exception) {
                    backupMessage = R.string.backup_save_failed
                } finally {
                    pwd.fill('\u0000')
                }
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val selected = uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(selected)?.use { it.readBytes() }
                }.getOrNull()
            } ?: return@launch
            if (isPilmilBackup(bytes)) {
                pendingContainer = bytes
                passphraseInput = ""
                passphraseError = null
            } else {
                viewModel.importCsv(bytes.decodeToString()) { count ->
                    importedCount = count
                    backupMessage = null
                }
            }
        }
    }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsContent(
        state = s,
        notificationsAllowed = notificationsAllowed,
        onMorning = { enabled ->
            if (enabled) ensurePermission()
            viewModel.update { r, _ -> r.copy(morningEnabled = enabled) }
        },
        onMorningShift = { minutes -> viewModel.update { r, time -> r.copy(morningTime = time.plusMinutes(minutes)) } },
        onEvening = { enabled ->
            if (enabled) ensurePermission()
            viewModel.update { r, _ -> r.copy(eveningEnabled = enabled) }
        },
        onOpenReport = onOpenReport,
        onOpenIntro = onOpenIntro,
        onImportCsv = { csvPicker.launch("*/*") },
        onExportPilmil = {
            passphraseInput = ""
            passphraseConfirm = ""
            passphraseError = null
            showExportDialog = true
        },
        pilmilStatus = backupMessage?.let { stringResource(it) },
        importedCount = importedCount,
        cloudBackupAvailable = BackupPolicy.settingAvailable(Build.VERSION.SDK_INT),
        cloudBackupPossible = deviceSecure,
        cloudBackupOn = cloudBackupOn,
        onCloudBackupChange = { on ->
            cloudConsent.enabled = on
            cloudBackupOn = on
        },
        onErase = { viewModel.erase(onErased) },
    )

    if (showExportDialog) {
        val mismatch = passphraseConfirm.isNotEmpty() && passphraseInput != passphraseConfirm
        val valid = passphraseInput.isNotEmpty() && passphraseInput == passphraseConfirm
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.backup_export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_export_text), style = MaterialTheme.typography.bodySmall)
                    PasswordField(stringResource(R.string.backup_password), passphraseInput) { passphraseInput = it }
                    PasswordField(stringResource(R.string.backup_password_confirm), passphraseConfirm, isError = mismatch) { passphraseConfirm = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        pendingExportPassphrase = passphraseInput.toCharArray()
                        passphraseInput = ""
                        passphraseConfirm = ""
                        showExportDialog = false
                        pilmilExporter.launch("sommelle.pilmil")
                    },
                ) { Text(stringResource(R.string.backup_export_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }

    // Étape 1 de la restauration : mot de passe. Le fichier est déchiffré et vérifié sans rien écrire.
    val container = pendingContainer
    if (container != null) {
        AlertDialog(
            onDismissRequest = { pendingContainer = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_restore_text), style = MaterialTheme.typography.bodySmall)
                    PasswordField(stringResource(R.string.backup_password), passphraseInput, isError = passphraseError != null) {
                        passphraseInput = it
                        passphraseError = null
                    }
                    passphraseError?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphraseInput.isNotEmpty(),
                    onClick = {
                        val pwd = passphraseInput.toCharArray()
                        scope.launch {
                            try {
                                when (val opened = viewModel.backup.open(container, pwd)) {
                                    is OpenedBackup.Full -> pendingFull = opened
                                    is OpenedBackup.LegacyCsv -> viewModel.importCsv(opened.csv) { count ->
                                        importedCount = count
                                        backupMessage = null
                                    }
                                }
                                pendingContainer = null
                                passphraseInput = ""
                            } catch (e: BackupRefused) {
                                passphraseError = e.failure.message()
                            } finally {
                                pwd.fill('\u0000')
                            }
                        }
                    },
                ) { Text(stringResource(R.string.backup_restore_open)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingContainer = null }) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }

    // Étape 2 : confirmation explicite avant de remplacer les données actuelles.
    val toRestore = pendingFull
    if (toRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingFull = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_text, toRestore.nights)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingFull = null
                        scope.launch {
                            backupMessage = try {
                                viewModel.backup.restore(toRestore)
                                R.string.backup_restored
                            } catch (_: Exception) {
                                R.string.backup_restore_failed
                            }
                        }
                    },
                ) { Text(stringResource(R.string.backup_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFull = null }) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }
}

private fun BackupFailure.message(): Int = when (this) {
    BackupFailure.WRONG_PASSWORD -> R.string.backup_wrong_password
    BackupFailure.CORRUPTED -> R.string.backup_corrupted
    BackupFailure.UNSUPPORTED_VERSION -> R.string.backup_unsupported
    BackupFailure.NOT_A_BACKUP -> R.string.backup_not_sommelle
}

@Composable
private fun PasswordField(label: String, value: String, isError: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
    )
}

private val SOMMELLE_PILMIL_MAGIC = byteArrayOf(0x50, 0x49, 0x4C, 0x4D, 0x49, 0x4C)

private fun isPilmilBackup(bytes: ByteArray): Boolean =
    bytes.size >= 7 && SOMMELLE_PILMIL_MAGIC.indices.all { i -> bytes[i] == SOMMELLE_PILMIL_MAGIC[i] }

/** Réglages sans état ni dépendance : affichés tels quels dans les captures de la documentation. */
@Composable
fun SettingsContent(
    state: SettingsState,
    notificationsAllowed: Boolean,
    onMorning: (Boolean) -> Unit,
    onMorningShift: (Long) -> Unit,
    onEvening: (Boolean) -> Unit,
    onOpenReport: () -> Unit,
    onErase: () -> Unit,
    onOpenIntro: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onExportPilmil: () -> Unit = {},
    pilmilStatus: String? = null,
    importedCount: Int? = null,
    cloudBackupAvailable: Boolean = false,
    cloudBackupPossible: Boolean = false,
    cloudBackupOn: Boolean = false,
    onCloudBackupChange: (Boolean) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var confirmErase by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold {
        ScreenTitle(stringResource(R.string.settings_title))

        Section(stringResource(R.string.settings_reminders)) {
            ToggleRow(
                title = stringResource(R.string.settings_morning),
                hint = stringResource(R.string.settings_morning_hint),
                checked = state.reminders.morningEnabled,
                onChange = onMorning,
            )
            if (state.reminders.morningEnabled) {
                Text(stringResource(R.string.settings_morning_time), style = MaterialTheme.typography.titleSmall)
                TimeStepper(
                    value = Texts.current().time(state.morningTime),
                    onMinus = { onMorningShift(-15) },
                    onPlus = { onMorningShift(15) },
                    onMinusHour = { onMorningShift(-60) },
                    onPlusHour = { onMorningShift(60) },
                )
            }
            ToggleRow(
                title = stringResource(R.string.settings_evening),
                hint = stringResource(if (state.hasWindow) R.string.settings_evening_hint else R.string.settings_evening_unavailable),
                checked = state.reminders.eveningEnabled && state.hasWindow,
                enabled = state.hasWindow,
                onChange = onEvening,
            )
            if (!notificationsAllowed && (state.reminders.morningEnabled || state.reminders.eveningEnabled)) {
                Text(stringResource(R.string.settings_notifications_off), style = MaterialTheme.typography.bodySmall)
            }
        }

        NightAppearanceSection()

        Section(stringResource(R.string.settings_for_doctor)) {
            Text(stringResource(R.string.settings_report_hint), style = MaterialTheme.typography.bodyMedium)
            SommelleButton(stringResource(R.string.settings_report), onOpenReport, style = SommelleButtonStyle.Outlined)
        }

        val privacyUrl = stringResource(R.string.settings_privacy_url)
        Section(stringResource(R.string.settings_data)) {
            InfoLine(SommelleIcons.History, stringResource(R.string.settings_data_hint))
            SommelleButton(stringResource(R.string.settings_backup_export), onExportPilmil, style = SommelleButtonStyle.Outlined)
            SommelleButton(stringResource(R.string.settings_backup_restore), onImportCsv, style = SommelleButtonStyle.Outlined)
            if (cloudBackupAvailable) {
                ToggleRow(
                    title = stringResource(R.string.settings_cloud_backup),
                    hint = stringResource(if (cloudBackupPossible) R.string.settings_cloud_backup_hint else R.string.settings_cloud_backup_unavailable),
                    checked = cloudBackupOn && cloudBackupPossible,
                    enabled = cloudBackupPossible,
                    onChange = onCloudBackupChange,
                )
            }
            SommelleButton(stringResource(R.string.settings_import_csv), onImportCsv, style = SommelleButtonStyle.Outlined)
            if (pilmilStatus != null) {
                Text(pilmilStatus, style = MaterialTheme.typography.bodySmall)
            }
            if (importedCount != null) {
                Text(stringResource(R.string.settings_import_result, importedCount), style = MaterialTheme.typography.bodySmall)
            }
            SommelleButton(stringResource(R.string.settings_privacy), { uriHandler.openSafely(privacyUrl) }, style = SommelleButtonStyle.Quiet)
            SommelleButton(stringResource(R.string.settings_review_button), { openStore(context) }, style = SommelleButtonStyle.Quiet)
            SommelleButton(stringResource(R.string.settings_erase), { confirmErase = true }, style = SommelleButtonStyle.Quiet)
        }

        Section(stringResource(R.string.settings_contact)) {
            Text(stringResource(R.string.settings_contact_hint), style = MaterialTheme.typography.bodyMedium)
            Text(CONTACT_EMAIL, style = MaterialTheme.typography.titleSmall)
            SommelleButton(
                stringResource(R.string.settings_contact_button),
                { uriHandler.openSafely("mailto:$CONTACT_EMAIL?subject=Sommelle") },
                style = SommelleButtonStyle.Outlined,
            )
        }

        Text(stringResource(R.string.settings_help), style = MaterialTheme.typography.bodySmall)
        SommelleButton(stringResource(R.string.settings_call_3114), { uriHandler.openSafely("tel:3114") }, style = SommelleButtonStyle.Quiet)

        Section(stringResource(R.string.settings_suite_title)) {
            Text(stringResource(R.string.settings_suite_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompanionAppRow(
                badge = "🌿",
                name = stringResource(R.string.settings_app_respire_name),
                description = stringResource(R.string.settings_app_respire_desc),
                onClick = { uriHandler.openSafely("https://pilmil-studio.github.io/respire/") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompanionAppRow(
                badge = "🌙",
                name = stringResource(R.string.settings_app_lunaison_name),
                description = stringResource(R.string.settings_app_lunaison_desc),
                onClick = { uriHandler.openSafely("https://pilmil-studio.github.io/lunaison/") },
            )
        }

        Section(stringResource(R.string.settings_about)) {
            SommelleButton(stringResource(R.string.settings_replay_intro), onOpenIntro, style = SommelleButtonStyle.Outlined)
            Text(stringResource(R.string.settings_version, appVersionName()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_commitment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SommelleButton(
                    stringResource(R.string.settings_source),
                    { uriHandler.openSafely("https://github.com/Pilmil-Studio/sommelle") },
                    modifier = Modifier.weight(1f),
                    style = SommelleButtonStyle.Outlined,
                )
                SommelleButton(
                    stringResource(R.string.settings_privacy),
                    { uriHandler.openSafely(privacyUrl) },
                    modifier = Modifier.weight(1f),
                    style = SommelleButtonStyle.Outlined,
                )
            }
            PilmilLegalBlock()
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.settings_erase_title)) },
            text = { Text(stringResource(R.string.settings_erase_text)) },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; onErase() }) { Text(stringResource(R.string.settings_erase_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.settings_erase_cancel)) }
            },
        )
    }
}

@Composable
private fun NightAppearanceSection() {
    val context = LocalContext.current
    NightThemeStore.load(context)
    val nightConfig by NightThemeStore.config.collectAsStateWithLifecycle()
    val modes = listOf(
        NightThemeMode.AUTO_HOURS to R.string.night_mode_auto,
        NightThemeMode.ALWAYS_DAY to R.string.night_mode_day,
        NightThemeMode.ALWAYS_NIGHT to R.string.night_mode_night,
        NightThemeMode.FOLLOW_SYSTEM to R.string.night_mode_system,
    )
    Section(stringResource(R.string.night_theme_title)) {
        Text(stringResource(R.string.night_theme_hint), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, label) ->
                val selected = nightConfig.mode == mode
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            NightThemeStore.update(context) { it.copy(mode = mode) }
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (nightConfig.mode == NightThemeMode.AUTO_HOURS) {
            HourRow(stringResource(R.string.night_start_label, nightConfig.startHour),
                onEarlier = { NightThemeStore.update(context) { it.copy(startHour = it.startHour - 1) } },
                onLater = { NightThemeStore.update(context) { it.copy(startHour = it.startHour + 1) } })
            HourRow(stringResource(R.string.night_end_label, nightConfig.endHour),
                onEarlier = { NightThemeStore.update(context) { it.copy(endHour = it.endHour - 1) } },
                onLater = { NightThemeStore.update(context) { it.copy(endHour = it.endHour + 1) } })
        }
    }
}

/** Une heure réglable : le libellé, puis « − 1 h » et « + 1 h » avec une description lue par TalkBack. */
@Composable
private fun HourRow(label: String, onEarlier: () -> Unit, onLater: () -> Unit) {
    val earlier = stringResource(R.string.night_hour_earlier)
    val later = stringResource(R.string.night_hour_later)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SommelleButton("− 1 h", onEarlier, style = SommelleButtonStyle.Outlined, modifier = Modifier.weight(1f).semantics { contentDescription = earlier })
            SommelleButton("+ 1 h", onLater, style = SommelleButtonStyle.Outlined, modifier = Modifier.weight(1f).semantics { contentDescription = later })
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** Ligne entière cliquable avec interrupteur, annoncée comme un interrupteur par TalkBack. */
@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun CompanionAppRow(
    badge: String,
    name: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(badge, fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Version de l'application installée (celle du manifeste), plutôt qu'un numéro écrit en dur. */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
}

/** Fiche Play Store, ou la page web si le Store n'est pas installé (APK GitHub, F-Droid…). */
private fun openStore(context: Context) {
    val pkg = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(web) }
    }
}

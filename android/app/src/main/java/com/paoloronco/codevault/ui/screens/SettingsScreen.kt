package com.paoloronco.codevault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.os.LocaleListCompat
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.paoloronco.codevault.BuildConfig
import com.paoloronco.codevault.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paoloronco.codevault.security.SecurityManager
import com.paoloronco.codevault.ui.BackupResult
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import com.paoloronco.codevault.ui.theme.VaultTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack:    () -> Unit
) {
    val context = LocalContext.current
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()

    val bioAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
    var biometricEnabled by remember {
        mutableStateOf(viewModel.securityManager.isBiometricEnabled())
    }
    var autoLockTimeout by remember {
        mutableStateOf(viewModel.securityManager.getAutoLockTimeout())
    }

    // Dialogs
    var showChangePinDialog   by remember { mutableStateOf(false) }
    var showExportPinDialog   by remember { mutableStateOf(false) }
    var showImportPinDialog   by remember { mutableStateOf(false) }
    var showResetDialog       by remember { mutableStateOf(false) }
    var pendingExportUri      by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri      by remember { mutableStateOf<android.net.Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // SAF launchers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) { pendingExportUri = uri; showExportPinDialog = true } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) { pendingImportUri = uri; showImportPinDialog = true } }

    // Backup result feedback
    LaunchedEffect(backupResult) {
        when (val r = backupResult) {
            is BackupResult.Success ->
                snackbarHostState.showSnackbar(context.getString(R.string.settings_operation_success))
            is BackupResult.Error   ->
                snackbarHostState.showSnackbar(context.getString(R.string.settings_operation_error, r.message))
            else -> {}
        }
        if (backupResult != BackupResult.Idle) viewModel.clearBackupResult()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Sicurezza ----
            SettingsSection(stringResource(R.string.settings_security_section), Icons.Default.Security)

            SettingsItem(
                icon     = Icons.Default.Password,
                title    = if (viewModel.securityManager.getAuthType() == "pin")
                               stringResource(R.string.settings_change_pin)
                           else stringResource(R.string.settings_change_password),
                subtitle = stringResource(R.string.settings_change_auth_subtitle),
                onClick  = { showChangePinDialog = true }
            )

            // Biometric toggle
            if (bioAvailable) {
                val activity = context as? FragmentActivity
                val strBioSubtitle = stringResource(R.string.lock_bio_subtitle)
                val strUsePin      = stringResource(
                    if (viewModel.securityManager.getAuthType() == "pin")
                        R.string.lock_use_pin else R.string.lock_use_password
                )
                SettingsToggle(
                    icon     = Icons.Default.Fingerprint,
                    title    = stringResource(R.string.settings_biometric),
                    subtitle = stringResource(R.string.settings_biometric_subtitle),
                    checked  = biometricEnabled,
                    onToggle = { enable ->
                        if (!enable) {
                            // Disabling: no verification needed
                            biometricEnabled = false
                            viewModel.securityManager.setBiometricEnabled(false)
                        } else {
                            // Enabling: verify biometric first
                            triggerBiometric(
                                activity     = activity,
                                subtitle     = strBioSubtitle,
                                negativeText = strUsePin,
                                onSuccess    = {
                                    biometricEnabled = true
                                    viewModel.securityManager.setBiometricEnabled(true)
                                },
                                onError      = { _, _ ->
                                    // Keep toggle off if biometric fails/cancelled
                                }
                            )
                        }
                    }
                )
            }

            // Auto-lock
            SettingsSection(stringResource(R.string.settings_autolock_section), Icons.Default.Timer)
            Text(
                stringResource(R.string.settings_autolock_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = VaultOnSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecurityManager.TIMEOUT_OPTIONS.forEach { (ms, _) ->
                    val selected = autoLockTimeout == ms
                    FilterChip(
                        selected = selected,
                        onClick  = {
                            autoLockTimeout = ms
                            viewModel.securityManager.setAutoLockTimeout(ms)
                        },
                        label    = { Text(timeoutLabel(ms)) },
                        leadingIcon = if (selected) ({
                            Icon(Icons.Default.Check, null,
                                 modifier = Modifier.size(16.dp))
                        }) else null
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Backup ----
            SettingsSection(stringResource(R.string.settings_backup_section), Icons.Default.Backup)

            SettingsItem(
                icon     = Icons.Default.Upload,
                title    = stringResource(R.string.settings_export_title),
                subtitle = stringResource(R.string.settings_export_subtitle),
                onClick  = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                        .format(Date())
                    exportLauncher.launch("enclave_backup_$timestamp.evault")
                }
            )

            SettingsItem(
                icon     = Icons.Default.Download,
                title    = stringResource(R.string.settings_import_title),
                subtitle = stringResource(R.string.settings_import_subtitle),
                onClick  = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )

            // ---- Lingua ----
            SettingsSection(stringResource(R.string.settings_language_section), Icons.Default.Language)
            LanguageSelector()

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )

            // ---- Info ----
            SettingsSection(stringResource(R.string.settings_info_section), Icons.Default.Info)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                    InfoRow(stringResource(R.string.settings_db_encryption), "AES-256-GCM (Android Keystore)")
                    InfoRow(stringResource(R.string.settings_credentials_encryption), "AES-256-GCM (Android Keystore)")
                    InfoRow(stringResource(R.string.settings_backup_section), "AES-256-GCM + PBKDF2")
                    InfoRow(stringResource(R.string.settings_cloud_sync), stringResource(R.string.settings_cloud_sync_value))
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )

            // ---- Danger Zone ----
            SettingsSection(
                stringResource(R.string.settings_danger_section),
                Icons.Default.Warning,
                MaterialTheme.colorScheme.error
            )
            SettingsItem(
                icon      = Icons.Default.DeleteForever,
                title     = stringResource(R.string.settings_reset_vault_title),
                subtitle  = stringResource(R.string.settings_reset_vault_subtitle),
                iconTint  = MaterialTheme.colorScheme.error,
                onClick   = { showResetDialog = true }
            )
        }
    }

    // Reset vault dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title   = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text    = { Text(stringResource(R.string.settings_reset_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetVault(); showResetDialog = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.settings_reset_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Change PIN/password dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            isPin    = viewModel.securityManager.getAuthType() == "pin",
            onSave   = { newCode ->
                viewModel.securityManager.savePasscode(newCode)
                showChangePinDialog = false
            },
            onDismiss = { showChangePinDialog = false }
        )
    }

    // Export PIN dialog
    pendingExportUri?.let { uri ->
        if (showExportPinDialog) {
            PinConfirmDialog(
                title    = stringResource(R.string.settings_export_dialog_title),
                subtitle = stringResource(R.string.settings_export_dialog_subtitle),
                isPin    = viewModel.securityManager.getAuthType() == "pin",
                onConfirm = { code ->
                    if (viewModel.securityManager.verifyPasscode(code)) {
                        viewModel.exportBackup(uri, code)
                        showExportPinDialog = false
                        pendingExportUri    = null
                    } else {
                        // show error inside dialog
                    }
                },
                onDismiss = { showExportPinDialog = false; pendingExportUri = null }
            )
        }
    }

    // Import PIN dialog
    pendingImportUri?.let { uri ->
        if (showImportPinDialog) {
            PinConfirmDialog(
                title    = stringResource(R.string.settings_import_dialog_title),
                subtitle = stringResource(R.string.settings_import_dialog_subtitle),
                isPin    = viewModel.securityManager.getAuthType() == "pin",
                onConfirm = { code ->
                    viewModel.importBackup(uri, code)
                    showImportPinDialog = false
                    pendingImportUri    = null
                },
                onDismiss = { showImportPinDialog = false; pendingImportUri = null }
            )
        }
    }
}

// ---- Composable helpers ----

@Composable
private fun SettingsSection(
    title: String,
    icon:  ImageVector,
    color: androidx.compose.ui.graphics.Color = VaultTeal
) {
    Row(
        modifier          = Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style      = MaterialTheme.typography.labelLarge,
            color      = color,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp
        )
    }
}

@Composable
private fun SettingsItem(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    onClick:  () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VaultOnSurface)
            }
            Icon(Icons.Default.ChevronRight, null, tint = VaultOnSurface)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VaultOnSurface)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = VaultOnSurface)
        Text(value,  style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChangePinDialog(isPin: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newCode  by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf("") }

    val strTitle        = if (isPin) stringResource(R.string.settings_change_pin_dialog_title)
                          else       stringResource(R.string.settings_change_password_dialog_title)
    val strCurrent      = stringResource(R.string.settings_field_current)
    val strNew          = stringResource(R.string.settings_field_new)
    val strConfirmLabel = stringResource(R.string.settings_field_confirm)
    val strMismatch     = stringResource(R.string.settings_codes_mismatch)
    val strPinMin       = stringResource(R.string.settings_pin_min)
    val strPassMin      = stringResource(R.string.settings_password_min)

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(strTitle) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current, onValueChange = { current = it },
                    label = { Text(strCurrent) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword
                                       else KeyboardType.Password
                    )
                )
                OutlinedTextField(
                    value = newCode, onValueChange = { newCode = it },
                    label = { Text(strNew) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword
                                       else KeyboardType.Password
                    )
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = { Text(strConfirmLabel) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword
                                       else KeyboardType.Password
                    )
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    newCode != confirm -> error = strMismatch
                    isPin && newCode.length < 4 -> error = strPinMin
                    !isPin && newCode.length < 6 -> error = strPassMin
                    else -> onSave(newCode)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun PinConfirmDialog(
    title:    String,
    subtitle: String,
    isPin:    Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code  by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VaultOnSurface)
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text(if (isPin) stringResource(R.string.settings_field_pin) else stringResource(R.string.settings_field_password)) },
                    singleLine = true,
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) ({ Text(error) }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword
                                       else KeyboardType.Password
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(code) }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ── Timeout label helper ─────────────────────────────────────────────────────

@Composable
private fun timeoutLabel(ms: Long): String = when (ms) {
    SecurityManager.TIMEOUT_30S   -> stringResource(R.string.timeout_30s)
    SecurityManager.TIMEOUT_1M    -> stringResource(R.string.timeout_1m)
    SecurityManager.TIMEOUT_2M    -> stringResource(R.string.timeout_2m)
    SecurityManager.TIMEOUT_5M    -> stringResource(R.string.timeout_5m)
    SecurityManager.TIMEOUT_10M   -> stringResource(R.string.timeout_10m)
    SecurityManager.TIMEOUT_NEVER -> stringResource(R.string.timeout_never)
    else                           -> "$ms ms"
}

// ── Language selector ────────────────────────────────────────────────────────

private data class LangOption(val tag: String, val label: String, val flag: String)

private val LANGUAGES = listOf(
    LangOption("it", "Italiano", "🇮🇹"),
    LangOption("en", "English",  "🇬🇧"),
    LangOption("de", "Deutsch",  "🇩🇪"),
    LangOption("es", "Español",  "🇪🇸"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelector() {
    val current = remember {
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .split(",").firstOrNull()?.take(2) ?: Locale.getDefault().language
    }
    var selected by remember { mutableStateOf(current) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LANGUAGES.forEach { lang ->
            val isSelected = selected == lang.tag
            FilterChip(
                selected    = isSelected,
                onClick     = {
                    selected = lang.tag
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(lang.tag)
                    )
                },
                label       = { Text("${lang.flag}  ${lang.label}") },
                leadingIcon = if (isSelected) ({
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                }) else null
            )
        }
    }
}

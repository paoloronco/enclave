package com.paoloronco.codevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paoloronco.codevault.R
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.data.Categories
import com.paoloronco.codevault.data.EntryType
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.VaultGold
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    viewModel:  MainViewModel,
    accountId:  Long?,
    entryType:  String,           // EntryType key or "edit" when loading existing
    onSaved:    () -> Unit,
    onBack:     () -> Unit
) {
    var loaded       by remember { mutableStateOf(false) }
    var resolvedType by remember { mutableStateOf(entryType) }
    var title        by remember { mutableStateOf("") }
    var username     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var notes        by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf(Categories.GENERAL) }
    var isFavorite   by remember { mutableStateOf(false) }
    var showPass     by remember { mutableStateOf(false) }
    var showCatMenu  by remember { mutableStateOf(false) }
    var titleError   by remember { mutableStateOf(false) }
    var showGenDialog by remember { mutableStateOf(false) }

    val isEdit = accountId != null && accountId > 0

    LaunchedEffect(accountId) {
        if (isEdit) {
            viewModel.getAccountById(accountId!!)?.let { acc ->
                resolvedType = acc.type
                title        = acc.title
                username     = acc.username
                password     = acc.password
                notes        = acc.notes
                category     = acc.category
                isFavorite   = acc.isFavorite
            }
        }
        loaded = true
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val typeMeta = EntryType.ALL.find { it.key == resolvedType }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(typeMeta?.emoji ?: "🔑", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isEdit) stringResource(R.string.addedit_edit_title, typeMeta?.let { entryTypeLabel(it.key) } ?: stringResource(R.string.addedit_entry_fallback))
                            else        stringResource(R.string.addedit_new_title,  typeMeta?.let { entryTypeLabel(it.key) } ?: stringResource(R.string.addedit_entry_fallback)),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.addedit_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            stringResource(R.string.addedit_favorite_cd),
                            tint = if (isFavorite) VaultGold else VaultOnSurface
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Title (all types) ──────────────────────────────────
            OutlinedTextField(
                value         = title,
                onValueChange = { title = it; titleError = false },
                label         = { Text(stringResource(R.string.addedit_field_title)) },
                singleLine    = true,
                isError       = titleError,
                supportingText = if (titleError) ({ Text(stringResource(R.string.addedit_title_required)) }) else null,
                leadingIcon   = { Icon(Icons.AutoMirrored.Filled.Label, null, tint = VaultOnSurface) },
                modifier      = Modifier.fillMaxWidth()
            )

            // ── Category (account, pin, wifi) ──────────────────────
            if (resolvedType != EntryType.NOTE) {
                ExposedDropdownMenuBox(
                    expanded         = showCatMenu,
                    onExpandedChange = { showCatMenu = it }
                ) {
                    OutlinedTextField(
                        value         = category,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(stringResource(R.string.addedit_category)) },
                        leadingIcon   = {
                            Icon(categoryIcon(category), null,
                                tint = categoryColor(category),
                                modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCatMenu) },
                        modifier     = Modifier.fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded         = showCatMenu,
                        onDismissRequest = { showCatMenu = false }
                    ) {
                        Categories.ALL.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(categoryIcon(cat), null,
                                            tint = categoryColor(cat),
                                            modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(cat)
                                    }
                                },
                                onClick = { category = cat; showCatMenu = false }
                            )
                        }
                    }
                }
            }

            // ── Type-specific fields ───────────────────────────────
            when (resolvedType) {

                EntryType.ACCOUNT -> {
                    // Username
                    OutlinedTextField(
                        value         = username,
                        onValueChange = { username = it },
                        label         = { Text(stringResource(R.string.addedit_username_email)) },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.Person, null, tint = VaultOnSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Password + generator
                    PasswordRow(
                        value     = password,
                        onChange  = { password = it },
                        show      = showPass,
                        onToggle  = { showPass = !showPass },
                        onGenerate = { showGenDialog = true }
                    )
                }

                EntryType.PIN -> {
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { if (it.all { c -> c.isDigit() }) password = it },
                        label         = { Text(stringResource(R.string.addedit_pin_code)) },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.Pin, null, tint = VaultOnSurface) },
                        visualTransformation = if (showPass) VisualTransformation.None
                                              else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon  = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = VaultOnSurface
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Optional extra info (card holder, expiry, etc.)
                    OutlinedTextField(
                        value         = username,
                        onValueChange = { username = it },
                        label         = { Text(stringResource(R.string.addedit_cardholder)) },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.CreditCard, null, tint = VaultOnSurface) },
                        modifier      = Modifier.fillMaxWidth()
                    )
                }

                EntryType.NOTE -> {
                    OutlinedTextField(
                        value         = notes,
                        onValueChange = { notes = it },
                        label         = { Text(stringResource(R.string.addedit_secure_note)) },
                        minLines      = 6,
                        maxLines      = 20,
                        leadingIcon   = { Icon(Icons.AutoMirrored.Filled.Notes, null, tint = VaultOnSurface) },
                        modifier      = Modifier.fillMaxWidth()
                    )
                }

                EntryType.WIFI -> {
                    // SSID in username field
                    OutlinedTextField(
                        value         = username,
                        onValueChange = { username = it },
                        label         = { Text(stringResource(R.string.addedit_wifi_ssid)) },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.Wifi, null, tint = VaultOnSurface) },
                        modifier      = Modifier.fillMaxWidth()
                    )
                    PasswordRow(
                        value      = password,
                        onChange   = { password = it },
                        show       = showPass,
                        onToggle   = { showPass = !showPass },
                        onGenerate = { showGenDialog = true },
                        labelRes   = R.string.addedit_wifi_password
                    )
                }
            }

            // ── Notes (account / pin / wifi) ───────────────────────
            if (resolvedType != EntryType.NOTE) {
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text(stringResource(R.string.addedit_notes_optional)) },
                    minLines      = 2,
                    maxLines      = 5,
                    leadingIcon   = { Icon(Icons.AutoMirrored.Filled.Notes, null, tint = VaultOnSurface) },
                    modifier      = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Save ───────────────────────────────────────────────
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else {
                        val account = AccountEntity(
                            id         = if (isEdit) accountId!! else 0,
                            type       = resolvedType,
                            title      = title.trim(),
                            username   = username.trim(),
                            password   = password,
                            notes      = notes.trim(),
                            category   = category,
                            isFavorite = isFavorite,
                            updatedAt  = System.currentTimeMillis()
                        )
                        if (isEdit) viewModel.updateAccount(account)
                        else        viewModel.addAccount(account)
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEdit) stringResource(R.string.update) else stringResource(R.string.save), fontSize = 16.sp)
            }
        }
    }

    if (showGenDialog) {
        PasswordGeneratorDialog(
            onGenerated = { generated -> password = generated; showGenDialog = false },
            onDismiss   = { showGenDialog = false }
        )
    }
}

// ── Password row with generator ──────────────────────────────────────────────

@Composable
private fun PasswordRow(
    value:     String,
    onChange:  (String) -> Unit,
    show:      Boolean,
    onToggle:  () -> Unit,
    onGenerate: () -> Unit,
    labelRes:   Int = R.string.detail_field_password
) {
    val label = stringResource(labelRes)
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            label         = { Text(label) },
            singleLine    = true,
            visualTransformation = if (show) VisualTransformation.None
                                  else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon   = { Icon(Icons.Default.Key, null, tint = VaultOnSurface) },
            trailingIcon  = {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = VaultOnSurface
                    )
                }
            },
            modifier = Modifier.weight(1f)
        )
        FilledTonalIconButton(onClick = onGenerate) {
            Icon(Icons.Default.AutoAwesome, "Genera",
                 tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── Password generator dialog ─────────────────────────────────────────────

@Composable
private fun PasswordGeneratorDialog(onGenerated: (String) -> Unit, onDismiss: () -> Unit) {
    var length    by remember { mutableStateOf(16f) }
    var uppercase by remember { mutableStateOf(true) }
    var digits    by remember { mutableStateOf(true) }
    var special   by remember { mutableStateOf(true) }
    var preview   by remember { mutableStateOf(genPassword(16, true, true, true)) }

    fun regen() { preview = genPassword(length.toInt(), uppercase, digits, special) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addedit_gen_password_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            preview,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = ::regen) {
                            Icon(Icons.Default.Refresh, null,
                                 tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(stringResource(R.string.addedit_gen_length, length.toInt()),
                     style = MaterialTheme.typography.bodySmall)
                Slider(value = length, onValueChange = { length = it; regen() },
                       valueRange = 8f..32f, steps = 23)
                GenToggle(stringResource(R.string.addedit_gen_uppercase), uppercase) { uppercase = it; regen() }
                GenToggle(stringResource(R.string.addedit_gen_digits),    digits)    { digits    = it; regen() }
                GenToggle(stringResource(R.string.addedit_gen_symbols),   special)   { special   = it; regen() }
            }
        },
        confirmButton = {
            Button(onClick = { onGenerated(preview) }) { Text(stringResource(R.string.addedit_use_password)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun GenToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.height(24.dp))
    }
}

private fun genPassword(len: Int, upper: Boolean, digits: Boolean, special: Boolean): String {
    var cs = "abcdefghijklmnopqrstuvwxyz"
    if (upper)   cs += "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if (digits)  cs += "0123456789"
    if (special) cs += "!@#\$%^&*()_+-=[]{}|;:,.<>?"
    if (cs.isEmpty()) cs = "abcdefghijklmnopqrstuvwxyz"
    val rng = SecureRandom()
    return (1..len).map { cs[rng.nextInt(cs.length)] }.joinToString("")
}

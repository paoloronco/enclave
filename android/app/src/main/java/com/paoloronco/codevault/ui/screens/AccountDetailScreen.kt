package com.paoloronco.codevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.paoloronco.codevault.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.VaultGold
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    viewModel:  MainViewModel,
    accountId:  Long,
    onEdit:     () -> Unit,
    onDeleted:  () -> Unit,
    onBack:     () -> Unit
) {
    val context = LocalContext.current
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPassword     by remember { mutableStateOf(false) }
    var copiedLabel      by remember { mutableStateOf("") }

    LaunchedEffect(accountId) {
        account = viewModel.getAccountById(accountId)
    }

    // Snackbar for copy feedback
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(copiedLabel) {
        if (copiedLabel.isNotEmpty()) {
            snackbarHostState.showSnackbar(context.getString(R.string.detail_copied, copiedLabel))
            copiedLabel = ""
        }
    }

    val acc = account
    if (acc == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(acc.title, fontWeight = FontWeight.SemiBold,
                         color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back_cd))
                    }
                },
                actions = {
                    if (acc.isFavorite) {
                        Icon(
                            Icons.Default.Star, null,
                            tint     = VaultGold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, stringResource(R.string.detail_edit_cd),
                             tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.detail_delete_cd),
                             tint = MaterialTheme.colorScheme.error)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category banner
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(categoryColor(acc.category).copy(alpha = 0.12f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(categoryColor(acc.category).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(categoryIcon(acc.category), null,
                         tint = categoryColor(acc.category), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(acc.category,
                         style = MaterialTheme.typography.labelLarge,
                         color = categoryColor(acc.category),
                         fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.detail_updated_at, formatDate(acc.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultOnSurface
                    )
                }
            }

            // Fields
            if (acc.username.isNotEmpty()) {
                DetailField(
                    label    = stringResource(R.string.addedit_username_email),
                    value    = acc.username,
                    icon     = Icons.Default.Person,
                    onCopy   = {
                        viewModel.copyToClipboard(context, "Username", acc.username)
                        copiedLabel = "Username"
                    }
                )
            }

            // Password field (masked by default)
            DetailFieldPassword(
                value       = acc.password,
                showPassword = showPassword,
                onToggle    = { showPassword = !showPassword },
                onCopy      = {
                    viewModel.copyToClipboard(context, "Password", acc.password)
                    copiedLabel = "Password"
                }
            )

            if (acc.notes.isNotEmpty()) {
                DetailField(
                    label  = stringResource(R.string.detail_field_notes),
                    value  = acc.notes,
                    icon   = Icons.AutoMirrored.Filled.Notes,
                    onCopy = {
                        viewModel.copyToClipboard(context, context.getString(R.string.detail_field_notes), acc.notes)
                        copiedLabel = context.getString(R.string.detail_field_notes)
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text(stringResource(R.string.detail_delete_title)) },
            text    = { Text(stringResource(R.string.detail_delete_confirm, acc.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(acc)
                        showDeleteDialog = false
                        onDeleted()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DetailField(
    label:  String,
    value:  String,
    icon:   androidx.compose.ui.graphics.vector.ImageVector,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, null, tint = VaultOnSurface, modifier = Modifier.padding(top = 2.dp).size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label,
                     style = MaterialTheme.typography.labelSmall,
                     color = VaultOnSurface,
                     fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(value,
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.detail_copy_cd),
                     tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DetailFieldPassword(
    value:       String,
    showPassword: Boolean,
    onToggle:    () -> Unit,
    onCopy:      () -> Unit
) {
    val displayValue = if (showPassword) value else "•".repeat(value.length.coerceAtMost(20))

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
            Icon(Icons.Default.Key, null,
                 tint = VaultOnSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_field_password),
                     style = MaterialTheme.typography.labelSmall,
                     color = VaultOnSurface, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    displayValue,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (!showPassword) androidx.compose.ui.text.font.FontFamily.Monospace
                                 else androidx.compose.ui.text.font.FontFamily.Default
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    stringResource(R.string.detail_show_hide_cd),
                    tint = VaultOnSurface, modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.detail_copy_cd),
                     tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
    return sdf.format(Date(timestamp))
}

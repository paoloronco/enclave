package com.paoloronco.codevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paoloronco.codevault.R
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.data.EntryType
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.VaultError
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val trashAccounts by viewModel.trashAccounts.collectAsStateWithLifecycle(emptyList())

    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (trashAccounts.isNotEmpty()) {
                        TextButton(
                            onClick = { showEmptyTrashDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.trash_empty_button),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.trash_empty_button))
                        }
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
        ) {
            if (trashAccounts.isEmpty()) {
                TrashEmptyState()
            } else {
                val retentionDays = viewModel.getTrashRetentionDays()
                // Info banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (retentionDays > 0)
                                stringResource(R.string.trash_banner_info_days, retentionDays)
                            else
                                stringResource(R.string.trash_banner_info_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trashAccounts, key = { it.id }) { account ->
                        TrashItemCard(
                            account = account,
                            retentionDays = retentionDays,
                            onRestore = {
                                viewModel.restoreAccount(account)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.trash_restored_snackbar, account.title)
                                    )
                                }
                            },
                            onDeletePermanently = {
                                deleteTarget = account
                            }
                        )
                    }
                }
            }
        }
    }

    // Single item permanent delete confirmation
    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.trash_delete_item_title)) },
            text = { Text(stringResource(R.string.trash_delete_item_confirm, account.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePermanently(account)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VaultError)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Empty entire trash confirmation
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VaultError)
                ) { Text(stringResource(R.string.trash_empty_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TrashItemCard(
    account: AccountEntity,
    retentionDays: Int,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val catColor = categoryColor(account.category)
    val typeEmoji = EntryType.ALL.find { it.key == account.type }?.emoji ?: "🔑"
    val daysLeft = calculateDaysLeft(account.deletedAt, retentionDays)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(catColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(typeEmoji, fontSize = 20.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val expirationText = when {
                    daysLeft < 0  -> stringResource(R.string.trash_no_expiration)
                    daysLeft == 1 -> stringResource(R.string.trash_days_left_one)
                    else          -> stringResource(R.string.trash_days_left, daysLeft)
                }
                Text(
                    expirationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (daysLeft < 0) VaultOnSurface else VaultError,
                    fontSize = 12.sp
                )
            }

            // Actions
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = stringResource(R.string.trash_restore),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDeletePermanently) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.trash_delete_forever),
                    tint = VaultError,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TrashEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🗑️", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.trash_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.trash_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = VaultOnSurface,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )
    }
}

private fun calculateDaysLeft(deletedAt: Long?, retentionDays: Int): Int {
    if (deletedAt == null || retentionDays <= 0) return -1
    val elapsedMs = System.currentTimeMillis() - deletedAt
    val elapsedDays = (elapsedMs / (1000L * 60 * 60 * 24)).toInt()
    return maxOf(0, retentionDays - elapsedDays)
}
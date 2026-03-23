package com.paoloronco.codevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.paoloronco.codevault.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.data.Categories
import com.paoloronco.codevault.data.EntryType
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel:       MainViewModel,
    onAddClick:      (String) -> Unit,   // receives EntryType key
    onItemClick:     (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context     = LocalContext.current
    val accounts    by viewModel.accounts.collectAsStateWithLifecycle(emptyList())
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Enclave",
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.lock() }) {
                        Icon(Icons.Default.Lock, stringResource(R.string.home_lock_cd), tint = VaultOnSurface)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, stringResource(R.string.home_settings_cd), tint = VaultOnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                shape          = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.home_add_cd))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder   = { Text(stringResource(R.string.home_search_hint), color = VaultOnSurface) },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = VaultOnSurface) },
                trailingIcon  = if (searchQuery.isNotEmpty()) ({
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, null, tint = VaultOnSurface)
                    }
                }) else null,
                singleLine    = true,
                shape         = RoundedCornerShape(16.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (accounts.isEmpty()) {
                EmptyState(hasSearch = searchQuery.isNotEmpty())
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pluralStringResource(R.plurals.entries_count, accounts.size, accounts.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultOnSurface
                    )
                }
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        SwipeToDeleteItem(onDelete = { deleteTarget = account }) {
                            AccountCard(
                                account = account,
                                onClick = { onItemClick(account.id) },
                                onCopy  = {
                                    viewModel.copyToClipboard(context, account.title, account.password)
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    // ── Add-entry bottom sheet ─────────────────────────────────────
    if (showAddSheet) {
        AddEntrySheet(
            onSelect  = { type -> showAddSheet = false; onAddClick(type) },
            onDismiss = { showAddSheet = false }
        )
    }

    // ── Delete confirmation ────────────────────────────────────────
    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title   = { Text(stringResource(R.string.home_delete_title)) },
            text    = { Text(stringResource(R.string.home_delete_confirm, account.title)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAccount(account); deleteTarget = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = VaultError)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ── Add entry type bottom sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntrySheet(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.home_add_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 20.dp)
            )
            EntryType.ALL.forEach { meta ->
                EntryTypeRow(
                    emoji    = meta.emoji,
                    title    = entryTypeLabel(meta.key),
                    subtitle = entryTypeSubtitle(meta.key),
                    onClick  = { onSelect(meta.key) }
                )
                if (meta != EntryType.ALL.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryTypeRow(
    emoji:    String,
    title:    String,
    subtitle: String,
    onClick:  () -> Unit
) {
    Surface(
        onClick = onClick,
        color   = Color.Transparent,
        shape   = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultOnSurface
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint     = VaultOnSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Account card ────────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
    account: AccountEntity,
    onClick: () -> Unit,
    onCopy:  () -> Unit
) {
    val catColor = categoryColor(account.category)
    val typeEmoji = EntryType.ALL.find { it.key == account.type }?.emoji ?: "🔑"

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
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

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
                    if (account.isFavorite) {
                        Icon(
                            Icons.Default.Star, null,
                            tint     = VaultGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                val sub = when (account.type) {
                    EntryType.ACCOUNT -> account.username
                    EntryType.PIN     -> "••••"
                    EntryType.NOTE    -> account.notes.take(40)
                    EntryType.WIFI    -> account.username   // SSID stored in username
                    else              -> account.username
                }
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = VaultOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    account.category,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = catColor.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }

            // Copy button (hidden for notes)
            if (account.type != EntryType.NOTE) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        stringResource(R.string.home_copy_cd),
                        tint     = VaultOnSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ── Swipe to delete ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange  = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
        positionalThreshold = { it * 0.4f }
    )
    SwipeToDismissBox(
        state             = state,
        backgroundContent = {
            val pct = state.progress
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VaultError.copy(alpha = (pct * 2f).coerceAtMost(1f))),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete, stringResource(R.string.home_delete_cd),
                    tint     = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        content = { content() }
    )
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasSearch: Boolean) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (hasSearch) "🔍" else "🔐", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (hasSearch) stringResource(R.string.home_no_results)
            else           stringResource(R.string.home_empty_vault),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasSearch) stringResource(R.string.home_try_different_search)
            else           stringResource(R.string.home_empty_vault_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = VaultOnSurface
        )
    }
}

// ── Category helpers ─────────────────────────────────────────────────────────

fun categoryColor(category: String): Color = when (category) {
    Categories.GENERAL  -> CatGeneral
    Categories.SOCIAL   -> CatSocial
    Categories.BANKING  -> CatBanking
    Categories.EMAIL    -> CatEmail
    Categories.WORK     -> CatWork
    Categories.SHOPPING -> CatShopping
    else                -> CatOther
}

fun categoryIcon(category: String): ImageVector = when (category) {
    Categories.GENERAL  -> Icons.Default.Lock
    Categories.SOCIAL   -> Icons.Default.People
    Categories.BANKING  -> Icons.Default.AccountBalance
    Categories.EMAIL    -> Icons.Default.Email
    Categories.WORK     -> Icons.Default.Work
    Categories.SHOPPING -> Icons.Default.ShoppingCart
    else                -> Icons.Default.Category
}

@Composable
fun entryTypeLabel(key: String): String = when (key) {
    EntryType.ACCOUNT -> stringResource(R.string.entry_type_account)
    EntryType.PIN     -> stringResource(R.string.entry_type_pin)
    EntryType.NOTE    -> stringResource(R.string.entry_type_note)
    EntryType.WIFI    -> stringResource(R.string.entry_type_wifi)
    else              -> key
}

@Composable
fun entryTypeSubtitle(key: String): String = when (key) {
    EntryType.ACCOUNT -> stringResource(R.string.entry_type_account_sub)
    EntryType.PIN     -> stringResource(R.string.entry_type_pin_sub)
    EntryType.NOTE    -> stringResource(R.string.entry_type_note_sub)
    EntryType.WIFI    -> stringResource(R.string.entry_type_wifi_sub)
    else              -> ""
}

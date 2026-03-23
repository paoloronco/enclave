package com.paoloronco.codevault.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import com.paoloronco.codevault.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paoloronco.codevault.backup.BackupManager
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.data.AppDatabase
import com.paoloronco.codevault.security.SecurityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed class BackupResult {
    object Idle    : BackupResult()
    object Success : BackupResult()
    data class Error(val message: String) : BackupResult()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val securityManager = SecurityManager(application)
    private val db      = AppDatabase.getDatabase(application)
    private val dao     = db.accountDao()
    private val backup  = BackupManager(application)

    // ---- Auth state ----
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // ---- Search ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts = _searchQuery.flatMapLatest { q ->
        if (q.isBlank()) dao.getAllAccounts() else dao.searchAccounts(q)
    }.map { list -> list.map { it.decrypted() } }

    // ---- Backup feedback ----
    private val _backupResult = MutableStateFlow<BackupResult>(BackupResult.Idle)
    val backupResult: StateFlow<BackupResult> = _backupResult.asStateFlow()

    // ---- Auto-lock ----
    private var autoLockJob: Job? = null

    init {
        if (securityManager.isPasscodeSet()) startAutoLockTimer()
    }

    // ---- Auth ----

    fun unlock(passcode: String): Boolean {
        return if (securityManager.verifyPasscode(passcode)) {
            _isUnlocked.value = true
            resetAutoLockTimer()
            true
        } else false
    }

    fun unlockBiometric() {
        _isUnlocked.value = true
        resetAutoLockTimer()
    }

    fun setupPasscode(passcode: String, authType: String = "pin") {
        securityManager.savePasscode(passcode)
        securityManager.setAuthType(authType)
        _isUnlocked.value = true
        startAutoLockTimer()
    }

    fun lock() {
        autoLockJob?.cancel()
        if (securityManager.isPasscodeSet()) _isUnlocked.value = false
    }

    fun isPasscodeSet(): Boolean = securityManager.isPasscodeSet()

    // ---- Onboarding ----

    fun isOnboardingComplete(): Boolean = securityManager.isOnboardingComplete()
    fun completeOnboarding() = securityManager.setOnboardingComplete()

    // ---- Auto-lock timer ----

    fun resetAutoLockTimer() {
        val timeout = securityManager.getAutoLockTimeout()
        if (timeout == SecurityManager.TIMEOUT_NEVER || !_isUnlocked.value) return
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            delay(timeout)
            lock()
        }
    }

    private fun startAutoLockTimer() = resetAutoLockTimer()

    // ---- CRUD ----

    fun addAccount(account: AccountEntity) =
        viewModelScope.launch { dao.insertAccount(account.encrypted()) }

    fun updateAccount(account: AccountEntity) =
        viewModelScope.launch { dao.updateAccount(account.encrypted()) }

    fun deleteAccount(account: AccountEntity) =
        viewModelScope.launch { dao.deleteAccount(account) }

    suspend fun getAccountById(id: Long): AccountEntity? =
        dao.getAccountById(id)?.decrypted()

    // ---- Encryption helpers ----

    private fun AccountEntity.encrypted() = copy(
        title    = securityManager.encryptField(title),
        username = securityManager.encryptField(username),
        password = securityManager.encryptField(password),
        notes    = securityManager.encryptField(notes)
    )

    private fun AccountEntity.decrypted() = copy(
        title    = securityManager.decryptField(title),
        username = securityManager.decryptField(username),
        password = securityManager.decryptField(password),
        notes    = securityManager.decryptField(notes)
    )

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    // ---- Clipboard ----

    fun copyToClipboard(context: Context, label: String, text: String) {
        val mgr  = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        mgr.setPrimaryClip(clip)
        // Auto-clear after 30 s
        viewModelScope.launch {
            delay(30_000L)
            mgr.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    // ---- Backup ----

    fun exportBackup(outputUri: Uri, passcode: String) {
        viewModelScope.launch {
            try {
                val accounts = dao.getAllAccountsOnce().map { it.decrypted() }
                backup.exportBackup(accounts, passcode, outputUri)
                _backupResult.value = BackupResult.Success
            } catch (e: Exception) {
                _backupResult.value = BackupResult.Error(e.message ?: getApplication<Application>().getString(R.string.backup_error_export))
            }
        }
    }

    fun importBackup(inputUri: Uri, passcode: String, replace: Boolean = false) {
        viewModelScope.launch {
            try {
                val accounts = backup.importBackup(inputUri, passcode)
                if (replace) dao.deleteAll()
                dao.insertAll(accounts.map { it.encrypted() })
                _backupResult.value = BackupResult.Success
            } catch (e: Exception) {
                _backupResult.value = BackupResult.Error(e.message ?: getApplication<Application>().getString(R.string.backup_error_import))
            }
        }
    }

    fun clearBackupResult() { _backupResult.value = BackupResult.Idle }

    // ---- Vault reset ----

    fun resetVault() {
        autoLockJob?.cancel()
        viewModelScope.launch {
            dao.deleteAll()
            securityManager.resetVault()
            _isUnlocked.value = false
        }
    }
}

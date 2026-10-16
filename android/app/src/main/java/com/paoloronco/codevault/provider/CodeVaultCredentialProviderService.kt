package com.paoloronco.codevault.provider

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.*
import androidx.credentials.exceptions.*
import androidx.credentials.provider.*
import com.paoloronco.codevault.R
import com.paoloronco.codevault.data.AppDatabase
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.security.SecurityManager
import kotlinx.coroutines.*

@RequiresApi(34)
class CodeVaultCredentialProviderService : CredentialProviderService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        scope.launch {
            try {
                val response = processGetCredential(request)
                callback.onResult(response)
            } catch (e: Exception) {
                Log.e("EnclaveProvider", "Error in onBeginGetCredential", e)
                callback.onError(GetCredentialUnknownException(e.message))
            }
        }
    }

    private suspend fun processGetCredential(request: BeginGetCredentialRequest): BeginGetCredentialResponse {
        val responseBuilder = BeginGetCredentialResponse.Builder()
        
        val passwordOption = request.beginGetCredentialOptions
            .filterIsInstance<BeginGetPasswordOption>()
            .firstOrNull()

        if (passwordOption != null) {
            val db = AppDatabase.getDatabase(this)
            val securityManager = SecurityManager(this)
            val accounts = db.accountDao().getAllAccountsOnce()
            val icon = Icon.createWithResource(this, R.mipmap.ic_launcher)

            // Filter for account types and decrypt titles for display matching
            accounts.filter { it.type == "account" }.forEach { account ->
                val decryptedTitle = securityManager.decryptField(account.title)
                val decryptedUsername = securityManager.decryptField(account.username)

                val entry = PasswordCredentialEntry.Builder(
                    this,
                    decryptedUsername,
                    createPendingIntent(account.id),
                    passwordOption
                )
                .setDisplayName(decryptedTitle)
                .setIcon(icon)
                .build()
                
                responseBuilder.addCredentialEntry(entry)
            }
        }

        return responseBuilder.build()
    }

    private fun createPendingIntent(accountId: Long): PendingIntent {
        val intent = Intent(this, CredentialEntryActivity::class.java).apply {
            putExtra("ACCOUNT_ID", accountId)
        }
        return PendingIntent.getActivity(
            this,
            accountId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        if (request is BeginCreatePasswordCredentialRequest) {
            val responseBuilder = BeginCreateCredentialResponse.Builder()
            val intent = Intent(this, CreateCredentialActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val entry = CreateEntry.Builder(
                "Salva in Enclave",
                pendingIntent
            )
            .build()
            
            responseBuilder.addCreateEntry(entry)
            callback.onResult(responseBuilder.build())
        } else {
            callback.onResult(BeginCreateCredentialResponse.Builder().build())
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

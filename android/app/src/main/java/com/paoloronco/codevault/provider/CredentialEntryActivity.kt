package com.paoloronco.codevault.provider

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.credentials.PasswordCredential
import androidx.credentials.GetCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.screens.LockScreen
import com.paoloronco.codevault.ui.theme.CodeVaultTheme

import androidx.activity.enableEdgeToEdge

class CredentialEntryActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val accountId = intent.getLongExtra("ACCOUNT_ID", -1L)
        if (accountId == -1L) {
            finish()
            return
        }

        setContent {
            CodeVaultTheme {
                val viewModel: MainViewModel = viewModel()
                val isUnlocked by viewModel.isUnlocked.collectAsState()

                if (isUnlocked) {
                    LaunchedEffect(Unit) {
                        val account = viewModel.getAccountById(accountId)
                        if (account != null) {
                            val response = GetCredentialResponse(
                                PasswordCredential(account.username, account.password)
                            )
                            val resultIntent = Intent()
                            PendingIntentHandler.setGetCredentialResponse(resultIntent, response)
                            setResult(RESULT_OK, resultIntent)
                        } else {
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                    }
                } else {
                    LockScreen(
                        viewModel = viewModel,
                        onUnlocked = { viewModel.unlockBiometric() }
                    )
                }
            }
        }
    }
}

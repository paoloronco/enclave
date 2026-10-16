package com.paoloronco.codevault.provider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CreateCustomCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paoloronco.codevault.data.AccountEntity
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.screens.LockScreen
import com.paoloronco.codevault.ui.theme.CodeVaultTheme

import androidx.activity.enableEdgeToEdge

class CreateCredentialActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val createRequest = try {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } catch (e: Exception) {
            Log.e("CreateCredential", "Failed to retrieve request", e)
            null
        }

        val passwordRequest = createRequest?.callingRequest as? CreatePasswordRequest
        val username = passwordRequest?.id ?: ""
        val password = passwordRequest?.password ?: ""

        if (username.isEmpty() || password.isEmpty()) {
            Log.w("CreateCredential", "Missing username or password in request")
        }

        setContent {
            CodeVaultTheme {
                val viewModel: MainViewModel = viewModel()
                val isUnlocked by viewModel.isUnlocked.collectAsState()

                if (isUnlocked) {
                    SaveCredentialScreen(
                        username = username,
                        onSave = { title ->
                            viewModel.addAccount(
                                AccountEntity(
                                    title = title,
                                    username = username,
                                    password = password
                                )
                            )
                            val resultIntent = Intent()
                            val response = CreateCustomCredentialResponse(
                                PasswordCredential.TYPE_PASSWORD_CREDENTIAL,
                                Bundle()
                            )
                            PendingIntentHandler.setCreateCredentialResponse(resultIntent, response)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
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

@Composable
fun SaveCredentialScreen(
    username: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Salva credenziale in Enclave", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titolo (es. Google, Facebook)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Nome utente: $username", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Annulla") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (title.isNotBlank()) onSave(title) }, enabled = title.isNotBlank()) {
                Text("Salva")
            }
        }
    }
}

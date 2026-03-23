package com.paoloronco.codevault.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paoloronco.codevault.R
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.VaultError
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    viewModel:       MainViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    // step 0 = choose type, 1 = enter, 2 = confirm
    var step       by remember { mutableIntStateOf(0) }
    var authType   by remember { mutableStateOf("pin") }
    var passcode   by remember { mutableStateOf("") }
    var confirm    by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf("") }

    val scope        = rememberCoroutineScope()
    val shakeOffset  = remember { Animatable(0f) }

    fun shake() = scope.launch {
        shakeOffset.snapTo(0f)
        shakeOffset.animateTo(0f, keyframes {
            durationMillis = 400
            12f at 60; -12f at 130; 8f at 210; -8f at 280; 0f at 400
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Logo ────────────────────────────────────────────────────
        Text("🔐", fontSize = 60.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Enclave",
            style      = MaterialTheme.typography.headlineLarge,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.setup_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = VaultOnSurface
        )
        Spacer(Modifier.height(48.dp))

        AnimatedContent(
            targetState  = step,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "setup_step"
        ) { currentStep ->
            when (currentStep) {

                // ── Step 0: choose auth type ─────────────────────────
                0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // No-recovery warning
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = VaultError.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier          = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint     = VaultError,
                                modifier = Modifier.size(18.dp).padding(top = 1.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.setup_no_recovery_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = VaultError
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.setup_choose_auth),
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AuthTypeCard(
                            title    = "PIN",
                            subtitle = stringResource(R.string.setup_pin_type_subtitle),
                            emoji    = "🔢",
                            selected = authType == "pin",
                            onClick  = { authType = "pin" }
                        )
                        AuthTypeCard(
                            title    = "Password",
                            subtitle = stringResource(R.string.setup_password_type_subtitle),
                            emoji    = "🔡",
                            selected = authType == "password",
                            onClick  = { authType = "password" }
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick  = { step = 1 },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.continue_btn), fontSize = 16.sp) }
                }

                // ── Step 1: enter code ────────────────────────────────
                1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (authType == "pin") stringResource(R.string.setup_set_pin)
                        else                   stringResource(R.string.setup_set_password),
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (authType == "pin") stringResource(R.string.setup_pin_hint)
                        else                   stringResource(R.string.setup_password_hint),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = VaultOnSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))

                    if (authType == "pin") {
                        // PIN via numpad
                        PinDots(
                            filled   = passcode.length,
                            total    = PIN_LENGTH,
                            modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                            if (errorMsg.isNotEmpty()) {
                                Text(errorMsg, color = VaultError, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        PinNumPad(
                            onDigit  = { d ->
                                if (passcode.length < PIN_LENGTH) {
                                    passcode += d
                                    if (passcode.length == PIN_LENGTH) {
                                        errorMsg = ""; step = 2
                                    }
                                }
                            },
                            onDelete = { if (passcode.isNotEmpty()) passcode = passcode.dropLast(1) }
                        )
                    } else {
                        // Password via text field
                        OutlinedTextField(
                            value         = passcode,
                            onValueChange = { passcode = it; errorMsg = "" },
                            label         = { Text(stringResource(R.string.settings_field_password)) },
                            singleLine    = true,
                            isError       = errorMsg.isNotEmpty(),
                            supportingText = if (errorMsg.isNotEmpty()) ({ Text(errorMsg) }) else null,
                            visualTransformation = if (showPass) VisualTransformation.None
                                                  else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon  = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(
                                        if (showPass) Icons.Default.VisibilityOff
                                        else          Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = VaultOnSurface
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                errorMsg = ""
                                when {
                                    passcode.length < 8 ->
                                        errorMsg = context.getString(R.string.setup_password_min_length)
                                    else -> step = 2
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            enabled  = passcode.isNotEmpty()
                        ) { Text(stringResource(R.string.next), fontSize = 16.sp) }
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { step = 0; passcode = ""; errorMsg = "" }) {
                        Text(stringResource(R.string.back), color = VaultOnSurface)
                    }
                }

                // ── Step 2: confirm ───────────────────────────────────
                2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (authType == "pin") stringResource(R.string.setup_confirm_pin)
                        else                   stringResource(R.string.setup_confirm_password),
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (authType == "pin") stringResource(R.string.setup_pin_confirm_hint)
                        else                   stringResource(R.string.setup_password_confirm_hint),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = VaultOnSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))

                    if (authType == "pin") {
                        PinDots(
                            filled   = confirm.length,
                            total    = PIN_LENGTH,
                            modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                            if (errorMsg.isNotEmpty()) {
                                Text(errorMsg, color = VaultError, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        PinNumPad(
                            onDigit  = { d ->
                                if (confirm.length < PIN_LENGTH) {
                                    confirm += d
                                    if (confirm.length == PIN_LENGTH) {
                                        if (confirm == passcode) {
                                            viewModel.setupPasscode(passcode, "pin")
                                            onSetupComplete()
                                        } else {
                                            errorMsg = context.getString(R.string.setup_pin_mismatch)
                                            shake()
                                            confirm = ""
                                        }
                                    }
                                }
                            },
                            onDelete = { if (confirm.isNotEmpty()) confirm = confirm.dropLast(1) }
                        )
                    } else {
                        OutlinedTextField(
                            value         = confirm,
                            onValueChange = { confirm = it; errorMsg = "" },
                            label         = { Text(stringResource(R.string.setup_confirm_password_label)) },
                            singleLine    = true,
                            isError       = errorMsg.isNotEmpty(),
                            supportingText = if (errorMsg.isNotEmpty()) ({ Text(errorMsg) }) else null,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = shakeOffset.value }
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                errorMsg = ""
                                if (confirm != passcode) {
                                    errorMsg = context.getString(R.string.setup_passwords_mismatch)
                                    shake()
                                    confirm = ""
                                } else {
                                    viewModel.setupPasscode(passcode, "password")
                                    onSetupComplete()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            enabled  = confirm.isNotEmpty()
                        ) { Text(stringResource(R.string.setup_create_vault), fontSize = 16.sp) }
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { step = 1; confirm = ""; errorMsg = "" }) {
                        Text(stringResource(R.string.back), color = VaultOnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTypeCard(
    title:    String,
    subtitle: String,
    emoji:    String,
    selected: Boolean,
    onClick:  () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.width(140.dp),
        shape     = RoundedCornerShape(18.dp),
        border    = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else          MaterialTheme.colorScheme.outline
        ),
        colors    = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = if (selected) MaterialTheme.colorScheme.primary
                             else          MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style     = MaterialTheme.typography.bodySmall,
                color     = VaultOnSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

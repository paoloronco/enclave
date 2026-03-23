package com.paoloronco.codevault.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.paoloronco.codevault.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.paoloronco.codevault.ui.MainViewModel
import com.paoloronco.codevault.ui.theme.VaultError
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import com.paoloronco.codevault.ui.theme.VaultTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val PIN_LENGTH   = 6
private const val MAX_ATTEMPTS  = 5
private const val LOCKOUT_SECS  = 30L

@Composable
fun LockScreen(
    viewModel:  MainViewModel,
    onUnlocked: () -> Unit
) {
    val context      = LocalContext.current
    val activity     = context as? FragmentActivity
    val scope        = rememberCoroutineScope()
    val authType     = remember { viewModel.securityManager.getAuthType() }  // "pin"|"password"
    val bioEnabled   = remember { viewModel.securityManager.isBiometricEnabled() }
    val bioAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }
    val showBio = bioEnabled && bioAvailable

    val isRooted = remember {
        arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/su", "/system/app/Superuser.apk", "/system/app/SuperSU.apk"
        ).any { java.io.File(it).exists() }
    }
    val strRootWarning  = stringResource(R.string.lock_root_warning)
    val strSubtitle     = stringResource(R.string.lock_subtitle)
    val strTooMany      = stringResource(R.string.lock_too_many_attempts)
    val strBeforeRetry  = stringResource(R.string.lock_before_retry)
    val strWrongLast    = stringResource(R.string.lock_wrong_last)
    val strWrongFmt     = stringResource(R.string.lock_wrong_attempts)
    val strUnlock       = stringResource(R.string.lock_unlock_button)
    val strFingerprint  = stringResource(R.string.lock_fingerprint)
    val strUsePin       = stringResource(R.string.lock_use_pin)
    val strUsePassword  = stringResource(R.string.lock_use_password)
    val strBioSubtitle  = stringResource(R.string.lock_bio_subtitle)

    // ── State ──────────────────────────────────────────────────────
    var pin           by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }
    var showPassword  by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf("") }
    var failedCount   by remember { mutableIntStateOf(0) }
    var lockedUntilMs by remember { mutableLongStateOf(0L) }
    var remainingSecs by remember { mutableIntStateOf(0) }
    var bioTriggered  by remember { mutableStateOf(false) }

    val shakeOffset   = remember { Animatable(0f) }
    val passwordFocus = remember { FocusRequester() }
    val isLockedOut   = remainingSecs > 0

    // ── Lockout countdown ──────────────────────────────────────────
    LaunchedEffect(lockedUntilMs) {
        if (lockedUntilMs == 0L) return@LaunchedEffect
        while (true) {
            val diff = lockedUntilMs - System.currentTimeMillis()
            if (diff <= 0) { remainingSecs = 0; break }
            remainingSecs = (diff / 1000).toInt() + 1
            delay(200L)
        }
    }

    // ── Shake on wrong attempt ─────────────────────────────────────
    fun shake() = scope.launch {
        shakeOffset.snapTo(0f)
        shakeOffset.animateTo(
            targetValue   = 0f,
            animationSpec = keyframes {
                durationMillis = 450
                 14f at  50
                -14f at 120
                 10f at 200
                -10f at 270
                  5f at 350
                  0f at 450
            }
        )
    }

    // ── Wrong attempt ─────────────────────────────────────────────
    fun onWrongAttempt() {
        failedCount++
        shake()
        pin = ""; password = ""
        if (failedCount >= MAX_ATTEMPTS) {
            lockedUntilMs = System.currentTimeMillis() + LOCKOUT_SECS * 1000L
            failedCount   = 0
            errorMsg      = ""
        } else {
            val left = MAX_ATTEMPTS - failedCount
            errorMsg = if (left == 1) strWrongLast
                       else           strWrongFmt.format(left)
        }
    }

    // ── Unlock attempt ─────────────────────────────────────────────
    fun tryUnlock(code: String) {
        if (isLockedOut || code.isBlank()) return
        if (viewModel.unlock(code)) onUnlocked() else onWrongAttempt()
    }

    // ── Auto-trigger biometric on first render ─────────────────────
    LaunchedEffect(Unit) {
        if (showBio && !bioTriggered) {
            bioTriggered = true
            delay(350L)
            triggerBiometric(
                activity     = activity,
                subtitle     = strBioSubtitle,
                negativeText = if (authType == "pin") strUsePin else strUsePassword,
                onSuccess    = { viewModel.unlockBiometric(); onUnlocked() },
                onError      = { code, msg ->
                    // Ignore user-cancelled / negative-button presses
                    if (code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        code != BiometricPrompt.ERROR_USER_CANCELED    &&
                        code != BiometricPrompt.ERROR_CANCELED) {
                        errorMsg = msg
                    }
                }
            )
        }
        if (authType == "password") {
            delay(300L)
            runCatching { passwordFocus.requestFocus() }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Root warning banner
        if (isRooted) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = VaultError.copy(alpha = 0.12f)
                )
            ) {
                Text(
                    strRootWarning,
                    modifier  = Modifier.padding(12.dp),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = VaultError,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Header
        Text("🔐", fontSize = 60.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Enclave",
            style      = MaterialTheme.typography.headlineMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            strSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = VaultOnSurface
        )
        Spacer(Modifier.height(40.dp))

        // ── Lockout banner ─────────────────────────────────────────
        AnimatedVisibility(visible = isLockedOut, enter = fadeIn(), exit = fadeOut()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = VaultError.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier            = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strTooMany,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = VaultError,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${remainingSecs}s",
                        style      = MaterialTheme.typography.displaySmall,
                        color      = VaultError,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        strBeforeRetry,
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultError.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // ── PIN numpad ─────────────────────────────────────────────
        AnimatedVisibility(visible = !isLockedOut && authType == "pin") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinDots(
                    filled   = pin.length,
                    total    = PIN_LENGTH,
                    modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
                )
                Spacer(Modifier.height(10.dp))
                // Fixed-height error slot to avoid layout jump
                Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                    if (errorMsg.isNotEmpty()) {
                        Text(
                            errorMsg,
                            color     = VaultError,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                PinNumPad(
                    disabled    = isLockedOut,
                    showBio     = showBio,
                    onDigit     = { d ->
                        if (pin.length < PIN_LENGTH) {
                            pin += d
                            if (pin.length == PIN_LENGTH) tryUnlock(pin)
                        }
                    },
                    onDelete    = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onBiometric = {
                        triggerBiometric(
                            activity     = activity,
                            subtitle     = strBioSubtitle,
                            negativeText = strUsePin,
                            onSuccess    = { viewModel.unlockBiometric(); onUnlocked() },
                            onError      = { _, msg -> errorMsg = msg }
                        )
                    }
                )
            }
        }

        // ── Password field ─────────────────────────────────────────
        AnimatedVisibility(visible = !isLockedOut && authType == "password") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
            ) {
                OutlinedTextField(
                    value           = password,
                    onValueChange   = { password = it },
                    label           = { Text(stringResource(R.string.settings_field_password)) },
                    singleLine      = true,
                    isError         = errorMsg.isNotEmpty(),
                    supportingText  = if (errorMsg.isNotEmpty()) ({ Text(errorMsg) }) else null,
                    visualTransformation = if (showPassword) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { tryUnlock(password) }),
                    trailingIcon    = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff
                                else              Icons.Default.Visibility,
                                contentDescription = null,
                                tint = VaultOnSurface
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick  = { tryUnlock(password) },
                    enabled  = password.isNotEmpty() && !isLockedOut,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text(strUnlock, fontSize = 16.sp)
                }
                if (showBio) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick  = {
                            triggerBiometric(
                                activity     = activity,
                                subtitle     = strBioSubtitle,
                                negativeText = strUsePassword,
                                onSuccess    = { viewModel.unlockBiometric(); onUnlocked() },
                                onError      = { _, msg -> errorMsg = msg }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VaultTeal.copy(alpha = 0.15f),
                            contentColor   = VaultTeal
                        )
                    ) {
                        Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strFingerprint)
                    }
                }
            }
        }
    }
}

// ── BiometricPrompt helper (accessible from other screens) ──────────────────

fun triggerBiometric(
    activity:     FragmentActivity?,
    subtitle:     String = "",
    negativeText: String = "Cancel",
    onSuccess:    () -> Unit,
    onError:      (Int, String) -> Unit
) {
    if (activity == null) return
    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
            onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
            onError(errorCode, errString.toString())
        override fun onAuthenticationFailed() { /* single miss — system shows retry */ }
    }

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Enclave")
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeText)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(info)
}

package com.paoloronco.codevault.ui.screens

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.paoloronco.codevault.ui.theme.*
import kotlinx.coroutines.delay

// ── Language data ─────────────────────────────────────────────────────────────

private data class LangEntry(
    val code:          String,
    val flag:          String,
    val selfName:      String,
    // Welcome screen
    val welcomeWord:   String,
    val preposition:   String,   // "in" / "to" / "zu" / "a"
    val tagline:       String,
    val f1Title:       String,
    val f1Sub:         String,
    val f2Sub:         String,
    val f3Title:       String,
    val f3Sub:         String,
    val f4Title:       String,
    val f4Sub:         String,
    // Language screen
    val selectWord:    String,
    val selectSubtitle: String,
    val continueWord:  String
)

private val LANGUAGES = listOf(
    LangEntry(
        code        = "it",
        flag        = "🇮🇹",
        selfName    = "Italiano",
        welcomeWord = "Benvenuto",
        preposition = "in",
        tagline     = "Il gestore di password sicuro,\nprivato e 100% offline",
        f1Title     = "Sicurezza AES-256",
        f1Sub       = "Tutti i dati cifrati con AES-256-GCM e Android Keystore",
        f2Sub       = "Nessun dato inviato in rete, mai",
        f3Title     = "Accesso biometrico",
        f3Sub       = "Sblocca con impronta digitale o PIN",
        f4Title     = "Tutto organizzato",
        f4Sub       = "Password, PIN, note WiFi e molto altro",
        selectWord   = "Seleziona lingua",
        selectSubtitle = "Scegli la tua lingua preferita",
        continueWord = "Continua"
    ),
    LangEntry(
        code        = "en",
        flag        = "🇬🇧",
        selfName    = "English",
        welcomeWord = "Welcome",
        preposition = "to",
        tagline     = "The secure, private,\n100% offline password manager",
        f1Title     = "AES-256 Security",
        f1Sub       = "All data encrypted with AES-256-GCM and Android Keystore",
        f2Sub       = "No data sent online, ever",
        f3Title     = "Biometric access",
        f3Sub       = "Unlock with fingerprint or PIN",
        f4Title     = "Everything organized",
        f4Sub       = "Passwords, PINs, WiFi notes and much more",
        selectWord   = "Select language",
        selectSubtitle = "Choose your preferred language",
        continueWord = "Continue"
    ),
    LangEntry(
        code        = "de",
        flag        = "🇩🇪",
        selfName    = "Deutsch",
        welcomeWord = "Willkommen",
        preposition = "zu",
        tagline     = "Der sichere, private und\n100% offline Passwort-Manager",
        f1Title     = "AES-256 Sicherheit",
        f1Sub       = "Alle Daten mit AES-256-GCM und Android Keystore verschlüsselt",
        f2Sub       = "Keine Daten werden ins Netz gesendet, nie",
        f3Title     = "Biometrischer Zugang",
        f3Sub       = "Mit Fingerabdruck oder PIN entsperren",
        f4Title     = "Alles organisiert",
        f4Sub       = "Passwörter, PINs, WLAN-Notizen und vieles mehr",
        selectWord   = "Sprache wählen",
        selectSubtitle = "Wähle deine bevorzugte Sprache",
        continueWord = "Weiter"
    ),
    LangEntry(
        code        = "es",
        flag        = "🇪🇸",
        selfName    = "Español",
        welcomeWord = "Bienvenido",
        preposition = "a",
        tagline     = "El gestor de contraseñas seguro,\nprivado y 100% sin conexión",
        f1Title     = "Seguridad AES-256",
        f1Sub       = "Todos los datos cifrados con AES-256-GCM y Android Keystore",
        f2Sub       = "Ningún dato enviado a la red, jamás",
        f3Title     = "Acceso biométrico",
        f3Sub       = "Desbloquea con huella dactilar o PIN",
        f4Title     = "Todo organizado",
        f4Sub       = "Contraseñas, PINs, notas WiFi y mucho más",
        selectWord   = "Selecciona idioma",
        selectSubtitle = "Elige tu idioma preferido",
        continueWord = "Continuar"
    ),
)

// ── Onboarding flow (welcome → language) ─────────────────────────────────────

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (targetState > initialState)
                (slideInHorizontally(tween(380)) { it } + fadeIn(tween(380))) togetherWith
                (slideOutHorizontally(tween(380)) { -it } + fadeOut(tween(280)))
            else
                (slideInHorizontally(tween(380)) { -it } + fadeIn(tween(380))) togetherWith
                (slideOutHorizontally(tween(380)) { it } + fadeOut(tween(280)))
        },
        label = "onboarding_step"
    ) { s ->
        when (s) {
            0    -> WelcomeScreen(onContinue = { step = 1 })
            else -> LanguageSelectionScreen(onComplete = onComplete)
        }
    }
}

// ── Welcome screen ────────────────────────────────────────────────────────────

@Composable
internal fun WelcomeScreen(onContinue: () -> Unit) {
    val initialIndex = remember {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: ""
        LANGUAGES.indexOfFirst { it.code == tag }.takeIf { it >= 0 } ?: 0
    }
    var langIndex by remember { mutableIntStateOf(initialIndex) }

    // Single timer drives all text — same language everywhere
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200L)
            langIndex = (langIndex + 1) % LANGUAGES.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(52.dp))

            // App icon — static
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(VaultTeal.copy(alpha = 0.12f))
                    .border(1.5.dp, VaultTeal.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint     = VaultTeal,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Welcome word — FAST (snap) ────────────────────────────────
            AnimatedContent(
                targetState = langIndex,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 4 })
                },
                label = "welcome_word"
            ) { idx ->
                Text(
                    text       = LANGUAGES[idx].welcomeWord,
                    fontSize   = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color      = VaultTeal,
                    textAlign  = TextAlign.Center
                )
            }

            // ── "in/to/zu/a Enclave" — MEDIUM ──────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = langIndex,
                    transitionSpec = {
                        (fadeIn(tween(650)) + slideInVertically(tween(650)) { it / 3 }) togetherWith
                        (fadeOut(tween(500)) + slideOutVertically(tween(500)) { -it / 4 })
                    },
                    label = "preposition"
                ) { idx ->
                    Text(
                        text       = LANGUAGES[idx].preposition,
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text       = " Enclave",
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Tagline — SLOW fade ───────────────────────────────────────
            AnimatedContent(
                targetState = langIndex,
                transitionSpec = {
                    fadeIn(tween(900)) togetherWith fadeOut(tween(700))
                },
                label = "tagline"
            ) { idx ->
                Text(
                    text       = LANGUAGES[idx].tagline,
                    fontSize   = 15.sp,
                    color      = VaultOnSurface,
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            // ── Feature rows — SLOWEST fade (all at once) ─────────────────
            AnimatedContent(
                targetState = langIndex,
                transitionSpec = {
                    fadeIn(tween(1100)) togetherWith fadeOut(tween(850))
                },
                label = "features"
            ) { idx ->
                val lang = LANGUAGES[idx]
                Column {
                    OnboardingFeatureRow(
                        icon      = Icons.Default.Lock,
                        iconColor = VaultTeal,
                        title     = lang.f1Title,
                        subtitle  = lang.f1Sub
                    )
                    OnboardingFeatureRow(
                        icon      = Icons.Default.CloudOff,
                        iconColor = CatBanking,
                        title     = "100% Offline",
                        subtitle  = lang.f2Sub
                    )
                    OnboardingFeatureRow(
                        icon      = Icons.Default.Fingerprint,
                        iconColor = CatSocial,
                        title     = lang.f3Title,
                        subtitle  = lang.f3Sub
                    )
                    OnboardingFeatureRow(
                        icon      = Icons.Default.Category,
                        iconColor = CatWork,
                        title     = lang.f4Title,
                        subtitle  = lang.f4Sub
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // ── Continue button — animates with language index ─────────────
        Button(
            onClick  = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .height(56.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultTeal)
        ) {
            AnimatedContent(
                targetState   = langIndex,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
                label         = "continue_word"
            ) { idx ->
                Text(LANGUAGES[idx].continueWord, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Language selection screen ─────────────────────────────────────────────────

@Composable
internal fun LanguageSelectionScreen(onComplete: () -> Unit) {
    val initialSelected = remember {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: ""
        LANGUAGES.indexOfFirst { it.code == tag }.takeIf { it >= 0 } ?: 0
    }
    var titleIndex by remember { mutableIntStateOf(initialSelected) }
    var selected   by remember { mutableIntStateOf(initialSelected) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000L)
            titleIndex = (titleIndex + 1) % LANGUAGES.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // Animated title
        AnimatedContent(
            targetState = titleIndex,
            transitionSpec = {
                (fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) togetherWith
                (fadeOut(tween(350)) + slideOutVertically(tween(350)) { -it / 4 })
            },
            label = "select_word"
        ) { idx ->
            Text(
                text       = LANGUAGES[idx].selectWord,
                fontSize   = 30.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = titleIndex,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) },
            label = "select_subtitle"
        ) { idx ->
            Text(
                text      = LANGUAGES[idx].selectSubtitle,
                fontSize  = 14.sp,
                color     = VaultOnSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(36.dp))

        LANGUAGES.forEachIndexed { i, lang ->
            LanguageOptionCard(
                entry      = lang,
                isSelected = selected == i,
                onClick    = { selected = i }
            )
            if (i < LANGUAGES.lastIndex) Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        // Continue button — label follows selected language
        val activity = LocalContext.current as? ComponentActivity
        Button(
            onClick = {
                // 1. Persist onboarding-complete BEFORE recreation
                onComplete()
                // 2. Persist locale preference
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(LANGUAGES[selected].code)
                )
                // 3. Always recreate — ensures locale applies even if same code was already set
                activity?.recreate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .height(56.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultTeal)
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
                },
                label = "continue_label"
            ) { idx ->
                Text(LANGUAGES[idx].continueWord, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingFeatureRow(
    icon:      ImageVector,
    iconColor: Color,
    title:     String,
    subtitle:  String
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title,    fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, fontSize = 13.sp, color = VaultOnSurface, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun LanguageOptionCard(
    entry:      LangEntry,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) VaultTeal else VaultBorder,
        label       = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) VaultTeal.copy(alpha = 0.08f) else VaultCard,
        label       = "bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.flag, fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                text       = entry.selfName,
                fontSize   = 17.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isSelected) VaultTeal else MaterialTheme.colorScheme.onBackground
            )
            if (isSelected) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Check, null, tint = VaultTeal, modifier = Modifier.size(20.dp))
            }
        }
    }
}

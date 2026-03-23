package com.paoloronco.codevault.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VaultColorScheme = darkColorScheme(
    primary            = VaultTeal,
    onPrimary          = VaultNavy,
    primaryContainer   = VaultTealDark,
    onPrimaryContainer = VaultTealLight,
    secondary          = VaultTealLight,
    onSecondary        = VaultNavy,
    background         = VaultNavy,
    onBackground       = VaultOnBg,
    surface            = VaultSurface,
    onSurface          = VaultOnBg,
    surfaceVariant     = VaultCard,
    onSurfaceVariant   = VaultOnSurface,
    outline            = VaultBorder,
    error              = VaultError,
    onError            = VaultNavy,
)

@Composable
fun CodeVaultTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography  = Typography,
        content     = content
    )
}

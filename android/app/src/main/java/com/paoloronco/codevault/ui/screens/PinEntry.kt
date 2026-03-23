package com.paoloronco.codevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paoloronco.codevault.ui.theme.VaultCard
import com.paoloronco.codevault.ui.theme.VaultOnSurface
import com.paoloronco.codevault.ui.theme.VaultTeal

/** Dot row that shows filled/empty circles for each PIN digit. */
@Composable
fun PinDots(
    filled:   Int,
    total:    Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val active = i < filled
            Box(
                modifier = Modifier
                    .size(if (active) 18.dp else 14.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else        MaterialTheme.colorScheme.outline
                    )
            )
        }
    }
}

/**
 * Full numeric keypad.
 * Layout: 1-2-3 / 4-5-6 / 7-8-9 / [bio?] 0 [⌫]
 */
@Composable
fun PinNumPad(
    disabled:    Boolean      = false,
    showBio:     Boolean      = false,
    onDigit:     (String) -> Unit,
    onDelete:    () -> Unit,
    onBiometric: (() -> Unit)? = null
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { d ->
                    PinKey(label = d, enabled = !disabled, onClick = { onDigit(d) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showBio && onBiometric != null) {
                PinKey(
                    icon    = Icons.Default.Fingerprint,
                    tint    = VaultTeal,
                    enabled = !disabled,
                    onClick = onBiometric
                )
            } else {
                Spacer(Modifier.size(74.dp))
            }
            PinKey(label = "0", enabled = !disabled, onClick = { onDigit("0") })
            PinKey(
                icon    = Icons.AutoMirrored.Filled.Backspace,
                tint    = MaterialTheme.colorScheme.onSurface,
                enabled = !disabled,
                onClick = onDelete
            )
        }
    }
}

/** Single circular key. */
@Composable
fun PinKey(
    label:   String? = null,
    icon:    ImageVector? = null,
    tint:    Color   = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick        = onClick,
        enabled        = enabled,
        modifier       = Modifier.size(74.dp),
        shape          = CircleShape,
        color          = VaultCard,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                label != null -> Text(
                    label,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color      = if (enabled) MaterialTheme.colorScheme.onSurface
                                 else VaultOnSurface
                )
                icon != null  -> Icon(
                    icon,
                    contentDescription = null,
                    tint     = if (enabled) tint else VaultOnSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

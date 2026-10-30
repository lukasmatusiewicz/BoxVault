package com.boxvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = VaultAmberPrimaryDark,
    onPrimary = VaultAmberOnPrimaryDark,
    primaryContainer = VaultAmberContainerDark,
    onPrimaryContainer = VaultAmberOnContainerDark,
    secondary = VaultCyanSecondaryDark,
    onSecondary = VaultCyanOnSecondaryDark,
    secondaryContainer = VaultCyanContainerDark,
    onSecondaryContainer = VaultCyanOnContainerDark,
    tertiary = VaultEmeraldTertiaryDark,
    onTertiary = VaultEmeraldOnTertiaryDark,
    tertiaryContainer = VaultEmeraldContainerDark,
    onTertiaryContainer = VaultEmeraldOnContainerDark,
    background = VaultBackgroundDark,
    onBackground = VaultOnBackgroundDark,
    surface = VaultSurfaceDark,
    onSurface = VaultOnSurfaceDark,
    surfaceVariant = VaultSurfaceVariantDark,
    onSurfaceVariant = VaultOnSurfaceVariantDark,
    outline = VaultOutlineDark
)

private val HighContrastLightColorScheme = lightColorScheme(
    primary = VaultAmberPrimaryLight,
    onPrimary = VaultAmberOnPrimaryLight,
    primaryContainer = VaultAmberContainerLight,
    onPrimaryContainer = VaultAmberOnContainerLight,
    secondary = VaultCyanSecondaryLight,
    onSecondary = VaultCyanOnSecondaryLight,
    secondaryContainer = VaultCyanContainerLight,
    onSecondaryContainer = VaultCyanOnContainerLight,
    tertiary = VaultEmeraldTertiaryLight,
    onTertiary = VaultEmeraldOnTertiaryLight,
    tertiaryContainer = VaultEmeraldContainerLight,
    onTertiaryContainer = VaultEmeraldOnContainerLight,
    background = VaultBackgroundLight,
    onBackground = VaultOnBackgroundLight,
    surface = VaultSurfaceLight,
    onSurface = VaultOnSurfaceLight,
    surfaceVariant = VaultSurfaceVariantLight,
    onSurfaceVariant = VaultOnSurfaceVariantLight,
    outline = VaultOutlineLight
)

@Composable
fun BoxVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        HighContrastDarkColorScheme
    } else {
        HighContrastLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BoxVaultTypography,
        content = content
    )
}

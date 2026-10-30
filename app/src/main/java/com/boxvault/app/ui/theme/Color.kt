package com.boxvault.app.ui.theme

import androidx.compose.ui.graphics.Color

// High-Contrast Industrial / Utility Palette optimized for dimly lit basements & garages
val VaultAmberPrimaryDark = Color(0xFFFBBF24)
val VaultAmberOnPrimaryDark = Color(0xFF111827)
val VaultAmberContainerDark = Color(0xFF78350F)
val VaultAmberOnContainerDark = Color(0xFFFEF3C7)

val VaultCyanSecondaryDark = Color(0xFF22D3EE)
val VaultCyanOnSecondaryDark = Color(0xFF083344)
val VaultCyanContainerDark = Color(0xFF164E63)
val VaultCyanOnContainerDark = Color(0xFFCFFAFE)

val VaultEmeraldTertiaryDark = Color(0xFF34D399)
val VaultEmeraldOnTertiaryDark = Color(0xFF064E3B)
val VaultEmeraldContainerDark = Color(0xFF065F46)
val VaultEmeraldOnContainerDark = Color(0xFFD1FAE5)

val VaultBackgroundDark = Color(0xFF090D16)
val VaultOnBackgroundDark = Color(0xFFF9FAFB)
val VaultSurfaceDark = Color(0xFF111827)
val VaultOnSurfaceDark = Color(0xFFF9FAFB)
val VaultSurfaceVariantDark = Color(0xFF1F2937)
val VaultOnSurfaceVariantDark = Color(0xFFE5E7EB)
val VaultOutlineDark = Color(0xFF4B5563)

// High-Contrast Light Palette
val VaultAmberPrimaryLight = Color(0xFFB45309)
val VaultAmberOnPrimaryLight = Color(0xFFFFFFFF)
val VaultAmberContainerLight = Color(0xFFFEF3C7)
val VaultAmberOnContainerLight = Color(0xFF451A03)

val VaultCyanSecondaryLight = Color(0xFF0E7490)
val VaultCyanOnSecondaryLight = Color(0xFFFFFFFF)
val VaultCyanContainerLight = Color(0xFFCFFAFE)
val VaultCyanOnContainerLight = Color(0xFF083344)

val VaultEmeraldTertiaryLight = Color(0xFF047857)
val VaultEmeraldOnTertiaryLight = Color(0xFFFFFFFF)
val VaultEmeraldContainerLight = Color(0xFFD1FAE5)
val VaultEmeraldOnContainerLight = Color(0xFF064E3B)

val VaultBackgroundLight = Color(0xFFF8FAFC)
val VaultOnBackgroundLight = Color(0xFF0F172A)
val VaultSurfaceLight = Color(0xFFFFFFFF)
val VaultOnSurfaceLight = Color(0xFF0F172A)
val VaultSurfaceVariantLight = Color(0xFFE2E8F0)
val VaultOnSurfaceVariantLight = Color(0xFF1E293B)
val VaultOutlineLight = Color(0xFF64748B)

data class BoxColorOption(
    val label: String,
    val hex: String,
    val color: Color
)

val DefaultBoxColorOptions: List<BoxColorOption> = listOf(
    BoxColorOption("Safety Amber", "#F59E0B", Color(0xFFF59E0B)),
    BoxColorOption("Emerald Green", "#10B981", Color(0xFF10B981)),
    BoxColorOption("Cobalt Blue", "#3B82F6", Color(0xFF3B82F6)),
    BoxColorOption("Crimson Red", "#EF4444", Color(0xFFEF4444)),
    BoxColorOption("Cyan Utility", "#06B6D4", Color(0xFF06B6D4)),
    BoxColorOption("Violet Tag", "#8B5CF6", Color(0xFF8B5CF6)),
    BoxColorOption("High-Vis Orange", "#F97316", Color(0xFFF97316)),
    BoxColorOption("Steel Slate", "#64748B", Color(0xFF64748B))
)

fun parseBoxColor(colorHex: String): Color {
    val cleaned = colorHex.trim().removePrefix("#")
    return try {
        val rgb = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(0xFF000000 or rgb)
            8 -> Color(rgb)
            else -> Color(0xFFF59E0B)
        }
    } catch (_: Exception) {
        Color(0xFFF59E0B)
    }
}

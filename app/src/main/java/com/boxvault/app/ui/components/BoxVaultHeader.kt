package com.boxvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxvault.app.R

/**
 * Unified high-contrast branding header for BoxVault.
 * - Displays edge-to-edge full-bleed background banner consistently across Dashboard and Sub-screens.
 * - The "BoxVault" logo text remains 100% visible and sharp, fading into background only at bottom (0.68f to 1.0f).
 * - On Dashboard: Action buttons (Privacy and Theme) are placed neatly in the top-right corner.
 * - On Sub-screens: A sleek "← Dashboard" home pill button is displayed in the top-right corner, and tapping header returns to Dashboard.
 */
@Composable
fun BoxVaultHeader(
    modifier: Modifier = Modifier,
    isDashboard: Boolean = true,
    onHeaderClick: (() -> Unit)? = null,
    onPrivacyClick: (() -> Unit)? = null,
    onThemeClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onHeaderClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.header_return_dashboard),
                        onClick = onHeaderClick
                    )
                } else Modifier
            )
    ) {
        // Full-bleed Edge-to-Edge Banner Image
        Image(
            painter = painterResource(id = R.drawable.banner_header_wide),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1024f / 317f)
        )

        // Bottom Gradient Scrim: 100% transparent through 68% height to keep text crystal-clear,
        // fading seamlessly into background from 68% to 100% height.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.68f to Color.Transparent,
                        0.84f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        if (isDashboard) {
            // Dashboard: Action buttons (Privacy and Theme) in top-right corner
            // Offset down so orange [ QUICK SCAN ] graphic on the banner remains visible
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 28.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onPrivacyClick != null) {
                    FilledTonalIconButton(
                        onClick = onPrivacyClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = stringResource(R.string.dashboard_action_privacy),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (onThemeClick != null) {
                    FilledTonalIconButton(
                        onClick = onThemeClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = stringResource(R.string.dashboard_action_theme),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            // Sub-screens: Sleek "← Dashboard" return pill in top-right corner
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 28.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onHeaderClick != null) {
                    Surface(
                        onClick = onHeaderClick,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.header_tap_for_dashboard),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

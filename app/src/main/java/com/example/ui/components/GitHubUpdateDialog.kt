package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.engine.AppUpdateInfo
import com.example.engine.GitHubUpdateManager
import com.example.engine.UpdateEnforcementMode
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * In-app GitHub APK Update Popup (ONLY displayed while the user is actively inside Luma):
 *
 * - Version A: [UpdateEnforcementMode.OPTIONAL_SKIPPABLE]
 *   Polite floating popup allowing the user to download the GitHub APK now, remind later,
 *   or skip this specific optional version.
 *
 * - Version B: [UpdateEnforcementMode.MANDATORY_BLOCKING]
 *   Strict non-skippable gate (Back key blocked, outside-tap blocked, no close button)
 *   requiring the user to download & install the newer GitHub APK before continuing.
 */
@Composable
fun GitHubUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDownloadApk: () -> Unit,
    onRemindLater: () -> Unit,
    onSkipVersion: () -> Unit,
    onRetryCheck: suspend () -> Unit
) {
    val palette = LocalFolioPalette.current
    val scope = rememberCoroutineScope()
    val isMandatory = updateInfo.mode == UpdateEnforcementMode.MANDATORY_BLOCKING
    var isVerifying by remember { mutableStateOf(false) }

    // Block hardware/gesture Back press on mandatory updates
    BackHandler(enabled = isMandatory) {
        // Intentionally locked until the required GitHub APK update is installed
    }

    val badgeColor = if (isMandatory) Color(0xFFEF4444) else palette.accent
    val sizeLabel = remember(updateInfo.apkSizeBytes) {
        if (updateInfo.apkSizeBytes > 0L) {
            val mb = updateInfo.apkSizeBytes / (1024.0 * 1024.0)
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            "Luma.apk · GitHub"
        }
    }

    LumaFloatingDropdownDialog(
        onDismiss = {
            if (!isMandatory) onRemindLater()
        },
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.46f),
        maxWidth = 480.dp,
        maxHeightFraction = 0.86f,
        dismissOnScrimClick = !isMandatory,
        dismissOnBackPress = !isMandatory,
        scrimAlpha = if (isMandatory) 0.58f else 0.22f,
        testTag = if (isMandatory) "github_update_mandatory_dialog" else "github_update_optional_dialog"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = if (isMandatory) "Actualización obligatoria" else "Nueva versión disponible",
            subtitle = if (isMandatory) {
                "Requerida para continuar usando Luma"
            } else {
                "Descarga directa de Luma.apk desde GitHub"
            },
            leadingIcon = if (isMandatory) Icons.Rounded.Lock else Icons.Rounded.SystemUpdate,
            onDismiss = {
                if (!isMandatory) dismissAnimated()
            },
            showCloseButton = !isMandatory,
            closeTestTag = "github_update_close"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Mode status pill + version transition banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeColor.copy(alpha = if (palette.isDark) 0.14f else 0.09f))
                    .border(1.dp, badgeColor.copy(alpha = 0.34f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isMandatory) Icons.Rounded.Lock else Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isMandatory) "ACTUALIZACIÓN OBLIGATORIA" else "ACTUALIZACIÓN OPCIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        letterSpacing = 0.6.sp
                    )
                }

                // Version comparison pill: v1.0 -> v1.2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeColor)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "v${updateInfo.versionName}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkMuted
                    )
                }

                if (isMandatory) {
                    Text(
                        text = updateInfo.blockMessage.ifBlank {
                            "Tu versión actual de Luma ha quedado obsoleta. Descarga e instala el nuevo APK desde GitHub para continuar."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            // 2. Release Notes / Changelog Card
            if (updateInfo.changelog.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.68f))
                        .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Novedades de v${updateInfo.versionName}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = updateInfo.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        lineHeight = 19.sp
                    )
                }
            }

            // 3. Primary CTA: Download APK from GitHub
            TactileButton(
                onClick = onDownloadApk,
                containerColor = badgeColor,
                shadowColor = if (isMandatory) Color(0xFFB91C1C) else palette.accentShadow,
                contentColor = Color.White,
                cornerRadius = 18.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_update_download_btn")
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isMandatory) "Descargar e instalar APK" else "Actualizar ahora",
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // 4. Secondary controls depending on Optional vs Mandatory mode
            if (isMandatory) {
                // Re-verify button (for after installing or reconnecting network)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                        .clickable(enabled = !isVerifying) {
                            scope.launch {
                                isVerifying = true
                                try {
                                    onRetryCheck()
                                } finally {
                                    isVerifying = false
                                }
                            }
                        }
                        .padding(vertical = 11.dp, horizontal = 14.dp)
                        .testTag("github_update_recheck_btn"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = palette.ink,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isVerifying) "Comprobando versión…" else "Ya actualicé · Verificar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                }
            } else {
                // Optional / Skippable mode: "Más tarde" + "Omitir esta versión"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .clickable { dismissAnimated() }
                            .padding(vertical = 11.dp)
                            .testTag("github_update_later_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Más tarde",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .clickable {
                                onSkipVersion()
                            }
                            .padding(vertical = 11.dp)
                            .testTag("github_update_skip_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Omitir versión",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkMuted
                        )
                    }
                }
            }
        }
    }
}

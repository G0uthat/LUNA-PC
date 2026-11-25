package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.CriticalEventKind
import com.example.engine.DynamicIslandEventManager
import com.example.engine.LumaDiskCacheManager
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CacheCardShape = RoundedCornerShape(20.dp)
private val CacheChipShape = RoundedCornerShape(14.dp)

/**
 * Storage & Acceleration Cache control card inside Luma Settings.
 * Displays live disk cache usage (parsed documents, rendered PDF pages, OCR results,
 * precomputed thumbnails/temp files), allows adjusting the global LRU budget (default 500 MB),
 * and provides a safe one-tap "Limpiar caché" action that never deletes user books or notebooks.
 */
@Composable
fun DiskCacheSettingsCard(
    diskCacheLimitMb: Int,
    onLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<LumaDiskCacheManager.DiskCacheReport?>(null) }
    var isClearing by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var lastStatusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(diskCacheLimitMb, refreshNonce) {
        LumaDiskCacheManager.setLimitMb(diskCacheLimitMb)
        report = LumaDiskCacheManager.inspectUsage(context)
    }

    val currentReport = report
    val usedLabel = if (currentReport != null) {
        LumaDiskCacheManager.formatBytes(currentReport.totalBytes)
    } else {
        "Calculando..."
    }
    val limitLabel = LumaDiskCacheManager.formatLimitLabel(diskCacheLimitMb)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CacheCardShape)
            .background(palette.surfaceAlt.copy(alpha = 0.58f))
            .border(1.dp, palette.border, CacheCardShape)
            .padding(14.dp)
            .testTag("disk_cache_settings_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Almacenamiento y caché",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink
                )
            }
            Text(
                text = "$usedLabel / $limitLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        }

        TactileProgressBar(
            progress = currentReport?.usageFraction ?: 0f,
            height = 7.dp,
            modifier = Modifier.fillMaxWidth()
        )

        if (currentReport != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CacheBreakdownRow(
                    label = "Documentos parseados (reapertura instantánea)",
                    value = LumaDiskCacheManager.formatBytes(currentReport.parsedDocsBytes)
                )
                CacheBreakdownRow(
                    label = "Páginas PDF renderizadas",
                    value = LumaDiskCacheManager.formatBytes(currentReport.pdfPagesBytes)
                )
                val ocrSuffix = if (currentReport.ocrPageCount > 0) {
                    "${currentReport.ocrPageCount} págs · ${LumaDiskCacheManager.formatBytes(currentReport.ocrCacheBytes)}"
                } else {
                    LumaDiskCacheManager.formatBytes(currentReport.ocrCacheBytes)
                }
                CacheBreakdownRow(
                    label = "Resultados OCR guardados",
                    value = ocrSuffix
                )
                CacheBreakdownRow(
                    label = "Miniaturas y temporales",
                    value = LumaDiskCacheManager.formatBytes(currentReport.thumbnailsAndTempBytes)
                )
            }
        }

        Text(
            text = "Límite máximo de caché en disco",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (limitMb in LumaDiskCacheManager.PRESET_LIMITS_MB) {
                val selected = diskCacheLimitMb == limitMb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CacheChipShape)
                        .background(if (selected) palette.accent else palette.surface)
                        .border(
                            width = if (selected) 0.dp else 1.dp,
                            color = if (selected) palette.accent else palette.border,
                            shape = CacheChipShape
                        )
                        .clickable {
                            onLimitChange(limitMb)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    LumaDiskCacheManager.setLimitMb(limitMb)
                                    LumaDiskCacheManager.enforceGlobalBudget(context)
                                }
                                refreshNonce++
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .testTag("cache_limit_${limitMb}mb"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LumaDiskCacheManager.formatLimitLabel(limitMb),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selected) palette.onAccent else palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = lastStatusMessage
                ?: "Acelera la reapertura de libros, PDFs y OCR. Limpiar la caché nunca borra tus libros ni tus cuadernos.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted
        )

        TactileButton(
            onClick = {
                if (isClearing) return@TactileButton
                isClearing = true
                scope.launch {
                    val freedBytes = LumaDiskCacheManager.clearAllCaches(context)
                    val freedText = LumaDiskCacheManager.formatBytes(freedBytes)
                    lastStatusMessage = if (freedBytes > 0L) {
                        "Caché liberada: $freedText recuperados."
                    } else {
                        "La caché ya estaba limpia."
                    }
                    refreshNonce++
                    isClearing = false
                    DynamicIslandEventManager.postOperationStatus(
                        kind = CriticalEventKind.EXPORT_DONE,
                        badgeLabel = "CACHÉ",
                        title = "Caché limpiada",
                        subtitle = if (freedBytes > 0L) {
                            "$freedText liberados · Libros y cuadernos intactos"
                        } else {
                            "No había archivos temporales por liberar"
                        },
                        accentColorArgb = 0xFF22C55EL,
                        dedupeKey = "luma_cache_cleared"
                    )
                }
            },
            containerColor = palette.surface,
            shadowColor = palette.borderShadow,
            contentColor = palette.ink,
            borderColor = palette.border,
            cornerRadius = 16.dp,
            lipDepth = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("clear_disk_cache_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.CleaningServices,
                contentDescription = "Limpiar caché",
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isClearing) "Limpiando caché..." else "Limpiar caché",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CacheBreakdownRow(
    label: String,
    value: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )
    }
}

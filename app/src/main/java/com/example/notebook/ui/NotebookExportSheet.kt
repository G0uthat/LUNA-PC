package com.example.notebook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notebook.engine.NotebookExportFormat
import com.example.notebook.engine.NotebookExportOptions
import com.example.notebook.engine.NotebookExportProgress
import com.example.notebook.engine.NotebookExportScale
import com.example.notebook.engine.NotebookExportScope
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.TactileButton
import com.example.ui.theme.LocalFolioPalette
import kotlin.math.roundToInt

/**
 * Phase 4 — Fast Export Sheet:
 * - Vector PDF (full notebook or current page) with selectable text & crisp strokes
 * - PNG (optional transparent background) or JPG of current page
 * - All pages as ZIP (PNG or JPG)
 * - Resolution 1×, 2×, or 3×
 * - One-tap Share (FileProvider), Save to Device (MediaStore / SAF), and Add to Luma Library (PDF)
 * - Live Dispatchers.IO progress bar with instant Cancel button
 */
@Composable
fun NotebookExportSheet(
    notebookTitle: String,
    pageCount: Int,
    currentPageNumber: Int,
    exportProgress: NotebookExportProgress?,
    onShare: (NotebookExportOptions) -> Unit,
    onSaveToDevice: (NotebookExportOptions) -> Unit,
    onSaveToCustomFolder: (NotebookExportOptions) -> Unit,
    onAddPdfToLumaLibrary: (NotebookExportOptions) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var format by remember { mutableStateOf(NotebookExportFormat.PDF) }
    var scope by remember { mutableStateOf(NotebookExportScope.ALL_PAGES) }
    var scale by remember { mutableStateOf(NotebookExportScale.X2) }
    var transparentBg by remember { mutableStateOf(false) }

    val options = remember(format, scope, scale, transparentBg) {
        NotebookExportOptions(
            format = format,
            scope = if (format == NotebookExportFormat.PDF) scope else if (format.isZipBundle) NotebookExportScope.ALL_PAGES else NotebookExportScope.CURRENT_PAGE,
            scale = scale,
            transparentBackground = transparentBg && (format == NotebookExportFormat.PNG || format == NotebookExportFormat.ZIP_PNG)
        )
    }

    val isExporting = exportProgress != null

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 450.dp,
        maxHeightFraction = 0.90f,
        testTag = "nb_export_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Exportar cuaderno",
            subtitle = "$notebookTitle · $pageCount ${if (pageCount == 1) "página" else "páginas"}",
            leadingIcon = Icons.Rounded.IosShare,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Progress Card (when exporting)
            if (exportProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                        .testTag("nb_export_progress_card"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exportProgress.message,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(exportProgress.fraction * 100f).roundToInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.accent
                        )
                    }
                    LinearProgressIndicator(
                        progress = { exportProgress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                        color = palette.accent,
                        trackColor = palette.surface,
                        strokeCap = StrokeCap.Round
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE11D48).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFE11D48).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onCancelExport)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = "Cancelar exportación",
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Cancelar exportación",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE11D48)
                        )
                    }
                }
            }

            // 1. Format Selector
            Text(
                text = "Formato",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.inkMuted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormatChip(
                    icon = Icons.Rounded.PictureAsPdf,
                    label = "PDF",
                    selected = format == NotebookExportFormat.PDF,
                    enabled = !isExporting,
                    onClick = { format = NotebookExportFormat.PDF },
                    testTag = "nb_fmt_pdf"
                )
                FormatChip(
                    icon = Icons.Rounded.Image,
                    label = "PNG",
                    selected = format == NotebookExportFormat.PNG,
                    enabled = !isExporting,
                    onClick = { format = NotebookExportFormat.PNG },
                    testTag = "nb_fmt_png"
                )
                FormatChip(
                    icon = Icons.Rounded.Image,
                    label = "JPG",
                    selected = format == NotebookExportFormat.JPG,
                    enabled = !isExporting,
                    onClick = { format = NotebookExportFormat.JPG },
                    testTag = "nb_fmt_jpg"
                )
                FormatChip(
                    icon = Icons.Rounded.FolderZip,
                    label = "ZIP PNG",
                    selected = format == NotebookExportFormat.ZIP_PNG,
                    enabled = !isExporting,
                    onClick = { format = NotebookExportFormat.ZIP_PNG },
                    testTag = "nb_fmt_zip_png"
                )
                FormatChip(
                    icon = Icons.Rounded.FolderZip,
                    label = "ZIP JPG",
                    selected = format == NotebookExportFormat.ZIP_JPG,
                    enabled = !isExporting,
                    onClick = { format = NotebookExportFormat.ZIP_JPG },
                    testTag = "nb_fmt_zip_jpg"
                )
            }
            Text(
                text = format.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted
            )

            // 2. Scope selector (PDF: all pages vs current page)
            if (format == NotebookExportFormat.PDF && pageCount > 1) {
                Text(
                    text = "Alcance del PDF",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScopeButton(
                        label = "Todo ($pageCount págs)",
                        selected = scope == NotebookExportScope.ALL_PAGES,
                        enabled = !isExporting,
                        onClick = { scope = NotebookExportScope.ALL_PAGES },
                        modifier = Modifier.weight(1f)
                    )
                    ScopeButton(
                        label = "Solo pág. $currentPageNumber",
                        selected = scope == NotebookExportScope.CURRENT_PAGE,
                        enabled = !isExporting,
                        onClick = { scope = NotebookExportScope.CURRENT_PAGE },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Transparent PNG background toggle
            if (format == NotebookExportFormat.PNG || format == NotebookExportFormat.ZIP_PNG) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fondo transparente",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Omite el papel y conserva solo trazos, texto e imágenes",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted
                        )
                    }
                    Switch(
                        checked = transparentBg,
                        onCheckedChange = { if (!isExporting) transparentBg = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onAccent,
                            checkedTrackColor = palette.accent
                        )
                    )
                }
            }

            // 4. Resolution Scale Selector (1× / 2× / 3×)
            Text(
                text = "Resolución",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.inkMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotebookExportScale.entries.forEach { s ->
                    val selected = scale == s
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) palette.accent else palette.surfaceAlt)
                            .border(
                                width = 1.dp,
                                color = if (selected) palette.accent else palette.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !isExporting) { scale = s }
                            .padding(vertical = 8.dp)
                            .testTag("nb_scale_${s.multiplier}x"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = s.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) palette.onAccent else palette.ink
                        )
                        Text(
                            text = s.detail,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) palette.onAccent.copy(alpha = 0.85f) else palette.inkMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // 5. One-Tap Action Buttons (all >= 48dp touch targets)
            TactileButton(
                onClick = {
                    if (!isExporting) onShare(options)
                },
                cornerRadius = 15.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("nb_export_share_action")
            ) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Compartir ${format.label} (${scale.label})",
                    fontWeight = FontWeight.Bold
                )
            }

            ExportSheetActionRow(
                icon = Icons.Rounded.SaveAlt,
                title = "Guardar en el dispositivo",
                subtitle = if (format == NotebookExportFormat.PNG || format == NotebookExportFormat.JPG) {
                    "Guarda directo en Galería / Imágenes › Luma"
                } else {
                    "Guarda directo en Descargas › Luma"
                },
                enabled = !isExporting,
                onClick = { onSaveToDevice(options) },
                testTag = "nb_export_save_device"
            )

            ExportSheetActionRow(
                icon = Icons.Rounded.SaveAlt,
                title = "Elegir carpeta manualmente…",
                subtitle = "Selector de archivos de Android (Drive, SD, carpetas)",
                enabled = !isExporting,
                onClick = {
                    dismissWithAnim()
                    onSaveToCustomFolder(options)
                },
                testTag = "nb_export_save_saf"
            )

            // 6. Add to My Luma Library (PDF -> real book in Room library)
            ExportSheetActionRow(
                icon = Icons.Rounded.AutoStories,
                title = "Añadir a mi biblioteca de Luma",
                subtitle = if (format == NotebookExportFormat.PDF && scope == NotebookExportScope.CURRENT_PAGE) {
                    "Importa la página $currentPageNumber como PDF listo para leer"
                } else {
                    "Importa este cuaderno como PDF vectorial en tu biblioteca"
                },
                accentHighlight = true,
                enabled = !isExporting,
                onClick = {
                    val pdfOptions = options.copy(
                        format = NotebookExportFormat.PDF,
                        transparentBackground = false
                    )
                    onAddPdfToLumaLibrary(pdfOptions)
                },
                testTag = "nb_export_add_to_library"
            )
        }
    }
}

@Composable
private fun FormatChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent else palette.surfaceAlt)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) palette.onAccent else palette.accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) palette.onAccent else palette.ink
        )
    }
}

@Composable
private fun ScopeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) palette.accentSoft else palette.surfaceAlt)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.accent else palette.border,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) palette.accent else palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExportSheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    accentHighlight: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (accentHighlight) palette.accentSoft else palette.surfaceAlt)
            .border(
                width = 1.dp,
                color = if (accentHighlight) palette.accent.copy(alpha = 0.4f) else palette.border,
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (accentHighlight) palette.accent else palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = (if (accentHighlight) palette.onAccent else palette.accent).copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink.copy(alpha = alpha)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted.copy(alpha = alpha)
            )
        }
    }
}

package com.example.ui.components

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.model.DocumentFormat
import com.example.ui.theme.LocalFolioPalette

data class PendingExternalFile(
    val uri: Uri,
    val displayName: String,
    val format: DocumentFormat?,
    val isImage: Boolean,
    val errorReason: String? = null
) {
    val isSupported: Boolean
        get() = errorReason == null && (isImage || format != null)
}

data class ExternalBatchProgressState(
    val currentIndex: Int,
    val totalCount: Int,
    val currentFileName: String
)

/**
 * Chooser dialog shown whenever the user opens or shares a file with Luma
 * ("Abrir con Luma" / "Compartir"). Lets the user decide between:
 * - Importing into the library & opening in the Reader
 * - Creating a Study Notebook (with PDF pages or image as locked background)
 * Also presents clear security/unsupported-format feedback when a file is invalid.
 */
@Composable
fun ExternalImportChooserDialog(
    pending: PendingExternalFile,
    isBusy: Boolean,
    onOpenInReader: () -> Unit,
    onCreateNotebook: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val badgeLabel = when {
        pending.errorReason != null -> "NO COMPATIBLE"
        pending.isImage -> "IMAGEN"
        pending.format != null -> pending.format.label
        else -> "ARCHIVO"
    }

    LumaFloatingDropdownDialog(
        onDismiss = { if (!isBusy) onDismiss() },
        alignment = Alignment.Center,
        maxWidth = 430.dp,
        maxHeightFraction = 0.74f,
        testTag = "external_import_chooser_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = if (pending.isSupported) "Abrir en Luma" else "Archivo no compatible",
            subtitle = if (pending.isSupported) "Elige cómo deseas usar este archivo" else "No se pudo validar el archivo recibido",
            leadingIcon = when {
                !pending.isSupported -> Icons.Rounded.ErrorOutline
                pending.isImage -> Icons.Rounded.Image
                else -> Icons.Rounded.FileOpen
            },
            onDismiss = { if (!isBusy) dismissWithAnim() }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File summary card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (pending.isSupported) palette.accentSoft else Color(0xFFEF4444).copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            if (pending.isSupported) palette.accent.copy(alpha = 0.35f) else Color(0xFFEF4444).copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (pending.isSupported) palette.accent else Color(0xFFDC2626)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pending.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            pending.errorReason != null -> pending.errorReason
                            pending.isImage -> "Se guardará en un Cuaderno para dibujar y añadir etiquetas encima"
                            else -> "Formato verificado por firma real · listo para tu biblioteca"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pending.errorReason != null) Color(0xFFDC2626) else palette.inkMuted
                    )
                }
            }

            if (isBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.4.dp,
                        color = palette.accent
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Importando archivo…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink
                    )
                }
            } else if (!pending.isSupported) {
                TactileButton(
                    onClick = { dismissWithAnim() },
                    cornerRadius = 14.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("external_unsupported_ok")
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            } else {
                if (!pending.isImage && pending.format != null) {
                    ExternalChooserOptionCard(
                        icon = Icons.Rounded.AutoStories,
                        title = "Abrir en el lector",
                        subtitle = "Importa a Libros (evitando duplicados) y ábrelo ahora",
                        highlighted = true,
                        testTag = "external_choice_reader",
                        onClick = onOpenInReader
                    )
                }

                ExternalChooserOptionCard(
                    icon = Icons.Rounded.Draw,
                    title = when {
                        pending.isImage -> "Enviar a un Cuaderno"
                        pending.format == DocumentFormat.PDF -> "Crear Cuaderno sobre este PDF"
                        else -> "Crear Cuaderno de estudio"
                    },
                    subtitle = when {
                        pending.isImage -> "Coloca la imagen o PNG como fondo fijo para rotular, dibujar y señalar"
                        pending.format == DocumentFormat.PDF -> "Importa el PDF y convierte cada página en fondo anotable de cuaderno"
                        else -> "Guarda el documento en Libros y abre un cuaderno nuevo para tus apuntes"
                    },
                    highlighted = pending.isImage,
                    testTag = "external_choice_notebook",
                    onClick = onCreateNotebook
                )
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * Live progress dialog shown when importing multiple shared files via ACTION_SEND_MULTIPLE.
 */
@Composable
fun ExternalBatchImportDialog(
    progress: ExternalBatchProgressState
) {
    val palette = LocalFolioPalette.current
    val frac = if (progress.totalCount > 0) {
        (progress.currentIndex.toFloat() / progress.totalCount.toFloat()).coerceIn(0.05f, 1f)
    } else 0.1f

    LumaFloatingDropdownDialog(
        onDismiss = {},
        alignment = Alignment.Center,
        maxWidth = 400.dp,
        maxHeightFraction = 0.45f,
        testTag = "external_batch_import_dialog"
    ) { _ ->
        LumaDropdownHeader(
            title = "Importando archivos (${progress.currentIndex}/${progress.totalCount})",
            subtitle = "Detectando formato real y evitando duplicados…",
            leadingIcon = Icons.Rounded.FileOpen,
            onDismiss = {}
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = progress.currentFileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = palette.accent,
                trackColor = palette.surfaceAlt,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ExternalChooserOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (highlighted) palette.accentSoft else palette.surface)
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) palette.accent.copy(alpha = 0.55f) else palette.border,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (highlighted) palette.accent else palette.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) palette.onAccent else palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
        }
    }
}

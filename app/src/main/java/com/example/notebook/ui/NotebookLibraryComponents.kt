package com.example.notebook.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.data.NotebookSummaryItem
import com.example.notebook.model.NotebookPageSizePreset
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PaperTintOptions = listOf(
    "Blanco" to 0xFFFFFFFFL,
    "Marfil" to 0xFFFCF8EEL,
    "Papel cálido" to 0xFFF5ECD8L,
    "Pizarra oscura" to 0xFF181C24L
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookGridCard(
    notebook: NotebookSummaryItem,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val thumbPath = notebook.firstPageThumbnailPath.ifBlank { notebook.coverImagePath }
    var thumbBitmap by remember(thumbPath, notebook.updatedAt) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(thumbPath, notebook.updatedAt) {
        thumbBitmap = if (thumbPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(thumbPath)
                    if (f.exists() && f.length() > 0L) BitmapFactory.decodeFile(f.absolutePath) else null
                }.getOrNull()
            }
        } else null
    }

    val template = remember(notebook.pageTemplate) { NotebookPageTemplate.fromName(notebook.pageTemplate) }
    val dateLabel = remember(notebook.updatedAt) {
        SimpleDateFormat("d MMM", Locale.forLanguageTag("es")).format(Date(notebook.updatedAt))
    }
    val pageLabel = if (notebook.pageCount == 1) "1 pág" else "${notebook.pageCount} págs"
    val cardShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            )
            .testTag("notebook_card_${notebook.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.74f)
                .clip(cardShape)
                .background(Color.White)
                .border(1.dp, palette.border, cardShape)
        ) {
            val bmp = thumbBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Vista previa de ${notebook.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                NotebookTemplatePreviewCanvas(
                    template = template,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Subtle binding spine accent on left edge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(7.dp)
                    .fillMaxSize()
                    .background(palette.accent.copy(alpha = 0.85f))
            )

            // Top-left "Cuaderno" + page count badge (consistent with book format badges)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 13.dp, top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC111827))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Cuaderno · $pageLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Top-right options pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x99111827))
                    .clickable(onClick = onLongPress)
                    .testTag("notebook_more_${notebook.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Opciones del cuaderno",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = notebook.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${template.label} · $dateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NotebookTemplatePreviewCanvas(
    template: NotebookPageTemplate,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(Color(0xFFFCFBF8))) {
        val lineColor = Color(0xFFD6DCE8)
        val accentLine = Color(0xFFE5989B)
        when (template) {
            NotebookPageTemplate.BLANK -> Unit
            NotebookPageTemplate.GRID -> {
                val step = size.width / 10f
                var x = step
                while (x < size.width) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
            NotebookPageTemplate.DOTS -> {
                val step = size.width / 9f
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(Color(0xFFBFC8D9), radius = 1.8f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            NotebookPageTemplate.RULED -> {
                val step = size.height / 14f
                var y = step * 1.6f
                while (y < size.height - step * 0.5f) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
                    y += step
                }
                drawLine(accentLine, Offset(size.width * 0.16f, 0f), Offset(size.width * 0.16f, size.height), strokeWidth = 1.4f)
            }
            NotebookPageTemplate.CORNELL -> {
                val headerY = size.height * 0.14f
                val footerY = size.height * 0.82f
                val cueX = size.width * 0.30f
                drawLine(accentLine, Offset(0f, headerY), Offset(size.width, headerY), strokeWidth = 1.5f)
                drawLine(accentLine, Offset(0f, footerY), Offset(size.width, footerY), strokeWidth = 1.5f)
                drawLine(accentLine, Offset(cueX, headerY), Offset(cueX, footerY), strokeWidth = 1.5f)
                val step = (footerY - headerY) / 10f
                var y = headerY + step
                while (y < footerY) {
                    drawLine(lineColor, Offset(cueX, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
        }
    }
}

@Composable
fun EmptyNotebooksCard(
    onCreateBlank: () -> Unit,
    onImportPdf: () -> Unit,
    onImportImage: () -> Unit
) {
    val palette = LocalFolioPalette.current
    TactileCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Draw,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Cuadernos de Estudio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = "Crea apuntes visuales estilo estudio: banners, resaltador debajo del texto, flechas, llaves, PNGs recortados o anota encima de un PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
            TactileButton(
                onClick = onCreateBlank,
                cornerRadius = 16.dp,
                lipDepth = 2.dp,
                modifier = Modifier.testTag("empty_create_notebook_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Crear primer cuaderno", fontWeight = FontWeight.Bold)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickSourceChip(
                    icon = Icons.Rounded.PictureAsPdf,
                    label = "Desde PDF",
                    onClick = onImportPdf,
                    testTag = "empty_notebook_from_pdf"
                )
                QuickSourceChip(
                    icon = Icons.Rounded.Image,
                    label = "Desde Imagen",
                    onClick = onImportImage,
                    testTag = "empty_notebook_from_image"
                )
            }
        }
    }
}

@Composable
private fun QuickSourceChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(palette.surfaceAlt)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.ink)
    }
}

@Composable
fun CreateNotebookDialog(
    onCreate: (title: String, template: NotebookPageTemplate, widthPx: Int, heightPx: Int, bgColor: Long) -> Unit,
    onCreateFromPdf: () -> Unit,
    onCreateFromImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var title by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(NotebookPageTemplate.BLANK) }
    var selectedSize by remember { mutableStateOf(NotebookPageSizePreset.A4) }
    var customWidthText by remember { mutableStateOf("1480") }
    var customHeightText by remember { mutableStateOf("2092") }
    var selectedPaperColor by remember { mutableLongStateOf(0xFFFFFFFFL) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 440.dp,
        maxHeightFraction = 0.88f,
        testTag = "create_notebook_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Nuevo Cuaderno",
            subtitle = "Apuntes, diagramas y anotaciones sobre PDF",
            leadingIcon = Icons.Rounded.Draw,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nombre del cuaderno") },
                placeholder = { Text("Ej. Farmacología · Neuroanatomía") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_notebook_title_input")
            )

            // Quick import from PDF or Image
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "O empieza desde un archivo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        QuickSourceChip(
                            icon = Icons.Rounded.PictureAsPdf,
                            label = "Fondo PDF",
                            onClick = {
                                dismissWithAnim()
                                onCreateFromPdf()
                            },
                            testTag = "create_notebook_from_pdf"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        QuickSourceChip(
                            icon = Icons.Rounded.Image,
                            label = "Fondo Imagen",
                            onClick = {
                                dismissWithAnim()
                                onCreateFromImage()
                            },
                            testTag = "create_notebook_from_image"
                        )
                    }
                }
            }

            // Paper templates
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Plantilla de hoja",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                NotebookPageTemplate.entries.chunked(3).forEach { rowTemplates ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (tpl in rowTemplates) {
                            val selected = selectedTemplate == tpl
                            val shape = RoundedCornerShape(12.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(if (selected) palette.accent else palette.surfaceAlt)
                                    .border(
                                        1.dp,
                                        if (selected) palette.accent else palette.border,
                                        shape
                                    )
                                    .clickable { selectedTemplate = tpl }
                                    .padding(vertical = 10.dp, horizontal = 6.dp)
                                    .testTag("template_${tpl.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tpl.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) palette.onAccent else palette.ink,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(3 - rowTemplates.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Page size presets: A4, Carta, 16:9, Cuadrado, Personalizado
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tamaño de hoja",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                NotebookPageSizePreset.entries.chunked(3).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (preset in rowPresets) {
                            OrientationChip(
                                label = preset.label,
                                selected = selectedSize == preset,
                                onClick = { selectedSize = preset },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowPresets.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (selectedSize == NotebookPageSizePreset.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customWidthText,
                            onValueChange = { customWidthText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Ancho px") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeightText,
                            onValueChange = { customHeightText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Alto px") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Paper tint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Color de hoja",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((_, colorHex) in PaperTintOptions) {
                        val isMatch = selectedPaperColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex.toInt()))
                                .border(
                                    width = if (isMatch) 2.5.dp else 1.2.dp,
                                    color = if (isMatch) palette.accent else palette.border,
                                    shape = CircleShape
                                )
                                .clickable { selectedPaperColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isMatch) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (colorHex == 0xFF181C24L) Color.White else palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            TactileButton(
                onClick = {
                    val w = if (selectedSize == NotebookPageSizePreset.CUSTOM) {
                        (customWidthText.toIntOrNull() ?: NotebookEntity.DEFAULT_PAGE_WIDTH_PX).coerceIn(600, 2400)
                    } else selectedSize.widthPx
                    val h = if (selectedSize == NotebookPageSizePreset.CUSTOM) {
                        (customHeightText.toIntOrNull() ?: NotebookEntity.DEFAULT_PAGE_HEIGHT_PX).coerceIn(600, 2600)
                    } else selectedSize.heightPx
                    onCreate(
                        title.trim().ifEmpty { "Cuaderno de estudio" },
                        selectedTemplate,
                        w,
                        h,
                        selectedPaperColor
                    )
                    dismissWithAnim()
                },
                cornerRadius = 16.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_create_notebook_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Crear y abrir cuaderno", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OrientationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surfaceAlt)
            .border(1.dp, if (selected) palette.accent else palette.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) palette.accent else palette.ink
        )
    }
}

@Composable
fun NotebookOptionsDialog(
    notebook: NotebookSummaryItem,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var draftName by remember(notebook.id) { mutableStateOf(notebook.title) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 390.dp,
        maxHeightFraction = 0.65f,
        testTag = "notebook_options_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Gestionar cuaderno",
            subtitle = "${notebook.pageCount} páginas",
            leadingIcon = Icons.Rounded.DriveFileRenameOutline,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text("Título del cuaderno") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border
                ),
                modifier = Modifier.fillMaxWidth()
            )
            TactileButton(
                onClick = {
                    val clean = draftName.trim()
                    if (clean.isNotEmpty()) {
                        onRename(clean)
                    }
                    dismissWithAnim()
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar nombre", fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1AE11D48))
                    .border(1.dp, Color(0x55E11D48), RoundedCornerShape(14.dp))
                    .clickable {
                        onDelete()
                        dismissWithAnim()
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Eliminar cuaderno y archivos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE11D48)
                )
            }
        }
    }
}

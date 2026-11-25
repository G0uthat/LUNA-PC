package com.example.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LibraryDocumentItem
import com.example.data.local.PlaylistEntity
import com.example.ui.components.LumaCapsuleOptionRow
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.theme.LocalFolioPalette

internal val PlaylistSwatches = listOf(
    0xFF3E6AE1L,
    0xFF6366F1L,
    0xFF0D9488L,
    0xFFD97706L,
    0xFFE11D48L,
    0xFF7C3AEDL
)

/**
 * Floating rounded window shown when long-pressing any book cover.
 * Loads synopsis on-demand from Room so the main Library grid stays lean.
 */
@Composable
internal fun BookOptionsFloatingDialog(
    doc: LibraryDocumentItem,
    loadSynopsis: suspend (Long) -> String,
    onAddToPlaylist: () -> Unit,
    onChangeCoverPhoto: () -> Unit,
    onRegenerateCover: () -> Unit,
    onEditMetadata: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val coverBitmap = rememberDecodedCoverBitmap(doc.coverImagePath)
    val coverImageBitmap = remember(coverBitmap) { coverBitmap?.asImageBitmap() }
    val coverColor = remember(doc.coverColorHex) { Color(doc.coverColorHex.toInt()) }
    var loadedSynopsis by remember(doc.id) { mutableStateOf("") }
    LaunchedEffect(doc.id) {
        loadedSynopsis = loadSynopsis(doc.id)
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.2f),
        maxWidth = 360.dp,
        maxHeightFraction = 0.82f,
        testTag = "book_options_floating_dialog"
    ) { dismissAnimated ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(coverColor)
                ) {
                    if (coverImageBitmap != null) {
                        Image(
                            bitmap = coverImageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(doc.author)
                            if (doc.publisherYear.isNotBlank()) append(" · ${doc.publisherYear}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (doc.genre.isNotBlank()) {
                        Text(
                            text = doc.genre,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable(onClick = dismissAnimated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = palette.ink, modifier = Modifier.size(18.dp))
                }
            }

            if (loadedSynopsis.isNotBlank()) {
                Text(
                    text = loadedSynopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 6.dp),
                thickness = 1.dp,
                color = palette.border.copy(alpha = 0.6f)
            )

            LumaCapsuleOptionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "PONER EN SERIE",
                subtitle = "Agrupar con otros libros o sagas",
                iconOnRight = true,
                onClick = onAddToPlaylist,
                testTag = "book_option_playlist"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.Image,
                title = "CAMBIAR CARÁTULA",
                subtitle = "Elegir imagen de tu galería",
                iconOnRight = true,
                onClick = onChangeCoverPhoto,
                testTag = "book_option_cover_photo"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "PORTADA AUTOMÁTICA",
                subtitle = "Crear con título y autor actual",
                iconOnRight = true,
                onClick = onRegenerateCover,
                testTag = "book_option_cover_auto"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.Edit,
                title = "EDITAR METADATOS",
                subtitle = "Título, autor, género y sinopsis",
                iconOnRight = true,
                onClick = onEditMetadata,
                testTag = "book_option_metadata"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "ELIMINAR LIBRO",
                subtitle = "Quitar de tu biblioteca local",
                isDestructive = true,
                iconOnRight = true,
                onClick = onDelete,
                testTag = "book_option_delete"
            )
        }
    }
}

@Composable
internal fun EditBookMetadataDialog(
    doc: LibraryDocumentItem,
    loadSynopsis: suspend (Long) -> String,
    onSave: (title: String, author: String, genre: String, year: String, synopsis: String, badgeTag: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var title by remember(doc.id) { mutableStateOf(doc.title) }
    var author by remember(doc.id) { mutableStateOf(doc.author) }
    var genre by remember(doc.id) { mutableStateOf(doc.genre) }
    var year by remember(doc.id) { mutableStateOf(doc.publisherYear) }
    var badgeTag by remember(doc.id) { mutableStateOf(doc.badgeTag) }
    var synopsis by remember(doc.id) { mutableStateOf("") }
    LaunchedEffect(doc.id) {
        val loaded = loadSynopsis(doc.id)
        if (synopsis.isEmpty() && loaded.isNotBlank()) {
            synopsis = loaded
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(26.dp),
        title = { Text("Nombre y metadatos", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título del libro") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("meta_title_input")
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Autor") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("meta_author_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Género / Saga") },
                        placeholder = { Text("Fantasía, Manhwa…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Año") },
                        placeholder = { Text("2025") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedTextField(
                    value = badgeTag,
                    onValueChange = { badgeTag = it },
                    label = { Text("Etiqueta en carátula") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = { Text("Sinopsis o notas") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, author, genre, year, synopsis, badgeTag) },
                enabled = title.isNotBlank()
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold, color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = palette.inkMuted)
            }
        }
    )
}

@Composable
internal fun CreatePlaylistDialog(
    onCreate: (name: String, description: String, accentColorHex: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(PlaylistSwatches.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Nueva serie o playlist", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Agrupa sagas, manhwas o colecciones por tema como listas de reproducción.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la serie o playlist") },
                    placeholder = { Text("Ej. Saga Fantasía, Manhwas Favoritos…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hex in PlaylistSwatches) {
                        val isSel = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex.toInt()))
                                .border(
                                    width = if (isSel) 2.5.dp else 0.dp,
                                    color = if (isSel) palette.ink else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, description, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear", fontWeight = FontWeight.Bold, color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = palette.inkMuted)
            }
        }
    )
}

@Composable
internal fun AssignBookPlaylistsDialog(
    doc: LibraryDocumentItem,
    playlists: List<PlaylistEntity>,
    memberPlaylistIds: Set<Long>,
    onToggleMembership: (Long, Boolean) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Guardar en series / playlists",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlists.isEmpty()) {
                    Text(
                        text = "Aún no tienes series. Crea una para agrupar tus libros y sagas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                } else {
                    for (pl in playlists) {
                        val isMember = pl.id in memberPlaylistIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surfaceAlt.copy(alpha = 0.6f))
                                .clickable { onToggleMembership(pl.id, isMember) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isMember,
                                onCheckedChange = { onToggleMembership(pl.id, isMember) },
                                colors = CheckboxDefaults.colors(checkedColor = palette.accent)
                            )
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo", fontWeight = FontWeight.Bold, color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCreateNewPlaylist) {
                Text("+ Nueva serie", color = palette.accent)
            }
        }
    )
}

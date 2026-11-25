package com.example.notebook.ui

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notebook.model.NotebookStarterTemplate
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.theme.LocalFolioPalette

/**
 * Categorized Study Font Picker Dialog (Phase 3):
 * - 28 bundled OFL/Apache fonts organized by study category.
 * - Each font name and sample renders directly in its own real bundled `FontFamily`.
 * - Persisted Favorites and Recents pinned at the top, plus instant search by name/category.
 */
@Composable
fun NotebookFontPickerDialog(
    selectedFontId: String,
    favoriteFontIds: List<String>,
    recentFontIds: List<String>,
    onSelectFont: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var query by remember { mutableStateOf("") }
    val normalizedSelected = remember(selectedFontId) { NotebookFonts.canonicalId(selectedFontId) }
    val favoriteSet = remember(favoriteFontIds) { favoriteFontIds.toSet() }

    val filteredCatalog = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) NotebookFonts.CATALOG
        else NotebookFonts.CATALOG.filter { spec ->
            spec.displayName.lowercase().contains(q) || spec.category.label.lowercase().contains(q)
        }
    }

    val favoriteSpecs = remember(favoriteFontIds) {
        favoriteFontIds.map { NotebookFonts.specFor(it) }.distinctBy { it.id }
    }
    val recentSpecs = remember(recentFontIds, favoriteSet) {
        recentFontIds.map { NotebookFonts.specFor(it) }.distinctBy { it.id }.filterNot { it.id in favoriteSet }
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 480.dp,
        maxHeightFraction = 0.88f,
        testTag = "nb_font_picker_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Tipografías (${NotebookFonts.CATALOG.size})",
            subtitle = "Todas empaquetadas sin conexión · toca ★ para fijar favoritas",
            leadingIcon = Icons.Rounded.FontDownload,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar fuente (Poppins, Caveat, Serif…)") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                                .clip(CircleShape)
                                .clickable { query = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda", tint = palette.inkMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nb_font_search_input")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (query.isBlank()) {
                    if (favoriteSpecs.isNotEmpty()) {
                        item(key = "hdr_fav") {
                            FontSectionHeader(title = "Favoritas", accent = true)
                        }
                        items(favoriteSpecs, key = { "fav_${it.id}" }) { spec ->
                            FontCatalogRow(
                                spec = spec,
                                isSelected = spec.id == normalizedSelected,
                                isFavorite = true,
                                onSelect = {
                                    onSelectFont(spec.id)
                                    dismissWithAnim()
                                },
                                onToggleFavorite = { onToggleFavorite(spec.id) }
                            )
                        }
                    }

                    if (recentSpecs.isNotEmpty()) {
                        item(key = "hdr_rec") {
                            FontSectionHeader(title = "Recientes", accent = false)
                        }
                        items(recentSpecs, key = { "rec_${it.id}" }) { spec ->
                            FontCatalogRow(
                                spec = spec,
                                isSelected = spec.id == normalizedSelected,
                                isFavorite = spec.id in favoriteSet,
                                onSelect = {
                                    onSelectFont(spec.id)
                                    dismissWithAnim()
                                },
                                onToggleFavorite = { onToggleFavorite(spec.id) }
                            )
                        }
                    }

                    for (cat in NotebookFontCategory.entries) {
                        val catItems = NotebookFonts.CATALOG.filter { it.category == cat }
                        if (catItems.isNotEmpty()) {
                            item(key = "hdr_${cat.name}") {
                                FontSectionHeader(title = cat.label, accent = false)
                            }
                            items(catItems, key = { "cat_${it.id}" }) { spec ->
                                FontCatalogRow(
                                    spec = spec,
                                    isSelected = spec.id == normalizedSelected,
                                    isFavorite = spec.id in favoriteSet,
                                    onSelect = {
                                        onSelectFont(spec.id)
                                        dismissWithAnim()
                                    },
                                    onToggleFavorite = { onToggleFavorite(spec.id) }
                                )
                            }
                        }
                    }
                } else {
                    if (filteredCatalog.isEmpty()) {
                        item(key = "empty_search") {
                            Text(
                                text = "No se encontraron fuentes con \"$query\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkMuted,
                                modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp)
                            )
                        }
                    } else {
                        items(filteredCatalog, key = { "srch_${it.id}" }) { spec ->
                            FontCatalogRow(
                                spec = spec,
                                isSelected = spec.id == normalizedSelected,
                                isFavorite = spec.id in favoriteSet,
                                onSelect = {
                                    onSelectFont(spec.id)
                                    dismissWithAnim()
                                },
                                onToggleFavorite = { onToggleFavorite(spec.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontSectionHeader(title: String, accent: Boolean) {
    val palette = LocalFolioPalette.current
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        color = if (accent) palette.accent else palette.inkMuted,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun FontCatalogRow(
    spec: NotebookFontSpec,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val family = remember(spec.id) { NotebookFonts.getComposeFontFamily(spec.id) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (isSelected) palette.accentSoft else palette.surfaceAlt)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) palette.accent else palette.border, shape)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .testTag("nb_font_row_${spec.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = spec.displayName,
                    fontFamily = family,
                    fontSize = if (spec.category == NotebookFontCategory.HANDWRITTEN || spec.category == NotebookFontCategory.CALLIGRAPHIC) 19.sp else 16.sp,
                    color = if (isSelected) palette.accent else palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!spec.supportsBold && !spec.supportsItalic) {
                    Text(
                        text = "Estilo único",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surface)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = spec.sampleHint,
                fontFamily = family,
                fontSize = 12.sp,
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Fuente seleccionada",
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite)
                .testTag("nb_font_fav_${spec.id}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = if (isFavorite) "Quitar de favoritas" else "Marcar como favorita",
                tint = if (isFavorite) Color(0xFFF59E0B) else palette.inkMuted,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/**
 * Editable Starter Templates Dialog (Phase 3):
 * Cornell, Concept Map (with live anchored Connectors), Title Summary, and Comparative Table.
 */
@Composable
fun NotebookStarterTemplatesDialog(
    onApplyTemplate: (NotebookStarterTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 450.dp,
        maxHeightFraction = 0.80f,
        testTag = "nb_starter_templates_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Plantillas de arranque",
            subtitle = "Inserta bloques 100% editables en la hoja actual (puedes deshacer)",
            leadingIcon = Icons.Rounded.AutoAwesome,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (tpl in NotebookStarterTemplate.entries) {
                val icon: ImageVector = when (tpl) {
                    NotebookStarterTemplate.CORNELL -> Icons.Rounded.Dashboard
                    NotebookStarterTemplate.CONCEPT_MAP -> Icons.Rounded.AccountTree
                    NotebookStarterTemplate.TITLE_SUMMARY -> Icons.Rounded.Subject
                    NotebookStarterTemplate.COMPARATIVE_TABLE -> Icons.Rounded.TableChart
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                        .clickable {
                            onApplyTemplate(tpl)
                            dismissWithAnim()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("nb_starter_tpl_${tpl.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tpl.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.ink
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tpl.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DictionarySourceEntity
import com.example.data.local.DocumentEntity
import com.example.data.model.AiProviderType
import com.example.data.model.DocumentFormat
import com.example.data.model.ExportFormatOption
import com.example.data.model.ReaderPreferences
import com.example.data.model.TranslationEngine
import com.example.ui.theme.LocalFolioPalette

/**
 * Combined sheet for:
 * 1) Translation engine selection: Google Traductor (zero API key) OR AI with user's own local API key
 * 2) Custom dictionary URL template sources ({palabra}), e.g. RAE, WordReference, DeepL
 */
@Composable
fun DictionaryAndAiSettingsSheet(
    prefs: ReaderPreferences,
    sources: List<DictionarySourceEntity>,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onAddSource: (name: String, urlTemplate: String) -> Unit,
    onToggleSource: (DictionarySourceEntity) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.5f, 0.06f),
        maxWidth = 520.dp,
        maxHeightFraction = 0.84f,
        testTag = "dictionary_ai_sheet"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Diccionario y Traducción",
            subtitle = "Motor de traducción y fuentes",
            leadingIcon = Icons.Rounded.Translate,
            onDismiss = dismissAnimated
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DictionaryAndAiSettingsContent(
                prefs = prefs,
                sources = sources,
                onUpdatePrefs = onUpdatePrefs,
                onAddSource = onAddSource,
                onToggleSource = onToggleSource,
                onDeleteSource = onDeleteSource
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun DictionaryAndAiSettingsContent(
    prefs: ReaderPreferences,
    sources: List<DictionarySourceEntity>,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onAddSource: (name: String, urlTemplate: String) -> Unit,
    onToggleSource: (DictionarySourceEntity) -> Unit,
    onDeleteSource: (Long) -> Unit
) {
    val palette = LocalFolioPalette.current

    var apiKeyInput by remember(prefs.aiApiKey) { mutableStateOf(prefs.aiApiKey) }
    var modelInput by remember(prefs.aiModel) { mutableStateOf(prefs.aiModel) }
    var baseUrlInput by remember(prefs.aiBaseUrl) { mutableStateOf(prefs.aiBaseUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    var newSourceName by remember { mutableStateOf("") }
    var newSourceUrl by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Translation Engine (Google Traductor vs User BYOK AI API)
        SectionTitle("Motor de traducción (Palabras y Manhwa)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TactileChip(
                label = "Google Traductor",
                icon = Icons.Rounded.Translate,
                selected = prefs.translationEngine == TranslationEngine.GOOGLE_TRANSLATE,
                onClick = { onUpdatePrefs { it.copy(translationEngine = TranslationEngine.GOOGLE_TRANSLATE) } },
                modifier = Modifier.testTag("engine_google_translate")
            )
            TactileChip(
                label = "IA (Tu API Key)",
                icon = Icons.Rounded.AutoAwesome,
                selected = prefs.translationEngine == TranslationEngine.AI_CUSTOM_API,
                onClick = { onUpdatePrefs { it.copy(translationEngine = TranslationEngine.AI_CUSTOM_API) } },
                modifier = Modifier.testTag("engine_ai_custom")
            )
        }

        if (prefs.translationEngine == TranslationEngine.GOOGLE_TRANSLATE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.accentSoft)
                    .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Listo para usar sin claves: traduce palabras al mantener pulsado y globos de manhwa usando Google Traductor y modelo local ML Kit EN → ES.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink
                )
            }
        } else {
            // Dedicated space for user's own API key (stored locally in DataStore)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceAlt.copy(alpha = 0.7f))
                    .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Configura tu propia clave API (guardada solo en tu dispositivo)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (provider in AiProviderType.entries) {
                        TactileChip(
                            label = provider.displayName,
                            selected = prefs.aiProvider == provider,
                            onClick = {
                                modelInput = provider.defaultModel
                                baseUrlInput = provider.defaultBaseUrl
                                onUpdatePrefs {
                                    it.copy(
                                        aiProvider = provider,
                                        aiModel = provider.defaultModel,
                                        aiBaseUrl = provider.defaultBaseUrl
                                    )
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Tu API Key") },
                    placeholder = { Text("Pega tu clave de Gemini o OpenAI…") },
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "Ocultar" else "Ver")
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_api_key_input")
                )

                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Modelo") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("URL Base del endpoint") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                TactileButton(
                    onClick = {
                        onUpdatePrefs {
                            it.copy(
                                aiApiKey = apiKeyInput.trim(),
                                aiModel = modelInput.trim().ifEmpty { it.aiProvider.defaultModel },
                                aiBaseUrl = baseUrlInput.trim().ifEmpty { it.aiProvider.defaultBaseUrl }
                            )
                        }
                    },
                    cornerRadius = 16.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier.testTag("save_ai_config_button")
                ) {
                    Text("Guardar configuración de IA", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 2. Dictionary Popover Sources
        SectionTitle("Fuentes del diccionario contextual")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (source in sources) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.65f))
                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = if (source.isWikipediaApi) "API REST instantánea (es.wikipedia.org)" else source.urlTemplate,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = source.isEnabled,
                        onCheckedChange = { onToggleSource(source) },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
                    )
                    if (!source.isWikipediaApi) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDeleteSource(source.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Eliminar fuente",
                                tint = palette.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Add custom {palabra} URL source
        SectionTitle("Añadir fuente personalizada")
        OutlinedTextField(
            value = newSourceName,
            onValueChange = { newSourceName = it; sourceError = null },
            label = { Text("Nombre (ej. DeepL, Linguee)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("new_dict_name_input")
        )
        OutlinedTextField(
            value = newSourceUrl,
            onValueChange = { newSourceUrl = it; sourceError = null },
            label = { Text("URL con plantilla {palabra}") },
            placeholder = { Text("https://www.deepl.com/translator#en/es/{palabra}") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("new_dict_url_input")
        )
        if (sourceError != null) {
            Text(
                text = sourceError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TactileChip(
                label = "+ DeepL EN→ES",
                selected = false,
                onClick = {
                    newSourceName = "DeepL"
                    newSourceUrl = "https://www.deepl.com/translator#en/es/{palabra}"
                }
            )
            TactileChip(
                label = "+ Linguee",
                selected = false,
                onClick = {
                    newSourceName = "Linguee"
                    newSourceUrl = "https://www.linguee.es/ingles-espanol/search?query={palabra}"
                }
            )
        }
        TactileButton(
            onClick = {
                val name = newSourceName.trim()
                val url = newSourceUrl.trim()
                when {
                    name.isEmpty() -> sourceError = "Escribe un nombre para la fuente."
                    !url.startsWith("http") -> sourceError = "La URL debe comenzar por https://"
                    !url.contains("{palabra}", ignoreCase = true) && !url.contains("{word}", ignoreCase = true) ->
                        sourceError = "Incluye {palabra} en la URL (ej. https://dle.rae.es/{palabra})."
                    else -> {
                        onAddSource(name, url)
                        newSourceName = ""
                        newSourceUrl = ""
                    }
                }
            },
            cornerRadius = 16.dp,
            lipDepth = 2.dp,
            modifier = Modifier.testTag("add_dict_source_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar fuente al popover", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Dialog letting the user export the annotated document fusing freehand drawings + selectable text.
 */
@Composable
fun MergedExportDialog(
    document: DocumentEntity,
    drawingStrokeCount: Int,
    highlightCount: Int,
    ocrPageCount: Int,
    isExporting: Boolean,
    onExportSaveAs: (ExportFormatOption) -> Unit,
    onExportShare: (ExportFormatOption) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val docFormat = DocumentFormat.fromExtension(document.format) ?: DocumentFormat.PDF
    var selectedOption by remember { mutableStateOf(ExportFormatOption.NATIVE_MERGED) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.5f, 0.08f),
        maxWidth = 480.dp,
        maxHeightFraction = 0.82f,
        dismissOnScrimClick = !isExporting,
        testTag = "merged_export_dialog"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Exportar fusionado",
            subtitle = "Dibujos + texto seleccionable",
            leadingIcon = Icons.Rounded.IosShare,
            onDismiss = { if (!isExporting) dismissAnimated() }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tus dibujos y subrayados se fusionarán dentro del archivo final manteniendo todo el texto 100% seleccionable y buscable.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )

            // Stats pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accentSoft)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatBadge("Trazos", "$drawingStrokeCount")
                StatBadge("Subrayados", "$highlightCount")
                if (docFormat == DocumentFormat.PDF) {
                    StatBadge("Págs OCR", "$ocrPageCount")
                }
            }

            // Format choices
            if (docFormat == DocumentFormat.PPTX) {
                ExportOptionCard(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "PDF de diapositivas con firmas y trazos",
                    subtitle = "Exporta cada diapositiva PowerPoint con su proporción real, imágenes, texto seleccionable y tus anotaciones o firmas encima.",
                    selected = true,
                    onClick = { selectedOption = ExportFormatOption.UNIVERSAL_PDF }
                )
            } else if (docFormat != DocumentFormat.PDF) {
                ExportOptionCard(
                    icon = Icons.Rounded.Description,
                    title = "${docFormat.label} original fusionado",
                    subtitle = if (docFormat == DocumentFormat.EPUB) {
                        "Conserva texto XHTML seleccionable + capa SVG vectorial encima"
                    } else {
                        "Conserva texto editable con resaltados y notas"
                    },
                    selected = selectedOption == ExportFormatOption.NATIVE_MERGED,
                    onClick = { selectedOption = ExportFormatOption.NATIVE_MERGED }
                )
                ExportOptionCard(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "PDF en formato de hoja estándar + firmas",
                    subtitle = "Genera PDF vectorial respetando el tamaño de hoja elegido (A4, Carta, A3, etc.) con texto seleccionable y tus firmas/dibujos.",
                    selected = selectedOption == ExportFormatOption.UNIVERSAL_PDF,
                    onClick = { selectedOption = ExportFormatOption.UNIVERSAL_PDF }
                )
            } else {
                ExportOptionCard(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "PDF fusionado (Dibujos + Texto/OCR seleccionable)",
                    subtitle = "Inyecta trazos vectoriales sin aplastar el texto original y añade capa OCR invisible seleccionable para manhwas.",
                    selected = true,
                    onClick = { selectedOption = ExportFormatOption.NATIVE_MERGED }
                )
            }

            Spacer(Modifier.height(4.dp))

            TactileButton(
                onClick = { onExportSaveAs(selectedOption) },
                enabled = !isExporting,
                cornerRadius = 16.dp,
                lipDepth = 3.dp,
                modifier = Modifier.testTag("export_save_as_button")
            ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isExporting) "Fusionando…" else "Guardar en dispositivo",
                    fontWeight = FontWeight.Bold
                )
            }

            TactileButton(
                onClick = { onExportShare(selectedOption) },
                enabled = !isExporting,
                containerColor = palette.surfaceAlt,
                shadowColor = palette.borderShadow,
                contentColor = palette.ink,
                borderColor = palette.border,
                cornerRadius = 16.dp,
                lipDepth = 3.dp,
                modifier = Modifier.testTag("export_share_button")
            ) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir archivo fusionado", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    val palette = LocalFolioPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = palette.accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.inkMuted
        )
    }
}

@Composable
private fun ExportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.5f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.accent else palette.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) palette.accent else palette.inkMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
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

package com.example.notebook.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notebook.data.NotebookPageEntity
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.engine.NotebookImageExportFormat
import com.example.notebook.model.ImageObject
import com.example.notebook.model.NotebookQuickStyle
import com.example.notebook.model.NotebookTextSpan
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.TableObject
import com.example.notebook.model.TextAlignMode
import com.example.notebook.model.TextListMode
import com.example.notebook.model.TextObject
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.TactileButton
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private val BoxBorderOptions: List<Pair<String, Long?>> = listOf(
    "Sin borde" to null,
    "Rojo" to 0xFFE11D48L,
    "Negro" to 0xFF151C2BL,
    "Morado" to 0xFF7C3AEDL,
    "Azul" to 0xFF1D5BFFL,
    "Verde" to 0xFF16A34AL
)

private val BoxFillOptions: List<Pair<String, Long?>> = listOf(
    "Transp." to null,
    "Blanco" to 0xFFFFFFFFL,
    "Crema" to 0xFFFFFBEBL,
    "Celeste" to 0xFFEFF6FFL,
    "Rosa" to 0xFFFDF2F8L,
    "Menta" to 0xFFECFDF5L
)

private val QuickStudySymbols = listOf(
    "α", "β", "γ", "μ", "Δ", "↑", "↓", "→", "←", "⇄", "≥", "≤", "±", "°", "²", "³", "⁺", "⁻", "₂", "₃", "✓", "✗"
)

/**
 * Rich Multi-Color Text Box & Title Banner Editor (`TextObject`):
 * - Multi-color spans (bold, italic, underline, strikethrough, superscript, subscript, inline highlight).
 * - 28 categorized study fonts (Favorites + Recents + Search), disabling bold/italic on single-weight fonts.
 * - 1-tap Quick Styles (built-in + custom saved in DataStore), quick clinical/math symbols, bullets/numbering,
 *   alignment, line spacing, banner shadow, rounded border, and background fill.
 */
@Composable
fun RichTextBoxEditorDialog(
    initialItem: TextObject,
    favoriteFontIds: List<String> = emptyList(),
    recentFontIds: List<String> = emptyList(),
    customQuickStyles: List<NotebookQuickStyle> = emptyList(),
    onToggleFavoriteFont: (String) -> Unit = {},
    onFontSelected: (String) -> Unit = {},
    onSaveQuickStyle: (NotebookQuickStyle) -> Unit = {},
    onDeleteQuickStyle: (String) -> Unit = {},
    onSave: (TextObject) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val spans = remember(initialItem.id) {
        mutableStateListOf<NotebookTextSpan>().apply {
            if (initialItem.spans.isNotEmpty()) addAll(initialItem.spans)
        }
    }
    var draftText by remember { mutableStateOf("") }
    var draftColor by remember { mutableLongStateOf(if (initialItem.isBanner) 0xFF1D5BFFL else 0xFF151C2BL) }
    var draftBold by remember { mutableStateOf(initialItem.isBanner) }
    var draftItalic by remember { mutableStateOf(false) }
    var draftUnderline by remember { mutableStateOf(false) }
    var draftStrike by remember { mutableStateOf(false) }
    var draftSup by remember { mutableStateOf(false) }
    var draftSub by remember { mutableStateOf(false) }
    var draftHighlight by remember { mutableStateOf(false) }

    var fontId by remember(initialItem.id) { mutableStateOf(NotebookFonts.canonicalId(initialItem.fontId)) }
    var showFontPicker by remember { mutableStateOf(false) }
    var listMode by remember(initialItem.id) { mutableStateOf(initialItem.listMode) }
    var alignMode by remember(initialItem.id) { mutableStateOf(initialItem.alignment) }
    var isBanner by remember(initialItem.id) { mutableStateOf(initialItem.isBanner) }
    var fontSizeNorm by remember(initialItem.id) { mutableFloatStateOf(initialItem.fontSizeNorm) }
    var lineSpacingMult by remember(initialItem.id) { mutableFloatStateOf(initialItem.lineSpacingMult) }
    var borderColor by remember(initialItem.id) { mutableStateOf(initialItem.borderColorArgb) }
    var fillColor by remember(initialItem.id) { mutableStateOf(initialItem.fillColorArgb) }

    val fontSpec = remember(fontId) { NotebookFonts.specFor(fontId) }
    val composeFont = remember(fontId) { NotebookFonts.getComposeFontFamily(fontId) }

    LaunchedEffect(fontSpec.id) {
        if (!fontSpec.supportsBold && draftBold) draftBold = false
        if (!fontSpec.supportsItalic && draftItalic) draftItalic = false
    }

    LaunchedEffect(initialItem.id) {
        if (initialItem.spans.size == 1) {
            val only = initialItem.spans.first()
            draftText = only.text
            draftColor = only.colorArgb
            draftBold = only.isBold && fontSpec.supportsBold
            draftItalic = only.isItalic && fontSpec.supportsItalic
            draftUnderline = only.isUnderline
            draftStrike = only.isStrikethrough
            draftSup = only.isSuperscript
            draftSub = only.isSubscript
            draftHighlight = only.highlightColorArgb != null
            spans.clear()
        }
    }

    fun applyQuickStyle(style: NotebookQuickStyle) {
        val canonical = NotebookFonts.canonicalId(style.fontId)
        val sp = NotebookFonts.specFor(canonical)
        fontId = canonical
        onFontSelected(canonical)
        draftColor = style.textColorArgb
        draftBold = style.isBold && sp.supportsBold
        if (!sp.supportsItalic) draftItalic = false
        draftHighlight = style.highlightColorArgb != null
        fillColor = style.fillColorArgb
        borderColor = style.borderColorArgb
        fontSizeNorm = style.fontSizeNorm
        isBanner = style.isBanner
        if (style.isBanner) alignMode = TextAlignMode.CENTER
        if (spans.isNotEmpty()) {
            val updated = spans.map {
                it.copy(
                    colorArgb = style.textColorArgb,
                    isBold = style.isBold && sp.supportsBold,
                    isItalic = it.isItalic && sp.supportsItalic,
                    highlightColorArgb = style.highlightColorArgb
                )
            }
            spans.clear()
            spans.addAll(updated)
        }
    }

    fun appendDraftSpan() {
        if (draftText.isEmpty()) return
        spans.add(
            NotebookTextSpan(
                text = draftText,
                colorArgb = draftColor,
                isBold = draftBold && fontSpec.supportsBold,
                isItalic = draftItalic && fontSpec.supportsItalic,
                isUnderline = draftUnderline,
                isStrikethrough = draftStrike,
                isSuperscript = draftSup,
                isSubscript = draftSub,
                highlightColorArgb = if (draftHighlight) 0xFFFDE047L else null
            )
        )
        draftText = ""
        draftSup = false
        draftSub = false
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 480.dp,
        maxHeightFraction = 0.93f,
        testTag = "notebook_richtext_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = if (isBanner) "Título / Banner" else "Caja de texto enriquecido",
            subtitle = "Spans multicolor, sub/superíndice, tipografías y estilos rápidos",
            leadingIcon = Icons.Rounded.TextFields,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Estilos rápidos (1-tap presets + custom saved styles)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Estilos rápidos", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (qs in NotebookQuickStyle.BUILT_IN) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(qs.fillColorArgb?.let { Color(it.toInt()) } ?: palette.surfaceAlt)
                                .border(1.dp, qs.borderColorArgb?.let { Color(it.toInt()) } ?: palette.border, RoundedCornerShape(10.dp))
                                .clickable { applyQuickStyle(qs) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("nb_quick_style_${qs.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = qs.name,
                                fontSize = 11.sp,
                                fontWeight = if (qs.isBold) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = Color(qs.textColorArgb.toInt())
                            )
                        }
                    }
                    for (qs in customQuickStyles) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(qs.fillColorArgb?.let { Color(it.toInt()) } ?: palette.accentSoft)
                                .border(1.dp, qs.borderColorArgb?.let { Color(it.toInt()) } ?: palette.accent, RoundedCornerShape(10.dp))
                                .clickable { applyQuickStyle(qs) }
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = qs.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(qs.textColorArgb.toInt())
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Borrar estilo",
                                tint = palette.inkMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { onDeleteQuickStyle(qs.id) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accentSoft)
                            .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable {
                                val nextNum = customQuickStyles.size + 1
                                onSaveQuickStyle(
                                    NotebookQuickStyle(
                                        id = "usr_${UUID.randomUUID().toString().take(8)}",
                                        name = "Mi estilo $nextNum",
                                        fontId = fontId,
                                        textColorArgb = draftColor,
                                        fillColorArgb = fillColor,
                                        borderColorArgb = borderColor,
                                        highlightColorArgb = if (draftHighlight) 0xFFFDE047L else null,
                                        fontSizeNorm = fontSizeNorm,
                                        isBanner = isBanner,
                                        isBold = draftBold && fontSpec.supportsBold,
                                        isBuiltIn = false
                                    )
                                )
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                            .testTag("nb_save_quick_style_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                        Text("Guardar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                    }
                }
            }

            val previewSpans = spans.toList() + if (draftText.isNotEmpty()) {
                listOf(
                    NotebookTextSpan(
                        text = draftText,
                        colorArgb = draftColor,
                        isBold = draftBold && fontSpec.supportsBold,
                        isItalic = draftItalic && fontSpec.supportsItalic,
                        isUnderline = draftUnderline,
                        isStrikethrough = draftStrike,
                        isSuperscript = draftSup,
                        isSubscript = draftSub,
                        highlightColorArgb = if (draftHighlight) 0xFFFDE047L else null
                    )
                )
            } else emptyList()

            // 2. Live WYSIWYG preview rendered in real bundled FontFamily
            val previewFontSp = (fontSizeNorm * 720f).coerceIn(13f, 28f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(fillColor?.let { Color(it.toInt()) } ?: Color.White)
                    .border(
                        width = if (borderColor != null) 2.dp else 1.dp,
                        color = borderColor?.let { Color(it.toInt()) } ?: palette.border,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (previewSpans.isEmpty()) {
                    Text(
                        text = "Vista previa (${fontSpec.displayName})…",
                        fontFamily = composeFont,
                        fontSize = previewFontSp.sp,
                        color = Color(0xFF94A3B8)
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            for (sp in previewSpans) {
                                val decos = buildList {
                                    if (sp.isUnderline) add(TextDecoration.Underline)
                                    if (sp.isStrikethrough) add(TextDecoration.LineThrough)
                                }
                                val decoration = when (decos.size) {
                                    0 -> TextDecoration.None
                                    1 -> decos.first()
                                    else -> TextDecoration.combine(decos)
                                }
                                val shift = when {
                                    sp.isSuperscript -> BaselineShift.Superscript
                                    sp.isSubscript -> BaselineShift.Subscript
                                    else -> BaselineShift.None
                                }
                                val spanSize = if (sp.isSuperscript || sp.isSubscript) {
                                    (previewFontSp * 0.72f).sp
                                } else {
                                    previewFontSp.sp
                                }
                                withStyle(
                                    SpanStyle(
                                        color = Color(sp.colorArgb.toInt()),
                                        fontFamily = composeFont,
                                        fontSize = spanSize,
                                        fontWeight = if (sp.isBold && fontSpec.supportsBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (sp.isItalic && fontSpec.supportsItalic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = decoration,
                                        baselineShift = shift,
                                        background = sp.highlightColorArgb?.let { Color(it.toInt()).copy(alpha = 0.5f) }
                                            ?: Color.Unspecified
                                    )
                                ) {
                                    val prefix = when {
                                        length == 0 && listMode == TextListMode.BULLETS -> "• "
                                        length == 0 && listMode == TextListMode.NUMBERED -> "1. "
                                        else -> ""
                                    }
                                    append(prefix + sp.text)
                                }
                            }
                        },
                        fontSize = previewFontSp.sp
                    )
                }
            }

            OutlinedTextField(
                value = draftText,
                onValueChange = { draftText = it },
                label = { Text("Escribe texto o tramo de estilo") },
                placeholder = { Text("Ej. H₂O · Ca²⁺ · Dosis terapéutica") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nb_text_input")
            )

            // Quick scientific / clinical symbols bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (sym in QuickStudySymbols) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .clickable { draftText += sym }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sym, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    }
                }
            }

            // Active span color dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                for (c in NotebookQuickInkColors) {
                    val sel = c == draftColor
                    Box(
                        modifier = Modifier
                            .size(if (sel) 28.dp else 24.dp)
                            .clip(CircleShape)
                            .background(Color(c.toInt()))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) palette.ink else palette.border, CircleShape)
                            .clickable { draftColor = c }
                    )
                }
            }

            // Span styles: Bold, Italic, Underline, Strikethrough, Superscript, Subscript, Highlight + "+ Otro tramo"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallToggleChip(
                    label = "Negrita",
                    selected = draftBold && fontSpec.supportsBold,
                    enabled = fontSpec.supportsBold,
                    onClick = { draftBold = !draftBold },
                    icon = Icons.Rounded.FormatBold
                )
                SmallToggleChip(
                    label = "Cursiva",
                    selected = draftItalic && fontSpec.supportsItalic,
                    enabled = fontSpec.supportsItalic,
                    onClick = { draftItalic = !draftItalic },
                    icon = Icons.Rounded.FormatItalic
                )
                SmallToggleChip("Subr.", draftUnderline, { draftUnderline = !draftUnderline }, Icons.Rounded.FormatUnderlined)
                SmallToggleChip("Tachado", draftStrike, { draftStrike = !draftStrike }, Icons.Rounded.FormatStrikethrough)
                SmallToggleChip("X²", draftSup, {
                    draftSup = !draftSup
                    if (draftSup) draftSub = false
                })
                SmallToggleChip("X₂", draftSub, {
                    draftSub = !draftSub
                    if (draftSub) draftSup = false
                })
                SmallToggleChip("Resaltar", draftHighlight, { draftHighlight = !draftHighlight })
                if (draftText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accentSoft)
                            .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { appendDraftSpan() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("nb_add_color_span")
                    ) {
                        Text("+ Otro tramo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.accent)
                    }
                }
                if (spans.isNotEmpty()) {
                    SmallToggleChip("Quitar último", false, { if (spans.isNotEmpty()) spans.removeAt(spans.lastIndex) })
                }
            }

            // Categorized Font Picker trigger card (shows real selected typeface + category)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .clickable { showFontPicker = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("nb_open_font_picker_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fontSpec.displayName,
                        fontFamily = composeFont,
                        fontSize = 16.sp,
                        color = palette.ink
                    )
                    Text(
                        text = "${fontSpec.category.label} · ${NotebookFonts.CATALOG.size} fuentes disponibles",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Cambiar tipografía", tint = palette.accent, modifier = Modifier.size(20.dp))
            }

            // List mode + Alignment + Banner shadow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (lm in TextListMode.entries) {
                    SmallToggleChip(lm.label, listMode == lm, { listMode = lm })
                }
                SmallToggleChip(
                    label = "Banner sombra",
                    selected = isBanner,
                    onClick = {
                        isBanner = !isBanner
                        if (isBanner) {
                            fontId = NotebookFonts.DEFAULT_BANNER_ID
                            alignMode = TextAlignMode.CENTER
                            if (fillColor == null) fillColor = 0xFFFFFFFFL
                            if (borderColor == null) borderColor = 0xFF1D5BFFL
                            if (fontSizeNorm < 0.030f) fontSizeNorm = 0.036f
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (am in TextAlignMode.entries) {
                    SmallToggleChip(am.label, alignMode == am, { alignMode = am })
                }
            }

            // Border color selector
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Borde redondeado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((label, col) in BoxBorderOptions) {
                        val sel = borderColor == col
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) palette.accentSoft else palette.surfaceAlt)
                                .border(1.5.dp, col?.let { Color(it.toInt()) } ?: palette.border, RoundedCornerShape(10.dp))
                                .clickable { borderColor = col }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                        }
                    }
                }
            }

            // Fill color selector
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Fondo de caja", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((label, col) in BoxFillOptions) {
                        val sel = fillColor == col
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(col?.let { Color(it.toInt()) } ?: palette.surfaceAlt)
                                .border(if (sel) 2.dp else 1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(10.dp))
                                .clickable { fillColor = col }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF151C2B))
                        }
                    }
                }
            }

            // Font size + Line spacing sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tamaño", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted, modifier = Modifier.width(68.dp))
                Slider(
                    value = fontSizeNorm,
                    onValueChange = { fontSizeNorm = it },
                    valueRange = 0.014f..0.058f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
                Text("${(fontSizeNorm * 1000f).roundToInt()}", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Interlineado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted, modifier = Modifier.width(68.dp))
                Slider(
                    value = lineSpacingMult,
                    onValueChange = { lineSpacingMult = it },
                    valueRange = 1.0f..2.0f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
                Text(String.format("%.1fx", lineSpacingMult), style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
            }

            TactileButton(
                onClick = {
                    val finalSpans = previewSpans.ifEmpty {
                        listOf(
                            NotebookTextSpan(
                                text = if (isBanner) "TÍTULO DE APUNTES" else "Texto",
                                colorArgb = draftColor,
                                isBold = draftBold && fontSpec.supportsBold,
                                isItalic = draftItalic && fontSpec.supportsItalic,
                                isUnderline = draftUnderline,
                                isStrikethrough = draftStrike,
                                isSuperscript = draftSup,
                                isSubscript = draftSub
                            )
                        )
                    }
                    onFontSelected(fontId)
                    onSave(
                        initialItem.copy(
                            spans = finalSpans,
                            fontId = fontId,
                            fontSizeNorm = fontSizeNorm,
                            lineSpacingMult = lineSpacingMult,
                            alignment = alignMode,
                            listMode = listMode,
                            isBanner = isBanner,
                            hasShadow = isBanner,
                            borderColorArgb = borderColor,
                            fillColorArgb = fillColor
                        )
                    )
                    dismissWithAnim()
                },
                cornerRadius = 15.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nb_text_save_button")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aplicar en la hoja", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showFontPicker) {
        NotebookFontPickerDialog(
            selectedFontId = fontId,
            favoriteFontIds = favoriteFontIds,
            recentFontIds = recentFontIds,
            onSelectFont = { chosen ->
                fontId = chosen
                onFontSelected(chosen)
            },
            onToggleFavorite = onToggleFavoriteFont,
            onDismiss = { showFontPicker = false }
        )
    }
}

@Composable
fun CalloutLabelEditorDialog(
    shape: ShapeObject,
    onSave: (ShapeObject) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var text by remember(shape.id) { mutableStateOf(shape.labelText.ifBlank { "Etiqueta" }) }
    var bgColor by remember(shape.id) { mutableLongStateOf(shape.labelBgColorArgb) }
    var hasShadow by remember(shape.id) { mutableStateOf(shape.hasShadow) }
    val bgOptions = listOf(0xFFFFF8D6L, 0xFFFFFFFFL, 0xFFEFF6FFL, 0xFFFDF2F8L, 0xFFECFDF5L)

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 380.dp,
        maxHeightFraction = 0.68f,
        testTag = "nb_callout_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Globo / Etiqueta señaladora",
            subtitle = "Rótulo con cola que apunta a una zona",
            leadingIcon = Icons.Rounded.TextFields,
            onDismiss = dismissWithAnim
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Texto de la etiqueta") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (c in bgOptions) {
                    val sel = c == bgColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(c.toInt()))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) palette.accent else palette.border, CircleShape)
                            .clickable { bgColor = c }
                    )
                }
                Spacer(Modifier.weight(1f))
                SmallToggleChip("Sombra", hasShadow, { hasShadow = !hasShadow })
            }
            TactileButton(
                onClick = {
                    onSave(
                        shape.copy(
                            labelText = text.trim().ifBlank { "Etiqueta" },
                            labelBgColorArgb = bgColor,
                            hasShadow = hasShadow
                        )
                    )
                    dismissWithAnim()
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar etiqueta", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Image Crop, Flip, Opacity, Shadow & Rounded Corners Inspector (`ImageObject`) with full PNG transparency.
 */
@Composable
fun ImageCropAndStyleDialog(
    imageItem: ImageObject,
    bitmap: Bitmap?,
    onRemoveBackground: () -> Unit = {},
    onSave: (ImageObject) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var cornerRadius by remember(imageItem.id) { mutableFloatStateOf(imageItem.cornerRadiusDp) }
    var isOvalCrop by remember(imageItem.id) { mutableStateOf(imageItem.isOvalCrop) }
    var opacity by remember(imageItem.id) { mutableFloatStateOf(imageItem.opacity) }
    var flipH by remember(imageItem.id) { mutableStateOf(imageItem.flipHorizontal) }
    var flipV by remember(imageItem.id) { mutableStateOf(imageItem.flipVertical) }
    var hasShadow by remember(imageItem.id) { mutableStateOf(imageItem.hasShadow) }
    var cropLeft by remember(imageItem.id) { mutableFloatStateOf(imageItem.cropLeft) }
    var cropTop by remember(imageItem.id) { mutableFloatStateOf(imageItem.cropTop) }
    var cropRight by remember(imageItem.id) { mutableFloatStateOf(imageItem.cropRight) }
    var cropBottom by remember(imageItem.id) { mutableFloatStateOf(imageItem.cropBottom) }
    var borderColor by remember(imageItem.id) { mutableStateOf(imageItem.borderColorArgb) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 430.dp,
        maxHeightFraction = 0.90f,
        testTag = "nb_image_crop_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Recorte, volteo y estilo",
            subtitle = "Rectangular, ovalado o PNG transparente intacto",
            leadingIcon = Icons.Rounded.Crop,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bitmap != null && !bitmap.isRecycled) {
                val imgBmp = remember(bitmap) { bitmap.asImageBitmap() }
                val previewClip = if (isOvalCrop) CircleShape else RoundedCornerShape(cornerRadius.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(136.dp)
                            .graphicsLayer {
                                scaleX = if (flipH) -1f else 1f
                                scaleY = if (flipV) -1f else 1f
                                alpha = opacity
                            }
                            .clip(previewClip)
                    ) {
                        val sx = (cropLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                        val sy = (cropTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                        val sw = ((cropRight - cropLeft) * bitmap.width).toInt().coerceIn(1, bitmap.width - sx)
                        val sh = ((cropBottom - cropTop) * bitmap.height).toInt().coerceIn(1, bitmap.height - sy)
                        drawImage(
                            image = imgBmp,
                            srcOffset = IntOffset(sx, sy),
                            srcSize = IntSize(sw, sh),
                            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                        )
                    }
                }
            }

            // Shape crop mode (Rectangular vs Oval/Circle) + Flip & Shadow toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallToggleChip("Rectangular", !isOvalCrop, { isOvalCrop = false })
                SmallToggleChip("Óvalo / Círculo", isOvalCrop, { isOvalCrop = true })
                SmallToggleChip("Voltear H", flipH, { flipH = !flipH })
                SmallToggleChip("Voltear V", flipV, { flipV = !flipV })
                SmallToggleChip("Sombra", hasShadow, { hasShadow = !hasShadow })
            }

            // On-device ML Kit Background Removal button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.accentSoft)
                    .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                    .clickable {
                        dismissWithAnim()
                        onRemoveBackground()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("nb_image_remove_bg_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoFixHigh,
                    contentDescription = "Quitar fondo con IA",
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Quitar fondo (IA en el dispositivo)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }

            // Rounded corner presets (when rectangular)
            if (!isOvalCrop) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Recto" to 0f, "Suave" to 12f, "Redondo" to 26f, "Píldora" to 48f).forEach { (lbl, cr) ->
                        val sel = kotlin.math.abs(cornerRadius - cr) < 2f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) palette.accent else palette.surfaceAlt)
                                .clickable { cornerRadius = cr }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) palette.onAccent else palette.ink)
                        }
                    }
                }
            }

            CropSliderRow("Opacidad", opacity, 0.15f..1f) { opacity = it }
            CropSliderRow("Izquierda", cropLeft, 0f..(cropRight - 0.12f).coerceAtLeast(0.05f)) { cropLeft = it }
            CropSliderRow("Derecha", cropRight, (cropLeft + 0.12f).coerceAtMost(0.95f)..1f) { cropRight = it }
            CropSliderRow("Arriba", cropTop, 0f..(cropBottom - 0.12f).coerceAtLeast(0.05f)) { cropTop = it }
            CropSliderRow("Abajo", cropBottom, (cropTop + 0.12f).coerceAtMost(0.95f)..1f) { cropBottom = it }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((lbl, col) in BoxBorderOptions) {
                    val sel = borderColor == col
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) palette.accentSoft else palette.surfaceAlt)
                            .border(1.5.dp, col?.let { Color(it.toInt()) } ?: palette.border, RoundedCornerShape(10.dp))
                            .clickable { borderColor = col }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    }
                }
            }

            TactileButton(
                onClick = {
                    onSave(
                        imageItem.copy(
                            cornerRadiusDp = cornerRadius,
                            isOvalCrop = isOvalCrop,
                            opacity = opacity,
                            flipHorizontal = flipH,
                            flipVertical = flipV,
                            hasShadow = hasShadow,
                            cropLeft = cropLeft,
                            cropTop = cropTop,
                            cropRight = cropRight,
                            cropBottom = cropBottom,
                            borderColorArgb = borderColor
                        )
                    )
                    dismissWithAnim()
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Aplicar cambios", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Editable n × m Table Editor (`TableObject`) for comparative pharmacology/study charts.
 */
@Composable
fun TableEditorDialog(
    table: TableObject,
    onSave: (TableObject) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var rows by remember(table.id) { mutableIntStateOf(table.rows.coerceIn(2, 6)) }
    var cols by remember(table.id) { mutableIntStateOf(table.cols.coerceIn(2, 5)) }
    var hasHeader by remember(table.id) { mutableStateOf(table.hasHeaderRow) }
    val cellValues = remember(table.id) {
        mutableStateListOf<String>().apply {
            val needed = 6 * 5
            for (i in 0 until needed) add("")
            for (r in 0 until table.rows.coerceIn(1, 6)) {
                for (c in 0 until table.cols.coerceIn(1, 5)) {
                    this[r * 5 + c] = table.cells.getOrNull(r * table.cols + c).orEmpty()
                }
            }
        }
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 460.dp,
        maxHeightFraction = 0.88f,
        testTag = "nb_table_editor_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Tabla de estudio ($rows × $cols)",
            subtitle = "Edita filas, columnas y contenido de cada celda",
            leadingIcon = Icons.Rounded.TableChart,
            onDismiss = dismissWithAnim
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dimension controls + Header toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperBadge("Filas", rows, onDec = { if (rows > 2) rows-- }, onInc = { if (rows < 6) rows++ })
                StepperBadge("Cols", cols, onDec = { if (cols > 2) cols-- }, onInc = { if (cols < 5) cols++ })
                Spacer(Modifier.weight(1f))
                SmallToggleChip("Encabezado", hasHeader, { hasHeader = !hasHeader })
            }

            // Cell inputs grid
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in 0 until cols) {
                        val slot = r * 5 + c
                        OutlinedTextField(
                            value = cellValues[slot],
                            onValueChange = { cellValues[slot] = it },
                            placeholder = {
                                Text(
                                    if (r == 0 && hasHeader) "Col ${c + 1}" else "—",
                                    fontSize = 11.sp
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (r == 0 && hasHeader) FontWeight.Bold else FontWeight.Normal
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            TactileButton(
                onClick = {
                    val packed = ArrayList<String>(rows * cols)
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            packed.add(cellValues[r * 5 + c])
                        }
                    }
                    onSave(
                        table.copy(
                            rows = rows,
                            cols = cols,
                            cells = packed,
                            hasHeaderRow = hasHeader
                        )
                    )
                    dismissWithAnim()
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nb_table_save_button")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aplicar tabla", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepperBadge(label: String, value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = palette.inkMuted, fontWeight = FontWeight.Bold)
        Icon(
            Icons.Rounded.Remove,
            contentDescription = "Menos $label",
            tint = palette.ink,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onDec)
                .padding(2.dp)
        )
        Text("$value", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
        Icon(
            Icons.Rounded.Add,
            contentDescription = "Más $label",
            tint = palette.ink,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onInc)
                .padding(2.dp)
        )
    }
}

@Composable
private fun CropSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val palette = LocalFolioPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, modifier = Modifier.width(64.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(26.dp),
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
        )
    }
}

/**
 * Page Organizer & Paper Style Drawer/Dialog.
 */
@Composable
fun NotebookPagesSheetDialog(
    pages: List<NotebookPageEntity>,
    currentPageIndex: Int,
    activeTemplate: NotebookPageTemplate,
    onSelectPage: (Int) -> Unit,
    onAddBlankPage: () -> Unit,
    onAddPdfPages: () -> Unit,
    onSetBackgroundImage: () -> Unit,
    onChangeTemplate: (NotebookPageTemplate) -> Unit,
    onChangePaperColor: (Long) -> Unit,
    onDuplicateCurrent: () -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeletePage: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 460.dp,
        maxHeightFraction = 0.88f,
        testTag = "nb_pages_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Páginas y papel (${pages.size})",
            subtitle = "Añade hojas, fondos PDF/imagen o cambia plantilla",
            leadingIcon = Icons.Rounded.Layers,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionTile(Icons.Rounded.Add, "+ Hoja", { onAddBlankPage(); dismissWithAnim() }, Modifier.weight(1f))
                SmallActionTile(Icons.Rounded.PictureAsPdf, "+ PDF", { dismissWithAnim(); onAddPdfPages() }, Modifier.weight(1f))
                SmallActionTile(Icons.Rounded.Image, "Fondo img", { dismissWithAnim(); onSetBackgroundImage() }, Modifier.weight(1f))
                SmallActionTile(Icons.Rounded.ContentCopy, "Duplicar", { onDuplicateCurrent(); dismissWithAnim() }, Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Plantilla del cuaderno", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (tpl in NotebookPageTemplate.entries) {
                        val sel = tpl == activeTemplate
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) palette.accent else palette.surfaceAlt)
                                .clickable { onChangeTemplate(tpl) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(tpl.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) palette.onAccent else palette.ink)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Color hoja:", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                    listOf(0xFFFFFFFFL, 0xFFFCF8EEL, 0xFFF5ECD8L, 0xFF181C24L).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(c.toInt()))
                                .border(1.dp, palette.border, CircleShape)
                                .clickable { onChangePaperColor(c) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.forEachIndexed { idx, page ->
                    val isCurrent = idx == currentPageIndex
                    var bmp by remember(page.thumbnailPath) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(page.thumbnailPath) {
                        bmp = if (page.thumbnailPath.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val f = File(page.thumbnailPath)
                                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                                }.getOrNull()
                            }
                        } else null
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) palette.accentSoft else palette.surfaceAlt)
                            .border(
                                if (isCurrent) 1.5.dp else 1.dp,
                                if (isCurrent) palette.accent else palette.border,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onSelectPage(idx)
                                dismissWithAnim()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .aspectRatio(0.74f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(page.backgroundColor.toInt()))
                                .border(1.dp, palette.border, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val loaded = bmp
                            if (loaded != null) {
                                Image(
                                    bitmap = loaded.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("${idx + 1}", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Página ${idx + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            Text(
                                text = if (page.backgroundAssetPath.isNotBlank()) "Con fondo PDF / Imagen" else "Hoja de plantilla",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.inkMuted
                            )
                        }

                        if (idx > 0) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Subir página",
                                tint = palette.inkMuted,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { onMovePage(idx, idx - 1) }
                                    .padding(4.dp)
                            )
                        }
                        if (idx < pages.lastIndex) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "Bajar página",
                                tint = palette.inkMuted,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { onMovePage(idx, idx + 1) }
                                    .padding(4.dp)
                            )
                        }
                        if (pages.size > 1) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Eliminar página",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { onDeletePage(page.id) }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = palette.accent, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.ink, maxLines = 1)
    }
}

@Composable
private fun SmallToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true
) {
    val palette = LocalFolioPalette.current
    val contentAlpha = if (enabled) 1f else 0.36f
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected && enabled) palette.accent else palette.surfaceAlt)
            .border(1.dp, if (selected && enabled) palette.accent else palette.border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = (if (selected && enabled) palette.onAccent else palette.ink).copy(alpha = contentAlpha),
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = (if (selected && enabled) palette.onAccent else palette.ink).copy(alpha = contentAlpha)
        )
    }
}

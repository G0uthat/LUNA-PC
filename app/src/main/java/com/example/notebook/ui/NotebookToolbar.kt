package com.example.notebook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notebook.model.BuiltInStickerKind
import com.example.notebook.model.NotebookEraserMode
import com.example.notebook.model.NotebookShapeKind
import com.example.notebook.model.NotebookTool
import com.example.ui.theme.LocalFolioPalette
import kotlin.math.roundToInt

// Curated medical & academic study palette
val NotebookQuickInkColors = listOf(
    0xFF151C2BL, // Grafito
    0xFF1D5BFFL, // Azul cobalto
    0xFFE11D48L, // Carmesí
    0xFFEC4899L, // Rosa estudio
    0xFF16A34AL, // Esmeralda
    0xFF7C3AEDL, // Morado
    0xFFF97316L, // Naranja
    0xFF0891B2L  // Turquesa
)

val NotebookQuickHighlighterColors = listOf(
    0xFFFDE047L, // Amarillo
    0xFF86EFACL, // Verde
    0xFFF9A8D4L, // Rosa
    0xFF7DD3FCL, // Celeste
    0xFFFDBA74L, // Naranja
    0xFFD8B4FEL  // Lavanda
)

// Core 9 studio dock tools requested in Phase 2 (extended study tools live neatly in contextual chips)
private val PrimaryDockTools = listOf(
    NotebookTool.PENCIL,
    NotebookTool.PEN,
    NotebookTool.HIGHLIGHTER,
    NotebookTool.ERASER,
    NotebookTool.SELECT,
    NotebookTool.SHAPE,
    NotebookTool.TEXT_BOX,
    NotebookTool.IMAGE,
    NotebookTool.PAN_ZOOM
)

private fun toolIcon(tool: NotebookTool): ImageVector = when (tool) {
    NotebookTool.PENCIL -> Icons.Rounded.Edit
    NotebookTool.PEN -> Icons.Rounded.Create
    NotebookTool.FINELINER -> Icons.Rounded.Brush
    NotebookTool.HIGHLIGHTER -> Icons.Rounded.BorderColor
    NotebookTool.ERASER -> Icons.Rounded.AutoFixOff
    NotebookTool.SELECT -> Icons.Rounded.Gesture
    NotebookTool.SHAPE -> Icons.Rounded.Category
    NotebookTool.TEXT_BOX -> Icons.Rounded.TextFields
    NotebookTool.TITLE_BANNER -> Icons.Rounded.Subtitles
    NotebookTool.CONNECTOR -> Icons.Rounded.Timeline
    NotebookTool.STICKER -> Icons.Rounded.AutoAwesome
    NotebookTool.TABLE -> Icons.Rounded.TableChart
    NotebookTool.IMAGE -> Icons.Rounded.Image
    NotebookTool.PAN_ZOOM -> Icons.Rounded.PanTool
}

/**
 * Translucent floating Study Studio Toolbar (ZERO Modifier.blur()!).
 * - Adapts strictly via M3 `NotebookWindowWidthClass`:
 *   - `MEDIUM` / `EXPANDED`: collapsible floating vertical side dock on the left edge.
 *   - `COMPACT`: slim scrollable bottom dock.
 * - All primary controls enforce >= 48 dp touch targets for accessibility and stylus comfort.
 */
@Composable
fun NotebookStudioToolbar(
    widthClass: NotebookWindowWidthClass,
    isDockCollapsed: Boolean,
    onToggleDockCollapsed: () -> Unit,
    activeTool: NotebookTool,
    activeShapeKind: NotebookShapeKind,
    activeStickerKind: BuiltInStickerKind,
    activeColorArgb: Long,
    activeHighlighterColorArgb: Long,
    recentColors: List<Long>,
    savedColors: List<Long>,
    strokeWidthNorm: Float,
    alpha: Float,
    stabilizerLevel: Float,
    eraserMode: NotebookEraserMode,
    isEyedropperActive: Boolean,
    shapeFillEnabled: Boolean,
    shapeDashedEnabled: Boolean,
    shapeShadowEnabled: Boolean,
    showPropertiesPanel: Boolean,
    myStickerPaths: List<String> = emptyList(),
    onSelectTool: (NotebookTool) -> Unit,
    onSelectShapeKind: (NotebookShapeKind) -> Unit,
    onSelectStickerKind: (BuiltInStickerKind) -> Unit,
    onSelectColor: (Long) -> Unit,
    onOpenColorPicker: () -> Unit,
    onToggleEyedropper: () -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onStabilizerChange: (Float) -> Unit,
    onSelectEraserMode: (NotebookEraserMode) -> Unit,
    onToggleShapeFill: () -> Unit,
    onToggleShapeDashed: () -> Unit,
    onToggleShapeShadow: () -> Unit,
    onTogglePropertiesPanel: () -> Unit,
    onPickImage: () -> Unit,
    onAddQuickTitleBanner: () -> Unit = {},
    onPasteClipboardImage: () -> Unit = {},
    onPickMyStickerPng: () -> Unit = {},
    onPlaceMySticker: (String) -> Unit = {},
    onOpenStarterTemplates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val surfaceColor = palette.surface.copy(alpha = 0.90f)
    val activeDisplayColor = if (activeTool == NotebookTool.HIGHLIGHTER) activeHighlighterColorArgb else activeColorArgb

    fun handleDockToolTap(tool: NotebookTool) {
        if (tool == NotebookTool.IMAGE) {
            // Switch to IMAGE/STICKER contextual hub or immediately open panel to pick Photo/PNG/Sticker/Table
            if (activeTool == NotebookTool.IMAGE || activeTool == NotebookTool.STICKER) {
                onPickImage()
            } else {
                onSelectTool(NotebookTool.IMAGE)
                if (!showPropertiesPanel) onTogglePropertiesPanel()
            }
            return
        }
        if (tool == activeTool) {
            onTogglePropertiesPanel()
        } else {
            onSelectTool(tool)
            if ((tool == NotebookTool.SHAPE || tool == NotebookTool.ERASER) && !showPropertiesPanel) {
                onTogglePropertiesPanel()
            }
        }
    }

    if (widthClass.isWide) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isDockCollapsed) {
                // Collapsed floating studio pill (48dp touch target)
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(surfaceColor)
                        .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                        .clickable(onClick = onToggleDockCollapsed)
                        .testTag("nb_expand_dock_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Mostrar herramientas",
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(surfaceColor)
                        .border(1.dp, palette.border, RoundedCornerShape(22.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("notebook_wide_rail"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Collapse handle (>= 48dp)
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleDockCollapsed)
                            .testTag("nb_collapse_dock_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Plegar barra lateral",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    for (tool in PrimaryDockTools) {
                        val selected = isDockToolSelected(activeTool, tool)
                        ToolIconButton(
                            icon = toolIcon(tool),
                            label = if (tool == NotebookTool.IMAGE) "Media" else tool.label,
                            selected = selected,
                            onClick = { handleDockToolTap(tool) },
                            testTag = "nb_tool_${tool.name}"
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // Color + Properties toggle (>= 48dp)
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePropertiesPanel)
                            .testTag("nb_toggle_properties"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(activeDisplayColor.toInt()))
                                .border(2.dp, palette.ink.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Panel contextual de herramienta",
                                tint = contrastTint(activeDisplayColor),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showPropertiesPanel,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    NotebookPropertiesPopover(
                        activeTool = activeTool,
                        activeShapeKind = activeShapeKind,
                        activeStickerKind = activeStickerKind,
                        activeColorArgb = activeDisplayColor,
                        recentColors = recentColors,
                        savedColors = savedColors,
                        myStickerPaths = myStickerPaths,
                        strokeWidthNorm = strokeWidthNorm,
                        alpha = alpha,
                        stabilizerLevel = stabilizerLevel,
                        eraserMode = eraserMode,
                        isEyedropperActive = isEyedropperActive,
                        shapeFillEnabled = shapeFillEnabled,
                        shapeDashedEnabled = shapeDashedEnabled,
                        shapeShadowEnabled = shapeShadowEnabled,
                        onSelectTool = onSelectTool,
                        onSelectShapeKind = onSelectShapeKind,
                        onSelectStickerKind = onSelectStickerKind,
                        onSelectColor = onSelectColor,
                        onOpenColorPicker = onOpenColorPicker,
                        onToggleEyedropper = onToggleEyedropper,
                        onStrokeWidthChange = onStrokeWidthChange,
                        onAlphaChange = onAlphaChange,
                        onStabilizerChange = onStabilizerChange,
                        onSelectEraserMode = onSelectEraserMode,
                        onToggleShapeFill = onToggleShapeFill,
                        onToggleShapeDashed = onToggleShapeDashed,
                        onToggleShapeShadow = onToggleShapeShadow,
                        onPickImage = onPickImage,
                        onAddQuickTitleBanner = onAddQuickTitleBanner,
                        onPasteClipboardImage = onPasteClipboardImage,
                        onPickMyStickerPng = onPickMyStickerPng,
                        onPlaceMySticker = onPlaceMySticker,
                        onOpenStarterTemplates = onOpenStarterTemplates,
                        modifier = Modifier.widthIn(max = 310.dp)
                    )
                }
            }
        }
    } else {
        // COMPACT window width -> bottom floating dock
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = showPropertiesPanel,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NotebookPropertiesPopover(
                    activeTool = activeTool,
                    activeShapeKind = activeShapeKind,
                    activeStickerKind = activeStickerKind,
                    activeColorArgb = activeDisplayColor,
                    recentColors = recentColors,
                    savedColors = savedColors,
                    myStickerPaths = myStickerPaths,
                    strokeWidthNorm = strokeWidthNorm,
                    alpha = alpha,
                    stabilizerLevel = stabilizerLevel,
                    eraserMode = eraserMode,
                    isEyedropperActive = isEyedropperActive,
                    shapeFillEnabled = shapeFillEnabled,
                    shapeDashedEnabled = shapeDashedEnabled,
                    shapeShadowEnabled = shapeShadowEnabled,
                    onSelectTool = onSelectTool,
                    onSelectShapeKind = onSelectShapeKind,
                    onSelectStickerKind = onSelectStickerKind,
                    onSelectColor = onSelectColor,
                    onOpenColorPicker = onOpenColorPicker,
                    onToggleEyedropper = onToggleEyedropper,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onAlphaChange = onAlphaChange,
                    onStabilizerChange = onStabilizerChange,
                    onSelectEraserMode = onSelectEraserMode,
                    onToggleShapeFill = onToggleShapeFill,
                    onToggleShapeDashed = onToggleShapeDashed,
                    onToggleShapeShadow = onToggleShapeShadow,
                    onPickImage = onPickImage,
                    onAddQuickTitleBanner = onAddQuickTitleBanner,
                    onPasteClipboardImage = onPasteClipboardImage,
                    onPickMyStickerPng = onPickMyStickerPng,
                    onPlaceMySticker = onPlaceMySticker,
                    onOpenStarterTemplates = onOpenStarterTemplates,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(surfaceColor)
                    .border(1.dp, palette.border, RoundedCornerShape(22.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("notebook_compact_dock"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePropertiesPanel)
                        .testTag("nb_toggle_properties"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(activeDisplayColor.toInt()))
                            .border(2.dp, palette.ink.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Colores y ajustes",
                            tint = contrastTint(activeDisplayColor),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                for (tool in PrimaryDockTools) {
                    val selected = isDockToolSelected(activeTool, tool)
                    ToolIconButton(
                        icon = toolIcon(tool),
                        label = if (tool == NotebookTool.IMAGE) "Media" else tool.label,
                        selected = selected,
                        onClick = { handleDockToolTap(tool) },
                        testTag = "nb_tool_${tool.name}"
                    )
                }
            }
        }
    }
}

private fun isDockToolSelected(activeTool: NotebookTool, dockTool: NotebookTool): Boolean = when (dockTool) {
    NotebookTool.PEN -> activeTool == NotebookTool.PEN || activeTool == NotebookTool.FINELINER
    NotebookTool.TEXT_BOX -> activeTool == NotebookTool.TEXT_BOX || activeTool == NotebookTool.TITLE_BANNER
    NotebookTool.SHAPE -> activeTool == NotebookTool.SHAPE || activeTool == NotebookTool.CONNECTOR
    NotebookTool.IMAGE -> activeTool == NotebookTool.IMAGE || activeTool == NotebookTool.STICKER || activeTool == NotebookTool.TABLE
    else -> activeTool == dockTool
}

private fun contrastTint(argb: Long): Color {
    val c = argb.toInt()
    val lum = 0.299f * android.graphics.Color.red(c) +
        0.587f * android.graphics.Color.green(c) +
        0.114f * android.graphics.Color.blue(c)
    return if (lum > 175f) Color(0xFF151C2B) else Color.White
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(if (selected) palette.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.onAccent else palette.ink,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) palette.onAccent else palette.inkMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun NotebookPropertiesPopover(
    activeTool: NotebookTool,
    activeShapeKind: NotebookShapeKind,
    activeStickerKind: BuiltInStickerKind,
    activeColorArgb: Long,
    recentColors: List<Long>,
    savedColors: List<Long>,
    myStickerPaths: List<String>,
    strokeWidthNorm: Float,
    alpha: Float,
    stabilizerLevel: Float,
    eraserMode: NotebookEraserMode,
    isEyedropperActive: Boolean,
    shapeFillEnabled: Boolean,
    shapeDashedEnabled: Boolean,
    shapeShadowEnabled: Boolean,
    onSelectTool: (NotebookTool) -> Unit,
    onSelectShapeKind: (NotebookShapeKind) -> Unit,
    onSelectStickerKind: (BuiltInStickerKind) -> Unit,
    onSelectColor: (Long) -> Unit,
    onOpenColorPicker: () -> Unit,
    onToggleEyedropper: () -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onStabilizerChange: (Float) -> Unit,
    onSelectEraserMode: (NotebookEraserMode) -> Unit,
    onToggleShapeFill: () -> Unit,
    onToggleShapeDashed: () -> Unit,
    onToggleShapeShadow: () -> Unit,
    onPickImage: () -> Unit,
    onAddQuickTitleBanner: () -> Unit,
    onPasteClipboardImage: () -> Unit,
    onPickMyStickerPng: () -> Unit,
    onPlaceMySticker: (String) -> Unit,
    onOpenStarterTemplates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val basePalette = if (activeTool == NotebookTool.HIGHLIGHTER) {
        NotebookQuickHighlighterColors + NotebookPastelPalette
    } else {
        NotebookQuickInkColors + NotebookPastelPalette
    }
    val swatches = (savedColors + recentColors + basePalette).distinct()
    val isDrawingTool = activeTool == NotebookTool.PENCIL ||
        activeTool == NotebookTool.PEN ||
        activeTool == NotebookTool.FINELINER ||
        activeTool == NotebookTool.HIGHLIGHTER

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface.copy(alpha = 0.95f))
            .border(1.dp, palette.border, RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("notebook_properties_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Live Stroke Preview Canvas when drawing or shaping
        if (isDrawingTool || activeTool == NotebookTool.SHAPE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.04f, size.height * 0.58f)
                        quadraticTo(size.width * 0.28f, size.height * 0.08f, size.width * 0.50f, size.height * 0.52f)
                        quadraticTo(size.width * 0.74f, size.height * 0.94f, size.width * 0.96f, size.height * 0.36f)
                    }
                    val previewAlpha = when (activeTool) {
                        NotebookTool.HIGHLIGHTER -> (alpha * 0.48f).coerceIn(0.12f, 0.85f)
                        NotebookTool.PENCIL -> (alpha * 0.78f).coerceIn(0.15f, 0.95f)
                        else -> alpha
                    }
                    val mult = when (activeTool) {
                        NotebookTool.HIGHLIGHTER -> 2.4f
                        NotebookTool.FINELINER -> 0.55f
                        NotebookTool.PENCIL -> 0.85f
                        else -> 1.15f
                    }
                    val strokePx = (strokeWidthNorm * 1400f * mult).coerceIn(2f, 22f)
                    drawPath(
                        path = path,
                        color = Color(activeColorArgb.toInt()).copy(alpha = previewAlpha),
                        style = Stroke(
                            width = strokePx,
                            cap = if (activeTool == NotebookTool.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // 2. Quick sub-modes for Pen/Fineliner, Text/Banner, Media/Sticker/Table
        if (activeTool == NotebookTool.PEN || activeTool == NotebookTool.FINELINER) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTogglePill("Bolígrafo", activeTool == NotebookTool.PEN, { onSelectTool(NotebookTool.PEN) }, "nb_sub_pen")
                MiniTogglePill("Punta fina", activeTool == NotebookTool.FINELINER, { onSelectTool(NotebookTool.FINELINER) }, "nb_sub_fineliner")
            }
        }

        if (activeTool == NotebookTool.TEXT_BOX || activeTool == NotebookTool.TITLE_BANNER) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniTogglePill("Caja de texto", activeTool == NotebookTool.TEXT_BOX, { onSelectTool(NotebookTool.TEXT_BOX) }, "nb_sub_textbox")
                MiniTogglePill("Banner 1 toque", false, onAddQuickTitleBanner, "nb_quick_title_banner_btn")
                MiniTogglePill("Banner libre", activeTool == NotebookTool.TITLE_BANNER, { onSelectTool(NotebookTool.TITLE_BANNER) }, "nb_sub_banner")
                MiniTogglePill("Plantillas", false, onOpenStarterTemplates, "nb_open_templates_from_text")
            }
        }

        if (activeTool == NotebookTool.IMAGE || activeTool == NotebookTool.STICKER || activeTool == NotebookTool.TABLE) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniTogglePill("Foto / PNG", false, onPickImage, "nb_insert_photo_png")
                MiniTogglePill("Pegar portap.", false, onPasteClipboardImage, "nb_paste_clipboard_img")
                MiniTogglePill("Stickers", activeTool == NotebookTool.STICKER, { onSelectTool(NotebookTool.STICKER) }, "nb_sub_sticker")
                MiniTogglePill("Tabla", activeTool == NotebookTool.TABLE, { onSelectTool(NotebookTool.TABLE) }, "nb_sub_table")
                MiniTogglePill("Plantillas", false, onOpenStarterTemplates, "nb_open_templates_from_media")
            }
        }

        // 3. Eraser Mode Selector (Por trazo / Por píxel)
        if (activeTool == NotebookTool.ERASER) {
            Text(
                text = "Modo de borrador",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.inkMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in NotebookEraserMode.entries) {
                    MiniTogglePill(
                        label = mode.label,
                        active = eraserMode == mode,
                        onClick = { onSelectEraserMode(mode) },
                        testTag = "nb_eraser_mode_${mode.name}"
                    )
                }
            }
        }

        // 4. Color Swatches + Eyedropper + Full Color Picker (Pastel / HSV / RGB / HEX)
        if (activeTool != NotebookTool.ERASER && activeTool != NotebookTool.PAN_ZOOM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Full Color Picker button (>= 48dp touch area)
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                        .clip(CircleShape)
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent, CircleShape)
                        .clickable(onClick = onOpenColorPicker)
                        .testTag("nb_open_color_creator"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Selector HSV/RGB/HEX", tint = palette.accent, modifier = Modifier.size(20.dp))
                }
                // Eyedropper button
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                        .clip(CircleShape)
                        .background(if (isEyedropperActive) palette.accent else palette.surfaceAlt)
                        .border(1.dp, if (isEyedropperActive) palette.accent else palette.border, CircleShape)
                        .clickable(onClick = onToggleEyedropper)
                        .testTag("nb_eyedropper_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Colorize,
                        contentDescription = "Cuentagotas",
                        tint = if (isEyedropperActive) palette.onAccent else palette.ink,
                        modifier = Modifier.size(19.dp)
                    )
                }
                for (colorLong in swatches) {
                    val selected = (colorLong and 0xFFFFFFL) == (activeColorArgb and 0xFFFFFFL)
                    Box(
                        modifier = Modifier
                            .size(if (selected) 34.dp else 30.dp)
                            .clip(CircleShape)
                            .background(Color(colorLong.toInt()))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) palette.ink else palette.border,
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(colorLong) }
                    )
                }
            }
        }

        // 5. Shapes & Connector picker
        if (activeTool == NotebookTool.SHAPE || activeTool == NotebookTool.CONNECTOR) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniTogglePill("Formas", activeTool == NotebookTool.SHAPE, { onSelectTool(NotebookTool.SHAPE) }, "nb_sub_shapes")
                    MiniTogglePill("Conector", activeTool == NotebookTool.CONNECTOR, { onSelectTool(NotebookTool.CONNECTOR) }, "nb_sub_connector")
                    MiniTogglePill(if (shapeFillEnabled) "Relleno: Sí" else "Relleno", shapeFillEnabled, onToggleShapeFill, "nb_toggle_shape_fill")
                    MiniTogglePill("Discontinuo", shapeDashedEnabled, onToggleShapeDashed, "nb_toggle_shape_dashed")
                    MiniTogglePill("Sombra", shapeShadowEnabled, onToggleShapeShadow, "nb_toggle_shape_shadow")
                }
                if (activeTool == NotebookTool.SHAPE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (kind in NotebookShapeKind.entries) {
                            val isSel = activeShapeKind == kind
                            MiniTogglePill(kind.label, isSel, { onSelectShapeKind(kind) }, "nb_shape_${kind.name}")
                        }
                    }
                } else {
                    Text(
                        text = "Toca el objeto origen y luego el destino para anclar",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
            }
        }

        // 6. Sticker Picker (16 built-in vector study stickers + "Mis stickers" user PNG library)
        if (activeTool == NotebookTool.STICKER) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (stKind in BuiltInStickerKind.entries) {
                        MiniTogglePill(stKind.label, activeStickerKind == stKind, { onSelectStickerKind(stKind) }, "nb_sticker_${stKind.name}")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniTogglePill("+ Mi PNG", false, onPickMyStickerPng, "nb_add_my_sticker_png")
                    if (myStickerPaths.isEmpty()) {
                        Text(
                            text = "Mis stickers PNG reutilizables",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted
                        )
                    } else {
                        myStickerPaths.forEachIndexed { idx, path ->
                            MiniTogglePill(
                                label = "Sticker ${idx + 1}",
                                active = false,
                                onClick = { onPlaceMySticker(path) },
                                testTag = "nb_my_sticker_$idx"
                            )
                        }
                    }
                }
            }
        }

        // 7. Grosor slider
        if (isDrawingTool || activeTool == NotebookTool.SHAPE || activeTool == NotebookTool.CONNECTOR || activeTool == NotebookTool.ERASER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (activeTool == NotebookTool.ERASER) "Radio" else "Grosor",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted,
                    modifier = Modifier.width(50.dp)
                )
                Slider(
                    value = strokeWidthNorm,
                    onValueChange = onStrokeWidthChange,
                    valueRange = 0.0016f..0.018f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
                Text(
                    text = "${(strokeWidthNorm * 1000f).roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    modifier = Modifier.width(28.dp)
                )
            }
        }

        // 8. Opacity slider
        if (isDrawingTool || activeTool == NotebookTool.SHAPE || activeTool == NotebookTool.STICKER || activeTool == NotebookTool.CONNECTOR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Opac.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted,
                    modifier = Modifier.width(50.dp)
                )
                Slider(
                    value = alpha,
                    onValueChange = onAlphaChange,
                    valueRange = 0.2f..1f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
                Text(
                    text = "${(alpha * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    modifier = Modifier.width(34.dp)
                )
            }
        }

        // 9. Stroke Stabilizer (Suavizado de trazo)
        if (isDrawingTool) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Estab.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted,
                    modifier = Modifier.width(50.dp)
                )
                Slider(
                    value = stabilizerLevel,
                    onValueChange = onStabilizerChange,
                    valueRange = 0f..0.85f,
                    modifier = Modifier.weight(1f).height(26.dp).testTag("nb_stabilizer_slider"),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
                Text(
                    text = "${((stabilizerLevel / 0.85f) * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniTogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Box(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) palette.accent else palette.surfaceAlt)
            .border(1.dp, if (active) palette.accent else palette.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) palette.onAccent else palette.ink,
            maxLines = 1
        )
    }
}

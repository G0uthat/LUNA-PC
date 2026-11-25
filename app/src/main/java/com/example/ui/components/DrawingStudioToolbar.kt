package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DrawingTool
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlin.math.roundToInt

private enum class StudioFlyout { NONE, COLORS, STROKE }

private val StudioRailShape = RoundedCornerShape(24.dp)
private val StudioFlyoutShape = RoundedCornerShape(22.dp)

/**
 * Translucent floating side studio dock tailored for tablets (inspired by digital drawing studios):
 * - Slim, low-obstruction vertical rail with glass translucency (never blocks writing space)
 * - 1-tap swap left/right edge for right- or left-handed stylists + 1-tap minimize pill
 * - Includes Arrow tool, Shape Fill toggle, Opacity + Thickness popover, 12+ Studio Swatches,
 *   saved custom colors, and Paint-style Custom Color Creator dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabletDrawingStudioDock(
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float,
    activeShapeFilled: Boolean,
    customColors: List<Long>,
    canUndo: Boolean,
    canRedo: Boolean,
    isStandardPageView: Boolean,
    standardFormatLabel: String,
    showStandardViewToggle: Boolean,
    canTogglePanZoom: Boolean,
    isPanZoomMode: Boolean,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onAddCustomColor: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onToggleShapeFilled: () -> Unit,
    onToggleStandardPageView: () -> Unit,
    onTogglePanZoomMode: () -> Unit,
    onApplySignaturePreset: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    var dockedOnRight by remember { mutableStateOf(true) }
    var isCollapsed by remember { mutableStateOf(false) }
    var activeFlyout by remember { mutableStateOf(StudioFlyout.NONE) }
    var showColorCreator by remember { mutableStateOf(false) }

    val glassSurface = palette.surface.copy(alpha = if (palette.isDark) 0.78f else 0.80f)
    val glassBorder = palette.border.copy(alpha = 0.72f)
    val flyoutSurface = palette.surface.copy(alpha = if (palette.isDark) 0.90f else 0.92f)
    val isFillableShape = activeTool == DrawingTool.RECTANGLE || activeTool == DrawingTool.CIRCLE

    Box(
        modifier = modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        if (isCollapsed) {
            // Minimal translucent floating capsule when minimized so 99% of canvas is free
            Row(
                modifier = Modifier
                    .align(if (dockedOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .shadow(8.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(50))
                    .background(glassSurface)
                    .border(1.dp, glassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("tablet_studio_collapsed_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(activeColorArgb.toInt()).copy(alpha = activeAlpha.coerceIn(0.25f, 1f)))
                        .border(1.5.dp, palette.ink, CircleShape)
                )
                StudioMiniIconButton(
                    icon = Icons.Rounded.OpenInFull,
                    contentDescription = "Expandir estudio",
                    selected = false,
                    onClick = { isCollapsed = false },
                    testTag = "tablet_studio_expand"
                )
                StudioMiniIconButton(
                    icon = Icons.Rounded.Check,
                    contentDescription = "Terminar dibujo",
                    selected = true,
                    onClick = onDone,
                    testTag = "draw_done"
                )
            }
        } else {
            Row(
                modifier = Modifier.align(if (dockedOnRight) Alignment.CenterEnd else Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (dockedOnRight) {
                    StudioSideFlyoutPanel(
                        flyout = activeFlyout,
                        flyoutSurface = flyoutSurface,
                        glassBorder = glassBorder,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        activeAlpha = activeAlpha,
                        activeShapeFilled = activeShapeFilled,
                        customColors = customColors,
                        isStandardPageView = isStandardPageView,
                        standardFormatLabel = standardFormatLabel,
                        showStandardViewToggle = showStandardViewToggle,
                        onSelectColor = { hex ->
                            if (activeTool == DrawingTool.ERASER) onSelectTool(DrawingTool.PEN)
                            onSelectColor(hex)
                        },
                        onOpenColorCreator = {
                            activeFlyout = StudioFlyout.NONE
                            showColorCreator = true
                        },
                        onWidthChange = onWidthChange,
                        onAlphaChange = onAlphaChange,
                        onToggleShapeFilled = onToggleShapeFilled,
                        onApplySignaturePreset = onApplySignaturePreset,
                        onToggleStandardPageView = onToggleStandardPageView
                    )
                }

                // Slim translucent vertical studio dock
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .heightIn(max = 560.dp)
                        .shadow(10.dp, StudioRailShape, ambientColor = Color.Black.copy(alpha = 0.22f))
                        .clip(StudioRailShape)
                        .background(glassSurface)
                        .border(1.dp, glassBorder, StudioRailShape)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag("drawing_toolbar"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Header controls: collapse & swap side
                    StudioMiniIconButton(
                        icon = Icons.Rounded.CloseFullscreen,
                        contentDescription = "Minimizar barra",
                        selected = false,
                        onClick = {
                            activeFlyout = StudioFlyout.NONE
                            isCollapsed = true
                        },
                        testTag = "tablet_studio_collapse"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.SwapHoriz,
                        contentDescription = "Cambiar lado",
                        selected = false,
                        onClick = { dockedOnRight = !dockedOnRight },
                        testTag = "tablet_studio_swap_side"
                    )

                    HorizontalDivider(color = glassBorder, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))

                    // Freehand + Eraser
                    StudioMiniIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = "Lápiz",
                        selected = !isPanZoomMode && activeTool == DrawingTool.PEN,
                        onClick = { onSelectTool(DrawingTool.PEN) },
                        testTag = "draw_tool_pen"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.BorderColor,
                        contentDescription = "Resaltador",
                        selected = !isPanZoomMode && activeTool == DrawingTool.HIGHLIGHTER,
                        onClick = { onSelectTool(DrawingTool.HIGHLIGHTER) },
                        testTag = "draw_tool_highlighter"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.AutoFixNormal,
                        contentDescription = "Borrador",
                        selected = !isPanZoomMode && activeTool == DrawingTool.ERASER,
                        onClick = { onSelectTool(DrawingTool.ERASER) },
                        testTag = "draw_tool_eraser"
                    )

                    HorizontalDivider(color = glassBorder, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))

                    // Geometric Paint-style tools: Line, Arrow, Rectangle, Circle, Fill toggle
                    StudioMiniIconButton(
                        icon = Icons.Rounded.HorizontalRule,
                        contentDescription = "Línea",
                        selected = !isPanZoomMode && activeTool == DrawingTool.LINE,
                        onClick = { onSelectTool(DrawingTool.LINE) },
                        testTag = "draw_tool_line"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.NorthEast,
                        contentDescription = "Flecha",
                        selected = !isPanZoomMode && activeTool == DrawingTool.ARROW,
                        onClick = { onSelectTool(DrawingTool.ARROW) },
                        testTag = "draw_tool_arrow"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.CropSquare,
                        contentDescription = "Cuadrado",
                        selected = !isPanZoomMode && activeTool == DrawingTool.RECTANGLE,
                        onClick = { onSelectTool(DrawingTool.RECTANGLE) },
                        testTag = "draw_tool_rectangle"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Círculo",
                        selected = !isPanZoomMode && activeTool == DrawingTool.CIRCLE,
                        onClick = { onSelectTool(DrawingTool.CIRCLE) },
                        testTag = "draw_tool_circle"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.FormatColorFill,
                        contentDescription = if (activeShapeFilled) "Relleno activo" else "Solo contorno",
                        selected = activeShapeFilled,
                        dimmed = !isFillableShape && !activeShapeFilled,
                        onClick = onToggleShapeFilled,
                        testTag = "draw_toggle_fill"
                    )

                    HorizontalDivider(color = glassBorder, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))

                    // Color swatch orb (opens studio palette + Paint color creator)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (activeFlyout == StudioFlyout.COLORS) palette.accentSoft else Color.Transparent)
                            .clickable {
                                activeFlyout = if (activeFlyout == StudioFlyout.COLORS) StudioFlyout.NONE else StudioFlyout.COLORS
                            }
                            .testTag("tablet_studio_color_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(activeColorArgb.toInt()).copy(alpha = activeAlpha.coerceIn(0.25f, 1f)))
                                .border(2.5.dp, palette.ink, CircleShape)
                        )
                    }

                    // Stroke thickness + opacity flyout button
                    StudioMiniIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Grosor y opacidad",
                        selected = activeFlyout == StudioFlyout.STROKE,
                        onClick = {
                            activeFlyout = if (activeFlyout == StudioFlyout.STROKE) StudioFlyout.NONE else StudioFlyout.STROKE
                        },
                        testTag = "tablet_studio_stroke_button"
                    )

                    if (canTogglePanZoom) {
                        StudioMiniIconButton(
                            icon = Icons.Rounded.OpenWith,
                            contentDescription = if (isPanZoomMode) "Zoom activo" else "Mover / Zoom",
                            selected = isPanZoomMode,
                            onClick = onTogglePanZoomMode,
                            testTag = "draw_toggle_pan_zoom"
                        )
                    }

                    HorizontalDivider(color = glassBorder, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))

                    // Undo / Redo / Clear / Done
                    StudioMiniIconButton(
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Deshacer",
                        selected = false,
                        dimmed = !canUndo,
                        onClick = { if (canUndo) onUndo() },
                        testTag = "draw_undo"
                    )
                    StudioMiniIconButton(
                        icon = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Rehacer",
                        selected = false,
                        dimmed = !canRedo,
                        onClick = { if (canRedo) onRedo() },
                        testTag = "draw_redo"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Limpiar página",
                        selected = false,
                        onClick = onClearPage,
                        testTag = "draw_clear"
                    )
                    StudioMiniIconButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = "Terminar dibujo",
                        selected = true,
                        onClick = onDone,
                        testTag = "draw_done"
                    )
                }

                if (!dockedOnRight) {
                    StudioSideFlyoutPanel(
                        flyout = activeFlyout,
                        flyoutSurface = flyoutSurface,
                        glassBorder = glassBorder,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        activeAlpha = activeAlpha,
                        activeShapeFilled = activeShapeFilled,
                        customColors = customColors,
                        isStandardPageView = isStandardPageView,
                        standardFormatLabel = standardFormatLabel,
                        showStandardViewToggle = showStandardViewToggle,
                        onSelectColor = { hex ->
                            if (activeTool == DrawingTool.ERASER) onSelectTool(DrawingTool.PEN)
                            onSelectColor(hex)
                        },
                        onOpenColorCreator = {
                            activeFlyout = StudioFlyout.NONE
                            showColorCreator = true
                        },
                        onWidthChange = onWidthChange,
                        onAlphaChange = onAlphaChange,
                        onToggleShapeFilled = onToggleShapeFilled,
                        onApplySignaturePreset = onApplySignaturePreset,
                        onToggleStandardPageView = onToggleStandardPageView
                    )
                }
            }
        }
    }

    if (showColorCreator) {
        PaintColorCreatorDialog(
            initialColorArgb = activeColorArgb,
            onConfirmColor = onAddCustomColor,
            onDismiss = { showColorCreator = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioSideFlyoutPanel(
    flyout: StudioFlyout,
    flyoutSurface: Color,
    glassBorder: Color,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float,
    activeShapeFilled: Boolean,
    customColors: List<Long>,
    isStandardPageView: Boolean,
    standardFormatLabel: String,
    showStandardViewToggle: Boolean,
    onSelectColor: (Long) -> Unit,
    onOpenColorCreator: () -> Unit,
    onWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onToggleShapeFilled: () -> Unit,
    onApplySignaturePreset: () -> Unit,
    onToggleStandardPageView: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    AnimatedVisibility(
        visible = flyout != StudioFlyout.NONE,
        enter = scaleIn(initialScale = 0.92f, animationSpec = motion.tactileSpring()) + fadeIn(motion.tweenSpec(120)),
        exit = scaleOut(targetScale = 0.94f, animationSpec = motion.exitTween(90)) + fadeOut(motion.exitTween(80))
    ) {
        Column(
            modifier = Modifier
                .width(236.dp)
                .shadow(12.dp, StudioFlyoutShape, ambientColor = Color.Black.copy(alpha = 0.24f))
                .clip(StudioFlyoutShape)
                .background(flyoutSurface)
                .border(1.dp, glassBorder, StudioFlyoutShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (flyout) {
                StudioFlyout.COLORS -> {
                    Text(
                        text = "Paleta de estudio",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (hex in TabletStudioPaletteColors) {
                            val selected = activeColorArgb == hex && activeTool != DrawingTool.ERASER
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex.toInt()))
                                    .border(
                                        width = if (selected) 3.dp else 1.5.dp,
                                        color = if (selected) palette.ink else palette.border,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectColor(hex) }
                            )
                        }
                    }

                    if (customColors.isNotEmpty()) {
                        Text(
                            text = "Tus colores creados",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkMuted
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (hex in customColors) {
                                val selected = activeColorArgb == hex && activeTool != DrawingTool.ERASER
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex.toInt()))
                                        .border(
                                            width = if (selected) 3.dp else 1.5.dp,
                                            color = if (selected) palette.ink else palette.border,
                                            shape = CircleShape
                                        )
                                        .clickable { onSelectColor(hex) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3B82F6).copy(alpha = 0.18f),
                                        Color(0xFFEC4899).copy(alpha = 0.18f),
                                        Color(0xFFF59E0B).copy(alpha = 0.18f)
                                    )
                                )
                            )
                            .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenColorCreator)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("open_paint_color_creator"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Crear color",
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Crear color personalizado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                    }
                }

                StudioFlyout.STROKE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ajustes del trazo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.ink
                        )
                        // Live stroke preview dot
                        Box(
                            modifier = Modifier
                                .size((8f + activeWidthNorm * 850f).coerceIn(8f, 26f).dp)
                                .clip(CircleShape)
                                .background(Color(activeColorArgb.toInt()).copy(alpha = activeAlpha.coerceIn(0.15f, 1f)))
                                .border(1.dp, palette.border, CircleShape)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Grosor", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, fontWeight = FontWeight.Bold)
                            Text("${(activeWidthNorm * 1000).roundToInt()} pt", style = MaterialTheme.typography.labelSmall, color = palette.ink, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = activeWidthNorm,
                            onValueChange = onWidthChange,
                            valueRange = 0.0035f..0.022f,
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opacidad", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, fontWeight = FontWeight.Bold)
                            Text("${(activeAlpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = palette.ink, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = activeAlpha,
                            onValueChange = onAlphaChange,
                            valueRange = 0.15f..1.0f,
                            modifier = Modifier.fillMaxWidth().height(28.dp).testTag("draw_opacity_slider"),
                            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ToolbarModeChip(
                            icon = Icons.Rounded.FormatColorFill,
                            label = if (activeShapeFilled) "Relleno: Sí" else "Relleno: No",
                            selected = activeShapeFilled,
                            onClick = onToggleShapeFilled,
                            testTag = "draw_flyout_fill_chip"
                        )
                        ToolbarModeChip(
                            icon = Icons.Rounded.Draw,
                            label = "Firma fina",
                            selected = false,
                            onClick = onApplySignaturePreset,
                            testTag = "draw_signature_preset"
                        )
                        if (showStandardViewToggle) {
                            ToolbarModeChip(
                                icon = Icons.Rounded.Description,
                                label = if (isStandardPageView) "Hoja · $standardFormatLabel" else "Hoja estándar",
                                selected = isStandardPageView,
                                onClick = onToggleStandardPageView,
                                testTag = "draw_toggle_standard_view"
                            )
                        }
                    }
                }

                StudioFlyout.NONE -> Unit
            }
        }
    }
}

@Composable
private fun StudioMiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> palette.accent
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                selected -> Color.White
                dimmed -> palette.inkMuted.copy(alpha = 0.38f)
                else -> palette.ink
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun PaintColorAddBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF5A67),
                        Color(0xFFF5B731),
                        Color(0xFF1EB974),
                        Color(0xFF1D5BFF),
                        Color(0xFF9333EA),
                        Color(0xFFFF5A67)
                    )
                )
            )
            .border(1.5.dp, palette.surface, CircleShape)
            .clickable(onClick = onClick)
            .testTag("open_paint_color_creator"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(palette.surface.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Crear color",
                tint = palette.ink,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

package com.example.notebook.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.notebook.data.NotebookPageEntity
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookLayerInfo
import com.example.notebook.model.NotebookPageSizePreset
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Material 3 WindowSizeClass width buckets (adapts purely by available width, never device sniffing).
 */
enum class NotebookWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED;

    val isWide: Boolean get() = this != COMPACT

    companion object {
        fun fromWidth(width: Dp): NotebookWindowWidthClass = when {
            width >= 840.dp -> EXPANDED
            width >= 600.dp -> MEDIUM
            else -> COMPACT
        }
    }
}

val NotebookPastelPalette = listOf(
    0xFFFDE68AL, // Amarillo pastel
    0xFFFECACAL, // Rosa suave
    0xFFFBCFE8L, // Rosa chicle
    0xFFDDD6FEL, // Lavanda
    0xFFBFDBFEL, // Azul cielo
    0xFFA5F3FCL, // Turquesa claro
    0xFFBBF7D0L, // Menta
    0xFFD9F99DL, // Lima suave
    0xFFFED7AAL, // Durazno
    0xFFE2E8F0L, // Gris papel
    0xFF94A3B8L, // Pizarra
    0xFF1E293BL  // Grafito
)

/**
 * Studio Color Picker Dialog:
 * - Paleta pastel rápida
 * - Creador visual HSV (cuadro SV + barra de tono) + controles RGB + campo HEX editable
 * - Recientes (máx 12, DataStore) y Guardados (máx 12, DataStore)
 * - Botón Cuentagotas para tomar color directamente del lienzo
 */
@Composable
fun NotebookColorPickerDialog(
    initialColorArgb: Long,
    recentColors: List<Long>,
    savedColors: List<Long>,
    onSelectColor: (Long) -> Unit,
    onSaveColor: (Long) -> Unit,
    onRemoveSavedColor: (Long) -> Unit,
    onStartEyedropper: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColorArgb.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember {
        mutableStateOf(String.format("#%06X", initialColorArgb.toInt() and 0xFFFFFF))
    }

    val currentColorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    val currentColorLong = currentColorInt.toLong() and 0xFFFFFFFFL
    val rVal = android.graphics.Color.red(currentColorInt)
    val gVal = android.graphics.Color.green(currentColorInt)
    val bVal = android.graphics.Color.blue(currentColorInt)

    fun syncFromArgbInt(argb: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        hexInput = String.format("#%06X", argb and 0xFFFFFF)
    }

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        syncFromArgbInt(android.graphics.Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
                .testTag("nb_color_picker_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(currentColorInt))
                            .border(2.dp, palette.border, CircleShape)
                    )
                    Column {
                        Text(
                            text = "Selector de color",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.ink
                        )
                        Text(
                            text = "Pastel · HSV · RGB · HEX",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .background(palette.accentSoft)
                            .clickable {
                                onStartEyedropper()
                                onDismiss()
                            }
                            .testTag("nb_color_picker_eyedropper"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Colorize, contentDescription = "Cuentagotas", tint = palette.accent)
                    }
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = palette.inkMuted)
                    }
                }
            }

            // 1. Paleta pastel rápida
            Text("Paleta pastel rápida", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.inkMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (pastel in NotebookPastelPalette) {
                    val isSel = (pastel and 0xFFFFFFL) == (currentColorLong and 0xFFFFFFL)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(pastel.toInt()))
                            .border(if (isSel) 2.5.dp else 1.dp, if (isSel) palette.ink else palette.border, CircleShape)
                            .clickable { syncFromArgbInt(pastel.toInt()) }
                    )
                }
            }

            // 2. 2D Saturation/Value square + Hue slider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            sat = (offset.x / size.width).coerceIn(0f, 1f)
                            value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            hexInput = String.format("#%06X", android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            sat = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            hexInput = String.format("#%06X", android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pureHue)))
                    drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val cx = sat * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black, radius = 11.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
                }
            }

            // Rainbow Hue bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            hue = ((off.x / size.width) * 360f).coerceIn(0f, 360f)
                            hexInput = String.format("#%06X", android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                            hexInput = String.format("#%06X", android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val spectrum = listOf(
                        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                    )
                    drawRect(brush = Brush.horizontalGradient(spectrum))
                    val hx = (hue / 360f) * size.width
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(hx, size.height * 0.5f), style = Stroke(3.dp.toPx()))
                }
            }

            // 3. HEX input + Save to favorites button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase().take(7)
                        hexInput = cleaned
                        val hexDigits = cleaned.removePrefix("#")
                        if (hexDigits.length == 6) {
                            val parsed = hexDigits.toIntOrNull(16)
                            if (parsed != null) {
                                val argb = (0xFF000000.toInt()) or parsed
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        }
                    },
                    label = { Text("HEX") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("nb_color_hex_input")
                )
                OutlinedButton(
                    onClick = { onSaveColor(currentColorLong) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nb_color_save_btn")
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Guardar")
                }
            }

            // 4. Compact RGB sliders
            RgbSliderRow("R", rVal, Color(0xFFEF4444)) { updateFromRgb(it, gVal, bVal) }
            RgbSliderRow("G", gVal, Color(0xFF22C55E)) { updateFromRgb(rVal, it, bVal) }
            RgbSliderRow("B", bVal, Color(0xFF3B82F6)) { updateFromRgb(rVal, gVal, it) }

            // 5. Guardados (max 12)
            if (savedColors.isNotEmpty()) {
                Text(
                    text = "Guardados (${savedColors.size}/12) · toca × para quitar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (sc in savedColors) {
                        Box(modifier = Modifier.size(40.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(CircleShape)
                                    .background(Color(sc.toInt()))
                                    .border(1.5.dp, palette.border, CircleShape)
                                    .clickable { syncFromArgbInt(sc.toInt()) }
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(palette.surface)
                                    .border(1.dp, palette.border, CircleShape)
                                    .clickable { onRemoveSavedColor(sc) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Quitar guardado", tint = palette.ink, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
            }

            // 6. Recientes (max 12)
            if (recentColors.isNotEmpty()) {
                Text(
                    text = "Recientes",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (rc in recentColors) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(rc.toInt()))
                                .border(1.dp, palette.border, CircleShape)
                                .clickable { syncFromArgbInt(rc.toInt()) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancelar", color = palette.inkMuted)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSelectColor(currentColorLong)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nb_color_apply_btn")
                ) {
                    Text("Usar color", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RgbSliderRow(label: String, value: Int, trackColor: Color, onChange: (Int) -> Unit) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, color = trackColor, modifier = Modifier.width(16.dp), fontSize = 12.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).height(26.dp),
            colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor)
        )
        Text("$value", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, modifier = Modifier.width(28.dp))
    }
}

/**
 * Full Layers Manager Dialog:
 * Add, select active layer, show/hide, lock/unlock, reorder (up/down), delete custom layers, per-layer opacity.
 */
@Composable
fun NotebookLayersDialog(
    layers: List<NotebookLayerInfo>,
    activeLayerId: String,
    objectCountsByLayer: Map<String, Int>,
    onSelectActiveLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onToggleVisible: (String) -> Unit,
    onToggleLocked: (String) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onMoveLayer: (fromIndex: Int, toIndex: Int) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    // Display top-most layer first (standard drawing app convention), map indices back to bottom-to-top list
    val topToBottom = layers.reversed()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .padding(18.dp)
                .testTag("nb_layers_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, tint = palette.accent)
                    Column {
                        Text("Capas de la hoja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.ink)
                        Text("Orden de arriba hacia abajo · opacidad independiente", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                    }
                }
                Button(
                    onClick = onAddLayer,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nb_add_layer_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Capa")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topToBottom.forEachIndexed { displayIdx, layer ->
                    val realIndex = layers.lastIndex - displayIdx
                    val isActive = layer.id == activeLayerId
                    val isBuiltIn = layer.id in NotebookLayerIds.ORDERED
                    val count = objectCountsByLayer[layer.id] ?: 0

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isActive) palette.accentSoft else palette.surfaceAlt)
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) palette.accent else palette.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectActiveLayer(layer.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("nb_layer_row_${layer.id}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = layer.name,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$count elementos" + if (isActive) " · Activa" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) palette.accent else palette.inkMuted
                                )
                            }

                            // Reorder up (higher render priority = realIndex + 1)
                            LayerIconBtn(
                                icon = Icons.Rounded.ArrowUpward,
                                desc = "Subir capa",
                                enabled = realIndex < layers.lastIndex,
                                onClick = { onMoveLayer(realIndex, realIndex + 1) }
                            )
                            // Reorder down (lower render priority = realIndex - 1)
                            LayerIconBtn(
                                icon = Icons.Rounded.ArrowDownward,
                                desc = "Bajar capa",
                                enabled = realIndex > 0,
                                onClick = { onMoveLayer(realIndex, realIndex - 1) }
                            )
                            LayerIconBtn(
                                icon = if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                desc = if (layer.visible) "Ocultar capa" else "Mostrar capa",
                                tint = if (layer.visible) palette.ink else palette.inkMuted.copy(alpha = 0.45f),
                                onClick = { onToggleVisible(layer.id) }
                            )
                            LayerIconBtn(
                                icon = if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                desc = if (layer.locked) "Desbloquear capa" else "Bloquear capa",
                                tint = if (layer.locked) Color(0xFFD97706) else palette.inkMuted,
                                onClick = { onToggleLocked(layer.id) }
                            )
                            if (!isBuiltIn && layers.size > 1) {
                                LayerIconBtn(
                                    icon = Icons.Rounded.DeleteOutline,
                                    desc = "Eliminar capa",
                                    tint = Color(0xFFE11D48),
                                    onClick = { onDeleteLayer(layer.id) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Opacidad", style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                            Slider(
                                value = layer.opacity,
                                onValueChange = { onOpacityChange(layer.id, it) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.weight(1f).height(24.dp),
                                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                            )
                            Text(
                                text = "${(layer.opacity * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink,
                                modifier = Modifier.width(34.dp)
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Listo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LayerIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null
) {
    val palette = LocalFolioPalette.current
    val resolved = (tint ?: palette.ink).copy(alpha = if (enabled) 1f else 0.28f)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 42.dp, minHeight = 42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = resolved, modifier = Modifier.size(19.dp))
    }
}

/**
 * Collapsible Page Thumbnail Strip:
 * Quick page switching, add, duplicate, reorder, delete, plus cross-page Copy/Paste objects!
 */
@Composable
fun NotebookPageStrip(
    pages: List<NotebookPageEntity>,
    currentPageIndex: Int,
    pageAspectRatio: Float,
    hasSelectedObjects: Boolean,
    copiedObjectCount: Int,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeletePage: (Long) -> Unit,
    onCopySelected: () -> Unit,
    onPasteCopied: () -> Unit,
    onCloseStrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val curPage = pages.getOrNull(currentPageIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface.copy(alpha = 0.94f))
            .border(1.dp, palette.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("nb_page_thumbnail_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StripActionChip(Icons.Rounded.Add, "Hoja", accent = true, onClick = onAddPage, testTag = "nb_strip_add_page")
            StripActionChip(Icons.Rounded.ContentCopy, "Duplicar", onClick = onDuplicatePage, testTag = "nb_strip_dup_page")
            if (currentPageIndex > 0) {
                StripActionChip(Icons.AutoMirrored.Rounded.ArrowBack, "Mover", onClick = { onMovePage(currentPageIndex, currentPageIndex - 1) })
            }
            if (currentPageIndex < pages.lastIndex) {
                StripActionChip(Icons.AutoMirrored.Rounded.ArrowForward, "Mover", onClick = { onMovePage(currentPageIndex, currentPageIndex + 1) })
            }
            if (pages.size > 1 && curPage != null) {
                StripActionChip(Icons.Rounded.DeleteOutline, "Borrar", danger = true, onClick = { onDeletePage(curPage.id) })
            }
            if (hasSelectedObjects) {
                StripActionChip(Icons.Rounded.ContentCopy, "Copiar obj.", accent = true, onClick = onCopySelected, testTag = "nb_strip_copy_obj")
            }
            if (copiedObjectCount > 0) {
                StripActionChip(Icons.Rounded.ContentPaste, "Pegar ($copiedObjectCount)", accent = true, onClick = onPasteCopied, testTag = "nb_strip_paste_obj")
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCloseStrip),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Ocultar tira de páginas", tint = palette.inkMuted, modifier = Modifier.size(18.dp))
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                val isCurrent = index == currentPageIndex
                PageStripThumbnailCard(
                    page = page,
                    pageNumber = index + 1,
                    aspectRatio = pageAspectRatio,
                    isCurrent = isCurrent,
                    onClick = { onSelectPage(index) }
                )
            }
        }
    }
}

@Composable
private fun StripActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    danger: Boolean = false,
    testTag: String? = null
) {
    val palette = LocalFolioPalette.current
    val bg = when {
        accent -> palette.accent
        danger -> Color(0x1AE11D48)
        else -> palette.surfaceAlt
    }
    val fg = when {
        accent -> palette.onAccent
        danger -> Color(0xFFE11D48)
        else -> palette.ink
    }
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (accent) palette.accent else palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

@Composable
private fun PageStripThumbnailCard(
    page: NotebookPageEntity,
    pageNumber: Int,
    aspectRatio: Float,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var thumbBitmap by remember(page.thumbnailPath, page.contentJson.length) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.thumbnailPath, page.contentJson.length) {
        thumbBitmap = withContext(Dispatchers.IO) {
            val path = page.thumbnailPath
            if (path.isBlank()) null else {
                val f = File(path)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            }
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) palette.accentSoft else palette.surfaceAlt)
            .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) palette.accent else palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .height(64.dp)
                .aspectRatio(aspectRatio.coerceIn(0.6f, 1.8f))
                .clip(RoundedCornerShape(7.dp))
                .background(Color(page.backgroundColor.toInt()))
                .border(1.dp, palette.border, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Miniatura página $pageNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "$pageNumber",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (page.backgroundColor == 0xFF141821L) Color.White else palette.inkMuted
                )
            }
        }
        Text(
            text = "Pág. $pageNumber",
            fontSize = 10.sp,
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isCurrent) palette.accent else palette.inkMuted
        )
    }
}

/**
 * Editable Notebook Title Dialog triggered by tapping the notebook title in the top bar.
 */
@Composable
fun NotebookTitleEditDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var text by remember { mutableStateOf(initialTitle) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(22.dp))
                .padding(18.dp)
                .testTag("nb_title_edit_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Título del cuaderno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.ink)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("nb_title_input")
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancelar", color = palette.inkMuted)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val cleaned = text.trim()
                        if (cleaned.isNotEmpty()) onConfirm(cleaned)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nb_title_save_btn")
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Top Bar Overflow Menu (⋯):
 * Stylus-only mode, Aspect-ratio lock, Page strip visibility, Background & page size, Paste, Clear page.
 */
@Composable
fun NotebookStudioOverflowMenuDialog(
    stylusOnlyMode: Boolean,
    lockAspectRatio: Boolean,
    showPageStrip: Boolean,
    copiedObjectCount: Int,
    onToggleStylusOnly: (Boolean) -> Unit,
    onToggleLockAspect: (Boolean) -> Unit,
    onTogglePageStrip: (Boolean) -> Unit,
    onOpenBackgroundAndSize: () -> Unit,
    onOpenStarterTemplates: () -> Unit = {},
    onPasteCopied: () -> Unit,
    onClearCurrentPage: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .padding(18.dp)
                .testTag("nb_overflow_menu_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Opciones del estudio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.ink)

            MenuSwitchRow(
                icon = Icons.Rounded.Draw,
                title = "Solo lápiz (Stylus)",
                subtitle = "El lápiz dibuja y el dedo desplaza la hoja",
                checked = stylusOnlyMode,
                onCheckedChange = onToggleStylusOnly,
                testTag = "nb_toggle_stylus_only"
            )

            MenuSwitchRow(
                icon = Icons.Rounded.Lock,
                title = "Bloquear proporción",
                subtitle = "Mantiene aspecto al redimensionar objetos",
                checked = lockAspectRatio,
                onCheckedChange = onToggleLockAspect,
                testTag = "nb_toggle_lock_aspect"
            )

            MenuSwitchRow(
                icon = Icons.Rounded.ViewCarousel,
                title = "Tira de miniaturas",
                subtitle = "Acceso rápido a páginas y copiar/pegar",
                checked = showPageStrip,
                onCheckedChange = onTogglePageStrip,
                testTag = "nb_toggle_page_strip"
            )

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenBackgroundAndSize()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nb_menu_bg_size_btn")
            ) {
                Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Fondo, papel y tamaño de hoja")
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenStarterTemplates()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nb_menu_starter_templates_btn")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Plantillas de arranque (Cornell, Mapa…)")
            }

            if (copiedObjectCount > 0) {
                OutlinedButton(
                    onClick = {
                        onPasteCopied()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pegar objetos copiados ($copiedObjectCount)")
                }
            }

            OutlinedButton(
                onClick = {
                    onClearCurrentPage()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limpiar hoja actual", color = Color(0xFFE11D48))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable
private fun MenuSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceAlt)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = palette.ink, fontSize = 13.5.sp)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
        )
    }
}

/**
 * Combined Background (template, solid color, dark, image, PDF pages) & Page Size (A4, Carta, 16:9, Cuadrado, Personalizado) Dialog.
 */
@Composable
fun NotebookBackgroundAndSizeDialog(
    activeTemplate: NotebookPageTemplate,
    currentPaperColorArgb: Long,
    currentWidthPx: Int,
    currentHeightPx: Int,
    onChangeTemplate: (NotebookPageTemplate) -> Unit,
    onChangePaperColor: (Long) -> Unit,
    onSetBackgroundImage: () -> Unit,
    onAddPdfPages: () -> Unit,
    onChangePageSize: (widthPx: Int, heightPx: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val initialPreset = remember(currentWidthPx, currentHeightPx) {
        NotebookPageSizePreset.entries.firstOrNull {
            it != NotebookPageSizePreset.CUSTOM && it.widthPx == currentWidthPx && it.heightPx == currentHeightPx
        } ?: NotebookPageSizePreset.CUSTOM
    }
    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var customW by remember { mutableStateOf(currentWidthPx.toString()) }
    var customH by remember { mutableStateOf(currentHeightPx.toString()) }

    val paperSwatches = listOf(
        "Blanco" to 0xFFFFFFFFL,
        "Crema" to 0xFFFFFBF0L,
        "Menta" to 0xFFF2FBF5L,
        "Azul" to 0xFFF2F7FFL,
        "Oscuro" to 0xFF141821L
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .heightIn(max = 650.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
                .testTag("nb_bg_size_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Fondo y tamaño de hoja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.ink)

            Text("Plantilla de papel", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.inkMuted)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (tpl in NotebookPageTemplate.entries) {
                    val isSel = tpl == activeTemplate
                    Box(
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) palette.accent else palette.surfaceAlt)
                            .border(1.dp, if (isSel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                            .clickable { onChangeTemplate(tpl) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tpl.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) palette.onAccent else palette.ink)
                    }
                }
            }

            Text("Color de fondo (sólido / oscuro)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.inkMuted)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((label, colorLong) in paperSwatches) {
                    val isSel = colorLong == currentPaperColorArgb
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onChangePaperColor(colorLong) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong.toInt()))
                                .border(if (isSel) 2.5.dp else 1.dp, if (isSel) palette.accent else palette.border, CircleShape)
                        )
                        Text(label, fontSize = 10.sp, color = palette.inkMuted)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSetBackgroundImage()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Imagen fondo", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddPdfPages()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Hojas PDF", maxLines = 1)
                }
            }

            Text("Tamaño de página", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.inkMuted)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (preset in NotebookPageSizePreset.entries) {
                    val isSel = preset == selectedPreset
                    Box(
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) palette.accent else palette.surfaceAlt)
                            .border(1.dp, if (isSel) palette.accent else palette.border, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPreset = preset
                                if (preset != NotebookPageSizePreset.CUSTOM) {
                                    customW = preset.widthPx.toString()
                                    customH = preset.heightPx.toString()
                                    onChangePageSize(preset.widthPx, preset.heightPx)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(preset.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) palette.onAccent else palette.ink)
                    }
                }
            }

            if (selectedPreset == NotebookPageSizePreset.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customW,
                        onValueChange = { customW = it.filter(Char::isDigit).take(4) },
                        label = { Text("Ancho px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customH,
                        onValueChange = { customH = it.filter(Char::isDigit).take(4) },
                        label = { Text("Alto px") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val w = customW.toIntOrNull()?.coerceIn(600, 3200) ?: 1480
                            val h = customH.toIntOrNull()?.coerceIn(600, 3200) ?: 2092
                            onChangePageSize(w, h)
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Aplicar")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Listo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFolioPalette
import java.util.Locale
import kotlin.math.roundToInt

val TabletStudioPaletteColors = listOf(
    0xFF1D5BFFL, // Cobalt
    0xFF151C2BL, // Graphite Ink
    0xFFE11D48L, // Crimson
    0xFFFF5A67L, // Coral Red
    0xFFF97316L, // Studio Orange
    0xFFF5B731L, // Warm Amber
    0xFF1EB974L, // Emerald
    0xFF0D9488L, // Teal
    0xFF06B6D4L, // Cyan
    0xFF7C3AEDL, // Study Violet
    0xFFD946EFL, // Fuchsia
    0xFFFFFFFFL  // Paper White
)

private val SpectrumHueStops = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000)
)

/**
 * Paint-style Custom Color Creator dialog:
 * - Interactive 2D Saturation-Value gradient canvas
 * - Rainbow Hue spectrum bar
 * - Fine R / G / B sliders + live HEX readout
 * - Quick studio swatches + instant "Usar y guardar color" confirmation
 */
@Composable
fun PaintColorCreatorDialog(
    initialColorArgb: Long,
    onConfirmColor: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val initialHsv = remember(initialColorArgb) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColorArgb.toInt(), hsv)
        Triple(hsv[0], hsv[1], hsv[2])
    }
    var hue by remember(initialColorArgb) { mutableFloatStateOf(initialHsv.first) }
    var sat by remember(initialColorArgb) { mutableFloatStateOf(initialHsv.second) }
    var value by remember(initialColorArgb) { mutableFloatStateOf(initialHsv.third) }

    val selectedArgbInt = AndroidColor.HSVToColor(255, floatArrayOf(hue, sat, value))
    val selectedColorLong = (selectedArgbInt.toLong() and 0xFFFFFFFFL)
    val selectedColor = Color(selectedArgbInt)
    val red = AndroidColor.red(selectedArgbInt)
    val green = AndroidColor.green(selectedArgbInt)
    val blue = AndroidColor.blue(selectedArgbInt)
    val hexText = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 420.dp,
        maxHeightFraction = 0.88f,
        testTag = "paint_color_creator_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Creador de color",
            subtitle = "Mezcla tu propio tono estilo estudio",
            leadingIcon = Icons.Rounded.Palette,
            onDismiss = dismissWithAnim
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live Preview + HEX badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(selectedColor)
                        .border(1.5.dp, palette.border, RoundedCornerShape(12.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hexText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = "RGB ($red, $green, $blue) · Tono ${hue.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
                TactileButton(
                    onClick = {
                        onConfirmColor(selectedColorLong)
                        dismissWithAnim()
                    },
                    cornerRadius = 14.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier.testTag("paint_color_confirm")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Usar", fontWeight = FontWeight.Bold)
                }
            }

            // 2. Interactive 2D Saturation × Brightness Canvas
            val huePureColor = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, 1f, 1f)))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            sat = (down.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            value = (1f - down.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()
                                sat = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                value = (1f - change.position.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            }
                        }
                    }
                    .testTag("paint_sv_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(brush = Brush.horizontalGradient(listOf(Color.White, huePureColor)))
                    drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val cx = sat * size.width
                    val cy = (1f - value) * size.height
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.65f),
                        radius = 11.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            // 3. Interactive Rainbow Hue Spectrum Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, palette.border, RoundedCornerShape(50))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            hue = ((down.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) * 359.9f)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()
                                hue = ((change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) * 359.9f)
                            }
                        }
                    }
                    .testTag("paint_hue_bar")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(brush = Brush.horizontalGradient(SpectrumHueStops))
                    val x = (hue / 360f).coerceIn(0f, 1f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = 10.dp.toPx(),
                        center = Offset(x, size.height / 2f)
                    )
                    drawCircle(
                        color = huePureColor,
                        radius = 7.dp.toPx(),
                        center = Offset(x, size.height / 2f)
                    )
                }
            }

            // 4. Fine R / G / B Sliders (Paint-style precision)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RgbSliderRow(
                    label = "R",
                    value = red,
                    trackColor = Color(0xFFE11D48),
                    onValueChange = { updateFromRgb(it, green, blue) }
                )
                RgbSliderRow(
                    label = "G",
                    value = green,
                    trackColor = Color(0xFF16A34A),
                    onValueChange = { updateFromRgb(red, it, blue) }
                )
                RgbSliderRow(
                    label = "B",
                    value = blue,
                    trackColor = Color(0xFF2563EB),
                    onValueChange = { updateFromRgb(red, green, it) }
                )
            }

            // 5. Quick Studio Base Swatches (2 neat rows of 6)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Colores base de estudio",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkMuted
                )
                TabletStudioPaletteColors.chunked(6).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (swatch in rowColors) {
                            val isMatch = swatch == selectedColorLong
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch.toInt()))
                                    .border(
                                        width = if (isMatch) 3.dp else 1.5.dp,
                                        color = if (isMatch) palette.ink else palette.border,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        AndroidColor.colorToHSV(swatch.toInt(), hsv)
                                        hue = hsv[0]
                                        sat = hsv[1]
                                        value = hsv[2]
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbSliderRow(
    label: String,
    value: Int,
    trackColor: Color,
    onValueChange: (Int) -> Unit
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = trackColor,
            modifier = Modifier.width(16.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor
            )
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkMuted,
            modifier = Modifier.width(28.dp)
        )
    }
}

package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.paletteFor
import kotlin.math.roundToInt

private data class EyeComfortPreset(
    val id: String,
    val label: String,
    val subtitle: String,
    val theme: ReadingTheme,
    val warmFilter: Float,
    val nightDimming: Float,
    val isNight: Boolean
)

private val EyeComfortPresets = listOf(
    EyeComfortPreset(
        id = "warm_day",
        label = "Crema cálida",
        subtitle = "Día suave",
        theme = ReadingTheme.WARM_CREAM,
        warmFilter = 0.08f,
        nightDimming = 0f,
        isNight = false
    ),
    EyeComfortPreset(
        id = "amber_shield",
        label = "Ámbar suave",
        subtitle = "Anti-fatiga",
        theme = ReadingTheme.AMBER_SOFT,
        warmFilter = 0.22f,
        nightDimming = 0f,
        isNight = false
    ),
    EyeComfortPreset(
        id = "warm_night",
        label = "Noche cálida",
        subtitle = "Sin reflejo",
        theme = ReadingTheme.WARM_NIGHT,
        warmFilter = 0.16f,
        nightDimming = 0.12f,
        isNight = true
    ),
    EyeComfortPreset(
        id = "deep_night",
        label = "Oscuro total",
        subtitle = "Habitación oscura",
        theme = ReadingTheme.AMOLED,
        warmFilter = 0.08f,
        nightDimming = 0.22f,
        isNight = true
    )
)

/**
 * Reusable Eye-Comfort & Configurable Night Reading Mode panel used in both
 * Reader Appearance settings and Library App Settings.
 */
@Composable
fun SoftThemeAndNightModePanel(
    prefs: ReaderPreferences,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    compactMode: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val dayThemes = remember { ReadingTheme.entries.filter { !it.isDark } }
    val nightThemes = remember { ReadingTheme.entries.filter { it.isDark } }
    var paletteTab by remember(palette.isDark) { mutableIntStateOf(if (palette.isDark) 1 else 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surfaceAlt.copy(alpha = 0.58f))
            .border(1.dp, palette.border, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row with active theme badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Column {
                    Text(
                        text = "Temas suaves y confort visual",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = "Paletas cálidas y modo nocturno anti-fatiga",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.accentSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = palette.theme.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }
        }

        // 1-tap Eye-Comfort Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (preset in EyeComfortPresets) {
                val presetPalette = paletteFor(preset.theme)
                val isSelected = palette.theme == preset.theme &&
                    kotlin.math.abs(prefs.warmFilterStrength - preset.warmFilter) < 0.06f
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) palette.accentSoft else palette.surface)
                        .border(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) palette.accent else palette.border,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            paletteTab = if (preset.isNight) 1 else 0
                            onUpdatePrefs {
                                it.copy(
                                    theme = preset.theme,
                                    nightModeEnabled = preset.isNight,
                                    nightAutoSchedule = false,
                                    preferredDayTheme = if (!preset.isNight) preset.theme else it.preferredDayTheme,
                                    preferredNightTheme = if (preset.isNight) preset.theme else it.preferredNightTheme,
                                    warmFilterStrength = preset.warmFilter,
                                    nightTextDimming = preset.nightDimming
                                )
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("comfort_preset_${preset.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(presetPalette.background)
                            .border(1.5.dp, presetPalette.accent, CircleShape)
                    )
                    Column {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) palette.accent else palette.ink,
                            maxLines = 1
                        )
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = palette.inkMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Day (Warm & Soft) vs Night Palette Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val daySelected = paletteTab == 0
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (daySelected) palette.accent else Color.Transparent)
                    .clickable { paletteTab = 0 }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LightMode,
                    contentDescription = null,
                    tint = if (daySelected) palette.onAccent else palette.inkMuted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Cálidos (${dayThemes.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (daySelected) palette.onAccent else palette.inkMuted
                )
            }
            val nightSelected = paletteTab == 1
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (nightSelected) palette.accent else Color.Transparent)
                    .clickable { paletteTab = 1 }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.DarkMode,
                    contentDescription = null,
                    tint = if (nightSelected) palette.onAccent else palette.inkMuted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Nocturnos (${nightThemes.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (nightSelected) palette.onAccent else palette.inkMuted
                )
            }
        }

        // Theme Swatches (Horizontal scroll in compact mode, wrapped grid in full settings)
        val shownThemes = if (paletteTab == 0) dayThemes else nightThemes
        if (compactMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (theme in shownThemes) {
                    ThemeSwatchTile(
                        theme = theme,
                        selected = palette.theme == theme,
                        onSelect = {
                            onUpdatePrefs {
                                it.copy(
                                    theme = theme,
                                    nightModeEnabled = theme.isDark,
                                    nightAutoSchedule = false,
                                    preferredDayTheme = if (!theme.isDark) theme else it.preferredDayTheme,
                                    preferredNightTheme = if (theme.isDark) theme else it.preferredNightTheme
                                )
                            }
                        },
                        modifier = Modifier.width(86.dp)
                    )
                }
            }
        } else {
            val rows = shownThemes.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rowThemes in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (theme in rowThemes) {
                            ThemeSwatchTile(
                                theme = theme,
                                selected = palette.theme == theme,
                                onSelect = {
                                    onUpdatePrefs {
                                        it.copy(
                                            theme = theme,
                                            nightModeEnabled = theme.isDark,
                                            nightAutoSchedule = false,
                                            preferredDayTheme = if (!theme.isDark) theme else it.preferredDayTheme,
                                            preferredNightTheme = if (theme.isDark) theme else it.preferredNightTheme
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowThemes.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.65f))

        // Quick Night Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Modo lectura nocturna",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink
                )
                Text(
                    text = "Alterna rápido entre ${prefs.preferredDayTheme.displayName} y ${prefs.preferredNightTheme.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = palette.isDark,
                onCheckedChange = { enableNight ->
                    paletteTab = if (enableNight) 1 else 0
                    onUpdatePrefs {
                        val targetTheme = if (enableNight) {
                            it.preferredNightTheme.takeIf { t -> t.isDark } ?: ReadingTheme.WARM_NIGHT
                        } else {
                            it.preferredDayTheme.takeIf { t -> !t.isDark } ?: ReadingTheme.WARM_CREAM
                        }
                        it.copy(
                            theme = targetTheme,
                            nightModeEnabled = enableNight,
                            nightAutoSchedule = false
                        )
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                modifier = Modifier.testTag("night_mode_quick_switch")
            )
        }

        // Configurable Auto Night Schedule
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Horario nocturno automático",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Activa ${prefs.preferredNightTheme.displayName} de ${formatHour(prefs.nightStartHour)} a ${formatHour(prefs.nightEndHour)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
                Switch(
                    checked = prefs.nightAutoSchedule,
                    onCheckedChange = { enabled ->
                        onUpdatePrefs { it.copy(nightAutoSchedule = enabled) }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                    modifier = Modifier.testTag("night_auto_schedule_switch")
                )
            }

            if (prefs.nightAutoSchedule) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HourStepperPill(
                        label = "Inicio noche",
                        hour = prefs.nightStartHour,
                        onStep = { delta ->
                            onUpdatePrefs { it.copy(nightStartHour = (it.nightStartHour + delta + 24) % 24) }
                        },
                        modifier = Modifier.weight(1f),
                        testTagPrefix = "night_start_hour"
                    )
                    HourStepperPill(
                        label = "Fin noche",
                        hour = prefs.nightEndHour,
                        onStep = { delta ->
                            onUpdatePrefs { it.copy(nightEndHour = (it.nightEndHour + delta + 24) % 24) }
                        },
                        modifier = Modifier.weight(1f),
                        testTagPrefix = "night_end_hour"
                    )
                }
            }
        }

        // Warm Blue-Light Filter Slider (0%..100% -> 0f..0.45f)
        val warmPct = ((prefs.warmFilterStrength / 0.45f) * 100f).roundToInt().coerceIn(0, 100)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFE59A4B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Filtro de luz cálida",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink
                    )
                }
                Text(
                    text = if (warmPct == 0) "Apagado" else "$warmPct%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }
            Slider(
                value = prefs.warmFilterStrength,
                onValueChange = { v -> onUpdatePrefs { it.copy(warmFilterStrength = v.coerceIn(0f, 0.45f)) } },
                valueRange = 0f..0.45f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFE59A4B),
                    activeTrackColor = Color(0xFFE59A4B)
                ),
                modifier = Modifier.testTag("warm_filter_slider")
            )
        }

        // Soft Night Contrast & Glare Dimmer (0%..100% -> 0f..0.38f)
        val dimPct = ((prefs.nightTextDimming / 0.38f) * 100f).roundToInt().coerceIn(0, 100)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Atenuación nocturna suave",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink
                )
                Text(
                    text = if (dimPct == 0) "Estándar" else "-$dimPct% brillo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }
            Text(
                text = "Suaviza el contraste del texto en habitaciones oscuras para evitar deslumbramiento.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkMuted
            )
            Slider(
                value = prefs.nightTextDimming,
                onValueChange = { v -> onUpdatePrefs { it.copy(nightTextDimming = v.coerceIn(0f, 0.38f)) } },
                valueRange = 0f..0.38f,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent
                ),
                modifier = Modifier.testTag("night_dimming_slider")
            )
        }
    }
}

@Composable
private fun ThemeSwatchTile(
    theme: ReadingTheme,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePalette = LocalFolioPalette.current
    val tp = paletteFor(theme)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tp.background)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) activePalette.accent else tp.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .testTag("theme_option_${theme.name}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = tp.ink
            )
            if (theme.isWarmSoft) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tp.accent)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) tp.accent else tp.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (theme.comfortTag.isNotBlank()) {
            Text(
                text = theme.comfortTag,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                color = tp.inkMuted.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HourStepperPill(
    label: String,
    hour: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(palette.surfaceAlt.copy(alpha = 0.7f))
            .border(1.dp, palette.border, RoundedCornerShape(13.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.inkMuted
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, CircleShape)
                    .clickable { onStep(-1) }
                    .testTag("${testTagPrefix}_minus"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Restar una hora",
                    tint = palette.ink,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = formatHour(hour),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = palette.accent
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, CircleShape)
                    .clickable { onStep(1) }
                    .testTag("${testTagPrefix}_plus"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Sumar una hora",
                    tint = palette.ink,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

private fun formatHour(hour: Int): String = String.format("%02d:00", hour.coerceIn(0, 23))

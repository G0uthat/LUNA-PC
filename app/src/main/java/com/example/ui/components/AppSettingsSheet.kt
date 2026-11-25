package com.example.ui.components

import android.app.Activity
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DictionarySourceEntity
import com.example.data.model.IslandNotificationFilterMode
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.TargetFpsOption
import com.example.engine.DisplayRefreshRateController
import com.example.engine.DynamicIslandEventManager
import com.example.engine.SystemMediaController
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val SettingsCardShape20 = RoundedCornerShape(20.dp)
private val SettingsCardShape18 = RoundedCornerShape(18.dp)
private val SettingsCardShape16 = RoundedCornerShape(16.dp)
private val SettingsChipShape14 = RoundedCornerShape(14.dp)

/**
 * Centralized App Configuration sheet (reachable from Library settings gear & discreet Reader gear):
 * - Tab 0: General & Visual (7 Themes incl. Azul Marino, Device System Font toggle, Menu Style
 *          [Marcapáginas arrastrable / Isla dinámica / Clásico], Dock Size scale for Tablets, Auto-Covers)
 * - Tab 1: Diccionario e IA (Google Traductor vs User BYOK AI API Key + custom {palabra} sources)
 * - Tab 2: Créditos, comunidad Discord y actualizaciones en GitHub.
 */
@Composable
fun AppSettingsSheet(
    prefs: ReaderPreferences,
    sources: List<DictionarySourceEntity>,
    initialTab: Int = 0,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onAddSource: (name: String, urlTemplate: String) -> Unit,
    onToggleSource: (DictionarySourceEntity) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onRegenerateCovers: (() -> Unit)? = null,
    onReplayOnboarding: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.84f, 0.06f),
        maxWidth = 520.dp,
        maxHeightFraction = 0.85f,
        testTag = "app_settings_sheet"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Ajustes",
            subtitle = "Tema, lectura, diccionario y créditos",
            leadingIcon = Icons.Rounded.Settings,
            onDismiss = dismissAnimated,
            closeTestTag = "app_settings_close"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppSettingsContent(
                prefs = prefs,
                sources = sources,
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                onUpdatePrefs = onUpdatePrefs,
                onAddSource = onAddSource,
                onToggleSource = onToggleSource,
                onDeleteSource = onDeleteSource,
                onRegenerateCovers = onRegenerateCovers,
                onReplayOnboarding = onReplayOnboarding
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun AppSettingsContent(
    prefs: ReaderPreferences,
    sources: List<DictionarySourceEntity>,
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onAddSource: (name: String, urlTemplate: String) -> Unit,
    onToggleSource: (DictionarySourceEntity) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onRegenerateCovers: (() -> Unit)? = null,
    onReplayOnboarding: (() -> Unit)? = null
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    var showIslandPolicyDialog by remember { mutableStateOf(false) }

    if (showIslandPolicyDialog) {
        IslandNotificationPolicyDialog(
            prefs = prefs,
            onUpdatePrefs = onUpdatePrefs,
            onOpenSystemAccess = { SystemMediaController.openNotificationAccessSettings(context) },
            onDismiss = { showIslandPolicyDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Segmented Tabs (horizontally scrollable so all 3 fit comfortably on compact screens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileChip(
                label = "Diseño y Menú",
                icon = Icons.Rounded.Palette,
                selected = activeTab == 0,
                onClick = { onTabChange(0) },
                modifier = Modifier.testTag("settings_tab_general")
            )
            TactileChip(
                label = "Diccionario e IA",
                icon = Icons.Rounded.MenuBook,
                selected = activeTab == 1,
                onClick = { onTabChange(1) },
                modifier = Modifier.testTag("settings_tab_dict_ai")
            )
            TactileChip(
                label = "Créditos",
                icon = Icons.Rounded.Favorite,
                selected = activeTab == 2,
                onClick = { onTabChange(2) },
                modifier = Modifier.testTag("settings_tab_credits")
            )
        }

        if (activeTab == 0) {
            // 1. Soft Visual Themes, Warm Blue-Light Shield & Configurable Night Reading Mode
            SoftThemeAndNightModePanel(
                prefs = prefs,
                onUpdatePrefs = onUpdatePrefs,
                compactMode = false
            )

            // 2. FPS / High Refresh Rate Unlock (60, 90, 120, 144, 240 FPS + Máx)
            val displayInfo = remember(context, prefs.targetFps) {
                (context as? Activity)?.let { DisplayRefreshRateController.getDisplayInfo(it) }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SettingsCardShape20)
                    .background(palette.surfaceAlt.copy(alpha = 0.55f))
                    .border(1.dp, palette.border, SettingsCardShape20)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Tasa de refresco (FPS)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.ink
                        )
                    }
                    val maxHzLabel = displayInfo?.maxSupportedHz?.roundToInt() ?: 60
                    Text(
                        text = "Pantalla: ${maxHzLabel} Hz",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (fpsOption in TargetFpsOption.entries) {
                        val selected = prefs.targetFps == fpsOption
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(SettingsChipShape14)
                                .background(if (selected) palette.accent else palette.surface)
                                .border(
                                    width = if (selected) 0.dp else 1.dp,
                                    color = if (selected) palette.accent else palette.border,
                                    shape = SettingsChipShape14
                                )
                                .clickable {
                                    onUpdatePrefs { it.copy(targetFps = fpsOption) }
                                }
                                .padding(vertical = 10.dp, horizontal = 2.dp)
                                .testTag("settings_fps_${fpsOption.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = fpsOption.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) palette.onAccent else palette.ink,
                                maxLines = 1
                            )
                            Text(
                                text = if (fpsOption == TargetFpsOption.AUTO) "Auto" else "FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) palette.onAccent.copy(alpha = 0.85f) else palette.inkMuted,
                                maxLines = 1
                            )
                        }
                    }
                }

                val maxHz = displayInfo?.maxSupportedHz?.roundToInt() ?: 60
                val statusCaption = when {
                    prefs.targetFps == TargetFpsOption.AUTO ->
                        "Desbloqueado automáticamente al máximo nativo de tu pantalla (${maxHz} Hz)."
                    prefs.targetFps.fps.roundToInt() > maxHz ->
                        "Solicitando ${prefs.targetFps.label} FPS. Tu panel actual soporta hasta ${maxHz} Hz (se aplicará el máximo disponible)."
                    else ->
                        "Frecuencia objetivo desbloqueada a ${prefs.targetFps.label} FPS con impulso táctil."
                }
                Text(
                    text = statusCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mostrar contador de FPS en vivo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink
                    )
                    Switch(
                        checked = prefs.showFpsCounter,
                        onCheckedChange = { show -> onUpdatePrefs { it.copy(showFpsCounter = show) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                        modifier = Modifier.testTag("show_fps_counter_switch")
                    )
                }
            }

            // 3. Device System Typography Toggle (changes ALL app fonts to device font)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SettingsCardShape18)
                    .background(palette.surfaceAlt.copy(alpha = 0.7f))
                    .border(1.dp, palette.border, SettingsCardShape18)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Usar tipografía del dispositivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Adapta toda la interfaz de Luma a la fuente configurada en tu teléfono o tablet",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = prefs.useSystemFont,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(useSystemFont = enabled) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                    modifier = Modifier.testTag("use_system_font_switch")
                )
            }

            // 4. Reader Menu Style (Draggable Bookmark / Apple Dynamic Island / Classic Bar)
            SectionTitle("Estilo del menú durante la lectura")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (style in ReaderMenuStyle.entries) {
                    val selected = prefs.menuStyle == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SettingsCardShape18)
                            .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.55f))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) palette.accent else palette.border,
                                shape = SettingsCardShape18
                            )
                            .clickable { onUpdatePrefs { it.copy(menuStyle = style) } }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("menu_style_${style.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = if (selected) palette.accent else palette.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = style.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            Text(
                                text = style.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                    }
                }
            }

            // 5. Configurable Menu Size for Phones & Large Tablets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SettingsCardShape18)
                    .background(palette.surfaceAlt.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tamaño del menú desplegable",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "${(prefs.dockScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent
                    )
                }
                Text(
                    text = "Ajusta la escala del marcapáginas e isla para teléfonos o tablets grandes",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
                Slider(
                    value = prefs.dockScale,
                    onValueChange = { s -> onUpdatePrefs { it.copy(dockScale = s) } },
                    valueRange = 0.85f..1.35f,
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                    modifier = Modifier.testTag("dock_scale_slider")
                )
            }

            // 5.5 Immersive Auto-Hide Chrome (fade out all bars & floating options while reading)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SettingsCardShape18)
                    .background(palette.surfaceAlt.copy(alpha = 0.65f))
                    .border(1.dp, palette.border, SettingsCardShape18)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ocultado inmersivo al leer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Desvanece botones, isla y barras tras unos segundos leyendo; toca en cualquier lugar para volver a verlos",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = prefs.autoHideReaderChromeEnabled,
                        onCheckedChange = { enabled ->
                            onUpdatePrefs { it.copy(autoHideReaderChromeEnabled = enabled) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                        modifier = Modifier.testTag("auto_hide_chrome_switch")
                    )
                }

                if (prefs.autoHideReaderChromeEnabled) {
                    Text(
                        text = "Tiempo antes de ocultar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    val delayOptions = listOf(4 to "4s", 8 to "8s", 15 to "15s", 30 to "30s")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((sec, label) in delayOptions) {
                            val selected = prefs.autoHideReaderChromeDelaySec == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(SettingsChipShape14)
                                    .background(if (selected) palette.accent else palette.surface)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = if (selected) palette.accent else palette.border,
                                        shape = SettingsChipShape14
                                    )
                                    .clickable {
                                        onUpdatePrefs { it.copy(autoHideReaderChromeDelaySec = sec) }
                                    }
                                    .padding(vertical = 8.dp)
                                    .testTag("auto_hide_delay_${sec}s"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) palette.onAccent else palette.ink
                                )
                            }
                        }
                    }

                    Text(
                        text = "Rapidez del degradado",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    val fadeOptions = listOf(120 to "Muy rápido", 220 to "Rápido", 380 to "Suave")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((ms, label) in fadeOptions) {
                            val selected = prefs.autoHideFadeSpeedMs == ms
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(SettingsChipShape14)
                                    .background(if (selected) palette.accent else palette.surface)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = if (selected) palette.accent else palette.border,
                                        shape = SettingsChipShape14
                                    )
                                    .clickable {
                                        onUpdatePrefs { it.copy(autoHideFadeSpeedMs = ms) }
                                    }
                                    .padding(vertical = 8.dp)
                                    .testTag("auto_hide_fade_${ms}ms"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) palette.onAccent else palette.ink,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 6. Effortless Tap-to-Turn-Page + Night PDF Filter + Dynamic Island Policy Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SettingsCardShape18)
                    .background(palette.surfaceAlt.copy(alpha = 0.65f))
                    .border(1.dp, palette.border, SettingsCardShape18)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cambio rápido con toque lateral",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Un clic en el borde izquierdo o derecho pasa o retrocede página en libros y PDFs",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = prefs.tapZonesEnabled,
                        onCheckedChange = { enabled -> onUpdatePrefs { it.copy(tapZonesEnabled = enabled) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                        modifier = Modifier.testTag("tap_zones_enabled_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Filtro nocturno suave en PDF",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Atenúa el blanco intenso de los PDFs cuando lees con temas oscuros o Medianoche",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = prefs.pdfNightFilter,
                        onCheckedChange = { enabled -> onUpdatePrefs { it.copy(pdfNightFilter = enabled) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                        modifier = Modifier.testTag("pdf_night_filter_switch")
                    )
                }

                // Isolated status row so 1Hz media playback position ticks NEVER recompose AppSettingsContent!
                IslandPolicySummaryRow(
                    filterMode = prefs.islandFilterMode,
                    allowedCount = prefs.islandAllowedPackages.size,
                    onClick = { showIslandPolicyDialog = true }
                )
            }

            // 7. Storage & Acceleration Disk Cache (configurable limit + Limpiar caché)
            DiskCacheSettingsCard(
                diskCacheLimitMb = prefs.diskCacheLimitMb,
                onLimitChange = { limitMb ->
                    onUpdatePrefs { it.copy(diskCacheLimitMb = limitMb) }
                }
            )

            // 8. Auto-covers helper
            if (onRegenerateCovers != null) {
                TactileButton(
                    onClick = onRegenerateCovers,
                    containerColor = palette.surfaceAlt,
                    shadowColor = palette.borderShadow,
                    contentColor = palette.ink,
                    borderColor = palette.border,
                    cornerRadius = 18.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier.testTag("regenerate_covers_button")
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Autocompletar carátulas de la biblioteca", fontWeight = FontWeight.Bold)
                }
            }
        } else if (activeTab == 1) {
            // Embedded Dictionary Sources & BYOK AI Engine content
            DictionaryAndAiSettingsContent(
                prefs = prefs,
                sources = sources,
                onUpdatePrefs = onUpdatePrefs,
                onAddSource = onAddSource,
                onToggleSource = onToggleSource,
                onDeleteSource = onDeleteSource
            )
        } else {
            // Tab 2: Credits, Community Discord & Dedication
            LumaCreditsSettingsContent(
                onReplayOnboarding = onReplayOnboarding
            )
        }
    }
}

@Composable
private fun IslandPolicySummaryRow(
    filterMode: IslandNotificationFilterMode,
    allowedCount: Int,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val listenerAccessFlow = remember {
        SystemMediaController.mediaState
            .map { it.isNotificationAccessGranted }
            .distinctUntilChanged()
    }
    val isListenerGranted by listenerAccessFlow.collectAsStateWithLifecycle(
        initialValue = SystemMediaController.mediaState.value.isNotificationAccessGranted
    )
    val suppressedCount by DynamicIslandEventManager.suppressedCount.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape16)
            .background(palette.surface)
            .border(1.dp, palette.accent.copy(alpha = 0.32f), SettingsCardShape16)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag("island_event_manager_settings_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (isListenerGranted) {
                        Color(0xFF22C55E).copy(alpha = 0.14f)
                    } else {
                        palette.accentSoft
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (isListenerGranted) Color(0xFF22C55E) else palette.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Gestor de Isla Dinámica",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            val modeSummary = when (filterMode) {
                IslandNotificationFilterMode.ALLOWED_ONLY ->
                    "Críticos + $allowedCount apps permitidas"
                IslandNotificationFilterMode.CRITICAL_ONLY ->
                    "Solo estados críticos (apps silenciadas)"
                IslandNotificationFilterMode.ALL_APPS ->
                    "Estados críticos y todas las apps"
            }
            Text(
                text = if (isListenerGranted) {
                    "$modeSummary · $suppressedCount bloqueadas"
                } else {
                    "$modeSummary · Activa permiso del sistema"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Configurar Isla Dinámica",
            tint = palette.accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

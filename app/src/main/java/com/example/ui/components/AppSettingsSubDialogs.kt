package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.IslandNotificationFilterMode
import com.example.data.model.ReaderPreferences
import com.example.engine.DynamicIslandEventManager
import com.example.engine.GitHubUpdateManager
import com.example.engine.SystemMediaController
import com.example.engine.UpdateCheckOutcome
import com.example.engine.UpdateEnforcementMode
import com.example.notebook.ui.NotebookFonts
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LUMA_DISCORD_URL = "https://discord.gg/f2Ey5NP9dN"

private val SubCardShape24 = RoundedCornerShape(24.dp)
private val SubCardShape22 = RoundedCornerShape(22.dp)
private val SubCardShape18 = RoundedCornerShape(18.dp)
private val SubCardShape16 = RoundedCornerShape(16.dp)
private val SubCardShape14 = RoundedCornerShape(14.dp)

@Composable
internal fun LumaCreditsSettingsContent(
    onReplayOnboarding: (() -> Unit)? = null
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    var copiedDiscord by remember { mutableStateOf(false) }
    var showFontLicensesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_credits_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Brand emblem card with the vectorized calligraphic Luma logo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SubCardShape24)
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .border(1.dp, palette.border, SubCardShape24)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LumaCalligraphicLogo(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(66.dp)
            )
            Text(
                text = "Lector universal EPUB · PDF · DOCX · PPTX · TXT · FB2",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                textAlign = TextAlign.Center
            )
        }

        // 2. Community Discord Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SubCardShape22)
                .background(palette.surfaceAlt.copy(alpha = 0.68f))
                .border(1.dp, palette.accent.copy(alpha = 0.34f), SubCardShape22)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5865F2).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = null,
                        tint = Color(0xFF5865F2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Comunidad oficial en Discord",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = "Comparte lecturas, reporta mejoras y únete a la comunidad de Luma.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
            }

            // Direct clickable URL box + Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SubCardShape14)
                    .background(palette.surface)
                    .border(1.dp, palette.border, SubCardShape14)
                    .clickable {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LUMA_DISCORD_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("credits_discord_link_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LUMA_DISCORD_URL,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .clickable {
                            runCatching {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Discord Luma", LUMA_DISCORD_URL))
                                copiedDiscord = true
                            }
                        }
                        .testTag("credits_copy_discord_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (copiedDiscord) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar enlace de Discord",
                        tint = if (copiedDiscord) Color(0xFF22C55E) else palette.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            TactileButton(
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LUMA_DISCORD_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                containerColor = Color(0xFF5865F2),
                shadowColor = Color(0xFF3C45A5),
                contentColor = Color.White,
                cornerRadius = 16.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("credits_open_discord_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unirse al Discord de la comunidad", fontWeight = FontWeight.ExtraBold)
            }
        }

        // 3. Dedicated separate Dedication Card
        val roseAccent = if (palette.isDark) Color(0xFFF48FB1) else Color(0xFFD84B78)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SubCardShape24)
                .background(roseAccent.copy(alpha = if (palette.isDark) 0.12f else 0.08f))
                .border(1.dp, roseAccent.copy(alpha = 0.30f), SubCardShape24)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag("credits_dedication_card"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(roseAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = roseAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = "Dedicada a mi amada Anghela.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = palette.ink,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )
        }

        // 4. Isolated GitHub APK Updates Card so checkOutcome emissions don't recompose sibling cards
        GitHubUpdatesSettingsCard()

        // 5. Font Licenses ("Licencias de fuentes" — OFL 1.1 & Apache 2.0)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SubCardShape18)
                .background(palette.surfaceAlt.copy(alpha = 0.6f))
                .border(1.dp, palette.border, SubCardShape18)
                .clickable { showFontLicensesDialog = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("credits_font_licenses_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.FontDownload,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Licencias de fuentes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink
                )
                Text(
                    text = "30 tipografías libres empaquetadas (SIL OFL 1.1 y Apache 2.0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        // 6. Optional Permissions & Interactive Tour Replay
        if (onReplayOnboarding != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SubCardShape18)
                    .background(palette.surfaceAlt.copy(alpha = 0.6f))
                    .border(1.dp, palette.border, SubCardShape18)
                    .clickable(onClick = onReplayOnboarding)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("credits_replay_tour_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permisos y tour interactivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Vuelve a revisar permisos del sistema y el recorrido guiado",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showFontLicensesDialog) {
        FontLicensesDialog(onDismiss = { showFontLicensesDialog = false })
    }
}

@Composable
private fun FontLicensesDialog(
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    var expandedLicenseAsset by remember { mutableStateOf<String?>(null) }
    var licenseText by remember { mutableStateOf("") }

    LaunchedEffect(expandedLicenseAsset) {
        val assetName = expandedLicenseAsset
        if (assetName == null) {
            licenseText = ""
        } else {
            licenseText = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("font_licenses/$assetName").bufferedReader().use { it.readText() }
                }.getOrDefault("No se pudo cargar el archivo de licencia.")
            }
        }
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 520.dp,
        maxHeightFraction = 0.88f,
        testTag = "font_licenses_dialog"
    ) { dismissWithAnim ->
        LumaDropdownHeader(
            title = "Licencias de fuentes",
            subtitle = "Atribución oficial de todas las tipografías empaquetadas",
            leadingIcon = Icons.Rounded.FontDownload,
            onDismiss = dismissWithAnim,
            closeTestTag = "font_licenses_close"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // License full-text toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SIL OFL 1.1" to "OFL-1.1.txt", "Apache 2.0" to "APACHE-2.0.txt").forEach { (label, file) ->
                    val open = expandedLicenseAsset == file
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(SubCardShape14)
                            .background(if (open) palette.accent else palette.surfaceAlt)
                            .border(1.dp, if (open) palette.accent else palette.border, SubCardShape14)
                            .clickable { expandedLicenseAsset = if (open) null else file }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (open) "Ocultar $label" else "Leer $label",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (open) palette.onAccent else palette.ink
                        )
                    }
                }
            }

            if (expandedLicenseAsset != null && licenseText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SubCardShape14)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, SubCardShape14)
                        .padding(12.dp)
                ) {
                    Text(
                        text = licenseText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = palette.ink
                    )
                }
            }

            // Full attribution list (28 catalog study fonts + Noto Sans & Noto Sans Symbols fallback)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SubCardShape16)
                    .background(palette.surfaceAlt.copy(alpha = 0.65f))
                    .border(1.dp, palette.border, SubCardShape16)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allCredits = remember {
                    NotebookFonts.CATALOG.map { Triple(it.displayName, it.attribution, it.licenseName) } + listOf(
                        Triple("Noto Sans", "Google", "SIL OFL 1.1"),
                        Triple("Noto Sans Symbols", "Google", "SIL OFL 1.1")
                    )
                }
                allCredits.forEachIndexed { idx, (name, author, license) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            Text(
                                text = author,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                        Text(
                            text = license,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.accentSoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (idx < allCredits.lastIndex) {
                        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubUpdatesSettingsCard() {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateOutcome by GitHubUpdateManager.checkOutcome.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SubCardShape22)
            .background(palette.surfaceAlt.copy(alpha = 0.68f))
            .border(1.dp, palette.border, SubCardShape22)
            .padding(16.dp)
            .testTag("settings_github_updates_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Actualizaciones de Luma",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink
                )
                Text(
                    text = "Versión instalada: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
        }

        val isChecking = updateOutcome is UpdateCheckOutcome.Checking
        TactileButton(
            onClick = {
                if (!isChecking) {
                    scope.launch {
                        GitHubUpdateManager.checkForUpdates(context, manualCheck = true)
                    }
                }
            },
            cornerRadius = 16.dp,
            lipDepth = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("github_check_updates_btn")
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.onAccent
                )
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isChecking) "Comprobando actualizaciones…" else "Buscar actualización ahora",
                fontWeight = FontWeight.ExtraBold
            )
        }

        when (val status = updateOutcome) {
            is UpdateCheckOutcome.UpToDate -> {
                Text(
                    text = "Luma está al día (v${status.checkedVersion}).",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF22C55E)
                )
            }
            is UpdateCheckOutcome.UpdateFound -> {
                Text(
                    text = "Nueva versión disponible: v${status.info.versionName} (${if (status.info.mode == UpdateEnforcementMode.MANDATORY_BLOCKING) "Obligatoria" else "Opcional"}).",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }
            is UpdateCheckOutcome.Error -> {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
            else -> Unit
        }
    }
}

/**
 * Floating dropdown configuration dialog for the centralized Dynamic Island Event Manager:
 * controls critical state alerts, notification filter mode, per-app allowlist (curated + auto-discovered),
 * Android Notification Access shortcut, and instant live previews.
 */
@Composable
internal fun IslandNotificationPolicyDialog(
    prefs: ReaderPreferences,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onOpenSystemAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val listenerAccessFlow = remember {
        SystemMediaController.mediaState
            .map { it.isNotificationAccessGranted }
            .distinctUntilChanged()
    }
    val isListenerGranted by listenerAccessFlow.collectAsStateWithLifecycle(
        initialValue = SystemMediaController.mediaState.value.isNotificationAccessGranted
    )
    val suppressedCount by DynamicIslandEventManager.suppressedCount.collectAsStateWithLifecycle()
    val discoveredApps by DynamicIslandEventManager.discoveredApps.collectAsStateWithLifecycle()

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.5f, 0.08f),
        maxWidth = 520.dp,
        maxHeightFraction = 0.86f,
        testTag = "island_policy_dialog"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Gestor de Isla Dinámica",
            subtitle = "Estados críticos y apps permitidas",
            leadingIcon = Icons.Rounded.Shield,
            onDismiss = dismissAnimated,
            closeTestTag = "island_policy_close"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. System Notification Listener Permission Banner + Suppressed counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SubCardShape18)
                    .background(
                        if (isListenerGranted) Color(0xFF22C55E).copy(alpha = 0.10f)
                        else palette.accentSoft.copy(alpha = 0.7f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isListenerGranted) Color(0xFF22C55E).copy(alpha = 0.35f) else palette.accent.copy(alpha = 0.4f),
                        shape = SubCardShape18
                    )
                    .clickable(onClick = onOpenSystemAccess)
                    .padding(horizontal = 13.dp, vertical = 11.dp)
                    .testTag("island_system_access_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isListenerGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (isListenerGranted) Color(0xFF22C55E) else palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isListenerGranted) "Puente del sistema activo" else "Activar acceso a notificaciones",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = if (isListenerGranted) {
                            "Interceptando avisos con filtro estricto ($suppressedCount silenciadas)"
                        } else {
                            "Permite a Luma mostrar solo las apps autorizadas en la Isla Dinámica"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isListenerGranted) "Ajustar" else "Activar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isListenerGranted) Color(0xFF22C55E) else palette.accent
                )
            }

            // 2. Live Preview Triggers (Critical State vs Allowed App)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SubCardShape14)
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.32f), SubCardShape14)
                        .clickable { DynamicIslandEventManager.triggerPreviewCritical() }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("island_preview_critical_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Probar crítico",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SubCardShape14)
                        .background(palette.accentSoft.copy(alpha = 0.72f))
                        .border(1.dp, palette.accent.copy(alpha = 0.32f), SubCardShape14)
                        .clickable { DynamicIslandEventManager.triggerPreviewAllowedApp(context) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("island_preview_app_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Probar app",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1
                    )
                }
            }

            // 3. Filter Policy Mode Selector
            SectionTitle("Política de filtrado")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in IslandNotificationFilterMode.entries) {
                    val selected = prefs.islandFilterMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SubCardShape16)
                            .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.55f))
                            .border(
                                width = if (selected) 1.8.dp else 1.dp,
                                color = if (selected) palette.accent else palette.border,
                                shape = SubCardShape16
                            )
                            .clickable { onUpdatePrefs { it.copy(islandFilterMode = mode) } }
                            .padding(horizontal = 13.dp, vertical = 10.dp)
                            .testTag("island_filter_mode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.accent else palette.ink
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                    }
                }
            }

            // 4. Critical Device & System State Monitors
            SectionTitle("Estados críticos integrados")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SubCardShape18)
                    .background(palette.surfaceAlt.copy(alpha = 0.6f))
                    .border(1.dp, palette.border, SubCardShape18)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IslandCriticalToggleRow(
                    icon = Icons.Rounded.BatteryAlert,
                    iconTint = Color(0xFFEF4444),
                    title = "Batería baja y crítica",
                    subtitle = "Avisa en 15%, 5% y al reconectar el cargador",
                    checked = prefs.islandShowCriticalBattery,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(islandShowCriticalBattery = enabled) } },
                    testTag = "island_toggle_battery"
                )
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                IslandCriticalToggleRow(
                    icon = Icons.Rounded.WifiOff,
                    iconTint = Color(0xFFF59E0B),
                    title = "Pérdida y recuperación de red",
                    subtitle = "Informa cuando pierdes o recuperas conexión a internet",
                    checked = prefs.islandShowCriticalConnectivity,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(islandShowCriticalConnectivity = enabled) } },
                    testTag = "island_toggle_connectivity"
                )
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                IslandCriticalToggleRow(
                    icon = Icons.Rounded.PhoneInTalk,
                    iconTint = Color(0xFF22C55E),
                    title = "Llamadas y alarmas urgentes",
                    subtitle = "Prioridad máxima sobre cualquier otra notificación",
                    checked = prefs.islandAllowIncomingCalls,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(islandAllowIncomingCalls = enabled) } },
                    testTag = "island_toggle_calls"
                )
            }

            // 5. Explicitly Permitted External Apps Allowlist (Discovered + Curated)
            if (prefs.islandFilterMode == IslandNotificationFilterMode.ALLOWED_ONLY) {
                SectionTitle("Apps permitidas (${prefs.islandAllowedPackages.size})")

                val curatedPackages = remember { DynamicIslandEventManager.curatedApps.map { it.packageName }.toSet() }
                val extraDiscovered = remember(discoveredApps, curatedPackages) {
                    discoveredApps.filter { it.packageName !in curatedPackages }
                }

                if (extraDiscovered.isNotEmpty()) {
                    Text(
                        text = "Detectadas recientemente",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SubCardShape18)
                            .background(palette.surfaceAlt.copy(alpha = 0.6f))
                            .border(1.dp, palette.border, SubCardShape18)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        extraDiscovered.forEachIndexed { index, app ->
                            val isAllowed = app.packageName in prefs.islandAllowedPackages
                            IslandAppAllowToggleRow(
                                name = app.appName,
                                subtitle = if (app.wasBlocked && !isAllowed) "Bloqueada recientemente" else "Detectada en el dispositivo",
                                allowed = isAllowed,
                                onToggle = { enable ->
                                    onUpdatePrefs { current ->
                                        val next = if (enable) {
                                            current.islandAllowedPackages + app.packageName
                                        } else {
                                            current.islandAllowedPackages - app.packageName
                                        }
                                        current.copy(islandAllowedPackages = next)
                                    }
                                },
                                testTag = "island_discovered_${app.packageName}"
                            )
                            if (index < extraDiscovered.lastIndex) {
                                HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SubCardShape18)
                        .background(palette.surfaceAlt.copy(alpha = 0.6f))
                        .border(1.dp, palette.border, SubCardShape18)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DynamicIslandEventManager.curatedApps.forEachIndexed { index, app ->
                        val isAllowed = app.packageName in prefs.islandAllowedPackages
                        IslandAppAllowToggleRow(
                            name = app.displayName,
                            subtitle = app.category,
                            allowed = isAllowed,
                            onToggle = { enable ->
                                onUpdatePrefs { current ->
                                    val next = if (enable) {
                                        current.islandAllowedPackages + app.packageName
                                    } else {
                                        current.islandAllowedPackages - app.packageName
                                    }
                                    current.copy(islandAllowedPackages = next)
                                }
                            },
                            testTag = "island_curated_${app.packageName}"
                        )
                        if (index < DynamicIslandEventManager.curatedApps.lastIndex) {
                            HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun IslandCriticalToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun IslandAppAllowToggleRow(
    name: String,
    subtitle: String,
    allowed: Boolean,
    onToggle: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!allowed) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = allowed,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            modifier = Modifier.testTag(testTag)
        )
    }
}

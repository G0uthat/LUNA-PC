package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.BookmarkDockSide
import com.example.data.model.DocumentFormat
import com.example.data.model.MediaPlaybackInfo
import com.example.data.model.ReaderMenuStyle
import com.example.engine.DynamicIslandEvent
import com.example.engine.SystemBrightnessController
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlin.math.roundToInt

private val BrightnessRowCompactShape = RoundedCornerShape(12.dp)
private val BrightnessRowRegularShape = RoundedCornerShape(16.dp)
private val BrightnessSyncShape = RoundedCornerShape(8.dp)
private val IslandActionTileShape = RoundedCornerShape(11.dp)
private val ClassicBarActionShape = RoundedCornerShape(12.dp)
private val FormatOuterCompactShape = RoundedCornerShape(14.dp)
private val FormatOuterRegularShape = RoundedCornerShape(18.dp)
private val FormatInnerCompactShape = RoundedCornerShape(11.dp)
private val FormatInnerRegularShape = RoundedCornerShape(14.dp)

/**
 * Unified distraction-free Reader Menu supporting 3 configurable styles:
 * 1. BOOKMARK_DOCK: Soft organic bookmark pill draggable freely to ANY side & vertical height.
 * 2. DYNAMIC_ISLAND: Sleek Apple-style floating top capsule expanding softly on tap.
 * 3. CLASSIC_BAR: Traditional Kindle-style top & bottom overlay bars toggled by tapping center.
 *
 * Keeps ONLY objective reading actions + direct brightness control + a tiny discreet Settings gear (Kindle style).
 * Respects [dockScale] (0.85x..1.35x) for phones and large tablets.
 */
@Composable
fun BookmarkDockOverlay(
    menuStyle: ReaderMenuStyle,
    side: BookmarkDockSide,
    dockYFraction: Float,
    dockScale: Float,
    brightnessValue: Float, // -1f = auto, 0.05f..1f = manual
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isDrawingMode: Boolean,
    isPdfDocument: Boolean,
    documentFormat: DocumentFormat = DocumentFormat.EPUB,
    showFormatModeToggle: Boolean = false,
    isOriginalFormatView: Boolean = false,
    isPageTranslated: Boolean,
    isProcessingStatus: Boolean = false,
    processingStatusText: String = "",
    mediaInfo: MediaPlaybackInfo = MediaPlaybackInfo(),
    activeIslandEvent: DynamicIslandEvent? = null,
    queuedIslandCount: Int = 0,
    bookTitle: String,
    progressLabel: String,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onDockRepositioned: (BookmarkDockSide, Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onSetOriginalFormatView: (Boolean) -> Unit = {},
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    onMediaPlayPause: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onMediaPrevious: () -> Unit = {},
    onMediaSeek: (Long) -> Unit = {},
    onOpenNotificationAccess: () -> Unit = {},
    onTapIslandEvent: (DynamicIslandEvent) -> Unit = {},
    onDismissIslandEvent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isDrawingMode) return

    when (menuStyle) {
        ReaderMenuStyle.BOOKMARK_DOCK -> Box(modifier = modifier.fillMaxSize()) {
            DraggableSoftBookmarkDock(
                side = side,
                dockYFraction = dockYFraction,
                dockScale = dockScale,
                brightnessValue = brightnessValue,
                isExpanded = isExpanded,
                isBookmarkedHere = isBookmarkedHere,
                isPdfDocument = isPdfDocument,
                documentFormat = documentFormat,
                showFormatModeToggle = showFormatModeToggle,
                isOriginalFormatView = isOriginalFormatView,
                isPageTranslated = isPageTranslated,
                bookTitle = bookTitle,
                progressLabel = progressLabel,
                onToggleExpanded = onToggleExpanded,
                onDismiss = onDismiss,
                onDockRepositioned = onDockRepositioned,
                onBrightnessChanged = onBrightnessChanged,
                onToggleBookmarkHere = onToggleBookmarkHere,
                onOpenAppearance = onOpenAppearance,
                onOpenToc = onOpenToc,
                onToggleDrawing = onToggleDrawing,
                onSetOriginalFormatView = onSetOriginalFormatView,
                onTriggerOcrTranslate = onTriggerOcrTranslate,
                onOpenAnnotations = onOpenAnnotations,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
                onOpenExport = onOpenExport,
                onBackToLibrary = onBackToLibrary,
                modifier = Modifier.fillMaxSize()
            )
            // Route centralized critical states & allowed app notifications cleanly via Dynamic Island
            DynamicIslandEventBanner(
                event = if (!isExpanded) activeIslandEvent else null,
                queuedCount = queuedIslandCount,
                onTapEvent = onTapIslandEvent,
                onDismiss = onDismissIslandEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )
        }

        ReaderMenuStyle.DYNAMIC_ISLAND -> DynamicIslandReaderDock(
            dockScale = dockScale,
            brightnessValue = brightnessValue,
            isExpanded = isExpanded,
            isBookmarkedHere = isBookmarkedHere,
            isPdfDocument = isPdfDocument,
            documentFormat = documentFormat,
            showFormatModeToggle = showFormatModeToggle,
            isOriginalFormatView = isOriginalFormatView,
            isPageTranslated = isPageTranslated,
            isProcessingStatus = isProcessingStatus,
            processingStatusText = processingStatusText,
            mediaInfo = mediaInfo,
            activeIslandEvent = activeIslandEvent,
            queuedIslandCount = queuedIslandCount,
            bookTitle = bookTitle,
            progressLabel = progressLabel,
            onToggleExpanded = onToggleExpanded,
            onDismiss = onDismiss,
            onBrightnessChanged = onBrightnessChanged,
            onToggleBookmarkHere = onToggleBookmarkHere,
            onOpenAppearance = onOpenAppearance,
            onOpenToc = onOpenToc,
            onToggleDrawing = onToggleDrawing,
            onSetOriginalFormatView = onSetOriginalFormatView,
            onTriggerOcrTranslate = onTriggerOcrTranslate,
            onOpenAnnotations = onOpenAnnotations,
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenExport = onOpenExport,
            onBackToLibrary = onBackToLibrary,
            onMediaPlayPause = onMediaPlayPause,
            onMediaNext = onMediaNext,
            onMediaPrevious = onMediaPrevious,
            onMediaSeek = onMediaSeek,
            onOpenNotificationAccess = onOpenNotificationAccess,
            onTapIslandEvent = onTapIslandEvent,
            onDismissIslandEvent = onDismissIslandEvent,
            modifier = modifier
        )

        ReaderMenuStyle.CLASSIC_BAR -> Box(modifier = modifier.fillMaxSize()) {
            ClassicKindleOverlayBars(
                dockScale = dockScale,
                brightnessValue = brightnessValue,
                isExpanded = isExpanded,
                isBookmarkedHere = isBookmarkedHere,
                isPdfDocument = isPdfDocument,
                documentFormat = documentFormat,
                showFormatModeToggle = showFormatModeToggle,
                isOriginalFormatView = isOriginalFormatView,
                isPageTranslated = isPageTranslated,
                bookTitle = bookTitle,
                progressLabel = progressLabel,
                onDismiss = onDismiss,
                onBrightnessChanged = onBrightnessChanged,
                onToggleBookmarkHere = onToggleBookmarkHere,
                onOpenAppearance = onOpenAppearance,
                onOpenToc = onOpenToc,
                onToggleDrawing = onToggleDrawing,
                onSetOriginalFormatView = onSetOriginalFormatView,
                onTriggerOcrTranslate = onTriggerOcrTranslate,
                onOpenAnnotations = onOpenAnnotations,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
                onOpenExport = onOpenExport,
                onBackToLibrary = onBackToLibrary,
                modifier = Modifier.fillMaxSize()
            )
            DynamicIslandEventBanner(
                event = if (!isExpanded) activeIslandEvent else null,
                queuedCount = queuedIslandCount,
                onTapEvent = onTapIslandEvent,
                onDismiss = onDismissIslandEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )
        }
    }
}

@Composable
internal fun DirectBrightnessRow(
    brightnessValue: Float,
    onBrightnessChanged: (Float) -> Unit,
    darkSurfaceOverride: Boolean = false,
    compact: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Refresh write-settings permission ONLY on mount & ON_RESUME (never on every slider drag frame!)
    var canWriteSystem by remember(context) {
        mutableStateOf(SystemBrightnessController.canWriteSystemSettings(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canWriteSystem = SystemBrightnessController.canWriteSystemSettings(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bg = if (darkSurfaceOverride) Color.White.copy(alpha = 0.08f) else palette.surfaceAlt.copy(alpha = 0.72f)
    val fg = if (darkSurfaceOverride) Color(0xFFF3F6FC) else palette.ink
    val sliderPos = brightnessValue.coerceIn(0.01f, 1f)
    val pct = (sliderPos * 100f).roundToInt().coerceIn(1, 100)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(if (compact) BrightnessRowCompactShape else BrightnessRowRegularShape)
            .background(bg)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 1.dp else 2.dp)
            .testTag("dock_brightness_control"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.BrightnessLow,
            contentDescription = "Bajar brillo",
            tint = fg.copy(alpha = 0.7f),
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Slider(
            value = sliderPos,
            onValueChange = { v -> onBrightnessChanged(v.coerceIn(0.01f, 1f)) },
            valueRange = 0.01f..1f,
            modifier = Modifier
                .weight(1f)
                .height(if (compact) 26.dp else 34.dp)
                .padding(horizontal = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent
            )
        )
        Icon(
            imageVector = Icons.Rounded.Brightness6,
            contentDescription = "Subir brillo",
            tint = fg.copy(alpha = 0.85f),
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Spacer(Modifier.width(5.dp))
        if (canWriteSystem) {
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = fg.copy(alpha = 0.8f)
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(BrightnessSyncShape)
                    .background(palette.accent.copy(alpha = 0.16f))
                    .clickable {
                        SystemBrightnessController.openWriteSettingsPermission(context)
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("brightness_sync_system_btn")
            ) {
                Text(
                    text = "Sync",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent
                )
            }
        }
    }
}

@Composable
internal fun IslandActionTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val safeScale = scale.coerceIn(1f, 1.45f)
    Column(
        modifier = modifier
            .clip(IslandActionTileShape)
            .background(if (active) palette.accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = (7.dp * safeScale), horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((2.dp * safeScale).coerceAtMost(4.dp))
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size((15.dp * safeScale).coerceAtMost(22.dp)))
        Text(
            text = label,
            fontSize = (8.5f * safeScale).coerceIn(8.5f, 12f).sp,
            lineHeight = (10f * safeScale).coerceIn(10f, 14f).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
internal fun ClassicBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .clip(ClassicBarActionShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = palette.ink, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, maxLines = 1)
    }
}

@Composable
internal fun FormatViewModeSegmentedToggle(
    documentFormat: DocumentFormat,
    isOriginalFormatView: Boolean,
    onSelectOriginalFormat: (Boolean) -> Unit,
    darkSurfaceOverride: Boolean = false,
    compact: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val containerBg = if (darkSurfaceOverride) Color.White.copy(alpha = 0.08f) else palette.surfaceAlt.copy(alpha = 0.82f)
    val containerBorder = if (darkSurfaceOverride) Color.White.copy(alpha = 0.14f) else palette.border
    val inactiveInk = if (darkSurfaceOverride) Color.White.copy(alpha = 0.72f) else palette.inkMuted

    val originalIcon = when (documentFormat) {
        DocumentFormat.PPTX -> Icons.Rounded.Slideshow
        DocumentFormat.PDF -> Icons.Rounded.PictureAsPdf
        else -> Icons.Rounded.Description
    }
    val originalLabel = if (compact) documentFormat.label else "Formato ${documentFormat.label}"
    val epubLabel = if (compact) "Adaptado" else "Modo EPUB"

    // Defer color animation reads to drawBehind so toggling causes 0 per-frame recompositions
    val epubBgState = animateColorAsState(
        targetValue = if (!isOriginalFormatView) palette.accent else Color.Transparent,
        animationSpec = motion.tweenSpec(180),
        label = "fmt_toggle_epub_bg"
    )
    val origBgState = animateColorAsState(
        targetValue = if (isOriginalFormatView) palette.accent else Color.Transparent,
        animationSpec = motion.tweenSpec(180),
        label = "fmt_toggle_orig_bg"
    )
    val outerShape = if (compact) FormatOuterCompactShape else FormatOuterRegularShape
    val innerShape = if (compact) FormatInnerCompactShape else FormatInnerRegularShape

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(containerBg)
            .border(1.dp, containerBorder, outerShape)
            .padding(3.dp)
            .testTag("menu_format_mode_toggle"),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(innerShape)
                .drawBehind { drawRect(epubBgState.value) }
                .clickable { onSelectOriginalFormat(false) }
                .padding(horizontal = 8.dp, vertical = if (compact) 5.dp else 8.dp)
                .testTag("toggle_view_adapted_epub"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = "Vista adaptada estilo EPUB",
                tint = if (!isOriginalFormatView) Color.White else inactiveInk,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = epubLabel,
                fontSize = if (compact) 10.5.sp else 11.5.sp,
                fontWeight = if (!isOriginalFormatView) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (!isOriginalFormatView) Color.White else inactiveInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(innerShape)
                .drawBehind { drawRect(origBgState.value) }
                .clickable { onSelectOriginalFormat(true) }
                .padding(horizontal = 8.dp, vertical = if (compact) 5.dp else 8.dp)
                .testTag("toggle_view_original_format"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = originalIcon,
                contentDescription = "Vista formato original ${documentFormat.label}",
                tint = if (isOriginalFormatView) Color.White else inactiveInk,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = originalLabel,
                fontSize = if (compact) 10.5.sp else 11.5.sp,
                fontWeight = if (isOriginalFormatView) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isOriginalFormatView) Color.White else inactiveInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DockToolRow(
    icon: ImageVector,
    label: String,
    scale: Float,
    onClick: () -> Unit,
    testTag: String,
    highlighted: Boolean = false,
    accentTint: Boolean = false,
    iconOnRight: Boolean = true
) {
    val circleSize = (36.dp * scale.coerceIn(0.9f, 1.18f)).coerceIn(34.dp, 42.dp)
    LumaCapsuleOptionRow(
        title = label,
        icon = icon,
        selected = highlighted,
        isAccentPrimary = accentTint,
        iconOnRight = iconOnRight,
        iconCircleSize = circleSize,
        onClick = onClick,
        testTag = testTag
    )
}

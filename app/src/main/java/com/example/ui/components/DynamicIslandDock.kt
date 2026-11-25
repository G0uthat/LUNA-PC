package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentFormat
import com.example.data.model.MediaPlaybackInfo
import com.example.engine.DynamicIslandEvent
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion

private val IslandPillShape = RoundedCornerShape(50)
private val IslandExpandedShape = RoundedCornerShape(24.dp)
private val IslandArtworkShape = RoundedCornerShape(11.dp)
private val IslandNoticeShape = RoundedCornerShape(10.dp)
private val IslandArtworkPlaceholderBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF1F3B73), Color(0xFF121C30))
)
private val IslandBgColor = Color(0xFF0C111C)
private val IslandFgColor = Color(0xFFF3F6FC)
private val IslandTopOrigin = TransformOrigin(0.5f, 0f)

/**
 * Apple / Infinix-style Dynamic Island floating capsule at top center:
 * - Uses crisp Zoom-In & Zoom-Out anchored at top-center (TransformOrigin(0.5f, 0f)) with
 *   FastOutSlowInEasing (ZERO overshoot bounce and ZERO square clipping artifacts!).
 * - Integrates live OCR / translation status directly inside the capsule (Live Activity style)
 *   so floating pills never overlap or collide with the Dynamic Island!
 */
@Composable
internal fun DynamicIslandReaderDock(
    dockScale: Float,
    brightnessValue: Float,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isPdfDocument: Boolean,
    documentFormat: DocumentFormat,
    showFormatModeToggle: Boolean,
    isOriginalFormatView: Boolean,
    isPageTranslated: Boolean,
    isProcessingStatus: Boolean,
    processingStatusText: String,
    mediaInfo: MediaPlaybackInfo,
    activeIslandEvent: DynamicIslandEvent?,
    queuedIslandCount: Int,
    bookTitle: String,
    progressLabel: String,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onSetOriginalFormatView: (Boolean) -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaSeek: (Long) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTapIslandEvent: (DynamicIslandEvent) -> Unit,
    onDismissIslandEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current

    // Only allow switching between Lectura and Música when media is actively playing or recently paused/detected
    val hasMediaAvailable = mediaInfo.hasActiveSession && mediaInfo.title.isNotBlank()
    // 0 = Lectura (app/reader tools), 1 = Música / Video (Spotify, YouTube, etc.)
    var activeIslandTab by remember { mutableIntStateOf(0) }

    // Cache ImageBitmap wrapper across 1Hz media position ticks
    val albumArtImage = remember(mediaInfo.albumArt) { mediaInfo.albumArt?.asImageBitmap() }

    LaunchedEffect(hasMediaAvailable) {
        if (!hasMediaAvailable && activeIslandTab != 0) {
            activeIslandTab = 0
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availMaxWidth = this.maxWidth
        val availMaxHeight = this.maxHeight
        val isCompactHeight = availMaxHeight < 480.dp
        // Responsive device-proportional scaling so the Dynamic Island feels naturally sized on tablets too
        val deviceScale = when {
            availMaxWidth >= 960.dp -> 1.42f
            availMaxWidth >= 760.dp -> 1.30f
            availMaxWidth >= 600.dp -> 1.18f
            else -> 1.0f
        }
        val pillScale = (dockScale * deviceScale).coerceIn(0.88f, 1.58f)
        // Gentle 15% dim + subtle frosted veil matching ProfileDropdownCapsuleOverlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(motion.tweenSpec(105)),
            exit = fadeOut(motion.exitTween(80))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Top-center anchor container sitting cleanly in the reserved top safe band (NEVER invades sheets/PDFs!)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(top = (6.dp * deviceScale).coerceAtMost(10.dp), start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 0. Centralized critical states & permitted external app notifications via Dynamic Island
            DynamicIslandEventBanner(
                event = if (!isExpanded) activeIslandEvent else null,
                queuedCount = queuedIslandCount,
                onTapEvent = onTapIslandEvent,
                onDismiss = onDismissIslandEvent
            )

            // 1. Compact Apple/Infinix Dynamic Island Pill (snappy GPU Zoom-In / Zoom-Out)
            AnimatedVisibility(
                visible = !isExpanded && activeIslandEvent == null,
                enter = scaleIn(
                    initialScale = 0.90f,
                    transformOrigin = IslandTopOrigin,
                    animationSpec = motion.tactileSpring()
                ) + fadeIn(motion.emphasizedTween(105)),
                exit = scaleOut(
                    targetScale = 0.90f,
                    transformOrigin = IslandTopOrigin,
                    animationSpec = motion.exitTween(85)
                ) + fadeOut(motion.exitTween(75))
            ) {
                val showCompactMedia = !isProcessingStatus && hasMediaAvailable
                Row(
                    modifier = Modifier
                        .widthIn(max = (264.dp * deviceScale).coerceAtMost(420.dp))
                        .shadow(8.dp, IslandPillShape, ambientColor = Color.Black.copy(alpha = 0.32f))
                        .clip(IslandPillShape)
                        .background(IslandBgColor.copy(alpha = 0.96f))
                        .border(
                            width = 1.dp,
                            color = when {
                                isProcessingStatus -> palette.accent.copy(alpha = 0.7f)
                                showCompactMedia && mediaInfo.isPlaying -> Color(0xFF1DB954).copy(alpha = 0.45f)
                                else -> Color.White.copy(alpha = 0.14f)
                            },
                            shape = IslandPillShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded
                        )
                        .padding(
                            horizontal = (12.dp * pillScale),
                            vertical = (5.5.dp * pillScale)
                        )
                        .testTag("dynamic_island_dock"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp * deviceScale.coerceAtMost(1.3f))
                ) {
                    if (isProcessingStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp * deviceScale),
                            strokeWidth = 2.dp,
                            color = Color(0xFF639BFF)
                        )
                        Text(
                            text = processingStatusText.ifBlank { "Traduciendo…" },
                            fontSize = (11f * deviceScale).coerceIn(11f, 15.5f).sp,
                            fontWeight = FontWeight.Bold,
                            color = IslandFgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size((6.dp * deviceScale).coerceAtMost(9.dp))
                                .clip(CircleShape)
                                .background(if (isBookmarkedHere) Color(0xFF639BFF) else Color(0xFF39C689))
                        )
                        Text(
                            text = progressLabel.substringBefore("·").trim(),
                            fontSize = (11f * deviceScale).coerceIn(11f, 15.5f).sp,
                            fontWeight = FontWeight.Bold,
                            color = IslandFgColor
                        )
                        if (showCompactMedia) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(12.dp * deviceScale)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                            if (albumArtImage != null) {
                                Image(
                                    bitmap = albumArtImage,
                                    contentDescription = "Miniatura multimedia",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size((16.dp * deviceScale).coerceAtMost(24.dp))
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size((16.dp * deviceScale).coerceAtMost(24.dp))
                                        .clip(CircleShape)
                                        .background(if (mediaInfo.isPlaying) Color(0xFF1DB954) else Color(0xFF5C8DFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size((10.dp * deviceScale).coerceAtMost(15.dp))
                                    )
                                }
                            }
                            Text(
                                text = mediaInfo.title,
                                fontSize = (10.5f * deviceScale).coerceIn(10.5f, 14.5f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IslandFgColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = (110.dp * deviceScale).coerceAtMost(200.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "Expandir isla dinámica",
                                tint = IslandFgColor.copy(alpha = 0.72f),
                                modifier = Modifier.size((13.dp * deviceScale).coerceAtMost(19.dp))
                            )
                        }
                    }
                }
            }

            // 2. Compact Expanded Dynamic Island — snappy spring Zoom-In / rapid Zoom-Out
            AnimatedVisibility(
                visible = isExpanded,
                enter = scaleIn(
                    initialScale = 0.84f,
                    transformOrigin = IslandTopOrigin,
                    animationSpec = motion.modalSpring()
                ) + fadeIn(motion.emphasizedTween(110)),
                exit = scaleOut(
                    targetScale = 0.86f,
                    transformOrigin = IslandTopOrigin,
                    animationSpec = motion.exitTween(90)
                ) + fadeOut(motion.exitTween(80))
            ) {
                val baseExpandedW = when {
                    availMaxWidth >= 900.dp -> (470.dp * dockScale).coerceIn(420.dp, 580.dp)
                    availMaxWidth >= 680.dp -> (420.dp * dockScale).coerceIn(370.dp, 520.dp)
                    isCompactHeight -> (400.dp * dockScale).coerceIn(320.dp, 460.dp)
                    else -> (312.dp * dockScale).coerceIn(280.dp, 400.dp)
                }
                val expandedW = baseExpandedW.coerceAtMost((availMaxWidth - 28.dp).coerceAtLeast(260.dp))
                val headerBtnSize = (28.dp * deviceScale).coerceIn(28.dp, 38.dp)
                val headerIconSize = (15.dp * deviceScale).coerceIn(15.dp, 21.dp)
                Column(
                    modifier = Modifier
                        .width(expandedW)
                        .heightIn(max = (availMaxHeight - 36.dp).coerceAtLeast(180.dp))
                        .shadow(
                            elevation = if (motion.isLowRam) 5.dp else 12.dp,
                            shape = IslandExpandedShape,
                            ambientColor = Color.Black.copy(alpha = 0.36f)
                        )
                        .clip(IslandExpandedShape)
                        .background(IslandBgColor.copy(alpha = 0.97f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), IslandExpandedShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = (12.dp * deviceScale).coerceIn(12.dp, 18.dp),
                            vertical = if (isCompactHeight) 8.dp else (10.dp * deviceScale).coerceIn(10.dp, 15.dp)
                        )
                        .testTag("dynamic_island_expanded"),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else (8.dp * deviceScale).coerceIn(8.dp, 12.dp))
                ) {
                    // Show Lectura <-> Música switcher ONLY when music/video is playing or recently paused
                    if (hasMediaAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(IslandPillShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                IslandModePill(
                                    label = "Lectura",
                                    icon = Icons.Rounded.AutoStories,
                                    selected = activeIslandTab == 0,
                                    accentColor = palette.accent,
                                    onClick = { activeIslandTab = 0 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("island_tab_reading")
                                )
                                IslandModePill(
                                    label = "Música",
                                    icon = Icons.Rounded.MusicNote,
                                    selected = activeIslandTab == 1,
                                    accentColor = Color(0xFF1DB954),
                                    showLiveDot = mediaInfo.isPlaying,
                                    onClick = { activeIslandTab = 1 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("island_tab_media")
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(headerBtnSize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Contraer",
                                    tint = IslandFgColor.copy(alpha = 0.78f),
                                    modifier = Modifier.size(headerIconSize)
                                )
                            }
                        }
                    }

                    if (!hasMediaAvailable || activeIslandTab == 0) {
                        // Compact Header: Back + Title/Progress + Settings (+ Close when switcher is hidden)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(headerBtnSize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { onDismiss(); onBackToLibrary() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Biblioteca", tint = IslandFgColor, modifier = Modifier.size(headerIconSize))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookTitle,
                                    fontSize = (12f * deviceScale).coerceIn(12f, 16f).sp,
                                    lineHeight = (15f * deviceScale).coerceIn(14f, 20f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IslandFgColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isProcessingStatus && processingStatusText.isNotBlank()) {
                                        processingStatusText
                                    } else {
                                        progressLabel
                                    },
                                    fontSize = (10f * deviceScale).coerceIn(10f, 13.5f).sp,
                                    lineHeight = (12f * deviceScale).coerceIn(12f, 16f).sp,
                                    color = if (isProcessingStatus) Color(0xFF639BFF) else IslandFgColor.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(headerBtnSize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onDismiss(); onOpenSettings() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Configuración", tint = IslandFgColor.copy(alpha = 0.78f), modifier = Modifier.size(headerIconSize))
                            }
                            if (!hasMediaAvailable) {
                                Box(
                                    modifier = Modifier
                                        .size(headerBtnSize)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable(onClick = onDismiss),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Contraer", tint = IslandFgColor.copy(alpha = 0.78f), modifier = Modifier.size(headerIconSize))
                                }
                            }
                        }

                        // Compact Brightness Slider inside Dynamic Island
                        DirectBrightnessRow(
                            brightnessValue = brightnessValue,
                            onBrightnessChanged = onBrightnessChanged,
                            darkSurfaceOverride = true,
                            compact = deviceScale < 1.2f
                        )

                        // Dedicated View Mode Switcher ONLY for non-EPUB files (PDF, PPTX, DOCX, TXT, FB2)
                        if (showFormatModeToggle) {
                            FormatViewModeSegmentedToggle(
                                documentFormat = documentFormat,
                                isOriginalFormatView = isOriginalFormatView,
                                darkSurfaceOverride = true,
                                compact = deviceScale < 1.2f,
                                onSelectOriginalFormat = { original ->
                                    onSetOriginalFormatView(original)
                                    onDismiss()
                                }
                            )
                        }

                        // ALL quick reading tools in ONE SINGLE compact row!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy((3.dp * deviceScale).coerceAtMost(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IslandActionTile(
                                icon = Icons.Rounded.TextFields,
                                label = "Texto",
                                active = false,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenAppearance() }
                            IslandActionTile(
                                icon = Icons.Rounded.FormatListBulleted,
                                label = "Índice",
                                active = false,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenToc() }
                            IslandActionTile(
                                icon = Icons.Rounded.Draw,
                                label = "Firmar",
                                active = false,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onToggleDrawing() }
                            IslandActionTile(
                                icon = if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                label = "Marcar",
                                active = isBookmarkedHere,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onToggleBookmarkHere(); onDismiss() }
                            if (isPdfDocument) {
                                IslandActionTile(
                                    icon = Icons.Rounded.Translate,
                                    label = if (isPageTranslated) "Orig." else "Trad.",
                                    active = isPageTranslated,
                                    scale = deviceScale,
                                    modifier = Modifier.weight(1f)
                                ) { onDismiss(); onTriggerOcrTranslate() }
                            }
                            IslandActionTile(
                                icon = Icons.Rounded.Bookmarks,
                                label = "Notas",
                                active = false,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenAnnotations() }
                            IslandActionTile(
                                icon = Icons.Rounded.Search,
                                label = "Buscar",
                                active = false,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenSearch() }
                            IslandActionTile(
                                icon = Icons.Rounded.IosShare,
                                label = "Export",
                                active = false,
                                scale = deviceScale,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenExport() }
                        }
                    } else {
                        // TAB 1: Compact Live Music & Video Controller (only reachable when media is active/recent)
                        DynamicIslandMediaPanel(
                            mediaInfo = mediaInfo,
                            islandFg = IslandFgColor,
                            accentColor = palette.accent,
                            onPlayPause = onMediaPlayPause,
                            onNext = onMediaNext,
                            onPrevious = onMediaPrevious,
                            onSeek = onMediaSeek,
                            onOpenNotificationAccess = onOpenNotificationAccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandModePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLiveDot: Boolean = false
) {
    Row(
        modifier = modifier
            .height(25.dp)
            .clip(IslandPillShape)
            .background(if (selected) accentColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1
        )
        if (showLiveDot && !selected) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954))
            )
        }
    }
}

@Composable
private fun DynamicIslandMediaPanel(
    mediaInfo: MediaPlaybackInfo,
    islandFg: Color,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    val duration = mediaInfo.durationMs.coerceAtLeast(0L)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val albumArtImage = remember(mediaInfo.albumArt) { mediaInfo.albumArt?.asImageBitmap() }

    val progressFraction = if (isScrubbing) {
        scrubFraction
    } else if (duration > 0L) {
        (mediaInfo.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dynamic_island_media_panel"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Compact Artwork thumbnail + Title + Artist + App pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(IslandArtworkShape)
                    .background(IslandArtworkPlaceholderBrush)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), IslandArtworkShape),
                contentAlignment = Alignment.Center
            ) {
                if (albumArtImage != null) {
                    Image(
                        bitmap = albumArtImage,
                        contentDescription = "Miniatura de reproducción",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF8AB4FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (mediaInfo.appName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (mediaInfo.isPlaying) Color(0xFF1DB954) else Color(0xFF8B99B3))
                        )
                        Text(
                            text = mediaInfo.appName,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mediaInfo.isPlaying) Color(0xFF4ADE80) else islandFg.copy(alpha = 0.62f),
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = mediaInfo.title,
                    fontSize = 12.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = islandFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (mediaInfo.artist.isNotBlank()) {
                    Text(
                        text = mediaInfo.artist,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = islandFg.copy(alpha = 0.64f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Compact Track / Video progress slider + timestamps
        if (duration > 0L) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progressFraction,
                    onValueChange = { v ->
                        isScrubbing = true
                        scrubFraction = v.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        if (isScrubbing) {
                            onSeek((scrubFraction * duration).toLong())
                        }
                        isScrubbing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = if (mediaInfo.isPlaying) Color(0xFF1DB954) else accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val shownPos = if (isScrubbing) (scrubFraction * duration).toLong() else mediaInfo.positionMs
                    Text(
                        text = formatMediaTime(shownPos),
                        fontSize = 9.5.sp,
                        color = islandFg.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatMediaTime(duration),
                        fontSize = 9.5.sp,
                        color = islandFg.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Compact Media transport buttons: Previous / Play-Pause / Next
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .clickable(onClick = onPrevious)
                    .testTag("island_media_prev"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = islandFg,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (mediaInfo.isPlaying) Color(0xFF1DB954) else accentColor)
                    .clickable(onClick = onPlayPause)
                    .testTag("island_media_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mediaInfo.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (mediaInfo.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .clickable(onClick = onNext)
                    .testTag("island_media_next"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = islandFg,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Compact link to unlock artwork/title/progress when using AudioManager fallback
        if (!mediaInfo.isNotificationAccessGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(IslandNoticeShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onOpenNotificationAccess)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("island_media_enable_access"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = Color(0xFF7FA8FF),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Activar permiso para ver carátula y avance",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = islandFg.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}

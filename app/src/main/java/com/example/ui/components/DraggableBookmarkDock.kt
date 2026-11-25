package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.BookmarkDockSide
import com.example.data.model.DocumentFormat
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PillDragShape = RoundedCornerShape(20.dp)
private val PillRightShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
private val PillLeftShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 4.dp, bottomStart = 4.dp)
private val DockCapsuleMenuShape = RoundedCornerShape(32.dp)

@Composable
internal fun DraggableSoftBookmarkDock(
    side: BookmarkDockSide,
    dockYFraction: Float,
    dockScale: Float,
    brightnessValue: Float,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isPdfDocument: Boolean,
    documentFormat: DocumentFormat,
    showFormatModeToggle: Boolean,
    isOriginalFormatView: Boolean,
    isPageTranslated: Boolean,
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
    onSetOriginalFormatView: (Boolean) -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val density = LocalDensity.current
    val isRight = side == BookmarkDockSide.RIGHT

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val tabTouchW = with(density) { (48.dp * dockScale.coerceIn(0.9f, 1.25f)).toPx() }
        val tabTouchH = with(density) { (68.dp * dockScale.coerceIn(0.9f, 1.25f)).toPx() }

        val coroutineScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }

        // Persistent Animatables — NEVER re-keyed on side/dockYFraction so pointerInput NEVER holds stale refs!
        val animX = remember { Animatable(if (isRight) maxW - tabTouchW else 0f) }
        val animY = remember {
            Animatable((dockYFraction * maxH - tabTouchH / 2f).coerceIn(maxH * 0.10f, maxH * 0.86f))
        }
        var hasInitializedPosition by remember { mutableStateOf(false) }

        val latestMaxW by rememberUpdatedState(maxW)
        val latestMaxH by rememberUpdatedState(maxH)
        val latestTabW by rememberUpdatedState(tabTouchW)
        val latestTabH by rememberUpdatedState(tabTouchH)
        val latestOnRepositioned by rememberUpdatedState(onDockRepositioned)

        // Sync smoothly when layout size or saved preferences change while not actively dragging
        LaunchedEffect(side, dockYFraction, maxW, maxH, tabTouchW, tabTouchH) {
            if (!isDragging) {
                val targetX = if (side == BookmarkDockSide.RIGHT) {
                    (maxW - tabTouchW).coerceAtLeast(0f)
                } else {
                    0f
                }
                val targetY = (dockYFraction * maxH - tabTouchH / 2f)
                    .coerceIn(maxH * 0.10f, (maxH - tabTouchH - maxH * 0.08f).coerceAtLeast(maxH * 0.10f))
                if (!hasInitializedPosition) {
                    animX.snapTo(targetX)
                    animY.snapTo(targetY)
                    hasInitializedPosition = true
                } else {
                    launch {
                        animX.animateTo(targetX, motion.glideSpring())
                    }
                    launch {
                        animY.animateTo(targetY, motion.glideSpring())
                    }
                }
            }
        }

        // Tactile lift & bookmark pop read ONLY inside graphicsLayer {} -> ZERO recompositions per frame!
        val dragLiftScaleState = animateFloatAsState(
            targetValue = if (isDragging) 1.12f else 1f,
            animationSpec = motion.tactileSpring(),
            label = "bookmark_drag_lift"
        )
        val bookmarkIconPopState = animateFloatAsState(
            targetValue = if (isBookmarkedHere) 1.12f else 1f,
            animationSpec = motion.expressiveSpring(),
            label = "bookmark_icon_pop"
        )

        // IMPORTANT: Do NOT read animY.value in composition scope (that forces 120Hz recompositions while dragging!).
        // Use persisted dockYFraction for the drawer TransformOrigin so dragging causes ZERO recompositions.
        val pillOrigin = TransformOrigin(if (isRight) 1f else 0f, 0.5f)
        val drawerOrigin = TransformOrigin(if (isRight) 1f else 0f, dockYFraction.coerceIn(0.15f, 0.85f))

        // Gentle 15% dim + subtle frosted veil matching ProfileDropdownCapsuleOverlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(motion.tweenSpec(190)),
            exit = fadeOut(motion.exitTween(155))
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

        // Soft Organic Draggable Bookmark Pill Tab (1:1 instant finger tracking + Dynamic Island Zoom-In/Zoom-Out)
        AnimatedVisibility(
            visible = !isExpanded,
            enter = scaleIn(
                initialScale = 0.78f,
                transformOrigin = pillOrigin,
                animationSpec = motion.tactileSpring()
            ) + fadeIn(motion.emphasizedTween(175)),
            exit = scaleOut(
                targetScale = 0.78f,
                transformOrigin = pillOrigin,
                animationSpec = motion.exitTween(155)
            ) + fadeOut(motion.exitTween(130))
        ) {
            val pillShape = when {
                isDragging -> PillDragShape
                isRight -> PillRightShape
                else -> PillLeftShape
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                    .size(
                        width = with(density) { tabTouchW.toDp() },
                        height = with(density) { tabTouchH.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                coroutineScope.launch {
                                    animX.stop()
                                    animY.stop()
                                }
                            },
                            onDragEnd = {
                                val w = latestMaxW
                                val h = latestMaxH
                                val tw = latestTabW
                                val th = latestTabH
                                val newSide = if (animX.value + tw / 2f > w / 2f) {
                                    BookmarkDockSide.RIGHT
                                } else {
                                    BookmarkDockSide.LEFT
                                }
                                val snapX = if (newSide == BookmarkDockSide.RIGHT) {
                                    (w - tw).coerceAtLeast(0f)
                                } else {
                                    0f
                                }
                                val newYFrac = ((animY.value + th / 2f) / h).coerceIn(0.12f, 0.88f)
                                isDragging = false
                                latestOnRepositioned(newSide, newYFrac)
                                coroutineScope.launch {
                                    animX.animateTo(
                                        targetValue = snapX,
                                        animationSpec = motion.glideSpring()
                                    )
                                }
                            },
                            onDragCancel = {
                                val w = latestMaxW
                                val tw = latestTabW
                                val snapX = if (animX.value + tw / 2f > w / 2f) {
                                    (w - tw).coerceAtLeast(0f)
                                } else {
                                    0f
                                }
                                isDragging = false
                                coroutineScope.launch {
                                    animX.animateTo(
                                        targetValue = snapX,
                                        animationSpec = motion.glideSpring()
                                    )
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxAllowedX = (latestMaxW - latestTabW).coerceAtLeast(0f)
                                val minAllowedY = latestMaxH * 0.08f
                                val maxAllowedY = (latestMaxH - latestTabH - latestMaxH * 0.06f)
                                    .coerceAtLeast(minAllowedY)
                                val nextX = (animX.value + dragAmount.x).coerceIn(0f, maxAllowedX)
                                val nextY = (animY.value + dragAmount.y).coerceIn(minAllowedY, maxAllowedY)
                                coroutineScope.launch {
                                    animX.snapTo(nextX)
                                    animY.snapTo(nextY)
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpanded
                    )
                    .testTag("bookmark_dock_tab"),
                contentAlignment = if (isDragging) {
                    Alignment.Center
                } else if (isRight) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
            ) {
                // Soft translucent capsule bookmark handle (GPU-composited scale for 0-jank 120Hz feel)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val s = dragLiftScaleState.value
                            scaleX = s
                            scaleY = s
                        }
                        .size(
                            width = (26.dp * dockScale.coerceIn(0.9f, 1.25f)),
                            height = (54.dp * dockScale.coerceIn(0.9f, 1.25f))
                        )
                        .shadow(
                            elevation = if (isDragging) 14.dp else 6.dp,
                            shape = pillShape,
                            ambientColor = palette.accent.copy(alpha = if (isDragging) 0.42f else 0.25f)
                        )
                        .clip(pillShape)
                        .background(palette.accent.copy(alpha = if (isDragging) 0.96f else 0.86f))
                        .border(1.dp, Color.White.copy(alpha = if (isDragging) 0.45f else 0.26f), pillShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBookmarkedHere) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Abrir menú marcapáginas (arrastrable)",
                        tint = Color.White,
                        modifier = Modifier
                            .graphicsLayer {
                                val s = bookmarkIconPopState.value
                                scaleX = s
                                scaleY = s
                            }
                            .size((15.dp * dockScale.coerceIn(0.9f, 1.25f)))
                    )
                }
            }
        }

        val isCompactHeight = this.maxHeight < 480.dp
        val dockWidth = (264.dp * dockScale).coerceIn(232.dp, if (isCompactHeight) 320.dp else 360.dp)
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(
                initialScale = 0.66f,
                transformOrigin = drawerOrigin,
                animationSpec = motion.modalSpring()
            ) + fadeIn(motion.emphasizedTween(185)),
            exit = scaleOut(
                targetScale = 0.68f,
                transformOrigin = drawerOrigin,
                animationSpec = motion.exitTween(175)
            ) + fadeOut(motion.exitTween(145)),
            modifier = Modifier
                .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                .displayCutoutPadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isCompactHeight) 12.dp else 18.dp, vertical = if (isCompactHeight) 10.dp else 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(dockWidth)
                    .shadow(
                        elevation = if (palette.isDark) 20.dp else 16.dp,
                        shape = DockCapsuleMenuShape,
                        ambientColor = Color.Black.copy(alpha = 0.28f),
                        spotColor = Color.Black.copy(alpha = 0.24f)
                    )
                    .clip(DockCapsuleMenuShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, DockCapsuleMenuShape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("bookmark_dock_menu"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Soft header with title + circular Settings gear + circular close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border, CircleShape)
                            .clickable {
                                onDismiss()
                                onOpenSettings()
                            }
                            .testTag("dock_discreet_settings"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Configuración",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar menú",
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    thickness = 1.dp,
                    color = palette.border.copy(alpha = 0.6f)
                )

                // Direct Brightness Control directly inside the floating capsule!
                DirectBrightnessRow(
                    brightnessValue = brightnessValue,
                    onBrightnessChanged = onBrightnessChanged
                )

                // Dedicated View Mode Switcher ONLY for non-EPUB files (PDF, PPTX, DOCX, TXT, FB2)
                if (showFormatModeToggle) {
                    FormatViewModeSegmentedToggle(
                        documentFormat = documentFormat,
                        isOriginalFormatView = isOriginalFormatView,
                        onSelectOriginalFormat = { original ->
                            onSetOriginalFormatView(original)
                            onDismiss()
                        }
                    )
                }

                // Objective Reading Tools in Luma floating capsule style (circular icon badges aligned to thumb edge)
                DockToolRow(
                    icon = if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    label = if (isBookmarkedHere) "QUITAR MARCADOR" else "MARCAR PÁGINA",
                    highlighted = isBookmarkedHere,
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onToggleBookmarkHere(); onDismiss() },
                    testTag = "dock_toggle_bookmark"
                )
                DockToolRow(
                    icon = Icons.Rounded.TextFields,
                    label = "TEXTO Y TEMA",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenAppearance() },
                    testTag = "dock_appearance"
                )
                DockToolRow(
                    icon = Icons.Rounded.FormatListBulleted,
                    label = "ÍNDICE",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenToc() },
                    testTag = "dock_toc"
                )
                DockToolRow(
                    icon = Icons.Rounded.Draw,
                    label = "DIBUJAR / FIRMAR",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onToggleDrawing() },
                    testTag = "dock_draw"
                )
                if (isPdfDocument) {
                    DockToolRow(
                        icon = Icons.Rounded.Translate,
                        label = if (isPageTranslated) "VER ORIGINAL" else "TRADUCIR (OCR)",
                        highlighted = isPageTranslated,
                        iconOnRight = isRight,
                        scale = dockScale,
                        onClick = { onDismiss(); onTriggerOcrTranslate() },
                        testTag = "dock_ocr_translate"
                    )
                }
                DockToolRow(
                    icon = Icons.Rounded.Bookmarks,
                    label = "NOTAS",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenAnnotations() },
                    testTag = "dock_annotations"
                )
                DockToolRow(
                    icon = Icons.Rounded.Search,
                    label = "BUSCAR",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenSearch() },
                    testTag = "dock_search"
                )
                DockToolRow(
                    icon = Icons.Rounded.IosShare,
                    label = "EXPORTAR",
                    accentTint = true,
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenExport() },
                    testTag = "dock_export"
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    thickness = 1.dp,
                    color = palette.border.copy(alpha = 0.6f)
                )

                DockToolRow(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    label = "BIBLIOTECA",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onBackToLibrary() },
                    testTag = "dock_back_library"
                )
            }
        }
    }
}

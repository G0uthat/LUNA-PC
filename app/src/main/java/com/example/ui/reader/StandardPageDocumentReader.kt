package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.PageAnimationMode
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedDocument
import com.example.data.model.ParsedSlide
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.StandardPageFormat
import com.example.data.model.TextAlignOption
import com.example.engine.DynamicIslandEventManager
import com.example.ui.components.DrawingCanvasOverlay
import com.example.ui.components.ReaderBatteryPillBubble
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Immutable
internal data class StandardDocumentSheet(
    val sheetIndex: Int,
    val chapterIndex: Int,
    val locationKey: String,
    val headerLabel: String,
    val blocks: List<ParsedBlock> = emptyList(),
    val slide: ParsedSlide? = null
)

private val StdPillShape = RoundedCornerShape(50)
private val StdNotesCardShape = RoundedCornerShape(20.dp)
private val StdPaperShape = RoundedCornerShape(4.dp)

/**
 * Universal Standard Physical Page & PowerPoint Slide Reader:
 * - Renders ANY document (PPTX, DOCX, EPUB, TXT, FB2) at true physical sheet dimensions
 *   (Auto file size, A4, Carta, Oficio, A3, A5, Slide 16:9, Slide 4:3) instead of reflowing to phone width.
 * - Supports silky GPU focal pinch-to-zoom (0.85x..4.5x), double-tap zoom, and 2D pan.
 * - Anchors freehand drawings & signatures directly to the exact physical sheet coordinates so
 *   users can zoom straight onto a signature line, lock pan, sign cleanly, and export to PDF.
 */
@Composable
fun StandardPageDocumentReader(
    parsed: ParsedDocument,
    currentUnitIndex: Int,
    jumpToUnitTrigger: Int,
    prefs: ReaderPreferences,
    drawingsByLocation: Map<String, List<DrawingStroke>>,
    isDrawingMode: Boolean,
    isPanZoomWhileDrawing: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float = 1f,
    activeShapeFilled: Boolean = false,
    onActiveSheetChanged: (sheetIndex: Int, totalSheets: Int, locationKey: String, chapterIndex: Int) -> Unit,
    onSelectStandardFormat: (StandardPageFormat) -> Unit,
    onToggleStandardView: () -> Unit,
    onStrokeCompleted: (String, DrawingStroke) -> Unit,
    onEraseStrokes: (String, Set<String>) -> Unit,
    onTapPageCenter: () -> Unit,
    showBatteryBubble: Boolean = true,
    chromeAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val coroutineScope = rememberCoroutineScope()
    val isPptx = parsed.format == DocumentFormat.PPTX

    val (resolvedWidthPt, resolvedHeightPt) = remember(
        isPptx,
        prefs.standardPageFormat,
        parsed.nativePageWidthPt,
        parsed.nativePageHeightPt
    ) {
        if (isPptx) {
            // PPTX always uses the exact native slide dimensions embedded in the presentation file
            parsed.nativePageWidthPt to parsed.nativePageHeightPt
        } else {
            prefs.standardPageFormat.resolveSizePt(parsed.nativePageWidthPt, parsed.nativePageHeightPt)
        }
    }
    val sheetAspectRatio = (resolvedWidthPt / resolvedHeightPt.coerceAtLeast(1f)).coerceIn(0.45f, 2.25f)
    val activeFormatLabel = remember(isPptx, prefs.standardPageFormat, parsed.detectedFormatLabel) {
        if (isPptx || prefs.standardPageFormat == StandardPageFormat.AUTO) {
            parsed.detectedFormatLabel
        } else {
            prefs.standardPageFormat.shortLabel
        }
    }

    val sheets = remember(parsed, resolvedWidthPt, resolvedHeightPt, isPptx) {
        if (isPptx && parsed.slides.isNotEmpty()) {
            parsed.slides.mapIndexed { idx, slide ->
                StandardDocumentSheet(
                    sheetIndex = idx,
                    chapterIndex = idx,
                    locationKey = "pptx-slide-$idx",
                    headerLabel = "Diapositiva ${idx + 1} / ${parsed.slides.size}",
                    slide = slide
                )
            }
        } else {
            paginateDocumentForPhysicalSheet(
                parsed = parsed,
                pageWidthPt = resolvedWidthPt,
                pageHeightPt = resolvedHeightPt
            )
        }
    }
    val safeSheetCount = sheets.size.coerceAtLeast(1)

    val initialSheetIdx = remember(sheets, currentUnitIndex) {
        sheets.indexOfFirst { it.chapterIndex == currentUnitIndex }
            .takeIf { it >= 0 }
            ?: currentUnitIndex.coerceIn(0, safeSheetCount - 1)
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showSpeakerNotes by remember { mutableStateOf(false) }

    val isZoomed by remember { derivedStateOf { zoomScale > 1.02f } }

    val canInteractWithCanvas = isDrawingMode && !isPanZoomWhileDrawing
    val canPanOrZoom = !canInteractWithCanvas

    val hasDynamicIsland = prefs.menuStyle == ReaderMenuStyle.DYNAMIC_ISLAND
    val topBarPaddingDp = if (hasDynamicIsland) (48.dp * prefs.dockScale.coerceIn(0.88f, 1.25f)) else 12.dp
    val deskBackground = remember(palette.background, palette.surfaceAlt, palette.isDark) {
        if (palette.isDark) {
            lerp(palette.background, Color.Black, 0.35f)
        } else {
            lerp(palette.surfaceAlt, Color(0xFFDCE2EC), 0.42f)
        }
    }

    var activeSheetIndex by remember { mutableIntStateOf(initialSheetIdx) }
    val activeSheet = sheets.getOrNull(activeSheetIndex.coerceIn(0, safeSheetCount - 1))
    val latestOnActiveSheetChanged by rememberUpdatedState(onActiveSheetChanged)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(deskBackground)
            .testTag("standard_page_reader_root")
    ) {
        val isTabletViewport = maxWidth >= 680.dp

        if (prefs.pageAnimation == PageAnimationMode.VERTICAL_SCROLL) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialSheetIdx)
            LaunchedEffect(jumpToUnitTrigger) {
                if (jumpToUnitTrigger > 0) {
                    val targetSheet = sheets.indexOfFirst { it.chapterIndex == currentUnitIndex }
                        .takeIf { it >= 0 } ?: currentUnitIndex.coerceIn(0, safeSheetCount - 1)
                    listState.scrollToItem(targetSheet)
                }
            }
            // Observe scroll index via snapshotFlow instead of reading firstVisibleItemIndex in composition scope
            LaunchedEffect(listState, sheets) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { rawIdx ->
                        val idx = rawIdx.coerceIn(0, safeSheetCount - 1)
                        activeSheetIndex = idx
                        val sheet = sheets.getOrNull(idx) ?: return@collect
                        latestOnActiveSheetChanged(idx, safeSheetCount, sheet.locationKey, sheet.chapterIndex)
                    }
            }

            val verticalCardMaxW = if (sheetAspectRatio < 0.95f) 600.dp else 920.dp
            LazyColumn(
                state = listState,
                userScrollEnabled = !canInteractWithCanvas && !isZoomed,
                contentPadding = PaddingValues(
                    top = topBarPaddingDp + 54.dp,
                    bottom = if (isDrawingMode && !isTabletViewport) 156.dp else 80.dp,
                    start = 14.dp,
                    end = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                items(
                    count = safeSheetCount,
                    key = { idx -> sheets[idx].locationKey },
                    contentType = { if (isPptx) "pptx_slide" else "std_sheet" }
                ) { idx ->
                    val sheet = sheets[idx]
                    StandardInteractiveSheetFrame(
                        sheet = sheet,
                        isPptx = isPptx,
                        aspectRatio = sheetAspectRatio,
                        pageWidthPt = resolvedWidthPt,
                        pageHeightPt = resolvedHeightPt,
                        formatBadge = activeFormatLabel,
                        prefs = prefs,
                        strokes = drawingsByLocation[sheet.locationKey].orEmpty(),
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        canDraw = canInteractWithCanvas,
                        canPanZoom = canPanOrZoom,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        activeAlpha = activeAlpha,
                        activeShapeFilled = activeShapeFilled,
                        onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                        onStrokeCompleted = { onStrokeCompleted(sheet.locationKey, it) },
                        onEraseStrokes = { onEraseStrokes(sheet.locationKey, it) },
                        onTapAtXFraction = { onTapPageCenter() },
                        preferWidthBound = true,
                        modifier = Modifier
                            .widthIn(max = verticalCardMaxW)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(initialPage = initialSheetIdx) { safeSheetCount }

            LaunchedEffect(jumpToUnitTrigger) {
                if (jumpToUnitTrigger > 0) {
                    val targetSheet = sheets.indexOfFirst { it.chapterIndex == currentUnitIndex }
                        .takeIf { it >= 0 } ?: currentUnitIndex.coerceIn(0, safeSheetCount - 1)
                    pagerState.animateScrollToPage(targetSheet, animationSpec = motion.emphasizedTween(290))
                }
            }

            // Observe settled/current page via snapshotFlow so paging doesn't recompose the whole reader tree
            LaunchedEffect(pagerState, sheets) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { rawIdx ->
                        zoomScale = 1f
                        panOffset = Offset.Zero
                        val idx = rawIdx.coerceIn(0, safeSheetCount - 1)
                        activeSheetIndex = idx
                        val sheet = sheets.getOrNull(idx) ?: return@collect
                        latestOnActiveSheetChanged(idx, safeSheetCount, sheet.locationKey, sheet.chapterIndex)
                    }
            }

            val handleTapX: (Float) -> Unit = remember(pagerState, safeSheetCount, prefs.tapZonesEnabled, isDrawingMode, motion) {
                { xFrac: Float ->
                    if (zoomScale > 1.05f || !prefs.tapZonesEnabled || isDrawingMode) {
                        onTapPageCenter()
                    } else {
                        when {
                            xFrac < 0.24f && pagerState.currentPage > 0 -> {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage - 1,
                                        animationSpec = motion.emphasizedTween(280)
                                    )
                                }
                                Unit
                            }
                            xFrac > 0.76f && pagerState.currentPage < safeSheetCount - 1 -> {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + 1,
                                        animationSpec = motion.emphasizedTween(280)
                                    )
                                }
                                Unit
                            }
                            else -> onTapPageCenter()
                        }
                    }
                }
            }

            // Constant inset padding ensures Immersive Mode NEVER shifts the physical sheet by even 1px!
            HorizontalPager(
                state = pagerState,
                key = { idx -> sheets[idx].locationKey },
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = motion.glideSpring()
                ),
                userScrollEnabled = !canInteractWithCanvas && !isZoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(
                        top = topBarPaddingDp + 44.dp,
                        bottom = 44.dp
                    )
            ) { idx ->
                val sheet = sheets[idx]
                val sheetZoom = if (idx == pagerState.currentPage) zoomScale else 1f
                val sheetPan = if (idx == pagerState.currentPage) panOffset else Offset.Zero
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Only scroll vertically on compact-height phone landscape (< 480.dp);
                    // on tablets, render the true physical paper sheet (e.g. A4) fitted cleanly and proportionally!
                    val isPhoneLandscapeWithPortraitSheet = !isPptx &&
                        maxWidth > maxHeight &&
                        maxHeight < 480.dp &&
                        sheetAspectRatio < 0.95f
                    if (isPhoneLandscapeWithPortraitSheet) {
                        val viewportH = maxHeight
                        val pageScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    state = pageScrollState,
                                    enabled = !canInteractWithCanvas && !isZoomed
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = viewportH)
                                    .padding(bottom = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StandardInteractiveSheetFrame(
                                    sheet = sheet,
                                    isPptx = isPptx,
                                    aspectRatio = sheetAspectRatio,
                                    pageWidthPt = resolvedWidthPt,
                                    pageHeightPt = resolvedHeightPt,
                                    formatBadge = activeFormatLabel,
                                    prefs = prefs,
                                    strokes = drawingsByLocation[sheet.locationKey].orEmpty(),
                                    zoomScale = sheetZoom,
                                    panOffset = sheetPan,
                                    canDraw = canInteractWithCanvas,
                                    canPanZoom = canPanOrZoom,
                                    activeTool = activeTool,
                                    activeColorArgb = activeColorArgb,
                                    activeWidthNorm = activeWidthNorm,
                                    activeAlpha = activeAlpha,
                                    activeShapeFilled = activeShapeFilled,
                                    onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                                    onStrokeCompleted = { onStrokeCompleted(sheet.locationKey, it) },
                                    onEraseStrokes = { onEraseStrokes(sheet.locationKey, it) },
                                    onTapAtXFraction = handleTapX,
                                    preferWidthBound = true,
                                    modifier = Modifier
                                        .widthIn(max = 560.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        StandardInteractiveSheetFrame(
                            sheet = sheet,
                            isPptx = isPptx,
                            aspectRatio = sheetAspectRatio,
                            pageWidthPt = resolvedWidthPt,
                            pageHeightPt = resolvedHeightPt,
                            formatBadge = activeFormatLabel,
                            prefs = prefs,
                            strokes = drawingsByLocation[sheet.locationKey].orEmpty(),
                            zoomScale = sheetZoom,
                            panOffset = sheetPan,
                            canDraw = canInteractWithCanvas,
                            canPanZoom = canPanOrZoom,
                            activeTool = activeTool,
                            activeColorArgb = activeColorArgb,
                            activeWidthNorm = activeWidthNorm,
                            activeAlpha = activeAlpha,
                            activeShapeFilled = activeShapeFilled,
                            onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                            onStrokeCompleted = { onStrokeCompleted(sheet.locationKey, it) },
                            onEraseStrokes = { onEraseStrokes(sheet.locationKey, it) },
                            onTapAtXFraction = handleTapX,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Top Floating Bar: Two distinct side-by-side pill bubbles (Format Selector + Battery) with ZERO overlap
        if (chromeAlpha > 0.02f) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(top = topBarPaddingDp, start = 12.dp, end = 12.dp)
                .graphicsLayer { alpha = chromeAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // Bubble 1: Scrollable Format Selector Strip (Auto, A4, Carta, Oficio, A3, A5, 16:9, 4:3 + EPUB switch)
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .shadow(8.dp, StdPillShape, ambientColor = Color.Black.copy(alpha = 0.22f))
                    .clip(StdPillShape)
                    .background(palette.surface.copy(alpha = 0.96f))
                    .border(1.dp, palette.border, StdPillShape)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("standard_page_format_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            Row(
                modifier = Modifier
                    .clip(StdPillShape)
                    .background(palette.accentSoft)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isPptx) Icons.Rounded.Slideshow else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isPptx) "PPTX · $activeFormatLabel" else "Hoja · $activeFormatLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent
                )
            }

            // PPTX automatically reads native slide dimensions without manual aspect chips;
            // DOCX/TXT/FB2 retain physical paper format selectors (Auto, A4, Carta, Oficio, A3, A5).
            if (!isPptx) {
                val selectableFormats = remember {
                    listOf(
                        StandardPageFormat.AUTO,
                        StandardPageFormat.A4,
                        StandardPageFormat.LETTER,
                        StandardPageFormat.LEGAL,
                        StandardPageFormat.A3,
                        StandardPageFormat.A5
                    )
                }

                for (fmt in selectableFormats) {
                    val isSelected = prefs.standardPageFormat == fmt
                    Box(
                        modifier = Modifier
                            .clip(StdPillShape)
                            .background(if (isSelected) palette.accent else Color.Transparent)
                            .clickable {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                onSelectStandardFormat(fmt)
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("std_format_chip_${fmt.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fmt.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else palette.inkMuted
                        )
                    }
                }
            }

            // Quick 1-tap toggle back to Adapted EPUB mode
            Row(
                modifier = Modifier
                    .clip(StdPillShape)
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, StdPillShape)
                    .clickable(onClick = onToggleStandardView)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("std_bar_switch_adapted"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = "Cambiar a modo adaptado EPUB",
                    tint = palette.ink,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Modo EPUB",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink
                )
            }
            }

            // Bubble 2: Separate Battery Pill sitting cleanly alongside the Format Selector
            if (showBatteryBubble) {
                StandardTopBarBatteryBubbleHost()
            }
        }
        }

        // Speaker Notes Pill (PPTX only when slide has notes) & Zoom Preview Controls (− / % / +)
        val currentSlideNotes = activeSheet?.slide?.notes?.takeIf { it.isNotBlank() }
        if (chromeAlpha > 0.02f) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = if (isDrawingMode && !isTabletViewport) 152.dp else 40.dp,
                    start = 16.dp,
                    end = 16.dp
                )
                .graphicsLayer { alpha = chromeAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = showSpeakerNotes && !currentSlideNotes.isNullOrBlank(),
                enter = fadeIn(motion.tweenSpec(190)),
                exit = fadeOut(motion.exitTween(155))
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .shadow(12.dp, StdNotesCardShape)
                        .clip(StdNotesCardShape)
                        .background(palette.surface.copy(alpha = 0.97f))
                        .border(1.dp, palette.border, StdNotesCardShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "NOTAS DEL ORADOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = currentSlideNotes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isZoomModified = abs(zoomScale - 1f) > 0.03f
                Row(
                    modifier = Modifier
                        .shadow(6.dp, StdPillShape)
                        .clip(StdPillShape)
                        .background(palette.surface.copy(alpha = 0.94f))
                        .border(1.dp, if (isZoomModified) palette.accent.copy(alpha = 0.55f) else palette.border, StdPillShape)
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .testTag("std_zoom_control_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(StdPillShape)
                            .clickable(enabled = zoomScale > 0.46f) {
                                val next = (zoomScale - 0.15f).coerceIn(0.45f, 4.5f)
                                zoomScale = next
                                if (next <= 1.01f) panOffset = Offset.Zero
                            }
                            .testTag("std_zoom_out_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Alejar página",
                            tint = if (zoomScale > 0.46f) palette.ink else palette.inkMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(StdPillShape)
                            .background(if (isZoomModified) palette.accentSoft else Color.Transparent)
                            .clickable {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("std_zoom_reset_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isZoomModified) {
                            Icon(
                                imageVector = Icons.Rounded.FitScreen,
                                contentDescription = "Restablecer zoom al 100%",
                                tint = palette.accent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isZoomModified) palette.accent else palette.ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(StdPillShape)
                            .clickable(enabled = zoomScale < 4.45f) {
                                zoomScale = (zoomScale + 0.25f).coerceIn(0.45f, 4.5f)
                            }
                            .testTag("std_zoom_in_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Acercar página",
                            tint = if (zoomScale < 4.45f) palette.ink else palette.inkMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!currentSlideNotes.isNullOrBlank() && !isDrawingMode) {
                    Row(
                        modifier = Modifier
                            .shadow(6.dp, StdPillShape)
                            .clip(StdPillShape)
                            .background(if (showSpeakerNotes) palette.accent else palette.surface.copy(alpha = 0.96f))
                            .border(1.dp, if (showSpeakerNotes) palette.accent else palette.border, StdPillShape)
                            .clickable { showSpeakerNotes = !showSpeakerNotes }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("pptx_speaker_notes_pill"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notes,
                            contentDescription = "Notas del orador",
                            tint = if (showSpeakerNotes) Color.White else palette.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (showSpeakerNotes) "Ocultar notas" else "Notas",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (showSpeakerNotes) Color.White else palette.ink
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StandardTopBarBatteryBubbleHost() {
    val motion = LocalLumaMotion.current
    val activeIslandEvent by DynamicIslandEventManager.activeEvent.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = activeIslandEvent == null,
        enter = fadeIn(motion.tweenSpec(180)),
        exit = fadeOut(motion.exitTween(140))
    ) {
        ReaderBatteryPillBubble(
            modifier = Modifier.testTag("standard_page_battery_bubble")
        )
    }
}

@Composable
private fun StandardInteractiveSheetFrame(
    sheet: StandardDocumentSheet,
    isPptx: Boolean,
    aspectRatio: Float,
    pageWidthPt: Float,
    pageHeightPt: Float,
    formatBadge: String,
    prefs: ReaderPreferences,
    strokes: List<DrawingStroke>,
    zoomScale: Float,
    panOffset: Offset,
    canDraw: Boolean,
    canPanZoom: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float = 1f,
    activeShapeFilled: Boolean = false,
    onZoomPanChange: (Float, Offset) -> Unit,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEraseStrokes: (Set<String>) -> Unit,
    onTapAtXFraction: (Float) -> Unit,
    preferWidthBound: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val density = LocalDensity.current
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestPan by rememberUpdatedState(panOffset)
    val latestOnZoomPan by rememberUpdatedState(onZoomPanChange)
    val latestOnTapX by rememberUpdatedState(onTapAtXFraction)

    // Physical paper surface: crisp white paper for light/pastel themes, low-glare studio paper for dark themes
    val paperBg = if (palette.isDark) Color(0xFF1A202D) else Color(0xFFFFFFFF)
    val paperInk = if (palette.isDark) Color(0xFFEDF2FA) else Color(0xFF141B29)
    val paperMuted = if (palette.isDark) Color(0xFF9AA7BD) else Color(0xFF5B677C)
    val paperBorder = if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFCFD8E6)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = maxHeight
        // Compute exact fitted physical sheet dimensions preserving true aspectRatio without clipping
        val useHeightBound = !preferWidthBound && availH.value.isFinite() && availH.value > 0f && (availW.value / availH.value) > aspectRatio
        val sheetBaseWpx = with(density) {
            if (useHeightBound) (availH * aspectRatio).toPx() else availW.toPx()
        }.coerceAtLeast(120f)
        val sheetBaseHpx = with(density) {
            if (useHeightBound) availH.toPx() else (availW / aspectRatio).toPx()
        }.coerceAtLeast(120f)
        val latestSheetW by rememberUpdatedState(sheetBaseWpx)
        val latestSheetH by rememberUpdatedState(sheetBaseHpx)

        val sheetSizeMod = if (useHeightBound) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
        }

        // Untransformed gesture host wraps the transformed sheet so pinch centroid & pan are
        // measured in exact screen/viewport coordinates -> true focal zoom right under the fingers!
        Box(
            modifier = Modifier
                .then(if (useHeightBound) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .then(
                    if (canPanZoom) {
                        Modifier
                            .pointerInput(sheet.locationKey, canPanZoom) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedCount = event.changes.count { it.pressed }
                                        if (pressedCount >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val centroid = event.calculateCentroid(useCurrent = true)
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                val oldZoom = latestZoom
                                                val newZoom = (oldZoom * zoomChange).coerceIn(0.45f, 4.5f)
                                                if (newZoom <= 1.01f) {
                                                    latestOnZoomPan(newZoom, Offset.Zero)
                                                } else {
                                                    val center = Offset(size.width / 2f, size.height / 2f)
                                                    val focal = centroid - center
                                                    val ratio = newZoom / oldZoom
                                                    val rawPan = (latestPan - focal) * ratio + focal + panChange
                                                    val maxX = maxOf(latestSheetW * (newZoom - 1f) * 0.56f, 0f)
                                                    val maxY = maxOf(latestSheetH * (newZoom - 1f) * 0.56f, 0f)
                                                    latestOnZoomPan(
                                                        newZoom,
                                                        Offset(
                                                            rawPan.x.coerceIn(-maxX, maxX),
                                                            rawPan.y.coerceIn(-maxY, maxY)
                                                        )
                                                    )
                                                }
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        } else if (pressedCount == 1 && latestZoom > 1.02f) {
                                            val panChange = event.calculatePan()
                                            if (panChange != Offset.Zero) {
                                                val maxX = maxOf(latestSheetW * (latestZoom - 1f) * 0.56f, 0f)
                                                val maxY = maxOf(latestSheetH * (latestZoom - 1f) * 0.56f, 0f)
                                                val nextX = (latestPan.x + panChange.x).coerceIn(-maxX, maxX)
                                                val nextY = (latestPan.y + panChange.y).coerceIn(-maxY, maxY)
                                                val atHorizontalEdge = abs(nextX - latestPan.x) < 0.5f &&
                                                    abs(panChange.x) > abs(panChange.y)
                                                latestOnZoomPan(latestZoom, Offset(nextX, nextY))
                                                if (!atHorizontalEdge) {
                                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(sheet.locationKey, canPanZoom) {
                                // Omitting onDoubleTap removes Android's ~300 ms wait so single taps fire in 0 ms!
                                detectTapGestures(
                                    onTap = { tapPos ->
                                        val nx = (tapPos.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        latestOnTapX(nx)
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = sheetSizeMod
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .shadow(
                        elevation = if (palette.isDark) 14.dp else 10.dp,
                        shape = StdPaperShape,
                        ambientColor = Color.Black.copy(alpha = 0.24f),
                        spotColor = Color.Black.copy(alpha = 0.20f)
                    )
                    .clip(StdPaperShape)
                    .background(paperBg)
                    .border(1.dp, paperBorder, StdPaperShape)
            ) {
                val sheetW = maxWidth.value.coerceAtLeast(120f)
                val sheetH = maxHeight.value.coerceAtLeast(120f)
                // Scale factor translating physical PDF points (e.g. 595pt A4 or 842pt A3) to rendered dp
                val ptScale = (sheetW / pageWidthPt.coerceAtLeast(240f)).coerceIn(0.28f, 1.35f)
                val fontFamily = prefs.font.toFontFamily()

                if (isPptx && sheet.slide != null) {
                    PptxSlideSurface(
                        slide = sheet.slide,
                        headerLabel = sheet.headerLabel,
                        formatBadge = formatBadge,
                        ptScale = ptScale,
                        isLandscape = sheetW >= sheetH,
                        paperInk = paperInk,
                        paperMuted = paperMuted,
                        accent = palette.accent,
                        fontFamily = fontFamily
                    )
                } else {
                    PhysicalDocumentSheetSurface(
                        sheet = sheet,
                        formatBadge = formatBadge,
                        ptScale = ptScale,
                        paperInk = paperInk,
                        paperMuted = paperMuted,
                        paperBorder = paperBorder,
                        accent = palette.accent,
                        fontFamily = fontFamily,
                        textAlign = if (prefs.textAlign == TextAlignOption.JUSTIFY) {
                            TextAlign.Justify
                        } else {
                            TextAlign.Start
                        }
                    )
                }

                // Pixel-locked persistent Drawing / Signature Canvas Layer directly over the physical sheet!
                DrawingCanvasOverlay(
                    strokes = strokes,
                    isInteractive = canDraw,
                    activeTool = activeTool,
                    activeColorArgb = activeColorArgb,
                    activeWidthNorm = activeWidthNorm,
                    activeAlpha = activeAlpha,
                    activeShapeFilled = activeShapeFilled,
                    onStrokeCompleted = onStrokeCompleted,
                    onEraseStrokeIds = onEraseStrokes,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Deterministically paginates any text document (DOCX, EPUB, TXT, FB2) into physical sheets
 * matching real paper dimensions (A4, Carta, Oficio, A3, A5, or native file size).
 */
private fun paginateDocumentForPhysicalSheet(
    parsed: ParsedDocument,
    pageWidthPt: Float,
    pageHeightPt: Float
): List<StandardDocumentSheet> {
    val usableW = (pageWidthPt - 104f).coerceAtLeast(220f)
    val usableH = (pageHeightPt - 118f).coerceAtLeast(260f)
    val charsPerLine = (usableW / 5.75f).toInt().coerceIn(38, 128)
    val maxLinesPerSheet = (usableH / 16.2f).toInt().coerceIn(18, 72)

    val result = mutableListOf<StandardDocumentSheet>()
    var currentBlocks = mutableListOf<ParsedBlock>()
    var currentChapterIdx = 0
    var usedLines = 0

    fun flushSheet() {
        if (currentBlocks.isEmpty()) return
        val idx = result.size
        result.add(
            StandardDocumentSheet(
                sheetIndex = idx,
                chapterIndex = currentChapterIdx,
                locationKey = "std-page-$idx",
                headerLabel = "Hoja ${idx + 1}",
                blocks = currentBlocks.toList()
            )
        )
        currentBlocks = mutableListOf()
        usedLines = 0
    }

    for (chapter in parsed.chapters) {
        if (currentBlocks.isNotEmpty() && usedLines > maxLinesPerSheet * 0.55f) {
            flushSheet()
        }
        currentChapterIdx = chapter.index
        for (block in chapter.blocks) {
            val weightMult = when (block.type) {
                BlockType.HEADING_1 -> 1.9f
                BlockType.HEADING_2 -> 1.45f
                else -> 1f
            }
            val blockLines = ceil(block.text.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
            val weighted = (blockLines * weightMult).toInt().coerceAtLeast(1) + 1

            if (usedLines + weighted > maxLinesPerSheet && currentBlocks.isNotEmpty()) {
                flushSheet()
                currentChapterIdx = chapter.index
            }

            if (weighted > maxLinesPerSheet) {
                // Split very long paragraphs neatly across consecutive physical sheets
                val maxChars = (charsPerLine * (maxLinesPerSheet - 3)).coerceAtLeast(240)
                var offset = 0
                while (offset < block.text.length) {
                    val remaining = block.text.length - offset
                    val end = if (remaining <= maxChars) {
                        block.text.length
                    } else {
                        val candidate = offset + maxChars
                        val spaceBack = block.text.lastIndexOf(' ', candidate)
                        if (spaceBack > offset + maxChars / 2) spaceBack else candidate
                    }
                    val slice = block.text.substring(offset, end).trim()
                    if (slice.isNotEmpty()) {
                        currentBlocks.add(block.copy(text = slice))
                        flushSheet()
                        currentChapterIdx = chapter.index
                    }
                    offset = end
                }
            } else {
                currentBlocks.add(block)
                usedLines += weighted
            }
        }
    }
    flushSheet()

    if (result.isEmpty()) {
        result.add(
            StandardDocumentSheet(
                sheetIndex = 0,
                chapterIndex = 0,
                locationKey = "std-page-0",
                headerLabel = "Hoja 1",
                blocks = listOf(ParsedBlock(0, BlockType.PARAGRAPH, parsed.title))
            )
        )
    }

    val total = result.size
    return result.map { it.copy(headerLabel = "Hoja ${it.sheetIndex + 1} / $total") }
}

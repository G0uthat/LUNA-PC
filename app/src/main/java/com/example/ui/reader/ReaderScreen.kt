package com.example.ui.reader

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.HighlightNoteEntity
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.ExportFormatOption
import com.example.data.model.ParsedBlock
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.StandardPageFormat
import com.example.engine.DynamicIslandEventManager
import com.example.engine.MergedDocumentExporter
import com.example.engine.SocialRepository
import com.example.engine.SystemBrightnessController
import com.example.engine.SystemMediaController
import com.example.ui.components.AnnotationsAndBookmarksSheet
import com.example.ui.components.AppSettingsSheet
import com.example.ui.components.AppearanceSettingsSheet
import com.example.ui.components.BookmarkDockOverlay
import com.example.ui.components.ContextualDictionaryPopover
import com.example.ui.components.DrawingFloatingToolbar
import com.example.ui.components.InBookSearchSheet
import com.example.ui.components.LumaActionFeedbackOverlay
import com.example.ui.components.MergedExportDialog
import com.example.ui.components.NoteEditorDialog
import com.example.ui.components.ReaderBatteryOnlyIndicator
import com.example.ui.components.TactileButton
import com.example.ui.components.TocBottomSheet
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackToLibrary: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val context = LocalContext.current
    val view = LocalView.current

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val drawingsByLocation by viewModel.drawingsByLocation.collectAsStateWithLifecycle()
    val ocrByPage by viewModel.ocrByPage.collectAsStateWithLifecycle()
    val dictSources by viewModel.dictionarySources.collectAsStateWithLifecycle()
    val socialRepo = remember(context) { SocialRepository.getInstance(context) }

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var editingHighlight by remember { mutableStateOf<HighlightNoteEntity?>(null) }
    var pendingExportOption by remember { mutableStateOf(ExportFormatOption.NATIVE_MERGED) }
    var showTempProgressOverlay by remember { mutableStateOf(false) }
    var lastChromeActivityTick by remember { mutableIntStateOf(0) }
    var isChromeAutoHidden by remember { mutableStateOf(false) }

    val wakeReaderChrome: () -> Unit = {
        isChromeAutoHidden = false
        lastChromeActivityTick++
    }

    val activity = context as? Activity

    // Hide Android system status bar icons during reading (shows ONLY battery indicator + Dynamic Island alerts)
    DisposableEffect(view) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
            socialRepo.clearReadingPresence()
            SystemBrightnessController.clearWindowOverride(activity)
        }
    }

    // SAF CreateDocument launcher for merged export (Zero storage permissions)
    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.exportMergedToUri(uri, pendingExportOption) {
                showExportDialog = false
            }
        }
    }

    // Hardware/gesture back handling
    BackHandler {
        when {
            state.selectedWord != null -> viewModel.dismissWordPopover()
            state.isDockExpanded -> viewModel.toggleDock(false)
            state.isDrawingMode -> viewModel.toggleDrawingMode(false)
            isChromeAutoHidden -> wakeReaderChrome()
            else -> onBackToLibrary()
        }
    }

    val anyOverlayOpenForAutoHide = state.isDrawingMode ||
        state.isDockExpanded ||
        state.selectedWord != null ||
        showAppearanceSheet ||
        showTocSheet ||
        showAnnotationsSheet ||
        showSearchSheet ||
        showSettingsSheet ||
        showExportDialog ||
        showNewNoteDialog ||
        editingHighlight != null

    // Auto-hide all floating reader chrome into distraction-free text-only mode after user-configured inactivity
    LaunchedEffect(
        lastChromeActivityTick,
        prefs.autoHideReaderChromeEnabled,
        prefs.autoHideReaderChromeDelaySec,
        anyOverlayOpenForAutoHide
    ) {
        if (!prefs.autoHideReaderChromeEnabled || anyOverlayOpenForAutoHide) {
            isChromeAutoHidden = false
            return@LaunchedEffect
        }
        delay(prefs.autoHideReaderChromeDelaySec.coerceIn(3, 60) * 1000L)
        isChromeAutoHidden = true
    }

    val readerChromeAlpha by animateFloatAsState(
        targetValue = if (isChromeAutoHidden) 0f else 1f,
        animationSpec = tween(
            durationMillis = prefs.autoHideFadeSpeedMs.coerceIn(80, 600),
            easing = FastOutSlowInEasing
        ),
        label = "reader_immersive_chrome_alpha"
    )

    val handleTapCenter: () -> Unit = {
        if (isChromeAutoHidden) {
            // Clicking anywhere while immersed immediately restores all controls with the fast fade-in
            wakeReaderChrome()
        } else {
            lastChromeActivityTick++
            if (prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR) {
                viewModel.toggleDock()
            } else {
                showTempProgressOverlay = !showTempProgressOverlay
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("reader_screen_root")
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = palette.accent)
                    Text(
                        text = "Preparando documento…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted
                    )
                }
            }

            state.error != null || state.parsed == null || state.document == null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = state.error ?: "Error al cargar el documento",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink
                    )
                    TactileButton(onClick = onBackToLibrary) {
                        Text("Volver a Biblioteca")
                    }
                }
            }

            else -> {
                val doc = state.document!!
                val parsed = state.parsed!!
                val isEpub = parsed.format == DocumentFormat.EPUB
                val isPdf = parsed.format == DocumentFormat.PDF
                val isPptx = parsed.format == DocumentFormat.PPTX
                val showOriginalView = !isEpub && state.viewAsOriginalFormat
                val isShowingPdfNative = isPdf && showOriginalView
                val isShowingStandardSheet = !isPdf && showOriginalView

                val resolvedFormatLabel = if (prefs.standardPageFormat == StandardPageFormat.AUTO) {
                    parsed.detectedFormatLabel
                } else {
                    prefs.standardPageFormat.shortLabel
                }
                val totalUnits = (if (isShowingPdfNative) parsed.pdfPageCount else state.totalSheetCount).coerceAtLeast(1)
                val currentDisplayIndex = if (isShowingPdfNative) state.currentUnitIndex else state.currentSheetIndex
                val progress = ((currentDisplayIndex + 1).toFloat() / totalUnits.toFloat()).coerceIn(0f, 1f)
                val remainingWords = (parsed.totalWords * (1f - progress)).roundToInt().coerceAtLeast(0)
                val minsLeft = max(1, remainingWords / 210)
                val progressSummary = "${(progress * 100).roundToInt()}% · ~$minsLeft min restantes"
                val currentLocKey = viewModel.currentLocationKey()
                val isBookmarkedHere by remember(bookmarks, currentLocKey) {
                    derivedStateOf { bookmarks.any { it.locationKey == currentLocKey } }
                }

                // Enrich PDF adapted chapters with ML Kit OCR text on scanned/manhwa pages when available
                val effectiveChapters = remember(parsed, ocrByPage, isPdf, state.showTranslatedManhwaOverlay) {
                    if (!isPdf) {
                        parsed.chapters
                    } else {
                        parsed.chapters.map { ch ->
                            val nativeText = parsed.pdfPageTexts.getOrNull(ch.index).orEmpty()
                            val ocrPage = ocrByPage[ch.index]
                            if (nativeText.isBlank() && ocrPage != null && ocrPage.blocks.isNotEmpty()) {
                                val ocrBlocks = buildList {
                                    add(ParsedBlock(0, BlockType.HEADING_2, "Página ${ch.index + 1} (OCR)"))
                                    ocrPage.blocks.forEach { ob ->
                                        val txt = if (state.showTranslatedManhwaOverlay && !ob.translatedText.isNullOrBlank()) {
                                            ob.translatedText
                                        } else {
                                            ob.originalText
                                        }.trim()
                                        if (txt.isNotEmpty()) {
                                            add(ParsedBlock(size, BlockType.PARAGRAPH, txt))
                                        }
                                    }
                                }
                                ch.copy(blocks = ocrBlocks)
                            } else {
                                ch
                            }
                        }
                    }
                }

                // Share live reading status with connected P2P friends (respects privacy toggles)
                val progressPctInt = (progress * 100).roundToInt()
                LaunchedEffect(parsed.title, progressPctInt) {
                    socialRepo.updateReadingPresence(parsed.title, progressPctInt)
                }

                val anyReaderMenuOpen by remember(
                    state.isDockExpanded,
                    showAppearanceSheet,
                    showTocSheet,
                    showAnnotationsSheet,
                    showSearchSheet,
                    showSettingsSheet,
                    showExportDialog,
                    showNewNoteDialog,
                    editingHighlight
                ) {
                    derivedStateOf {
                        state.isDockExpanded || showAppearanceSheet || showTocSheet ||
                            showAnnotationsSheet || showSearchSheet || showSettingsSheet ||
                            showExportDialog || showNewNoteDialog || editingHighlight != null
                    }
                }

                // 1. Distraction-Free Reading Surface (NEVER wrapped in animated Modifier.blur — 0 full-screen blur passes!)
                when {
                    isShowingPdfNative -> {
                        PdfManhwaReader(
                            filePath = doc.filePath,
                            pageCount = parsed.pdfPageCount,
                            currentPageIndex = state.currentUnitIndex,
                            prefs = prefs,
                            drawingsByLocation = drawingsByLocation,
                            ocrByPage = ocrByPage,
                            showTranslatedOverlay = state.showTranslatedManhwaOverlay,
                            isProcessingOcrOrTranslation = state.isProcessingOcrOrTranslation,
                            processingMessage = state.ocrTranslationStatus,
                            isDockExpanded = state.isDockExpanded,
                            isDrawingMode = state.isDrawingMode,
                            isPanZoomWhileDrawing = state.isPanZoomWhileDrawing,
                            detectedFormatLabel = parsed.detectedFormatLabel,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            activeWidthNorm = state.activeWidthNorm,
                            activeAlpha = state.activeAlpha,
                            activeShapeFilled = state.activeShapeFilled,
                            onPageChanged = { idx -> viewModel.onUnitChanged(idx) },
                            onPageBitmapReady = viewModel::onPdfPageBitmapReady,
                            onWordSelected = viewModel::onWordSelected,
                            onStrokeCompleted = viewModel::addStroke,
                            onEraseStrokes = viewModel::eraseStrokes,
                            onToggleTranslateOverlay = {
                                wakeReaderChrome()
                                viewModel.toggleOrRunManhwaPageTranslation()
                            },
                            onTapPageCenter = handleTapCenter,
                            chromeAlpha = readerChromeAlpha,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    isShowingStandardSheet -> {
                        StandardPageDocumentReader(
                            parsed = parsed,
                            currentUnitIndex = state.currentUnitIndex,
                            jumpToUnitTrigger = state.jumpToChapterTrigger,
                            prefs = prefs,
                            drawingsByLocation = drawingsByLocation,
                            isDrawingMode = state.isDrawingMode,
                            isPanZoomWhileDrawing = state.isPanZoomWhileDrawing,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            activeWidthNorm = state.activeWidthNorm,
                            activeAlpha = state.activeAlpha,
                            activeShapeFilled = state.activeShapeFilled,
                            onActiveSheetChanged = viewModel::onFluidSheetChanged,
                            onSelectStandardFormat = { fmt ->
                                wakeReaderChrome()
                                viewModel.updatePreferences { it.copy(standardPageFormat = fmt) }
                            },
                            onToggleStandardView = {
                                wakeReaderChrome()
                                viewModel.toggleStandardPageViewForCurrentDoc()
                            },
                            onStrokeCompleted = viewModel::addStroke,
                            onEraseStrokes = viewModel::eraseStrokes,
                            onTapPageCenter = handleTapCenter,
                            showBatteryBubble = !state.isDrawingMode &&
                                !(prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && state.isDockExpanded),
                            chromeAlpha = readerChromeAlpha,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        FluidBookReader(
                            chapters = effectiveChapters,
                            currentChapterIndex = state.currentUnitIndex,
                            jumpToChapterTrigger = state.jumpToChapterTrigger,
                            prefs = prefs,
                            highlights = highlights,
                            drawingsByLocation = drawingsByLocation,
                            isDrawingMode = state.isDrawingMode,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            activeWidthNorm = state.activeWidthNorm,
                            activeAlpha = state.activeAlpha,
                            activeShapeFilled = state.activeShapeFilled,
                            activeSelection = state.selectedWord,
                            onActiveSheetChanged = viewModel::onFluidSheetChanged,
                            onWordSelected = { wordCtx ->
                                wakeReaderChrome()
                                viewModel.onWordSelected(wordCtx)
                            },
                            onStrokeCompleted = viewModel::addStroke,
                            onEraseStrokes = viewModel::eraseStrokes,
                            onTapPageCenter = handleTapCenter,
                            chromeAlpha = readerChromeAlpha,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Draw-phase frosted gradient veil replacing expensive animated Modifier.blur()
                ReaderFrostedMenuScrim(visible = anyReaderMenuOpen)

                // 2. Ultra-minimalist discrete reading progress footer (fades cleanly in immersive mode)
                if (readerChromeAlpha > 0.02f) {
                    AnimatedVisibility(
                        visible = (prefs.showMinimalFooter || showTempProgressOverlay) && !state.isDrawingMode &&
                            !(prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && state.isDockExpanded),
                        enter = fadeIn(motion.tweenSpec(185)),
                        exit = fadeOut(motion.exitTween(150)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .graphicsLayer { alpha = readerChromeAlpha }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isShowingPdfNative -> "Pág. ${currentDisplayIndex + 1} / $totalUnits · PDF"
                                    isPptx && showOriginalView -> "Diapositiva ${currentDisplayIndex + 1} / $totalUnits"
                                    isShowingStandardSheet -> "Hoja ${currentDisplayIndex + 1} / $totalUnits · $resolvedFormatLabel"
                                    !isEpub -> "Hoja ${currentDisplayIndex + 1} / $totalUnits · Modo EPUB"
                                    else -> "Hoja ${currentDisplayIndex + 1} / $totalUnits"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.inkMuted.copy(alpha = 0.72f)
                            )
                            Text(
                                text = progressSummary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.inkMuted.copy(alpha = 0.72f)
                            )
                        }
                    }

                    // 2.5 Isolated Battery indicator host (fades out cleanly in immersive mode)
                    if (!isShowingStandardSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = readerChromeAlpha }
                        ) {
                            ReaderBatteryStatusHost(
                                isDrawingMode = state.isDrawingMode,
                                isClassicExpanded = prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && state.isDockExpanded
                            )
                        }
                    }

                    // 3. Isolated Dock Host (fades cleanly to text-only during immersive reading)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = readerChromeAlpha }
                    ) {
                        ReaderDockOverlayHost(
                            prefs = prefs,
                            isExpanded = state.isDockExpanded,
                            isBookmarkedHere = isBookmarkedHere,
                            isDrawingMode = state.isDrawingMode,
                            isPdfDocument = isShowingPdfNative,
                            documentFormat = parsed.format,
                            showFormatModeToggle = !isEpub,
                            isOriginalFormatView = showOriginalView,
                            isPageTranslated = ocrByPage[state.currentUnitIndex]?.isTranslated == true && state.showTranslatedManhwaOverlay,
                            isProcessingStatus = state.isProcessingOcrOrTranslation,
                            processingStatusText = state.ocrTranslationStatus,
                            bookTitle = parsed.title,
                            progressLabel = progressSummary,
                            onToggleExpanded = {
                                wakeReaderChrome()
                                viewModel.toggleDock()
                            },
                            onDismiss = {
                                wakeReaderChrome()
                                viewModel.toggleDock(false)
                            },
                            onDockRepositioned = { newSide, newY ->
                                wakeReaderChrome()
                                viewModel.updatePreferences { it.copy(dockSide = newSide, dockYFraction = newY) }
                            },
                            onToggleBookmarkHere = {
                                wakeReaderChrome()
                                viewModel.toggleBookmarkCurrentPage()
                            },
                            onOpenAppearance = {
                                wakeReaderChrome()
                                showAppearanceSheet = true
                            },
                            onOpenToc = {
                                wakeReaderChrome()
                                showTocSheet = true
                            },
                            onToggleDrawing = {
                                wakeReaderChrome()
                                viewModel.toggleDrawingMode()
                            },
                            onSetOriginalFormatView = { orig ->
                                wakeReaderChrome()
                                viewModel.setViewAsOriginalFormat(orig)
                            },
                            onTriggerOcrTranslate = {
                                wakeReaderChrome()
                                viewModel.toggleOrRunManhwaPageTranslation()
                            },
                            onOpenAnnotations = {
                                wakeReaderChrome()
                                showAnnotationsSheet = true
                            },
                            onOpenSearch = {
                                wakeReaderChrome()
                                showSearchSheet = true
                            },
                            onOpenSettings = {
                                wakeReaderChrome()
                                settingsInitialTab = 0
                                showSettingsSheet = true
                            },
                            onOpenExport = {
                                wakeReaderChrome()
                                showExportDialog = true
                            },
                            onBackToLibrary = onBackToLibrary
                        )
                    }
                }

                // 4. Adaptive Floating Drawing Studio Toolbar (translucent side dock on tablets, bottom bar on phones)
                if (state.isDrawingMode) {
                    DrawingFloatingToolbar(
                        activeTool = state.activeTool,
                        activeColorArgb = state.activeColorArgb,
                        activeWidthNorm = state.activeWidthNorm,
                        activeAlpha = state.activeAlpha,
                        activeShapeFilled = state.activeShapeFilled,
                        customColors = state.customDrawingColors,
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        isStandardPageView = showOriginalView,
                        standardFormatLabel = if (isPdf) "PDF" else resolvedFormatLabel,
                        showStandardViewToggle = !isEpub,
                        canTogglePanZoom = showOriginalView,
                        isPanZoomMode = state.isPanZoomWhileDrawing,
                        onSelectTool = viewModel::setDrawingTool,
                        onSelectColor = viewModel::setDrawingColor,
                        onAddCustomColor = viewModel::addCustomDrawingColor,
                        onWidthChange = viewModel::setDrawingWidth,
                        onAlphaChange = viewModel::setDrawingAlpha,
                        onToggleShapeFilled = viewModel::toggleDrawingShapeFilled,
                        onToggleStandardPageView = viewModel::toggleStandardPageViewForCurrentDoc,
                        onTogglePanZoomMode = viewModel::togglePanZoomWhileDrawing,
                        onApplySignaturePreset = viewModel::applySignaturePreset,
                        onUndo = viewModel::undoDrawing,
                        onRedo = viewModel::redoDrawing,
                        onClearPage = viewModel::clearCurrentPageDrawings,
                        onDone = { viewModel.toggleDrawingMode(false) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 5. Dynamic Contextual Dictionary & EN->ES Translation Popover (kept mounted for silky enter/exit)
                ContextualDictionaryPopover(
                    selection = state.selectedWord,
                    sources = dictSources,
                    instantTranslation = state.instantTranslation,
                    isTranslatingWord = state.isTranslatingWord,
                    translationStatusText = state.wordTranslationStatus,
                    wikiSummary = state.wikiSummary,
                    wikiError = state.wikiError,
                    isWikiLoading = state.isWikiLoading,
                    onHighlight = { colorArgb -> viewModel.addHighlightFromSelection(colorArgb) },
                    onAddNote = { showNewNoteDialog = true },
                    onUpdateSelection = viewModel::updateSelection,
                    onOpenDictionarySettings = {
                        settingsInitialTab = 1
                        showSettingsSheet = true
                    },
                    onDismiss = viewModel::dismissWordPopover
                )

                // 6. Modal Bottom Sheets & Dialogs
                if (showAppearanceSheet) {
                    AppearanceSettingsSheet(
                        prefs = prefs,
                        documentFormat = parsed.format,
                        isOriginalFormatView = showOriginalView,
                        onSetOriginalFormatView = viewModel::setViewAsOriginalFormat,
                        onUpdatePrefs = viewModel::updatePreferences,
                        onDismiss = { showAppearanceSheet = false }
                    )
                }

                if (showTocSheet) {
                    TocBottomSheet(
                        toc = parsed.toc,
                        currentIndex = state.currentUnitIndex,
                        onSelectEntry = { idx -> viewModel.onUnitChanged(idx, isExplicitJump = true) },
                        onDismiss = { showTocSheet = false }
                    )
                }

                if (showSearchSheet) {
                    InBookSearchSheet(
                        query = state.searchQuery,
                        matches = state.searchMatches,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSelectMatch = { match -> viewModel.onUnitChanged(match.targetIndex, isExplicitJump = true) },
                        onDismiss = { showSearchSheet = false }
                    )
                }

                if (showAnnotationsSheet) {
                    AnnotationsAndBookmarksSheet(
                        highlights = highlights,
                        bookmarks = bookmarks,
                        onNavigateToUnit = { idx -> viewModel.onUnitChanged(idx, isExplicitJump = true) },
                        onDeleteHighlight = viewModel::deleteHighlight,
                        onEditHighlightNote = { hl -> editingHighlight = hl },
                        onDeleteBookmark = viewModel::deleteBookmark,
                        onDismiss = { showAnnotationsSheet = false }
                    )
                }

                if (showSettingsSheet) {
                    AppSettingsSheet(
                        prefs = prefs,
                        sources = dictSources,
                        initialTab = settingsInitialTab,
                        onUpdatePrefs = viewModel::updatePreferences,
                        onAddSource = viewModel::addDictionarySource,
                        onToggleSource = viewModel::toggleDictionarySource,
                        onDeleteSource = viewModel::deleteDictionarySource,
                        onDismiss = { showSettingsSheet = false }
                    )
                }

                if (showNewNoteDialog && state.selectedWord != null) {
                    NoteEditorDialog(
                        quotedText = state.selectedWord!!.word,
                        initialNote = "",
                        initialColorArgb = 0xFFF6C445L,
                        onSave = { noteText, colorArgb ->
                            viewModel.addHighlightFromSelection(colorArgb, noteText)
                            showNewNoteDialog = false
                        },
                        onDismiss = { showNewNoteDialog = false }
                    )
                }

                editingHighlight?.let { hl ->
                    NoteEditorDialog(
                        quotedText = hl.quotedText,
                        initialNote = hl.noteText,
                        initialColorArgb = hl.colorArgb,
                        onSave = { noteText, colorArgb ->
                            viewModel.updateHighlightNote(hl.id, noteText, colorArgb)
                            editingHighlight = null
                        },
                        onDismiss = { editingHighlight = null }
                    )
                }

                if (showExportDialog) {
                    val strokeCount = remember(drawingsByLocation) {
                        drawingsByLocation.values.sumOf { it.size }
                    }
                    MergedExportDialog(
                        document = doc,
                        drawingStrokeCount = strokeCount,
                        highlightCount = highlights.size,
                        ocrPageCount = ocrByPage.size,
                        isExporting = state.isExporting,
                        onExportSaveAs = { option ->
                            pendingExportOption = option
                            val ext = MergedDocumentExporter.resolveOutputExtension(doc, option)
                            val safe = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36)
                            exportSaveLauncher.launch("${safe}_anotado.$ext")
                        },
                        onExportShare = { option ->
                            viewModel.exportMergedForShare(option) { file, mime ->
                                showExportDialog = false
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "Compartir libro fusionado"))
                                }
                            }
                        },
                        onDismiss = { showExportDialog = false }
                    )
                }
            }
        }

        // Silk bookmark ribbon drop & tactile micro-icon animations (replaces Material Snackbars)
        LumaActionFeedbackOverlay(
            event = state.feedbackEvent,
            onConsumed = viewModel::clearFeedbackEvent
        )
    }
}

/**
 * Draw-phase-only frosted scrim replacing expensive animated full-screen Modifier.blur().
 * Reads animated alpha strictly inside `drawBehind` (zero recomposition of reader content).
 */
@Composable
private fun ReaderFrostedMenuScrim(visible: Boolean) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val scrimAlphaState = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motion.tweenSpec(200),
        label = "reader_menu_scrim_alpha"
    )
    val scrimBrush = remember(palette.isDark, palette.background) {
        val base = if (palette.isDark) Color.Black else palette.background
        Brush.verticalGradient(
            colors = listOf(
                base.copy(alpha = if (palette.isDark) 0.34f else 0.24f),
                Color.Black.copy(alpha = if (palette.isDark) 0.26f else 0.16f),
                base.copy(alpha = if (palette.isDark) 0.36f else 0.26f)
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val a = scrimAlphaState.value
                if (a > 0.005f) {
                    drawRect(brush = scrimBrush, alpha = a)
                }
            }
    )
}

@Composable
private fun BoxScope.ReaderBatteryStatusHost(
    isDrawingMode: Boolean,
    isClassicExpanded: Boolean
) {
    val motion = LocalLumaMotion.current
    val activeIslandEvent by DynamicIslandEventManager.activeEvent.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !isDrawingMode && !isClassicExpanded && activeIslandEvent == null,
        enter = fadeIn(motion.tweenSpec(180)),
        exit = fadeOut(motion.exitTween(140)),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .displayCutoutPadding()
            .statusBarsPadding()
            .padding(top = 6.dp, end = 12.dp)
    ) {
        com.example.ui.components.ReaderBatteryPillBubble()
    }
}

@Composable
private fun ReaderDockOverlayHost(
    prefs: ReaderPreferences,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isDrawingMode: Boolean,
    isPdfDocument: Boolean,
    documentFormat: DocumentFormat,
    showFormatModeToggle: Boolean,
    isOriginalFormatView: Boolean,
    isPageTranslated: Boolean,
    isProcessingStatus: Boolean,
    processingStatusText: String,
    bookTitle: String,
    progressLabel: String,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onDockRepositioned: (com.example.data.model.BookmarkDockSide, Float) -> Unit,
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
    onBackToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mediaInfo by SystemMediaController.mediaState.collectAsStateWithLifecycle()
    val activeIslandEvent by DynamicIslandEventManager.activeEvent.collectAsStateWithLifecycle()
    val queuedIslandCount by DynamicIslandEventManager.queuedCount.collectAsStateWithLifecycle()
    val topModalHostId by DynamicIslandEventManager.topModalHostId.collectAsStateWithLifecycle()

    var liveBrightness by remember {
        mutableFloatStateOf(SystemBrightnessController.readSystemBrightnessFraction(context))
    }
    LaunchedEffect(context) {
        SystemBrightnessController.observeSystemBrightness(context).collect { systemFrac ->
            liveBrightness = systemFrac
        }
    }

    BookmarkDockOverlay(
        menuStyle = prefs.menuStyle,
        side = prefs.dockSide,
        dockYFraction = prefs.dockYFraction,
        dockScale = prefs.dockScale,
        brightnessValue = liveBrightness,
        isExpanded = isExpanded,
        isBookmarkedHere = isBookmarkedHere,
        isDrawingMode = isDrawingMode,
        isPdfDocument = isPdfDocument,
        documentFormat = documentFormat,
        showFormatModeToggle = showFormatModeToggle,
        isOriginalFormatView = isOriginalFormatView,
        isPageTranslated = isPageTranslated,
        isProcessingStatus = isProcessingStatus,
        processingStatusText = processingStatusText,
        mediaInfo = mediaInfo,
        activeIslandEvent = if (topModalHostId == null) activeIslandEvent else null,
        queuedIslandCount = queuedIslandCount,
        bookTitle = bookTitle,
        progressLabel = progressLabel,
        onToggleExpanded = onToggleExpanded,
        onDismiss = onDismiss,
        onDockRepositioned = onDockRepositioned,
        onBrightnessChanged = { b ->
            val clamped = b.coerceIn(0.01f, 1f)
            liveBrightness = clamped
            SystemBrightnessController.applyAbsoluteBrightness(context, activity, clamped)
        },
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
        onMediaPlayPause = { SystemMediaController.togglePlayPause(context) },
        onMediaNext = { SystemMediaController.skipToNext(context) },
        onMediaPrevious = { SystemMediaController.skipToPrevious(context) },
        onMediaSeek = { posMs -> SystemMediaController.seekTo(posMs) },
        onOpenNotificationAccess = { SystemMediaController.openNotificationAccessSettings(context) },
        onTapIslandEvent = { event -> DynamicIslandEventManager.openActiveEvent(context, event) },
        onDismissIslandEvent = { DynamicIslandEventManager.dismissActiveEvent() }
    )
}

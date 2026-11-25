package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.OcrPageResult
import com.example.data.model.PageAnimationMode
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.SelectedWordContext
import com.example.ui.components.DrawingCanvasOverlay
import androidx.compose.foundation.pager.PagerDefaults
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Byte-bounded LRU cache for rendered PDF pages.
 * Stores RGB_565 bitmaps sized to the active viewport width (~3 MB per page vs 17–24 MB previously),
 * capped at min(maxHeap / 6, 40 MB) to guarantee smooth scrolling with zero OOM spikes.
 */
private object PdfPageBitmapCache {
    private val maxKb: Int = com.example.engine.MemoryPressureManager.pdfCacheMaxKb()

    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    init {
        com.example.engine.MemoryPressureManager.registerTrimmer("pdf_page_bitmap_cache") { level ->
            synchronized(cache) {
                com.example.engine.MemoryPressureManager.trimLruCache(cache, level)
            }
        }
    }

    fun key(filePath: String, pageIndex: Int, widthPx: Int): String = "$filePath#$pageIndex@$widthPx"

    fun get(key: String): Bitmap? = synchronized(cache) {
        cache.get(key)?.takeIf { !it.isRecycled }
    }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) {
            cache.put(key, bitmap)
        }
    }

    fun clearForFile(filePath: String) {
        synchronized(cache) {
            val prefix = "$filePath#"
            val snapshot = cache.snapshot()
            for (k in snapshot.keys) {
                if (k.startsWith(prefix)) {
                    cache.remove(k)
                }
            }
        }
    }
}

/**
 * Renders a single PDF page with Android's PdfRenderer (strictly ARGB_8888), optionally compacting
 * post-render to RGB_565 on low-RAM devices, populates RAM cache, and writes asynchronously to
 * [com.example.engine.PdfPageDiskCache] so future opens decode in ~6–10 ms.
 */
private suspend fun renderAndCachePdfPage(
    appContext: android.content.Context,
    filePath: String,
    pageIndex: Int,
    targetRenderWidthPx: Int,
    renderer: PdfRenderer,
    renderMutex: Mutex
): Bitmap? {
    val cacheKey = PdfPageBitmapCache.key(filePath, pageIndex, targetRenderWidthPx)
    // Tier 1: Instant RAM hit (<1 ms)
    PdfPageBitmapCache.get(cacheKey)?.let { return it }

    // Tier 2: Fast native JPEG disk cache hit (~6–10 ms, zero renderMutex contention!)
    val diskHit = com.example.engine.PdfPageDiskCache.read(appContext, filePath, pageIndex, targetRenderWidthPx)
    if (diskHit != null) {
        PdfPageBitmapCache.put(cacheKey, diskHit)
        return diskHit
    }

    // Tier 3: Render via PdfRenderer (strictly ARGB_8888) under mutex
    val rendered = renderMutex.withLock {
        currentCoroutineContext().ensureActive()
        PdfPageBitmapCache.get(cacheKey)?.let { return@withLock it }
        runCatching {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@runCatching null
            renderer.openPage(pageIndex).use { page ->
                val maxCap = if (com.example.engine.MemoryPressureManager.isLowRamDevice) 960 else 1280
                val targetWidth = targetRenderWidthPx.coerceIn(640, maxCap)
                val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                val rawHeight = (page.height * scale).roundToInt().coerceAtLeast(1)
                val maxHeightCap = (targetWidth * 2.4f).roundToInt().coerceAtMost(2400)
                val (finalW, finalH) = if (rawHeight > maxHeightCap) {
                    val ratio = maxHeightCap.toFloat() / rawHeight
                    (targetWidth * ratio).roundToInt().coerceAtLeast(1) to maxHeightCap
                } else {
                    targetWidth to rawHeight
                }
                // Android PdfRenderer.Page.render() STRICTLY requires ARGB_8888
                val argbBmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                argbBmp.eraseColor(AndroidColor.WHITE)
                page.render(argbBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val cachedBmp = if (com.example.engine.MemoryPressureManager.isLowRamDevice) {
                    val compact = runCatching { argbBmp.copy(Bitmap.Config.RGB_565, false) }.getOrNull()
                    if (compact != null) {
                        argbBmp.recycle()
                        compact
                    } else {
                        argbBmp
                    }
                } else {
                    argbBmp
                }
                PdfPageBitmapCache.put(cacheKey, cachedBmp)
                cachedBmp
            }
        }.getOrNull()
    }

    // Persist to disk cache so future visits never pay PdfRenderer cost again
    if (rendered != null && !rendered.isRecycled) {
        com.example.engine.PdfPageDiskCache.write(appContext, filePath, pageIndex, targetRenderWidthPx, rendered)
    }
    return rendered
}

/**
 * Native PDF & Manhwa Reader:
 * - High-resolution crisp rendering via android.graphics.pdf.PdfRenderer
 * - Google Drive-style continuous focal pinch-to-zoom (1x–4.5x), 1-finger pan when zoomed,
 *   and instant double-tap zoom around the tapped point (zero gesture restarts!)
 * - E-Ink & Fade support BOTH swipe AND left/right edge taps
 * - Zero-overlap status & quick-toggle pills (Dynamic Island shows live status inside the capsule;
 *   non-Island modes place pills safely at the bottom above footer/classic bar)
 */
@Composable
fun PdfManhwaReader(
    filePath: String,
    pageCount: Int,
    currentPageIndex: Int,
    prefs: ReaderPreferences,
    drawingsByLocation: Map<String, List<DrawingStroke>>,
    ocrByPage: Map<Int, OcrPageResult>,
    showTranslatedOverlay: Boolean,
    isProcessingOcrOrTranslation: Boolean,
    processingMessage: String,
    isDockExpanded: Boolean = false,
    isDrawingMode: Boolean,
    isPanZoomWhileDrawing: Boolean = false,
    detectedFormatLabel: String = "",
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float = 1f,
    activeShapeFilled: Boolean = false,
    onPageChanged: (Int) -> Unit,
    onPageBitmapReady: (Int, Bitmap) -> Unit,
    onWordSelected: (SelectedWordContext) -> Unit,
    onStrokeCompleted: (String, DrawingStroke) -> Unit,
    onEraseStrokes: (String, Set<String>) -> Unit,
    onToggleTranslateOverlay: () -> Unit,
    onTapPageCenter: () -> Unit,
    chromeAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safeIndex = currentPageIndex.coerceIn(0, safePageCount - 1)
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val coroutineScope = rememberCoroutineScope()

    val renderMutex = remember(filePath) { Mutex() }
    var pfdAndRenderer by remember(filePath) { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    var pdfOpenError by remember(filePath) { mutableStateOf<String?>(null) }
    var isOpeningPdf by remember(filePath) { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isOpeningPdf = true
        pdfOpenError = null
        val opened = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(filePath)
                require(file.exists() && file.length() > 0L) { "El archivo PDF no existe o está vacío." }
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(pfd)
                    pfd to renderer
                } catch (t: Throwable) {
                    runCatching { pfd.close() }
                    throw t
                }
            }
        }
        if (!isActive) {
            opened.getOrNull()?.let { (pfd, renderer) ->
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
            return@LaunchedEffect
        }
        opened.onSuccess { pair ->
            pfdAndRenderer = pair
            isOpeningPdf = false
        }.onFailure { err ->
            pfdAndRenderer = null
            pdfOpenError = err.localizedMessage ?: "No se pudo abrir este PDF (puede estar dañado o protegido con contraseña)."
            isOpeningPdf = false
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            PdfPageBitmapCache.clearForFile(filePath)
            val pair = pfdAndRenderer
            pfdAndRenderer = null
            runCatching { pair?.second?.close() }
            runCatching { pair?.first?.close() }
        }
    }

    val effectivePageCount = (pfdAndRenderer?.second?.pageCount ?: safePageCount).coerceAtLeast(1)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var activeVisiblePage by remember(filePath) { mutableIntStateOf(safeIndex.coerceIn(0, effectivePageCount - 1)) }

    val hasDynamicIsland = prefs.menuStyle == ReaderMenuStyle.DYNAMIC_ISLAND
    val islandClearanceDp = if (hasDynamicIsland) (46.dp * prefs.dockScale.coerceIn(0.88f, 1.25f)) else 0.dp
    val pdfViewportMod = if (hasDynamicIsland) {
        Modifier.fillMaxSize().displayCutoutPadding().statusBarsPadding().padding(top = islandClearanceDp)
    } else {
        Modifier.fillMaxSize().displayCutoutPadding()
    }
    val applyPdfNightFilter = prefs.pdfNightFilter && palette.isDark
    val canDrawOnCanvas = isDrawingMode && !isPanZoomWhileDrawing

    if (pdfOpenError != null) {
        Box(
            modifier = modifier.fillMaxSize().background(palette.background).padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No se pudo renderizar el PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink
                )
                Text(
                    text = pdfOpenError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(palette.background)) {
        val isTabletViewport = maxWidth >= 680.dp
        val appContext = LocalContext.current.applicationContext
        // Quantize viewport width to 64px buckets; use lighter ceiling on low-RAM devices for rapid rendering
        val maxRenderWidth = if (com.example.engine.MemoryPressureManager.isLowRamDevice) 960 else 1280
        val targetRenderWidthPx = ((constraints.maxWidth / 64) * 64).coerceIn(640, maxRenderWidth)

        // Predictive background prefetcher: warms N+1, N+2, N-1, N+3 into RAM & 1.5 GB disk cache
        // while the user reads the current page so page flips are 0 ms instant!
        LaunchedEffect(filePath, activeVisiblePage, pfdAndRenderer, targetRenderWidthPx) {
            val renderer = pfdAndRenderer?.second ?: return@LaunchedEffect
            delay(55L) // Let the active visible page grab renderMutex first
            val prefetchOrder = if (com.example.engine.MemoryPressureManager.isLowRamDevice) {
                listOf(activeVisiblePage + 1, activeVisiblePage - 1, activeVisiblePage + 2)
            } else {
                listOf(activeVisiblePage + 1, activeVisiblePage + 2, activeVisiblePage - 1, activeVisiblePage + 3)
            }
            withContext(Dispatchers.IO) {
                for (idx in prefetchOrder) {
                    if (!isActive) break
                    if (idx in 0 until effectivePageCount) {
                        renderAndCachePdfPage(
                            appContext = appContext,
                            filePath = filePath,
                            pageIndex = idx,
                            targetRenderWidthPx = targetRenderWidthPx,
                            renderer = renderer,
                            renderMutex = renderMutex
                        )
                    }
                }
            }
        }

        if (prefs.pageAnimation == PageAnimationMode.VERTICAL_SCROLL) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
            val isZoomed by remember { derivedStateOf { zoomScale > 1.02f } }
            LaunchedEffect(safeIndex) {
                if (abs(listState.firstVisibleItemIndex - safeIndex) > 1) {
                    listState.scrollToItem(safeIndex)
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect {
                        activeVisiblePage = it
                        onPageChanged(it)
                    }
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = !canDrawOnCanvas && !isZoomed,
                modifier = pdfViewportMod,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(
                    count = safePageCount,
                    key = { pageIdx -> "pdf_page_$pageIdx" },
                    contentType = { "pdf_page" }
                ) { pageIdx ->
                    val inRenderWindow = abs(pageIdx - activeVisiblePage) <= 1
                    PdfInteractivePage(
                        filePath = filePath,
                        pageIndex = pageIdx,
                        targetRenderWidthPx = targetRenderWidthPx,
                        shouldRender = inRenderWindow,
                        renderer = pfdAndRenderer?.second,
                        renderMutex = renderMutex,
                        ocrResult = ocrByPage[pageIdx],
                        showTranslatedOverlay = showTranslatedOverlay,
                        applyNightFilter = applyPdfNightFilter,
                        strokes = drawingsByLocation["pdf-page-$pageIdx"].orEmpty(),
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        isDrawingMode = canDrawOnCanvas,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        activeAlpha = activeAlpha,
                        activeShapeFilled = activeShapeFilled,
                        fitInsideViewport = false,
                        onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                        onBitmapReady = { bmp -> onPageBitmapReady(pageIdx, bmp) },
                        onWordSelected = onWordSelected,
                        onStrokeCompleted = { onStrokeCompleted("pdf-page-$pageIdx", it) },
                        onEraseStrokes = { onEraseStrokes("pdf-page-$pageIdx", it) },
                        onTapAtXFraction = { xFrac ->
                            if (prefs.tapZonesEnabled && zoomScale <= 1.05f) {
                                when {
                                    xFrac < 0.28f && pageIdx > 0 -> {
                                        coroutineScope.launch { listState.animateScrollToItem(pageIdx - 1) }
                                    }
                                    xFrac > 0.72f && pageIdx < safePageCount - 1 -> {
                                        coroutineScope.launch { listState.animateScrollToItem(pageIdx + 1) }
                                    }
                                    else -> onTapPageCenter()
                                }
                            } else {
                                onTapPageCenter()
                            }
                        },
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(initialPage = safeIndex) { safePageCount }
            val isZoomed by remember { derivedStateOf { zoomScale > 1.02f } }
            var eInkFlash by remember { mutableStateOf(false) }
            var lastPdfPage by remember { mutableIntStateOf(safeIndex) }

            LaunchedEffect(safeIndex) {
                if (pagerState.currentPage != safeIndex) {
                    if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                        pagerState.scrollToPage(safeIndex)
                    } else {
                        pagerState.animateScrollToPage(safeIndex, animationSpec = motion.emphasizedTween(165))
                    }
                }
            }
            LaunchedEffect(pagerState, prefs.pageAnimation) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        zoomScale = 1f
                        panOffset = Offset.Zero
                        if (prefs.pageAnimation == PageAnimationMode.E_INK && page != lastPdfPage) {
                            eInkFlash = true
                            delay(65)
                            eInkFlash = false
                        }
                        lastPdfPage = page
                        activeVisiblePage = page
                        onPageChanged(page)
                    }
            }

            val handlePdfTap: (Float) -> Unit = { xFraction ->
                if (zoomScale > 1.05f || !prefs.tapZonesEnabled) {
                    onTapPageCenter()
                } else {
                    when {
                        xFraction < 0.28f && pagerState.currentPage > 0 -> {
                            val prev = pagerState.currentPage - 1
                            coroutineScope.launch {
                                if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                                    pagerState.scrollToPage(prev)
                                } else {
                                    pagerState.animateScrollToPage(prev, animationSpec = motion.emphasizedTween(155))
                                }
                            }
                        }
                        xFraction > 0.72f && pagerState.currentPage < safePageCount - 1 -> {
                            val next = pagerState.currentPage + 1
                            coroutineScope.launch {
                                if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                                    pagerState.scrollToPage(next)
                                } else {
                                    pagerState.animateScrollToPage(next, animationSpec = motion.emphasizedTween(155))
                                }
                            }
                        }
                        else -> onTapPageCenter()
                    }
                }
            }

            Box(modifier = pdfViewportMod) {
                HorizontalPager(
                    state = pagerState,
                    key = { pageIdx -> "pdf_page_$pageIdx" },
                    beyondViewportPageCount = 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = motion.glideSpring()
                    ),
                    userScrollEnabled = !canDrawOnCanvas && !isZoomed,
                    modifier = Modifier.fillMaxSize()
                ) { pageIdx ->
                    // Read pagerState offset strictly inside graphicsLayer/drawWithContent (0 recompositions per swipe frame)
                    val animMod = remember(prefs.pageAnimation, pagerState, pageIdx) {
                        when (prefs.pageAnimation) {
                            PageAnimationMode.BOOK_CURL -> Modifier
                                .graphicsLayer {
                                    val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                                    val absOffset = abs(pageOffset).coerceIn(0f, 1.5f)
                                    cameraDistance = 14f * density
                                    if (absOffset >= 0.995f) {
                                        // Offscreen beyondViewport page: hide completely so 3D perspective never bleeds onto active page
                                        rotationY = 0f
                                        alpha = 0f
                                    } else if (pageOffset > 0f) {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        rotationY = -pageOffset * 48f
                                        val tailFade = ((1f - absOffset) / 0.18f).coerceIn(0f, 1f)
                                        alpha = (1f - absOffset * 0.18f) * tailFade
                                    } else {
                                        rotationY = 0f
                                        alpha = 1f
                                    }
                                }
                                .drawWithContent {
                                    drawContent()
                                    val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                                    val curlStrength = abs(pageOffset).coerceIn(0f, 1f)
                                    if (curlStrength in 0.01f..0.99f) {
                                        val shadowWidth = size.width * 0.16f * curlStrength
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.18f * curlStrength)
                                                ),
                                                startX = size.width - shadowWidth,
                                                endX = size.width
                                            )
                                        )
                                    }
                                }

                            PageAnimationMode.DEPTH_CARD -> Modifier.graphicsLayer {
                                val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                                val absOffset = abs(pageOffset).coerceIn(0f, 1.5f)
                                if (absOffset >= 0.995f) {
                                    alpha = 0f
                                    translationX = 0f
                                } else if (pageOffset < 0f) {
                                    val scale = 0.88f + (1f - absOffset) * 0.12f
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 0.5f + (1f - absOffset) * 0.5f
                                    translationX = -pageOffset * size.width * 0.7f
                                } else {
                                    alpha = 1f
                                    translationX = 0f
                                }
                            }

                            PageAnimationMode.FADE -> Modifier.graphicsLayer {
                                val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                                val absOffset = abs(pageOffset)
                                if (absOffset >= 0.995f) {
                                    translationX = 0f
                                    alpha = 0f
                                } else {
                                    translationX = pageOffset * size.width
                                    alpha = (1f - absOffset).coerceIn(0f, 1f)
                                }
                            }

                            PageAnimationMode.E_INK -> Modifier.graphicsLayer {
                                val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                                val absOffset = abs(pageOffset)
                                if (absOffset >= 0.5f) {
                                    translationX = 0f
                                    alpha = 0f
                                } else {
                                    translationX = pageOffset * size.width
                                    alpha = 1f
                                }
                            }

                            else -> Modifier
                        }
                    }

                    val pageZoom = if (pageIdx == pagerState.currentPage) zoomScale else 1f
                    val pagePan = if (pageIdx == pagerState.currentPage) panOffset else Offset.Zero

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .then(animMod)
                    ) {
                        val viewportHeight = maxHeight
                        // Compact-height phone landscape uses vertical scroll so users can read & scroll smoothly;
                        // tablets and portrait screens fit the full page proportionally without stretching wide!
                        val isPhoneLandscape = maxWidth > maxHeight && maxHeight < 480.dp
                        val inRenderWindow = abs(pageIdx - activeVisiblePage) <= 1

                        if (isPhoneLandscape) {
                            val pageScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        state = pageScrollState,
                                        enabled = !canDrawOnCanvas && !isZoomed
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = viewportHeight)
                                        .padding(bottom = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PdfInteractivePage(
                                        filePath = filePath,
                                        pageIndex = pageIdx,
                                        targetRenderWidthPx = targetRenderWidthPx,
                                        shouldRender = inRenderWindow,
                                        renderer = pfdAndRenderer?.second,
                                        renderMutex = renderMutex,
                                        ocrResult = ocrByPage[pageIdx],
                                        showTranslatedOverlay = showTranslatedOverlay,
                                        applyNightFilter = applyPdfNightFilter,
                                        strokes = drawingsByLocation["pdf-page-$pageIdx"].orEmpty(),
                                        zoomScale = pageZoom,
                                        panOffset = pagePan,
                                        isDrawingMode = canDrawOnCanvas,
                                        activeTool = activeTool,
                                        activeColorArgb = activeColorArgb,
                                        activeWidthNorm = activeWidthNorm,
                                        activeAlpha = activeAlpha,
                                        activeShapeFilled = activeShapeFilled,
                                        fitInsideViewport = false,
                                        onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                                        onBitmapReady = { bmp -> onPageBitmapReady(pageIdx, bmp) },
                                        onWordSelected = onWordSelected,
                                        onStrokeCompleted = { onStrokeCompleted("pdf-page-$pageIdx", it) },
                                        onEraseStrokes = { onEraseStrokes("pdf-page-$pageIdx", it) },
                                        onTapAtXFraction = handlePdfTap,
                                        modifier = Modifier
                                            .widthIn(max = 600.dp)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            PdfInteractivePage(
                                filePath = filePath,
                                pageIndex = pageIdx,
                                targetRenderWidthPx = targetRenderWidthPx,
                                shouldRender = inRenderWindow,
                                renderer = pfdAndRenderer?.second,
                                renderMutex = renderMutex,
                                ocrResult = ocrByPage[pageIdx],
                                showTranslatedOverlay = showTranslatedOverlay,
                                applyNightFilter = applyPdfNightFilter,
                                strokes = drawingsByLocation["pdf-page-$pageIdx"].orEmpty(),
                                zoomScale = pageZoom,
                                panOffset = pagePan,
                                isDrawingMode = canDrawOnCanvas,
                                activeTool = activeTool,
                                activeColorArgb = activeColorArgb,
                                activeWidthNorm = activeWidthNorm,
                                activeAlpha = activeAlpha,
                                activeShapeFilled = activeShapeFilled,
                                fitInsideViewport = true,
                                onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                                onBitmapReady = { bmp -> onPageBitmapReady(pageIdx, bmp) },
                                onWordSelected = onWordSelected,
                                onStrokeCompleted = { onStrokeCompleted("pdf-page-$pageIdx", it) },
                                onEraseStrokes = { onEraseStrokes("pdf-page-$pageIdx", it) },
                                onTapAtXFraction = handlePdfTap,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (eInkFlash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (palette.isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.65f))
                    )
                }
            }
        }

        // Bottom-anchored conflict-free status & quick controls strip:
        // - Never collides with Dynamic Island at TopCenter or Classic Top Bar!
        // - Automatically slides cleanly above Classic Bottom Bar when expanded!
        val bottomClearance by animateDpAsState(
            targetValue = when {
                prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && isDockExpanded -> 144.dp
                else -> 40.dp
            },
            animationSpec = motion.emphasizedTween(220),
            label = "pdf_pill_bottom_clearance"
        )

        if (chromeAlpha > 0.02f) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = bottomClearance, start = 16.dp, end = 16.dp)
                    .graphicsLayer { alpha = chromeAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Show floating OCR/translation pill ONLY when not already displayed inside Dynamic Island
            AnimatedVisibility(
                visible = isProcessingOcrOrTranslation && prefs.menuStyle != ReaderMenuStyle.DYNAMIC_ISLAND,
                enter = fadeIn(motion.emphasizedTween(185)) + slideInVertically(motion.emphasizedTween(200)) { it / 2 },
                exit = fadeOut(motion.exitTween(160)) + slideOutVertically(motion.exitTween(180)) { it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .shadow(10.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(palette.surface.copy(alpha = 0.96f))
                        .border(1.5.dp, palette.accent, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("ocr_translation_progress_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.2.dp,
                        color = palette.accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = processingMessage.ifBlank { "Procesando OCR y traducción…" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick floating controls: Original Paper badge, − / % / + Zoom controls & Translated Manhwa toggle
            val showFormatBadge = prefs.useStandardPageView && detectedFormatLabel.isNotBlank() && !isDrawingMode
            val isZoomModified = abs(zoomScale - 1f) > 0.03f
            val showZoomBar = (!isDrawingMode || isTabletViewport || isZoomModified)
            val showQuickControls = showZoomBar || showFormatBadge || ocrByPage[safeIndex]?.isTranslated == true
            AnimatedVisibility(
                visible = showQuickControls,
                enter = fadeIn(motion.tweenSpec(180)),
                exit = fadeOut(motion.exitTween(150))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showFormatBadge) {
                        Row(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .background(palette.surface.copy(alpha = 0.95f))
                                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hoja · $detectedFormatLabel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent
                            )
                        }
                    }
                    if (showZoomBar) {
                        Row(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .background(palette.surface.copy(alpha = 0.94f))
                                .border(
                                    1.dp,
                                    if (isZoomModified) palette.accent.copy(alpha = 0.55f) else palette.border,
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                .testTag("pdf_zoom_control_pill"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = zoomScale > 0.46f) {
                                        val next = (zoomScale - 0.15f).coerceIn(0.45f, 4.5f)
                                        zoomScale = next
                                        if (next <= 1.01f) panOffset = Offset.Zero
                                    }
                                    .testTag("pdf_zoom_out_btn"),
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
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isZoomModified) palette.accentSoft else Color.Transparent)
                                    .clickable {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("pdf_zoom_reset_btn"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (isZoomModified) {
                                    Icon(
                                        Icons.Rounded.FitScreen,
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
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = zoomScale < 4.45f) {
                                        zoomScale = (zoomScale + 0.25f).coerceIn(0.45f, 4.5f)
                                    }
                                    .testTag("pdf_zoom_in_btn"),
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
                    }
                    if (ocrByPage[safeIndex]?.isTranslated == true) {
                        Row(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .background(if (showTranslatedOverlay) palette.accent else palette.surface.copy(alpha = 0.95f))
                                .border(1.dp, palette.accent, RoundedCornerShape(50))
                                .clickable(onClick = onToggleTranslateOverlay)
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                                .testTag("toggle_manhwa_translation_pill"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showTranslatedOverlay) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = "Alternar traducción",
                                tint = if (showTranslatedOverlay) Color.White else palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (showTranslatedOverlay) "Español" else "Original",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (showTranslatedOverlay) Color.White else palette.ink
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PdfInteractivePage(
    filePath: String,
    pageIndex: Int,
    targetRenderWidthPx: Int,
    shouldRender: Boolean,
    renderer: PdfRenderer?,
    renderMutex: Mutex,
    ocrResult: OcrPageResult?,
    showTranslatedOverlay: Boolean,
    applyNightFilter: Boolean,
    strokes: List<DrawingStroke>,
    zoomScale: Float,
    panOffset: Offset,
    isDrawingMode: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float = 1f,
    activeShapeFilled: Boolean = false,
    fitInsideViewport: Boolean = false,
    onZoomPanChange: (Float, Offset) -> Unit,
    onBitmapReady: (Bitmap) -> Unit,
    onWordSelected: (SelectedWordContext) -> Unit,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEraseStrokes: (Set<String>) -> Unit,
    onTapAtXFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val cacheKey = remember(filePath, pageIndex, targetRenderWidthPx) {
        PdfPageBitmapCache.key(filePath, pageIndex, targetRenderWidthPx)
    }
    var bitmap by remember(cacheKey, renderer) {
        mutableStateOf(if (shouldRender) PdfPageBitmapCache.get(cacheKey) else null)
    }
    var aspectRatio by remember(pageIndex) {
        val cached = PdfPageBitmapCache.get(cacheKey)
        mutableFloatStateOf(
            if (cached != null) cached.width.toFloat() / cached.height.toFloat().coerceAtLeast(1f)
            else 0.707f
        )
    }
    var selectedOcrWordBox by remember(pageIndex) { mutableStateOf<com.example.data.model.OcrWordBox?>(null) }

    // Always read latest zoom/pan inside gesture coroutines WITHOUT restarting pointerInput!
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestPan by rememberUpdatedState(panOffset)
    val latestOcr by rememberUpdatedState(ocrResult)
    val latestOnZoomPan by rememberUpdatedState(onZoomPanChange)
    val latestOnTapX by rememberUpdatedState(onTapAtXFraction)

    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(cacheKey, shouldRender, renderer) {
        if (!shouldRender) {
            // Release off-screen page reference immediately (LRU cache retains recent pages within byte cap)
            bitmap = null
            return@LaunchedEffect
        }
        val cached = PdfPageBitmapCache.get(cacheKey)
        if (cached != null) {
            aspectRatio = cached.width.toFloat() / cached.height.toFloat().coerceAtLeast(1f)
            bitmap = cached
            onBitmapReady(cached)
            return@LaunchedEffect
        }
        if (renderer == null) return@LaunchedEffect

        val rendered = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            renderAndCachePdfPage(
                appContext = appContext,
                filePath = filePath,
                pageIndex = pageIndex,
                targetRenderWidthPx = targetRenderWidthPx,
                renderer = renderer,
                renderMutex = renderMutex
            )
        }
        if (rendered != null) {
            aspectRatio = rendered.width.toFloat() / rendered.height.toFloat().coerceAtLeast(1f)
            bitmap = rendered
            onBitmapReady(rendered)
        }
    }

    val bmp = bitmap
    val imageBitmap = remember(bmp) { bmp?.asImageBitmap() }
    if (bmp == null || imageBitmap == null) {
        Box(
            modifier = modifier.aspectRatio(aspectRatio).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = palette.accent)
        }
        return
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = maxHeight
        val useHeightBound = fitInsideViewport &&
            availH.value.isFinite() &&
            availH.value > 0f &&
            (availW.value / availH.value) > aspectRatio
        val pageBaseWpx = with(density) {
            if (useHeightBound) (availH * aspectRatio).toPx() else availW.toPx()
        }.coerceAtLeast(120f)
        val pageBaseHpx = with(density) {
            if (useHeightBound) availH.toPx() else (availW / aspectRatio).toPx()
        }.coerceAtLeast(120f)
        val latestPageW by rememberUpdatedState(pageBaseWpx)
        val latestPageH by rememberUpdatedState(pageBaseHpx)

        val pageSizeMod = if (useHeightBound) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
        }

        // Untransformed outer host receives pinch & pan gestures in exact viewport pixels
        // so focal pinch-to-zoom anchors strictly beneath the user's fingers (0.45x..4.5x)!
        Box(
            modifier = Modifier
                .then(if (fitInsideViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .then(
                    if (!isDrawingMode) {
                        Modifier
                            .pointerInput(pageIndex) {
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
                                                    val maxX = maxOf(latestPageW * (newZoom - 1f) * 0.56f, 0f)
                                                    val maxY = maxOf(latestPageH * (newZoom - 1f) * 0.56f, 0f)
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
                                                val maxX = maxOf(latestPageW * (latestZoom - 1f) * 0.56f, 0f)
                                                val maxY = maxOf(latestPageH * (latestZoom - 1f) * 0.56f, 0f)
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
                            .pointerInput(pageIndex) {
                                detectTapGestures(
                                    onTap = { tapPos ->
                                        selectedOcrWordBox = null
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
                modifier = pageSizeMod
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .then(
                        if (!isDrawingMode) {
                            Modifier.pointerInput(pageIndex) {
                                // Handles tap & long-press OCR word selection directly in page-local coordinates
                                detectTapGestures(
                                    onTap = { tapPos ->
                                        selectedOcrWordBox = null
                                        val nx = (tapPos.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        latestOnTapX(nx)
                                    },
                                    onLongPress = { pos ->
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val hit = findOcrWordAt(latestOcr, nx, ny)
                                        if (hit != null) {
                                            val (block, word) = hit
                                            selectedOcrWordBox = word
                                            onWordSelected(
                                                SelectedWordContext(
                                                    word = word.text,
                                                    snippet = block.originalText,
                                                    locationKey = "pdf-page-$pageIndex",
                                                    blockIndex = block.id,
                                                    startChar = 0,
                                                    endChar = word.text.length,
                                                    anchorXFraction = nx,
                                                    anchorYFraction = ny
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val pageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                // 1. Crisp rendered PDF / Manhwa page image (with optional low-glare midnight filter)
                val nightColorFilter = remember(applyNightFilter) {
                    if (!applyNightFilter) null
                    else ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                0.82f, 0f, 0f, 0f, 0f,
                                0f, 0.80f, 0f, 0f, 0f,
                                0f, 0f, 0.76f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Página PDF ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = nightColorFilter,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Subtle PC-style OCR word highlight box when user long-presses a word on a Manhwa/PDF
                if (selectedOcrWordBox != null) {
                    val box = selectedOcrWordBox!!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = Color(0xFF1D5BFF).copy(alpha = 0.28f),
                            topLeft = Offset(box.left * size.width, box.top * size.height),
                            size = Size((box.right - box.left) * size.width, (box.bottom - box.top) * size.height),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }

                // 3. Seamless Borderless Manhwa Speech-Bubble Text Replacement
                // Scales typography proportionally to the active sheet width & bubble area on phones and tablets
                if (showTranslatedOverlay && ocrResult != null && ocrResult.isTranslated) {
                    val pageWidthDp = with(density) { pageWidthPx.toDp().value }.coerceAtLeast(240f)
                    val pageScale = (pageWidthDp / 360f).coerceIn(1.0f, 3.0f)
                    val bubbleCorner = (10f * pageScale.coerceAtMost(1.6f)).dp
                    val bubblePadH = (3.5f * pageScale.coerceAtMost(1.6f)).dp
                    val bubblePadV = (2.5f * pageScale.coerceAtMost(1.6f)).dp
                    val minSp = (9.5f * pageScale.coerceAtMost(1.85f)).coerceIn(9.5f, 18f)
                    val maxSp = (17f * pageScale).coerceIn(17f, 44f)

                    for (block in ocrResult.blocks) {
                        val translated = block.translatedText?.takeIf { it.isNotBlank() } ?: continue
                        val padX = 0.012f
                        val padY = 0.008f
                        val normLeft = (block.left - padX).coerceAtLeast(0f)
                        val normTop = (block.top - padY).coerceAtLeast(0f)
                        val normW = ((block.right - block.left) + padX * 2f).coerceIn(0.08f, 1f - normLeft)
                        val normH = ((block.bottom - block.top) + padY * 2f).coerceIn(0.032f, 1f - normTop)

                        val leftPx = (normLeft * pageWidthPx).roundToInt()
                        val topPx = (normTop * pageHeightPx).roundToInt()
                        val widthPx = (normW * pageWidthPx).roundToInt()
                        val heightPx = (normH * pageHeightPx).roundToInt()

                        val boxWidthDp = with(density) { widthPx.toDp() }
                        val boxHeightDp = with(density) { heightPx.toDp() }

                        val innerW = (boxWidthDp.value - bubblePadH.value * 2f).coerceAtLeast(24f)
                        val innerH = (boxHeightDp.value - bubblePadV.value * 2f).coerceAtLeast(16f)
                        val areaDp2 = (innerW * innerH).coerceAtLeast(360f)
                        val charCount = translated.length.coerceAtLeast(3)
                        var fittedSp = kotlin.math.sqrt((areaDp2 * 0.86f) / (charCount * 0.92f))
                            .coerceIn(minSp, maxSp)
                        val estCharsPerLine = (innerW / (fittedSp * 0.56f)).coerceAtLeast(1f)
                        val estLines = kotlin.math.ceil(charCount / estCharsPerLine)
                        val estHeight = estLines * fittedSp * 1.15f
                        if (estHeight > innerH && estHeight > 1f) {
                            fittedSp = (fittedSp * (innerH / estHeight)).coerceIn(minSp * 0.78f, maxSp)
                        }

                        val bgColor = Color(block.bgColorArgb.toInt())
                        val inkColor = Color(block.inkColorArgb.toInt())

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(leftPx, topPx) }
                                .size(width = boxWidthDp, height = boxHeightDp)
                                .clip(RoundedCornerShape(bubbleCorner))
                                .background(bgColor)
                                .padding(horizontal = bubblePadH, vertical = bubblePadV),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = translated,
                                color = inkColor,
                                fontSize = fittedSp.sp,
                                lineHeight = (fittedSp * 1.14f).sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 4. Zoom-synchronized persistent Drawing Canvas Overlay
                DrawingCanvasOverlay(
                    strokes = strokes,
                    isInteractive = isDrawingMode,
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

private fun findOcrWordAt(
    ocrPage: OcrPageResult?,
    nx: Float,
    ny: Float
): Pair<com.example.data.model.OcrBlockBox, com.example.data.model.OcrWordBox>? {
    if (ocrPage == null) return null
    val pad = 0.018f
    for (block in ocrPage.blocks) {
        for (word in block.words) {
            if (nx in (word.left - pad)..(word.right + pad) &&
                ny in (word.top - pad)..(word.bottom + pad)
            ) {
                return block to word
            }
        }
    }
    var best: Pair<com.example.data.model.OcrBlockBox, com.example.data.model.OcrWordBox>? = null
    var bestDist = 0.085f
    for (block in ocrPage.blocks) {
        for (word in block.words) {
            val cx = (word.left + word.right) * 0.5f
            val cy = (word.top + word.bottom) * 0.5f
            val dist = kotlin.math.hypot(cx - nx, cy - ny)
            if (dist < bestDist) {
                bestDist = dist
                best = block to word
            }
        }
    }
    return best
}

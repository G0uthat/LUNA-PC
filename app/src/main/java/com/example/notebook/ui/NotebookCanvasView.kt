package com.example.notebook.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.GroupWork
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.engine.NotebookCanvasRenderer
import com.example.notebook.engine.NotebookLayerCache
import com.example.notebook.engine.NotebookStudyHelpers
import com.example.notebook.model.BuiltInStickerKind
import com.example.notebook.model.ConnectorObject
import com.example.notebook.model.ImageObject
import com.example.notebook.model.NbPoint
import com.example.notebook.model.NotebookAlignAction
import com.example.notebook.model.NotebookEraserMode
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookObject
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.NotebookShapeKind
import com.example.notebook.model.NotebookTool
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.StickerObject
import com.example.notebook.model.StrokeBlendMode
import com.example.notebook.model.StrokeObject
import com.example.notebook.model.StrokeToolKind
import com.example.notebook.model.TableObject
import com.example.notebook.model.TextObject
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ItemDragHandle {
    MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, ROTATE
}

/**
 * High-performance 60–120 FPS Study Notebook Canvas:
 * - Per-layer cached bitmaps (`NotebookLayerCache`) + live overlay -> ZERO per-point recompositions.
 * - Real Eyedropper pixel sampling, Stylus-only mode & palm rejection (`PointerType.Stylus`/`Eraser`),
 *   EMA stroke stabilizer, focal pinch zoom 0.5x–5.0x, double-tap to fit, and Ctrl+Scroll mouse zoom.
 * - Freehand Lasso multi-selection, aspect-ratio-locked resize, magnetic center/edge snap guides,
 *   stroke & pixel erasers, and full floating Selection Action Bar (>= 48 dp targets).
 */
@Composable
fun NotebookCanvasView(
    pageAspectRatio: Float,
    template: NotebookPageTemplate,
    backgroundType: NotebookBackgroundType,
    backgroundColorArgb: Long,
    backgroundBitmap: Bitmap?,
    content: NotebookPageContent,
    loadedBitmaps: Map<String, Bitmap>,
    activeTool: NotebookTool,
    activeLayerId: String,
    activeShapeKind: NotebookShapeKind,
    activeStickerKind: BuiltInStickerKind,
    activeColorArgb: Long,
    activeHighlighterColorArgb: Long,
    activeStrokeWidthNorm: Float,
    activeAlpha: Float,
    stabilizerLevel: Float,
    eraserMode: NotebookEraserMode,
    stylusOnlyMode: Boolean,
    lockAspectRatio: Boolean,
    isEyedropperActive: Boolean,
    activeShapeFillEnabled: Boolean,
    activeShapeDashed: Boolean,
    activeShapeShadow: Boolean,
    selectedObjectIds: Set<String>,
    pendingConnectorFromId: String?,
    zoomScale: Float,
    panOffset: Offset,
    onZoomPanChange: (Float, Offset) -> Unit,
    onResetZoomPan: () -> Unit,
    onEyedropperColorPicked: (Long) -> Unit,
    onAddStroke: (StrokeObject) -> Unit,
    onEraseStrokes: (Set<String>) -> Unit,
    onErasePixelsAt: (normX: Float, normY: Float, radiusNorm: Float) -> Unit,
    onAddShape: (ShapeObject) -> Unit,
    onAddSticker: (StickerObject) -> Unit,
    onConnectorObjectTap: (String) -> Unit,
    onRequestCreateTextAt: (normX: Float, normY: Float, isBanner: Boolean) -> Unit,
    onRequestCreateTableAt: (normX: Float, normY: Float) -> Unit,
    onRequestEditText: (TextObject) -> Unit,
    onRequestEditCallout: (ShapeObject) -> Unit,
    onRequestEditImage: (ImageObject) -> Unit,
    onRequestEditTable: (TableObject) -> Unit,
    onSelectObject: (String?) -> Unit,
    onSelectObjects: (Set<String>) -> Unit,
    onBeginObjectsTransform: (List<NotebookObject>) -> Unit,
    onUpdateObjectsLive: (List<NotebookObject>) -> Unit,
    onCommitObjectsTransform: () -> Unit,
    onCopySelected: () -> Unit,
    onFlipSelectedH: () -> Unit,
    onFlipSelectedV: () -> Unit,
    onGroupSelected: () -> Unit,
    onUngroupSelected: () -> Unit,
    onAlignSelected: (NotebookAlignAction) -> Unit,
    onSetSelectedOpacity: (Float) -> Unit,
    onToggleLockSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onDeleteSelected: () -> Unit,
    onConvertStrokesToText: () -> Unit = {},
    onRemoveImageBackground: (ImageObject) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val gestureScope = rememberCoroutineScope()
    val typefaceProvider = remember(context) {
        { fontId: String -> NotebookFonts.resolveAndroidTypeface(context, fontId) }
    }
    val curContent by rememberUpdatedState(content)
    val curTool by rememberUpdatedState(activeTool)
    val curLayerId by rememberUpdatedState(activeLayerId)
    val curShapeKind by rememberUpdatedState(activeShapeKind)
    val curStickerKind by rememberUpdatedState(activeStickerKind)
    val curColor by rememberUpdatedState(activeColorArgb)
    val curHlColor by rememberUpdatedState(activeHighlighterColorArgb)
    val curWidth by rememberUpdatedState(activeStrokeWidthNorm)
    val curAlpha by rememberUpdatedState(activeAlpha)
    val curStabilizer by rememberUpdatedState(stabilizerLevel)
    val curEraserMode by rememberUpdatedState(eraserMode)
    val curStylusOnly by rememberUpdatedState(stylusOnlyMode)
    val curLockAspect by rememberUpdatedState(lockAspectRatio)
    val curEyedropper by rememberUpdatedState(isEyedropperActive)
    val curShapeFill by rememberUpdatedState(activeShapeFillEnabled)
    val curShapeDashed by rememberUpdatedState(activeShapeDashed)
    val curShapeShadow by rememberUpdatedState(activeShapeShadow)
    val curSelectedIds by rememberUpdatedState(selectedObjectIds)
    val curZoom by rememberUpdatedState(zoomScale)
    val curPan by rememberUpdatedState(panOffset)

    val layerCache = remember { NotebookLayerCache() }
    DisposableEffect(Unit) {
        onDispose { layerCache.recycleAll() }
    }

    // Draw-phase-only live buffers -> ZERO recompositions per ink / lasso / snap sample!
    val livePoints = remember { ArrayList<NbPoint>(256) }
    val liveLassoPoints = remember { ArrayList<NbPoint>(128) }
    var liveStrokeRef by remember { mutableStateOf<StrokeObject?>(null) }
    var liveShapeRef by remember { mutableStateOf<ShapeObject?>(null) }
    var activeSnapX by remember { mutableStateOf<Float?>(null) }
    var activeSnapY by remember { mutableStateOf<Float?>(null) }
    val drawInvalidation = remember { mutableIntStateOf(0) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var lastTapTimeMs by remember { mutableStateOf(0L) }
    var lastTapItemId by remember { mutableStateOf<String?>(null) }
    var lastFitTapMs by remember { mutableStateOf(0L) }
    var lastStylusTimeMs by remember { mutableStateOf(0L) }

    var showAlignPopover by remember { mutableStateOf(false) }
    var showOpacityPopover by remember { mutableStateOf(false) }

    fun screenToPageNorm(viewportPos: Offset, pressure: Float = 1f): NbPoint? {
        val vw = viewportSize.width.toFloat()
        val vh = viewportSize.height.toFloat()
        val pw = pageSize.width.toFloat()
        val ph = pageSize.height.toFloat()
        if (vw <= 1f || vh <= 1f || pw <= 1f || ph <= 1f) return null
        val z = curZoom.coerceAtLeast(0.5f)
        val untransformedX = (viewportPos.x - vw * 0.5f - curPan.x) / z + vw * 0.5f
        val untransformedY = (viewportPos.y - vh * 0.5f - curPan.y) / z + vh * 0.5f
        val pageLeft = (vw - pw) * 0.5f
        val pageTop = (vh - ph) * 0.5f
        val safePressure = if (pressure > 0.05f) pressure.coerceIn(0.25f, 1.8f) else 1f
        return NbPoint(
            x = (untransformedX - pageLeft) / pw,
            y = (untransformedY - pageTop) / ph,
            pressure = safePressure
        )
    }

    fun clampPan(scale: Float, rawPan: Offset): Offset {
        val vw = viewportSize.width.toFloat()
        val vh = viewportSize.height.toFloat()
        val maxX = max(vw * 0.65f, vw * scale * 0.65f)
        val maxY = max(vh * 0.65f, vh * scale * 0.65f)
        return Offset(
            x = rawPan.x.coerceIn(-maxX, maxX),
            y = rawPan.y.coerceIn(-maxY, maxY)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(if (palette.isDark) Color(0xFF10131A) else Color(0xFFEAECEF))
            .onSizeChanged { viewportSize = it }
            // Mouse Ctrl + Scroll Wheel focal zoom on tablets / desktop mode
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll && ev.keyboardModifiers.isCtrlPressed) {
                            val change = ev.changes.firstOrNull() ?: continue
                            val scrollY = change.scrollDelta.y
                            if (scrollY != 0f) {
                                val oldZoom = curZoom
                                val factor = if (scrollY < 0f) 1.12f else 0.89f
                                val newZoom = (oldZoom * factor).coerceIn(0.5f, 5.0f)
                                val ratio = newZoom / oldZoom.coerceAtLeast(0.01f)
                                val center = Offset(viewportSize.width * 0.5f, viewportSize.height * 0.5f)
                                val rel = change.position - center
                                val focalPan = (curPan - rel) * ratio + rel
                                onZoomPanChange(newZoom, clampPan(newZoom, focalPan))
                                change.consume()
                            }
                        }
                    }
                }
            }
            // Main touch & stylus gesture loop
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val pointerType = firstDown.type
                    val nowDownMs = System.currentTimeMillis()

                    val isStylus = pointerType == PointerType.Stylus
                    val isStylusEraser = pointerType == PointerType.Eraser
                    val isFinger = pointerType == PointerType.Touch

                    if (isStylus || isStylusEraser) {
                        lastStylusTimeMs = nowDownMs
                    }

                    // Palm rejection: ignore stray single-touch palm contacts within 420 ms of active stylus stroke
                    val palmRejected = isFinger && !curStylusOnly && (nowDownMs - lastStylusTimeMs) < 420L

                    // Stylus-only mode: finger pans & zooms while stylus draws!
                    val fingerPanMode = curStylusOnly && isFinger && !curEyedropper
                    val effectiveTool = when {
                        curEyedropper -> NotebookTool.PAN_ZOOM
                        isStylusEraser -> NotebookTool.ERASER
                        fingerPanMode -> NotebookTool.PAN_ZOOM
                        else -> curTool
                    }

                    var gestureMultiTouch = false
                    var dragHandle: ItemDragHandle? = null
                    var dragItemsStart: List<NotebookObject> = emptyList()
                    var dragOriginNorm: NbPoint? = null
                    var didMovePointer = false
                    var shapeStartNorm: NbPoint? = null
                    var isDrawingLasso = false
                    val erasedThisGesture = HashSet<String>()
                    // Hold-to-Snap (~450 ms steady hold at end of freehand stroke -> straight line / ellipse / rounded rect)
                    var holdSnapJob: Job? = null
                    var holdAnchorNorm: NbPoint? = null
                    var snappedShape: ShapeObject? = null

                    val downNorm = screenToPageNorm(firstDown.position, firstDown.pressure)

                    if (curEyedropper && downNorm != null) {
                        layerCache.samplePixelColor(downNorm.x, downNorm.y)?.let { onEyedropperColorPicked(it) }
                        firstDown.consume()
                    } else if (!palmRejected && downNorm != null) {
                        when (effectiveTool) {
                            NotebookTool.PENCIL, NotebookTool.PEN, NotebookTool.FINELINER, NotebookTool.HIGHLIGHTER -> {
                                livePoints.clear()
                                livePoints.add(downNorm)
                                val isHl = effectiveTool == NotebookTool.HIGHLIGHTER
                                val isFine = effectiveTool == NotebookTool.FINELINER
                                val isPencil = effectiveTool == NotebookTool.PENCIL
                                liveStrokeRef = StrokeObject(
                                    layerId = if (isHl) NotebookLayerIds.UNDERLAY else curLayerId,
                                    tool = when (effectiveTool) {
                                        NotebookTool.PENCIL -> StrokeToolKind.PENCIL
                                        NotebookTool.HIGHLIGHTER -> StrokeToolKind.HIGHLIGHTER
                                        NotebookTool.FINELINER -> StrokeToolKind.FINELINER
                                        else -> StrokeToolKind.PEN
                                    },
                                    colorArgb = if (isHl) curHlColor else curColor,
                                    widthNorm = when {
                                        isFine -> (curWidth * 0.55f).coerceAtLeast(0.0014f)
                                        isPencil -> (curWidth * 0.88f).coerceAtLeast(0.0016f)
                                        else -> curWidth
                                    },
                                    opacity = curAlpha,
                                    points = livePoints,
                                    blendMode = if (isHl) StrokeBlendMode.MULTIPLY else StrokeBlendMode.NORMAL
                                )
                                drawInvalidation.intValue++
                            }

                            NotebookTool.ERASER -> {
                                if (curEraserMode == NotebookEraserMode.PIXEL) {
                                    onErasePixelsAt(downNorm.x, downNorm.y, max(0.018f, curWidth * 2.8f))
                                } else {
                                    collectHitStrokes(curContent, downNorm, erasedThisGesture)
                                    if (erasedThisGesture.isNotEmpty()) {
                                        onEraseStrokes(erasedThisGesture.toSet())
                                    }
                                }
                            }

                            NotebookTool.SHAPE -> {
                                shapeStartNorm = downNorm
                            }

                            NotebookTool.SELECT -> {
                                val singleSel = curSelectedIds.singleOrNull()?.let { curContent.findById(it) }
                                val detectedHandle = if (singleSel != null && !singleSel.locked && singleSel.visible && singleSel !is ConnectorObject) {
                                    hitTestHandles(singleSel, downNorm)
                                } else null

                                if (singleSel != null && detectedHandle != null) {
                                    dragHandle = detectedHandle
                                    dragItemsStart = listOf(singleSel)
                                    dragOriginNorm = downNorm
                                    onBeginObjectsTransform(dragItemsStart)
                                } else {
                                    val hitObj = hitTestInteractiveObjects(curContent, downNorm)
                                    if (hitObj != null) {
                                        val alreadySelected = hitObj.id in curSelectedIds
                                        val targetGroup = if (alreadySelected && curSelectedIds.size > 1) {
                                            curContent.objects.filter { it.id in curSelectedIds && !it.locked }
                                        } else {
                                            onSelectObject(hitObj.id)
                                            if (hitObj.groupId != null) {
                                                curContent.objects.filter { it.groupId == hitObj.groupId && !it.locked }
                                            } else {
                                                if (hitObj.locked) emptyList() else listOf(hitObj)
                                            }
                                        }
                                        if (targetGroup.isNotEmpty()) {
                                            dragHandle = ItemDragHandle.MOVE
                                            dragItemsStart = targetGroup
                                            dragOriginNorm = downNorm
                                            onBeginObjectsTransform(targetGroup)
                                        }
                                    } else {
                                        // Dragging on empty space in Lazo/Select starts freehand Lasso selection!
                                        isDrawingLasso = true
                                        liveLassoPoints.clear()
                                        liveLassoPoints.add(downNorm)
                                        drawInvalidation.intValue++
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        // If a stylus touches down mid-finger-gesture, reject stray palms
                        if (pressed.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }) {
                            lastStylusTimeMs = System.currentTimeMillis()
                        }

                        // 2+ fingers -> focal pinch zoom (0.5x–5.0x) + pan
                        val touchPressed = pressed.filter { it.type == PointerType.Touch }
                        if (pressed.size >= 2 && (touchPressed.size >= 2 || !pressed.any { it.type == PointerType.Stylus })) {
                            if (!gestureMultiTouch) {
                                gestureMultiTouch = true
                                holdSnapJob?.cancel()
                                holdSnapJob = null
                                snappedShape = null
                                livePoints.clear()
                                liveLassoPoints.clear()
                                liveStrokeRef = null
                                liveShapeRef = null
                                activeSnapX = null
                                activeSnapY = null
                                if (dragItemsStart.isNotEmpty()) {
                                    onCommitObjectsTransform()
                                }
                                dragHandle = null
                                dragItemsStart = emptyList()
                                shapeStartNorm = null
                                isDrawingLasso = false
                                drawInvalidation.intValue++
                            }
                            val zoomFactor = event.calculateZoom()
                            val panDelta = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (zoomFactor != 1f || panDelta != Offset.Zero) {
                                val oldZoom = curZoom
                                val newZoom = (oldZoom * zoomFactor).coerceIn(0.5f, 5.0f)
                                val ratio = newZoom / oldZoom.coerceAtLeast(0.01f)
                                val center = Offset(viewportSize.width * 0.5f, viewportSize.height * 0.5f)
                                val rel = centroid - center
                                val focalPan = (curPan - rel) * ratio + rel + panDelta
                                onZoomPanChange(newZoom, clampPan(newZoom, focalPan))
                            }
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                            continue
                        }

                        if (gestureMultiTouch || palmRejected) {
                            pressed.forEach { it.consume() }
                            continue
                        }

                        // Prioritize stylus pointer if both stylus + palm rest touch screen simultaneously
                        val change = pressed.firstOrNull { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
                            ?: pressed.first()
                        val prevPos = change.previousPosition
                        val currPos = change.position
                        if ((currPos - firstDown.position).getDistance() > 8f) {
                            didMovePointer = true
                        }

                        if (curEyedropper) {
                            screenToPageNorm(currPos)?.let { pt ->
                                layerCache.samplePixelColor(pt.x, pt.y)?.let { onEyedropperColorPicked(it) }
                            }
                            change.consume()
                            continue
                        }

                        if (effectiveTool == NotebookTool.PAN_ZOOM) {
                            val delta = currPos - prevPos
                            if (delta != Offset.Zero) {
                                onZoomPanChange(curZoom, clampPan(curZoom, curPan + delta))
                                change.consume()
                            }
                            continue
                        }

                        val rawNorm = screenToPageNorm(currPos, change.pressure) ?: continue
                        when (effectiveTool) {
                            NotebookTool.PENCIL, NotebookTool.PEN, NotebookTool.FINELINER, NotebookTool.HIGHLIGHTER -> {
                                val canHoldSnap = effectiveTool != NotebookTool.HIGHLIGHTER
                                val last = livePoints.lastOrNull()
                                val smoothed = if (last != null && curStabilizer > 0.02f) {
                                    val k = curStabilizer.coerceIn(0f, 0.85f)
                                    NbPoint(
                                        x = last.x * k + rawNorm.x * (1f - k),
                                        y = last.y * k + rawNorm.y * (1f - k),
                                        pressure = last.pressure * 0.35f + rawNorm.pressure * 0.65f
                                    )
                                } else rawNorm

                                if (snappedShape != null) {
                                    // Allow subtle endpoint refinement if user keeps dragging a snapped line
                                    val curSnap = snappedShape
                                    if (curSnap != null && curSnap.kind == NotebookShapeKind.LINE) {
                                        val updatedLine = curSnap.copy(
                                            x = min(curSnap.startX, rawNorm.x),
                                            y = min(curSnap.startY, rawNorm.y),
                                            width = abs(rawNorm.x - curSnap.startX).coerceAtLeast(0.02f),
                                            height = abs(rawNorm.y - curSnap.startY).coerceAtLeast(0.02f),
                                            endX = rawNorm.x,
                                            endY = rawNorm.y
                                        )
                                        snappedShape = updatedLine
                                        liveShapeRef = updatedLine
                                        drawInvalidation.intValue++
                                    }
                                    change.consume()
                                } else {
                                    if (last == null || hypot((smoothed.x - last.x).toDouble(), (smoothed.y - last.y).toDouble()) > 0.0020) {
                                        livePoints.add(smoothed)
                                        drawInvalidation.intValue++
                                    }
                                    if (canHoldSnap) {
                                        val anchor = holdAnchorNorm
                                        val movedFromAnchor = anchor == null ||
                                            hypot((rawNorm.x - anchor.x).toDouble(), (rawNorm.y - anchor.y).toDouble()) > 0.009
                                        if (movedFromAnchor) {
                                            holdAnchorNorm = rawNorm
                                            holdSnapJob?.cancel()
                                            if (livePoints.size >= 6) {
                                                val snapshotPts = livePoints.toList()
                                                val snapLayer = curLayerId
                                                val snapColor = curColor
                                                val snapFill = curShapeFill
                                                val snapDashed = curShapeDashed
                                                val snapShadow = curShapeShadow
                                                val snapAlpha = curAlpha
                                                val snapWidth = curWidth
                                                holdSnapJob = gestureScope.launch {
                                                    delay(450L)
                                                    val recognized = NotebookStudyHelpers.recognizePerfectShape(
                                                        points = snapshotPts,
                                                        layerId = snapLayer,
                                                        colorArgb = snapColor,
                                                        fillEnabled = snapFill,
                                                        isDashed = snapDashed,
                                                        hasShadow = snapShadow,
                                                        opacity = snapAlpha,
                                                        strokeWidthNorm = snapWidth
                                                    )
                                                    if (recognized != null && liveStrokeRef != null) {
                                                        snappedShape = recognized
                                                        liveShapeRef = recognized
                                                        liveStrokeRef = null
                                                        drawInvalidation.intValue++
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            }

                            NotebookTool.ERASER -> {
                                if (curEraserMode == NotebookEraserMode.PIXEL) {
                                    onErasePixelsAt(rawNorm.x, rawNorm.y, max(0.018f, curWidth * 2.8f))
                                } else {
                                    val prevSize = erasedThisGesture.size
                                    collectHitStrokes(curContent, rawNorm, erasedThisGesture)
                                    if (erasedThisGesture.size > prevSize) {
                                        onEraseStrokes(erasedThisGesture.toSet())
                                    }
                                }
                                change.consume()
                            }

                            NotebookTool.SHAPE -> {
                                val origin = shapeStartNorm
                                if (origin != null && !curShapeKind.isTapStamp) {
                                    liveShapeRef = buildShapeFromDrag(
                                        kind = curShapeKind,
                                        start = origin,
                                        end = rawNorm,
                                        layerId = curLayerId,
                                        colorArgb = curColor,
                                        fillEnabled = curShapeFill,
                                        isDashed = curShapeDashed,
                                        hasShadow = curShapeShadow,
                                        opacity = curAlpha,
                                        strokeWidthNorm = curWidth
                                    )
                                    drawInvalidation.intValue++
                                }
                                change.consume()
                            }

                            NotebookTool.SELECT -> {
                                if (isDrawingLasso) {
                                    val lastL = liveLassoPoints.lastOrNull()
                                    if (lastL == null || hypot((rawNorm.x - lastL.x).toDouble(), (rawNorm.y - lastL.y).toDouble()) > 0.006) {
                                        liveLassoPoints.add(rawNorm)
                                        drawInvalidation.intValue++
                                    }
                                    change.consume()
                                } else {
                                    val origin = dragOriginNorm
                                    val handle = dragHandle
                                    if (dragItemsStart.isNotEmpty() && origin != null && handle != null && didMovePointer) {
                                        if (handle == ItemDragHandle.MOVE) {
                                            val rawDx = rawNorm.x - origin.x
                                            val rawDy = rawNorm.y - origin.y
                                            val snapResult = computeMagneticSnap(
                                                movingItems = dragItemsStart,
                                                allItems = curContent.objects,
                                                rawDx = rawDx,
                                                rawDy = rawDy
                                            )
                                            activeSnapX = snapResult.guideX
                                            activeSnapY = snapResult.guideY
                                            drawInvalidation.intValue++
                                            val moved = dragItemsStart.map { it.movedBy(snapResult.dx, snapResult.dy) }
                                            onUpdateObjectsLive(moved)
                                        } else {
                                            val single = dragItemsStart.first()
                                            val updated = applyHandleTransform(
                                                start = single,
                                                handle = handle,
                                                origin = origin,
                                                current = rawNorm,
                                                lockAspectRatio = curLockAspect
                                            )
                                            onUpdateObjectsLive(listOf(updated))
                                        }
                                        change.consume()
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }

                    // Pointer lifted
                    holdSnapJob?.cancel()
                    holdSnapJob = null
                    activeSnapX = null
                    activeSnapY = null
                    if (!gestureMultiTouch && !palmRejected && !curEyedropper) {
                        when (effectiveTool) {
                            NotebookTool.PENCIL, NotebookTool.PEN, NotebookTool.FINELINER, NotebookTool.HIGHLIGHTER -> {
                                val snapped = snappedShape
                                snappedShape = null
                                val pts = livePoints.toList()
                                val draft = liveStrokeRef
                                livePoints.clear()
                                liveStrokeRef = null
                                if (snapped != null) {
                                    liveShapeRef = null
                                    drawInvalidation.intValue++
                                    onAddShape(snapped)
                                } else {
                                    drawInvalidation.intValue++
                                    if (draft != null && pts.isNotEmpty()) {
                                        val b = StrokeObject.computeBounds(pts)
                                        onAddStroke(
                                            draft.copy(
                                                x = b[0],
                                                y = b[1],
                                                width = b[2],
                                                height = b[3],
                                                points = pts
                                            )
                                        )
                                    }
                                }
                            }

                            NotebookTool.PAN_ZOOM -> {
                                // Double-tap to fit page
                                if (!didMovePointer) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastFitTapMs < 340L) {
                                        onResetZoomPan()
                                        lastFitTapMs = 0L
                                    } else {
                                        lastFitTapMs = now
                                    }
                                }
                            }

                            NotebookTool.SHAPE -> {
                                val preview = liveShapeRef
                                liveShapeRef = null
                                drawInvalidation.intValue++
                                if (preview != null && didMovePointer) {
                                    onAddShape(preview)
                                    if (preview.kind == NotebookShapeKind.CALLOUT) {
                                        onRequestEditCallout(preview)
                                    }
                                } else if (downNorm != null && curShapeKind.isTapStamp) {
                                    val badgeLabel = if (curShapeKind == NotebookShapeKind.NUMBER_BADGE) {
                                        val nextNum = curContent.objects.count {
                                            it is ShapeObject && it.kind == NotebookShapeKind.NUMBER_BADGE
                                        } + 1
                                        nextNum.toString()
                                    } else ""
                                    onAddShape(
                                        buildStampShape(
                                            kind = curShapeKind,
                                            center = downNorm,
                                            layerId = curLayerId,
                                            colorArgb = curColor,
                                            opacity = curAlpha,
                                            strokeWidthNorm = curWidth,
                                            labelText = badgeLabel
                                        )
                                    )
                                }
                            }

                            NotebookTool.STICKER -> {
                                if (!didMovePointer && downNorm != null) {
                                    val w = 0.085f
                                    val h = 0.062f
                                    onAddSticker(
                                        StickerObject(
                                            layerId = curLayerId,
                                            x = (downNorm.x - w * 0.5f).coerceIn(0.01f, 0.91f),
                                            y = (downNorm.y - h * 0.5f).coerceIn(0.01f, 0.93f),
                                            width = w,
                                            height = h,
                                            builtInKind = curStickerKind,
                                            colorArgb = curColor,
                                            opacity = curAlpha
                                        )
                                    )
                                }
                            }

                            NotebookTool.CONNECTOR -> {
                                if (!didMovePointer && downNorm != null) {
                                    val hit = hitTestInteractiveObjects(curContent, downNorm)
                                    if (hit != null) onConnectorObjectTap(hit.id)
                                }
                            }

                            NotebookTool.TABLE -> {
                                if (!didMovePointer && downNorm != null) {
                                    onRequestCreateTableAt(downNorm.x, downNorm.y)
                                }
                            }

                            NotebookTool.TEXT_BOX -> {
                                if (!didMovePointer && downNorm != null) {
                                    onRequestCreateTextAt(downNorm.x, downNorm.y, false)
                                }
                            }

                            NotebookTool.TITLE_BANNER -> {
                                if (!didMovePointer && downNorm != null) {
                                    onRequestCreateTextAt(downNorm.x, downNorm.y, true)
                                }
                            }

                            NotebookTool.SELECT -> {
                                if (isDrawingLasso) {
                                    val poly = liveLassoPoints.toList()
                                    liveLassoPoints.clear()
                                    drawInvalidation.intValue++
                                    if (didMovePointer && poly.size >= 4) {
                                        val insideIds = curContent.objects
                                            .filter { obj ->
                                                obj.visible &&
                                                    obj !is ConnectorObject &&
                                                    curContent.isLayerVisible(obj.layerId) &&
                                                    !curContent.isLayerLocked(obj.layerId) &&
                                                    isPointInPolygon(obj.centerX, obj.centerY, poly)
                                            }
                                            .map { it.id }
                                            .toSet()
                                        onSelectObjects(insideIds)
                                    } else {
                                        onSelectObject(null)
                                    }
                                } else {
                                    if (dragItemsStart.isNotEmpty()) {
                                        onCommitObjectsTransform()
                                    }
                                    if (!didMovePointer && downNorm != null) {
                                        val hit = hitTestInteractiveObjects(curContent, downNorm)
                                        val now = System.currentTimeMillis()
                                        if (hit != null && hit.id == lastTapItemId && (now - lastTapTimeMs) < 380L) {
                                            when (hit) {
                                                is TextObject -> onRequestEditText(hit)
                                                is ShapeObject -> if (hit.kind == NotebookShapeKind.CALLOUT) onRequestEditCallout(hit)
                                                is ImageObject -> onRequestEditImage(hit)
                                                is TableObject -> onRequestEditTable(hit)
                                                else -> Unit
                                            }
                                            lastTapItemId = null
                                        } else {
                                            lastTapItemId = hit?.id
                                            lastTapTimeMs = now
                                        }
                                    }
                                }
                            }

                            else -> Unit
                        }
                    } else {
                        liveLassoPoints.clear()
                        drawInvalidation.intValue++
                    }
                }
            }
            .testTag("notebook_canvas_viewport"),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth - 20.dp
        val maxH = maxHeight - 20.dp
        val fitByWidth = (maxW / pageAspectRatio) <= maxH

        val pageModifier = if (fitByWidth) {
            Modifier.size(width = maxW.coerceAtLeast(120.dp), height = (maxW / pageAspectRatio).coerceAtLeast(120.dp))
        } else {
            Modifier.size(width = (maxH * pageAspectRatio).coerceAtLeast(120.dp), height = maxH.coerceAtLeast(120.dp))
        }

        Box(
            modifier = pageModifier
                .aspectRatio(pageAspectRatio)
                .onSizeChanged { pageSize = it }
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
                .shadow(10.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .drawBehind {
                    val tick = drawInvalidation.intValue
                    if (tick >= 0 && size.width > 1f && size.height > 1f) {
                        val assetProvider: (String) -> Bitmap? = { rel -> loadedBitmaps[rel] }
                        layerCache.ensureLayersRendered(
                            pageWidthPx = size.width.toInt(),
                            pageHeightPx = size.height.toInt(),
                            template = template,
                            backgroundType = backgroundType,
                            backgroundColorArgb = backgroundColorArgb,
                            backgroundBitmap = backgroundBitmap,
                            content = content,
                            assetBitmapProvider = assetProvider,
                            loadedAssetCount = loadedBitmaps.size,
                            typefaceProvider = typefaceProvider
                        )
                        drawIntoCanvas { composeCanvas ->
                            val nc = composeCanvas.nativeCanvas
                            layerCache.blitCachedLayersWithLiveOverlay(
                                canvas = nc,
                                targetWidthPx = size.width,
                                targetHeightPx = size.height,
                                liveStroke = liveStrokeRef,
                                liveShapePreview = liveShapeRef,
                                assetBitmapProvider = assetProvider,
                                typefaceProvider = typefaceProvider
                            )
                            val selList = if (selectedObjectIds.isNotEmpty()) {
                                content.objects.filter { it.id in selectedObjectIds && it.visible }
                            } else if (pendingConnectorFromId != null) {
                                listOfNotNull(content.findById(pendingConnectorFromId))
                            } else emptyList()

                            if (selList.isNotEmpty()) {
                                NotebookCanvasRenderer.drawMultiSelectionOverlay(nc, selList, size.width, size.height)
                            }
                            if (liveLassoPoints.size >= 2) {
                                NotebookCanvasRenderer.drawLassoPath(nc, liveLassoPoints, size.width, size.height)
                            }
                            if (activeSnapX != null || activeSnapY != null) {
                                NotebookCanvasRenderer.drawSnapGuides(nc, activeSnapX, activeSnapY, size.width, size.height)
                            }
                        }
                    }
                }
                .testTag("notebook_page_sheet")
        )

        // Rich Floating Selection Action Bar (all touch targets >= 48dp, ZERO Modifier.blur())
        val selectedList = remember(selectedObjectIds, content.objects) {
            if (selectedObjectIds.isEmpty()) emptyList() else content.objects.filter { it.id in selectedObjectIds }
        }
        val singleSelected = selectedList.singleOrNull()
        val hasGroup = selectedList.any { it.groupId != null }

        if (selectedList.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.surface.copy(alpha = 0.94f))
                        .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("notebook_selected_item_actions"),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (singleSelected != null) {
                        when (singleSelected) {
                            is TextObject -> SelectedActionPill(Icons.Rounded.Edit, "Editar") { onRequestEditText(singleSelected) }
                            is ShapeObject -> if (singleSelected.kind == NotebookShapeKind.CALLOUT) {
                                SelectedActionPill(Icons.Rounded.Edit, "Texto") { onRequestEditCallout(singleSelected) }
                            }
                            is ImageObject -> {
                                SelectedActionPill(Icons.Rounded.Crop, "Recorte") { onRequestEditImage(singleSelected) }
                                SelectedActionPill(Icons.Rounded.AutoFixHigh, "Sin fondo") { onRemoveImageBackground(singleSelected) }
                            }
                            is TableObject -> SelectedActionPill(Icons.Rounded.TableChart, "Celdas") { onRequestEditTable(singleSelected) }
                            else -> Unit
                        }
                    }

                    if (selectedList.any { it is StrokeObject }) {
                        SelectedActionPill(Icons.Rounded.AutoFixHigh, "A texto", onConvertStrokesToText)
                    }

                    SelectedActionIcon(Icons.Rounded.Flip, "Voltear horizontal", onFlipSelectedH)
                    SelectedActionIcon(Icons.Rounded.SwapVert, "Voltear vertical", onFlipSelectedV)

                    if (selectedList.size >= 2 || hasGroup) {
                        SelectedActionPill(
                            icon = Icons.Rounded.GroupWork,
                            label = if (hasGroup) "Desagrupar" else "Agrupar",
                            onClick = if (hasGroup) onUngroupSelected else onGroupSelected
                        )
                    }

                    SelectedActionIcon(
                        icon = Icons.Rounded.AlignHorizontalCenter,
                        desc = "Alinear y distribuir",
                        tint = if (showAlignPopover) palette.accent else palette.ink,
                        onClick = {
                            showAlignPopover = !showAlignPopover
                            showOpacityPopover = false
                        }
                    )
                    SelectedActionIcon(
                        icon = Icons.Rounded.Opacity,
                        desc = "Opacidad del objeto",
                        tint = if (showOpacityPopover) palette.accent else palette.ink,
                        onClick = {
                            showOpacityPopover = !showOpacityPopover
                            showAlignPopover = false
                        }
                    )
                    SelectedActionIcon(Icons.Rounded.ContentCopy, "Copiar entre páginas", onCopySelected)
                    SelectedActionIcon(Icons.Rounded.FlipToFront, "Traer al frente", onBringToFront)
                    SelectedActionIcon(Icons.Rounded.FlipToBack, "Enviar atrás", onSendToBack)
                    SelectedActionIcon(
                        icon = if (selectedList.all { it.locked }) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        desc = if (selectedList.all { it.locked }) "Desbloquear" else "Bloquear",
                        tint = if (selectedList.all { it.locked }) Color(0xFFD97706) else palette.ink,
                        onClick = onToggleLockSelected
                    )
                    SelectedActionPill(Icons.Rounded.ContentCopy, "Duplicar", onDuplicateSelected)
                    SelectedActionIcon(
                        icon = Icons.Rounded.DeleteOutline,
                        desc = "Eliminar",
                        tint = Color(0xFFE11D48),
                        onClick = onDeleteSelected
                    )
                }

                // Inline Align / Distribute Popover
                if (showAlignPopover) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surface.copy(alpha = 0.96f))
                            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (action in NotebookAlignAction.entries) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surfaceAlt)
                                    .clickable { onAlignSelected(action) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(action.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                            }
                        }
                    }
                }

                // Inline Per-Object Opacity Slider
                if (showOpacityPopover) {
                    val avgOpacity = selectedList.map { it.objectOpacity }.average().toFloat().coerceIn(0.1f, 1f)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surface.copy(alpha = 0.96f))
                            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Opacidad", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted)
                        Slider(
                            value = avgOpacity,
                            onValueChange = onSetSelectedOpacity,
                            valueRange = 0.1f..1f,
                            modifier = Modifier.width(160.dp).height(26.dp),
                            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                        )
                        Text("${(avgOpacity * 100f).roundToInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(palette.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.onAccent)
    }
}

@Composable
private fun SelectedActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val palette = LocalFolioPalette.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint ?: palette.ink, modifier = Modifier.size(19.dp))
    }
}

private data class SnapResult(val dx: Float, val dy: Float, val guideX: Float?, val guideY: Float?)

/**
 * Soft magnetic snap to page center (0.5), page margins (0.05 / 0.95), and sibling object centers/edges.
 */
private fun computeMagneticSnap(
    movingItems: List<NotebookObject>,
    allItems: List<NotebookObject>,
    rawDx: Float,
    rawDy: Float
): SnapResult {
    val movingIds = movingItems.map { it.id }.toSet()
    val minX = movingItems.minOf { it.x } + rawDx
    val maxX = movingItems.maxOf { it.x + it.width } + rawDx
    val centerX = (minX + maxX) * 0.5f

    val minY = movingItems.minOf { it.y } + rawDy
    val maxY = movingItems.maxOf { it.y + it.height } + rawDy
    val centerY = (minY + maxY) * 0.5f

    val xTargets = ArrayList<Float>(16).apply {
        add(0.5f); add(0.05f); add(0.95f)
        for (o in allItems) {
            if (o.id !in movingIds && o.visible && o !is StrokeObject && o !is ConnectorObject) {
                add(o.centerX); add(o.x); add(o.x + o.width)
            }
        }
    }
    val yTargets = ArrayList<Float>(16).apply {
        add(0.5f); add(0.05f); add(0.95f)
        for (o in allItems) {
            if (o.id !in movingIds && o.visible && o !is StrokeObject && o !is ConnectorObject) {
                add(o.centerY); add(o.y); add(o.y + o.height)
            }
        }
    }

    val threshold = 0.013f
    var bestDx = rawDx
    var guideX: Float? = null
    var bestDistX = threshold
    for (t in xTargets) {
        for (candidate in floatArrayOf(centerX, minX, maxX)) {
            val d = abs(t - candidate)
            if (d < bestDistX) {
                bestDistX = d
                bestDx = rawDx + (t - candidate)
                guideX = t
            }
        }
    }

    var bestDy = rawDy
    var guideY: Float? = null
    var bestDistY = threshold
    for (t in yTargets) {
        for (candidate in floatArrayOf(centerY, minY, maxY)) {
            val d = abs(t - candidate)
            if (d < bestDistY) {
                bestDistY = d
                bestDy = rawDy + (t - candidate)
                guideY = t
            }
        }
    }

    return SnapResult(bestDx, bestDy, guideX, guideY)
}

private fun isPointInPolygon(px: Float, py: Float, poly: List<NbPoint>): Boolean {
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val xi = poly[i].x
        val yi = poly[i].y
        val xj = poly[j].x
        val yj = poly[j].y
        val intersect = ((yi > py) != (yj > py)) &&
            (px < (xj - xi) * (py - yi) / ((yj - yi).takeIf { abs(it) > 1e-6f } ?: 1e-6f) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

private fun hitTestInteractiveObjects(content: NotebookPageContent, pt: NbPoint): NotebookObject? {
    val selectable = content.objects
        .filter { it.visible && it !is ConnectorObject && content.isLayerVisible(it.layerId) && !content.isLayerLocked(it.layerId) }
        .sortedByDescending { it.zIndex }
    val pad = 0.022f
    // Prefer non-strokes first, then strokes so user can also lasso/move strokes!
    val nonStrokes = selectable.filter { it !is StrokeObject }
    for (item in nonStrokes) {
        val local = toItemLocalPoint(item, pt)
        if (local.x in (item.x - pad)..(item.x + item.width + pad) &&
            local.y in (item.y - pad)..(item.y + item.height + pad)
        ) {
            return item
        }
    }
    val strokes = selectable.filterIsInstance<StrokeObject>()
    for (s in strokes) {
        if (s.points.any { hypot((it.x - pt.x).toDouble(), (it.y - pt.y).toDouble()) <= 0.022 }) {
            return s
        }
    }
    return null
}

private fun hitTestHandles(item: NotebookObject, pt: NbPoint): ItemDragHandle? {
    val local = toItemLocalPoint(item, pt)
    val tol = 0.042f
    val cx = item.x + item.width * 0.5f
    val rotY = item.y - 0.042f
    if (hypot((local.x - cx).toDouble(), (local.y - rotY).toDouble()) <= tol * 1.15f) {
        return ItemDragHandle.ROTATE
    }
    val l = item.x
    val t = item.y
    val r = item.x + item.width
    val b = item.y + item.height
    return when {
        hypot((local.x - l).toDouble(), (local.y - t).toDouble()) <= tol -> ItemDragHandle.TOP_LEFT
        hypot((local.x - r).toDouble(), (local.y - t).toDouble()) <= tol -> ItemDragHandle.TOP_RIGHT
        hypot((local.x - l).toDouble(), (local.y - b).toDouble()) <= tol -> ItemDragHandle.BOTTOM_LEFT
        hypot((local.x - r).toDouble(), (local.y - b).toDouble()) <= tol -> ItemDragHandle.BOTTOM_RIGHT
        else -> null
    }
}

private fun toItemLocalPoint(item: NotebookObject, pt: NbPoint): NbPoint {
    if (item.rotationDeg == 0f) return pt
    val cx = item.x + item.width * 0.5f
    val cy = item.y + item.height * 0.5f
    val rad = Math.toRadians(-item.rotationDeg.toDouble())
    val dx = pt.x - cx
    val dy = pt.y - cy
    val rx = (dx * cos(rad) - dy * sin(rad)).toFloat() + cx
    val ry = (dx * sin(rad) + dy * cos(rad)).toFloat() + cy
    return NbPoint(rx, ry)
}

private fun applyHandleTransform(
    start: NotebookObject,
    handle: ItemDragHandle,
    origin: NbPoint,
    current: NbPoint,
    lockAspectRatio: Boolean
): NotebookObject {
    val dx = current.x - origin.x
    val dy = current.y - origin.y
    val aspect = (start.width / start.height.coerceAtLeast(0.01f)).coerceIn(0.15f, 8f)

    return when (handle) {
        ItemDragHandle.MOVE -> start.movedBy(dx, dy)
        ItemDragHandle.ROTATE -> {
            val cx = start.x + start.width * 0.5f
            val cy = start.y + start.height * 0.5f
            val angle = Math.toDegrees(atan2((current.y - cy).toDouble(), (current.x - cx).toDouble())).toFloat() + 90f
            val snapped = if (abs(angle % 45f) < 4f) (round(angle / 45f) * 45f) else angle
            start.rotatedTo(snapped)
        }
        ItemDragHandle.BOTTOM_RIGHT -> {
            val rawW = (start.width + dx).coerceAtLeast(0.05f)
            val rawH = if (lockAspectRatio) (rawW / aspect).coerceAtLeast(0.04f) else (start.height + dy).coerceAtLeast(0.04f)
            start.resizedTo(start.x, start.y, rawW, rawH)
        }
        ItemDragHandle.BOTTOM_LEFT -> {
            val rawW = (start.width - dx).coerceAtLeast(0.05f)
            val rawH = if (lockAspectRatio) (rawW / aspect).coerceAtLeast(0.04f) else (start.height + dy).coerceAtLeast(0.04f)
            start.resizedTo(start.x + (start.width - rawW), start.y, rawW, rawH)
        }
        ItemDragHandle.TOP_RIGHT -> {
            val rawW = (start.width + dx).coerceAtLeast(0.05f)
            val rawH = if (lockAspectRatio) (rawW / aspect).coerceAtLeast(0.04f) else (start.height - dy).coerceAtLeast(0.04f)
            start.resizedTo(start.x, start.y + (start.height - rawH), rawW, rawH)
        }
        ItemDragHandle.TOP_LEFT -> {
            val rawW = (start.width - dx).coerceAtLeast(0.05f)
            val rawH = if (lockAspectRatio) (rawW / aspect).coerceAtLeast(0.04f) else (start.height - dy).coerceAtLeast(0.04f)
            start.resizedTo(start.x + (start.width - rawW), start.y + (start.height - rawH), rawW, rawH)
        }
    }
}

private fun collectHitStrokes(content: NotebookPageContent, pt: NbPoint, out: MutableSet<String>) {
    val radius = 0.024
    for (obj in content.objects) {
        if (obj !is StrokeObject || !obj.visible || obj.locked || obj.id in out) continue
        if (!content.isLayerVisible(obj.layerId) || content.isLayerLocked(obj.layerId)) continue
        for (p in obj.points) {
            if (hypot((p.x - pt.x).toDouble(), (p.y - pt.y).toDouble()) <= radius) {
                out.add(obj.id)
                break
            }
        }
    }
}

private fun buildShapeFromDrag(
    kind: NotebookShapeKind,
    start: NbPoint,
    end: NbPoint,
    layerId: String,
    colorArgb: Long,
    fillEnabled: Boolean,
    isDashed: Boolean,
    hasShadow: Boolean,
    opacity: Float,
    strokeWidthNorm: Float
): ShapeObject {
    val minX = min(start.x, end.x)
    val minY = min(start.y, end.y)
    val w = max(abs(end.x - start.x), 0.04f)
    val h = max(abs(end.y - start.y), 0.04f)
    val softFill = if (fillEnabled) {
        val c = colorArgb.toInt()
        (0x36000000L or (c.toLong() and 0x00FFFFFFL))
    } else null

    return ShapeObject(
        layerId = layerId,
        x = minX,
        y = minY,
        width = w,
        height = h,
        kind = kind,
        startX = start.x,
        startY = start.y,
        endX = end.x,
        endY = end.y,
        midX = if (kind == NotebookShapeKind.POLY_ARROW) end.x else null,
        midY = if (kind == NotebookShapeKind.POLY_ARROW) start.y else null,
        strokeColorArgb = colorArgb,
        fillColorArgb = softFill,
        strokeWidthNorm = strokeWidthNorm.coerceAtLeast(0.0036f),
        isDashed = isDashed,
        opacity = opacity,
        hasShadow = hasShadow,
        labelText = if (kind == NotebookShapeKind.CALLOUT) "Etiqueta" else ""
    )
}

private fun buildStampShape(
    kind: NotebookShapeKind,
    center: NbPoint,
    layerId: String,
    colorArgb: Long,
    opacity: Float,
    strokeWidthNorm: Float,
    labelText: String = ""
): ShapeObject {
    val w = if (kind == NotebookShapeKind.NUMBER_BADGE) 0.056f else 0.068f
    val h = if (kind == NotebookShapeKind.NUMBER_BADGE) 0.044f else 0.048f
    val x = (center.x - w * 0.5f).coerceIn(0.01f, 0.92f)
    val y = (center.y - h * 0.5f).coerceIn(0.01f, 0.94f)
    return ShapeObject(
        layerId = layerId,
        x = x,
        y = y,
        width = w,
        height = h,
        kind = kind,
        startX = x,
        startY = y,
        endX = x + w,
        endY = y + h,
        strokeColorArgb = colorArgb,
        opacity = opacity,
        strokeWidthNorm = strokeWidthNorm.coerceAtLeast(0.0042f),
        labelText = labelText
    )
}

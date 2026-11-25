package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.StrokePoint
import com.example.ui.theme.LocalFolioPalette
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

val DrawingPaletteColors = listOf(
    0xFF1D5BFFL, // Cobalt
    0xFF151C2BL, // Graphite Ink
    0xFFFF5A67L, // Coral Red
    0xFFF5B731L, // Warm Amber
    0xFF1EB974L, // Emerald
    0xFF7C3AEDL  // Study Violet
)

private fun DrawingTool.isGeometricShape(): Boolean =
    this == DrawingTool.LINE || this == DrawingTool.ARROW || this == DrawingTool.RECTANGLE || this == DrawingTool.CIRCLE

private fun DrawingTool.isFillableShape(): Boolean =
    this == DrawingTool.RECTANGLE || this == DrawingTool.CIRCLE

private fun buildShapePath(
    target: Path,
    tool: DrawingTool,
    start: StrokePoint,
    end: StrokePoint,
    width: Float,
    height: Float
) {
    target.reset()
    val x0 = start.x * width
    val y0 = start.y * height
    val x1 = end.x * width
    val y1 = end.y * height
    when (tool) {
        DrawingTool.LINE -> {
            target.moveTo(x0, y0)
            target.lineTo(x1, y1)
        }
        DrawingTool.ARROW -> {
            target.moveTo(x0, y0)
            target.lineTo(x1, y1)
            val len = hypot(x1 - x0, y1 - y0)
            if (len > 4f) {
                val angle = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
                val headLen = (len * 0.24f).coerceIn(14f, 52f)
                val spread = Math.toRadians(28.0)
                val hx1 = x1 - (headLen * cos(angle - spread)).toFloat()
                val hy1 = y1 - (headLen * sin(angle - spread)).toFloat()
                val hx2 = x1 - (headLen * cos(angle + spread)).toFloat()
                val hy2 = y1 - (headLen * sin(angle + spread)).toFloat()
                target.moveTo(x1, y1)
                target.lineTo(hx1, hy1)
                target.moveTo(x1, y1)
                target.lineTo(hx2, hy2)
            }
        }
        DrawingTool.RECTANGLE -> {
            target.addRect(
                Rect(
                    left = min(x0, x1),
                    top = min(y0, y1),
                    right = max(x0, x1),
                    bottom = max(y0, y1)
                )
            )
        }
        DrawingTool.CIRCLE -> {
            target.addOval(
                Rect(
                    left = min(x0, x1),
                    top = min(y0, y1),
                    right = max(x0, x1),
                    bottom = max(y0, y1)
                )
            )
        }
        else -> {
            target.moveTo(x0, y0)
            target.lineTo(x1, y1)
        }
    }
}

private class CachedStrokeRender(
    val path: Path,
    val color: Color,
    val stroke: Stroke,
    val isFilled: Boolean
)

private class DrawingStrokePathCache {
    private var lastStrokes: List<DrawingStroke>? = null
    private var lastWidth: Float = -1f
    private var lastHeight: Float = -1f
    private var cached: List<CachedStrokeRender> = emptyList()

    fun getOrBuild(strokes: List<DrawingStroke>, width: Float, height: Float): List<CachedStrokeRender> {
        if (strokes === lastStrokes && width == lastWidth && height == lastHeight) {
            return cached
        }
        lastStrokes = strokes
        lastWidth = width
        lastHeight = height
        cached = strokes.mapNotNull { stroke ->
            if (stroke.points.size < 2) return@mapNotNull null
            val path = Path()
            if (stroke.tool.isGeometricShape()) {
                buildShapePath(path, stroke.tool, stroke.points.first(), stroke.points.last(), width, height)
            } else {
                val first = stroke.points[0]
                path.moveTo(first.x * width, first.y * height)
                for (i in 1 until stroke.points.size) {
                    val prev = stroke.points[i - 1]
                    val curr = stroke.points[i]
                    val midX = ((prev.x + curr.x) * 0.5f) * width
                    val midY = ((prev.y + curr.y) * 0.5f) * height
                    path.quadraticTo(prev.x * width, prev.y * height, midX, midY)
                }
                val last = stroke.points[stroke.points.lastIndex]
                path.lineTo(last.x * width, last.y * height)
            }
            CachedStrokeRender(
                path = path,
                color = Color(stroke.colorArgb.toInt()).copy(alpha = stroke.alpha),
                stroke = Stroke(
                    width = (stroke.widthNorm * width).coerceIn(2f, 44f),
                    cap = StrokeCap.Round,
                    join = if (stroke.tool == DrawingTool.RECTANGLE) StrokeJoin.Miter else StrokeJoin.Round
                ),
                isFilled = stroke.isFilled && stroke.tool.isFillableShape()
            )
        }
        return cached
    }
}

private class LiveStrokeBuffer {
    val points = ArrayList<StrokePoint>(128)
    val path = Path()
    var isActive: Boolean = false
    var tool: DrawingTool = DrawingTool.PEN
    var colorArgb: Long = 0xFF1D5BFFL
    var alpha: Float = 1f
    var widthNorm: Float = 0.008f
    var isFilled: Boolean = false
    private var lastPxX: Float = 0f
    private var lastPxY: Float = 0f

    fun start(
        firstPt: StrokePoint,
        canvasW: Float,
        canvasH: Float,
        tool: DrawingTool,
        colorArgb: Long,
        widthNorm: Float,
        alpha: Float,
        shapeFilled: Boolean
    ) {
        points.clear()
        points.add(firstPt)
        path.reset()
        val x = firstPt.x * canvasW
        val y = firstPt.y * canvasH
        path.moveTo(x, y)
        lastPxX = x
        lastPxY = y
        this.tool = tool
        this.colorArgb = colorArgb
        this.alpha = alpha.coerceIn(0.15f, 1f)
        this.widthNorm = if (tool == DrawingTool.HIGHLIGHTER) widthNorm * 2.7f else widthNorm
        this.isFilled = shapeFilled && tool.isFillableShape()
        this.isActive = true
    }

    fun append(nextPt: StrokePoint, canvasW: Float, canvasH: Float): Boolean {
        if (tool.isGeometricShape()) {
            val startPt = points.firstOrNull() ?: return false
            if (points.size == 1) {
                points.add(nextPt)
            } else {
                val prevEnd = points[1]
                if (hypot(nextPt.x - prevEnd.x, nextPt.y - prevEnd.y) <= 0.001f) return false
                points[1] = nextPt
            }
            buildShapePath(path, tool, startPt, nextPt, canvasW, canvasH)
            return true
        }
        val last = points.lastOrNull()
        if (last != null && hypot(nextPt.x - last.x, nextPt.y - last.y) <= 0.0025f) {
            return false
        }
        points.add(nextPt)
        val x = nextPt.x * canvasW
        val y = nextPt.y * canvasH
        val midX = (lastPxX + x) * 0.5f
        val midY = (lastPxY + y) * 0.5f
        path.quadraticTo(lastPxX, lastPxY, midX, midY)
        lastPxX = x
        lastPxY = y
        return true
    }

    fun finish(): List<StrokePoint> {
        isActive = false
        val snapshot = if (points.size >= 2) {
            if (tool.isGeometricShape()) {
                val a = points.first()
                val b = points.last()
                if (hypot(b.x - a.x, b.y - a.y) >= 0.006f) listOf(a, b) else emptyList()
            } else {
                points.toList()
            }
        } else {
            emptyList()
        }
        points.clear()
        path.reset()
        return snapshot
    }
}

/**
 * Renders persisted normalized strokes & Paint-style shapes over any page (EPUB/DOCX/PDF/Manhwa).
 * Zero-recomposition live drawing:
 * - Freehand strokes and geometric shapes (LINE, RECTANGLE, CIRCLE) update a reusable [Path] in O(1).
 * - Draw phase invalidates via a single [mutableIntStateOf] read strictly inside [drawBehind].
 * - Persisted strokes are cached as pre-built [Path] objects per canvas dimensions.
 * - Persists to storage ONLY when the stroke finishes (pointer release).
 */
@Composable
fun DrawingCanvasOverlay(
    strokes: List<DrawingStroke>,
    isInteractive: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float = 1f,
    activeShapeFilled: Boolean = false,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEraseStrokeIds: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveBuffer = remember { LiveStrokeBuffer() }
    val staticPathCache = remember { DrawingStrokePathCache() }
    // Read ONLY inside drawBehind -> 0 recompositions on every pointer move!
    val drawTick = remember { mutableIntStateOf(0) }

    val latestStrokes = rememberUpdatedState(strokes)
    val latestTool = rememberUpdatedState(activeTool)
    val latestColor = rememberUpdatedState(activeColorArgb)
    val latestWidth = rememberUpdatedState(activeWidthNorm)
    val latestAlpha = rememberUpdatedState(activeAlpha)
    val latestFilled = rememberUpdatedState(activeShapeFilled)
    val latestOnStrokeCompleted = rememberUpdatedState(onStrokeCompleted)
    val latestOnErase = rememberUpdatedState(onEraseStrokeIds)

    val inputModifier = if (isInteractive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)

                val firstPt = StrokePoint(
                    x = (down.position.x / w).coerceIn(0f, 1f),
                    y = (down.position.y / h).coerceIn(0f, 1f)
                )

                val tool = latestTool.value
                if (tool == DrawingTool.ERASER) {
                    val erasedAlready = mutableSetOf<String>()
                    val firstHits = mutableSetOf<String>()
                    findHitStrokes(latestStrokes.value, firstPt, 0.035f, firstHits)
                    if (firstHits.isNotEmpty()) {
                        erasedAlready.addAll(firstHits)
                        latestOnErase.value(firstHits)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val pt = StrokePoint(
                            x = (change.position.x / w).coerceIn(0f, 1f),
                            y = (change.position.y / h).coerceIn(0f, 1f)
                        )
                        val newlyHit = mutableSetOf<String>()
                        findHitStrokes(latestStrokes.value, pt, 0.038f, newlyHit)
                        newlyHit.removeAll(erasedAlready)
                        if (newlyHit.isNotEmpty()) {
                            erasedAlready.addAll(newlyHit)
                            latestOnErase.value(newlyHit)
                        }
                    }
                } else {
                    val color = latestColor.value
                    val widthNorm = latestWidth.value
                    val effectiveAlpha = if (tool == DrawingTool.HIGHLIGHTER && latestAlpha.value >= 0.98f) {
                        0.36f
                    } else {
                        latestAlpha.value
                    }
                    val shapeFilled = latestFilled.value
                    liveBuffer.start(firstPt, w, h, tool, color, widthNorm, effectiveAlpha, shapeFilled)
                    drawTick.intValue = drawTick.intValue + 1
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        val nextPt = StrokePoint(
                            x = (change.position.x / w).coerceIn(0f, 1f),
                            y = (change.position.y / h).coerceIn(0f, 1f)
                        )
                        if (liveBuffer.append(nextPt, w, h)) {
                            drawTick.intValue = drawTick.intValue + 1
                        }
                    }
                    val alpha = liveBuffer.alpha
                    val width = liveBuffer.widthNorm
                    val filled = liveBuffer.isFilled
                    val completedPoints = liveBuffer.finish()
                    drawTick.intValue = drawTick.intValue + 1
                    if (completedPoints.size >= 2) {
                        latestOnStrokeCompleted.value(
                            DrawingStroke(
                                id = UUID.randomUUID().toString(),
                                tool = tool,
                                colorArgb = color,
                                widthNorm = width,
                                alpha = alpha,
                                points = completedPoints,
                                isFilled = filled
                            )
                        )
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(inputModifier)
            .drawBehind {
                // Subscribe draw phase ONLY to live stroke ticks (0 composition invalidations)
                @Suppress("UNUSED_VARIABLE")
                val tick = drawTick.intValue
                val persisted = staticPathCache.getOrBuild(latestStrokes.value, size.width, size.height)
                for (item in persisted) {
                    if (item.isFilled) {
                        drawPath(path = item.path, color = item.color, style = Fill)
                    }
                    drawPath(
                        path = item.path,
                        color = item.color,
                        style = item.stroke
                    )
                }
                if (liveBuffer.isActive && liveBuffer.points.size >= 2) {
                    val liveColor = Color(liveBuffer.colorArgb.toInt()).copy(alpha = liveBuffer.alpha)
                    if (liveBuffer.isFilled) {
                        drawPath(path = liveBuffer.path, color = liveColor, style = Fill)
                    }
                    drawPath(
                        path = liveBuffer.path,
                        color = liveColor,
                        style = Stroke(
                            width = (liveBuffer.widthNorm * size.width).coerceIn(2f, 44f),
                            cap = StrokeCap.Round,
                            join = if (liveBuffer.tool == DrawingTool.RECTANGLE) StrokeJoin.Miter else StrokeJoin.Round
                        )
                    )
                }
            }
            .testTag("drawing_canvas_overlay")
    )
}

private fun distancePointToSegment(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float
): Float {
    val abx = bx - ax
    val aby = by - ay
    val lenSq = abx * abx + aby * aby
    if (lenSq <= 1e-8f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * abx + (py - ay) * aby) / lenSq).coerceIn(0f, 1f)
    val projX = ax + t * abx
    val projY = ay + t * aby
    return hypot(px - projX, py - projY)
}

private fun findHitStrokes(
    strokes: List<DrawingStroke>,
    point: StrokePoint,
    threshold: Float,
    outIds: MutableSet<String>
) {
    val thresholdSq = threshold * threshold
    for (stroke in strokes) {
        if (stroke.points.size < 2) continue
        when (stroke.tool) {
            DrawingTool.LINE, DrawingTool.ARROW -> {
                val a = stroke.points.first()
                val b = stroke.points.last()
                if (distancePointToSegment(point.x, point.y, a.x, a.y, b.x, b.y) <= threshold) {
                    outIds.add(stroke.id)
                }
            }
            DrawingTool.RECTANGLE -> {
                val a = stroke.points.first()
                val b = stroke.points.last()
                val left = min(a.x, b.x)
                val right = max(a.x, b.x)
                val top = min(a.y, b.y)
                val bottom = max(a.y, b.y)
                if (stroke.isFilled &&
                    point.x in (left - threshold)..(right + threshold) &&
                    point.y in (top - threshold)..(bottom + threshold)
                ) {
                    outIds.add(stroke.id)
                } else {
                    val hitTop = distancePointToSegment(point.x, point.y, left, top, right, top) <= threshold
                    val hitBottom = distancePointToSegment(point.x, point.y, left, bottom, right, bottom) <= threshold
                    val hitLeft = distancePointToSegment(point.x, point.y, left, top, left, bottom) <= threshold
                    val hitRight = distancePointToSegment(point.x, point.y, right, top, right, bottom) <= threshold
                    if (hitTop || hitBottom || hitLeft || hitRight) {
                        outIds.add(stroke.id)
                    }
                }
            }
            DrawingTool.CIRCLE -> {
                val a = stroke.points.first()
                val b = stroke.points.last()
                val cx = (a.x + b.x) * 0.5f
                val cy = (a.y + b.y) * 0.5f
                val rx = (abs(b.x - a.x) * 0.5f).coerceAtLeast(0.005f)
                val ry = (abs(b.y - a.y) * 0.5f).coerceAtLeast(0.005f)
                val dx = point.x - cx
                val dy = point.y - cy
                val normDist = hypot(dx / rx, dy / ry)
                if (stroke.isFilled && normDist <= 1.05f) {
                    outIds.add(stroke.id)
                } else {
                    val approxDist = abs(normDist - 1f) * min(rx, ry)
                    if (approxDist <= threshold) {
                        outIds.add(stroke.id)
                    }
                }
            }
            else -> {
                for (pt in stroke.points) {
                    val dx = pt.x - point.x
                    val dy = pt.y - point.y
                    if (dx * dx + dy * dy <= thresholdSq) {
                        outIds.add(stroke.id)
                        break
                    }
                }
            }
        }
    }
}

@Composable
fun DrawingFloatingToolbar(
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float = 1f,
    activeShapeFilled: Boolean = false,
    customColors: List<Long> = emptyList(),
    canUndo: Boolean,
    canRedo: Boolean,
    isStandardPageView: Boolean = false,
    standardFormatLabel: String = "A4",
    showStandardViewToggle: Boolean = true,
    canTogglePanZoom: Boolean = false,
    isPanZoomMode: Boolean = false,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onAddCustomColor: (Long) -> Unit = onSelectColor,
    onWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit = {},
    onToggleShapeFilled: () -> Unit = {},
    onToggleStandardPageView: () -> Unit = {},
    onTogglePanZoomMode: () -> Unit = {},
    onApplySignaturePreset: () -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= 680.dp) {
            TabletDrawingStudioDock(
                activeTool = activeTool,
                activeColorArgb = activeColorArgb,
                activeWidthNorm = activeWidthNorm,
                activeAlpha = activeAlpha,
                activeShapeFilled = activeShapeFilled,
                customColors = customColors,
                canUndo = canUndo,
                canRedo = canRedo,
                isStandardPageView = isStandardPageView,
                standardFormatLabel = standardFormatLabel,
                showStandardViewToggle = showStandardViewToggle,
                canTogglePanZoom = canTogglePanZoom,
                isPanZoomMode = isPanZoomMode,
                onSelectTool = onSelectTool,
                onSelectColor = onSelectColor,
                onAddCustomColor = onAddCustomColor,
                onWidthChange = onWidthChange,
                onAlphaChange = onAlphaChange,
                onToggleShapeFilled = onToggleShapeFilled,
                onToggleStandardPageView = onToggleStandardPageView,
                onTogglePanZoomMode = onTogglePanZoomMode,
                onApplySignaturePreset = onApplySignaturePreset,
                onUndo = onUndo,
                onRedo = onRedo,
                onClearPage = onClearPage,
                onDone = onDone
            )
        } else {
            PhoneDrawingBottomToolbar(
                activeTool = activeTool,
                activeColorArgb = activeColorArgb,
                activeWidthNorm = activeWidthNorm,
                activeAlpha = activeAlpha,
                activeShapeFilled = activeShapeFilled,
                customColors = customColors,
                canUndo = canUndo,
                canRedo = canRedo,
                isStandardPageView = isStandardPageView,
                standardFormatLabel = standardFormatLabel,
                showStandardViewToggle = showStandardViewToggle,
                canTogglePanZoom = canTogglePanZoom,
                isPanZoomMode = isPanZoomMode,
                onSelectTool = onSelectTool,
                onSelectColor = onSelectColor,
                onAddCustomColor = onAddCustomColor,
                onWidthChange = onWidthChange,
                onAlphaChange = onAlphaChange,
                onToggleShapeFilled = onToggleShapeFilled,
                onToggleStandardPageView = onToggleStandardPageView,
                onTogglePanZoomMode = onTogglePanZoomMode,
                onApplySignaturePreset = onApplySignaturePreset,
                onUndo = onUndo,
                onRedo = onRedo,
                onClearPage = onClearPage,
                onDone = onDone,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PhoneDrawingBottomToolbar(
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    activeAlpha: Float,
    activeShapeFilled: Boolean,
    customColors: List<Long>,
    canUndo: Boolean,
    canRedo: Boolean,
    isStandardPageView: Boolean,
    standardFormatLabel: String,
    showStandardViewToggle: Boolean,
    canTogglePanZoom: Boolean,
    isPanZoomMode: Boolean,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onAddCustomColor: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onToggleShapeFilled: () -> Unit,
    onToggleStandardPageView: () -> Unit,
    onTogglePanZoomMode: () -> Unit,
    onApplySignaturePreset: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val shape = RoundedCornerShape(26.dp)
    var showColorCreator by remember { mutableStateOf(false) }
    val isSignaturePresetActive = !isPanZoomMode &&
        activeTool == DrawingTool.PEN &&
        activeWidthNorm <= 0.005f &&
        (activeColorArgb == 0xFF141B29L || activeColorArgb == 0xFF1D5BFFL)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(palette.surface.copy(alpha = 0.94f))
            .border(1.5.dp, palette.border, shape)
            .padding(12.dp)
            .testTag("drawing_toolbar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 0: Quick Work / Signature Strip + Paint Shapes + Fill Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarModeChip(
                icon = Icons.Rounded.Draw,
                label = "Firma fina",
                selected = isSignaturePresetActive,
                onClick = onApplySignaturePreset,
                testTag = "draw_signature_preset"
            )
            ToolbarModeChip(
                icon = Icons.Rounded.HorizontalRule,
                label = "Línea",
                selected = !isPanZoomMode && activeTool == DrawingTool.LINE,
                onClick = { onSelectTool(DrawingTool.LINE) },
                testTag = "draw_tool_line"
            )
            ToolbarModeChip(
                icon = Icons.Rounded.NorthEast,
                label = "Flecha",
                selected = !isPanZoomMode && activeTool == DrawingTool.ARROW,
                onClick = { onSelectTool(DrawingTool.ARROW) },
                testTag = "draw_tool_arrow"
            )
            ToolbarModeChip(
                icon = Icons.Rounded.CropSquare,
                label = "Cuadrado",
                selected = !isPanZoomMode && activeTool == DrawingTool.RECTANGLE,
                onClick = { onSelectTool(DrawingTool.RECTANGLE) },
                testTag = "draw_tool_rectangle"
            )
            ToolbarModeChip(
                icon = Icons.Rounded.RadioButtonUnchecked,
                label = "Círculo",
                selected = !isPanZoomMode && activeTool == DrawingTool.CIRCLE,
                onClick = { onSelectTool(DrawingTool.CIRCLE) },
                testTag = "draw_tool_circle"
            )
            if (activeTool == DrawingTool.RECTANGLE || activeTool == DrawingTool.CIRCLE || activeShapeFilled) {
                ToolbarModeChip(
                    icon = Icons.Rounded.FormatColorFill,
                    label = if (activeShapeFilled) "Relleno" else "Contorno",
                    selected = activeShapeFilled,
                    onClick = onToggleShapeFilled,
                    testTag = "draw_toggle_fill"
                )
            }
            if (showStandardViewToggle) {
                ToolbarModeChip(
                    icon = Icons.Rounded.Description,
                    label = if (isStandardPageView) "Hoja · $standardFormatLabel" else "Hoja estándar",
                    selected = isStandardPageView,
                    onClick = onToggleStandardPageView,
                    testTag = "draw_toggle_standard_view"
                )
            }
            if (canTogglePanZoom) {
                ToolbarModeChip(
                    icon = Icons.Rounded.OpenWith,
                    label = if (isPanZoomMode) "Zoom activo" else "Mover / Zoom",
                    selected = isPanZoomMode,
                    onClick = onTogglePanZoomMode,
                    testTag = "draw_toggle_pan_zoom"
                )
            }
        }

        // Row 1: Freehand Tools + Undo / Redo / Clear / Done
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TactileIconButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "Lápiz",
                    selected = !isPanZoomMode && activeTool == DrawingTool.PEN,
                    onClick = { onSelectTool(DrawingTool.PEN) },
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_tool_pen")
                )
                TactileIconButton(
                    icon = Icons.Rounded.BorderColor,
                    contentDescription = "Resaltador translúcido",
                    selected = !isPanZoomMode && activeTool == DrawingTool.HIGHLIGHTER,
                    onClick = { onSelectTool(DrawingTool.HIGHLIGHTER) },
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_tool_highlighter")
                )
                TactileIconButton(
                    icon = Icons.Rounded.AutoFixNormal,
                    contentDescription = "Borrador",
                    selected = !isPanZoomMode && activeTool == DrawingTool.ERASER,
                    onClick = { onSelectTool(DrawingTool.ERASER) },
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_tool_eraser")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TactileIconButton(
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Deshacer",
                    onClick = { if (canUndo) onUndo() },
                    contentColor = if (canUndo) palette.ink else palette.inkMuted.copy(alpha = 0.4f),
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_undo")
                )
                TactileIconButton(
                    icon = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = "Rehacer",
                    onClick = { if (canRedo) onRedo() },
                    contentColor = if (canRedo) palette.ink else palette.inkMuted.copy(alpha = 0.4f),
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_redo")
                )
                TactileIconButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Limpiar trazos de página",
                    onClick = onClearPage,
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_clear")
                )
                TactileIconButton(
                    icon = Icons.Rounded.Check,
                    contentDescription = "Terminar dibujo",
                    selected = true,
                    onClick = onDone,
                    size = 44.dp,
                    modifier = Modifier.testTag("draw_done")
                )
            }
        }

        // Row 2: Color Swatches + Custom Color Creator + Thickness & Opacity
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val allSwatches = remember(customColors) {
                (DrawingPaletteColors + customColors).distinct()
            }
            for (hex in allSwatches) {
                val selected = activeColorArgb == hex && activeTool != DrawingTool.ERASER
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(hex.toInt()))
                        .border(
                            width = if (selected) 3.dp else 1.5.dp,
                            color = if (selected) palette.ink else palette.border,
                            shape = CircleShape
                        )
                        .clickable {
                            if (activeTool == DrawingTool.ERASER) onSelectTool(DrawingTool.PEN)
                            onSelectColor(hex)
                        }
                )
            }
            PaintColorAddBadge(onClick = { showColorCreator = true })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Grosor",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.inkMuted
            )
            Slider(
                value = activeWidthNorm,
                onValueChange = onWidthChange,
                valueRange = 0.0035f..0.022f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
            )
            Text(
                text = "Opac. ${(activeAlpha * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.inkMuted
            )
            Slider(
                value = activeAlpha,
                onValueChange = onAlphaChange,
                valueRange = 0.15f..1.0f,
                modifier = Modifier.weight(0.85f).testTag("draw_opacity_slider"),
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
            )
        }
    }

    if (showColorCreator) {
        PaintColorCreatorDialog(
            initialColorArgb = activeColorArgb,
            onConfirmColor = onAddCustomColor,
            onDismiss = { showColorCreator = false }
        )
    }
}

@Composable
internal fun ToolbarModeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.surfaceAlt)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent else palette.border,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else palette.ink,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else palette.ink
        )
    }
}

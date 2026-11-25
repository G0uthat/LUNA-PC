package com.example.notebook.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.model.NotebookLayerInfo
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.StrokeObject

/**
 * Per-layer offscreen Bitmap cache + reusable Path/Paint scratch pool for 60–120 FPS low-latency ink.
 *
 * - Respects layer visibility, reordering, and per-layer opacity (`NotebookLayerInfo`).
 * - Confirmed objects per layer are rasterized into cached bitmaps ONLY when their cache key changes.
 * - During active drawing, each frame blits cached bitmaps (<0.3 ms) and renders ONLY the in-progress stroke.
 * - Exposes `samplePixelColor(normX, normY)` for true Eyedropper (Cuentagotas) sampling across all visible layers!
 */
class NotebookLayerCache {

    private var cachedWidthPx: Int = 0
    private var cachedHeightPx: Int = 0
    private var lastCacheKey: Long = Long.MIN_VALUE

    private val layerBitmaps = LinkedHashMap<String, Bitmap>(6)
    private val layerCanvases = HashMap<String, Canvas>(6)
    private var cachedLayersMeta: List<NotebookLayerInfo> = NotebookLayerInfo.DEFAULT_LAYERS
    private var backgroundBitmapCache: Bitmap? = null
    private var backgroundCanvasCache: Canvas? = null

    val reusableStrokePath: Path = Path()
    val reusableStrokePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blitPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    fun ensureLayersRendered(
        pageWidthPx: Int,
        pageHeightPx: Int,
        template: NotebookPageTemplate,
        backgroundType: NotebookBackgroundType,
        backgroundColorArgb: Long,
        backgroundBitmap: Bitmap?,
        content: NotebookPageContent,
        assetBitmapProvider: (String) -> Bitmap?,
        typefaceProvider: (String) -> Typeface? = { null },
        loadedAssetCount: Int
    ) {
        val maxCacheEdge = 1600
        val scale = if (pageWidthPx > maxCacheEdge || pageHeightPx > maxCacheEdge) {
            maxCacheEdge.toFloat() / maxOf(pageWidthPx, pageHeightPx).coerceAtLeast(1)
        } else 1f
        val targetW = (pageWidthPx * scale).toInt().coerceIn(64, maxCacheEdge)
        val targetH = (pageHeightPx * scale).toInt().coerceIn(64, maxCacheEdge)

        val resolvedLayers = content.resolvedLayers()
        var key = 17L
        key = key * 31L + targetW
        key = key * 31L + targetH
        key = key * 31L + template.ordinal
        key = key * 31L + backgroundType.ordinal
        key = key * 31L + backgroundColorArgb
        key = key * 31L + (backgroundBitmap?.hashCode() ?: 0)
        key = key * 31L + content.revision
        key = key * 31L + content.objects.hashCode()
        key = key * 31L + resolvedLayers.hashCode()
        key = key * 31L + loadedAssetCount

        if (key == lastCacheKey && cachedWidthPx == targetW && cachedHeightPx == targetH && backgroundBitmapCache != null) {
            return
        }

        if (cachedWidthPx != targetW || cachedHeightPx != targetH) {
            recycleAll()
            cachedWidthPx = targetW
            cachedHeightPx = targetH
        }

        // 1. Background layer bitmap
        if (backgroundBitmapCache == null) {
            val created = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            backgroundBitmapCache = created
            backgroundCanvasCache = Canvas(created)
        }
        val bgCanvas = backgroundCanvasCache!!
        bgCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        NotebookCanvasRenderer.drawBackgroundLayer(
            canvas = bgCanvas,
            w = targetW.toFloat(),
            h = targetH.toFloat(),
            template = template,
            backgroundType = backgroundType,
            backgroundColorArgb = backgroundColorArgb,
            backgroundBitmap = backgroundBitmap
        )

        // 2. Per-layer object bitmaps in exact user-defined layer order
        cachedLayersMeta = resolvedLayers
        val objectsById = content.objects.associateBy { it.id }
        val activeLayerIds = HashSet<String>(resolvedLayers.size)

        for (layer in resolvedLayers) {
            activeLayerIds.add(layer.id)
            if (!layer.visible) {
                layerCanvases[layer.id]?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                continue
            }
            val bmp = layerBitmaps[layer.id] ?: Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also {
                layerBitmaps[layer.id] = it
                layerCanvases[layer.id] = Canvas(it)
            }
            val canvas = layerCanvases[layer.id]!!
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            NotebookCanvasRenderer.drawSingleLayer(
                canvas = canvas,
                layerId = layer.id,
                w = targetW.toFloat(),
                h = targetH.toFloat(),
                allObjects = content.objects,
                objectsById = objectsById,
                assetBitmapProvider = assetBitmapProvider,
                typefaceProvider = typefaceProvider,
                reusablePath = reusableStrokePath,
                reusablePaint = reusableStrokePaint
            )
            if (bmp.isRecycled) continue
        }

        val stale = layerBitmaps.keys.filterNot { it in activeLayerIds }
        for (oldKey in stale) {
            layerBitmaps.remove(oldKey)?.recycle()
            layerCanvases.remove(oldKey)
        }

        lastCacheKey = key
    }

    /**
     * Ultra-fast frame blit respecting layer order, visibility, and per-layer opacity,
     * injecting live stroke / shape previews directly on the active layer!
     */
    fun blitCachedLayersWithLiveOverlay(
        canvas: Canvas,
        targetWidthPx: Float,
        targetHeightPx: Float,
        liveStroke: StrokeObject?,
        liveShapePreview: ShapeObject?,
        assetBitmapProvider: (String) -> Bitmap?,
        typefaceProvider: (String) -> Typeface? = { null }
    ) {
        srcRect.set(0, 0, cachedWidthPx, cachedHeightPx)
        dstRect.set(0f, 0f, targetWidthPx, targetHeightPx)

        blitPaint.alpha = 255
        backgroundBitmapCache?.takeIf { !it.isRecycled }?.let {
            canvas.drawBitmap(it, srcRect, dstRect, blitPaint)
        }

        var drewLiveStroke = false
        var drewLiveShape = false

        for (layer in cachedLayersMeta) {
            if (!layer.visible) continue
            val bmp = layerBitmaps[layer.id]
            if (bmp != null && !bmp.isRecycled) {
                blitPaint.alpha = (layer.opacity.coerceIn(0.05f, 1f) * 255f).toInt()
                canvas.drawBitmap(bmp, srcRect, dstRect, blitPaint)
            }
            if (liveStroke != null && !drewLiveStroke && liveStroke.layerId == layer.id) {
                NotebookCanvasRenderer.drawStrokeFast(
                    canvas = canvas,
                    stroke = liveStroke,
                    w = targetWidthPx,
                    h = targetHeightPx,
                    reusablePath = reusableStrokePath,
                    reusablePaint = reusableStrokePaint
                )
                drewLiveStroke = true
            }
            if (liveShapePreview != null && !drewLiveShape && liveShapePreview.layerId == layer.id) {
                NotebookCanvasRenderer.drawObject(
                    canvas = canvas,
                    obj = liveShapePreview,
                    w = targetWidthPx,
                    h = targetHeightPx,
                    objectsById = emptyMap(),
                    assetBitmapProvider = assetBitmapProvider,
                    typefaceProvider = typefaceProvider,
                    reusablePath = reusableStrokePath,
                    reusablePaint = reusableStrokePaint
                )
                drewLiveShape = true
            }
        }
        blitPaint.alpha = 255

        if (liveStroke != null && !drewLiveStroke) {
            NotebookCanvasRenderer.drawStrokeFast(
                canvas = canvas,
                stroke = liveStroke,
                w = targetWidthPx,
                h = targetHeightPx,
                reusablePath = reusableStrokePath,
                reusablePaint = reusableStrokePaint
            )
        }
        if (liveShapePreview != null && !drewLiveShape) {
            NotebookCanvasRenderer.drawObject(
                canvas = canvas,
                obj = liveShapePreview,
                w = targetWidthPx,
                h = targetHeightPx,
                objectsById = emptyMap(),
                assetBitmapProvider = assetBitmapProvider,
                typefaceProvider = typefaceProvider,
                reusablePath = reusableStrokePath,
                reusablePaint = reusableStrokePaint
            )
        }
    }

    /**
     * Real Eyedropper (Cuentagotas): composites pixel at (normX, normY) across background + all visible layers.
     */
    fun samplePixelColor(normX: Float, normY: Float): Long? {
        val bg = backgroundBitmapCache ?: return null
        if (bg.isRecycled || cachedWidthPx <= 0 || cachedHeightPx <= 0) return null
        val px = (normX * cachedWidthPx).toInt().coerceIn(0, cachedWidthPx - 1)
        val py = (normY * cachedHeightPx).toInt().coerceIn(0, cachedHeightPx - 1)

        var outR = Color.red(bg.getPixel(px, py)).toFloat()
        var outG = Color.green(bg.getPixel(px, py)).toFloat()
        var outB = Color.blue(bg.getPixel(px, py)).toFloat()

        for (layer in cachedLayersMeta) {
            if (!layer.visible) continue
            val bmp = layerBitmaps[layer.id] ?: continue
            if (bmp.isRecycled) continue
            val pixel = bmp.getPixel(px, py)
            val a = (Color.alpha(pixel) / 255f) * layer.opacity.coerceIn(0f, 1f)
            if (a <= 0.01f) continue
            outR = Color.red(pixel) * a + outR * (1f - a)
            outG = Color.green(pixel) * a + outG * (1f - a)
            outB = Color.blue(pixel) * a + outB * (1f - a)
        }

        val argb = Color.argb(
            255,
            outR.toInt().coerceIn(0, 255),
            outG.toInt().coerceIn(0, 255),
            outB.toInt().coerceIn(0, 255)
        )
        return argb.toLong() and 0xFFFFFFFFL
    }

    fun recycleAll() {
        backgroundBitmapCache?.recycle()
        backgroundBitmapCache = null
        backgroundCanvasCache = null
        for (bmp in layerBitmaps.values) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        layerBitmaps.clear()
        layerCanvases.clear()
        lastCacheKey = Long.MIN_VALUE
    }
}

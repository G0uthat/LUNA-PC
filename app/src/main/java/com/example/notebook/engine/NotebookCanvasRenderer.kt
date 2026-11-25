package com.example.notebook.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.model.BuiltInStickerKind
import com.example.notebook.model.ConnectorObject
import com.example.notebook.model.ImageObject
import com.example.notebook.model.NbPoint
import com.example.notebook.model.NotebookFontId
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookObject
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.NotebookShapeKind
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.StickerObject
import com.example.notebook.model.StrokeBlendMode
import com.example.notebook.model.StrokeObject
import com.example.notebook.model.StrokeToolKind
import com.example.notebook.model.TableObject
import com.example.notebook.model.TextAlignMode
import com.example.notebook.model.TextListMode
import com.example.notebook.model.TextObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * SINGLE unified WYSIWYG Canvas renderer shared identically by:
 *  - Live screen per-layer cached bitmaps + live stroke overlay (`NotebookLayerCache` + `NotebookCanvasView`)
 *  - HD PDF export (`NotebookExporter` -> `PdfDocument`)
 *  - HD PNG / WebP export & WebP thumbnails
 *
 * Supports ALL schemaVersion=2 NotebookObject types:
 *  - StrokeObject (pressure modulation, quadratic Bézier smoothing, reusable Path, NORMAL / MULTIPLY highlighter)
 *  - ShapeObject (rounded rect, ellipse, line, simple/double/curved/poly arrows, { }, [ ], callout w/ tail, badges, dashed & shadow)
 *  - ConnectorObject (dynamically anchored to 2 objects — automatically tracks their movement/resize!)
 *  - ImageObject (PNG alpha, H/V flip, crop, rounded corners, border, opacity, shadow)
 *  - TextObject (multi-color spans, fontId, bold/italic/underline, inline highlight, alignment, line spacing, bullets & numbering, banner shadow)
 *  - StickerObject (13 built-in medical/study vector icons + custom user PNGs)
 *  - TableObject (n×m editable cells with optional header row)
 */
object NotebookCanvasRenderer {

    private val multiplyXfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)

    fun resolveLayerOrder(content: NotebookPageContent): List<String> =
        content.resolvedLayers().map { it.id }

    /**
     * Full direct page render (used by PDF export, PNG/WebP export, and WebP thumbnails for 1:1 WYSIWYG output).
     * Respects per-layer visibility, reordering, and per-layer opacity identically to NotebookLayerCache.
     */
    fun drawPage(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        template: NotebookPageTemplate,
        backgroundType: NotebookBackgroundType,
        backgroundColorArgb: Long,
        backgroundBitmap: Bitmap?,
        content: NotebookPageContent,
        assetBitmapProvider: (String) -> Bitmap?,
        typefaceProvider: (String) -> Typeface? = { null },
        selectedObjectId: String? = null,
        liveStroke: StrokeObject? = null,
        liveShapePreview: ShapeObject? = null,
        includeBackground: Boolean = true
    ) {
        if (widthPx <= 1f || heightPx <= 1f) return
        val scratchPath = Path()
        val scratchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val objectsById = content.objects.associateBy { it.id }

        if (includeBackground) {
            drawBackgroundLayer(canvas, widthPx, heightPx, template, backgroundType, backgroundColorArgb, backgroundBitmap)
        }

        val layers = content.resolvedLayers()
        for (layer in layers) {
            if (!layer.visible) continue
            val layerAlpha = (layer.opacity.coerceIn(0.05f, 1f) * 255f).toInt()
            val saveCount = if (layerAlpha < 254) {
                canvas.saveLayerAlpha(0f, 0f, widthPx, heightPx, layerAlpha)
            } else -1

            drawSingleLayer(
                canvas = canvas,
                layerId = layer.id,
                w = widthPx,
                h = heightPx,
                allObjects = content.objects,
                objectsById = objectsById,
                assetBitmapProvider = assetBitmapProvider,
                typefaceProvider = typefaceProvider,
                reusablePath = scratchPath,
                reusablePaint = scratchPaint
            )
            if (liveStroke != null && liveStroke.layerId == layer.id) {
                drawStrokeFast(canvas, liveStroke, widthPx, heightPx, scratchPath, scratchPaint)
            }
            if (liveShapePreview != null && liveShapePreview.layerId == layer.id) {
                drawObject(
                    canvas = canvas,
                    obj = liveShapePreview,
                    w = widthPx,
                    h = heightPx,
                    objectsById = objectsById,
                    assetBitmapProvider = assetBitmapProvider,
                    typefaceProvider = typefaceProvider,
                    reusablePath = scratchPath,
                    reusablePaint = scratchPaint
                )
            }
            if (saveCount >= 0) {
                canvas.restoreToCount(saveCount)
            }
        }

        if (selectedObjectId != null) {
            val selected = objectsById[selectedObjectId]
            if (selected != null && selected.visible) {
                drawSelectionOverlay(canvas, selected, widthPx, heightPx)
            }
        }
    }

    fun drawBackgroundLayer(
        canvas: Canvas,
        w: Float,
        h: Float,
        template: NotebookPageTemplate,
        backgroundType: NotebookBackgroundType,
        backgroundColorArgb: Long,
        backgroundBitmap: Bitmap?
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColorArgb.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        if (backgroundType != NotebookBackgroundType.TEMPLATE && backgroundBitmap != null && !backgroundBitmap.isRecycled) {
            val src = Rect(0, 0, backgroundBitmap.width, backgroundBitmap.height)
            val bmpRatio = backgroundBitmap.width.toFloat() / backgroundBitmap.height.coerceAtLeast(1)
            val pageRatio = w / h
            val dst = if (bmpRatio > pageRatio) {
                val fitH = w / bmpRatio
                val top = (h - fitH) / 2f
                RectF(0f, top, w, top + fitH)
            } else {
                val fitW = h * bmpRatio
                val left = (w - fitW) / 2f
                RectF(left, 0f, left + fitW, h)
            }
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(backgroundBitmap, src, dst, imgPaint)
            return
        }

        val isDarkPaper = Color.red(backgroundColorArgb.toInt()) < 90 &&
            Color.green(backgroundColorArgb.toInt()) < 90 &&
            Color.blue(backgroundColorArgb.toInt()) < 90
        val gridColor = if (isDarkPaper) 0x33FFFFFF else 0x264B5E80
        val marginColor = if (isDarkPaper) 0x55FF7B89 else 0x55E06C75
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridColor
            strokeWidth = max(1f, w * 0.0011f)
            style = Paint.Style.STROKE
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = marginColor
            strokeWidth = max(1.4f, w * 0.0017f)
            style = Paint.Style.STROKE
        }

        when (template) {
            NotebookPageTemplate.BLANK -> Unit
            NotebookPageTemplate.GRID -> {
                val step = w / 24f
                var x = step
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, linePaint)
                    x += step
                }
                var y = step
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
            }
            NotebookPageTemplate.DOTS -> {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDarkPaper) 0x4DFFFFFF else 0x44506380
                    style = Paint.Style.FILL
                }
                val step = w / 22f
                val radius = max(1.5f, w * 0.0018f)
                var y = step
                while (y < h) {
                    var x = step
                    while (x < w) {
                        canvas.drawCircle(x, y, radius, dotPaint)
                        x += step
                    }
                    y += step
                }
            }
            NotebookPageTemplate.RULED -> {
                val step = h / 30f
                var y = step * 2.5f
                while (y < h - step) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
                canvas.drawLine(w * 0.12f, 0f, w * 0.12f, h, accentPaint)
            }
            NotebookPageTemplate.CORNELL -> {
                val headerY = h * 0.12f
                val footerY = h * 0.84f
                val cueX = w * 0.28f
                canvas.drawLine(0f, headerY, w, headerY, accentPaint)
                canvas.drawLine(0f, footerY, w, footerY, accentPaint)
                canvas.drawLine(cueX, headerY, cueX, footerY, accentPaint)
                val step = (footerY - headerY) / 22f
                var y = headerY + step
                while (y < footerY) {
                    canvas.drawLine(cueX, y, w, y, linePaint)
                    y += step
                }
            }
        }
    }

    fun drawSingleLayer(
        canvas: Canvas,
        layerId: String,
        w: Float,
        h: Float,
        allObjects: List<NotebookObject>,
        objectsById: Map<String, NotebookObject>,
        assetBitmapProvider: (String) -> Bitmap?,
        typefaceProvider: (String) -> Typeface? = { null },
        reusablePath: Path,
        reusablePaint: Paint
    ) {
        val layerObjs = allObjects
            .filter { it.visible && it.layerId == layerId }
            .sortedBy { it.zIndex }
        for (obj in layerObjs) {
            drawObject(canvas, obj, w, h, objectsById, assetBitmapProvider, typefaceProvider, reusablePath, reusablePaint)
        }
    }

    fun drawObject(
        canvas: Canvas,
        obj: NotebookObject,
        w: Float,
        h: Float,
        objectsById: Map<String, NotebookObject>,
        assetBitmapProvider: (String) -> Bitmap?,
        typefaceProvider: (String) -> Typeface? = { null },
        reusablePath: Path,
        reusablePaint: Paint
    ) {
        if (!obj.visible) return
        when (obj) {
            is StrokeObject -> drawStrokeFast(canvas, obj, w, h, reusablePath, reusablePaint)
            is ConnectorObject -> drawConnectorObject(canvas, obj, w, h, objectsById)
            else -> {
                val left = obj.x * w
                val top = obj.y * h
                val itemW = obj.width * w
                val itemH = obj.height * h
                val cx = left + itemW * 0.5f
                val cy = top + itemH * 0.5f

                canvas.save()
                if (obj.rotationDeg != 0f) {
                    canvas.rotate(obj.rotationDeg, cx, cy)
                }
                when (obj) {
                    is ImageObject -> drawImageObject(canvas, obj, left, top, itemW, itemH, w, assetBitmapProvider)
                    is TextObject -> drawTextObject(canvas, obj, left, top, itemW, itemH, w, typefaceProvider)
                    is ShapeObject -> drawShapeObject(canvas, obj, w, h)
                    is StickerObject -> drawStickerObject(canvas, obj, left, top, itemW, itemH, w, assetBitmapProvider)
                    is TableObject -> drawTableObject(canvas, obj, left, top, itemW, itemH, w, typefaceProvider)
                    is StrokeObject, is ConnectorObject -> Unit
                }
                canvas.restore()
            }
        }
    }

    /**
     * Zero-allocation reusable-Path stroke renderer with quadratic Bézier smoothing,
     * true per-segment stylus pressure width modulation, Pencil graphite softness,
     * and NORMAL / MULTIPLY highlighter blend mode.
     */
    fun drawStrokeFast(
        canvas: Canvas,
        stroke: StrokeObject,
        w: Float,
        h: Float,
        reusablePath: Path,
        reusablePaint: Paint
    ) {
        val pts = stroke.points
        if (pts.isEmpty()) return

        val baseColor = stroke.colorArgb.toInt()
        val isHl = stroke.isHighlighterUnderlay
        val isPencil = stroke.tool == StrokeToolKind.PENCIL
        val toolAlphaFactor = when {
            isHl -> 0.44f
            isPencil -> 0.78f
            else -> 1f
        }
        val effectiveAlpha = (stroke.opacity * toolAlphaFactor).coerceIn(0.05f, 1f)
        val argb = Color.argb(
            (effectiveAlpha * 255f).toInt().coerceIn(10, 255),
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )

        val baseWidthPx = max(1.4f, stroke.widthNorm * w * (if (isHl) 3.4f else if (isPencil) 0.85f else 1f))

        reusablePaint.reset()
        reusablePaint.isAntiAlias = true
        reusablePaint.color = argb
        reusablePaint.style = Paint.Style.STROKE
        reusablePaint.strokeWidth = baseWidthPx
        reusablePaint.strokeCap = if (isHl) Paint.Cap.SQUARE else Paint.Cap.ROUND
        reusablePaint.strokeJoin = Paint.Join.ROUND
        if (stroke.blendMode == StrokeBlendMode.MULTIPLY) {
            reusablePaint.xfermode = multiplyXfermode
        }

        if (pts.size == 1) {
            reusablePaint.style = Paint.Style.FILL
            val rad = baseWidthPx * pts[0].pressure.coerceIn(0.45f, 1.65f) * 0.5f
            canvas.drawCircle(pts[0].x * w, pts[0].y * h, rad, reusablePaint)
            reusablePaint.xfermode = null
            return
        }

        // Detect whether real stylus pressure varies along this stroke
        var minP = Float.MAX_VALUE
        var maxP = -Float.MAX_VALUE
        if (!isHl) {
            for (p in pts) {
                if (p.pressure < minP) minP = p.pressure
                if (p.pressure > maxP) maxP = p.pressure
            }
        }
        val hasVariablePressure = !isHl && (maxP - minP) > 0.08f && pts.size >= 3

        if (hasVariablePressure) {
            // Smooth variable-width pressure segments (stylus calligraphy / pencil shading)
            var prevMidX = pts[0].x * w
            var prevMidY = pts[0].y * h
            for (i in 1 until pts.lastIndex) {
                val curr = pts[i]
                val next = pts[i + 1]
                val cx = curr.x * w
                val cy = curr.y * h
                val midX = (cx + next.x * w) * 0.5f
                val midY = (cy + next.y * h) * 0.5f
                val segPressure = ((curr.pressure + next.pressure) * 0.5f).coerceIn(0.35f, 1.8f)
                reusablePaint.strokeWidth = max(1.2f, baseWidthPx * segPressure)
                reusablePath.rewind()
                reusablePath.moveTo(prevMidX, prevMidY)
                reusablePath.quadTo(cx, cy, midX, midY)
                canvas.drawPath(reusablePath, reusablePaint)
                prevMidX = midX
                prevMidY = midY
            }
            val last = pts.last()
            reusablePaint.strokeWidth = max(1.2f, baseWidthPx * last.pressure.coerceIn(0.35f, 1.8f))
            canvas.drawLine(prevMidX, prevMidY, last.x * w, last.y * h, reusablePaint)
            reusablePaint.xfermode = null
            return
        }

        // Fast single-path quadratic Bézier render when pressure is uniform (finger / highlighter)
        if (!isHl && minP < Float.MAX_VALUE) {
            reusablePaint.strokeWidth = max(1.2f, baseWidthPx * ((minP + maxP) * 0.5f).coerceIn(0.45f, 1.65f))
        }
        reusablePath.rewind()
        val first = pts[0]
        reusablePath.moveTo(first.x * w, first.y * h)
        if (pts.size == 2) {
            reusablePath.lineTo(pts[1].x * w, pts[1].y * h)
        } else {
            for (i in 1 until pts.lastIndex) {
                val curr = pts[i]
                val next = pts[i + 1]
                val cx = curr.x * w
                val cy = curr.y * h
                val midX = (cx + next.x * w) * 0.5f
                val midY = (cy + next.y * h) * 0.5f
                reusablePath.quadTo(cx, cy, midX, midY)
            }
            val last = pts.last()
            reusablePath.lineTo(last.x * w, last.y * h)
        }
        canvas.drawPath(reusablePath, reusablePaint)
        reusablePaint.xfermode = null
    }

    private fun drawImageObject(
        canvas: Canvas,
        item: ImageObject,
        left: Float,
        top: Float,
        itemW: Float,
        itemH: Float,
        pageW: Float,
        assetBitmapProvider: (String) -> Bitmap?
    ) {
        val rect = RectF(left, top, left + itemW, top + itemH)
        val radiusPx = (item.cornerRadiusDp / 360f) * pageW
        val alphaInt = (item.opacity.coerceIn(0.05f, 1f) * 255f).toInt()

        if (item.hasShadow) {
            if (item.isOvalCrop) {
                val shOff = max(3f, pageW * 0.0045f)
                val shPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((36 * item.opacity).toInt().coerceIn(4, 52), 21, 28, 43)
                    style = Paint.Style.FILL
                }
                canvas.drawOval(
                    RectF(rect.left + shOff * 0.45f, rect.top + shOff, rect.right + shOff * 0.45f, rect.bottom + shOff),
                    shPaint
                )
            } else {
                drawSoftShadow(canvas, rect, radiusPx, pageW, item.opacity)
            }
        }

        val bmp = assetBitmapProvider(item.relativeAssetPath)
        canvas.save()
        if (item.isOvalCrop) {
            val clipPath = Path().apply { addOval(rect, Path.Direction.CW) }
            canvas.clipPath(clipPath)
        } else if (radiusPx > 0.5f) {
            val clipPath = Path().apply { addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW) }
            canvas.clipPath(clipPath)
        }
        if (item.flipHorizontal || item.flipVertical) {
            canvas.scale(
                if (item.flipHorizontal) -1f else 1f,
                if (item.flipVertical) -1f else 1f,
                rect.centerX(),
                rect.centerY()
            )
        }
        if (bmp != null && !bmp.isRecycled) {
            val sx0 = (item.cropLeft * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val sy0 = (item.cropTop * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val sx1 = (item.cropRight * bmp.width).toInt().coerceIn(sx0 + 1, bmp.width)
            val sy1 = (item.cropBottom * bmp.height).toInt().coerceIn(sy0 + 1, bmp.height)
            val srcRect = Rect(sx0, sy0, sx1, sy1)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = alphaInt }
            canvas.drawBitmap(bmp, srcRect, rect, paint)
        } else {
            val phPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A64748B }
            canvas.drawRect(rect, phPaint)
        }
        canvas.restore()

        item.borderColorArgb?.let { bdrColor ->
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyOpacity(bdrColor, item.opacity)
                style = Paint.Style.STROKE
                strokeWidth = max(1.5f, (item.borderWidthDp / 360f) * pageW)
            }
            if (item.isOvalCrop) {
                canvas.drawOval(rect, borderPaint)
            } else {
                canvas.drawRoundRect(rect, radiusPx, radiusPx, borderPaint)
            }
        }
    }

    private fun drawTextObject(
        canvas: Canvas,
        item: TextObject,
        left: Float,
        top: Float,
        itemW: Float,
        itemH: Float,
        pageW: Float,
        typefaceProvider: (String) -> Typeface?
    ) {
        val rect = RectF(left, top, left + itemW, top + itemH)
        val radiusPx = (item.cornerRadiusDp / 360f) * pageW

        if (item.hasShadow || item.isBanner) {
            drawSoftShadow(canvas, rect, radiusPx, pageW, item.opacity)
        }

        item.fillColorArgb?.let { fillColor ->
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyOpacity(fillColor, item.opacity)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
        }

        item.borderColorArgb?.let { borderColor ->
            val strokePx = max(1.5f, (item.borderWidthDp / 360f) * pageW)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyOpacity(borderColor, item.opacity)
                style = Paint.Style.STROKE
                strokeWidth = strokePx
            }
            canvas.drawRoundRect(rect, radiusPx, radiusPx, borderPaint)
        }

        val padH = max(8f, pageW * (if (item.isBanner) 0.022f else 0.014f))
        val padV = max(6f, pageW * (if (item.isBanner) 0.014f else 0.010f))
        val textWidth = (itemW - padH * 2f).toInt().coerceAtLeast(24)

        val baseTypeface = typefaceProvider(item.fontId) ?: when (NotebookFontId.fromId(item.fontId)) {
            NotebookFontId.SANS -> Typeface.SANS_SERIF
            NotebookFontId.SERIF -> Typeface.SERIF
            NotebookFontId.MONO -> Typeface.MONOSPACE
            NotebookFontId.ROUNDED -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (item.fontSizeNorm * pageW).coerceIn(10f, pageW * 0.16f)
            color = applyOpacity(item.spans.firstOrNull()?.colorArgb ?: 0xFF151C2BL, item.opacity)
            typeface = baseTypeface
        }

        val ssb = SpannableStringBuilder()
        var lineNumber = 1
        var atLineStart = true

        for (span in item.spans) {
            val lines = span.text.split("\n")
            lines.forEachIndexed { idx, segment ->
                if (idx > 0) {
                    ssb.append("\n")
                    atLineStart = true
                }
                if (atLineStart && segment.isNotEmpty() && item.listMode != TextListMode.NONE) {
                    val prefix = if (item.listMode == TextListMode.BULLETS) "• " else "${lineNumber++}. "
                    val pStart = ssb.length
                    ssb.append(prefix)
                    ssb.setSpan(
                        ForegroundColorSpan(applyOpacity(span.colorArgb, item.opacity)),
                        pStart,
                        ssb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    ssb.setSpan(StyleSpan(Typeface.BOLD), pStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    atLineStart = false
                }
                appendStyledSegment(
                    ssb = ssb,
                    text = segment,
                    colorArgb = applyOpacity(span.colorArgb, item.opacity),
                    isBold = span.isBold,
                    isItalic = span.isItalic,
                    isUnderline = span.isUnderline,
                    isStrikethrough = span.isStrikethrough,
                    isSuperscript = span.isSuperscript,
                    isSubscript = span.isSubscript,
                    highlightColorArgb = span.highlightColorArgb
                )
            }
        }

        // Zero-tofu scientific/Greek/math/arrow glyph fallback (identical on live screen AND PDF export!)
        applyMissingGlyphFallback(ssb, textPaint)

        val alignment = when {
            item.isBanner || item.alignment == TextAlignMode.CENTER -> Layout.Alignment.ALIGN_CENTER
            item.alignment == TextAlignMode.END -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val staticLayout = StaticLayout.Builder
            .obtain(ssb, 0, ssb.length, textPaint, textWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, item.lineSpacingMult.coerceIn(0.9f, 2.2f))
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.clipRect(rect)
        val verticalOffset = if (item.isBanner) {
            ((itemH - staticLayout.height) * 0.5f).coerceAtLeast(padV * 0.5f)
        } else {
            padV
        }
        canvas.translate(left + padH, top + verticalOffset)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * Ensures Greek letters, clinical arrows, subscripts/superscripts, and math operators
     * (`α β μ Δ ↑ ↓ → ⇄ ≥ ≤ ± ² ₂ ⁺ ⁻ ✓ ✗`) NEVER render as empty tofu boxes in custom Latin-subset fonts.
     */
    private fun applyMissingGlyphFallback(ssb: SpannableStringBuilder, primaryPaint: TextPaint) {
        if (ssb.isEmpty()) return
        var i = 0
        while (i < ssb.length) {
            val cp = Character.codePointAt(ssb, i)
            val charCount = Character.charCount(cp)
            if (cp > 0x7E && !Character.isWhitespace(cp)) {
                val glyphStr = ssb.substring(i, i + charCount)
                if (!primaryPaint.hasGlyph(glyphStr)) {
                    ssb.setSpan(
                        FallbackTypefaceSpan(Typeface.SANS_SERIF),
                        i,
                        i + charCount,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            i += charCount
        }
    }

    private class FallbackTypefaceSpan(private val fallback: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.typeface = fallback
        }
        override fun updateMeasureState(tp: TextPaint) {
            tp.typeface = fallback
        }
    }

    private fun appendStyledSegment(
        ssb: SpannableStringBuilder,
        text: String,
        colorArgb: Int,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        isStrikethrough: Boolean,
        isSuperscript: Boolean,
        isSubscript: Boolean,
        highlightColorArgb: Long?
    ) {
        if (text.isEmpty()) return
        val start = ssb.length
        ssb.append(text)
        val end = ssb.length
        ssb.setSpan(ForegroundColorSpan(colorArgb), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (style != Typeface.NORMAL) {
            ssb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isUnderline) {
            ssb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isStrikethrough) {
            ssb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isSuperscript) {
            ssb.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(RelativeSizeSpan(0.72f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (isSubscript) {
            ssb.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(RelativeSizeSpan(0.72f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (highlightColorArgb != null) {
            val raw = highlightColorArgb.toInt()
            val softHl = Color.argb(132, Color.red(raw), Color.green(raw), Color.blue(raw))
            ssb.setSpan(BackgroundColorSpan(softHl), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun drawShapeObject(canvas: Canvas, item: ShapeObject, w: Float, h: Float) {
        val strokePx = max(2f, item.strokeWidthNorm * w)
        val resolvedStrokeColor = applyOpacity(item.strokeColorArgb, item.opacity)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resolvedStrokeColor
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (item.isDashed) {
                pathEffect = DashPathEffect(floatArrayOf(strokePx * 3.2f, strokePx * 2.2f), 0f)
            }
        }
        val fillPaint = item.fillColorArgb?.let { fc ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyOpacity(fc, item.opacity)
                style = Paint.Style.FILL
            }
        }

        val left = item.x * w
        val top = item.y * h
        val right = (item.x + item.width) * w
        val bottom = (item.y + item.height) * h
        val rect = RectF(left, top, right, bottom)
        val radiusPx = (item.cornerRadiusDp / 360f) * w

        val sx = item.startX * w
        val sy = item.startY * h
        val ex = item.endX * w
        val ey = item.endY * h

        when (item.kind) {
            NotebookShapeKind.LINE -> {
                canvas.drawLine(sx, sy, ex, ey, strokePaint)
            }

            NotebookShapeKind.ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, strokePaint)
                drawArrowHead(canvas, sx, sy, ex, ey, strokePx, resolvedStrokeColor)
            }

            NotebookShapeKind.DOUBLE_ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, strokePaint)
                drawArrowHead(canvas, sx, sy, ex, ey, strokePx, resolvedStrokeColor)
                drawArrowHead(canvas, ex, ey, sx, sy, strokePx, resolvedStrokeColor)
            }

            NotebookShapeKind.CURVED_ARROW -> {
                val mx = (sx + ex) * 0.5f
                val my = (sy + ey) * 0.5f
                val dx = ex - sx
                val dy = ey - sy
                val ctrlX = item.midX?.times(w) ?: (mx - dy * 0.28f)
                val ctrlY = item.midY?.times(h) ?: (my + dx * 0.28f)
                val path = Path().apply {
                    moveTo(sx, sy)
                    quadTo(ctrlX, ctrlY, ex, ey)
                }
                canvas.drawPath(path, strokePaint)
                drawArrowHead(canvas, ctrlX, ctrlY, ex, ey, strokePx, resolvedStrokeColor)
            }

            NotebookShapeKind.POLY_ARROW -> {
                val mx = (item.midX ?: item.endX) * w
                val my = (item.midY ?: item.startY) * h
                val path = Path().apply {
                    moveTo(sx, sy)
                    lineTo(mx, my)
                    lineTo(ex, ey)
                }
                canvas.drawPath(path, strokePaint)
                drawArrowHead(canvas, mx, my, ex, ey, strokePx, resolvedStrokeColor)
            }

            NotebookShapeKind.ROUNDED_RECT -> {
                val cr = min(radiusPx, min(rect.width(), rect.height()) * 0.45f)
                if (item.hasShadow) drawSoftShadow(canvas, rect, cr, w, item.opacity)
                fillPaint?.let { canvas.drawRoundRect(rect, cr, cr, it) }
                canvas.drawRoundRect(rect, cr, cr, strokePaint)
            }

            NotebookShapeKind.ELLIPSE -> {
                if (item.hasShadow) {
                    val shOff = max(3f, w * 0.004f)
                    val shPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24151C2B }
                    canvas.drawOval(RectF(rect.left + shOff * 0.4f, rect.top + shOff, rect.right + shOff * 0.4f, rect.bottom + shOff), shPaint)
                }
                fillPaint?.let { canvas.drawOval(rect, it) }
                canvas.drawOval(rect, strokePaint)
            }

            NotebookShapeKind.BRACE_LEFT, NotebookShapeKind.BRACE_RIGHT -> {
                drawCurlyBrace(canvas, rect, item.kind == NotebookShapeKind.BRACE_LEFT, strokePaint)
            }

            NotebookShapeKind.BRACKET_LEFT, NotebookShapeKind.BRACKET_RIGHT -> {
                drawSquareBracket(canvas, rect, item.kind == NotebookShapeKind.BRACKET_LEFT, strokePaint)
            }

            NotebookShapeKind.BADGE_PLUS, NotebookShapeKind.BADGE_MINUS -> {
                val radius = min(rect.width(), rect.height()) * 0.48f
                val cx = rect.centerX()
                val cy = rect.centerY()
                val bgColor = item.fillColorArgb?.let { applyOpacity(it, item.opacity) } ?: Color.argb(
                    38,
                    Color.red(resolvedStrokeColor),
                    Color.green(resolvedStrokeColor),
                    Color.blue(resolvedStrokeColor)
                )
                val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = bgColor
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, radius, badgeFill)
                canvas.drawCircle(cx, cy, radius, strokePaint)
                val arm = radius * 0.48f
                canvas.drawLine(cx - arm, cy, cx + arm, cy, strokePaint)
                if (item.kind == NotebookShapeKind.BADGE_PLUS) {
                    canvas.drawLine(cx, cy - arm, cx, cy + arm, strokePaint)
                }
            }

            NotebookShapeKind.CHECKMARK -> {
                val p = Path().apply {
                    moveTo(rect.left + rect.width() * 0.16f, rect.top + rect.height() * 0.54f)
                    lineTo(rect.left + rect.width() * 0.42f, rect.bottom - rect.height() * 0.18f)
                    lineTo(rect.right - rect.width() * 0.14f, rect.top + rect.height() * 0.22f)
                }
                val checkPaint = Paint(strokePaint).apply {
                    pathEffect = null
                    strokeWidth = max(strokePx * 1.45f, min(rect.width(), rect.height()) * 0.14f)
                }
                canvas.drawPath(p, checkPaint)
            }

            NotebookShapeKind.CROSS_MARK -> {
                val pad = min(rect.width(), rect.height()) * 0.18f
                val crossPaint = Paint(strokePaint).apply {
                    pathEffect = null
                    strokeWidth = max(strokePx * 1.45f, min(rect.width(), rect.height()) * 0.14f)
                }
                canvas.drawLine(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad, crossPaint)
                canvas.drawLine(rect.right - pad, rect.top + pad, rect.left + pad, rect.bottom - pad, crossPaint)
            }

            NotebookShapeKind.STAR -> {
                val cx = rect.centerX()
                val cy = rect.centerY()
                val outerR = min(rect.width(), rect.height()) * 0.48f
                val innerR = outerR * 0.44f
                val p = Path()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) outerR else innerR
                    val ang = Math.toRadians((i * 36.0) - 90.0)
                    val px = cx + (rad * cos(ang)).toFloat()
                    val py = cy + (rad * sin(ang)).toFloat()
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                val starFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = item.fillColorArgb?.let { applyOpacity(it, item.opacity) } ?: Color.argb(
                        (70 * item.opacity).toInt().coerceIn(16, 255),
                        Color.red(resolvedStrokeColor),
                        Color.green(resolvedStrokeColor),
                        Color.blue(resolvedStrokeColor)
                    )
                    style = Paint.Style.FILL
                }
                canvas.drawPath(p, starFill)
                canvas.drawPath(p, strokePaint)
            }

            NotebookShapeKind.NUMBER_BADGE -> {
                val radius = min(rect.width(), rect.height()) * 0.48f
                val cx = rect.centerX()
                val cy = rect.centerY()
                val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = resolvedStrokeColor
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, radius, badgeFill)
                val numText = item.labelText.ifBlank { "1" }
                val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = max(12f, radius * 1.15f)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val fm = numPaint.fontMetrics
                canvas.drawText(numText, cx, cy - (fm.ascent + fm.descent) * 0.5f, numPaint)
            }

            NotebookShapeKind.CALLOUT -> {
                val label = item.labelText.ifBlank { "Etiqueta" }
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = resolvedStrokeColor
                    textSize = max(14f, w * 0.020f)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val textW = textPaint.measureText(label)
                val padX = max(12f, w * 0.015f)
                val padY = max(8f, w * 0.009f)
                val pillW = max(textW + padX * 2f, w * 0.10f)
                val pillH = textPaint.textSize + padY * 2f
                val pillRect = RectF(sx - pillW * 0.5f, sy - pillH * 0.5f, sx + pillW * 0.5f, sy + pillH * 0.5f)

                // Speech-bubble tail + target dot
                val tailPath = Path().apply {
                    val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                    val perpX = (-sin(angle) * pillH * 0.22f).toFloat()
                    val perpY = (cos(angle) * pillH * 0.22f).toFloat()
                    moveTo(sx + perpX, sy + perpY)
                    lineTo(ex, ey)
                    lineTo(sx - perpX, sy - perpY)
                    close()
                }
                val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = applyOpacity(item.labelBgColorArgb, item.opacity)
                    style = Paint.Style.FILL
                }
                if (item.hasShadow) drawSoftShadow(canvas, pillRect, pillH * 0.42f, w, item.opacity)
                canvas.drawPath(tailPath, pillBg)
                canvas.drawLine(pillRect.centerX(), pillRect.centerY(), ex, ey, strokePaint)
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = resolvedStrokeColor
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(ex, ey, strokePx * 1.55f, dotPaint)

                val cr = pillH * 0.42f
                canvas.drawRoundRect(pillRect, cr, cr, pillBg)
                canvas.drawRoundRect(pillRect, cr, cr, strokePaint)
                val fm = textPaint.fontMetrics
                val baseline = pillRect.centerY() - (fm.ascent + fm.descent) * 0.5f
                canvas.drawText(label, pillRect.centerX() - textW * 0.5f, baseline, textPaint)
            }
        }
    }

    /**
     * Dynamically resolves anchor edge points between `fromObjectId` and `toObjectId`
     * so connectors automatically follow both objects as they move or resize!
     */
    private fun drawConnectorObject(
        canvas: Canvas,
        conn: ConnectorObject,
        w: Float,
        h: Float,
        objectsById: Map<String, NotebookObject>
    ) {
        val fromObj = objectsById[conn.fromObjectId] ?: return
        val toObj = objectsById[conn.toObjectId] ?: return
        if (!fromObj.visible || !toObj.visible) return

        val p1 = anchorPointOnEdge(fromObj, toObj.centerX, toObj.centerY)
        val p2 = anchorPointOnEdge(toObj, fromObj.centerX, fromObj.centerY)
        val sx = p1.x * w
        val sy = p1.y * h
        val ex = p2.x * w
        val ey = p2.y * h

        val strokePx = max(2f, conn.strokeWidthNorm * w)
        val colorInt = applyOpacity(conn.colorArgb, conn.opacity)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (conn.isDashed) {
                pathEffect = DashPathEffect(floatArrayOf(strokePx * 3f, strokePx * 2f), 0f)
            }
        }

        if (conn.isCurved) {
            val mx = (sx + ex) * 0.5f
            val my = (sy + ey) * 0.5f
            val dx = ex - sx
            val dy = ey - sy
            val ctrlX = mx - dy * 0.16f
            val ctrlY = my + dx * 0.16f
            val path = Path().apply {
                moveTo(sx, sy)
                quadTo(ctrlX, ctrlY, ex, ey)
            }
            canvas.drawPath(path, paint)
            drawArrowHead(canvas, ctrlX, ctrlY, ex, ey, strokePx, colorInt)
            if (conn.doubleHeaded) {
                drawArrowHead(canvas, ctrlX, ctrlY, sx, sy, strokePx, colorInt)
            }
        } else {
            canvas.drawLine(sx, sy, ex, ey, paint)
            drawArrowHead(canvas, sx, sy, ex, ey, strokePx, colorInt)
            if (conn.doubleHeaded) {
                drawArrowHead(canvas, ex, ey, sx, sy, strokePx, colorInt)
            }
        }

        if (conn.labelText.isNotBlank()) {
            val mx = (sx + ex) * 0.5f
            val my = (sy + ey) * 0.5f
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorInt
                textSize = max(12f, w * 0.017f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val tw = tp.measureText(conn.labelText)
            val pad = max(6f, w * 0.008f)
            val bgRect = RectF(mx - tw * 0.5f - pad, my - tp.textSize * 0.65f - pad * 0.5f, mx + tw * 0.5f + pad, my + tp.textSize * 0.45f + pad * 0.5f)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(bgRect, pad, pad, bgPaint)
            canvas.drawText(conn.labelText, mx - tw * 0.5f, my + tp.textSize * 0.32f, tp)
        }
    }

    private fun anchorPointOnEdge(obj: NotebookObject, targetX: Float, targetY: Float): NbPoint {
        val cx = obj.centerX
        val cy = obj.centerY
        val dx = targetX - cx
        val dy = targetY - cy
        val hw = (obj.width * 0.5f).coerceAtLeast(0.005f)
        val hh = (obj.height * 0.5f).coerceAtLeast(0.005f)
        if (hypot(dx.toDouble(), dy.toDouble()) < 0.0001) return NbPoint(cx, cy)
        val scaleX = if (dx != 0f) hw / kotlin.math.abs(dx) else Float.MAX_VALUE
        val scaleY = if (dy != 0f) hh / kotlin.math.abs(dy) else Float.MAX_VALUE
        val t = min(scaleX, scaleY).coerceAtMost(1f)
        return NbPoint(cx + dx * t, cy + dy * t)
    }

    private fun drawStickerObject(
        canvas: Canvas,
        item: StickerObject,
        left: Float,
        top: Float,
        itemW: Float,
        itemH: Float,
        pageW: Float,
        assetBitmapProvider: (String) -> Bitmap?
    ) {
        val rect = RectF(left, top, left + itemW, top + itemH)
        val alphaInt = (item.opacity.coerceIn(0.05f, 1f) * 255f).toInt()

        // Custom user PNG sticker
        val customPath = item.customAssetPath
        if (!customPath.isNullOrBlank()) {
            val bmp = assetBitmapProvider(customPath)
            if (bmp != null && !bmp.isRecycled) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = alphaInt }
                canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), rect, p)
                return
            }
        }

        // Built-in medical & study vector badge stickers
        val colorInt = applyOpacity(item.colorArgb, item.opacity)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.46f
        val strokeW = max(2.2f, r * 0.16f)

        val bgFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.bgColorArgb?.let { applyOpacity(it, item.opacity) } ?: Color.argb(
                (38 * item.opacity).toInt(),
                Color.red(colorInt),
                Color.green(colorInt),
                Color.blue(colorInt)
            )
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.FILL
        }

        canvas.drawCircle(cx, cy, r, bgFill)
        canvas.drawCircle(cx, cy, r, strokePaint)

        when (item.builtInKind) {
            BuiltInStickerKind.CHECK -> {
                val p = Path().apply {
                    moveTo(cx - r * 0.44f, cy + r * 0.04f)
                    lineTo(cx - r * 0.10f, cy + r * 0.36f)
                    lineTo(cx + r * 0.48f, cy - r * 0.32f)
                }
                canvas.drawPath(p, strokePaint)
            }
            BuiltInStickerKind.CROSS -> {
                val d = r * 0.38f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, strokePaint)
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, strokePaint)
            }
            BuiltInStickerKind.PLUS_CIRCLE -> {
                val d = r * 0.44f
                canvas.drawLine(cx - d, cy, cx + d, cy, strokePaint)
                canvas.drawLine(cx, cy - d, cx, cy + d, strokePaint)
            }
            BuiltInStickerKind.MINUS_CIRCLE -> {
                val d = r * 0.44f
                canvas.drawLine(cx - d, cy, cx + d, cy, strokePaint)
            }
            BuiltInStickerKind.ALERT -> {
                canvas.drawLine(cx, cy - r * 0.42f, cx, cy + r * 0.12f, strokePaint)
                canvas.drawCircle(cx, cy + r * 0.38f, strokeW * 0.6f, fillPaint)
            }
            BuiltInStickerKind.STAR -> {
                val p = Path()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) r * 0.56f else r * 0.25f
                    val ang = Math.toRadians((i * 36.0) - 90.0)
                    val px = cx + (rad * cos(ang)).toFloat()
                    val py = cy + (rad * sin(ang)).toFloat()
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                canvas.drawPath(p, fillPaint)
            }
            BuiltInStickerKind.HEART -> {
                val p = Path().apply {
                    moveTo(cx, cy + r * 0.44f)
                    cubicTo(cx - r * 0.68f, cy + r * 0.06f, cx - r * 0.54f, cy - r * 0.52f, cx, cy - r * 0.16f)
                    cubicTo(cx + r * 0.54f, cy - r * 0.52f, cx + r * 0.68f, cy + r * 0.06f, cx, cy + r * 0.44f)
                    close()
                }
                canvas.drawPath(p, fillPaint)
            }
            BuiltInStickerKind.PILL -> {
                canvas.save()
                canvas.rotate(-35f, cx, cy)
                val pillRect = RectF(cx - r * 0.48f, cy - r * 0.24f, cx + r * 0.48f, cy + r * 0.24f)
                canvas.drawRoundRect(pillRect, r * 0.24f, r * 0.24f, strokePaint)
                canvas.drawLine(cx, pillRect.top, cx, pillRect.bottom, strokePaint)
                canvas.restore()
            }
            BuiltInStickerKind.ARROW_UP -> {
                canvas.drawLine(cx, cy + r * 0.42f, cx, cy - r * 0.36f, strokePaint)
                drawArrowHead(canvas, cx, cy + r * 0.2f, cx, cy - r * 0.44f, strokeW * 0.8f, colorInt)
            }
            BuiltInStickerKind.ARROW_DOWN -> {
                canvas.drawLine(cx, cy - r * 0.42f, cx, cy + r * 0.36f, strokePaint)
                drawArrowHead(canvas, cx, cy - r * 0.2f, cx, cy + r * 0.44f, strokeW * 0.8f, colorInt)
            }
            BuiltInStickerKind.ARROW_RIGHT -> {
                canvas.drawLine(cx - r * 0.44f, cy, cx + r * 0.34f, cy, strokePaint)
                drawArrowHead(canvas, cx - r * 0.2f, cy, cx + r * 0.46f, cy, strokeW * 0.8f, colorInt)
            }
            BuiltInStickerKind.SYRINGE -> {
                canvas.save()
                canvas.rotate(-40f, cx, cy)
                val barrel = RectF(cx - r * 0.34f, cy - r * 0.16f, cx + r * 0.24f, cy + r * 0.16f)
                canvas.drawRoundRect(barrel, r * 0.06f, r * 0.06f, strokePaint)
                // Needle
                canvas.drawLine(barrel.right, cy, cx + r * 0.54f, cy, strokePaint)
                // Plunger
                canvas.drawLine(barrel.left - r * 0.20f, cy, barrel.left, cy, strokePaint)
                canvas.drawLine(barrel.left - r * 0.20f, cy - r * 0.18f, barrel.left - r * 0.20f, cy + r * 0.18f, strokePaint)
                canvas.restore()
            }
            BuiltInStickerKind.MAGNIFIER -> {
                val lensCx = cx - r * 0.12f
                val lensCy = cy - r * 0.12f
                val lensR = r * 0.32f
                canvas.drawCircle(lensCx, lensCy, lensR, strokePaint)
                canvas.drawLine(lensCx + lensR * 0.72f, lensCy + lensR * 0.72f, cx + r * 0.44f, cy + r * 0.44f, strokePaint)
            }
            BuiltInStickerKind.BRAIN, BuiltInStickerKind.LIGHTBULB, BuiltInStickerKind.PIN -> {
                val glyph = when (item.builtInKind) {
                    BuiltInStickerKind.BRAIN -> "Rx"
                    BuiltInStickerKind.LIGHTBULB -> "!"
                    else -> "★"
                }
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colorInt
                    textSize = max(12f, r * 0.92f)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val fm = tp.fontMetrics
                canvas.drawText(glyph, cx, cy - (fm.ascent + fm.descent) * 0.5f, tp)
            }
        }
    }

    private fun drawTableObject(
        canvas: Canvas,
        table: TableObject,
        left: Float,
        top: Float,
        itemW: Float,
        itemH: Float,
        pageW: Float,
        typefaceProvider: (String) -> Typeface?
    ) {
        val rows = table.rows.coerceIn(1, 12)
        val cols = table.cols.coerceIn(1, 8)
        val cellW = itemW / cols
        val cellH = itemH / rows
        val radiusPx = max(4f, pageW * 0.008f)
        val outerRect = RectF(left, top, left + itemW, top + itemH)

        val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = applyOpacity(table.cellBgColorArgb, table.opacity)
            style = Paint.Style.FILL
        }
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = applyOpacity(table.headerBgColorArgb, table.opacity)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = applyOpacity(table.borderColorArgb, table.opacity)
            style = Paint.Style.STROKE
            strokeWidth = max(1.4f, pageW * 0.0018f)
        }

        canvas.save()
        val clipPath = Path().apply { addRoundRect(outerRect, radiusPx, radiusPx, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        canvas.drawRect(outerRect, cellFill)
        if (table.hasHeaderRow) {
            canvas.drawRect(left, top, left + itemW, top + cellH, headerFill)
        }

        for (r in 1 until rows) {
            val y = top + r * cellH
            canvas.drawLine(left, y, left + itemW, y, borderPaint)
        }
        for (c in 1 until cols) {
            val x = left + c * cellW
            canvas.drawLine(x, top, x, top + itemH, borderPaint)
        }

        val pad = max(4f, pageW * 0.007f)
        val baseSize = (table.fontSizeNorm * pageW).coerceIn(9f, pageW * 0.045f)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val text = table.cells.getOrNull(r * cols + c).orEmpty()
                if (text.isBlank()) continue
                val tableBaseTf = typefaceProvider("open_sans") ?: Typeface.SANS_SERIF
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = applyOpacity(table.textColorArgb, table.opacity)
                    textSize = baseSize
                    typeface = if (r == 0 && table.hasHeaderRow) {
                        Typeface.create(tableBaseTf, Typeface.BOLD)
                    } else tableBaseTf
                }
                val cellLeft = left + c * cellW + pad
                val cellTop = top + r * cellH + pad * 0.7f
                val maxW = (cellW - pad * 2f).toInt().coerceAtLeast(12)
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, maxW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()
                canvas.save()
                canvas.clipRect(left + c * cellW, top + r * cellH, left + (c + 1) * cellW, top + (r + 1) * cellH)
                canvas.translate(cellLeft, cellTop)
                layout.draw(canvas)
                canvas.restore()
            }
        }
        canvas.restore()

        canvas.drawRoundRect(outerRect, radiusPx, radiusPx, borderPaint)
    }

    private fun drawSoftShadow(canvas: Canvas, rect: RectF, radiusPx: Float, pageW: Float, opacity: Float) {
        val shadowOffset = max(3f, pageW * 0.0045f)
        val shadowRect = RectF(
            rect.left + shadowOffset * 0.45f,
            rect.top + shadowOffset,
            rect.right + shadowOffset * 0.45f,
            rect.bottom + shadowOffset
        )
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((36 * opacity).toInt().coerceIn(4, 52), 21, 28, 43)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(shadowRect, radiusPx, radiusPx, shadowPaint)
    }

    private fun drawArrowHead(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        strokePx: Float,
        colorInt: Int
    ) {
        val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
        val headLen = max(14f, strokePx * 4.1f)
        val spread = Math.toRadians(28.0)
        val x1 = toX - (headLen * cos(angle - spread)).toFloat()
        val y1 = toY - (headLen * sin(angle - spread)).toFloat()
        val x2 = toX - (headLen * cos(angle + spread)).toFloat()
        val y2 = toY - (headLen * sin(angle + spread)).toFloat()

        val headPath = Path().apply {
            moveTo(toX, toY)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.FILL
        }
        canvas.drawPath(headPath, fillPaint)
    }

    private fun drawCurlyBrace(canvas: Canvas, rect: RectF, pointsLeft: Boolean, paint: Paint) {
        val top = rect.top
        val bottom = rect.bottom
        val midY = rect.centerY()
        val h = rect.height().coerceAtLeast(10f)
        val tipX = if (pointsLeft) rect.left else rect.right
        val endX = if (pointsLeft) rect.right else rect.left
        val midX = rect.centerX()

        val path = Path().apply {
            moveTo(endX, top)
            cubicTo(midX, top, midX, top + h * 0.14f, midX, midY - h * 0.14f)
            cubicTo(midX, midY - h * 0.03f, midX, midY, tipX, midY)
            cubicTo(midX, midY, midX, midY + h * 0.03f, midX, midY + h * 0.14f)
            cubicTo(midX, bottom - h * 0.14f, midX, bottom, endX, bottom)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSquareBracket(canvas: Canvas, rect: RectF, openRight: Boolean, paint: Paint) {
        val spineX = if (openRight) rect.left else rect.right
        val tipX = if (openRight) rect.right else rect.left
        val path = Path().apply {
            moveTo(tipX, rect.top)
            lineTo(spineX, rect.top)
            lineTo(spineX, rect.bottom)
            lineTo(tipX, rect.bottom)
        }
        canvas.drawPath(path, paint)
    }

    fun drawSelectionOverlay(canvas: Canvas, item: NotebookObject, w: Float, h: Float) {
        if (item is ConnectorObject) return
        val left = item.x * w
        val top = item.y * h
        val right = (item.x + item.width) * w
        val bottom = (item.y + item.height) * h
        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f

        canvas.save()
        if (item.rotationDeg != 0f) {
            canvas.rotate(item.rotationDeg, cx, cy)
        }

        val accent = if (item.locked) 0xFFD97706.toInt() else 0xFF2563EB.toInt()
        val dashLen = max(8f, w * 0.01f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = max(2f, w * 0.0024f)
            pathEffect = DashPathEffect(floatArrayOf(dashLen, dashLen * 0.6f), 0f)
        }
        canvas.drawRect(left, top, right, bottom, borderPaint)

        if (!item.locked) {
            val handleRadius = max(8f, w * 0.011f)
            val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                style = Paint.Style.STROKE
                strokeWidth = max(2.2f, w * 0.0026f)
            }

            val corners = listOf(
                NbPoint(left, top),
                NbPoint(right, top),
                NbPoint(left, bottom),
                NbPoint(right, bottom)
            )
            for (c in corners) {
                canvas.drawCircle(c.x, c.y, handleRadius, handleFill)
                canvas.drawCircle(c.x, c.y, handleRadius, handleStroke)
            }

            val rotY = top - handleRadius * 3.1f
            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                strokeWidth = max(2f, w * 0.0022f)
            }
            canvas.drawLine(cx, top, cx, rotY, stemPaint)
            canvas.drawCircle(cx, rotY, handleRadius * 0.9f, handleFill)
            canvas.drawCircle(cx, rotY, handleRadius * 0.9f, handleStroke)
        }

        canvas.restore()
    }

    fun drawMultiSelectionOverlay(canvas: Canvas, items: List<NotebookObject>, w: Float, h: Float) {
        val visible = items.filter { it.visible && it !is ConnectorObject }
        if (visible.isEmpty()) return
        if (visible.size == 1) {
            drawSelectionOverlay(canvas, visible.first(), w, h)
            return
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val accent = 0xFF2563EB.toInt()
        val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x552563EB
            style = Paint.Style.STROKE
            strokeWidth = max(1.4f, w * 0.0016f)
        }
        for (it in visible) {
            val l = it.x * w
            val t = it.y * h
            val r = (it.x + it.width) * w
            val b = (it.y + it.height) * h
            canvas.drawRect(l, t, r, b, softPaint)
            minX = min(minX, l)
            minY = min(minY, t)
            maxX = max(maxX, r)
            maxY = max(maxY, b)
        }
        val dashLen = max(8f, w * 0.01f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = max(2.2f, w * 0.0025f)
            pathEffect = DashPathEffect(floatArrayOf(dashLen, dashLen * 0.6f), 0f)
        }
        canvas.drawRect(minX, minY, maxX, maxY, borderPaint)
    }

    fun drawLassoPath(canvas: Canvas, points: List<NbPoint>, w: Float, h: Float) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(points[0].x * w, points[0].y * h)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * w, points[i].y * h)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1E2563EB
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2563EB.toInt()
            style = Paint.Style.STROKE
            strokeWidth = max(2f, w * 0.0024f)
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    fun drawSnapGuides(canvas: Canvas, snapX: Float?, snapY: Float?, w: Float, h: Float) {
        if (snapX == null && snapY == null) return
        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCEC4899.toInt()
            style = Paint.Style.STROKE
            strokeWidth = max(1.6f, w * 0.0018f)
            pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        }
        if (snapX != null) {
            val px = snapX * w
            canvas.drawLine(px, 0f, px, h, guidePaint)
        }
        if (snapY != null) {
            val py = snapY * h
            canvas.drawLine(0f, py, w, py, guidePaint)
        }
    }

    private fun applyOpacity(argbLong: Long, opacity: Float): Int {
        val raw = argbLong.toInt()
        val origA = Color.alpha(raw)
        val finalA = (origA * opacity.coerceIn(0.05f, 1f)).toInt().coerceIn(8, 255)
        return Color.argb(finalA, Color.red(raw), Color.green(raw), Color.blue(raw))
    }
}

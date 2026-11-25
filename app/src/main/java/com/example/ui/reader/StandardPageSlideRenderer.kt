package com.example.ui.reader

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockType
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedSlide
import com.example.data.model.SlideElement
import com.example.data.model.SlideShapeGeometry
import com.example.data.model.SlideTextAlign
import com.example.data.model.SlideVerticalAlign
import com.example.engine.BitmapDecodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SlideImageClipShape = RoundedCornerShape(6.dp)

internal object SlideBitmapCache {
    // RAM-adaptive byte budget (8 MB on low-RAM devices, 18 MB on standard devices)
    private val cache = object : LruCache<String, Bitmap>(com.example.engine.MemoryPressureManager.slideCacheMaxKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    init {
        com.example.engine.MemoryPressureManager.registerTrimmer("slide_bitmap_cache") { level ->
            com.example.engine.MemoryPressureManager.trimLruCache(cache, level)
        }
    }

    suspend fun load(path: String): Bitmap? {
        cache.get(path)?.takeIf { !it.isRecycled }?.let { return it }
        val maxDim = com.example.engine.MemoryPressureManager.slideDecodeMaxDim()
        val useRgb565 = com.example.engine.MemoryPressureManager.isLowRamDevice
        return withContext(Dispatchers.IO) {
            BitmapDecodeUtils.decodeSampledFile(
                path = path,
                reqWidth = maxDim,
                reqHeight = maxDim,
                preferRgb565 = useRgb565
            )?.also { bmp ->
                cache.put(path, bmp)
            }
        }
    }
}

@Composable
internal fun PptxSlideSurface(
    slide: ParsedSlide,
    headerLabel: String,
    formatBadge: String,
    ptScale: Float,
    isLandscape: Boolean,
    paperInk: Color,
    paperMuted: Color,
    accent: Color,
    fontFamily: FontFamily
) {
    if (slide.elements.isNotEmpty()) {
        AuthenticPptxCanvasSurface(
            slide = slide,
            ptScale = ptScale,
            fontFamily = fontFamily
        )
        return
    }

    // Structured fallback for minimal/unpositioned slides
    val padX = (44f * ptScale).coerceIn(12f, 32f).dp
    val padY = (32f * ptScale).coerceIn(10f, 24f).dp
    val titleSp = (26f * ptScale).coerceIn(11f, 22f).sp
    val subtitleSp = (16f * ptScale).coerceIn(8.5f, 14f).sp
    val bodySp = (14f * ptScale).coerceIn(7.5f, 13f).sp
    val metaSp = (10f * ptScale).coerceIn(6.5f, 10f).sp

    val firstImgPath = slide.imagePaths.firstOrNull()
    val slideBitmap by produceState<Bitmap?>(initialValue = null, key1 = firstImgPath) {
        value = firstImgPath?.let { SlideBitmapCache.load(it) }
    }
    val slideImage = remember(slideBitmap) { slideBitmap?.asImageBitmap() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((5f * ptScale).coerceIn(2.5f, 5f).dp)
                .background(accent)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = padX, vertical = padY),
            verticalArrangement = Arrangement.spacedBy((8f * ptScale).coerceIn(3f, 8f).dp)
        ) {
            Text(
                text = slide.title,
                fontFamily = fontFamily,
                fontSize = titleSp,
                lineHeight = (titleSp.value * 1.18f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = paperInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (slide.subtitle.isNotBlank()) {
                Text(
                    text = slide.subtitle,
                    fontFamily = fontFamily,
                    fontSize = subtitleSp,
                    lineHeight = (subtitleSp.value * 1.24f).sp,
                    fontWeight = FontWeight.Medium,
                    color = paperMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (slideImage != null && isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((16f * ptScale).coerceIn(6f, 14f).dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (slide.bodyBlocks.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy((6f * ptScale).coerceIn(2.5f, 6f).dp)
                        ) {
                            SlideBodyBlocks(
                                blocks = slide.bodyBlocks,
                                bodySp = bodySp,
                                ink = paperInk,
                                accent = accent,
                                fontFamily = fontFamily
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(SlideImageClipShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = slideImage,
                            contentDescription = "Imagen de diapositiva",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy((6f * ptScale).coerceIn(2.5f, 6f).dp)
                ) {
                    SlideBodyBlocks(
                        blocks = slide.bodyBlocks,
                        bodySp = bodySp,
                        ink = paperInk,
                        accent = accent,
                        fontFamily = fontFamily
                    )
                    if (slideImage != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(SlideImageClipShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = slideImage,
                                contentDescription = "Imagen de diapositiva",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatBadge,
                    fontSize = metaSp,
                    fontWeight = FontWeight.SemiBold,
                    color = paperMuted.copy(alpha = 0.72f)
                )
                Text(
                    text = headerLabel,
                    fontSize = metaSp,
                    fontWeight = FontWeight.Bold,
                    color = paperMuted.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * High-fidelity original-layout PowerPoint canvas renderer.
 * Draws the authentic slide background (image, vertical gradient, or solid fill) and places every
 * OOXML shape, text box, picture, and table at its exact normalized slide coordinates.
 */
@Composable
private fun AuthenticPptxCanvasSurface(
    slide: ParsedSlide,
    ptScale: Float,
    fontFamily: FontFamily
) {
    val bgStart = Color(slide.backgroundColorArgb.toInt())
    val bgEnd = slide.backgroundGradientEndArgb?.let { Color(it.toInt()) }
    val bgBitmap by produceState<Bitmap?>(initialValue = null, key1 = slide.backgroundImagePath) {
        value = slide.backgroundImagePath?.let { SlideBitmapCache.load(it) }
    }
    val bgImage = remember(bgBitmap) { bgBitmap?.asImageBitmap() }
    val defaultSlideInk = if (bgStart.luminance() < 0.36f) Color(0xFFF5F7FA) else Color(0xFF141B29)

    val bgBrush = remember(bgStart, bgEnd) {
        bgEnd?.let { Brush.verticalGradient(listOf(bgStart, it)) }
    }
    val bgModifier = if (bgBrush != null) {
        Modifier.background(bgBrush)
    } else {
        Modifier.background(bgStart)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        val canvasW = maxWidth
        val canvasH = maxHeight

        // Optional slide master/slide background image
        if (bgImage != null) {
            Image(
                bitmap = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        for (element in slide.elements) {
            val elX = canvasW * element.left.coerceIn(-0.08f, 1.05f)
            val elY = canvasH * element.top.coerceIn(-0.08f, 1.05f)
            val elW = (canvasW * element.width.coerceIn(0.008f, 1.15f)).coerceAtLeast(2.dp)
            val elH = (canvasH * element.height.coerceIn(0.006f, 1.15f)).coerceAtLeast(2.dp)

            when (element) {
                is SlideElement.Picture -> {
                    val picBitmap by produceState<Bitmap?>(initialValue = null, key1 = element.imagePath) {
                        value = SlideBitmapCache.load(element.imagePath)
                    }
                    val picImage = remember(picBitmap) { picBitmap?.asImageBitmap() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(elX.roundToPx(), elY.roundToPx()) }
                            .size(width = elW, height = elH)
                            .graphicsLayer { rotationZ = element.rotationDeg },
                        contentAlignment = Alignment.Center
                    ) {
                        if (picImage != null) {
                            Image(
                                bitmap = picImage,
                                contentDescription = "Imagen de diapositiva",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                is SlideElement.ShapeBox -> {
                    val shape = remember(element.geometry, ptScale) {
                        when (element.geometry) {
                            SlideShapeGeometry.ROUND_RECT -> RoundedCornerShape((8f * ptScale).coerceIn(3f, 12f).dp)
                            SlideShapeGeometry.ELLIPSE -> CircleShape
                            else -> RectangleShape
                        }
                    }
                    val shapeBrush = remember(element.fillColorArgb, element.gradientEndColorArgb) {
                        if (element.fillColorArgb != null && element.gradientEndColorArgb != null) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(element.fillColorArgb.toInt()),
                                    Color(element.gradientEndColorArgb.toInt())
                                )
                            )
                        } else null
                    }
                    val fillMod = when {
                        shapeBrush != null -> Modifier.background(shapeBrush, shape = shape)
                        element.fillColorArgb != null -> Modifier.background(Color(element.fillColorArgb.toInt()), shape = shape)
                        else -> Modifier
                    }
                    val borderMod = if (element.borderColorArgb != null && element.borderWidthPt > 0f && element.geometry != SlideShapeGeometry.LINE) {
                        Modifier.border(
                            width = (element.borderWidthPt * ptScale).coerceIn(0.5f, 3.5f).dp,
                            color = Color(element.borderColorArgb.toInt()),
                            shape = shape
                        )
                    } else {
                        Modifier
                    }

                    val localInk = element.fillColorArgb?.let { fillArgb ->
                        val fc = Color(fillArgb.toInt())
                        if (fc.alpha > 0.45f) {
                            if (fc.luminance() < 0.38f) Color(0xFFF5F7FA) else Color(0xFF141B29)
                        } else {
                            defaultSlideInk
                        }
                    } ?: defaultSlideInk

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(elX.roundToPx(), elY.roundToPx()) }
                            .size(width = elW, height = elH)
                            .graphicsLayer { rotationZ = element.rotationDeg }
                            .then(if (element.geometry != SlideShapeGeometry.LINE) Modifier.clip(shape) else Modifier)
                            .then(if (element.geometry != SlideShapeGeometry.LINE) fillMod else Modifier)
                            .then(borderMod),
                        contentAlignment = when (element.verticalAlign) {
                            SlideVerticalAlign.TOP -> Alignment.TopStart
                            SlideVerticalAlign.MIDDLE -> Alignment.CenterStart
                            SlideVerticalAlign.BOTTOM -> Alignment.BottomStart
                        }
                    ) {
                        if (element.geometry == SlideShapeGeometry.LINE) {
                            val lineColor = Color((element.borderColorArgb ?: element.fillColorArgb ?: 0xFF64748BL).toInt())
                            val strokeDp = (element.borderWidthPt.coerceAtLeast(1f) * ptScale).coerceIn(0.8f, 3.5f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val midY = size.height * 0.5f
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, midY),
                                    end = Offset(size.width, midY),
                                    strokeWidth = strokeDp.dp.toPx()
                                )
                            }
                        } else if (element.paragraphs.isNotEmpty()) {
                            val padH = (5f * ptScale).coerceIn(2f, 8f).dp
                            val padV = (3f * ptScale).coerceIn(1f, 6f).dp
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = padH, vertical = padV),
                                verticalArrangement = Arrangement.spacedBy((3f * ptScale).coerceIn(1f, 5f).dp)
                            ) {
                                for (para in element.paragraphs) {
                                    val textAlign = when (para.align) {
                                        SlideTextAlign.LEFT -> TextAlign.Start
                                        SlideTextAlign.CENTER -> TextAlign.Center
                                        SlideTextAlign.RIGHT -> TextAlign.End
                                        SlideTextAlign.JUSTIFY -> TextAlign.Justify
                                    }
                                    val maxRunPt = para.runs.maxOfOrNull { it.fontSizePt } ?: 14f
                                    val lineHeightSp = (maxRunPt * ptScale * 1.20f).coerceIn(6.8f, 44f).sp
                                    val annotated = remember(para, ptScale, localInk) {
                                        buildAnnotatedString {
                                            if (para.bulletPrefix.isNotEmpty()) {
                                                val firstColor = para.runs.firstOrNull()?.colorArgb
                                                    ?.let { Color(it.toInt()) } ?: localInk
                                                val bulletSp = (maxRunPt * ptScale).coerceIn(5.5f, 36f).sp
                                                withStyle(SpanStyle(fontSize = bulletSp, color = firstColor)) {
                                                    append(para.bulletPrefix)
                                                }
                                            }
                                            for (run in para.runs) {
                                                val sp = (run.fontSizePt * ptScale).coerceIn(5.5f, 36f).sp
                                                val col = run.colorArgb?.let { Color(it.toInt()) } ?: localInk
                                                withStyle(
                                                    SpanStyle(
                                                        fontSize = sp,
                                                        fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                                                        fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                        textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                                        color = col
                                                    )
                                                ) {
                                                    append(run.text)
                                                }
                                            }
                                        }
                                    }
                                    val indentDp = (para.level * 10f * ptScale).coerceIn(0f, 24f).dp
                                    Text(
                                        text = annotated,
                                        fontFamily = fontFamily,
                                        lineHeight = lineHeightSp,
                                        textAlign = textAlign,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = indentDp)
                                    )
                                }
                            }
                        }
                    }
                }

                is SlideElement.Table -> {
                    val gridBorder = if (bgStart.luminance() < 0.36f) {
                        Color.White.copy(alpha = 0.25f)
                    } else {
                        Color(0xFFCBD5E1)
                    }
                    Column(
                        modifier = Modifier
                            .offset { IntOffset(elX.roundToPx(), elY.roundToPx()) }
                            .size(width = elW, height = elH)
                            .border(0.6.dp, gridBorder)
                    ) {
                        for (row in element.rows) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                row.forEachIndexed { colIdx, cell ->
                                    val weight = element.colWeights.getOrElse(colIdx) {
                                        1f / row.size.coerceAtLeast(1)
                                    }.coerceAtLeast(0.05f)
                                    val cellBg = cell.fillColorArgb?.let { Color(it.toInt()) } ?: Color.Transparent
                                    val cellInk = cell.textColorArgb?.let { Color(it.toInt()) } ?: if (cellBg.alpha > 0.45f) {
                                        if (cellBg.luminance() < 0.38f) Color.White else Color(0xFF141B29)
                                    } else {
                                        defaultSlideInk
                                    }
                                    val cellSp = (cell.fontSizePt * ptScale).coerceIn(5.2f, 16f).sp
                                    val cellAlign = when (cell.align) {
                                        SlideTextAlign.LEFT -> TextAlign.Start
                                        SlideTextAlign.CENTER -> TextAlign.Center
                                        SlideTextAlign.RIGHT -> TextAlign.End
                                        SlideTextAlign.JUSTIFY -> TextAlign.Justify
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(cellBg)
                                            .border(0.4.dp, gridBorder)
                                            .padding(horizontal = (3f * ptScale).coerceIn(1.5f, 5f).dp, vertical = 1.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cell.text,
                                            fontFamily = fontFamily,
                                            fontSize = cellSp,
                                            lineHeight = (cellSp.value * 1.15f).sp,
                                            fontWeight = if (cell.isBold) FontWeight.Bold else FontWeight.Normal,
                                            color = cellInk,
                                            textAlign = cellAlign,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideBodyBlocks(
    blocks: List<ParsedBlock>,
    bodySp: TextUnit,
    ink: Color,
    accent: Color,
    fontFamily: FontFamily
) {
    for (block in blocks.take(10)) {
        if (block.type == BlockType.LIST_ITEM) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = (bodySp.value * 0.42f).dp)
                        .size((bodySp.value * 0.34f).coerceIn(3f, 5f).dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = block.text,
                    fontFamily = fontFamily,
                    fontSize = bodySp,
                    lineHeight = (bodySp.value * 1.28f).sp,
                    color = ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = block.text,
                fontFamily = fontFamily,
                fontSize = bodySp,
                lineHeight = (bodySp.value * 1.28f).sp,
                color = ink,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PhysicalDocumentSheetSurface(
    sheet: StandardDocumentSheet,
    formatBadge: String,
    ptScale: Float,
    paperInk: Color,
    paperMuted: Color,
    paperBorder: Color,
    accent: Color,
    fontFamily: FontFamily,
    textAlign: TextAlign
) {
    val marginX = (52f * ptScale).coerceIn(14f, 40f).dp
    val marginY = (46f * ptScale).coerceIn(12f, 36f).dp
    val h1Sp = (19f * ptScale).coerceIn(9.5f, 18f).sp
    val h2Sp = (15f * ptScale).coerceIn(8.5f, 15f).sp
    val bodySp = (11.5f * ptScale).coerceIn(6.5f, 12.5f).sp
    val metaSp = (8.5f * ptScale).coerceIn(6f, 9.5f).sp
    val blockGap = (9f * ptScale).coerceIn(3.5f, 9f).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = marginX, vertical = marginY)
    ) {
        // Subtle top physical paper header (A4 / Carta / A3 indicator)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBadge,
                fontSize = metaSp,
                fontWeight = FontWeight.Bold,
                color = paperMuted.copy(alpha = 0.65f)
            )
            Text(
                text = sheet.headerLabel,
                fontSize = metaSp,
                fontWeight = FontWeight.SemiBold,
                color = paperMuted.copy(alpha = 0.65f)
            )
        }
        Spacer(Modifier.height((6f * ptScale).coerceIn(3f, 7f).dp))
        HorizontalDivider(thickness = 0.6.dp, color = paperBorder.copy(alpha = 0.7f))
        Spacer(Modifier.height((10f * ptScale).coerceIn(5f, 12f).dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(blockGap)
        ) {
            for (block in sheet.blocks) {
                when (block.type) {
                    BlockType.HEADING_1 -> {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = h1Sp,
                            lineHeight = (h1Sp.value * 1.24f).sp,
                            fontWeight = FontWeight.Bold,
                            color = paperInk
                        )
                    }
                    BlockType.HEADING_2 -> {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = h2Sp,
                            lineHeight = (h2Sp.value * 1.26f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = paperInk
                        )
                    }
                    BlockType.QUOTE -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .heightIn(min = 16.dp)
                                    .background(accent.copy(alpha = 0.75f))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = block.text,
                                fontFamily = fontFamily,
                                fontSize = bodySp,
                                lineHeight = (bodySp.value * 1.38f).sp,
                                fontStyle = FontStyle.Italic,
                                color = paperMuted
                            )
                        }
                    }
                    BlockType.LIST_ITEM -> {
                        Text(
                            text = "•  ${block.text}",
                            fontFamily = fontFamily,
                            fontSize = bodySp,
                            lineHeight = (bodySp.value * 1.38f).sp,
                            color = paperInk,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    BlockType.PARAGRAPH -> {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = bodySp,
                            lineHeight = (bodySp.value * 1.40f).sp,
                            textAlign = textAlign,
                            color = paperInk
                        )
                    }
                }
            }
        }
    }
}

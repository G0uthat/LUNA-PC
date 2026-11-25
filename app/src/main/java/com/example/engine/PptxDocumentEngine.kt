package com.example.engine

import android.content.Context
import android.graphics.Color as AndroidColor
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedChapter
import com.example.data.model.ParsedDocument
import com.example.data.model.ParsedSlide
import com.example.data.model.SlideElement
import com.example.data.model.SlideParagraph
import com.example.data.model.SlideShapeGeometry
import com.example.data.model.SlideTableCell
import com.example.data.model.SlideTextAlign
import com.example.data.model.SlideTextRun
import com.example.data.model.SlideVerticalAlign
import com.example.data.model.TocEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * PresentationML / DrawingML OOXML PowerPoint (.pptx) processor built on Jsoup's XML DOM parser.
 * Preserves authentic original slide dimensions (EMUs -> PDF points), slide ordering from
 * presentation.xml, Office theme color schemes, slideLayout/slideMaster placeholder geometry &
 * backgrounds, exact shape/image/table coordinates, grouped shapes, styled text runs, and notes.
 */
object PptxDocumentEngine {

    private data class RectNorm(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val rotationDeg: Float = 0f
    )

    private data class SlideBgInfo(
        val colorArgb: Long = 0xFFFFFFFFL,
        val gradientEndArgb: Long? = null,
        val imagePath: String? = null
    )

    private data class PlaceholderDefaults(
        val rect: RectNorm? = null,
        val defaultFontPt: Float? = null,
        val defaultBold: Boolean? = null,
        val defaultColorArgb: Long? = null,
        val defaultAlign: SlideTextAlign? = null
    )

    fun parsePptx(context: Context, file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        var docTitle = fallbackTitle
        var docAuthor = "Presentación PowerPoint"
        var slideWidthEmu = 12192000L // Default 16:9 widescreen (960pt x 540pt)
        var slideHeightEmu = 6858000L

        val slides = mutableListOf<ParsedSlide>()
        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        val mediaCacheDir = File(
            context.cacheDir,
            "pptx_media/${abs(file.absolutePath.hashCode())}_${file.lastModified()}"
        ).apply { mkdirs() }

        ZipFile(file).use { zip ->
            // 1. Metadata
            readXmlDoc(zip, "docProps/core.xml")?.let { core ->
                core.getElementsByTag("dc:title").firstOrNull()?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }?.let { docTitle = it }
                core.getElementsByTag("dc:creator").firstOrNull()?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }?.let { docAuthor = it }
            }

            // 2. Office Theme Palette (ppt/theme/theme1.xml or first theme)
            val themeEntryName = zip.entries().asSequence()
                .map { it.name }
                .firstOrNull { it.startsWith("ppt/theme/theme", ignoreCase = true) && it.endsWith(".xml", true) }
            val themeColors = parseThemeColors(themeEntryName?.let { readXmlDoc(zip, it) })

            // 3. Presentation slide dimensions + authoritative slide order from presentation.xml
            val presDoc = readXmlDoc(zip, "ppt/presentation.xml")
            val presRels = parseRelationships(readXmlDoc(zip, "ppt/_rels/presentation.xml.rels"))
            presDoc?.firstTagLocal("sldSz")?.let { sldSz ->
                val cx = sldSz.attr("cx").toLongOrNull()
                val cy = sldSz.attr("cy").toLongOrNull()
                if (cx != null && cy != null && cx > 0L && cy > 0L) {
                    slideWidthEmu = cx
                    slideHeightEmu = cy
                }
            }

            val slideW = slideWidthEmu.toFloat().coerceAtLeast(1f)
            val slideH = slideHeightEmu.toFloat().coerceAtLeast(1f)
            val slideWidthPt = (slideW / 12700f).coerceIn(320f, 2400f)
            val slideHeightPt = (slideH / 12700f).coerceIn(240f, 2400f)

            val orderedSlidePaths = mutableListOf<String>()
            presDoc?.firstTagLocal("sldIdLst")?.childrenByLocalName("sldId")?.forEach { sldId ->
                val rId = sldId.attrByLocalName("id")
                val target = presRels[rId]?.second
                if (!target.isNullOrBlank()) {
                    val resolved = resolveZipPath("ppt/", target)
                    if (zip.getEntry(resolved) != null && resolved !in orderedSlidePaths) {
                        orderedSlidePaths.add(resolved)
                    }
                }
            }
            if (orderedSlidePaths.isEmpty()) {
                val slideRegex = Regex("^ppt/slides/slide(\\d+)\\.xml$", RegexOption.IGNORE_CASE)
                orderedSlidePaths.addAll(
                    zip.entries().asSequence()
                        .mapNotNull { entry ->
                            val m = slideRegex.matchEntire(entry.name) ?: return@mapNotNull null
                            val num = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                            num to entry.name
                        }
                        .sortedBy { it.first }
                        .map { it.second }
                        .toList()
                )
            }

            // Cache layout & master placeholder maps and backgrounds
            val layoutCache = mutableMapOf<String, Pair<Map<String, PlaceholderDefaults>, SlideBgInfo?>>()
            val masterCache = mutableMapOf<String, Pair<Map<String, PlaceholderDefaults>, SlideBgInfo?>>()

            fun loadTemplateInfo(
                xmlPath: String,
                cache: MutableMap<String, Pair<Map<String, PlaceholderDefaults>, SlideBgInfo?>>
            ): Pair<Map<String, PlaceholderDefaults>, SlideBgInfo?> {
                cache[xmlPath]?.let { return it }
                val doc = readXmlDoc(zip, xmlPath) ?: return (emptyMap<String, PlaceholderDefaults>() to null)
                val baseDir = xmlPath.substringBeforeLast('/', "") + "/"
                val fileName = xmlPath.substringAfterLast('/')
                val rels = parseRelationships(readXmlDoc(zip, "${baseDir}_rels/$fileName.rels"))
                val phMap = extractPlaceholderDefaults(doc, slideW, slideH, themeColors)
                val bg = extractBackground(doc, rels, baseDir, zip, mediaCacheDir, themeColors, "tpl_${abs(xmlPath.hashCode())}")
                return (phMap to bg).also { cache[xmlPath] = it }
            }

            // 4. Parse every slide in presentation order
            orderedSlidePaths.forEachIndexed { slideIdx, slidePath ->
                val slideDoc = readXmlDoc(zip, slidePath) ?: return@forEachIndexed
                val slideDir = slidePath.substringBeforeLast('/', "ppt/slides") + "/"
                val slideFileName = slidePath.substringAfterLast('/')
                val slideRels = parseRelationships(readXmlDoc(zip, "${slideDir}_rels/$slideFileName.rels"))

                // Resolve SlideLayout -> SlideMaster inheritance
                val layoutTarget = slideRels.values.firstOrNull { it.first.contains("slideLayout", true) }?.second
                val layoutPath = layoutTarget?.let { resolveZipPath(slideDir, it) }
                val (layoutPh, layoutBg) = layoutPath?.let { loadTemplateInfo(it, layoutCache) }
                    ?: (emptyMap<String, PlaceholderDefaults>() to null)

                val masterPath = layoutPath?.let { lp ->
                    val lDir = lp.substringBeforeLast('/', "") + "/"
                    val lName = lp.substringAfterLast('/')
                    val lRels = parseRelationships(readXmlDoc(zip, "${lDir}_rels/$lName.rels"))
                    lRels.values.firstOrNull { it.first.contains("slideMaster", true) }?.second
                        ?.let { resolveZipPath(lDir, it) }
                }
                val (masterPh, masterBg) = masterPath?.let { loadTemplateInfo(it, masterCache) }
                    ?: (emptyMap<String, PlaceholderDefaults>() to null)

                val mergedPlaceholders = HashMap<String, PlaceholderDefaults>().apply {
                    putAll(masterPh)
                    for ((k, v) in layoutPh) {
                        val prev = this[k]
                        this[k] = PlaceholderDefaults(
                            rect = v.rect ?: prev?.rect,
                            defaultFontPt = v.defaultFontPt ?: prev?.defaultFontPt,
                            defaultBold = v.defaultBold ?: prev?.defaultBold,
                            defaultColorArgb = v.defaultColorArgb ?: prev?.defaultColorArgb,
                            defaultAlign = v.defaultAlign ?: prev?.defaultAlign
                        )
                    }
                }

                // Resolve slide background (Slide -> Layout -> Master -> Default white)
                val slideBg = extractBackground(slideDoc, slideRels, slideDir, zip, mediaCacheDir, themeColors, "s${slideIdx}_bg")
                    ?: layoutBg
                    ?: masterBg
                    ?: SlideBgInfo()

                val isDarkSlideBg = isColorDark(slideBg.colorArgb)
                val defaultInkArgb = if (isDarkSlideBg) 0xFFF4F7FCL else (themeColors["dk1"] ?: 0xFF151C2BL)

                val elements = mutableListOf<SlideElement>()
                val slideImagePaths = mutableListOf<String>()
                var extractedTitle = ""
                var extractedSubtitle = ""
                val bodyBlocks = mutableListOf<ParsedBlock>()

                fun processSpTree(
                    container: Element,
                    parentLeft: Float,
                    parentTop: Float,
                    parentW: Float,
                    parentH: Float,
                    chOffX: Float,
                    chOffY: Float,
                    chExtW: Float,
                    chExtH: Float
                ) {
                    for (child in container.children()) {
                        val local = child.localTagName()
                        when (local) {
                            "grpSp" -> {
                                val grpXfrm = child.firstTagLocal("grpSpPr")?.firstTagLocal("xfrm")
                                val grpRect = parseTransform(grpXfrm, chOffX, chOffY, chExtW, chExtH, parentLeft, parentTop, parentW, parentH)
                                val subOff = grpXfrm?.firstTagLocal("chOff")
                                val subExt = grpXfrm?.firstTagLocal("chExt")
                                val nextChX = subOff?.attr("x")?.toFloatOrNull() ?: 0f
                                val nextChY = subOff?.attr("y")?.toFloatOrNull() ?: 0f
                                val nextChW = subExt?.attr("cx")?.toFloatOrNull()?.takeIf { it > 0f } ?: slideW
                                val nextChH = subExt?.attr("cy")?.toFloatOrNull()?.takeIf { it > 0f } ?: slideH
                                if (grpRect != null) {
                                    processSpTree(
                                        child,
                                        grpRect.left,
                                        grpRect.top,
                                        grpRect.width,
                                        grpRect.height,
                                        nextChX,
                                        nextChY,
                                        nextChW,
                                        nextChH
                                    )
                                } else {
                                    processSpTree(child, parentLeft, parentTop, parentW, parentH, chOffX, chOffY, chExtW, chExtH)
                                }
                            }

                            "pic" -> {
                                val xfrm = child.firstTagLocal("spPr")?.firstTagLocal("xfrm")
                                val rect = parseTransform(xfrm, chOffX, chOffY, chExtW, chExtH, parentLeft, parentTop, parentW, parentH)
                                    ?: continue
                                val blip = child.firstTagLocal("blipFill")?.firstTagLocal("blip")
                                val embedId = blip?.attrByLocalName("embed")?.takeIf { it.isNotBlank() }
                                    ?: blip?.attrByLocalName("link")?.takeIf { it.isNotBlank() }
                                    ?: continue
                                val imgFile = extractMediaImage(zip, slideRels, slideDir, embedId, mediaCacheDir, "s${slideIdx}_${embedId}")
                                if (imgFile != null) {
                                    slideImagePaths.add(imgFile)
                                    elements.add(
                                        SlideElement.Picture(
                                            left = rect.left.coerceIn(0f, 0.98f),
                                            top = rect.top.coerceIn(0f, 0.98f),
                                            width = rect.width.coerceIn(0.02f, 1f),
                                            height = rect.height.coerceIn(0.02f, 1f),
                                            rotationDeg = rect.rotationDeg,
                                            imagePath = imgFile
                                        )
                                    )
                                }
                            }

                            "sp", "cxnSp" -> {
                                val phEl = child.firstTagLocal("nvSpPr")?.firstTagLocal("nvPr")?.firstTagLocal("ph")
                                val phType = phEl?.attr("type").orEmpty()
                                val phIdx = phEl?.attr("idx").orEmpty()
                                val isFooterPh = phType.equals("sldNum", true) ||
                                    phType.equals("dt", true) ||
                                    phType.equals("ftr", true)

                                val inherited = lookupPlaceholder(mergedPlaceholders, phType, phIdx)
                                val spPr = child.firstTagLocal("spPr")
                                val rect = parseTransform(
                                    spPr?.firstTagLocal("xfrm"),
                                    chOffX,
                                    chOffY,
                                    chExtW,
                                    chExtH,
                                    parentLeft,
                                    parentTop,
                                    parentW,
                                    parentH
                                ) ?: inherited?.rect ?: defaultRectForPlaceholder(phType) ?: continue

                                val prst = spPr?.firstTagLocal("prstGeom")?.attr("prst").orEmpty()
                                val geometry = when {
                                    local == "cxnSp" || prst.contains("line", true) -> SlideShapeGeometry.LINE
                                    prst.contains("ellipse", true) || prst.contains("oval", true) -> SlideShapeGeometry.ELLIPSE
                                    prst.contains("roundRect", true) -> SlideShapeGeometry.ROUND_RECT
                                    else -> SlideShapeGeometry.RECT
                                }

                                val (fillArgb, gradEndArgb) = parseFillColors(spPr, themeColors, child.firstTagLocal("style"))
                                val lnEl = spPr?.firstTagLocal("ln")
                                val hasNoLine = lnEl?.firstTagLocal("noFill") != null
                                val borderArgb = if (hasNoLine) null else parseColorContainer(lnEl, themeColors)
                                val borderWidthPt = if (borderArgb != null) {
                                    val wEmu = lnEl?.attr("w")?.toFloatOrNull() ?: 12700f
                                    (wEmu / 12700f).coerceIn(0.6f, 6f)
                                } else {
                                    0f
                                }

                                val txBody = child.firstTagLocal("txBody")
                                val bodyPr = txBody?.firstTagLocal("bodyPr")
                                val vAlign = when (bodyPr?.attr("anchor")) {
                                    "ctr" -> SlideVerticalAlign.MIDDLE
                                    "b" -> SlideVerticalAlign.BOTTOM
                                    else -> SlideVerticalAlign.TOP
                                }

                                val shapeBgForContrast = fillArgb ?: slideBg.colorArgb
                                val fallbackRunColor = inherited?.defaultColorArgb
                                    ?: if (isColorDark(shapeBgForContrast)) 0xFFF8FAFFL else defaultInkArgb
                                val defaultFontSize = inherited?.defaultFontPt ?: when {
                                    phType.equals("title", true) || phType.equals("ctrTitle", true) -> 30f
                                    phType.equals("subTitle", true) -> 18f
                                    isFooterPh -> 10f
                                    else -> 15f
                                }
                                val defaultBold = inherited?.defaultBold
                                    ?: (phType.equals("title", true) || phType.equals("ctrTitle", true))
                                val defaultAlign = inherited?.defaultAlign ?: if (phType.equals("ctrTitle", true)) {
                                    SlideTextAlign.CENTER
                                } else {
                                    SlideTextAlign.LEFT
                                }

                                val paragraphs = if (txBody != null) {
                                    parseParagraphs(
                                        txBody = txBody,
                                        themeColors = themeColors,
                                        defaultFontPt = defaultFontSize,
                                        defaultBold = defaultBold,
                                        defaultColorArgb = fallbackRunColor,
                                        defaultAlign = defaultAlign
                                    )
                                } else {
                                    emptyList()
                                }

                                val hasVisualFill = fillArgb != null || borderArgb != null || geometry == SlideShapeGeometry.LINE
                                if (paragraphs.isNotEmpty() || hasVisualFill) {
                                    elements.add(
                                        SlideElement.ShapeBox(
                                            left = rect.left.coerceIn(-0.05f, 0.99f),
                                            top = rect.top.coerceIn(-0.05f, 0.99f),
                                            width = rect.width.coerceIn(0.005f, 1.05f),
                                            height = rect.height.coerceIn(0.005f, 1.05f),
                                            rotationDeg = rect.rotationDeg,
                                            geometry = geometry,
                                            fillColorArgb = fillArgb,
                                            gradientEndColorArgb = gradEndArgb,
                                            borderColorArgb = borderArgb,
                                            borderWidthPt = borderWidthPt,
                                            verticalAlign = vAlign,
                                            paragraphs = paragraphs
                                        )
                                    )
                                }

                                // Also extract structured text for TOC, Search, and Adapted (EPUB) view!
                                if (!isFooterPh && paragraphs.isNotEmpty()) {
                                    for (para in paragraphs) {
                                        val plain = para.runs.joinToString("") { it.text }.trim()
                                        if (plain.isEmpty()) continue
                                        val isTitle = phType.equals("title", true) || phType.equals("ctrTitle", true)
                                        val isSub = phType.equals("subTitle", true)
                                        when {
                                            isTitle && extractedTitle.isBlank() -> extractedTitle = plain
                                            isTitle -> extractedTitle = "$extractedTitle $plain"
                                            isSub && extractedSubtitle.isBlank() -> extractedSubtitle = plain
                                            isSub -> extractedSubtitle = "$extractedSubtitle\n$plain"
                                            extractedTitle.isBlank() && bodyBlocks.isEmpty() && plain.length <= 92 -> extractedTitle = plain
                                            else -> {
                                                val bType = if (para.bulletPrefix.isNotEmpty() || para.level > 0) {
                                                    BlockType.LIST_ITEM
                                                } else {
                                                    BlockType.PARAGRAPH
                                                }
                                                bodyBlocks.add(ParsedBlock(bodyBlocks.size, bType, plain))
                                            }
                                        }
                                    }
                                }
                            }

                            "graphicFrame" -> {
                                val xfrm = child.firstTagLocal("xfrm")
                                val rect = parseTransform(xfrm, chOffX, chOffY, chExtW, chExtH, parentLeft, parentTop, parentW, parentH)
                                    ?: continue
                                val tbl = child.firstTagLocal("tbl") ?: continue
                                val tableEl = parseTable(tbl, rect, themeColors, defaultInkArgb)
                                if (tableEl != null) {
                                    elements.add(tableEl)
                                    for (row in tableEl.rows) {
                                        val rowText = row.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("  ·  ")
                                        if (rowText.isNotEmpty()) {
                                            bodyBlocks.add(ParsedBlock(bodyBlocks.size, BlockType.PARAGRAPH, rowText))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                slideDoc.firstTagLocal("spTree")?.let { spTree ->
                    processSpTree(
                        container = spTree,
                        parentLeft = 0f,
                        parentTop = 0f,
                        parentW = 1f,
                        parentH = 1f,
                        chOffX = 0f,
                        chOffY = 0f,
                        chExtW = slideW,
                        chExtH = slideH
                    )
                }

                // Extract speaker notes
                val notesTarget = slideRels.values.firstOrNull { it.first.contains("notesSlide", true) }?.second
                val notesText = notesTarget?.let { target ->
                    val notesPath = resolveZipPath(slideDir, target)
                    readXmlDoc(zip, notesPath)?.let { notesDoc ->
                        notesDoc.getElementsByTag("a:t").joinToString(" ") { it.text().trim() }.trim()
                    }
                }.orEmpty()

                val slideTitle = extractedTitle.ifBlank { "Diapositiva ${slideIdx + 1}" }
                val finalBodyBlocks = bodyBlocks.ifEmpty {
                    if (slideImagePaths.isEmpty() && extractedSubtitle.isBlank() && elements.isEmpty()) {
                        listOf(ParsedBlock(0, BlockType.PARAGRAPH, "Diapositiva ${slideIdx + 1}"))
                    } else {
                        emptyList()
                    }
                }

                val parsedSlide = ParsedSlide(
                    index = slideIdx,
                    title = slideTitle,
                    subtitle = extractedSubtitle,
                    bodyBlocks = finalBodyBlocks,
                    notes = notesText,
                    imagePaths = slideImagePaths,
                    backgroundColorArgb = slideBg.colorArgb,
                    backgroundGradientEndArgb = slideBg.gradientEndArgb,
                    backgroundImagePath = slideBg.imagePath,
                    elements = elements
                )
                slides.add(parsedSlide)

                val chapterBlocks = buildList {
                    var bIdx = 0
                    add(ParsedBlock(bIdx++, BlockType.HEADING_1, slideTitle))
                    if (extractedSubtitle.isNotBlank()) {
                        add(ParsedBlock(bIdx++, BlockType.HEADING_2, extractedSubtitle))
                    }
                    for (b in finalBodyBlocks) {
                        add(b.copy(index = bIdx++))
                    }
                    if (notesText.isNotBlank()) {
                        add(ParsedBlock(bIdx++, BlockType.QUOTE, "Notas: $notesText"))
                    }
                }
                val words = chapterBlocks.sumOf { countWords(it.text) }
                chapters.add(
                    ParsedChapter(
                        index = slideIdx,
                        title = "${slideIdx + 1}. ${slideTitle.take(54)}",
                        cfiPrefix = "pptx(/slide/$slideIdx)",
                        href = "slide_${slideIdx + 1}",
                        blocks = chapterBlocks,
                        wordCount = words
                    )
                )
                toc.add(TocEntry(title = "${slideIdx + 1}. ${slideTitle.take(56)}", targetIndex = slideIdx, level = 0))
            }

            if (slides.isEmpty()) {
                val fallbackSlide = ParsedSlide(
                    index = 0,
                    title = docTitle,
                    bodyBlocks = listOf(ParsedBlock(0, BlockType.PARAGRAPH, "No se encontraron diapositivas visibles en este archivo PPTX."))
                )
                slides.add(fallbackSlide)
                chapters.add(
                    ParsedChapter(0, docTitle, "pptx(/slide/0)", "slide_1", fallbackSlide.bodyBlocks, 9)
                )
                toc.add(TocEntry(docTitle, 0, 0))
            }

            return ParsedDocument(
                title = docTitle,
                author = docAuthor,
                format = DocumentFormat.PPTX,
                chapters = chapters,
                toc = toc,
                totalWords = chapters.sumOf { it.wordCount }.coerceAtLeast(slides.size * 25),
                slides = slides,
                nativePageWidthPt = slideWidthPt,
                nativePageHeightPt = slideHeightPt,
                detectedFormatLabel = detectSlideFormatLabel(slideWidthPt, slideHeightPt)
            )
        }
    }

    private fun readXmlDoc(zip: ZipFile, path: String): Document? {
        val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/")) ?: return null
        return runCatching {
            zip.getInputStream(entry).buffered(16 * 1024).use { stream ->
                Jsoup.parse(stream, "UTF-8", "", Parser.xmlParser())
            }
        }.getOrNull()
    }

    private fun parseRelationships(doc: Document?): Map<String, Pair<String, String>> {
        if (doc == null) return emptyMap()
        val map = mutableMapOf<String, Pair<String, String>>()
        for (el in doc.allElements) {
            if (el.localTagName() == "Relationship") {
                val id = el.attr("Id")
                val type = el.attr("Type")
                val target = el.attr("Target")
                if (id.isNotBlank() && target.isNotBlank()) {
                    map[id] = type to target
                }
            }
        }
        return map
    }

    private fun parseThemeColors(themeDoc: Document?): Map<String, Long> {
        val defaults = mutableMapOf(
            "dk1" to 0xFF101828L,
            "lt1" to 0xFFFFFFFFL,
            "dk2" to 0xFF344054L,
            "lt2" to 0xFFF2F4F7L,
            "accent1" to 0xFF2563EBL,
            "accent2" to 0xFFE11D48L,
            "accent3" to 0xFF10B981L,
            "accent4" to 0xFF8B5CF6L,
            "accent5" to 0xFFF59E0BL,
            "accent6" to 0xFF0EA5E9L,
            "hlink" to 0xFF1D4ED8L,
            "folHlink" to 0xFF6D28D9L
        )
        themeDoc?.firstTagLocal("clrScheme")?.children()?.forEach { slot ->
            val key = slot.localTagName()
            val srgb = slot.firstTagLocal("srgbClr")?.attr("val")
                ?: slot.firstTagLocal("sysClr")?.attr("lastClr")
            parseHexColor(srgb)?.let { defaults[key] = it }
        }
        defaults["tx1"] = defaults["dk1"] ?: 0xFF101828L
        defaults["bg1"] = defaults["lt1"] ?: 0xFFFFFFFFL
        defaults["tx2"] = defaults["dk2"] ?: 0xFF344054L
        defaults["bg2"] = defaults["lt2"] ?: 0xFFF2F4F7L
        return defaults
    }

    private fun extractPlaceholderDefaults(
        doc: Document,
        slideW: Float,
        slideH: Float,
        themeColors: Map<String, Long>
    ): Map<String, PlaceholderDefaults> {
        val map = mutableMapOf<String, PlaceholderDefaults>()
        val spTree = doc.firstTagLocal("spTree") ?: return map
        for (sp in spTree.childrenByLocalName("sp")) {
            val ph = sp.firstTagLocal("nvSpPr")?.firstTagLocal("nvPr")?.firstTagLocal("ph") ?: continue
            val type = ph.attr("type").lowercase()
            val idx = ph.attr("idx")
            val rect = parseTransform(
                sp.firstTagLocal("spPr")?.firstTagLocal("xfrm"),
                0f, 0f, slideW, slideH, 0f, 0f, 1f, 1f
            )
            val defRPr = sp.firstTagLocal("txBody")?.firstTagLocal("lstStyle")?.firstTagLocal("lvl1pPr")?.firstTagLocal("defRPr")
            val fontPt = defRPr?.attr("sz")?.toFloatOrNull()?.let { (it / 100f).coerceIn(8f, 72f) }
            val bold = defRPr?.attr("b")?.let { it == "1" || it.equals("true", true) }
            val color = parseColorContainer(defRPr, themeColors)
            val align = parseAlign(sp.firstTagLocal("txBody")?.firstTagLocal("lstStyle")?.firstTagLocal("lvl1pPr")?.attr("algn"))
            val info = PlaceholderDefaults(rect, fontPt, bold, color, align)
            if (type.isNotBlank() && idx.isNotBlank()) map["$type#$idx"] = info
            if (type.isNotBlank() && !map.containsKey(type)) map[type] = info
            if (idx.isNotBlank() && !map.containsKey("#$idx")) map["#$idx"] = info
        }
        return map
    }

    private fun lookupPlaceholder(
        map: Map<String, PlaceholderDefaults>,
        phType: String,
        phIdx: String
    ): PlaceholderDefaults? {
        val t = phType.lowercase()
        if (t.isNotBlank() && phIdx.isNotBlank()) map["$t#$phIdx"]?.let { return it }
        if (t.isNotBlank()) map[t]?.let { return it }
        if (phIdx.isNotBlank()) map["#$phIdx"]?.let { return it }
        return null
    }

    private fun defaultRectForPlaceholder(phType: String): RectNorm? = when (phType.lowercase()) {
        "ctrtitle" -> RectNorm(0.08f, 0.24f, 0.84f, 0.24f)
        "title" -> RectNorm(0.06f, 0.06f, 0.88f, 0.16f)
        "subtitle" -> RectNorm(0.10f, 0.50f, 0.80f, 0.18f)
        "body", "obj" -> RectNorm(0.06f, 0.24f, 0.88f, 0.66f)
        else -> null
    }

    private fun extractBackground(
        doc: Document,
        rels: Map<String, Pair<String, String>>,
        baseDir: String,
        zip: ZipFile,
        cacheDir: File,
        themeColors: Map<String, Long>,
        prefix: String
    ): SlideBgInfo? {
        val bgEl = doc.firstTagLocal("cSld")?.firstTagLocal("bg") ?: return null
        val bgPr = bgEl.firstTagLocal("bgPr")
        if (bgPr != null) {
            bgPr.firstTagLocal("blipFill")?.firstTagLocal("blip")?.let { blip ->
                val embed = blip.attrByLocalName("embed")
                if (embed.isNotBlank()) {
                    val img = extractMediaImage(zip, rels, baseDir, embed, cacheDir, "${prefix}_$embed")
                    if (img != null) return SlideBgInfo(colorArgb = 0xFFFFFFFFL, imagePath = img)
                }
            }
            val (startColor, endColor) = parseFillColors(bgPr, themeColors, null)
            if (startColor != null) {
                return SlideBgInfo(colorArgb = startColor, gradientEndArgb = endColor)
            }
        }
        val bgRef = bgEl.firstTagLocal("bgRef")
        if (bgRef != null) {
            parseColorContainer(bgRef, themeColors)?.let { c ->
                return SlideBgInfo(colorArgb = c)
            }
        }
        return null
    }

    private fun parseTransform(
        xfrm: Element?,
        chOffX: Float,
        chOffY: Float,
        chExtW: Float,
        chExtH: Float,
        parentLeft: Float,
        parentTop: Float,
        parentW: Float,
        parentH: Float
    ): RectNorm? {
        if (xfrm == null) return null
        val off = xfrm.firstTagLocal("off") ?: return null
        val ext = xfrm.firstTagLocal("ext") ?: return null
        val x = off.attr("x").toFloatOrNull() ?: return null
        val y = off.attr("y").toFloatOrNull() ?: return null
        val cx = ext.attr("cx").toFloatOrNull() ?: return null
        val cy = ext.attr("cy").toFloatOrNull() ?: return null
        val rot = (xfrm.attr("rot").toFloatOrNull() ?: 0f) / 60000f

        val safeChW = chExtW.coerceAtLeast(1f)
        val safeChH = chExtH.coerceAtLeast(1f)
        val left = parentLeft + ((x - chOffX) / safeChW) * parentW
        val top = parentTop + ((y - chOffY) / safeChH) * parentH
        val width = (cx / safeChW) * parentW
        val height = (cy / safeChH) * parentH
        return RectNorm(left, top, width, height, rot)
    }

    private fun parseFillColors(
        spPr: Element?,
        themeColors: Map<String, Long>,
        styleEl: Element?
    ): Pair<Long?, Long?> {
        if (spPr != null) {
            if (spPr.firstTagLocal("noFill") != null) return null to null
            spPr.firstTagLocal("solidFill")?.let { sf ->
                return parseColorContainer(sf, themeColors) to null
            }
            spPr.firstTagLocal("gradFill")?.let { gf ->
                val stops = gf.firstTagLocal("gsLst")?.childrenByLocalName("gs").orEmpty()
                val first = stops.firstOrNull()?.let { parseColorContainer(it, themeColors) }
                val last = stops.lastOrNull()?.let { parseColorContainer(it, themeColors) }
                if (first != null) return first to (last?.takeIf { it != first })
            }
        }
        styleEl?.firstTagLocal("fillRef")?.let { fillRef ->
            val idx = fillRef.attr("idx").toIntOrNull() ?: 0
            if (idx > 0) {
                parseColorContainer(fillRef, themeColors)?.let { return it to null }
            }
        }
        return null to null
    }

    private fun parseParagraphs(
        txBody: Element,
        themeColors: Map<String, Long>,
        defaultFontPt: Float,
        defaultBold: Boolean,
        defaultColorArgb: Long,
        defaultAlign: SlideTextAlign
    ): List<SlideParagraph> {
        val out = mutableListOf<SlideParagraph>()
        var autoNumCounter = 1

        for (pEl in txBody.childrenByLocalName("p")) {
            val pPr = pEl.firstTagLocal("pPr")
            val align = parseAlign(pPr?.attr("algn")) ?: defaultAlign
            val level = pPr?.attr("lvl")?.toIntOrNull()?.coerceIn(0, 4) ?: 0
            val buNone = pPr?.firstTagLocal("buNone") != null
            val buChar = pPr?.firstTagLocal("buChar")?.attr("char")
            val buAuto = pPr?.firstTagLocal("buAutoNum") != null
            val bulletPrefix = when {
                buNone -> ""
                !buChar.isNullOrBlank() -> "$buChar "
                buAuto -> "${autoNumCounter++}. "
                level > 0 -> "• "
                else -> ""
            }
            if (!buAuto) autoNumCounter = 1

            val defRPr = pPr?.firstTagLocal("defRPr")
            val pDefaultSize = defRPr?.attr("sz")?.toFloatOrNull()?.let { (it / 100f).coerceIn(7f, 72f) } ?: defaultFontPt
            val pDefaultBold = defRPr?.attr("b")?.let { it == "1" || it.equals("true", true) } ?: defaultBold
            val pDefaultColor = parseColorContainer(defRPr, themeColors) ?: defaultColorArgb

            val runs = mutableListOf<SlideTextRun>()
            for (child in pEl.children()) {
                when (child.localTagName()) {
                    "r", "fld" -> {
                        val text = child.firstTagLocal("t")?.text().orEmpty()
                        if (text.isEmpty()) continue
                        val rPr = child.firstTagLocal("rPr")
                        val sz = rPr?.attr("sz")?.toFloatOrNull()?.let { (it / 100f).coerceIn(7f, 72f) } ?: pDefaultSize
                        val bold = rPr?.attr("b")?.let { it == "1" || it.equals("true", true) } ?: pDefaultBold
                        val italic = rPr?.attr("i")?.let { it == "1" || it.equals("true", true) } ?: false
                        val underline = rPr?.attr("u")?.let { it.isNotBlank() && !it.equals("none", true) } ?: false
                        val color = parseColorContainer(rPr?.firstTagLocal("solidFill") ?: rPr, themeColors) ?: pDefaultColor
                        runs.add(
                            SlideTextRun(
                                text = text,
                                fontSizePt = sz,
                                isBold = bold,
                                isItalic = italic,
                                isUnderline = underline,
                                colorArgb = color
                            )
                        )
                    }
                    "br" -> {
                        runs.add(SlideTextRun(text = "\n", fontSizePt = pDefaultSize, colorArgb = pDefaultColor))
                    }
                }
            }
            if (runs.isNotEmpty() && runs.any { it.text.isNotBlank() }) {
                out.add(SlideParagraph(runs = runs, align = align, bulletPrefix = bulletPrefix, level = level))
            }
        }
        return out
    }

    private fun parseTable(
        tbl: Element,
        rect: RectNorm,
        themeColors: Map<String, Long>,
        defaultInkArgb: Long
    ): SlideElement.Table? {
        val gridCols = tbl.firstTagLocal("tblGrid")?.childrenByLocalName("gridCol").orEmpty()
        val rawWeights = gridCols.map { (it.attr("w").toFloatOrNull() ?: 1f).coerceAtLeast(1f) }
        val rows = mutableListOf<List<SlideTableCell>>()

        for ((rowIdx, tr) in tbl.childrenByLocalName("tr").withIndex()) {
            val cells = mutableListOf<SlideTableCell>()
            for (tc in tr.childrenByLocalName("tc")) {
                val tcPr = tc.firstTagLocal("tcPr")
                val fill = parseColorContainer(tcPr?.firstTagLocal("solidFill") ?: tcPr, themeColors)
                val txBody = tc.firstTagLocal("txBody")
                val paragraphs = if (txBody != null) {
                    parseParagraphs(
                        txBody = txBody,
                        themeColors = themeColors,
                        defaultFontPt = 11f,
                        defaultBold = rowIdx == 0,
                        defaultColorArgb = if (fill != null && isColorDark(fill)) 0xFFFFFFFFL else defaultInkArgb,
                        defaultAlign = SlideTextAlign.LEFT
                    )
                } else {
                    emptyList()
                }
                val text = paragraphs.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }.trim()
                val firstRun = paragraphs.firstOrNull()?.runs?.firstOrNull()
                cells.add(
                    SlideTableCell(
                        text = text,
                        fillColorArgb = fill,
                        textColorArgb = firstRun?.colorArgb ?: defaultInkArgb,
                        isBold = firstRun?.isBold ?: (rowIdx == 0),
                        fontSizePt = firstRun?.fontSizePt ?: 11f,
                        align = paragraphs.firstOrNull()?.align ?: SlideTextAlign.LEFT
                    )
                )
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        if (rows.isEmpty()) return null
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val weights = if (rawWeights.size == colCount) rawWeights else List(colCount) { 1f }
        return SlideElement.Table(
            left = rect.left.coerceIn(0f, 0.98f),
            top = rect.top.coerceIn(0f, 0.98f),
            width = rect.width.coerceIn(0.05f, 1f),
            height = rect.height.coerceIn(0.05f, 1f),
            colWeights = weights,
            rows = rows
        )
    }

    private fun extractMediaImage(
        zip: ZipFile,
        rels: Map<String, Pair<String, String>>,
        baseDir: String,
        embedId: String,
        cacheDir: File,
        cacheKey: String
    ): String? {
        val target = rels[embedId]?.second ?: return null
        val resolvedZipPath = resolveZipPath(baseDir, target)
        val mediaEntry = zip.getEntry(resolvedZipPath) ?: return null
        val ext = resolvedZipPath.substringAfterLast('.', "png").lowercase()
        if (ext !in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")) return null
        val cachedImg = File(cacheDir, "${cacheKey}.$ext")
        if (!cachedImg.exists() || cachedImg.length() == 0L) {
            runCatching {
                zip.getInputStream(mediaEntry).use { inp ->
                    cachedImg.outputStream().use { out -> inp.copyTo(out) }
                }
            }
        }
        return cachedImg.absolutePath.takeIf { cachedImg.exists() && cachedImg.length() > 0L }
    }

    private fun parseColorContainer(container: Element?, themeColors: Map<String, Long>): Long? {
        if (container == null) return null
        val srgbEl = container.firstTagLocal("srgbClr")
        val schemeEl = container.firstTagLocal("schemeClr")
        val colorEl = srgbEl ?: schemeEl ?: return null

        var argb = if (srgbEl != null) {
            parseHexColor(srgbEl.attr("val")) ?: return null
        } else {
            val key = schemeEl!!.attr("val")
            themeColors[key] ?: themeColors[key.lowercase()] ?: return null
        }

        // Apply DrawingML color transforms (lumMod, lumOff, tint, shade, alpha)
        val lumMod = colorEl.firstTagLocal("lumMod")?.attr("val")?.toFloatOrNull()?.div(100000f)
        val lumOff = colorEl.firstTagLocal("lumOff")?.attr("val")?.toFloatOrNull()?.div(100000f)
        val tint = colorEl.firstTagLocal("tint")?.attr("val")?.toFloatOrNull()?.div(100000f)
        val shade = colorEl.firstTagLocal("shade")?.attr("val")?.toFloatOrNull()?.div(100000f)
        val alphaVal = colorEl.firstTagLocal("alpha")?.attr("val")?.toFloatOrNull()?.div(100000f)

        var r = AndroidColor.red(argb.toInt())
        var g = AndroidColor.green(argb.toInt())
        var b = AndroidColor.blue(argb.toInt())
        var a = AndroidColor.alpha(argb.toInt())

        if (tint != null) {
            r = (r * tint + 255f * (1f - tint)).toInt().coerceIn(0, 255)
            g = (g * tint + 255f * (1f - tint)).toInt().coerceIn(0, 255)
            b = (b * tint + 255f * (1f - tint)).toInt().coerceIn(0, 255)
        }
        if (shade != null) {
            r = (r * shade).toInt().coerceIn(0, 255)
            g = (g * shade).toInt().coerceIn(0, 255)
            b = (b * shade).toInt().coerceIn(0, 255)
        }
        if (lumMod != null || lumOff != null) {
            val mod = lumMod ?: 1f
            val off = (lumOff ?: 0f) * 255f
            r = (r * mod + off).toInt().coerceIn(0, 255)
            g = (g * mod + off).toInt().coerceIn(0, 255)
            b = (b * mod + off).toInt().coerceIn(0, 255)
        }
        if (alphaVal != null) {
            a = (a * alphaVal).toInt().coerceIn(0, 255)
        }

        argb = AndroidColor.argb(a, r, g, b).toLong() and 0xFFFFFFFFL
        return argb
    }

    private fun parseHexColor(hex: String?): Long? {
        val clean = hex?.trim()?.removePrefix("#") ?: return null
        if (clean.length != 6 && clean.length != 8) return null
        return runCatching {
            val v = clean.toLong(16)
            if (clean.length == 6) (0xFF000000L or v) else v
        }.getOrNull()
    }

    private fun isColorDark(argb: Long): Boolean {
        val c = argb.toInt()
        val lum = (0.299 * AndroidColor.red(c) + 0.587 * AndroidColor.green(c) + 0.114 * AndroidColor.blue(c)) / 255.0
        return lum < 0.48
    }

    private fun parseAlign(algn: String?): SlideTextAlign? = when (algn?.lowercase()) {
        "ctr" -> SlideTextAlign.CENTER
        "r" -> SlideTextAlign.RIGHT
        "just", "dist" -> SlideTextAlign.JUSTIFY
        "l" -> SlideTextAlign.LEFT
        else -> null
    }

    private fun detectSlideFormatLabel(widthPt: Float, heightPt: Float): String {
        val ratio = widthPt / heightPt.coerceAtLeast(1f)
        return when {
            abs(ratio - (16f / 9f)) < 0.10f -> "Slide 16:9"
            abs(ratio - (4f / 3f)) < 0.10f -> "Slide 4:3"
            abs(ratio - (16f / 10f)) < 0.08f -> "Slide 16:10"
            else -> "${(widthPt * 25.4f / 72f).toInt()}×${(heightPt * 25.4f / 72f).toInt()} mm"
        }
    }

    private fun resolveZipPath(baseDir: String, relative: String): String {
        if (relative.startsWith("/")) return relative.removePrefix("/")
        val parts = (baseDir + relative).split('/')
        val stack = ArrayDeque<String>()
        for (p in parts) {
            when (p) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(p)
            }
        }
        return stack.joinToString("/")
    }

    private fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun Element.localTagName(): String = tagName().substringAfter(':')

    private fun Element.firstTagLocal(localName: String): Element? {
        for (el in allElements) {
            if (el !== this && el.localTagName() == localName) return el
        }
        return null
    }

    private fun Element.childrenByLocalName(localName: String): List<Element> =
        children().filter { it.localTagName() == localName }

    private fun Element.attrByLocalName(localAttr: String): String {
        for (attr in attributes()) {
            if (attr.key.substringAfter(':') == localAttr) return attr.value
        }
        return ""
    }
}

package com.example.engine

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedChapter
import com.example.data.model.ParsedDocument
import com.example.data.model.TocEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

object DocumentParser {

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val BLANK_LINE_SPLIT = Regex("\\r?\\n\\s*\\r?\\n")
    private val MULTI_NEWLINE_REGEX = Regex("\\n{3,}")
    private val HORIZ_WS_REGEX = Regex("[ \\t]+")

    suspend fun parse(context: Context, file: File, format: DocumentFormat): ParsedDocument =
        withContext(Dispatchers.IO) {
            ParsedDocumentDiskCache.read(context, file, format)?.let { return@withContext it }

            val parsed = when (format) {
                DocumentFormat.EPUB -> parseEpubFull(file)
                DocumentFormat.DOCX -> parseDocxStreaming(file)
                DocumentFormat.PPTX -> PptxDocumentEngine.parsePptx(context, file)
                DocumentFormat.PDF -> parsePdfFull(context, file)
                DocumentFormat.TXT -> parseTxtStreaming(file)
                DocumentFormat.FB2 -> parseFb2Streaming(file)
            }
            ParsedDocumentDiskCache.write(context, file, format, parsed)
            parsed
        }

    /**
     * Progressive document parser:
     * 1. Returns immediately from [ParsedDocumentDiskCache] if previously parsed (~15-30ms).
     * 2. For EPUB: streams OPF + the active chapter window [priorityUnitIndex ± 1] first and emits
     *    via [onPartialReady] so the reader opens immediately, then streams remaining chapters in
     *    background and caches the full document.
     * 3. For PDF: opens [PdfRenderer] (~5-15ms) and emits page count & dimensions via
     *    [onPartialReady] immediately so visual pages render without waiting for PDFBox text
     *    extraction; then extracts searchable/reflowable PDF text lazily in background and caches.
     */
    suspend fun parseProgressive(
        context: Context,
        file: File,
        format: DocumentFormat,
        priorityUnitIndex: Int = 0,
        onPartialReady: suspend (ParsedDocument) -> Unit
    ): ParsedDocument = withContext(Dispatchers.IO) {
        ParsedDocumentDiskCache.read(context, file, format)?.let { cached ->
            return@withContext cached
        }

        val fullDoc = when (format) {
            DocumentFormat.EPUB -> parseEpubProgressive(file, priorityUnitIndex, onPartialReady)
            DocumentFormat.PDF -> parsePdfProgressive(context, file, onPartialReady)
            DocumentFormat.DOCX -> parseDocxStreaming(file)
            DocumentFormat.PPTX -> PptxDocumentEngine.parsePptx(context, file)
            DocumentFormat.TXT -> parseTxtStreaming(file)
            DocumentFormat.FB2 -> parseFb2Streaming(file)
        }
        coroutineContext.ensureActive()
        ParsedDocumentDiskCache.write(context, file, format, fullDoc)
        fullDoc
    }

    private suspend fun parseEpubFull(file: File): ParsedDocument =
        parseEpubProgressive(file = file, priorityUnitIndex = 0, onPartialReady = null)

    private suspend fun parseEpubProgressive(
        file: File,
        priorityUnitIndex: Int,
        onPartialReady: (suspend (ParsedDocument) -> Unit)?
    ): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        ZipFile(file).use { zip ->
            val opfPath = zip.getEntry("META-INF/container.xml")?.let { entry ->
                zip.getInputStream(entry).use { stream -> extractOpfPathStreaming(stream) }
            } ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", true) }?.name

            val opfBaseDir = opfPath?.substringBeforeLast('/', "")
                ?.let { if (it.isEmpty()) "" else "$it/" } ?: ""

            var title = fallbackTitle
            var author = "Autor desconocido"
            val manifest = mutableMapOf<String, String>()
            val spineIds = mutableListOf<String>()

            opfPath?.let { zip.getEntry(it) }?.let { opfEntry ->
                zip.getInputStream(opfEntry).use { stream ->
                    parseOpfStreaming(
                        inputStream = stream,
                        onTitle = { if (it.isNotBlank()) title = it },
                        onAuthor = { if (it.isNotBlank()) author = it },
                        manifest = manifest,
                        spineIds = spineIds
                    )
                }
            }

            val orderedPaths = if (spineIds.isNotEmpty()) {
                spineIds.mapNotNull { id -> manifest[id]?.let { resolveZipPath(opfBaseDir, it) } }
            } else {
                zip.entries().asSequence()
                    .filter {
                        val n = it.name.lowercase()
                        (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) &&
                            !n.contains("nav.") && !n.contains("toc.")
                    }
                    .map { it.name }
                    .sorted()
                    .toList()
            }

            // Only emit partial stage when the EPUB actually has many chapters
            val useProgressiveStage = onPartialReady != null && orderedPaths.size >= 6
            val slots = arrayOfNulls<ParsedChapter>(orderedPaths.size)

            suspend fun parseSlot(slotIdx: Int) {
                coroutineContext.ensureActive()
                val path = orderedPaths[slotIdx]
                val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/")) ?: return
                val blocks = zip.getInputStream(entry).use { stream ->
                    extractBlocksFromXhtmlStream(stream)
                }
                if (blocks.isNotEmpty()) {
                    val chapterTitle = blocks.firstOrNull {
                        it.type == BlockType.HEADING_1 || it.type == BlockType.HEADING_2
                    }?.text?.take(70) ?: "Capítulo ${slotIdx + 1}"
                    val words = blocks.sumOf { countWords(it.text) }
                    slots[slotIdx] = ParsedChapter(
                        index = slotIdx,
                        title = chapterTitle,
                        cfiPrefix = "epubcfi(/6/${(slotIdx + 1) * 2}!)",
                        href = path,
                        blocks = blocks,
                        wordCount = words
                    )
                }
            }

            if (useProgressiveStage) {
                val safePriority = priorityUnitIndex.coerceIn(0, orderedPaths.lastIndex)
                val priorityWindow = ((safePriority - 1).coerceAtLeast(0)..(safePriority + 1).coerceAtMost(orderedPaths.lastIndex))
                for (i in priorityWindow) {
                    parseSlot(i)
                }
                // Build a temporary document preserving slot positions for instant chapter display
                val partialChapters = ArrayList<ParsedChapter>(orderedPaths.size)
                val partialToc = ArrayList<TocEntry>(orderedPaths.size)
                for (i in orderedPaths.indices) {
                    val ch = slots[i] ?: ParsedChapter(
                        index = i,
                        title = "Capítulo ${i + 1}",
                        cfiPrefix = "epubcfi(/6/${(i + 1) * 2}!)",
                        href = orderedPaths[i],
                        blocks = listOf(ParsedBlock(0, BlockType.PARAGRAPH, "Cargando capítulo…")),
                        wordCount = 2
                    )
                    partialChapters.add(ch)
                    partialToc.add(TocEntry(title = ch.title, targetIndex = i, level = 0))
                }
                if (partialChapters.isNotEmpty()) {
                    onPartialReady.invoke(
                        ParsedDocument(
                            title = title,
                            author = author,
                            format = DocumentFormat.EPUB,
                            chapters = partialChapters,
                            toc = partialToc,
                            totalWords = partialChapters.sumOf { it.wordCount }
                        )
                    )
                }
                // Stream remaining chapters cleanly in background
                for (i in orderedPaths.indices) {
                    if (i !in priorityWindow) {
                        parseSlot(i)
                    }
                }
            } else {
                for (i in orderedPaths.indices) {
                    parseSlot(i)
                }
            }

            val chapters = mutableListOf<ParsedChapter>()
            val toc = mutableListOf<TocEntry>()
            var totalWords = 0
            for (i in orderedPaths.indices) {
                val raw = slots[i] ?: continue
                val chapterIdx = chapters.size
                val reindexed = raw.copy(
                    index = chapterIdx,
                    cfiPrefix = "epubcfi(/6/${(chapterIdx + 1) * 2}!)",
                    title = if (raw.title.startsWith("Capítulo ")) "Capítulo ${chapterIdx + 1}" else raw.title
                )
                totalWords += reindexed.wordCount
                chapters.add(reindexed)
                toc.add(TocEntry(title = reindexed.title, targetIndex = chapterIdx, level = 0))
            }

            if (chapters.isEmpty()) {
                val fallbackBlock = ParsedBlock(0, BlockType.PARAGRAPH, "No se encontró contenido legible en este archivo EPUB.")
                chapters.add(ParsedChapter(0, title, "epubcfi(/6/2!)", "", listOf(fallbackBlock), 8))
                toc.add(TocEntry(title, 0, 0))
            }

            return ParsedDocument(
                title = title,
                author = author,
                format = DocumentFormat.EPUB,
                chapters = chapters,
                toc = toc,
                totalWords = totalWords
            )
        }
    }

    private suspend fun parseDocxStreaming(file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        val allBlocks = mutableListOf<ParsedBlock>()
        var docTitle = fallbackTitle
        var docAuthor = "Documento DOCX"
        var nativeWidthPt = 595f
        var nativeHeightPt = 842f

        ZipFile(file).use { zip ->
            zip.getEntry("docProps/core.xml")?.let { coreEntry ->
                zip.getInputStream(coreEntry).use { stream ->
                    parseCorePropsStreaming(
                        inputStream = stream,
                        onTitle = { if (it.isNotBlank()) docTitle = it },
                        onAuthor = { if (it.isNotBlank()) docAuthor = it }
                    )
                }
            }

            val docXmlEntry = zip.getEntry("word/document.xml")
            if (docXmlEntry != null) {
                zip.getInputStream(docXmlEntry).use { rawStream ->
                    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                    val parser = factory.newPullParser()
                    parser.setInput(BufferedInputStream(rawStream, 16 * 1024), "UTF-8")

                    var inParagraph = false
                    var currentStyle = ""
                    val paraBuilder = StringBuilder(256)
                    var eventCounter = 0

                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if ((++eventCounter and 0xFF) == 0) coroutineContext.ensureActive()
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                val tag = parser.name.substringAfter(':')
                                when (tag) {
                                    "pgSz" -> {
                                        var wTwips: Float? = null
                                        var hTwips: Float? = null
                                        var orient = ""
                                        for (i in 0 until parser.attributeCount) {
                                            val attr = parser.getAttributeName(i).substringAfter(':')
                                            val v = parser.getAttributeValue(i)
                                            when (attr) {
                                                "w" -> wTwips = v.toFloatOrNull()
                                                "h" -> hTwips = v.toFloatOrNull()
                                                "orient" -> orient = v
                                            }
                                        }
                                        if (wTwips != null && hTwips != null && wTwips > 0f && hTwips > 0f) {
                                            var wPt = wTwips / 20f
                                            var hPt = hTwips / 20f
                                            if (orient.equals("landscape", ignoreCase = true) && wPt < hPt) {
                                                val tmp = wPt
                                                wPt = hPt
                                                hPt = tmp
                                            }
                                            nativeWidthPt = wPt.coerceIn(240f, 2400f)
                                            nativeHeightPt = hPt.coerceIn(240f, 2400f)
                                        }
                                    }
                                    "p" -> {
                                        inParagraph = true
                                        currentStyle = ""
                                        paraBuilder.clear()
                                    }
                                    "pStyle" -> {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).endsWith("val")) {
                                                currentStyle = parser.getAttributeValue(i)
                                            }
                                        }
                                    }
                                    "br" -> if (inParagraph) paraBuilder.append('\n')
                                    "tab" -> if (inParagraph) paraBuilder.append("  ")
                                    "t" -> {
                                        if (inParagraph) {
                                            paraBuilder.append(parser.nextText())
                                        }
                                    }
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                val tag = parser.name.substringAfter(':')
                                if (tag == "p" && inParagraph) {
                                    val text = paraBuilder.toString().trim()
                                    if (text.isNotEmpty()) {
                                        val blockType = when {
                                            currentStyle.contains("Heading1", true) ||
                                                currentStyle.contains("Title", true) ||
                                                currentStyle.equals("1", true) -> BlockType.HEADING_1
                                            currentStyle.contains("Heading2", true) ||
                                                currentStyle.contains("Heading3", true) ||
                                                currentStyle.contains("Subtitle", true) -> BlockType.HEADING_2
                                            currentStyle.contains("Quote", true) -> BlockType.QUOTE
                                            currentStyle.contains("List", true) -> BlockType.LIST_ITEM
                                            else -> BlockType.PARAGRAPH
                                        }
                                        allBlocks.add(ParsedBlock(allBlocks.size, blockType, text))
                                    }
                                    inParagraph = false
                                }
                            }
                        }
                        event = parser.next()
                    }
                }
            }
        }

        // Split DOCX into chapters at HEADING_1 boundaries for clean navigation & TOC
        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        var currentTitle = docTitle
        var currentBlocks = mutableListOf<ParsedBlock>()

        fun flushChapter() {
            if (currentBlocks.isEmpty()) return
            val idx = chapters.size
            val reindexed = currentBlocks.mapIndexed { i, b -> b.copy(index = i) }
            val words = reindexed.sumOf { countWords(it.text) }
            chapters.add(
                ParsedChapter(
                    index = idx,
                    title = currentTitle,
                    cfiPrefix = "docx(/section/$idx)",
                    href = "section_$idx",
                    blocks = reindexed,
                    wordCount = words
                )
            )
            toc.add(TocEntry(currentTitle, idx, 0))
            currentBlocks = mutableListOf()
        }

        for (block in allBlocks) {
            if (block.type == BlockType.HEADING_1 && currentBlocks.isNotEmpty()) {
                flushChapter()
                currentTitle = block.text.take(70)
            } else if (block.type == BlockType.HEADING_1 && currentBlocks.isEmpty()) {
                currentTitle = block.text.take(70)
            }
            currentBlocks.add(block)
        }
        flushChapter()

        if (chapters.isEmpty()) {
            val emptyBlock = ParsedBlock(0, BlockType.PARAGRAPH, "El documento DOCX no contiene texto visible.")
            chapters.add(ParsedChapter(0, docTitle, "docx(/section/0)", "section_0", listOf(emptyBlock), 7))
            toc.add(TocEntry(docTitle, 0, 0))
        }

        return ParsedDocument(
            title = docTitle,
            author = docAuthor,
            format = DocumentFormat.DOCX,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount },
            nativePageWidthPt = nativeWidthPt,
            nativePageHeightPt = nativeHeightPt,
            detectedFormatLabel = detectPageFormatLabel(nativeWidthPt, nativeHeightPt)
        )
    }

    private fun detectPageFormatLabel(
        widthPt: Float,
        heightPt: Float,
        isPresentation: Boolean = false
    ): String {
        val w = widthPt.coerceAtLeast(1f)
        val h = heightPt.coerceAtLeast(1f)
        val ratio = w / h
        if (isPresentation || ratio > 1.25f) {
            if (kotlin.math.abs(ratio - (16f / 9f)) < 0.12f) return "Slide 16:9"
            if (kotlin.math.abs(ratio - (4f / 3f)) < 0.10f) return "Slide 4:3"
        }
        val minSide = minOf(w, h)
        val maxSide = maxOf(w, h)
        val isLandscape = w > h * 1.05f
        val base = when {
            kotlin.math.abs(minSide - 595f) < 18f && kotlin.math.abs(maxSide - 842f) < 22f -> "A4"
            kotlin.math.abs(minSide - 612f) < 18f && kotlin.math.abs(maxSide - 792f) < 22f -> "Carta"
            kotlin.math.abs(minSide - 612f) < 18f && kotlin.math.abs(maxSide - 1008f) < 28f -> "Oficio"
            kotlin.math.abs(minSide - 842f) < 24f && kotlin.math.abs(maxSide - 1191f) < 30f -> "A3"
            kotlin.math.abs(minSide - 420f) < 16f && kotlin.math.abs(maxSide - 595f) < 20f -> "A5"
            else -> {
                val mmW = (w * 25.4f / 72f).toInt()
                val mmH = (h * 25.4f / 72f).toInt()
                "${mmW}×${mmH} mm"
            }
        }
        return if (isLandscape && !base.contains("×")) "$base Horiz." else base
    }

    private suspend fun parsePdfFull(context: Context, file: File): ParsedDocument =
        parsePdfProgressive(context = context, file = file, onPartialReady = null)

    private suspend fun parsePdfProgressive(
        context: Context,
        file: File,
        onPartialReady: (suspend (ParsedDocument) -> Unit)?
    ): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        var pageCount = 1
        var nativeWidthPt = 595f
        var nativeHeightPt = 842f
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { firstPage ->
                            nativeWidthPt = firstPage.width.toFloat().coerceIn(240f, 2400f)
                            nativeHeightPt = firstPage.height.toFloat().coerceIn(240f, 2400f)
                        }
                    }
                }
            }
        }

        val formatLabel = detectPageFormatLabel(nativeWidthPt, nativeHeightPt)
        var docTitle = fallbackTitle
        var docAuthor = "Documento PDF"

        // Emit fast visual-ready shell immediately so PdfManhwaReader displays pages in <50ms!
        if (onPartialReady != null) {
            val fastShell = buildPdfParsedDocument(
                docTitle = docTitle,
                docAuthor = docAuthor,
                pageCount = pageCount,
                pageTexts = List(pageCount) { "" },
                nativeWidthPt = nativeWidthPt,
                nativeHeightPt = nativeHeightPt,
                formatLabel = formatLabel
            )
            onPartialReady.invoke(fastShell)
        }

        val pageTexts = MutableList(pageCount) { "" }
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).use { pdDoc ->
                pdDoc.documentInformation?.title?.takeIf { it.isNotBlank() }?.let { docTitle = it }
                pdDoc.documentInformation?.author?.takeIf { it.isNotBlank() }?.let { docAuthor = it }
                val stripper = PDFTextStripper()
                val total = pdDoc.numberOfPages.coerceAtMost(pageCount)
                for (p in 0 until total) {
                    coroutineContext.ensureActive()
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    pageTexts[p] = runCatching { stripper.getText(pdDoc).trim() }.getOrDefault("")
                }
            }
        }

        return buildPdfParsedDocument(
            docTitle = docTitle,
            docAuthor = docAuthor,
            pageCount = pageCount,
            pageTexts = pageTexts,
            nativeWidthPt = nativeWidthPt,
            nativeHeightPt = nativeHeightPt,
            formatLabel = formatLabel
        )
    }

    private fun buildPdfParsedDocument(
        docTitle: String,
        docAuthor: String,
        pageCount: Int,
        pageTexts: List<String>,
        nativeWidthPt: Float,
        nativeHeightPt: Float,
        formatLabel: String
    ): ParsedDocument {
        val toc = (0 until pageCount).map { idx ->
            val preview = pageTexts.getOrNull(idx).orEmpty()
                .lineSequence().firstOrNull { it.isNotBlank() }?.take(42)
            val label = if (!preview.isNullOrBlank()) "Pág. ${idx + 1} · $preview" else "Página ${idx + 1}"
            TocEntry(title = label, targetIndex = idx, level = 0)
        }

        val chapters = (0 until pageCount).map { idx ->
            val rawText = pageTexts.getOrNull(idx).orEmpty().trim()
            val blocks = if (rawText.isNotBlank()) {
                val paraSplit = rawText.split(BLANK_LINE_SPLIT)
                    .map { it.trim().replace(WHITESPACE_REGEX, " ") }
                    .filter { it.isNotBlank() }
                val lines = if (paraSplit.size <= 1 && rawText.contains('\n')) {
                    rawText.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toList()
                        .chunked(4)
                        .map { it.joinToString(" ") }
                } else {
                    paraSplit
                }
                buildList {
                    add(ParsedBlock(0, BlockType.HEADING_2, "Página ${idx + 1}"))
                    lines.forEachIndexed { i, text ->
                        val type = if (i == 0 && text.length in 3..80 && !text.endsWith(".")) {
                            BlockType.HEADING_1
                        } else {
                            BlockType.PARAGRAPH
                        }
                        add(ParsedBlock(size, type, text))
                    }
                }
            } else {
                listOf(
                    ParsedBlock(0, BlockType.HEADING_2, "Página ${idx + 1}"),
                    ParsedBlock(
                        1,
                        BlockType.QUOTE,
                        "Esta página PDF contiene imagen o gráficos sin texto incrustado. Cambia al modo Formato PDF para visualizarla o activar OCR."
                    )
                )
            }
            ParsedChapter(
                index = idx,
                title = toc.getOrNull(idx)?.title ?: "Página ${idx + 1}",
                cfiPrefix = "pdf(/page/$idx)",
                href = "page_${idx + 1}",
                blocks = blocks,
                wordCount = blocks.sumOf { countWords(it.text) }.coerceAtLeast(1)
            )
        }

        val totalWords = pageTexts.sumOf { countWords(it) }.coerceAtLeast(pageCount * 60)
        return ParsedDocument(
            title = docTitle,
            author = docAuthor,
            format = DocumentFormat.PDF,
            chapters = chapters,
            toc = toc,
            totalWords = totalWords,
            pdfPageCount = pageCount,
            pdfPageTexts = pageTexts,
            nativePageWidthPt = nativeWidthPt,
            nativePageHeightPt = nativeHeightPt,
            detectedFormatLabel = formatLabel
        )
    }

    private suspend fun parseTxtStreaming(file: File): ParsedDocument {
        val title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        val chunkSize = 14
        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        val currentChunk = ArrayList<String>(chunkSize)
        val paraBuilder = StringBuilder(256)
        var lineCounter = 0

        fun flushChunk() {
            if (currentChunk.isEmpty()) return
            val idx = chapters.size
            val blocks = currentChunk.mapIndexed { bIdx, text ->
                val type = if (bIdx == 0 && text.length < 72 && !text.endsWith(".")) {
                    BlockType.HEADING_1
                } else {
                    BlockType.PARAGRAPH
                }
                ParsedBlock(bIdx, type, text)
            }
            val chTitle = blocks.firstOrNull { it.type == BlockType.HEADING_1 }?.text
                ?: "Sección ${idx + 1}"
            chapters.add(
                ParsedChapter(
                    index = idx,
                    title = chTitle,
                    cfiPrefix = "txt(/section/$idx)",
                    href = "section_$idx",
                    blocks = blocks,
                    wordCount = blocks.sumOf { countWords(it.text) }
                )
            )
            toc.add(TocEntry(chTitle, idx, 0))
            currentChunk.clear()
        }

        fun flushParagraph() {
            if (paraBuilder.isEmpty()) return
            val normalized = paraBuilder.toString().trim().replace(WHITESPACE_REGEX, " ")
            paraBuilder.clear()
            if (normalized.isNotEmpty()) {
                currentChunk.add(normalized)
                if (currentChunk.size >= chunkSize) {
                    flushChunk()
                }
            }
        }

        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 16 * 1024).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if ((++lineCounter and 0xFF) == 0) coroutineContext.ensureActive()
                if (line.isBlank()) {
                    flushParagraph()
                } else {
                    if (paraBuilder.isNotEmpty()) paraBuilder.append(' ')
                    paraBuilder.append(line.trim())
                }
            }
        }
        flushParagraph()
        flushChunk()

        if (chapters.isEmpty()) {
            val b = ParsedBlock(0, BlockType.PARAGRAPH, "Archivo de texto vacío.")
            chapters.add(ParsedChapter(0, title, "txt(/section/0)", "0", listOf(b), 4))
            toc.add(TocEntry(title, 0, 0))
        }

        return ParsedDocument(
            title = title,
            author = "Texto plano",
            format = DocumentFormat.TXT,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount }
        )
    }

    private suspend fun parseFb2Streaming(file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        var bookTitle = ""
        var firstName = ""
        var lastName = ""
        val blocks = mutableListOf<ParsedBlock>()

        FileInputStream(file).use { rawStream ->
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(BufferedInputStream(rawStream, 16 * 1024), "UTF-8")

            var inBody = false
            var inTitle = false
            var inAuthor = false
            var eventCounter = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if ((++eventCounter and 0xFF) == 0) coroutineContext.ensureActive()
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.substringAfter(':').lowercase()) {
                            "book-title" -> if (!inBody && bookTitle.isBlank()) {
                                bookTitle = runCatching { parser.nextText().trim() }.getOrDefault("")
                            }
                            "author" -> if (!inBody) inAuthor = true
                            "first-name" -> if (inAuthor && firstName.isBlank()) {
                                firstName = runCatching { parser.nextText().trim() }.getOrDefault("")
                            }
                            "last-name" -> if (inAuthor && lastName.isBlank()) {
                                lastName = runCatching { parser.nextText().trim() }.getOrDefault("")
                            }
                            "body" -> inBody = true
                            "title" -> if (inBody) inTitle = true
                            "p" -> if (inBody) {
                                val txt = runCatching { parser.nextText().trim() }.getOrDefault("")
                                if (txt.isNotEmpty()) {
                                    blocks.add(
                                        ParsedBlock(
                                            blocks.size,
                                            if (inTitle) BlockType.HEADING_1 else BlockType.PARAGRAPH,
                                            txt
                                        )
                                    )
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.substringAfter(':').lowercase()) {
                            "author" -> inAuthor = false
                            "body" -> inBody = false
                            "title" -> inTitle = false
                        }
                    }
                }
                event = parser.next()
            }
        }

        val title = bookTitle.ifBlank { fallbackTitle }
        val author = "$firstName $lastName".trim().ifBlank { "FictionBook 2" }

        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        var currentTitle = title
        var currentList = mutableListOf<ParsedBlock>()

        fun flush() {
            if (currentList.isEmpty()) return
            val idx = chapters.size
            val reindexed = currentList.mapIndexed { i, b -> b.copy(index = i) }
            chapters.add(
                ParsedChapter(
                    index = idx,
                    title = currentTitle,
                    cfiPrefix = "fb2(/section/$idx)",
                    href = "section_$idx",
                    blocks = reindexed,
                    wordCount = reindexed.sumOf { countWords(it.text) }
                )
            )
            toc.add(TocEntry(currentTitle, idx, 0))
            currentList = mutableListOf()
        }

        for (b in blocks) {
            if (b.type == BlockType.HEADING_1 && currentList.isNotEmpty()) {
                flush()
                currentTitle = b.text.take(70)
            } else if (b.type == BlockType.HEADING_1) {
                currentTitle = b.text.take(70)
            }
            currentList.add(b)
        }
        flush()

        if (chapters.isEmpty()) {
            val empty = ParsedBlock(0, BlockType.PARAGRAPH, "No se encontró texto en este archivo FB2.")
            chapters.add(ParsedChapter(0, title, "fb2(/section/0)", "section_0", listOf(empty), 7))
            toc.add(TocEntry(title, 0, 0))
        }

        return ParsedDocument(
            title = title,
            author = author,
            format = DocumentFormat.FB2,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount }
        )
    }

    private fun extractOpfPathStreaming(inputStream: InputStream): String? = runCatching {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(BufferedInputStream(inputStream, 4096), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':').equals("rootfile", true)) {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) return@runCatching fullPath
            }
            event = parser.next()
        }
        null
    }.getOrNull()

    private fun parseOpfStreaming(
        inputStream: InputStream,
        onTitle: (String) -> Unit,
        onAuthor: (String) -> Unit,
        manifest: MutableMap<String, String>,
        spineIds: MutableList<String>
    ) {
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(BufferedInputStream(inputStream, 8192), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfter(':').lowercase()
                    when (localName) {
                        "title" -> onTitle(parser.nextText().trim())
                        "creator" -> onAuthor(parser.nextText().trim())
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                manifest[id] = href
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) spineIds.add(idref)
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun parseCorePropsStreaming(
        inputStream: InputStream,
        onTitle: (String) -> Unit,
        onAuthor: (String) -> Unit
    ) {
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(BufferedInputStream(inputStream, 4096), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.substringAfter(':').lowercase()) {
                        "title" -> onTitle(parser.nextText().trim())
                        "creator" -> onAuthor(parser.nextText().trim())
                    }
                }
                event = parser.next()
            }
        }
    }

    /**
     * Single-pass streaming XHTML/HTML block extractor.
     * Reads directly from a buffered character stream without ever allocating a giant whole-file
     * String or running multi-megabyte regexes over the chapter.
     */
    private fun extractBlocksFromXhtmlStream(inputStream: InputStream): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 8 * 1024)
        val tagBuf = StringBuilder(64)
        val textBuf = StringBuilder(256)
        val fallbackBuf = StringBuilder(256)

        var sawBodyTag = false
        var inBody = false
        var inTag = false
        var inComment = false
        var skipDepth = 0 // Inside <script>, <style>, <svg>, <head>
        var activeBlockTag: String? = null
        var activeBlockType: BlockType = BlockType.PARAGRAPH

        fun flushActiveBlock() {
            if (textBuf.isEmpty()) return
            val cleaned = decodeHtmlEntitiesAndClean(textBuf.toString())
            textBuf.clear()
            if (cleaned.isNotBlank()) {
                blocks.add(ParsedBlock(blocks.size, activeBlockType, cleaned))
            }
        }

        fun handleTag(rawTag: String) {
            val trimmed = rawTag.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("?")) return
            val isClosing = trimmed.startsWith("/")
            val isSelfClosing = trimmed.endsWith("/")
            val nameEnd = trimmed.indexOfFirst { it == ' ' || it == '\t' || it == '\n' || it == '\r' || it == '/' && trimmed.indexOf(it) > 0 }
            val rawName = if (isClosing) {
                trimmed.substring(1).substringBefore(' ').substringBefore('\t').substringBefore('\n')
            } else if (nameEnd > 0) {
                trimmed.substring(0, nameEnd)
            } else {
                trimmed.removeSuffix("/")
            }
            val name = rawName.substringAfter(':').lowercase()

            when (name) {
                "head", "script", "style", "svg" -> {
                    if (!isSelfClosing) {
                        if (isClosing) skipDepth = (skipDepth - 1).coerceAtLeast(0) else skipDepth++
                    }
                    return
                }
                "body" -> {
                    sawBodyTag = true
                    inBody = !isClosing
                    if (isClosing) {
                        flushActiveBlock()
                        activeBlockTag = null
                    }
                    return
                }
            }

            if (skipDepth > 0) return

            if (name == "br") {
                if (activeBlockTag != null) textBuf.append('\n')
                else if (inBody || !sawBodyTag) fallbackBuf.append('\n')
                return
            }

            val blockTypeForTag = when (name) {
                "h1" -> BlockType.HEADING_1
                "h2", "h3" -> BlockType.HEADING_2
                "blockquote" -> BlockType.QUOTE
                "li" -> BlockType.LIST_ITEM
                "p" -> BlockType.PARAGRAPH
                else -> null
            }

            if (blockTypeForTag != null && !isSelfClosing) {
                if (!isClosing) {
                    if (activeBlockTag != null) flushActiveBlock()
                    activeBlockTag = name
                    activeBlockType = blockTypeForTag
                } else if (activeBlockTag == name) {
                    flushActiveBlock()
                    activeBlockTag = null
                }
            }
        }

        val charBuffer = CharArray(4096)
        while (true) {
            val read = reader.read(charBuffer)
            if (read <= 0) break
            var i = 0
            while (i < read) {
                val c = charBuffer[i++]
                if (inComment) {
                    tagBuf.append(c)
                    val len = tagBuf.length
                    if (len >= 3 && tagBuf[len - 3] == '-' && tagBuf[len - 2] == '-' && tagBuf[len - 1] == '>') {
                        inComment = false
                        tagBuf.clear()
                    }
                    continue
                }
                if (inTag) {
                    if (c == '>') {
                        inTag = false
                        handleTag(tagBuf.toString())
                        tagBuf.clear()
                    } else {
                        tagBuf.append(c)
                        if (tagBuf.length == 3 && tagBuf[0] == '!' && tagBuf[1] == '-' && tagBuf[2] == '-') {
                            inTag = false
                            inComment = true
                            tagBuf.clear()
                        }
                    }
                } else {
                    if (c == '<') {
                        inTag = true
                        tagBuf.clear()
                    } else if (skipDepth == 0) {
                        if (activeBlockTag != null) {
                            textBuf.append(c)
                        } else if (inBody || !sawBodyTag) {
                            fallbackBuf.append(c)
                        }
                    }
                }
            }
        }
        flushActiveBlock()

        if (blocks.isEmpty() && fallbackBuf.isNotEmpty()) {
            val plain = decodeHtmlEntitiesAndClean(fallbackBuf.toString())
            if (plain.isNotBlank()) {
                blocks.add(ParsedBlock(0, BlockType.PARAGRAPH, plain))
            }
        }
        return blocks
    }

    private fun decodeHtmlEntitiesAndClean(raw: String): String {
        if (raw.isBlank()) return ""
        val decoded = if ('&' in raw) {
            raw.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
        } else {
            raw
        }
        return decoded
            .replace(HORIZ_WS_REGEX, " ")
            .replace(MULTI_NEWLINE_REGEX, "\n\n")
            .trim()
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').removePrefix("./")
        return if (baseDir.isEmpty()) cleanHref else "$baseDir$cleanHref"
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        var count = 0
        var inWord = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }
}

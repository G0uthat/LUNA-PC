package com.example.engine

import android.content.Context
import android.util.LruCache
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Persistent compact binary disk cache for [ParsedDocument].
 *
 * Stored under `filesDir/parsed_cache/` rather than SQLite Room rows to avoid Android SQLite's
 * 2 MB CursorWindow limit on large books/PDFs while giving ~15-30ms instant document re-opens.
 * Automatically invalidated whenever the source file's path, size, or lastModified changes,
 * and bounded by LRU eviction (max 30 files / 25 MB).
 */
object ParsedDocumentDiskCache {

    private const val MAGIC = 0x4C554D41 // "LUMA"
    private const val CACHE_VERSION = 1
    private const val MAX_CACHE_FILES = 250

    private const val ELEM_SHAPE = 1
    private const val ELEM_PICTURE = 2
    private const val ELEM_TABLE = 3

    // Ultra-fast RAM hot-cache for the most recently opened books (<1 ms instant reopen)
    private val hotMemoryCache = LruCache<String, ParsedDocument>(4)

    init {
        MemoryPressureManager.registerTrimmer("parsed_doc_hot_cache") { level ->
            synchronized(hotMemoryCache) {
                MemoryPressureManager.trimLruCache(hotMemoryCache, level)
            }
        }
    }

    fun clearMemoryCache() {
        synchronized(hotMemoryCache) {
            hotMemoryCache.evictAll()
        }
    }

    private fun memoryKey(sourceFile: File, format: DocumentFormat): String =
        "${sourceFile.absolutePath}|${sourceFile.length()}|${sourceFile.lastModified()}|${format.name}"

    fun read(context: Context, sourceFile: File, format: DocumentFormat): ParsedDocument? {
        val memKey = memoryKey(sourceFile, format)
        synchronized(hotMemoryCache) {
            hotMemoryCache.get(memKey)?.let { return it }
        }
        val cacheFile = cacheFileFor(context, sourceFile, format)
        if (!cacheFile.exists() || cacheFile.length() < 16L) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(cacheFile), 16 * 1024)).use { input ->
                val magic = input.readInt()
                val version = input.readInt()
                if (magic != MAGIC || version != CACHE_VERSION) {
                    cacheFile.delete()
                    return null
                }

                val title = input.readStringUtf()
                val author = input.readStringUtf()
                val fmtOrd = input.readInt()
                val parsedFormat = DocumentFormat.entries.getOrElse(fmtOrd) { format }
                val totalWords = input.readInt()
                val pdfPageCount = input.readInt()
                val nativeWidthPt = input.readFloat()
                val nativeHeightPt = input.readFloat()
                val detectedFormatLabel = input.readStringUtf()

                val pdfTextsSize = input.readInt()
                val pdfPageTexts = ArrayList<String>(pdfTextsSize)
                repeat(pdfTextsSize) { pdfPageTexts.add(input.readStringUtf()) }

                val chaptersSize = input.readInt()
                val chapters = ArrayList<ParsedChapter>(chaptersSize)
                repeat(chaptersSize) {
                    val chIndex = input.readInt()
                    val chTitle = input.readStringUtf()
                    val cfiPrefix = input.readStringUtf()
                    val href = input.readStringUtf()
                    val wordCount = input.readInt()
                    val blocks = readBlocks(input)
                    chapters.add(
                        ParsedChapter(
                            index = chIndex,
                            title = chTitle,
                            cfiPrefix = cfiPrefix,
                            href = href,
                            blocks = blocks,
                            wordCount = wordCount
                        )
                    )
                }

                val tocSize = input.readInt()
                val toc = ArrayList<TocEntry>(tocSize)
                repeat(tocSize) {
                    toc.add(
                        TocEntry(
                            title = input.readStringUtf(),
                            targetIndex = input.readInt(),
                            level = input.readInt()
                        )
                    )
                }

                val slidesSize = input.readInt()
                val slides = ArrayList<ParsedSlide>(slidesSize)
                var missingSlideAsset = false
                repeat(slidesSize) {
                    val sIdx = input.readInt()
                    val sTitle = input.readStringUtf()
                    val sSubtitle = input.readStringUtf()
                    val sNotes = input.readStringUtf()
                    val imgCount = input.readInt()
                    val imgPaths = ArrayList<String>(imgCount)
                    repeat(imgCount) {
                        val p = input.readStringUtf()
                        if (p.isNotBlank() && !File(p).exists()) missingSlideAsset = true
                        imgPaths.add(p)
                    }
                    val bgColor = input.readLong()
                    val bgGradEnd = input.readNullableLong()
                    val bgImgPath = input.readNullableString()
                    if (!bgImgPath.isNullOrBlank() && !File(bgImgPath).exists()) missingSlideAsset = true
                    val bodyBlocks = readBlocks(input)
                    val elemCount = input.readInt()
                    val elements = ArrayList<SlideElement>(elemCount)
                    repeat(elemCount) {
                        when (input.readByte().toInt()) {
                            ELEM_SHAPE -> {
                                val left = input.readFloat()
                                val top = input.readFloat()
                                val width = input.readFloat()
                                val height = input.readFloat()
                                val rot = input.readFloat()
                                val geom = SlideShapeGeometry.entries.getOrElse(input.readInt()) { SlideShapeGeometry.RECT }
                                val fill = input.readNullableLong()
                                val gradEnd = input.readNullableLong()
                                val border = input.readNullableLong()
                                val borderW = input.readFloat()
                                val vAlign = SlideVerticalAlign.entries.getOrElse(input.readInt()) { SlideVerticalAlign.TOP }
                                val paraCount = input.readInt()
                                val paragraphs = ArrayList<SlideParagraph>(paraCount)
                                repeat(paraCount) {
                                    val align = SlideTextAlign.entries.getOrElse(input.readInt()) { SlideTextAlign.LEFT }
                                    val bullet = input.readStringUtf()
                                    val level = input.readInt()
                                    val runCount = input.readInt()
                                    val runs = ArrayList<SlideTextRun>(runCount)
                                    repeat(runCount) {
                                        runs.add(
                                            SlideTextRun(
                                                text = input.readStringUtf(),
                                                fontSizePt = input.readFloat(),
                                                isBold = input.readBoolean(),
                                                isItalic = input.readBoolean(),
                                                isUnderline = input.readBoolean(),
                                                colorArgb = input.readNullableLong()
                                            )
                                        )
                                    }
                                    paragraphs.add(SlideParagraph(runs, align, bullet, level))
                                }
                                elements.add(
                                    SlideElement.ShapeBox(
                                        left = left,
                                        top = top,
                                        width = width,
                                        height = height,
                                        rotationDeg = rot,
                                        geometry = geom,
                                        fillColorArgb = fill,
                                        gradientEndColorArgb = gradEnd,
                                        borderColorArgb = border,
                                        borderWidthPt = borderW,
                                        verticalAlign = vAlign,
                                        paragraphs = paragraphs
                                    )
                                )
                            }

                            ELEM_PICTURE -> {
                                val left = input.readFloat()
                                val top = input.readFloat()
                                val width = input.readFloat()
                                val height = input.readFloat()
                                val rot = input.readFloat()
                                val imgPath = input.readStringUtf()
                                if (imgPath.isNotBlank() && !File(imgPath).exists()) missingSlideAsset = true
                                elements.add(SlideElement.Picture(left, top, width, height, rot, imgPath))
                            }

                            ELEM_TABLE -> {
                                val left = input.readFloat()
                                val top = input.readFloat()
                                val width = input.readFloat()
                                val height = input.readFloat()
                                val wCount = input.readInt()
                                val weights = ArrayList<Float>(wCount)
                                repeat(wCount) { weights.add(input.readFloat()) }
                                val rowCount = input.readInt()
                                val rows = ArrayList<List<SlideTableCell>>(rowCount)
                                repeat(rowCount) {
                                    val cellCount = input.readInt()
                                    val cells = ArrayList<SlideTableCell>(cellCount)
                                    repeat(cellCount) {
                                        cells.add(
                                            SlideTableCell(
                                                text = input.readStringUtf(),
                                                fillColorArgb = input.readNullableLong(),
                                                textColorArgb = input.readNullableLong(),
                                                isBold = input.readBoolean(),
                                                fontSizePt = input.readFloat(),
                                                align = SlideTextAlign.entries.getOrElse(input.readInt()) { SlideTextAlign.LEFT }
                                            )
                                        )
                                    }
                                    rows.add(cells)
                                }
                                elements.add(SlideElement.Table(left, top, width, height, weights, rows))
                            }
                        }
                    }
                    slides.add(
                        ParsedSlide(
                            index = sIdx,
                            title = sTitle,
                            subtitle = sSubtitle,
                            bodyBlocks = bodyBlocks,
                            notes = sNotes,
                            imagePaths = imgPaths,
                            backgroundColorArgb = bgColor,
                            backgroundGradientEndArgb = bgGradEnd,
                            backgroundImagePath = bgImgPath,
                            elements = elements
                        )
                    )
                }

                // If Android purged extracted PPTX media in cacheDir, fall back to re-parsing
                if (missingSlideAsset) {
                    cacheFile.delete()
                    return null
                }

                // Touch lastModified for LRU retention
                cacheFile.setLastModified(System.currentTimeMillis())

                val parsedDoc = ParsedDocument(
                    title = title,
                    author = author,
                    format = parsedFormat,
                    chapters = chapters,
                    toc = toc,
                    totalWords = totalWords,
                    pdfPageCount = pdfPageCount,
                    pdfPageTexts = pdfPageTexts,
                    slides = slides,
                    nativePageWidthPt = nativeWidthPt,
                    nativePageHeightPt = nativeHeightPt,
                    detectedFormatLabel = detectedFormatLabel
                )
                synchronized(hotMemoryCache) {
                    hotMemoryCache.put(memKey, parsedDoc)
                }
                parsedDoc
            }
        }.onFailure {
            runCatching { cacheFile.delete() }
        }.getOrNull()
    }

    fun write(context: Context, sourceFile: File, format: DocumentFormat, doc: ParsedDocument) {
        synchronized(hotMemoryCache) {
            hotMemoryCache.put(memoryKey(sourceFile, format), doc)
        }
        val dir = cacheDir(context)
        val target = cacheFileFor(context, sourceFile, format)
        val temp = File(dir, "${target.name}.tmp")
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temp), 16 * 1024)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(CACHE_VERSION)
                out.writeStringUtf(doc.title)
                out.writeStringUtf(doc.author)
                out.writeInt(doc.format.ordinal)
                out.writeInt(doc.totalWords)
                out.writeInt(doc.pdfPageCount)
                out.writeFloat(doc.nativePageWidthPt)
                out.writeFloat(doc.nativePageHeightPt)
                out.writeStringUtf(doc.detectedFormatLabel)

                out.writeInt(doc.pdfPageTexts.size)
                for (t in doc.pdfPageTexts) out.writeStringUtf(t)

                out.writeInt(doc.chapters.size)
                for (ch in doc.chapters) {
                    out.writeInt(ch.index)
                    out.writeStringUtf(ch.title)
                    out.writeStringUtf(ch.cfiPrefix)
                    out.writeStringUtf(ch.href)
                    out.writeInt(ch.wordCount)
                    writeBlocks(out, ch.blocks)
                }

                out.writeInt(doc.toc.size)
                for (entry in doc.toc) {
                    out.writeStringUtf(entry.title)
                    out.writeInt(entry.targetIndex)
                    out.writeInt(entry.level)
                }

                out.writeInt(doc.slides.size)
                for (slide in doc.slides) {
                    out.writeInt(slide.index)
                    out.writeStringUtf(slide.title)
                    out.writeStringUtf(slide.subtitle)
                    out.writeStringUtf(slide.notes)
                    out.writeInt(slide.imagePaths.size)
                    for (p in slide.imagePaths) out.writeStringUtf(p)
                    out.writeLong(slide.backgroundColorArgb)
                    out.writeNullableLong(slide.backgroundGradientEndArgb)
                    out.writeNullableString(slide.backgroundImagePath)
                    writeBlocks(out, slide.bodyBlocks)
                    out.writeInt(slide.elements.size)
                    for (el in slide.elements) {
                        when (el) {
                            is SlideElement.ShapeBox -> {
                                out.writeByte(ELEM_SHAPE)
                                out.writeFloat(el.left)
                                out.writeFloat(el.top)
                                out.writeFloat(el.width)
                                out.writeFloat(el.height)
                                out.writeFloat(el.rotationDeg)
                                out.writeInt(el.geometry.ordinal)
                                out.writeNullableLong(el.fillColorArgb)
                                out.writeNullableLong(el.gradientEndColorArgb)
                                out.writeNullableLong(el.borderColorArgb)
                                out.writeFloat(el.borderWidthPt)
                                out.writeInt(el.verticalAlign.ordinal)
                                out.writeInt(el.paragraphs.size)
                                for (p in el.paragraphs) {
                                    out.writeInt(p.align.ordinal)
                                    out.writeStringUtf(p.bulletPrefix)
                                    out.writeInt(p.level)
                                    out.writeInt(p.runs.size)
                                    for (r in p.runs) {
                                        out.writeStringUtf(r.text)
                                        out.writeFloat(r.fontSizePt)
                                        out.writeBoolean(r.isBold)
                                        out.writeBoolean(r.isItalic)
                                        out.writeBoolean(r.isUnderline)
                                        out.writeNullableLong(r.colorArgb)
                                    }
                                }
                            }

                            is SlideElement.Picture -> {
                                out.writeByte(ELEM_PICTURE)
                                out.writeFloat(el.left)
                                out.writeFloat(el.top)
                                out.writeFloat(el.width)
                                out.writeFloat(el.height)
                                out.writeFloat(el.rotationDeg)
                                out.writeStringUtf(el.imagePath)
                            }

                            is SlideElement.Table -> {
                                out.writeByte(ELEM_TABLE)
                                out.writeFloat(el.left)
                                out.writeFloat(el.top)
                                out.writeFloat(el.width)
                                out.writeFloat(el.height)
                                out.writeInt(el.colWeights.size)
                                for (w in el.colWeights) out.writeFloat(w)
                                out.writeInt(el.rows.size)
                                for (row in el.rows) {
                                    out.writeInt(row.size)
                                    for (cell in row) {
                                        out.writeStringUtf(cell.text)
                                        out.writeNullableLong(cell.fillColorArgb)
                                        out.writeNullableLong(cell.textColorArgb)
                                        out.writeBoolean(cell.isBold)
                                        out.writeFloat(cell.fontSizePt)
                                        out.writeInt(cell.align.ordinal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
            }
            evictIfNeeded(dir)
            LumaDiskCacheManager.enforceGlobalBudget(context)
        }.onFailure {
            runCatching { temp.delete() }
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "parsed_cache").apply { if (!exists()) mkdirs() }

    private fun cacheFileFor(context: Context, sourceFile: File, format: DocumentFormat): File {
        val rawKey = "${sourceFile.absolutePath}|${sourceFile.length()}|${sourceFile.lastModified()}|${format.name}|$CACHE_VERSION"
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
        val hex = buildString(32) {
            for (i in 0 until 16) {
                append(((digest[i].toInt() and 0xFF) + 0x100).toString(16).substring(1))
            }
        }
        return File(cacheDir(context), "$hex.bin")
    }

    private fun evictIfNeeded(dir: File) {
        val maxBytes = LumaDiskCacheManager.parsedCacheMaxBytes()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") } ?: return
        var totalBytes = files.sumOf { it.length() }
        if (files.size <= MAX_CACHE_FILES && totalBytes <= maxBytes) return
        val sortedOldestFirst = files.sortedBy { it.lastModified() }
        var remainingCount = files.size
        for (f in sortedOldestFirst) {
            if (remainingCount <= MAX_CACHE_FILES && totalBytes <= maxBytes) break
            val len = f.length()
            if (f.delete()) {
                totalBytes -= len
                remainingCount--
            }
        }
    }

    private fun writeBlocks(out: DataOutputStream, blocks: List<ParsedBlock>) {
        out.writeInt(blocks.size)
        for (b in blocks) {
            out.writeInt(b.index)
            out.writeByte(b.type.ordinal)
            out.writeStringUtf(b.text)
        }
    }

    private fun readBlocks(input: DataInputStream): List<ParsedBlock> {
        val count = input.readInt()
        val list = ArrayList<ParsedBlock>(count)
        repeat(count) {
            list.add(
                ParsedBlock(
                    index = input.readInt(),
                    type = BlockType.entries.getOrElse(input.readByte().toInt()) { BlockType.PARAGRAPH },
                    text = input.readStringUtf()
                )
            )
        }
        return list
    }

    // Length-prefixed UTF-8 strings (avoids DataOutputStream.writeUTF's 64 KB limit!)
    private fun DataOutputStream.writeStringUtf(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        if (bytes.isNotEmpty()) write(bytes)
    }

    private fun DataInputStream.readStringUtf(): String {
        val len = readInt()
        if (len <= 0) return ""
        val buf = ByteArray(len)
        readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeLong(value)
        }
    }

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableString(value: String?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeStringUtf(value)
        }
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readStringUtf() else null
}

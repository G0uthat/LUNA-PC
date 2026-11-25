package com.example.engine

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockType
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedChapter
import com.example.data.model.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

@Immutable
data class FluidSheetBlock(
    val block: ParsedBlock,
    val charOffsetInBlock: Int = 0
)

@Immutable
data class FluidSheet(
    val globalIndex: Int,
    val chapterIndex: Int,
    val sheetInChapter: Int,
    val chapterTitle: String,
    val cfiPrefix: String,
    val locationKey: String,
    val blocks: List<FluidSheetBlock>
)

@Immutable
data class TypographyMetricsCalibration(
    val avgCharWidthRatio: Float = 0.55f,
    val lineHeightScale: Float = 1.06f
)

private data class PaginationCacheKey(
    val chaptersIdentityHash: Int,
    val chapterCount: Int,
    val totalWords: Int,
    val fontSizeBucket: Int,
    val lineHeightBucket: Int,
    val marginDp: Int,
    val fontKey: String,
    val widthBucket: Int,
    val heightBucket: Int
)

/**
 * Splits reflowable book chapters (EPUB, DOCX, TXT, FB2) into discrete physical book sheets ("hojas")
 * based on active typography and viewport size:
 * - Runs on [Dispatchers.Default] and is fully cancellable via [ensureActive].
 * - Incremental: paginates the active chapter FIRST for near-instant display, then finishes
 *   the rest of the book in the background.
 * - Cached in-memory and invalidated ONLY when chapters, typography, or viewport dimensions change.
 * - Calibrates average character width & line height ONCE per font via a cached [TextMeasurer]
 *   instead of running full TextLayout per paragraph.
 */
object FluidPaginator {

    private const val CALIBRATION_SAMPLE =
        "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ áéíóúñ 0123456789"

    private val paginationCache = LruCache<PaginationCacheKey, List<FluidSheet>>(8)
    private val metricsCache = LruCache<String, TypographyMetricsCalibration>(16)

    /**
     * Calibrates average character width ratio once per (font, size, lineHeight) using Compose's
     * cached [TextMeasurer]. Must be called on the UI thread / Composition scope where [TextMeasurer]
     * is remembered; returns a lightweight immutable value safe to pass onto [Dispatchers.Default].
     */
    fun calibrateMetrics(
        textMeasurer: TextMeasurer,
        fontFamily: FontFamily,
        fontKey: String,
        prefs: ReaderPreferences,
        density: Density
    ): TypographyMetricsCalibration {
        val key = "$fontKey:${(prefs.fontSizeSp * 2).toInt()}:${(prefs.lineHeightMultiplier * 20).toInt()}"
        metricsCache.get(key)?.let { return it }

        val calibration = runCatching {
            val style = TextStyle(
                fontFamily = fontFamily,
                fontSize = prefs.fontSizeSp.sp,
                lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp
            )
            val result = textMeasurer.measure(text = CALIBRATION_SAMPLE, style = style, maxLines = 1)
            val measuredWidthDp = with(density) { result.size.width.toDp().value }
            val measuredHeightDp = with(density) { result.size.height.toDp().value }
            val charRatio = (measuredWidthDp / (CALIBRATION_SAMPLE.length * prefs.fontSizeSp))
                .coerceIn(0.42f, 0.72f)
            val expectedLineDp = (prefs.fontSizeSp * prefs.lineHeightMultiplier).coerceAtLeast(1f)
            val lineScale = (measuredHeightDp / expectedLineDp).coerceIn(0.98f, 1.24f)
            TypographyMetricsCalibration(avgCharWidthRatio = charRatio, lineHeightScale = lineScale)
        }.getOrElse {
            TypographyMetricsCalibration()
        }
        metricsCache.put(key, calibration)
        return calibration
    }

    fun getCachedSheets(
        chapters: List<ParsedChapter>,
        prefs: ReaderPreferences,
        viewportWidthDp: Float,
        viewportHeightDp: Float
    ): List<FluidSheet>? {
        if (chapters.isEmpty()) return emptyList()
        val key = buildCacheKey(chapters, prefs, viewportWidthDp, viewportHeightDp)
        return synchronized(paginationCache) { paginationCache.get(key) }
    }

    /**
     * Incremental cancellable pagination on [Dispatchers.Default]:
     * 1. Returns cached sheets immediately if key matches.
     * 2. Otherwise paginates [priorityChapterIndex] (+ immediate neighbor) first and invokes
     *    [onPartialReady] so the UI renders instantly without blocking on a 500-chapter book.
     * 3. Paginates all remaining chapters in the background, caches the full book, and returns it.
     */
    suspend fun paginateIncremental(
        chapters: List<ParsedChapter>,
        prefs: ReaderPreferences,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        priorityChapterIndex: Int,
        calibration: TypographyMetricsCalibration = TypographyMetricsCalibration(),
        onPartialReady: (partialSheets: List<FluidSheet>) -> Unit
    ): List<FluidSheet> = withContext(Dispatchers.Default) {
        if (chapters.isEmpty()) return@withContext emptyList()

        val key = buildCacheKey(chapters, prefs, viewportWidthDp, viewportHeightDp)
        synchronized(paginationCache) { paginationCache.get(key) }?.let { cached ->
            return@withContext cached
        }

        val usableWidthDp = (viewportWidthDp.coerceAtMost(760f) - prefs.horizontalMarginDp * 2f)
            .coerceAtLeast(180f)
        val usableHeightDp = (viewportHeightDp - 132f).coerceAtLeast(280f)

        val safePriority = priorityChapterIndex.coerceIn(0, chapters.lastIndex)
        val perChapterSheets = arrayOfNulls<List<FluidSheet>>(chapters.size)

        // Phase 1: Paginate active chapter first (and next chapter if book is multi-chapter)
        currentCoroutineContext().ensureActive()
        val prioritySheets = paginateSingleChapter(
            chapter = chapters[safePriority],
            prefs = prefs,
            usableWidthDp = usableWidthDp,
            usableHeightDp = usableHeightDp,
            calibration = calibration
        )
        perChapterSheets[safePriority] = prioritySheets

        if (chapters.size > 1) {
            val preview = reindexSheets(prioritySheets)
            onPartialReady(preview)
        }

        // Phase 2: Paginate remaining chapters incrementally, cooperatively cancellable
        for (i in chapters.indices) {
            if (perChapterSheets[i] != null) continue
            currentCoroutineContext().ensureActive()
            perChapterSheets[i] = paginateSingleChapter(
                chapter = chapters[i],
                prefs = prefs,
                usableWidthDp = usableWidthDp,
                usableHeightDp = usableHeightDp,
                calibration = calibration
            )
        }

        currentCoroutineContext().ensureActive()
        val totalCount = perChapterSheets.sumOf { it?.size ?: 0 }
        val allSheets = ArrayList<FluidSheet>(totalCount)
        var globalIdx = 0
        for (list in perChapterSheets) {
            if (list == null) continue
            for (sheet in list) {
                allSheets.add(
                    if (sheet.globalIndex == globalIdx) sheet else sheet.copy(globalIndex = globalIdx)
                )
                globalIdx++
            }
        }

        synchronized(paginationCache) {
            paginationCache.put(key, allSheets)
        }
        allSheets
    }

    /**
     * Synchronous pagination helper (used by exporters or lightweight callers).
     */
    fun paginate(
        chapters: List<ParsedChapter>,
        prefs: ReaderPreferences,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        calibration: TypographyMetricsCalibration = TypographyMetricsCalibration()
    ): List<FluidSheet> {
        if (chapters.isEmpty()) return emptyList()
        val key = buildCacheKey(chapters, prefs, viewportWidthDp, viewportHeightDp)
        synchronized(paginationCache) { paginationCache.get(key) }?.let { return it }

        val usableWidthDp = (viewportWidthDp.coerceAtMost(760f) - prefs.horizontalMarginDp * 2f)
            .coerceAtLeast(180f)
        val usableHeightDp = (viewportHeightDp - 132f).coerceAtLeast(280f)

        val sheets = mutableListOf<FluidSheet>()
        var globalIdx = 0
        for (chapter in chapters) {
            val chapterSheets = paginateSingleChapter(chapter, prefs, usableWidthDp, usableHeightDp, calibration)
            for (s in chapterSheets) {
                sheets.add(s.copy(globalIndex = globalIdx++))
            }
        }
        synchronized(paginationCache) {
            paginationCache.put(key, sheets)
        }
        return sheets
    }

    private fun paginateSingleChapter(
        chapter: ParsedChapter,
        prefs: ReaderPreferences,
        usableWidthDp: Float,
        usableHeightDp: Float,
        calibration: TypographyMetricsCalibration
    ): List<FluidSheet> {
        if (chapter.blocks.isEmpty()) {
            return listOf(
                FluidSheet(
                    globalIndex = 0,
                    chapterIndex = chapter.index,
                    sheetInChapter = 0,
                    chapterTitle = chapter.title,
                    cfiPrefix = chapter.cfiPrefix,
                    locationKey = "${chapter.cfiPrefix}#sheet-0",
                    blocks = emptyList()
                )
            )
        }

        val sheets = mutableListOf<FluidSheet>()
        var sheetInChapter = 0
        var currentBlocks = mutableListOf<FluidSheetBlock>()
        var currentHeightDp = 0f

        fun flushSheet() {
            if (currentBlocks.isEmpty()) return
            sheets.add(
                FluidSheet(
                    globalIndex = sheetInChapter,
                    chapterIndex = chapter.index,
                    sheetInChapter = sheetInChapter,
                    chapterTitle = chapter.title,
                    cfiPrefix = chapter.cfiPrefix,
                    locationKey = "${chapter.cfiPrefix}#sheet-$sheetInChapter",
                    blocks = currentBlocks.toList()
                )
            )
            sheetInChapter++
            currentBlocks = mutableListOf()
            currentHeightDp = 0f
        }

        for (block in chapter.blocks) {
            val chunks = splitBlockIfNeeded(block, prefs, usableWidthDp, usableHeightDp, calibration)
            for (chunk in chunks) {
                val chunkHeight = estimateBlockHeightDp(chunk.block, prefs, usableWidthDp, calibration)
                val gap = if (currentBlocks.isEmpty()) 0f else 14f

                if (currentBlocks.isNotEmpty() && currentHeightDp + gap + chunkHeight > usableHeightDp) {
                    flushSheet()
                }
                currentBlocks.add(chunk)
                currentHeightDp += (if (currentBlocks.size == 1) 0f else 14f) + chunkHeight
            }
        }
        flushSheet()
        return sheets
    }

    private fun reindexSheets(sheets: List<FluidSheet>): List<FluidSheet> =
        sheets.mapIndexed { idx, s -> if (s.globalIndex == idx) s else s.copy(globalIndex = idx) }

    private fun buildCacheKey(
        chapters: List<ParsedChapter>,
        prefs: ReaderPreferences,
        viewportWidthDp: Float,
        viewportHeightDp: Float
    ): PaginationCacheKey {
        val firstCfi = chapters.firstOrNull()?.cfiPrefix.orEmpty()
        val lastCfi = chapters.lastOrNull()?.cfiPrefix.orEmpty()
        val totalWords = chapters.sumOf { it.wordCount }
        val sig = 31 * (31 * firstCfi.hashCode() + lastCfi.hashCode()) + chapters.sumOf { it.blocks.size }
        val fontKey = if (prefs.useSystemFont) "SYS" else prefs.font.name
        return PaginationCacheKey(
            chaptersIdentityHash = sig,
            chapterCount = chapters.size,
            totalWords = totalWords,
            fontSizeBucket = (prefs.fontSizeSp * 2f).toInt(),
            lineHeightBucket = (prefs.lineHeightMultiplier * 20f).toInt(),
            marginDp = prefs.horizontalMarginDp.toInt(),
            fontKey = fontKey,
            widthBucket = (viewportWidthDp / 24f).toInt(),
            heightBucket = (viewportHeightDp / 32f).toInt()
        )
    }

    private fun fontSizeFor(type: BlockType, baseSp: Float): Float = when (type) {
        BlockType.HEADING_1 -> baseSp * 1.42f
        BlockType.HEADING_2 -> baseSp * 1.18f
        BlockType.QUOTE -> baseSp * 0.96f
        else -> baseSp
    }

    private fun estimateBlockHeightDp(
        block: ParsedBlock,
        prefs: ReaderPreferences,
        usableWidthDp: Float,
        calibration: TypographyMetricsCalibration
    ): Float {
        val effectiveWidth = if (block.type == BlockType.QUOTE) {
            (usableWidthDp - 44f).coerceAtLeast(140f)
        } else {
            usableWidthDp
        }
        val fontSize = fontSizeFor(block.type, prefs.fontSizeSp)
        val avgCharWidth = fontSize * calibration.avgCharWidthRatio
        val charsPerLine = max(14, (effectiveWidth / avgCharWidth).toInt())

        val lineCount = countWrappedLines(block.text, charsPerLine)
        val lineHeightDp = fontSize * prefs.lineHeightMultiplier * calibration.lineHeightScale
        val extraPadding = when (block.type) {
            BlockType.HEADING_1 -> 12f
            BlockType.HEADING_2 -> 8f
            BlockType.QUOTE -> 28f
            else -> 2f
        }
        return lineCount * lineHeightDp + extraPadding
    }

    private fun splitBlockIfNeeded(
        block: ParsedBlock,
        prefs: ReaderPreferences,
        usableWidthDp: Float,
        usableHeightDp: Float,
        calibration: TypographyMetricsCalibration
    ): List<FluidSheetBlock> {
        val fullHeight = estimateBlockHeightDp(block, prefs, usableWidthDp, calibration)
        if (fullHeight <= usableHeightDp * 0.92f || block.text.length < 120) {
            return listOf(FluidSheetBlock(block = block, charOffsetInBlock = 0))
        }

        val fontSize = fontSizeFor(block.type, prefs.fontSizeSp)
        val charsPerLine = max(14, (usableWidthDp / (fontSize * calibration.avgCharWidthRatio)).toInt())
        val lineHeightDp = fontSize * prefs.lineHeightMultiplier * calibration.lineHeightScale
        val maxLinesPerChunk = max(4, ((usableHeightDp * 0.84f) / lineHeightDp).toInt())
        val approxMaxChars = max(100, maxLinesPerChunk * charsPerLine)

        val result = mutableListOf<FluidSheetBlock>()
        val text = block.text
        var cursor = 0
        while (cursor < text.length) {
            val remaining = text.length - cursor
            if (remaining <= approxMaxChars) {
                result.add(
                    FluidSheetBlock(
                        block = block.copy(text = text.substring(cursor)),
                        charOffsetInBlock = cursor
                    )
                )
                break
            }
            var splitAt = (cursor + approxMaxChars).coerceAtMost(text.length)
            val lastSpace = text.lastIndexOf(' ', splitAt)
            if (lastSpace > cursor + approxMaxChars / 2) {
                splitAt = lastSpace
            }
            val piece = text.substring(cursor, splitAt).trimEnd()
            if (piece.isNotEmpty()) {
                result.add(
                    FluidSheetBlock(
                        block = block.copy(text = piece),
                        charOffsetInBlock = cursor
                    )
                )
            }
            cursor = splitAt
            while (cursor < text.length && text[cursor] == ' ') cursor++
        }
        return result.ifEmpty { listOf(FluidSheetBlock(block = block, charOffsetInBlock = 0)) }
    }

    private fun countWrappedLines(text: String, charsPerLine: Int): Int {
        if (text.isBlank()) return 1
        var lines = 0
        var lineStart = 0
        val len = text.length
        while (lineStart <= len) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl < 0) len else nl
            val lineLen = lineEnd - lineStart
            lines += if (lineLen <= 0) {
                1
            } else {
                ceil(lineLen.toFloat() / charsPerLine.toFloat()).toInt().coerceAtLeast(1)
            }
            if (nl < 0) break
            lineStart = nl + 1
        }
        return lines.coerceAtLeast(1)
    }
}

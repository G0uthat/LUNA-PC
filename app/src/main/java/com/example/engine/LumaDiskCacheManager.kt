package com.example.engine

import android.content.Context
import com.example.data.local.FolioDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified disk-cache manager for Luma (Phase 6 — Size & Speed Budget).
 *
 * Centralizes all regenerable acceleration caches under a single user-configurable disk budget
 * (default 500 MB):
 * 1. `filesDir/parsed_cache/` — compact binary parsed documents (instant re-open)
 * 2. `filesDir/pdf_page_cache/` — pre-rendered PDF pages (instant page flips)
 * 3. Room `ocr_page_cache` — persisted ML Kit OCR & translation results
 * 4. Regenerable thumbnails (suffix `_thumb.webp` inside `filesDir/covers/`), temporary exports (`filesDir/exports/`),
 *    and app `cacheDir`.
 *
 * SAFETY GUARANTEE: NEVER deletes user-imported books (`filesDir/books/`), user Study Notebooks
 * (`filesDir/notebooks/`), or primary book covers.
 */
object LumaDiskCacheManager {

    const val DEFAULT_LIMIT_MB = 500
    const val MIN_LIMIT_MB = 100
    const val MAX_LIMIT_MB = 2048

    val PRESET_LIMITS_MB: List<Int> = listOf(150, 300, 500, 1024, 2048)

    private val configuredLimitBytes = AtomicLong(DEFAULT_LIMIT_MB.toLong() * 1024L * 1024L)

    // Optional hooks registered by UI bitmap caches so clearing cache frees RAM too without circular deps
    @Volatile
    private var onClearRamCachesHook: (() -> Unit)? = null

    fun registerRamCacheClearHook(hook: () -> Unit) {
        onClearRamCachesHook = hook
    }

    fun maxCacheBytes(): Long = configuredLimitBytes.get()

    fun maxCacheMb(): Int = (configuredLimitBytes.get() / (1024L * 1024L)).toInt()

    /**
     * Updates the active global disk cache limit (clamped to 100 MB .. 2048 MB).
     */
    fun setLimitMb(limitMb: Int) {
        val clamped = limitMb.coerceIn(MIN_LIMIT_MB, MAX_LIMIT_MB)
        configuredLimitBytes.set(clamped.toLong() * 1024L * 1024L)
    }

    /**
     * Budget slice reserved for parsed documents (~30% of global budget, min 48 MB).
     */
    fun parsedCacheMaxBytes(): Long =
        (maxCacheBytes() * 30L / 100L).coerceAtLeast(48L * 1024L * 1024L)

    /**
     * Budget slice reserved for rendered PDF pages (~65% of global budget, min 64 MB).
     */
    fun pdfPageCacheMaxBytes(): Long =
        (maxCacheBytes() * 65L / 100L).coerceAtLeast(64L * 1024L * 1024L)

    data class DiskCacheReport(
        val parsedDocsBytes: Long,
        val pdfPagesBytes: Long,
        val ocrCacheBytes: Long,
        val ocrPageCount: Int,
        val thumbnailsAndTempBytes: Long,
        val limitBytes: Long
    ) {
        val totalBytes: Long
            get() = parsedDocsBytes + pdfPagesBytes + ocrCacheBytes + thumbnailsAndTempBytes

        val usageFraction: Float
            get() = if (limitBytes <= 0L) 0f else (totalBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f)
    }

    suspend fun inspectUsage(context: Context): DiskCacheReport = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val parsedBytes = dirSizeBytes(File(appCtx.filesDir, "parsed_cache"))
        val pdfBytes = dirSizeBytes(File(appCtx.filesDir, "pdf_page_cache"))
        val exportsBytes = dirSizeBytes(File(appCtx.filesDir, "exports"))
        val tempCacheBytes = dirSizeBytes(appCtx.cacheDir)
        val thumbBytes = coverThumbnailFiles(appCtx).sumOf { it.length() }

        val (ocrBytes, ocrCount) = runCatching {
            val dao = FolioDatabase.getInstance(appCtx).folioDao()
            dao.getOcrCacheBytes() to dao.getOcrCacheCount()
        }.getOrDefault(0L to 0)

        DiskCacheReport(
            parsedDocsBytes = parsedBytes,
            pdfPagesBytes = pdfBytes,
            ocrCacheBytes = ocrBytes,
            ocrPageCount = ocrCount,
            thumbnailsAndTempBytes = exportsBytes + tempCacheBytes + thumbBytes,
            limitBytes = maxCacheBytes()
        )
    }

    /**
     * Enforces the configured global cache budget via oldest-first LRU eviction across
     * `pdf_page_cache`, `parsed_cache`, and `exports`. Safe to call from IO threads.
     */
    fun enforceGlobalBudget(context: Context): Long {
        val appCtx = context.applicationContext
        val limit = maxCacheBytes()
        val candidates = ArrayList<File>(256)

        collectEvictableFiles(File(appCtx.filesDir, "pdf_page_cache"), candidates)
        collectEvictableFiles(File(appCtx.filesDir, "parsed_cache"), candidates)
        collectEvictableFiles(File(appCtx.filesDir, "exports"), candidates)

        var totalBytes = candidates.sumOf { it.length() }
        if (totalBytes <= limit) return 0L

        // Evict oldest-accessed entries first until comfortably at 90% of budget
        val targetBytes = (limit * 90L) / 100L
        candidates.sortBy { it.lastModified() }
        var freed = 0L
        for (file in candidates) {
            if (totalBytes <= targetBytes) break
            val len = file.length()
            if (file.delete()) {
                totalBytes -= len
                freed += len
            }
        }
        return freed
    }

    /**
     * Clears ALL regenerable acceleration caches (parsed documents, PDF page renders, Room OCR cache,
     * precomputed grid thumbnails, temporary exports, app `cacheDir`, and RAM caches) without ever
     * touching user books, Study Notebooks, or primary book covers.
     *
     * @return Total bytes reclaimed from disk/database.
     */
    suspend fun clearAllCaches(context: Context): Long = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        var freed = 0L

        freed += deleteDirContents(File(appCtx.filesDir, "parsed_cache"))
        freed += deleteDirContents(File(appCtx.filesDir, "pdf_page_cache"))
        freed += deleteDirContents(File(appCtx.filesDir, "exports"))
        freed += deleteDirContents(appCtx.cacheDir)

        // Delete only regenerable `_thumb.webp` siblings; primary covers stay untouched!
        for (thumb in coverThumbnailFiles(appCtx)) {
            val len = thumb.length()
            if (thumb.delete()) freed += len
        }

        // Clear cached ML Kit OCR pages in Room
        runCatching {
            val dao = FolioDatabase.getInstance(appCtx).folioDao()
            val ocrBytes = dao.getOcrCacheBytes()
            dao.clearAllOcrCache()
            freed += ocrBytes
        }

        // Also clear in-memory hot caches so UI stays consistent
        ParsedDocumentDiskCache.clearMemoryCache()
        runCatching { onClearRamCachesHook?.invoke() }

        freed
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B"
            safe < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", safe / 1024.0)
            safe < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatLimitLabel(mb: Int): String =
        if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"

    private fun coverThumbnailFiles(context: Context): List<File> {
        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) return emptyList()
        return coversDir.listFiles { f ->
            f.isFile && f.name.endsWith(CoverManager.THUMB_SUFFIX)
        }?.toList().orEmpty()
    }

    private fun collectEvictableFiles(dir: File, out: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.isFile) {
                out.add(child)
            } else if (child.isDirectory) {
                collectEvictableFiles(child, out)
            }
        }
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += if (child.isDirectory) dirSizeBytes(child) else child.length()
        }
        return total
    }

    private fun deleteDirContents(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                freed += deleteDirContents(child)
                runCatching { child.delete() }
            } else {
                val len = child.length()
                if (runCatching { child.delete() }.getOrDefault(false)) {
                    freed += len
                }
            }
        }
        return freed
    }
}

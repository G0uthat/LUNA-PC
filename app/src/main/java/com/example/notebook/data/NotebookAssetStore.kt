package com.example.notebook.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.engine.MemoryPressureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * High-speed disk + RAM asset storage for Study Notebooks:
 * - Stores all images & PNGs as real files in `filesDir/notebooks/{notebookId}/assets/`
 *   (NEVER as SQLite blobs); `contentJson` holds strictly relative file names.
 * - Preserves alpha channel cleanly using WebP lossless/high-quality encoding for PNG/transparent assets.
 * - Avoids redundant JPEG re-compression: when importing a JPEG that already fits within our
 *   pixel budget, copies the original bytes verbatim.
 * - Maintains a MemoryPressureManager-aware RAM LruCache so page switches & panning hit RAM in <1 ms.
 */
object NotebookAssetStore {

    private const val MAX_ASSET_LONG_EDGE = 2048
    private const val MAX_PDF_BG_WIDTH = 1600
    private const val THUMBNAIL_WIDTH = 420

    private val bitmapCache: LruCache<String, Bitmap> by lazy {
        val heapKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt().coerceAtLeast(32 * 1024)
        val maxKb = if (MemoryPressureManager.isLowRamDevice) {
            (heapKb / 8).coerceIn(8 * 1024, 18 * 1024)
        } else {
            (heapKb / 5).coerceIn(18 * 1024, 52 * 1024)
        }
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }.also { cache ->
            MemoryPressureManager.registerTrimmer("notebook_asset_bitmap_cache") { level ->
                synchronized(cache) {
                    MemoryPressureManager.trimLruCache(cache, level)
                }
            }
        }
    }

    fun notebookRootDir(context: Context, notebookId: Long): File =
        File(context.filesDir, "notebooks/$notebookId").apply { mkdirs() }

    fun assetsDir(context: Context, notebookId: Long): File =
        File(notebookRootDir(context, notebookId), "assets").apply { mkdirs() }

    fun thumbnailsDir(context: Context, notebookId: Long): File =
        File(notebookRootDir(context, notebookId), "thumbnails").apply { mkdirs() }

    fun resolveAssetFile(context: Context, notebookId: Long, relativeOrAbsolutePath: String): File {
        if (relativeOrAbsolutePath.isBlank()) return File("")
        val direct = File(relativeOrAbsolutePath)
        if (direct.isAbsolute) return direct
        return File(assetsDir(context, notebookId), relativeOrAbsolutePath)
    }

    fun getCachedBitmap(absolutePath: String): Bitmap? {
        if (absolutePath.isBlank()) return null
        return synchronized(bitmapCache) {
            bitmapCache.get(absolutePath)?.takeIf { !it.isRecycled }
        }
    }

    fun putCachedBitmap(absolutePath: String, bitmap: Bitmap) {
        if (absolutePath.isBlank() || bitmap.isRecycled) return
        synchronized(bitmapCache) {
            bitmapCache.put(absolutePath, bitmap)
        }
    }

    fun evictPath(absolutePath: String) {
        if (absolutePath.isBlank()) return
        synchronized(bitmapCache) {
            bitmapCache.remove(absolutePath)
        }
    }

    fun thumbnailFile(context: Context, notebookId: Long, pageId: Long): File =
        File(thumbnailsDir(context, notebookId), "thumb_page_${pageId}.webp")

    fun loadAssetBitmapSync(
        context: Context,
        notebookId: Long,
        relativeOrAbsolutePath: String,
        maxDimensionPx: Int = MAX_ASSET_LONG_EDGE
    ): Bitmap? {
        if (relativeOrAbsolutePath.isBlank()) return null
        val file = resolveAssetFile(context, notebookId, relativeOrAbsolutePath)
        if (!file.exists() || file.length() <= 0L) return null
        val cacheKey = "${file.absolutePath}@$maxDimensionPx"
        getCachedBitmap(cacheKey)?.let { return it }
        val decoded = decodeSampledBitmap(file, maxDimensionPx) ?: return null
        putCachedBitmap(cacheKey, decoded)
        return decoded
    }

    /**
     * Decodes (with subsampling if needed) and caches a notebook asset or background image into RAM.
     */
    suspend fun loadBitmap(
        context: Context,
        notebookId: Long,
        relativeOrAbsolutePath: String,
        maxDimensionPx: Int = MAX_ASSET_LONG_EDGE
    ): Bitmap? = withContext(Dispatchers.IO) {
        loadAssetBitmapSync(context, notebookId, relativeOrAbsolutePath, maxDimensionPx)
    }

    /**
     * Imports a user-selected image/PNG URI into `filesDir/notebooks/{notebookId}/assets/`.
     * Returns `Triple(relativeFileName, widthPx, heightPx)` or null on failure.
     * - JPEGs within MAX_ASSET_LONG_EDGE are copied directly without redundant recompression.
     * - PNGs / WebPs / alpha images / oversized photos are encoded as WebP (preserving full alpha!).
     */
    suspend fun importImageUri(
        context: Context,
        notebookId: Long,
        uri: Uri
    ): ImportedAssetResult? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase().orEmpty()
        val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        if (rawBytes.isEmpty()) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext null

        val dir = assetsDir(context, notebookId)
        val isJpeg = mime.contains("jpeg") || mime.contains("jpg") ||
            (rawBytes.size >= 2 && rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xD8.toByte())
        val longestEdge = max(srcW, srcH)

        // Smart JPEG passthrough: zero generational quality loss & instant copy!
        if (isJpeg && longestEdge <= MAX_ASSET_LONG_EDGE) {
            val fileName = "img_${UUID.randomUUID().toString().take(12)}.jpg"
            val outFile = File(dir, fileName)
            outFile.writeBytes(rawBytes)
            return@withContext ImportedAssetResult(
                relativeFileName = fileName,
                widthPx = srcW,
                heightPx = srcH
            )
        }

        // Decode with subsampling if oversized, preserving ARGB_8888 alpha channel for PNG/stickers
        val sampleSize = computeInSampleSize(srcW, srcH, MAX_ASSET_LONG_EDGE)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return@withContext null

        val finalBmp = scaleDownIfNeeded(decoded, MAX_ASSET_LONG_EDGE)
        val fileName = "img_${UUID.randomUUID().toString().take(12)}.webp"
        val outFile = File(dir, fileName)
        writeWebP(outFile, finalBmp, losslessIfAlpha = finalBmp.hasAlpha())

        val w = finalBmp.width
        val h = finalBmp.height
        putCachedBitmap("${outFile.absolutePath}@$MAX_ASSET_LONG_EDGE", finalBmp)
        if (finalBmp !== decoded) {
            decoded.recycle()
        }

        ImportedAssetResult(
            relativeFileName = fileName,
            widthPx = w,
            heightPx = h
        )
    }

    /**
     * Saves an in-memory ARGB_8888 transparent bitmap (e.g., ML Kit background-removed subject)
     * as a lossless WebP inside `filesDir/notebooks/{notebookId}/assets/` and warms the RAM cache.
     */
    suspend fun saveTransparentBitmapToNotebook(
        context: Context,
        notebookId: Long,
        bitmap: Bitmap
    ): ImportedAssetResult? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        val dir = assetsDir(context, notebookId)
        val finalBmp = scaleDownIfNeeded(bitmap, MAX_ASSET_LONG_EDGE)
        val fileName = "cutout_${UUID.randomUUID().toString().take(12)}.webp"
        val outFile = File(dir, fileName)
        writeWebP(outFile, finalBmp, losslessIfAlpha = true)
        val w = finalBmp.width
        val h = finalBmp.height
        putCachedBitmap("${outFile.absolutePath}@$MAX_ASSET_LONG_EDGE", finalBmp)
        putCachedBitmap("${outFile.absolutePath}@1280", finalBmp)
        ImportedAssetResult(
            relativeFileName = fileName,
            widthPx = w,
            heightPx = h
        )
    }

    fun userStickersDir(context: Context): File =
        File(context.filesDir, "notebooks/user_stickers").apply { mkdirs() }

    /**
     * Imports a user PNG/image into the shared `filesDir/notebooks/user_stickers/` library
     * preserving full alpha transparency so it can be reused across any notebook ("Mis stickers").
     */
    suspend fun importUserStickerUri(
        context: Context,
        uri: Uri
    ): String? = withContext(Dispatchers.IO) {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        if (rawBytes.isEmpty()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, 768)
        val decoded = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@withContext null

        val finalBmp = scaleDownIfNeeded(decoded, 768)
        val outFile = File(userStickersDir(context), "stk_${UUID.randomUUID().toString().take(12)}.webp")
        writeWebP(outFile, finalBmp, losslessIfAlpha = true)
        putCachedBitmap("${outFile.absolutePath}@$MAX_ASSET_LONG_EDGE", finalBmp)
        if (finalBmp !== decoded) decoded.recycle()
        outFile.absolutePath
    }

    /**
     * Renders a PDF file's pages into WebP background assets inside `filesDir/notebooks/{notebookId}/assets/`.
     * Returns list of `Pair(relativeFileName, Pair(widthPx, heightPx))` for each rendered page.
     */
    suspend fun renderPdfPagesToAssets(
        context: Context,
        notebookId: Long,
        pdfFile: File,
        maxPages: Int = 60
    ): List<ImportedAssetResult> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() <= 0L) return@withContext emptyList()
        val results = mutableListOf<ImportedAssetResult>()
        val dir = assetsDir(context, notebookId)
        val pfd = runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return@withContext emptyList()

        try {
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount.coerceAtMost(maxPages)
                for (i in 0 until total) {
                    renderer.openPage(i).use { page ->
                        val targetW = MAX_PDF_BG_WIDTH.coerceAtMost(if (MemoryPressureManager.isLowRamDevice) 1120 else MAX_PDF_BG_WIDTH)
                        val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
                        val targetH = (page.height * scale).roundToInt().coerceIn(240, 2600)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val fileName = "pdf_bg_${i}_${UUID.randomUUID().toString().take(8)}.webp"
                        val outFile = File(dir, fileName)
                        writeWebP(outFile, bmp, losslessIfAlpha = false, quality = 88)
                        if (i <= 2) {
                            putCachedBitmap("${outFile.absolutePath}@$MAX_ASSET_LONG_EDGE", bmp)
                        } else {
                            bmp.recycle()
                        }
                        results.add(ImportedAssetResult(fileName, targetW, targetH))
                    }
                }
            }
        } catch (_: Throwable) {
            // Return whatever pages succeeded cleanly
        } finally {
            runCatching { pfd.close() }
        }
        results
    }

    /**
     * Saves a page preview thumbnail as a compact WebP file in `filesDir/notebooks/{notebookId}/thumbnails/`.
     */
    suspend fun savePageThumbnail(
        context: Context,
        notebookId: Long,
        pageId: Long,
        bitmap: Bitmap
    ): String = withContext(Dispatchers.IO) {
        val dir = thumbnailsDir(context, notebookId)
        val outFile = File(dir, "thumb_page_${pageId}.webp")
        val scaled = scaleDownIfNeeded(bitmap, THUMBNAIL_WIDTH)
        writeWebP(outFile, scaled, losslessIfAlpha = false, quality = 82)
        evictPath(outFile.absolutePath)
        evictPath("${outFile.absolutePath}@$THUMBNAIL_WIDTH")
        putCachedBitmap(outFile.absolutePath, scaled)
        if (scaled !== bitmap) {
            // Do not recycle caller's bitmap; caller owns it
        }
        outFile.absolutePath
    }

    /**
     * Deletes all disk files (assets, thumbnails, PDF backgrounds) belonging to a notebook.
     */
    suspend fun deleteNotebookFiles(context: Context, notebookId: Long) = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "notebooks/$notebookId")
        val prefix = root.absolutePath
        synchronized(bitmapCache) {
            val keys = bitmapCache.snapshot().keys.filter { it.startsWith(prefix) }
            keys.forEach { bitmapCache.remove(it) }
        }
        runCatching { root.deleteRecursively() }
    }

    private fun decodeSampledBitmap(file: File, maxDimensionPx: Int): Bitmap? {
        val safeTarget = maxDimensionPx.coerceIn(64, MAX_ASSET_LONG_EDGE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(srcW, srcH, safeTarget)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull() ?: return null
        val scaled = scaleDownIfNeeded(decoded, safeTarget)
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val longest = max(width, height)
        // Strict power-of-2 subsampling so a 12 MP photo (4000x3000) is NEVER decoded full-size into RAM
        while ((longest / (sample * 2)) >= maxDim) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDownIfNeeded(src: Bitmap, maxLongEdge: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxLongEdge) return src
        val ratio = maxLongEdge.toFloat() / longest.toFloat()
        val matrix = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    @Suppress("DEPRECATION")
    private fun writeWebP(
        outFile: File,
        bitmap: Bitmap,
        losslessIfAlpha: Boolean,
        quality: Int = 90
    ) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (losslessIfAlpha) Bitmap.CompressFormat.WEBP_LOSSLESS
            else Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val effectiveQuality = if (losslessIfAlpha && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 100 else quality
        // Atomic write: encode into sibling .tmp file and rename so partial writes never corrupt disk assets
        val tmpFile = File(outFile.parentFile, "${outFile.name}.tmp")
        FileOutputStream(tmpFile).use { fos ->
            bitmap.compress(format, effectiveQuality, fos)
            fos.fd.sync()
        }
        if (outFile.exists()) outFile.delete()
        if (!tmpFile.renameTo(outFile)) {
            tmpFile.copyTo(outFile, overwrite = true)
            tmpFile.delete()
        }
    }
}

data class ImportedAssetResult(
    val relativeFileName: String,
    val widthPx: Int,
    val heightPx: Int
)

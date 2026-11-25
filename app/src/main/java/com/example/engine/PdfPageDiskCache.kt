package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-speed persistent disk cache for rendered PDF pages.
 *
 * Prioritizes instant response time over disk footprint (up to 1.5 GB / 4,000 pages LRU under
 * `filesDir/pdf_page_cache/`). Decoding a cached JPEG page via native libjpeg-turbo takes ~6–10 ms
 * versus 50–180 ms for `PdfRenderer.Page.render()`, making reopened PDFs and rapid page flips
 * feel practically instantaneous even on modest CPUs.
 */
object PdfPageDiskCache {

    private const val MAX_CACHE_FILES = 4000
    private const val JPEG_QUALITY = 92

    private val writesSinceSweep = AtomicInteger(0)

    fun read(
        context: Context,
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap? {
        val cacheFile = cacheFileFor(context, filePath, pageIndex, targetWidthPx) ?: return null
        if (!cacheFile.exists() || cacheFile.length() < 64L) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = if (MemoryPressureManager.isLowRamDevice) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
                inDither = false
            }
            val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
            if (bmp == null) {
                runCatching { cacheFile.delete() }
            } else {
                // Touch file periodically for LRU freshness
                cacheFile.setLastModified(System.currentTimeMillis())
            }
            bmp
        }.getOrNull()
    }

    fun write(
        context: Context,
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int,
        bitmap: Bitmap
    ) {
        if (bitmap.isRecycled) return
        val target = cacheFileFor(context, filePath, pageIndex, targetWidthPx) ?: return
        if (target.exists() && target.length() >= 64L) return
        val dir = target.parentFile ?: return
        val tmp = File(dir, "${target.name}.${Thread.currentThread().id}.tmp")
        runCatching {
            BufferedOutputStream(FileOutputStream(tmp), 32 * 1024).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
            }
            if (writesSinceSweep.incrementAndGet() >= 24) {
                writesSinceSweep.set(0)
                evictIfNeeded(dir)
                LumaDiskCacheManager.enforceGlobalBudget(context)
            }
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.applicationContext.filesDir, "pdf_page_cache").apply {
            if (!exists()) mkdirs()
        }

    private fun cacheFileFor(
        context: Context,
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int
    ): File? {
        val src = File(filePath)
        if (!src.exists()) return null
        val key = "$filePath|${src.length()}|${src.lastModified()}|$pageIndex|$targetWidthPx"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val hex = buildString(32) {
            for (i in 0 until 16) {
                append(((digest[i].toInt() and 0xFF) + 0x100).toString(16).substring(1))
            }
        }
        return File(cacheDir(context), "$hex.jpg")
    }

    private fun evictIfNeeded(dir: File) {
        val maxBytes = LumaDiskCacheManager.pdfPageCacheMaxBytes()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") } ?: return
        var totalBytes = files.sumOf { it.length() }
        if (files.size <= MAX_CACHE_FILES && totalBytes <= maxBytes) return
        val sortedOldestFirst = files.sortedBy { it.lastModified() }
        var remaining = files.size
        for (f in sortedOldestFirst) {
            if (remaining <= MAX_CACHE_FILES && totalBytes <= maxBytes) break
            val len = f.length()
            if (f.delete()) {
                totalBytes -= len
                remaining--
            }
        }
    }
}

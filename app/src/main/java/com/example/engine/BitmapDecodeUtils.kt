package com.example.engine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Centralized, memory-safe bitmap decoder.
 * Always measures bounds first (or configures ImageDecoder target size on API 28+) so images are
 * never decoded at full native resolution when only a thumbnail, avatar, or viewport-sized bitmap
 * is needed.
 */
object BitmapDecodeUtils {

    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
            // Guard against ultra-tall or ultra-wide images where one axis exceeds 2x target
            while ((rawWidth / inSampleSize) > reqWidth * 2 || (rawHeight / inSampleSize) > reqHeight * 2) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun decodeSampledFile(
        path: String,
        reqWidth: Int,
        reqHeight: Int,
        preferRgb565: Boolean = false
    ): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !preferRgb565) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    configureImageDecoder(decoder, info.size.width, info.size.height, reqWidth, reqHeight)
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    fun decodeSampledBytes(
        bytes: ByteArray,
        reqWidth: Int,
        reqHeight: Int,
        preferRgb565: Boolean = false
    ): Bitmap? {
        if (bytes.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !preferRgb565) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    configureImageDecoder(decoder, info.size.width, info.size.height, reqWidth, reqHeight)
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }

    fun decodeSampledUri(
        contentResolver: ContentResolver,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int,
        preferRgb565: Boolean = false
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !preferRgb565) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    configureImageDecoder(decoder, info.size.width, info.size.height, reqWidth, reqHeight)
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(BufferedInputStream(stream), null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(BufferedInputStream(stream), null, opts)
            }
        }.getOrNull()
    }

    /**
     * Streams a ZIP entry in two passes (bounds -> sampled decode) so multi-megabyte embedded
     * images inside EPUB/PPTX archives never require loading raw uncompressed bytes into RAM.
     */
    fun decodeSampledZipEntry(
        zip: ZipFile,
        entry: ZipEntry,
        reqWidth: Int,
        reqHeight: Int,
        preferRgb565: Boolean = false
    ): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { stream ->
            BitmapFactory.decodeStream(BufferedInputStream(stream), null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        zip.getInputStream(entry).use { stream ->
            BitmapFactory.decodeStream(BufferedInputStream(stream), null, opts)
        }
    }.getOrNull()

    private fun configureImageDecoder(
        decoder: ImageDecoder,
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ) {
        // Software allocator ensures safe interoperability with Canvas, PDF export, and pixel reads.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        if (srcWidth > 0 && srcHeight > 0 && (srcWidth > reqWidth || srcHeight > reqHeight)) {
            val scale = minOf(
                reqWidth.toFloat() / srcWidth.toFloat(),
                reqHeight.toFloat() / srcHeight.toFloat()
            ).coerceIn(0.05f, 1f)
            val targetW = max(1, (srcWidth * scale).roundToInt())
            val targetH = max(1, (srcHeight * scale).roundToInt())
            decoder.setTargetSize(targetW, targetH)
        }
    }
}

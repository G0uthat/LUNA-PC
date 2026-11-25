package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import com.example.data.model.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * Automatically extracts embedded covers (PDF page 1 / EPUB cover image) or generates
 * studio-grade minimalist portrait covers for books lacking artwork, powering the
 * Steam-style visual library grid.
 */
object CoverManager {

    private const val COVER_W = 420
    private const val COVER_H = 620
    private const val THUMB_W = 210
    private const val THUMB_H = 310
    private const val WEBP_QUALITY_FULL = 84
    private const val WEBP_QUALITY_THUMB = 78

    internal const val THUMB_SUFFIX = "_thumb.webp"

    /**
     * Resolves the precomputed lightweight 210×310 WebP grid thumbnail for [coverPath] when present,
     * falling back transparently to [coverPath] for legacy JPEG covers or missing thumbnails.
     */
    fun resolveGridCoverPath(coverPath: String?): String? {
        if (coverPath.isNullOrBlank()) return null
        val thumb = thumbnailFileFor(coverPath)
        return if (thumb != null && thumb.exists() && thumb.length() > 64L) {
            thumb.absolutePath
        } else {
            coverPath
        }
    }

    fun thumbnailFileFor(coverPath: String): File? {
        val base = coverPath.substringBeforeLast('.', missingDelimiterValue = "")
        if (base.isEmpty()) return null
        return File("${base}${THUMB_SUFFIX}")
    }

    suspend fun generateOrExtractCover(
        context: Context,
        sourceFile: File,
        format: DocumentFormat,
        title: String,
        author: String,
        accentColorHex: Long
    ): String = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val baseKey = "${abs(sourceFile.absolutePath.hashCode())}_${System.currentTimeMillis()}"
        val outFile = File(coversDir, "$baseKey.webp")

        val extracted: Bitmap? = when (format) {
            DocumentFormat.PDF -> extractPdfFirstPageBitmap(sourceFile)
            DocumentFormat.EPUB -> extractEpubCoverBitmap(sourceFile)
            DocumentFormat.PPTX -> extractPptxCoverBitmap(sourceFile)
            else -> null
        }

        val finalBitmap = extracted ?: renderArtisticPosterCover(
            title = title,
            author = author,
            formatLabel = format.label,
            accentColorHex = accentColorHex
        )

        writeCoverAndThumbnail(finalBitmap, outFile)
        finalBitmap.recycle()
        outFile.absolutePath
    }

    suspend fun saveCustomCoverFromUri(
        context: Context,
        imageUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val outFile = File(coversDir, "custom_${System.currentTimeMillis()}.webp")
            val decoded = BitmapDecodeUtils.decodeSampledUri(
                contentResolver = context.contentResolver,
                uri = imageUri,
                reqWidth = 480,
                reqHeight = 860
            ) ?: return@runCatching null

            val ratio = decoded.width.toFloat() / decoded.height.toFloat().coerceAtLeast(1f)
            val targetW = 480
            val targetH = (targetW / ratio).toInt().coerceIn(320, 860)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled !== decoded) decoded.recycle()

            writeCoverAndThumbnail(scaled, outFile)
            scaled.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    suspend fun regenerateArtisticCover(
        context: Context,
        title: String,
        author: String,
        format: DocumentFormat,
        accentColorHex: Long
    ): String = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val outFile = File(coversDir, "art_${System.currentTimeMillis()}.webp")
        val bmp = renderArtisticPosterCover(
            title = title,
            author = author,
            formatLabel = format.label,
            accentColorHex = accentColorHex
        )
        writeCoverAndThumbnail(bmp, outFile)
        bmp.recycle()
        outFile.absolutePath
    }

    private fun writeCoverAndThumbnail(bitmap: Bitmap, fullFile: File) {
        val format = webpFormat()
        runCatching {
            FileOutputStream(fullFile).use { fos ->
                bitmap.compress(format, WEBP_QUALITY_FULL, fos)
            }
        }
        // Precompute lightweight 210px thumbnail for instant zero-jank Library grid decode (~4x fewer pixels)
        val thumbFile = thumbnailFileFor(fullFile.absolutePath) ?: return
        runCatching {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            val tw = THUMB_W.coerceAtMost(bitmap.width.coerceAtLeast(1))
            val th = (tw / ratio).toInt().coerceIn(140, THUMB_H)
            val thumbBmp = Bitmap.createScaledBitmap(bitmap, tw, th, true)
            try {
                FileOutputStream(thumbFile).use { fos ->
                    thumbBmp.compress(format, WEBP_QUALITY_THUMB, fos)
                }
            } finally {
                if (thumbBmp !== bitmap) thumbBmp.recycle()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun extractPdfFirstPageBitmap(pdfFile: File): Bitmap? = runCatching {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return@runCatching null
                renderer.openPage(0).use { page ->
                    val scale = COVER_W.toFloat() / page.width.coerceAtLeast(1)
                    val targetH = (page.height * scale).toInt().coerceIn(280, 900)
                    // PdfRenderer.Page.render() requires ARGB_8888
                    val bmp = Bitmap.createBitmap(COVER_W, targetH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

    private fun extractEpubCoverBitmap(epubFile: File): Bitmap? = runCatching {
        ZipFile(epubFile).use { zip ->
            val imageEntries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { e ->
                    val lower = e.name.lowercase()
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                }
                .toList()

            if (imageEntries.isEmpty()) return@runCatching null

            val preferred = imageEntries.firstOrNull { it.name.contains("cover", ignoreCase = true) }
                ?: imageEntries.maxByOrNull { it.size }
                ?: return@runCatching null

            val decoded = BitmapDecodeUtils.decodeSampledZipEntry(
                zip = zip,
                entry = preferred,
                reqWidth = COVER_W,
                reqHeight = COVER_H
            ) ?: return@runCatching null
            val scaled = Bitmap.createScaledBitmap(decoded, COVER_W, COVER_H, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        }
    }.getOrNull()

    private fun extractPptxCoverBitmap(file: File): Bitmap? = runCatching {
        ZipFile(file).use { zip ->
            val preferred = zip.getEntry("docProps/thumbnail.jpeg")
                ?: zip.getEntry("docProps/thumbnail.jpg")
                ?: zip.getEntry("docProps/thumbnail.png")
                ?: zip.entries().asSequence()
                    .filter { entry ->
                        val lower = entry.name.lowercase()
                        lower.startsWith("ppt/media/") &&
                            (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
                    }
                    .maxByOrNull { it.size }
                ?: return@runCatching null

            val decoded = BitmapDecodeUtils.decodeSampledZipEntry(
                zip = zip,
                entry = preferred,
                reqWidth = COVER_W,
                reqHeight = COVER_H
            ) ?: return@runCatching null

            // Frame widescreen slide thumbnail cleanly onto a studio vertical book cover
            val out = Bitmap.createBitmap(COVER_W, COVER_H, Bitmap.Config.RGB_565)
            val canvas = Canvas(out)
            canvas.drawColor(Color.parseColor("#182032"))

            val scale = minOf(COVER_W.toFloat() / decoded.width.coerceAtLeast(1), (COVER_H * 0.62f) / decoded.height.coerceAtLeast(1))
            val dw = decoded.width * scale
            val dh = decoded.height * scale
            val left = (COVER_W - dw) * 0.5f
            val top = (COVER_H - dh) * 0.5f
            canvas.drawBitmap(decoded, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
            decoded.recycle()
            out
        }
    }.getOrNull()

    private fun renderArtisticPosterCover(
        title: String,
        author: String,
        formatLabel: String,
        accentColorHex: Long
    ): Bitmap {
        val bmp = Bitmap.createBitmap(COVER_W, COVER_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val baseColor = accentColorHex.toInt()
        val darker = darkenColor(baseColor, 0.56f)
        val deepShadow = darkenColor(baseColor, 0.30f)
        val glowColor = lightenColor(baseColor, 0.32f)

        // Rich atmospheric vertical gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, COVER_W.toFloat(), COVER_H.toFloat(),
                intArrayOf(baseColor, darker, deepShadow),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, COVER_W.toFloat(), COVER_H.toFloat(), bgPaint)

        // Luminous soft radial orb (entertainment poster vibe)
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                COVER_W * 0.78f, COVER_H * 0.24f, COVER_W * 0.62f,
                intArrayOf(Color.argb(120, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(COVER_W * 0.78f, COVER_H * 0.24f, COVER_W * 0.62f, orbPaint)

        // Geometric minimalist decorative rings & subtle book spine highlight
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val seed = abs(title.hashCode()) % 3
        when (seed) {
            0 -> {
                c.drawCircle(COVER_W * 0.84f, COVER_H * 0.20f, 128f, arcPaint)
                c.drawCircle(COVER_W * 0.84f, COVER_H * 0.20f, 82f, arcPaint)
            }
            1 -> {
                val r1 = RectF(COVER_W * 0.42f, -40f, COVER_W + 60f, 240f)
                c.drawRoundRect(r1, 48f, 48f, arcPaint)
                val r2 = RectF(COVER_W * 0.56f, 36f, COVER_W + 40f, 280f)
                c.drawRoundRect(r2, 40f, 40f, arcPaint)
            }
            else -> {
                c.drawArc(RectF(-80f, -60f, COVER_W * 1.1f, 320f), 10f, 160f, false, arcPaint)
                c.drawArc(RectF(40f, 20f, COVER_W * 1.15f, 360f), 30f, 140f, false, arcPaint)
            }
        }

        // Subtle left book-spine binding sheen
        val spinePaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 26f, 0f,
                intArrayOf(Color.argb(75, 0, 0, 0), Color.argb(28, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, 26f, COVER_H.toFloat(), spinePaint)

        // Minimal format pill top-left
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(56, 255, 255, 255) }
        val pillRect = RectF(34f, 36f, 124f, 70f)
        c.drawRoundRect(pillRect, 18f, 18f, pillBg)
        val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(formatLabel, pillRect.centerX(), pillRect.centerY() + 6f, pillText)

        // Crisp Editorial Title (wrapped via StaticLayout)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (title.length > 28) 32f else 38f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder
            .obtain(title, 0, title.length.coerceAtMost(72), titlePaint, COVER_W - 68)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(4)
            .setLineSpacing(6f, 1.05f)
            .build()

        val titleTop = (COVER_H - titleLayout.height - 104f).coerceAtLeast(160f)
        c.save()
        c.translate(34f, titleTop)
        titleLayout.draw(c)
        c.restore()

        // Divider bar + Author
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(165, 255, 255, 255) }
        val barY = COVER_H - 74f
        c.drawRoundRect(RectF(34f, barY, 92f, barY + 5f), 4f, 4f, barPaint)

        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        c.drawText(author.take(28), 34f, COVER_H - 36f, authorPaint)

        return bmp
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}

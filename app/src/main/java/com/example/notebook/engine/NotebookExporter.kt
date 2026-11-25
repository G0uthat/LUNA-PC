package com.example.notebook.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.engine.MemoryPressureManager
import com.example.notebook.data.NotebookAssetStore
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageEntity
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.model.ImageObject
import com.example.notebook.model.NotebookJsonCodec
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.StickerObject
import com.example.notebook.ui.NotebookFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

enum class NotebookImageExportFormat(val mimeType: String, val extension: String, val label: String) {
    PNG("image/png", "png", "PNG"),
    JPG("image/jpeg", "jpg", "JPG"),
    WEBP("image/webp", "webp", "WebP")
}

enum class NotebookExportFormat(
    val mimeType: String,
    val extension: String,
    val label: String,
    val subtitle: String,
    val isZipBundle: Boolean = false
) {
    PDF("application/pdf", "pdf", "PDF vectorial", "Texto seleccionable, trazos vectoriales e imágenes nítidas"),
    PNG("image/png", "png", "PNG", "Sin pérdidas · admite fondo transparente"),
    JPG("image/jpeg", "jpg", "JPG", "Ligero y compatible con cualquier galería"),
    ZIP_PNG("application/zip", "zip", "ZIP (PNGs)", "Todas las páginas en imágenes PNG dentro de un ZIP", isZipBundle = true),
    ZIP_JPG("application/zip", "zip", "ZIP (JPGs)", "Todas las páginas en imágenes JPG ligeras en ZIP", isZipBundle = true)
}

enum class NotebookExportScope(val label: String) {
    ALL_PAGES("Todo el cuaderno"),
    CURRENT_PAGE("Página actual")
}

enum class NotebookExportScale(val multiplier: Int, val label: String, val detail: String) {
    X1(1, "1×", "Rápido"),
    X2(2, "2×", "HD"),
    X3(3, "3×", "Ultra")
}

data class NotebookExportOptions(
    val format: NotebookExportFormat = NotebookExportFormat.PDF,
    val scope: NotebookExportScope = NotebookExportScope.ALL_PAGES,
    val scale: NotebookExportScale = NotebookExportScale.X2,
    val transparentBackground: Boolean = false
)

data class NotebookExportProgress(
    val fraction: Float,
    val message: String,
    val currentPage: Int = 0,
    val totalPages: Int = 1
)

/**
 * High-speed, cancellable, off-main-thread exporter & atomic WebP thumbnail generator for Study Notebooks.
 * Shares the SINGLE WYSIWYG [NotebookCanvasRenderer] so exported vector PDFs, PNGs (with optional
 * transparent background), JPGs, and multi-page ZIP bundles match the interactive canvas 1:1.
 *
 * - Runs exclusively on [Dispatchers.IO] with cooperative [ensureActive] cancellation between every page.
 * - Reuses the `PdfDocument` + `Canvas` vector approach from `MergedDocumentExporter`: text stays
 *   selectable and strokes/shapes stay crisp vector paths at any zoom.
 * - Recycles temporary page bitmaps immediately so 3× multi-page ZIP exports never OOM.
 */
object NotebookExporter {

    private const val THUMBNAIL_WIDTH_PX = 420
    private const val BASE_EXPORT_LONG_EDGE_1X = 1280

    suspend fun generatePageThumbnailWebp(
        context: Context,
        notebook: NotebookEntity,
        page: NotebookPageEntity,
        content: NotebookPageContent
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val aspect = notebook.pageHeightPx.toFloat() / notebook.pageWidthPx.coerceAtLeast(1)
            val thumbW = THUMBNAIL_WIDTH_PX
            val thumbH = (thumbW * aspect).toInt().coerceIn(240, 760)
            val bitmap = renderPageToBitmap(
                context = context,
                notebook = notebook,
                page = page,
                content = content,
                targetWidthPx = thumbW,
                targetHeightPx = thumbH,
                assetDecodeBudgetPx = 640,
                includeBackground = true
            )
            try {
                NotebookAssetStore.savePageThumbnail(context, notebook.id, page.id, bitmap)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }.getOrNull()
    }

    /**
     * Suggests a clean export filename for the chosen [options].
     */
    fun suggestedFileName(
        notebookTitle: String,
        currentPageNumber: Int,
        options: NotebookExportOptions
    ): String {
        val base = sanitizeFilename(notebookTitle)
        val suffix = when {
            options.format.isZipBundle -> "_todas_paginas"
            options.scope == NotebookExportScope.CURRENT_PAGE -> "_pag$currentPageNumber"
            else -> ""
        }
        return "${base}${suffix}.${options.format.extension}"
    }

    /**
     * Exports according to [options] directly to an arbitrary SAF [destinationUri] (zero broad permissions).
     */
    suspend fun exportToUri(
        context: Context,
        notebook: NotebookEntity,
        pages: List<NotebookPageEntity>,
        currentPage: NotebookPageEntity,
        contentByPageId: Map<Long, NotebookPageContent>,
        options: NotebookExportOptions,
        destinationUri: Uri,
        onProgress: (NotebookExportProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        context.contentResolver.openOutputStream(destinationUri)?.use { rawOut ->
            BufferedOutputStream(rawOut, 32 * 1024).use { out ->
                writeExportToStream(
                    context = context,
                    notebook = notebook,
                    pages = pages,
                    currentPage = currentPage,
                    contentByPageId = contentByPageId,
                    options = options,
                    out = out,
                    onProgress = onProgress
                )
                out.flush()
            }
            true
        } ?: false
    }

    /**
     * Exports according to [options] to an atomic local file inside `filesDir/exports/`
     * (used for Share Sheet via FileProvider and for "Añadir a mi biblioteca de Luma").
     */
    suspend fun exportToCacheFile(
        context: Context,
        notebook: NotebookEntity,
        pages: List<NotebookPageEntity>,
        currentPage: NotebookPageEntity,
        contentByPageId: Map<Long, NotebookPageContent>,
        options: NotebookExportOptions,
        onProgress: (NotebookExportProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileName = suggestedFileName(notebook.title, currentPage.pageIndex + 1, options)
        val outFile = File(exportsDir, fileName)
        val tmpFile = File(exportsDir, "${fileName}.tmp")
        try {
            FileOutputStream(tmpFile).use { fos ->
                BufferedOutputStream(fos, 32 * 1024).use { bos ->
                    writeExportToStream(
                        context = context,
                        notebook = notebook,
                        pages = pages,
                        currentPage = currentPage,
                        contentByPageId = contentByPageId,
                        options = options,
                        out = bos,
                        onProgress = onProgress
                    )
                    bos.flush()
                }
                fos.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            if (outFile.exists()) outFile.delete()
            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                tmpFile.delete()
            }
            outFile
        } catch (t: Throwable) {
            runCatching { tmpFile.delete() }
            throw t
        }
    }

    /**
     * One-tap zero-permission save on Android 10+ (API 29+) via MediaStore:
     * - Images (PNG/JPG) go straight to `Pictures/Luma`
     * - Documents (PDF/ZIP) go straight to `Download/Luma`
     * Returns the saved MediaStore [Uri], or null on API < 29 (caller opens SAF CreateDocument).
     */
    suspend fun saveToDeviceMediaStore(
        context: Context,
        notebook: NotebookEntity,
        pages: List<NotebookPageEntity>,
        currentPage: NotebookPageEntity,
        contentByPageId: Map<Long, NotebookPageContent>,
        options: NotebookExportOptions,
        onProgress: (NotebookExportProgress) -> Unit = {}
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        currentCoroutineContext().ensureActive()

        val resolver = context.contentResolver
        val fileName = suggestedFileName(notebook.title, currentPage.pageIndex + 1, options)
        val isSingleImage = options.format == NotebookExportFormat.PNG || options.format == NotebookExportFormat.JPG

        val collection = if (isSingleImage) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relPath = if (isSingleImage) {
            "${Environment.DIRECTORY_PICTURES}/Luma"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/Luma"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, options.format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collection, values) ?: return@withContext null
        try {
            resolver.openOutputStream(itemUri)?.use { rawOut ->
                BufferedOutputStream(rawOut, 32 * 1024).use { bos ->
                    writeExportToStream(
                        context = context,
                        notebook = notebook,
                        pages = pages,
                        currentPage = currentPage,
                        contentByPageId = contentByPageId,
                        options = options,
                        out = bos,
                        onProgress = onProgress
                    )
                    bos.flush()
                }
            } ?: error("No se pudo abrir el destino en MediaStore")

            val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, doneValues, null, null)
            itemUri
        } catch (t: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw t
        }
    }

    fun buildShareIntent(context: Context, file: File, mimeType: String, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    private suspend fun writeExportToStream(
        context: Context,
        notebook: NotebookEntity,
        pages: List<NotebookPageEntity>,
        currentPage: NotebookPageEntity,
        contentByPageId: Map<Long, NotebookPageContent>,
        options: NotebookExportOptions,
        out: OutputStream,
        onProgress: (NotebookExportProgress) -> Unit
    ) {
        when (options.format) {
            NotebookExportFormat.PDF -> {
                val targetPages = if (options.scope == NotebookExportScope.CURRENT_PAGE) {
                    listOf(currentPage)
                } else {
                    pages.sortedBy { it.pageIndex }
                }
                writeVectorPdfToStream(
                    context = context,
                    notebook = notebook,
                    pages = targetPages,
                    contentByPageId = contentByPageId,
                    scale = options.scale,
                    out = out,
                    onProgress = onProgress
                )
            }

            NotebookExportFormat.PNG -> {
                val parsed = contentByPageId[currentPage.id] ?: NotebookJsonCodec.decode(currentPage.contentJson)
                writeSingleImageToStream(
                    context = context,
                    notebook = notebook,
                    page = currentPage,
                    content = parsed,
                    isPng = true,
                    transparentBg = options.transparentBackground,
                    scale = options.scale,
                    out = out,
                    onProgress = onProgress
                )
            }

            NotebookExportFormat.JPG -> {
                val parsed = contentByPageId[currentPage.id] ?: NotebookJsonCodec.decode(currentPage.contentJson)
                writeSingleImageToStream(
                    context = context,
                    notebook = notebook,
                    page = currentPage,
                    content = parsed,
                    isPng = false,
                    transparentBg = false,
                    scale = options.scale,
                    out = out,
                    onProgress = onProgress
                )
            }

            NotebookExportFormat.ZIP_PNG,
            NotebookExportFormat.ZIP_JPG -> {
                val isPng = options.format == NotebookExportFormat.ZIP_PNG
                val sortedPages = pages.sortedBy { it.pageIndex }
                writeAllPagesZipToStream(
                    context = context,
                    notebook = notebook,
                    pages = sortedPages,
                    contentByPageId = contentByPageId,
                    isPng = isPng,
                    transparentBg = isPng && options.transparentBackground,
                    scale = options.scale,
                    out = out,
                    onProgress = onProgress
                )
            }
        }
    }

    /**
     * Vector PDF export via Android's [PdfDocument] + [NotebookCanvasRenderer.drawPage]:
     * - Text is drawn directly onto `pdfPage.canvas` with embedded fonts -> selectable vector text!
     * - Strokes, shapes, connectors, and tables are drawn as vector paths -> crisp at any zoom and tiny file size!
     * - Embedded images decode at the chosen 1×/2×/3× budget for crisp illustrations.
     */
    private suspend fun writeVectorPdfToStream(
        context: Context,
        notebook: NotebookEntity,
        pages: List<NotebookPageEntity>,
        contentByPageId: Map<Long, NotebookPageContent>,
        scale: NotebookExportScale,
        out: OutputStream,
        onProgress: (NotebookExportProgress) -> Unit
    ) {
        val total = pages.size.coerceAtLeast(1)
        onProgress(NotebookExportProgress(0.02f, "Preparando PDF vectorial…", 0, total))

        val pdf = PdfDocument()
        val template = NotebookPageTemplate.fromName(notebook.pageTemplate)
        // Standard crisp PDF canvas coordinate space preserving notebook aspect ratio
        val basePdfW = when (scale) {
            NotebookExportScale.X1 -> 840
            NotebookExportScale.X2 -> 1240
            NotebookExportScale.X3 -> 1654
        }
        val aspect = notebook.pageHeightPx.toFloat() / notebook.pageWidthPx.coerceAtLeast(1)
        val pdfW = basePdfW
        val pdfH = (pdfW * aspect).roundToInt().coerceIn(540, 2600)
        val imageDecodeBudget = when (scale) {
            NotebookExportScale.X1 -> 1024
            NotebookExportScale.X2 -> 1600
            NotebookExportScale.X3 -> 2048
        }

        try {
            pages.forEachIndexed { idx, page ->
                currentCoroutineContext().ensureActive()
                val step = idx + 1
                onProgress(
                    NotebookExportProgress(
                        fraction = (idx.toFloat() / total) * 0.88f + 0.04f,
                        message = "Generando PDF vectorial · pág $step de $total",
                        currentPage = step,
                        totalPages = total
                    )
                )

                val pageInfo = PdfDocument.PageInfo.Builder(pdfW, pdfH, step).create()
                val pdfPage = pdf.startPage(pageInfo)
                val parsed = contentByPageId[page.id] ?: NotebookJsonCodec.decode(page.contentJson)
                val bgBitmap = if (page.backgroundAssetPath.isNotBlank()) {
                    NotebookAssetStore.loadAssetBitmapSync(context, notebook.id, page.backgroundAssetPath, imageDecodeBudget)
                } else null
                val objectBitmaps = loadPageObjectBitmaps(context, notebook.id, parsed, imageDecodeBudget)

                NotebookCanvasRenderer.drawPage(
                    canvas = pdfPage.canvas,
                    widthPx = pdfW.toFloat(),
                    heightPx = pdfH.toFloat(),
                    template = template,
                    backgroundType = NotebookBackgroundType.fromName(page.backgroundType),
                    backgroundColorArgb = page.backgroundColor,
                    backgroundBitmap = bgBitmap,
                    content = parsed,
                    assetBitmapProvider = { rel -> objectBitmaps[rel] },
                    typefaceProvider = { fontId -> NotebookFonts.resolveAndroidTypeface(context, fontId) },
                    includeBackground = true
                )
                pdf.finishPage(pdfPage)
            }

            currentCoroutineContext().ensureActive()
            onProgress(NotebookExportProgress(0.95f, "Escribiendo archivo PDF…", total, total))
            pdf.writeTo(out)
            onProgress(NotebookExportProgress(1f, "PDF listo", total, total))
        } finally {
            pdf.close()
        }
    }

    private suspend fun writeSingleImageToStream(
        context: Context,
        notebook: NotebookEntity,
        page: NotebookPageEntity,
        content: NotebookPageContent,
        isPng: Boolean,
        transparentBg: Boolean,
        scale: NotebookExportScale,
        out: OutputStream,
        onProgress: (NotebookExportProgress) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        val formatLabel = if (isPng) "PNG" else "JPG"
        onProgress(NotebookExportProgress(0.18f, "Renderizando $formatLabel (${scale.label})…", 1, 1))

        val (targetW, targetH) = computeRasterDimensions(notebook, scale)
        val bitmap = renderPageToBitmap(
            context = context,
            notebook = notebook,
            page = page,
            content = content,
            targetWidthPx = targetW,
            targetHeightPx = targetH,
            assetDecodeBudgetPx = maxOf(targetW, targetH).coerceAtMost(2048),
            includeBackground = !(isPng && transparentBg)
        )
        try {
            currentCoroutineContext().ensureActive()
            onProgress(NotebookExportProgress(0.72f, "Codificando $formatLabel (${targetW}×${targetH})…", 1, 1))
            if (isPng) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            onProgress(NotebookExportProgress(1f, "$formatLabel listo", 1, 1))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun writeAllPagesZipToStream(
        context: Context,
        notebook: NotebookEntity,
        pages: List<NotebookPageEntity>,
        contentByPageId: Map<Long, NotebookPageContent>,
        isPng: Boolean,
        transparentBg: Boolean,
        scale: NotebookExportScale,
        out: OutputStream,
        onProgress: (NotebookExportProgress) -> Unit
    ) {
        val total = pages.size.coerceAtLeast(1)
        val ext = if (isPng) "png" else "jpg"
        val safeBase = sanitizeFilename(notebook.title)
        val (targetW, targetH) = computeRasterDimensions(notebook, scale)
        val decodeBudget = maxOf(targetW, targetH).coerceAtMost(2048)

        onProgress(NotebookExportProgress(0.02f, "Empaquetando $total páginas en ZIP…", 0, total))

        ZipOutputStream(out).use { zos ->
            zos.setLevel(if (isPng) 3 else 1) // PNG & JPG are already compressed; fast zip framing
            pages.forEachIndexed { idx, page ->
                currentCoroutineContext().ensureActive()
                val step = idx + 1
                onProgress(
                    NotebookExportProgress(
                        fraction = (idx.toFloat() / total) * 0.92f + 0.04f,
                        message = "Exportando página $step de $total (${scale.label} ${ext.uppercase()})…",
                        currentPage = step,
                        totalPages = total
                    )
                )

                val parsed = contentByPageId[page.id] ?: NotebookJsonCodec.decode(page.contentJson)
                val bitmap = renderPageToBitmap(
                    context = context,
                    notebook = notebook,
                    page = page,
                    content = parsed,
                    targetWidthPx = targetW,
                    targetHeightPx = targetH,
                    assetDecodeBudgetPx = decodeBudget,
                    includeBackground = !(isPng && transparentBg)
                )
                try {
                    currentCoroutineContext().ensureActive()
                    val entryName = "${safeBase}_pag_${String.format("%02d", step)}.$ext"
                    zos.putNextEntry(ZipEntry(entryName))
                    if (isPng) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, zos)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, zos)
                    }
                    zos.closeEntry()
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            zos.finish()
        }
        onProgress(NotebookExportProgress(1f, "ZIP completado ($total páginas)", total, total))
    }

    private fun computeRasterDimensions(
        notebook: NotebookEntity,
        scale: NotebookExportScale
    ): Pair<Int, Int> {
        val maxCap = if (MemoryPressureManager.isLowRamDevice) 2400 else 3600
        val desiredLongEdge = (BASE_EXPORT_LONG_EDGE_1X * scale.multiplier).coerceAtMost(maxCap)
        val rawW = notebook.pageWidthPx.coerceAtLeast(1)
        val rawH = notebook.pageHeightPx.coerceAtLeast(1)
        return if (rawH >= rawW) {
            val h = desiredLongEdge
            val w = (h.toFloat() * rawW / rawH).roundToInt().coerceIn(480, maxCap)
            w to h
        } else {
            val w = desiredLongEdge
            val h = (w.toFloat() * rawH / rawW).roundToInt().coerceIn(480, maxCap)
            w to h
        }
    }

    private fun renderPageToBitmap(
        context: Context,
        notebook: NotebookEntity,
        page: NotebookPageEntity,
        content: NotebookPageContent,
        targetWidthPx: Int,
        targetHeightPx: Int,
        assetDecodeBudgetPx: Int,
        includeBackground: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val template = NotebookPageTemplate.fromName(notebook.pageTemplate)
        val bgBitmap = if (includeBackground && page.backgroundAssetPath.isNotBlank()) {
            NotebookAssetStore.loadAssetBitmapSync(context, notebook.id, page.backgroundAssetPath, assetDecodeBudgetPx)
        } else null
        val objectBitmaps = loadPageObjectBitmaps(context, notebook.id, content, assetDecodeBudgetPx)

        NotebookCanvasRenderer.drawPage(
            canvas = canvas,
            widthPx = targetWidthPx.toFloat(),
            heightPx = targetHeightPx.toFloat(),
            template = template,
            backgroundType = NotebookBackgroundType.fromName(page.backgroundType),
            backgroundColorArgb = page.backgroundColor,
            backgroundBitmap = bgBitmap,
            content = content,
            assetBitmapProvider = { rel -> objectBitmaps[rel] },
            typefaceProvider = { fontId -> NotebookFonts.resolveAndroidTypeface(context, fontId) },
            includeBackground = includeBackground
        )
        return bitmap
    }

    private fun loadPageObjectBitmaps(
        context: Context,
        notebookId: Long,
        content: NotebookPageContent,
        decodeBudgetPx: Int
    ): Map<String, Bitmap> {
        val map = HashMap<String, Bitmap>()
        for (obj in content.objects) {
            val relPath = when (obj) {
                is ImageObject -> obj.relativeAssetPath
                is StickerObject -> obj.customAssetPath.orEmpty()
                else -> ""
            }
            if (relPath.isNotBlank() && !map.containsKey(relPath)) {
                NotebookAssetStore.loadAssetBitmapSync(context, notebookId, relPath, decodeBudgetPx)
                    ?.let { map[relPath] = it }
            }
        }
        return map
    }

    fun sanitizeFilename(raw: String): String =
        raw.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "cuaderno_luma" }
}

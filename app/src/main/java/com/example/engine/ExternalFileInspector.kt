package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.DocumentFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ExternalFileInspection(
    val uri: Uri,
    val sanitizedName: String,
    val format: DocumentFormat?,
    val isImage: Boolean,
    val sizeBytes: Long,
    val errorReason: String? = null
) {
    val isSupported: Boolean
        get() = errorReason == null && (isImage || format != null)
}

data class ValidatedDocumentImport(
    val tempFile: File,
    val sanitizedName: String,
    val format: DocumentFormat,
    val sha256Hex: String,
    val sizeBytes: Long
)

/**
 * Security-hardened external file inspector & duplicate detector for "Abrir con Luma" / "Compartir":
 * - Zero broad storage permissions: reads exclusively from the received content/file URI.
 * - Detects real file formats using magic bytes (%PDF, PK\x03\x04 ZIP inner entries for EPUB/DOCX/PPTX,
 *   PNG/JPG/WebP/GIF/BMP image headers, <FictionBook XML for FB2, printable UTF-8/UTF-16 for TXT)
 *   with filename extension as fallback.
 * - Enforces strict file size limits (150 MB documents / 35 MB images) during streaming copy.
 * - Protects against Zip Bombs (entry count cap, uncompressed size cap, compression ratio cap,
 *   and path-traversal `../` rejection).
 * - Computes single-pass streaming SHA-256 content hashes for fast duplicate detection.
 */
object ExternalFileInspector {

    const val MAX_DOCUMENT_BYTES: Long = 150L * 1024L * 1024L // 150 MB
    const val MAX_IMAGE_BYTES: Long = 35L * 1024L * 1024L     // 35 MB

    private const val MAX_ZIP_ENTRIES = 4_000
    private const val MAX_ZIP_UNCOMPRESSED_BYTES = 300L * 1024L * 1024L // 300 MB
    private const val MAX_ZIP_COMPRESSION_RATIO = 100.0
    private const val ZIP_RATIO_CHECK_THRESHOLD_BYTES = 1L * 1024L * 1024L // 1 MB

    private val indexLock = Any()

    /**
     * Sanitizes any untrusted filename from external apps / ContentProviders,
     * removing path separators, `..`, control characters, and capping length.
     */
    fun sanitizeDisplayName(raw: String?, fallback: String = "documento_luma"): String {
        val cleaned = raw
            .orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .replace("..", "_")
            .trim()
        val safe = cleaned.take(96).trim('.')
        return safe.ifBlank { fallback }
    }

    fun sanitizeStorageFileName(raw: String): String {
        val base = sanitizeDisplayName(raw)
        return base.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.')
            .take(80)
            .ifBlank { "documento_luma" }
    }

    /**
     * Lightweight pre-import inspection (runs on Dispatchers.IO):
     * resolves display name, checks advertised size, sniffs magic bytes (and ZIP entries if needed),
     * and returns either a valid format/image classification or a clear Spanish error reason.
     */
    fun inspectUri(context: Context, uri: Uri): ExternalFileInspection {
        val resolver = context.contentResolver
        var rawName: String? = null
        var advertisedSize = -1L

        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) rawName = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) advertisedSize = c.getLong(sizeIdx)
                }
            }
        }

        val displayName = sanitizeDisplayName(rawName ?: uri.lastPathSegment, "archivo_luma")
        val mime = runCatching { resolver.getType(uri) }.getOrNull()?.lowercase().orEmpty()

        // Sniff header magic bytes (up to 4 KB) without loading the file into RAM
        val header = readHeaderBytes(context, uri, 4096)
            ?: return ExternalFileInspection(
                uri = uri,
                sanitizedName = displayName,
                format = null,
                isImage = false,
                sizeBytes = advertisedSize,
                errorReason = "No se pudo acceder al archivo. Verifica los permisos del origen."
            )

        if (header.isEmpty()) {
            return ExternalFileInspection(
                uri = uri,
                sanitizedName = displayName,
                format = null,
                isImage = false,
                sizeBytes = 0L,
                errorReason = "El archivo está vacío."
            )
        }

        // 1. Magic-byte image check
        if (isImageMagic(header)) {
            if (advertisedSize > MAX_IMAGE_BYTES) {
                return ExternalFileInspection(
                    uri = uri,
                    sanitizedName = displayName,
                    format = null,
                    isImage = true,
                    sizeBytes = advertisedSize,
                    errorReason = "La imagen supera el límite de seguridad (${MAX_IMAGE_BYTES / (1024 * 1024)} MB)."
                )
            }
            return ExternalFileInspection(
                uri = uri,
                sanitizedName = displayName,
                format = null,
                isImage = true,
                sizeBytes = advertisedSize
            )
        }

        // 2. Magic-byte document detection (with ZIP inner inspection for PK\x03\x04)
        var detected: DocumentFormat? = detectFormatFromHeader(header)
        if (detected == null && isZipMagic(header)) {
            detected = sniffZipContainerFormat(context, uri)
        }
        // Fallback to extension / MIME if magic bytes were generic text/XML
        if (detected == null) {
            detected = detectFormatFallback(displayName, mime, header)
        }

        if (detected == null) {
            // Secondary image check if MIME explicitly declared an image format (e.g., HEIC/AVIF)
            if (mime.startsWith("image/")) {
                if (advertisedSize > MAX_IMAGE_BYTES) {
                    return ExternalFileInspection(
                        uri = uri,
                        sanitizedName = displayName,
                        format = null,
                        isImage = true,
                        sizeBytes = advertisedSize,
                        errorReason = "La imagen supera el límite de seguridad (${MAX_IMAGE_BYTES / (1024 * 1024)} MB)."
                    )
                }
                return ExternalFileInspection(
                    uri = uri,
                    sanitizedName = displayName,
                    format = null,
                    isImage = true,
                    sizeBytes = advertisedSize
                )
            }
            return ExternalFileInspection(
                uri = uri,
                sanitizedName = displayName,
                format = null,
                isImage = false,
                sizeBytes = advertisedSize,
                errorReason = "Formato no soportado. Luma abre PDF, EPUB, DOCX, PPTX, TXT, FB2 e imágenes PNG/JPG/WebP."
            )
        }

        if (advertisedSize > MAX_DOCUMENT_BYTES) {
            return ExternalFileInspection(
                uri = uri,
                sanitizedName = displayName,
                format = detected,
                isImage = false,
                sizeBytes = advertisedSize,
                errorReason = "El archivo supera el límite máximo permitido (${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB)."
            )
        }

        return ExternalFileInspection(
            uri = uri,
            sanitizedName = displayName,
            format = detected,
            isImage = false,
            sizeBytes = advertisedSize
        )
    }

    /**
     * Streams a received document URI into a temporary file inside `booksDir` while simultaneously:
     * - Enforcing the hard 150 MB byte limit
     * - Computing the SHA-256 content hash in a single pass
     * - Verifying real format via magic bytes + ZIP inner structure
     * - Running Zip Bomb & path-traversal protection on EPUB/DOCX/PPTX archives
     */
    fun copyValidateAndHashDocument(
        context: Context,
        uri: Uri,
        booksDir: File
    ): ValidatedDocumentImport {
        val resolver = context.contentResolver
        var rawName: String? = null
        var advertisedSize = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) rawName = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) advertisedSize = c.getLong(sizeIdx)
                }
            }
        }
        if (advertisedSize > MAX_DOCUMENT_BYTES) {
            throw SecurityException("El archivo supera el límite máximo de ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB.")
        }

        val displayName = sanitizeDisplayName(rawName ?: uri.lastPathSegment, "documento_luma")
        val safeStorageName = sanitizeStorageFileName(displayName)
        val mime = runCatching { resolver.getType(uri) }.getOrNull()?.lowercase().orEmpty()

        booksDir.mkdirs()
        val tempFile = File(booksDir, ".tmp_${System.currentTimeMillis()}_${safeStorageName}")

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        val headerBuf = ByteArray(4096)
        var headerFilled = 0

        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { out ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > MAX_DOCUMENT_BYTES) {
                            throw SecurityException("Archivo demasiado grande (máximo ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB).")
                        }
                        if (headerFilled < headerBuf.size) {
                            val toCopy = minOf(read, headerBuf.size - headerFilled)
                            System.arraycopy(buffer, 0, headerBuf, headerFilled, toCopy)
                            headerFilled += toCopy
                        }
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                    out.fd.sync()
                }
            } ?: error("No se pudo leer el archivo seleccionado.")

            if (totalBytes <= 0L) {
                error("El archivo está vacío.")
            }

            val header = headerBuf.copyOf(headerFilled)
            var format = detectFormatFromHeader(header)

            if (format == null && isZipMagic(header)) {
                format = validateAndClassifyZipFile(tempFile)
            } else if (format == DocumentFormat.EPUB || format == DocumentFormat.DOCX || format == DocumentFormat.PPTX) {
                validateAndClassifyZipFile(tempFile)
            }

            if (format == null) {
                format = detectFormatFallback(displayName, mime, header)
            }

            if (format == null) {
                error("Formato no soportado. Usa PDF, EPUB, DOCX, PPTX, TXT o FB2.")
            }

            // If fallback detected a ZIP-based format, still run zip-bomb guard!
            if ((format == DocumentFormat.EPUB || format == DocumentFormat.DOCX || format == DocumentFormat.PPTX) &&
                isZipMagic(header)
            ) {
                validateZipSafety(tempFile)
            }

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            return ValidatedDocumentImport(
                tempFile = tempFile,
                sanitizedName = displayName,
                format = format,
                sha256Hex = shaHex,
                sizeBytes = totalBytes
            )
        } catch (t: Throwable) {
            runCatching { tempFile.delete() }
            throw t
        }
    }

    /**
     * Checks whether a ZIP file is safe against Zip Bombs and path traversal, and classifies
     * whether it is an EPUB, DOCX, or PPTX archive.
     */
    private fun validateAndClassifyZipFile(file: File): DocumentFormat? {
        var hasEpubMime = false
        var hasEpubContainer = false
        var hasDocx = false
        var hasPptx = false

        ZipFile(file).use { zip ->
            var entryCount = 0
            var totalUncompressed = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw SecurityException("Archivo ZIP rechazado: demasiadas entradas internas (protección zip bomb).")
                }
                checkEntrySafety(entry)

                val uncomp = entry.size
                val comp = entry.compressedSize
                if (uncomp > 0) {
                    totalUncompressed += uncomp
                    if (totalUncompressed > MAX_ZIP_UNCOMPRESSED_BYTES) {
                        throw SecurityException("Archivo comprimido sospechosamente grande al descomprimir (protección zip bomb).")
                    }
                    if (uncomp > ZIP_RATIO_CHECK_THRESHOLD_BYTES && comp > 0) {
                        val ratio = uncomp.toDouble() / comp.toDouble()
                        if (ratio > MAX_ZIP_COMPRESSION_RATIO) {
                            throw SecurityException("Ratio de compresión anormal detectado (protección zip bomb).")
                        }
                    }
                }

                val name = entry.name.lowercase()
                when {
                    name == "mimetype" -> {
                        val text = runCatching {
                            zip.getInputStream(entry).use { it.readNBytesCompat(64).decodeToString().trim() }
                        }.getOrDefault("")
                        if (text.contains("epub+zip", ignoreCase = true)) hasEpubMime = true
                    }
                    name == "meta-inf/container.xml" -> hasEpubContainer = true
                    name == "word/document.xml" -> hasDocx = true
                    name == "ppt/presentation.xml" -> hasPptx = true
                }
            }
        }

        return when {
            hasEpubMime || hasEpubContainer -> DocumentFormat.EPUB
            hasDocx -> DocumentFormat.DOCX
            hasPptx -> DocumentFormat.PPTX
            else -> null
        }
    }

    private fun validateZipSafety(file: File) {
        ZipFile(file).use { zip ->
            var entryCount = 0
            var totalUncompressed = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw SecurityException("Archivo ZIP rechazado (protección zip bomb).")
                }
                checkEntrySafety(entry)
                val uncomp = entry.size
                val comp = entry.compressedSize
                if (uncomp > 0) {
                    totalUncompressed += uncomp
                    if (totalUncompressed > MAX_ZIP_UNCOMPRESSED_BYTES) {
                        throw SecurityException("Archivo comprimido excede el límite seguro al descomprimir.")
                    }
                    if (uncomp > ZIP_RATIO_CHECK_THRESHOLD_BYTES && comp > 0 &&
                        (uncomp.toDouble() / comp.toDouble()) > MAX_ZIP_COMPRESSION_RATIO
                    ) {
                        throw SecurityException("Ratio de compresión peligroso detectado (protección zip bomb).")
                    }
                }
            }
        }
    }

    private fun checkEntrySafety(entry: ZipEntry) {
        val name = entry.name
        if (name.startsWith("/") || name.startsWith("\\") || name.contains("../") || name.contains("..\\")) {
            throw SecurityException("Ruta inválida dentro del archivo comprimido.")
        }
    }

    /**
     * Quick streaming ZIP container sniff (without writing to disk) used only during pre-import inspection.
     */
    private fun sniffZipContainerFormat(context: Context, uri: Uri): DocumentFormat? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var checked = 0
                    while (checked < 120) {
                        val entry = zis.nextEntry ?: break
                        checked++
                        val name = entry.name.lowercase()
                        when (name) {
                            "mimetype" -> {
                                val data = zis.readNBytesCompat(64).decodeToString().trim()
                                if (data.contains("epub+zip", ignoreCase = true)) return@use DocumentFormat.EPUB
                            }
                            "meta-inf/container.xml" -> return@use DocumentFormat.EPUB
                            "word/document.xml" -> return@use DocumentFormat.DOCX
                            "ppt/presentation.xml" -> return@use DocumentFormat.PPTX
                        }
                    }
                    null
                }
            }
        }.getOrNull()
    }

    private fun detectFormatFromHeader(header: ByteArray): DocumentFormat? {
        // %PDF-
        if (header.size >= 4 &&
            header[0] == 0x25.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() &&
            header[3] == 0x46.toByte()
        ) {
            return DocumentFormat.PDF
        }

        // XML FictionBook2 (<FictionBook)
        val preview = runCatching { header.decodeToString() }.getOrDefault("")
        if (preview.contains("<FictionBook", ignoreCase = true)) {
            return DocumentFormat.FB2
        }

        return null
    }

    private fun detectFormatFallback(displayName: String, mime: String, header: ByteArray): DocumentFormat? {
        // Never misclassify binary executables / unknown binaries as TXT
        val byExt = DocumentFormat.fromFileName(displayName)
        if (byExt == DocumentFormat.TXT) {
            return if (looksLikeText(header)) DocumentFormat.TXT else null
        }
        if (byExt != null) return byExt

        return when (mime) {
            "application/pdf" -> DocumentFormat.PDF
            "application/epub+zip" -> DocumentFormat.EPUB
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocumentFormat.DOCX
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> DocumentFormat.PPTX
            "application/x-fictionbook+xml" -> DocumentFormat.FB2
            "text/plain" -> if (looksLikeText(header)) DocumentFormat.TXT else null
            else -> null
        }
    }

    private fun isZipMagic(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()

    private fun isImageMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        // PNG: 89 50 4E 47
        if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
        ) return true
        // JPEG: FF D8 FF
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return true
        // WebP: RIFF....WEBP
        if (header.size >= 12 &&
            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            header[8] == 0x57.toByte() && header[9] == 0x45.toByte() &&
            header[10] == 0x42.toByte() && header[11] == 0x50.toByte()
        ) return true
        // GIF8
        if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte()
        ) return true
        // BMP: 42 4D
        if (header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) return true
        return false
    }

    private fun looksLikeText(header: ByteArray): Boolean {
        if (header.isEmpty()) return true
        // UTF-16 BOM
        if (header.size >= 2 &&
            ((header[0] == 0xFF.toByte() && header[1] == 0xFE.toByte()) ||
                (header[0] == 0xFE.toByte() && header[1] == 0xFF.toByte()))
        ) return true
        var controlCount = 0
        for (b in header) {
            val v = b.toInt() and 0xFF
            if (v == 0) return false // Null byte -> binary file
            if (v < 0x09 || (v in 0x0E..0x1F)) controlCount++
        }
        return controlCount * 20 < header.size
    }

    private fun readHeaderBytes(context: Context, uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readNBytesCompat(maxBytes)
        }
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(maxBytes: Int): ByteArray {
        val out = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val r = read(out, offset, maxBytes - offset)
            if (r <= 0) break
            offset += r
        }
        return if (offset == maxBytes) out else out.copyOf(offset)
    }

    // ---- Lightweight Persistent SHA-256 Duplicate Index (zero Room schema changes!) ----

    private fun hashIndexFile(context: Context): File =
        File(File(context.filesDir, "books").apply { mkdirs() }, ".sha256_index.properties")

    fun computeFileSha256(file: File): String? = runCatching {
        if (!file.exists() || !file.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun findDuplicateDocumentId(
        context: Context,
        sha256Hex: String,
        existingDocs: List<Pair<Long, String>>
    ): Long? {
        synchronized(indexLock) {
            val idxFile = hashIndexFile(context)
            val props = Properties()
            if (idxFile.exists()) {
                runCatching { FileInputStream(idxFile).use { props.load(it) } }
            }

            // Fast O(1) lookup first
            val cachedId = props.getProperty(sha256Hex)?.toLongOrNull()
            if (cachedId != null && existingDocs.any { it.first == cachedId && File(it.second).exists() }) {
                return cachedId
            }

            // Lazily backfill any existing books not yet hashed in the index
            val indexedIds = props.values.mapNotNull { it.toString().toLongOrNull() }.toHashSet()
            var dirty = false
            var matchedId: Long? = null
            for ((docId, filePath) in existingDocs) {
                if (docId in indexedIds) continue
                val f = File(filePath)
                if (!f.exists()) continue
                val h = computeFileSha256(f) ?: continue
                props.setProperty(h, docId.toString())
                dirty = true
                if (h == sha256Hex && matchedId == null) {
                    matchedId = docId
                }
            }
            if (dirty) {
                runCatching { FileOutputStream(idxFile).use { props.store(it, "Luma SHA-256 dedupe index") } }
            }
            return matchedId
        }
    }

    fun recordDocumentHash(context: Context, sha256Hex: String, documentId: Long) {
        synchronized(indexLock) {
            val idxFile = hashIndexFile(context)
            val props = Properties()
            if (idxFile.exists()) {
                runCatching { FileInputStream(idxFile).use { props.load(it) } }
            }
            props.setProperty(sha256Hex, documentId.toString())
            runCatching { FileOutputStream(idxFile).use { props.store(it, "Luma SHA-256 dedupe index") } }
        }
    }
}

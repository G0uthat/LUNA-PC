package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.DocumentEntity
import com.example.data.local.FolioRepository
import com.example.data.model.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleLibraryGenerator {

    const val CURRENT_SAMPLE_VERSION = 2

    suspend fun ensureSampleDocuments(context: Context, repository: FolioRepository) =
        withContext(Dispatchers.IO) {
            val existingDocs = repository.getDocumentsForCoverCheck()
            val existingFormats = existingDocs.map { it.format.uppercase() }.toSet()
            val existingFileNames = existingDocs.map { File(it.filePath).name }.toSet()
            val booksDir = File(context.filesDir, "books").apply { mkdirs() }
            val now = System.currentTimeMillis()

            var epubId: Long? = existingDocs.firstOrNull { it.format.equals("EPUB", true) }?.id
            var manhwaId: Long? = existingDocs.firstOrNull { File(it.filePath).name == "shadow_blade_manhwa_ep1.pdf" }?.id
            var docxId: Long? = existingDocs.firstOrNull { it.format.equals("DOCX", true) }?.id
            var pdfId: Long? = existingDocs.firstOrNull { File(it.filePath).name == "guia_estudio_visual.pdf" }?.id
            var pptxId: Long? = existingDocs.firstOrNull { it.format.equals("PPTX", true) }?.id
            var txtId: Long? = existingDocs.firstOrNull { it.format.equals("TXT", true) }?.id
            var fb2Id: Long? = existingDocs.firstOrNull { it.format.equals("FB2", true) }?.id

            // 1. Real EPUB 3 Book (Temas suaves, tipografía, diccionario y subrayado)
            if (DocumentFormat.EPUB.name !in existingFormats) {
                val epubFile = File(booksDir, "el_atlas_de_la_lectura.epub")
                createSampleEpub(epubFile)
                val epubParsed = DocumentParser.parse(context, epubFile, DocumentFormat.EPUB)
                val epubCover = CoverManager.generateOrExtractCover(
                    context, epubFile, DocumentFormat.EPUB, epubParsed.title, epubParsed.author, 0xFF2B64F5
                )
                epubId = repository.insertDocument(
                    DocumentEntity(
                        title = epubParsed.title,
                        author = epubParsed.author,
                        format = DocumentFormat.EPUB.name,
                        filePath = epubFile.absolutePath,
                        coverImagePath = epubCover,
                        totalUnits = epubParsed.chapters.size,
                        currentUnit = 0,
                        progressPercent = 0.25f,
                        totalWords = epubParsed.totalWords,
                        coverColorHex = 0xFF2B64F5,
                        badgeTag = "EPUB",
                        lastOpenedAt = now
                    )
                )
            }

            // 2. Real Image-Based Manhwa PDF in English (OCR + Traducción instantánea)
            if ("shadow_blade_manhwa_ep1.pdf" !in existingFileNames && DocumentFormat.PDF.name !in existingFormats) {
                val manhwaFile = File(booksDir, "shadow_blade_manhwa_ep1.pdf")
                createSampleManhwaImagePdf(manhwaFile)
                val manhwaParsed = DocumentParser.parse(context, manhwaFile, DocumentFormat.PDF)
                val manhwaCover = CoverManager.generateOrExtractCover(
                    context, manhwaFile, DocumentFormat.PDF, "Shadow Blade · Ep. 1", "Studio Han", 0xFFE11D48
                )
                manhwaId = repository.insertDocument(
                    DocumentEntity(
                        title = "Shadow Blade · Ep. 1",
                        author = "Studio Han",
                        format = DocumentFormat.PDF.name,
                        filePath = manhwaFile.absolutePath,
                        coverImagePath = manhwaCover,
                        totalUnits = manhwaParsed.pdfPageCount,
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = 180,
                        coverColorHex = 0xFFE11D48,
                        badgeTag = "Manhwa OCR",
                        lastOpenedAt = now - 1_000
                    )
                )
            }

            // 3. Real PowerPoint (.pptx) Presentation 16:9 (Formato PPTX original <-> Modo EPUB + Firma/Anotación)
            if (DocumentFormat.PPTX.name !in existingFormats) {
                val pptxFile = File(booksDir, "presentacion_luma_trabajo.pptx")
                createSamplePptx(pptxFile)
                val pptxParsed = DocumentParser.parse(context, pptxFile, DocumentFormat.PPTX)
                val pptxCover = CoverManager.generateOrExtractCover(
                    context, pptxFile, DocumentFormat.PPTX, pptxParsed.title, pptxParsed.author, 0xFFD97706
                )
                pptxId = repository.insertDocument(
                    DocumentEntity(
                        title = pptxParsed.title,
                        author = pptxParsed.author,
                        format = DocumentFormat.PPTX.name,
                        filePath = pptxFile.absolutePath,
                        coverImagePath = pptxCover,
                        totalUnits = pptxParsed.slides.size.coerceAtLeast(1),
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = pptxParsed.totalWords,
                        coverColorHex = 0xFFD97706,
                        badgeTag = "PPTX 16:9",
                        lastOpenedAt = now - 1_500
                    )
                )
            }

            // 4. Real DOCX Document with A4 Page Dimensions & Signature Block (Hoja A4 + Firma)
            if (DocumentFormat.DOCX.name !in existingFormats) {
                val docxFile = File(booksDir, "cuaderno_arquitectura_minimalista.docx")
                createSampleDocx(docxFile)
                val docxParsed = DocumentParser.parse(context, docxFile, DocumentFormat.DOCX)
                val docxCover = CoverManager.generateOrExtractCover(
                    context, docxFile, DocumentFormat.DOCX, docxParsed.title, docxParsed.author, 0xFF0D9488
                )
                docxId = repository.insertDocument(
                    DocumentEntity(
                        title = docxParsed.title,
                        author = docxParsed.author,
                        format = DocumentFormat.DOCX.name,
                        filePath = docxFile.absolutePath,
                        coverImagePath = docxCover,
                        totalUnits = docxParsed.chapters.size,
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = docxParsed.totalWords,
                        coverColorHex = 0xFF0D9488,
                        badgeTag = "DOCX · A4",
                        lastOpenedAt = now - 2_000
                    )
                )
            }

            // 5. Real Vector Study PDF (Zoom, dibujo a mano alzada y exportación fusionada)
            if ("guia_estudio_visual.pdf" !in existingFileNames && existingDocs.isEmpty()) {
                val pdfFile = File(booksDir, "guia_estudio_visual.pdf")
                createSampleStudyPdf(pdfFile)
                val pdfParsed = DocumentParser.parse(context, pdfFile, DocumentFormat.PDF)
                val pdfCover = CoverManager.generateOrExtractCover(
                    context, pdfFile, DocumentFormat.PDF, "Guía de Estudio Visual", "Taller Luma", 0xFF4F46E5
                )
                pdfId = repository.insertDocument(
                    DocumentEntity(
                        title = "Guía de Estudio Visual",
                        author = "Taller Luma",
                        format = DocumentFormat.PDF.name,
                        filePath = pdfFile.absolutePath,
                        coverImagePath = pdfCover,
                        totalUnits = pdfParsed.pdfPageCount,
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = pdfParsed.totalWords,
                        coverColorHex = 0xFF4F46E5,
                        badgeTag = "PDF",
                        lastOpenedAt = now - 3_000
                    )
                )
            }

            // 6. Real TXT Document (Lectura rápida, búsqueda y cambio a formato hoja)
            if (DocumentFormat.TXT.name !in existingFormats) {
                val txtFile = File(booksDir, "notas_rapidas_luma.txt")
                createSampleTxt(txtFile)
                val txtParsed = DocumentParser.parse(context, txtFile, DocumentFormat.TXT)
                val txtCover = CoverManager.generateOrExtractCover(
                    context, txtFile, DocumentFormat.TXT, "Notas Rápidas de Lectura", "Guía Práctica Luma", 0xFF6366F1
                )
                txtId = repository.insertDocument(
                    DocumentEntity(
                        title = "Notas Rápidas de Lectura",
                        author = "Guía Práctica Luma",
                        format = DocumentFormat.TXT.name,
                        filePath = txtFile.absolutePath,
                        coverImagePath = txtCover,
                        totalUnits = txtParsed.chapters.size.coerceAtLeast(1),
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = txtParsed.totalWords,
                        coverColorHex = 0xFF6366F1,
                        badgeTag = "TXT",
                        lastOpenedAt = now - 4_000
                    )
                )
            }

            // 7. Real FB2 FictionBook 2 Short Story (Novela corta, modo nocturno y marcadores)
            if (DocumentFormat.FB2.name !in existingFormats) {
                val fb2File = File(booksDir, "cronicas_del_observatorio.fb2")
                createSampleFb2(fb2File)
                val fb2Parsed = DocumentParser.parse(context, fb2File, DocumentFormat.FB2)
                val fb2Cover = CoverManager.generateOrExtractCover(
                    context, fb2File, DocumentFormat.FB2, fb2Parsed.title, fb2Parsed.author, 0xFF9333EA
                )
                fb2Id = repository.insertDocument(
                    DocumentEntity(
                        title = fb2Parsed.title,
                        author = fb2Parsed.author,
                        format = DocumentFormat.FB2.name,
                        filePath = fb2File.absolutePath,
                        coverImagePath = fb2Cover,
                        totalUnits = fb2Parsed.chapters.size.coerceAtLeast(1),
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = fb2Parsed.totalWords,
                        coverColorHex = 0xFF9333EA,
                        badgeTag = "FB2",
                        lastOpenedAt = now - 5_000
                    )
                )
            }

            // Seed Spotify-style book playlists / sagas on first run
            if (repository.getPlaylistCount() == 0) {
                val p1 = repository.createPlaylist(
                    name = "Sagas y Manhwa",
                    description = "Cómics, novelas cortas y capítulos en curso",
                    accentColorHex = 0xFFE11D48
                )
                manhwaId?.let { repository.addBookToPlaylist(p1, it) }
                fb2Id?.let { repository.addBookToPlaylist(p1, it) }

                val p2 = repository.createPlaylist(
                    name = "Lectura y Estudio",
                    description = "Libros EPUB, apuntes TXT y guías",
                    accentColorHex = 0xFF2B64F5
                )
                epubId?.let { repository.addBookToPlaylist(p2, it) }
                txtId?.let { repository.addBookToPlaylist(p2, it) }
                pdfId?.let { repository.addBookToPlaylist(p2, it) }

                val p3 = repository.createPlaylist(
                    name = "Trabajo y Documentos",
                    description = "Presentaciones PPTX y hojas A4 listas para firmar",
                    accentColorHex = 0xFFD97706
                )
                pptxId?.let { repository.addBookToPlaylist(p3, it) }
                docxId?.let { repository.addBookToPlaylist(p3, it) }
            }
        }

    private fun createSampleEpub(target: File) {
        val chapters = listOf(
            Triple(
                "ch1.xhtml",
                "I. La arquitectura del silencio lector",
                """
                <h1>I. La arquitectura del silencio lector</h1>
                <p>Leer profundamente en una pantalla exige diseñar un refugio contra la fragmentación. Cuando desaparecen las barras innecesarias, la mente recupera su ritmo natural de atención sostenida y cada párrafo respira como en una página impresa.</p>
                <p>El concepto de lectura inmersiva nace de una idea sencilla: las herramientas poderosas deben permanecer ocultas detrás de un gesto sutil, como un marcapáginas que descansa al borde de la hoja y solo despliega su utilidad cuando el lector decide llamarlo.</p>
                <blockquote>«Un buen libro electrónico no compite por tu mirada; sostiene el texto con la misma discreción con la que una mesa sostiene un cuaderno abierto.»</blockquote>
                <p>En esta edición interactiva puedes mantener pulsada cualquier palabra —como bibliografía, mnemotecnia, serendipia o cartografía— para consultar Wikipedia, la RAE o WordReference en un instante sin perder la línea donde estabas leyendo.</p>
                """.trimIndent()
            ),
            Triple(
                "ch2.xhtml",
                "II. Tipografía, ritmo y memoria visual",
                """
                <h1>II. Tipografía, ritmo y memoria visual</h1>
                <p>La elección tipográfica modifica silenciosamente la velocidad de comprensión y la fatiga ocular. Las fuentes serif contemporáneas como Literata guían el movimiento horizontal de los ojos a lo largo de líneas extensas, mientras que familias humanistas como Lexend amplían la claridad entre caracteres para favorecer la fluidez lectora.</p>
                <h2>Ajustes que transforman la página</h2>
                <p>El interlineado generoso evita que la mirada salte por error a la línea equivocada. Los márgenes amplios crean un marco de reposo periférico y los temas cálidos como Sepia o el negro puro AMOLED protegen la vista durante sesiones nocturnas de estudio.</p>
                <p>Prueba subrayar este fragmento en azul cobalto o amarillo ámbar, añadir una nota personal y abrir el menú marcapáginas para activar el lienzo de dibujo a mano alzada sobre el texto.</p>
                """.trimIndent()
            ),
            Triple(
                "ch3.xhtml",
                "III. El arte de anotar en los márgenes",
                """
                <h1>III. El arte de anotar en los márgenes</h1>
                <p>Durante siglos, los eruditos convirtieron sus volúmenes en conversaciones vivas mediante la marginalia: flechas, llaves, definiciones rápidas y esquemas trazados directamente sobre las páginas. Anotar no ensucia un libro; lo transforma en memoria externa.</p>
                <p>Cuando combinas trazos libres con texto digital seleccionable, cada diagrama dibujado con el dedo o el lápiz óptico queda vinculado a su capítulo exacto. Al exportar el libro fusionado, tus dibujos permanecen superpuestos como gráficos vectoriales mientras las palabras siguen siendo completamente seleccionables.</p>
                <blockquote>«Leer sin dialogar con el texto es pasear por una biblioteca sin abrir las ventanas.»</blockquote>
                <p>Utiliza el buscador interno del marcapáginas para localizar términos como marginalia, tipografía o serendipia en todos los capítulos de este volumen.</p>
                """.trimIndent()
            )
        )

        ZipOutputStream(FileOutputStream(target)).use { zos ->
            // 1. Uncompressed mimetype first (EPUB 3 requirement)
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // 2. META-INF/container.xml
            writeZipText(
                zos, "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
            )

            // 3. OEBPS/content.opf
            val manifestItems = chapters.mapIndexed { idx, (file, _, _) ->
                """    <item id="ch${idx + 1}" href="$file" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n")
            val spineItems = chapters.indices.joinToString("\n") { idx ->
                """    <itemref idref="ch${idx + 1}"/>"""
            }
            writeZipText(
                zos, "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">folio-sample-atlas-001</dc:identifier>
    <dc:title>El Atlas de la Lectura Profunda</dc:title>
    <dc:creator>Clara Valdés</dc:creator>
    <dc:language>es</dc:language>
  </metadata>
  <manifest>
$manifestItems
  </manifest>
  <spine>
$spineItems
  </spine>
</package>"""
            )

            // 4. XHTML Chapters
            for ((fileName, chTitle, bodyHtml) in chapters) {
                val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="es">
<head>
  <meta charset="UTF-8"/>
  <title>$chTitle</title>
  <style>body{font-family:serif;line-height:1.65;margin:1.5em;color:#151C2B;} mark{background:#FFF0A6;}</style>
</head>
<body>
$bodyHtml
</body>
</html>"""
                writeZipText(zos, "OEBPS/$fileName", xhtml)
            }
        }
    }

    /**
     * Generates a REAL image-only Manhwa PDF (rasterized Bitmap panels embedded in PDF pages)
     * with crisp English speech bubbles so ML Kit OCR, EN->ES word long-press translation,
     * full-page Manhwa translation, and merged PDF export with invisible selectable OCR text work immediately!
     */
    private fun createSampleManhwaImagePdf(target: File) {
        val pdfDoc = PdfDocument()
        val pageWidth = 600
        val pageHeight = 960

        val scenes = listOf(
            ManhwaPageSpec(
                episodeTag = "EPISODE 01 · THE NORTHERN GATE",
                topPanelBg = intArrayOf(0xFF0F172A.toInt(), 0xFF1E3A8A.toInt()),
                bottomPanelBg = intArrayOf(0xFF1E1B4B.toInt(), 0xFF312E81.toInt()),
                bubbles = listOf(
                    BubbleSpec(52f, 110f, 340f, 225f, listOf("WAIT! DO NOT", "OPEN THAT", "ANCIENT GATE!")),
                    BubbleSpec(230f, 330f, 548f, 445f, listOf("THE SHADOW BLADE", "IS AWAKENING", "INSIDE THE TOWER!")),
                    BubbleSpec(55f, 560f, 370f, 675f, listOf("WE MUST PROTECT", "THE VILLAGE", "BEFORE DAWN.")),
                    BubbleSpec(235f, 765f, 545f, 880f, listOf("FOLLOW ME, HURRY!", "THERE IS NO", "TIME TO WASTE!"))
                )
            ),
            ManhwaPageSpec(
                episodeTag = "EPISODE 01 · AWAKENING",
                topPanelBg = intArrayOf(0xFF18181B.toInt(), 0xFF7F1D1D.toInt()),
                bottomPanelBg = intArrayOf(0xFF0C4A6E.toInt(), 0xFF0F172A.toInt()),
                bubbles = listOf(
                    BubbleSpec(60f, 120f, 375f, 235f, listOf("LOOK AT THE SKY!", "BLUE LIGHTNING", "IS FALLING!")),
                    BubbleSpec(210f, 345f, 545f, 455f, listOf("ONLY A TRUE", "GUARDIAN CAN", "HOLD THIS POWER.")),
                    BubbleSpec(55f, 575f, 385f, 690f, listOf("I WILL NOT RUN", "AWAY ANYMORE.", "THIS IS MY FIGHT!")),
                    BubbleSpec(225f, 775f, 545f, 885f, listOf("TO BE CONTINUED", "IN THE NEXT", "CHAPTER..."))
                )
            )
        )

        scenes.forEachIndexed { idx, spec ->
            // Render entire manhwa page to a pure raster Bitmap first (no native PDF text!)
            val bmp = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawManhwaArtwork(canvas, pageWidth.toFloat(), pageHeight.toFloat(), spec)

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            page.canvas.drawBitmap(bmp, 0f, 0f, null)
            pdfDoc.finishPage(page)
            bmp.recycle()
        }

        FileOutputStream(target).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }

    private data class BubbleSpec(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val lines: List<String>
    )

    private data class ManhwaPageSpec(
        val episodeTag: String,
        val topPanelBg: IntArray,
        val bottomPanelBg: IntArray,
        val bubbles: List<BubbleSpec>
    )

    private fun drawManhwaArtwork(canvas: Canvas, w: Float, h: Float, spec: ManhwaPageSpec) {
        // Dark manga page gutter background
        canvas.drawColor(Color.parseColor("#0B101B"))

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        // Top dramatic action panel
        val topRect = RectF(24f, 68f, w - 24f, h * 0.49f)
        panelPaint.shader = LinearGradient(
            0f, topRect.top, w, topRect.bottom,
            spec.topPanelBg[0], spec.topPanelBg[1], Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(topRect, 14f, 14f, panelPaint)
        drawSpeedLines(canvas, topRect, Color.argb(45, 255, 255, 255))
        drawHeroSilhouette(canvas, topRect.right - 140f, topRect.bottom - 18f, 1.0f)
        canvas.drawRoundRect(topRect, 14f, 14f, borderPaint)

        // Bottom dramatic panel
        val bottomRect = RectF(24f, h * 0.52f, w - 24f, h - 28f)
        panelPaint.shader = LinearGradient(
            0f, bottomRect.top, w, bottomRect.bottom,
            spec.bottomPanelBg[0], spec.bottomPanelBg[1], Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bottomRect, 14f, 14f, panelPaint)
        drawSpeedLines(canvas, bottomRect, Color.argb(38, 120, 180, 255))
        drawGlowingSword(canvas, bottomRect.left + 120f, bottomRect.bottom - 20f)
        canvas.drawRoundRect(bottomRect, 14f, 14f, borderPaint)

        // Episode header banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(spec.episodeTag, 28f, 44f, headerPaint)

        // Crisp white comic speech bubbles with high-contrast English lettering for ML Kit OCR
        val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (bubble in spec.bubbles) {
            val rect = RectF(bubble.left, bubble.top, bubble.right, bubble.bottom)
            canvas.drawRoundRect(rect, 34f, 34f, bubbleFill)
            canvas.drawRoundRect(rect, 34f, 34f, bubbleStroke)

            val centerX = rect.centerX()
            val lineHeight = 30f
            val totalHeight = bubble.lines.size * lineHeight
            var y = rect.centerY() - totalHeight / 2f + 22f
            for (line in bubble.lines) {
                canvas.drawText(line, centerX, y, textPaint)
                y += lineHeight
            }
        }
    }

    private fun drawSpeedLines(canvas: Canvas, rect: RectF, lineColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = 2.5f
        }
        canvas.save()
        canvas.clipRect(rect)
        val cx = rect.centerX()
        val cy = rect.centerY()
        for (i in 0 until 24) {
            val angle = Math.toRadians(i * 15.0)
            val x1 = cx + (Math.cos(angle) * 90).toFloat()
            val y1 = cy + (Math.sin(angle) * 90).toFloat()
            val x2 = cx + (Math.cos(angle) * 420).toFloat()
            val y2 = cy + (Math.sin(angle) * 420).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        canvas.restore()
    }

    private fun drawHeroSilhouette(canvas: Canvas, baseX: Float, baseY: Float, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#090D16")
            style = Paint.Style.FILL
        }
        val cape = Path().apply {
            moveTo(baseX - 70f * scale, baseY)
            quadTo(baseX - 30f * scale, baseY - 120f * scale, baseX, baseY - 150f * scale)
            quadTo(baseX + 45f * scale, baseY - 110f * scale, baseX + 85f * scale, baseY)
            close()
        }
        canvas.drawPath(cape, paint)
        canvas.drawCircle(baseX, baseY - 170f * scale, 24f * scale, paint)
        // Glowing eyes
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#60A5FA") }
        canvas.drawCircle(baseX - 7f, baseY - 170f, 3.5f, eyePaint)
        canvas.drawCircle(baseX + 7f, baseY - 170f, 3.5f, eyePaint)
    }

    private fun drawGlowingSword(canvas: Canvas, baseX: Float, baseY: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 56, 189, 248)
            strokeWidth = 22f
            strokeCap = Paint.Cap.ROUND
        }
        val blade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F2FE")
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(baseX - 40f, baseY, baseX + 115f, baseY - 220f, glow)
        canvas.drawLine(baseX - 40f, baseY, baseX + 115f, baseY - 220f, blade)
    }

    private fun createSampleDocx(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            writeZipText(
                zos, "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""
            )
            writeZipText(
                zos, "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""
            )
            writeZipText(
                zos, "docProps/core.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <dc:title>Cuaderno de Arquitectura Minimalista</dc:title>
  <dc:creator>Martín Soler</dc:creator>
</cp:coreProperties>"""
            )
            writeZipText(
                zos, "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>1. Principios de Diseño Táctil Minimalista</w:t></w:r></w:p>
    <w:p><w:r><w:t>El minimalismo táctil une dos mundos aparentemente opuestos: la calma silenciosa de las superficies limpias y la satisfacción física de los controles con profundidad. Cada botón transmite su intención antes de ser presionado.</w:t></w:r></w:p>
    <w:p><w:r><w:t>Este archivo DOCX real conserva su hoja A4 original en Luma. Usa el interruptor del menú para alternar entre Formato DOCX (hoja A4 exacta) y Modo EPUB (texto fluido adaptado a tu pantalla).</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>2. Revisión Profesional y Firma Rápida (A4)</w:t></w:r></w:p>
    <w:p><w:r><w:t>Cuando revisas contratos, informes o cuadernos de trabajo en formato Word, activa el botón Dibujar y pulsa Firma para estampar tu rúbrica directamente sobre la hoja A4 sin imprimir papel.</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Bloque de Conformidad y Firma</w:t></w:r></w:p>
    <w:p><w:r><w:t>Confirmo haber revisado este documento técnico y apruebo su exportación con anotaciones integradas.</w:t></w:r></w:p>
    <w:p><w:r><w:t>Firma del responsable: _______________________________</w:t></w:r></w:p>
    <w:p><w:r><w:t>Fecha y observaciones: _______________________________</w:t></w:r></w:p>
    <w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr>
  </w:body>
</w:document>"""
            )
        }
    }

    private fun createSampleStudyPdf(target: File) {
        val pdfDoc = PdfDocument()
        val width = 595
        val height = 842

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D5BFF")
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 14f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#151C2B")
            textSize = 15f
        }
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEF4FF")
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BFD4FF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val pages = listOf(
            Pair(
                "Guía de Estudio Visual",
                listOf(
                    "Página 1 · Fundamentos del enfoque en pantalla",
                    "",
                    "Este es un documento PDF vectorial con texto nativo seleccionable.",
                    "Puedes hacer pellizco para ampliar (pinch-to-zoom), activar el modo",
                    "de dibujo libre encima de los diagramas y exportar el archivo final",
                    "fusionando tus trazos vectoriales con el texto intacto.",
                    "",
                    "1. Reduce la carga cognitiva ocultando barras fijas durante la lectura.",
                    "2. Usa el marcapáginas lateral para alternar herramientas en un toque.",
                    "3. Mantén pulsada cualquier palabra con OCR activo para traducirla.",
                    "4. Exporta a PDF conservando el texto normal 100% seleccionable."
                )
            ),
            Pair(
                "Esquema de Memoria Activa",
                listOf(
                    "Página 2 · Anotación y repaso espaciado",
                    "",
                    "El dibujo a mano alzada activa regiones motoras y visuales que",
                    "refuerzan la consolidación de conceptos complejos en ingeniería,",
                    "medicina, idiomas y arquitectura de software.",
                    "",
                    "Prueba usar el resaltador translúcido sobre estas líneas y el lápiz",
                    "cobalto en el recuadro inferior para hacer un diagrama rápido.",
                    "Luego cierra el archivo y vuelve a abrirlo: tus trazos seguirán",
                    "exactamente en esta página y se incluirán al exportar el PDF."
                )
            )
        )

        pages.forEachIndexed { idx, (heading, lines) ->
            val info = PdfDocument.PageInfo.Builder(width, height, idx + 1).create()
            val page = pdfDoc.startPage(info)
            val c = page.canvas
            c.drawColor(Color.WHITE)

            // Cobalt top accent bar
            val barPaint = Paint().apply { color = Color.parseColor("#1D5BFF") }
            c.drawRect(0f, 0f, width.toFloat(), 14f, barPaint)

            c.drawText(heading, 48f, 76f, titlePaint)
            c.drawText("Taller Editorial Folio · Documento PDF de prueba", 48f, 102f, subtitlePaint)

            var y = 148f
            for (line in lines) {
                if (line.isNotEmpty()) {
                    c.drawText(line, 48f, y, bodyPaint)
                }
                y += 26f
            }

            // Sketch workspace box
            val box = RectF(48f, 480f, width - 48f, 740f)
            c.drawRoundRect(box, 18f, 18f, cardFill)
            c.drawRoundRect(box, 18f, 18f, cardBorder)
            c.drawText("Zona de anotación libre y diagramas", 72f, 518f, titlePaint.apply { textSize = 16f })
            titlePaint.textSize = 26f
            c.drawText("Activa el lápiz en el marcapáginas y dibuja aquí encima.", 72f, 546f, subtitlePaint)

            c.drawText("Página ${idx + 1} de ${pages.size}", width - 140f, height - 36f, subtitlePaint)
            pdfDoc.finishPage(page)
        }

        FileOutputStream(target).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }

    /**
     * Generates a genuine OOXML PowerPoint (.pptx) 16:9 widescreen presentation with real
     * theme colors, positioned DrawingML shapes, rounded cards, bullet points, a styled table,
     * and speaker notes — rendered natively by PptxDocumentEngine.
     */
    private fun createSamplePptx(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            writeZipText(
                zos, "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide4.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/notesSlides/notesSlide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""
            )

            writeZipText(
                zos, "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""
            )

            writeZipText(
                zos, "docProps/core.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <dc:title>Presentación Luma · Trabajo y Estudio</dc:title>
  <dc:creator>Equipo Luma</dc:creator>
</cp:coreProperties>"""
            )

            writeZipText(
                zos, "ppt/presentation.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldIdLst>
    <p:sldId id="256" r:id="rId1"/>
    <p:sldId id="257" r:id="rId2"/>
    <p:sldId id="258" r:id="rId3"/>
    <p:sldId id="259" r:id="rId4"/>
  </p:sldIdLst>
  <p:sldSz cx="12192000" cy="6858000" type="screen16x9"/>
</p:presentation>"""
            )

            writeZipText(
                zos, "ppt/_rels/presentation.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
</Relationships>"""
            )

            writeZipText(
                zos, "ppt/theme/theme1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Luma Editorial">
  <a:themeElements>
    <a:clrScheme name="Luma">
      <a:dk1><a:srgbClr val="1A2233"/></a:dk1>
      <a:lt1><a:srgbClr val="FFFDF8"/></a:lt1>
      <a:dk2><a:srgbClr val="16243D"/></a:dk2>
      <a:lt2><a:srgbClr val="F4EFE6"/></a:lt2>
      <a:accent1><a:srgbClr val="2B64F5"/></a:accent1>
      <a:accent2><a:srgbClr val="D97706"/></a:accent2>
      <a:accent3><a:srgbClr val="0D9488"/></a:accent3>
    </a:clrScheme>
  </a:themeElements>
</a:theme>"""
            )

            // Slide 1: Cover Slide (Deep Navy + Amber accent bar)
            writeZipText(
                zos, "ppt/slides/slide1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="16243D"/></a:solidFill></p:bgPr></p:bg>
    <p:spTree>
      <p:sp>
        <p:spPr>
          <a:xfrm><a:off x="762000" y="900000"/><a:ext cx="1950000" cy="420000"/></a:xfrm>
          <a:prstGeom prst="roundRect"/>
          <a:solidFill><a:srgbClr val="D97706"/></a:solidFill>
        </p:spPr>
        <p:txBody>
          <a:bodyPr anchor="ctr"/>
          <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1300" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>PRESENTACIÓN PPTX 16:9</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:nvPr><p:ph type="ctrTitle"/></p:nvPr></p:nvSpPr>
        <p:spPr><a:xfrm><a:off x="762000" y="1680000"/><a:ext cx="10668000" cy="1800000"/></a:xfrm></p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="4200" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Luma para Trabajo y Estudio</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="3" name="Subtitle"/><p:nvPr><p:ph type="subTitle"/></p:nvPr></p:nvSpPr>
        <p:spPr><a:xfrm><a:off x="762000" y="3450000"/><a:ext cx="9600000" cy="1400000"/></a:xfrm></p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="2000"><a:solidFill><a:srgbClr val="CBD5E1"/></a:solidFill></a:rPr><a:t>Visualiza diapositivas en su formato original 16:9, cambia a Modo EPUB cuando quieras leer con texto amplio y firma directamente sobre cada lámina.</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:spPr>
          <a:xfrm><a:off x="762000" y="5550000"/><a:ext cx="10668000" cy="60000"/></a:xfrm>
          <a:prstGeom prst="rect"/>
          <a:solidFill><a:srgbClr val="3B82F6"/></a:solidFill>
        </p:spPr>
      </p:sp>
      <p:sp>
        <p:spPr><a:xfrm><a:off x="762000" y="5750000"/><a:ext cx="10668000" cy="520000"/></a:xfrm></p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="94A3B8"/></a:solidFill></a:rPr><a:t>Desliza a la siguiente lámina o toca el botón Modo EPUB / Formato PPTX en el menú.</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            )
            writeZipText(
                zos, "ppt/slides/_rels/slide1.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
</Relationships>"""
            )
            writeZipText(
                zos, "ppt/notesSlides/notesSlide1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Nota del presentador: esta diapositiva conserva las coordenadas exactas 16:9 del archivo PowerPoint original.</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
</p:notes>"""
            )

            // Slide 2: Two-card visual layout (Warm Cream paper + Cobalt & Teal cards)
            writeZipText(
                zos, "ppt/slides/slide2.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="FBF6EC"/></a:solidFill></p:bgPr></p:bg>
    <p:spTree>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
        <p:spPr><a:xfrm><a:off x="680000" y="520000"/><a:ext cx="10800000" cy="820000"/></a:xfrm></p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="3000" b="1"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Dos formas de leer cualquier documento</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:spPr>
          <a:xfrm><a:off x="680000" y="1650000"/><a:ext cx="5150000" cy="4350000"/></a:xfrm>
          <a:prstGeom prst="roundRect"/>
          <a:solidFill><a:srgbClr val="EEF4FF"/></a:solidFill>
          <a:ln w="19050"><a:solidFill><a:srgbClr val="2B64F5"/></a:solidFill></a:ln>
        </p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="2100" b="1"><a:solidFill><a:srgbClr val="1D4AC0"/></a:solidFill></a:rPr><a:t>Formato Original (PPTX / A4)</a:t></a:r></a:p>
          <a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Mantiene la proporción exacta 16:9, A4, Carta o A3.</a:t></a:r></a:p>
          <a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Ideal para revisar diseños, tablas y diagramas.</a:t></a:r></a:p>
          <a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Perfecto para dibujar y firmar con precisión.</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:spPr>
          <a:xfrm><a:off x="6320000" y="1650000"/><a:ext cx="5150000" cy="4350000"/></a:xfrm>
          <a:prstGeom prst="roundRect"/>
          <a:solidFill><a:srgbClr val="ECFDF5"/></a:solidFill>
          <a:ln w="19050"><a:solidFill><a:srgbClr val="0D9488"/></a:solidFill></a:ln>
        </p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="2100" b="1"><a:solidFill><a:srgbClr val="0F766E"/></a:solidFill></a:rPr><a:t>Modo EPUB Adaptado</a:t></a:r></a:p>
          <a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Reorganiza el texto en lectura fluida sin hacer zoom.</a:t></a:r></a:p>
          <a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Usa tus fuentes favoritas, interlineado y temas cálidos.</a:t></a:r></a:p>
          <a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Permite subrayar y consultar diccionario al instante.</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            )

            // Slide 3: Real OOXML Table (<a:tbl>) comparing formats
            writeZipText(
                zos, "ppt/slides/slide3.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></p:bgPr></p:bg>
    <p:spTree>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
        <p:spPr><a:xfrm><a:off x="680000" y="480000"/><a:ext cx="10800000" cy="800000"/></a:xfrm></p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="2800" b="1"><a:solidFill><a:srgbClr val="16243D"/></a:solidFill></a:rPr><a:t>Resumen de formatos compatibles en Luma</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:graphicFrame>
        <p:xfrm><a:off x="680000" y="1550000"/><a:ext cx="10800000" cy="4400000"/></p:xfrm>
        <a:graphic>
          <a:graphicData>
            <a:tbl>
              <a:tblGrid>
                <a:gridCol w="2200000"/>
                <a:gridCol w="4300000"/>
                <a:gridCol w="4300000"/>
              </a:tblGrid>
              <a:tr h="900000">
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1500" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Formato</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="16243D"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1500" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Uso Principal</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="16243D"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1500" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Función Destacada</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="16243D"/></a:solidFill></a:tcPr></a:tc>
              </a:tr>
              <a:tr h="860000">
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="2B64F5"/></a:solidFill></a:rPr><a:t>EPUB / FB2</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Libros y lectura prolongada</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Temas cálidos, modo nocturno y diccionario</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></a:tcPr></a:tc>
              </a:tr>
              <a:tr h="860000">
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="D97706"/></a:solidFill></a:rPr><a:t>PPTX / DOCX</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Trabajo, clases y contratos</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Hoja 16:9 / A4, firma y exportación PDF</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:tcPr></a:tc>
              </a:tr>
              <a:tr h="860000">
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400" b="1"><a:solidFill><a:srgbClr val="E11D48"/></a:solidFill></a:rPr><a:t>PDF / TXT</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>Apuntes, artículos y Manhwa</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></a:tcPr></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:rPr sz="1400"><a:solidFill><a:srgbClr val="1A2233"/></a:solidFill></a:rPr><a:t>OCR interactivo, zoom y cambio a Modo EPUB</a:t></a:r></a:p></a:txBody><a:tcPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></a:tcPr></a:tc>
              </a:tr>
            </a:tbl>
          </a:graphicData>
        </a:graphic>
      </p:graphicFrame>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            )

            // Slide 4: Closing / Signature Practice Slide
            writeZipText(
                zos, "ppt/slides/slide4.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFBF3"/></a:solidFill></p:bgPr></p:bg>
    <p:spTree>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
        <p:spPr><a:xfrm><a:off x="762000" y="620000"/><a:ext cx="10668000" cy="860000"/></a:xfrm></p:spPr>
        <p:txBody>
          <a:p><a:r><a:rPr sz="3200" b="1"><a:solidFill><a:srgbClr val="332920"/></a:solidFill></a:rPr><a:t>Zona de práctica: anota o firma esta lámina</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
      <p:sp>
        <p:spPr>
          <a:xfrm><a:off x="762000" y="1850000"/><a:ext cx="10668000" cy="3600000"/></a:xfrm>
          <a:prstGeom prst="roundRect"/>
          <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
          <a:ln w="25400"><a:solidFill><a:srgbClr val="D97706"/></a:solidFill></a:ln>
        </p:spPr>
        <p:txBody>
          <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1800" b="1"><a:solidFill><a:srgbClr val="B45309"/></a:solidFill></a:rPr><a:t>Activa "Dibujar" en el menú y elige el preset "Firma"</a:t></a:r></a:p>
          <a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="1500"><a:solidFill><a:srgbClr val="64748B"/></a:solidFill></a:rPr><a:t>Dibuja tu firma o un esquema dentro de este recuadro y luego pulsa Exportar para obtener un PDF listo para enviar.</a:t></a:r></a:p>
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
            )
        }
    }

    /**
     * Generates a clean, structured plain-text (.txt) sample document for quick notes,
     * full-text search, soft warm themes, and toggling between Modo EPUB and standard sheet view.
     */
    private fun createSampleTxt(target: File) {
        val text = """
            NOTAS RÁPIDAS DE LECTURA Y ENFOQUE
            Guía Práctica Luma · Formato TXT

            1. Por qué leer texto plano sin distracciones
            Los archivos TXT son los más ligeros y universales para guardar apuntes de clase, borradores, guiones o resúmenes de libros. En Luma puedes abrirlos al instante con tipografía editorial y temas cálidos suaves.

            2. Cómo cuidar tu vista en sesiones largas
            - Elige Crema cálida, Arena suave o Ámbar durante el día para evitar el blanco brillante.
            - Sube el Filtro de luz cálida en Texto y Tema para reducir la fatiga ocular.
            - Activa el Horario nocturno automático para que Luma cambie a Noche cálida al caer la tarde.

            3. Prueba las herramientas aquí mismo
            - Mantén pulsada cualquier palabra para buscar su definición o subrayarla con color.
            - Usa el buscador del marcapáginas para localizar términos como "fatiga" o "horario".
            - Cambia el interruptor de la barra a Formato TXT si quieres ver estas notas como una hoja A4 lista para anotar o firmar.
        """.trimIndent()
        target.writeText(text, Charsets.UTF_8)
    }

    /**
     * Generates a valid FictionBook 2 (.fb2) XML short story showcasing native FB2 chapters,
     * epigraph, bookmarks, and nocturnal reading comfort.
     */
    private fun createSampleFb2(target: File) {
        val fb2Xml = """<?xml version="1.0" encoding="UTF-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
  <description>
    <title-info>
      <genre>sf</genre>
      <author>
        <first-name>Elena</first-name>
        <last-name>Mendoza</last-name>
      </author>
      <book-title>Crónicas del Observatorio</book-title>
      <lang>es</lang>
    </title-info>
  </description>
  <body>
    <title><p>Crónicas del Observatorio</p></title>
    <section>
      <title><p>Capítulo I · La lámpara de ámbar</p></title>
      <epigraph>
        <p>«Quien lee de noche descubre constelaciones que el sol oculta.»</p>
      </epigraph>
      <p>En lo alto de la sierra de Atacama, el observatorio antiguo conservaba una regla inquebrantable después del anochecer: ninguna pantalla podía emitir luz fría. Todas las bitácoras se consultaban bajo lámparas de tono ámbar para proteger la adaptación nocturna de los astrónomos.</p>
      <p>Mateo abrió su cuaderno digital mientras la cúpula giraba en silencio sobre su cabeza. Afuera, la Vía Láctea cruzaba el cielo como un río de tinta plateada. Dentro de la cabina, cada párrafo reposaba sobre un fondo oscuro cálido que permitía leer durante horas sin fatiga.</p>
      <p>Prueba activar el tema Noche cálida o Bosque noche desde Texto y Tema mientras lees este archivo FB2 real.</p>
    </section>
    <section>
      <title><p>Capítulo II · Señal en el meridiano</p></title>
      <p>A las dos de la madrugada, una pequeña pulsación apareció en el registro del telescopio. No era ruido atmosférico ni el paso de un satélite comercial; era un patrón rítmico que se repetía cada cuarenta y dos segundos con exactitud matemática.</p>
      <p>Mateo guardó un marcador en la página exacta del catálogo estelar, subrayó la coordenada principal y trazó un diagrama rápido sobre el margen. En pocos segundos había conectado tres observaciones separadas por veinte años de historia.</p>
      <p>Puedes usar el menú del marcapáginas lateral para añadir un marcador aquí, explorar el índice de capítulos o alternar entre Modo EPUB y Formato FB2.</p>
    </section>
  </body>
</FictionBook>"""
        target.writeText(fb2Xml, Charsets.UTF_8)
    }

    private fun writeZipText(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}

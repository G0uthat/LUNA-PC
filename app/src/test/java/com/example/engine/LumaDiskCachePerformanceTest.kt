package com.example.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedChapter
import com.example.data.model.ParsedDocument
import com.example.data.model.TocEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LumaDiskCachePerformanceTest {

    @Test
    fun parsedDocumentBinaryCacheRoundTripAndEvictionWorkCleanly() {
        val context: Context = ApplicationProvider.getApplicationContext()
        LumaDiskCacheManager.setLimitMb(500)

        // Create a realistic ~50,000-word synthetic book (20 chapters x 50 paragraphs x 50 words)
        val paragraphText =
            "La arquitectura moderna de lectura digital prioriza la velocidad de apertura y la fluidez visual " +
                "mediante cachés binarias compactas, tipografías empaquetadas localmente y perfiles ART optimizados " +
                "para reducir el jank en biblioteca, visor PDF y cuadernos de estudio de alta precisión interactiva."
        val chapters = (0 until 20).map { chIdx ->
            val blocks = (0 until 50).map { bIdx ->
                ParsedBlock(
                    index = bIdx,
                    type = if (bIdx == 0) BlockType.HEADING_1 else BlockType.PARAGRAPH,
                    text = "$paragraphText ($chIdx-$bIdx)"
                )
            }
            ParsedChapter(
                index = chIdx,
                title = "Capítulo ${chIdx + 1}",
                cfiPrefix = "/6/${(chIdx + 1) * 2}",
                href = "ch_${chIdx + 1}.xhtml",
                blocks = blocks,
                wordCount = blocks.size * 42
            )
        }
        val doc = ParsedDocument(
            title = "Atlas de Rendimiento Luma",
            author = "Equipo Luma",
            format = DocumentFormat.EPUB,
            chapters = chapters,
            toc = chapters.map { TocEntry(it.title, it.index, 0) },
            totalWords = chapters.sumOf { it.wordCount }
        )

        val fakeSource = File(context.filesDir, "books/perf_sample.epub").apply {
            parentFile?.mkdirs()
            writeText("synthetic-epub-source-for-cache-key")
        }

        val tWrite0 = System.nanoTime()
        ParsedDocumentDiskCache.write(context, fakeSource, DocumentFormat.EPUB, doc)
        val writeMs = (System.nanoTime() - tWrite0) / 1_000_000.0

        // Evict RAM cache so we measure pure disk binary cache read speed!
        ParsedDocumentDiskCache.clearMemoryCache()
        val tDiskRead0 = System.nanoTime()
        val diskRead = ParsedDocumentDiskCache.read(context, fakeSource, DocumentFormat.EPUB)
        val diskReadMs = (System.nanoTime() - tDiskRead0) / 1_000_000.0

        // Second read hits RAM hot cache
        val tRamRead0 = System.nanoTime()
        val ramRead = ParsedDocumentDiskCache.read(context, fakeSource, DocumentFormat.EPUB)
        val ramReadMs = (System.nanoTime() - tRamRead0) / 1_000_000.0

        assertNotNull(diskRead)
        assertNotNull(ramRead)
        assertEquals(doc.chapters.size, diskRead!!.chapters.size)
        assertEquals(doc.totalWords, diskRead.totalWords)

        val cacheDir = File(context.filesDir, "parsed_cache")
        val cacheBytes = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        assertTrue("Binary cache file should exist", cacheBytes > 10_000L)

        println(
            "PERF_METRIC parsed_cache_bytes=$cacheBytes write_ms=${"%.2f".format(writeMs)} " +
                "disk_read_ms=${"%.2f".format(diskReadMs)} ram_read_ms=${"%.3f".format(ramReadMs)}"
        )

        // Verify clearAllCaches reclaims cache without deleting user books!
        val freed = runBlocking { LumaDiskCacheManager.clearAllCaches(context) }
        assertTrue("clearAllCaches should reclaim cache bytes", freed >= cacheBytes)
        assertTrue("User book file must remain untouched", fakeSource.exists())
    }
}

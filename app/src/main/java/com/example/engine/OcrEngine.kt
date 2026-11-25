package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.OcrBlockBox
import com.example.data.model.OcrPageResult
import com.example.data.model.OcrWordBox
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Single-flight lock: prevents parallel ML Kit invocations from saturating CPU/GPU during navigation
    private val ocrMutex = Mutex()

    /**
     * Runs on-device ML Kit OCR (strictly single-flight via [ocrMutex]), clusters nearby lines/blocks
     * inside the same speech bubble so translations NEVER split or overlap awkwardly, and samples the
     * actual speech-bubble background & contrasting ink color for seamless borderless lettering.
     */
    suspend fun recognizePage(bitmap: Bitmap, pageIndex: Int): OcrPageResult =
        ocrMutex.withLock {
            withContext(Dispatchers.Default) {
                coroutineContext.ensureActive()
                if (bitmap.isRecycled) {
                    return@withContext OcrPageResult(pageIndex = pageIndex, blocks = emptyList(), isTranslated = false)
                }
                val w = bitmap.width.toFloat().coerceAtLeast(1f)
                val h = bitmap.height.toFloat().coerceAtLeast(1f)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).await()
                coroutineContext.ensureActive()

            val rawBlocks = visionText.textBlocks.mapNotNull { block ->
                val rect = block.boundingBox ?: return@mapNotNull null
                val words = mutableListOf<OcrWordBox>()
                for (line in block.lines) {
                    for (element in line.elements) {
                        val wRect = element.boundingBox ?: continue
                        val clean = element.text.trim()
                        if (clean.isNotEmpty()) {
                            words.add(
                                OcrWordBox(
                                    text = clean,
                                    left = (wRect.left / w).coerceIn(0f, 1f),
                                    top = (wRect.top / h).coerceIn(0f, 1f),
                                    right = (wRect.right / w).coerceIn(0f, 1f),
                                    bottom = (wRect.bottom / h).coerceIn(0f, 1f)
                                )
                            )
                        }
                    }
                }
                val text = block.text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                if (text.isBlank()) return@mapNotNull null
                MutableCluster(
                    left = (rect.left / w).coerceIn(0f, 1f),
                    top = (rect.top / h).coerceIn(0f, 1f),
                    right = (rect.right / w).coerceIn(0f, 1f),
                    bottom = (rect.bottom / h).coerceIn(0f, 1f),
                    texts = mutableListOf(text),
                    words = words
                )
            }.sortedBy { it.top }

            // Merge vertically close & horizontally overlapping blocks inside the same speech bubble!
            val merged = mutableListOf<MutableCluster>()
            for (candidate in rawBlocks) {
                val target = merged.firstOrNull { existing -> shouldMergeIntoSameBubble(existing, candidate) }
                if (target != null) {
                    target.left = min(target.left, candidate.left)
                    target.top = min(target.top, candidate.top)
                    target.right = max(target.right, candidate.right)
                    target.bottom = max(target.bottom, candidate.bottom)
                    target.texts.addAll(candidate.texts)
                    target.words.addAll(candidate.words)
                } else {
                    merged.add(candidate)
                }
            }

            val finalBlocks = merged.mapIndexed { idx, cluster ->
                val (bgArgb, inkArgb) = sampleSeamlessBubbleColors(
                    bitmap = bitmap,
                    left = cluster.left,
                    top = cluster.top,
                    right = cluster.right,
                    bottom = cluster.bottom
                )
                OcrBlockBox(
                    id = idx,
                    originalText = cluster.texts.joinToString(" ").replace(Regex("\\s+"), " ").trim(),
                    translatedText = null,
                    left = cluster.left,
                    top = cluster.top,
                    right = cluster.right,
                    bottom = cluster.bottom,
                    words = cluster.words,
                    bgColorArgb = bgArgb,
                    inkColorArgb = inkArgb
                )
            }

                OcrPageResult(
                    pageIndex = pageIndex,
                    blocks = finalBlocks,
                    isTranslated = false
                )
            }
        }

    private data class MutableCluster(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        val texts: MutableList<String>,
        val words: MutableList<OcrWordBox>
    )

    private fun shouldMergeIntoSameBubble(a: MutableCluster, b: MutableCluster): Boolean {
        val horizontalOverlap = min(a.right, b.right) - max(a.left, b.left)
        val minWidth = min(a.right - a.left, b.right - b.left).coerceAtLeast(0.01f)
        val overlapRatio = horizontalOverlap / minWidth

        val verticalGap = max(b.top - a.bottom, a.top - b.bottom)
        val avgLineH = ((a.bottom - a.top) + (b.bottom - b.top)) * 0.5f
        val maxAllowedGap = max(0.032f, avgLineH * 0.65f)

        return overlapRatio > 0.28f && verticalGap <= maxAllowedGap
    }

    /**
     * Samples the perimeter just around the text bounding box to match the exact speech-bubble
     * background color (usually white/cream inside comic bubbles, or dark in dark captions) so
     * translated text blends seamlessly with zero boxy outlines.
     */
    private fun sampleSeamlessBubbleColors(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Pair<Long, Long> {
        val w = bitmap.width
        val h = bitmap.height
        val padX = (0.008f * w).toInt().coerceAtLeast(2)
        val padY = (0.008f * h).toInt().coerceAtLeast(2)

        val x1 = ((left * w).toInt() - padX).coerceIn(0, w - 1)
        val x2 = ((right * w).toInt() + padX).coerceIn(0, w - 1)
        val y1 = ((top * h).toInt() - padY).coerceIn(0, h - 1)
        val y2 = ((bottom * h).toInt() + padY).coerceIn(0, h - 1)
        val cx = ((x1 + x2) / 2).coerceIn(0, w - 1)
        val cy = ((y1 + y2) / 2).coerceIn(0, h - 1)

        val sampleCoords = listOf(
            x1 to y1, cx to y1, x2 to y1,
            x1 to cy, x2 to cy,
            x1 to y2, cx to y2, x2 to y2
        )

        var rSum = 0
        var gSum = 0
        var bSum = 0
        var brightCount = 0
        for ((sx, sy) in sampleCoords) {
            val px = bitmap.getPixel(sx, sy)
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            if (lum > 210) brightCount++
            rSum += r
            gSum += g
            bSum += b
        }

        // If majority of perimeter is white/near-white comic bubble fill, snap cleanly to pure white
        if (brightCount >= 4) {
            return 0xFFFFFFFFL to 0xFF101828L
        }

        val count = sampleCoords.size
        val avgR = (rSum / count).coerceIn(0, 255)
        val avgG = (gSum / count).coerceIn(0, 255)
        val avgB = (bSum / count).coerceIn(0, 255)
        val avgBg = Color.argb(246, avgR, avgG, avgB).toLong() and 0xFFFFFFFFL
        val avgLum = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB
        val ink = if (avgLum > 145) 0xFF101828L else 0xFFF8FAFCL
        return avgBg to ink
    }
}

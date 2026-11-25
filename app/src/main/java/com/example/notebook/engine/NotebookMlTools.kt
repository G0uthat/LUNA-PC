package com.example.notebook.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.notebook.model.StrokeObject
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Strictly LAZY on-device ML Kit tools for Study Notebooks:
 * - Zero initialization at app launch or notebook open.
 * - Clients are created on demand only when the user taps "Quitar fondo (IA)" or "A texto",
 *   executed off the main thread with cooperative coroutine cancellation, and closed immediately.
 */
object NotebookMlTools {

    private const val MAX_SEGMENTATION_DIM = 1600

    /**
     * Removes the background of [sourceBitmap] using on-device ML Kit Subject Segmentation
     * and returns a transparent ARGB_8888 cutout bitmap.
     */
    suspend fun removeImageBackground(
        context: Context,
        sourceBitmap: Bitmap
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        if (sourceBitmap.isRecycled || sourceBitmap.width <= 1 || sourceBitmap.height <= 1) {
            return@withContext Result.failure(IllegalArgumentException("La imagen no está disponible"))
        }
        currentCoroutineContext().ensureActive()

        val scaledBitmap = scaleDownForMl(sourceBitmap, MAX_SEGMENTATION_DIM)
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)

        try {
            val input = InputImage.fromBitmap(scaledBitmap, 0)
            val result = segmenter.process(input).await()
            currentCoroutineContext().ensureActive()

            val fg = result.foregroundBitmap
                ?: return@withContext Result.failure(
                    IllegalStateException("No se detectó un sujeto claro para recortar el fondo")
                )
            val safeCopy = fg.copy(Bitmap.Config.ARGB_8888, false)
            Result.success(safeCopy)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val msg = t.localizedMessage.orEmpty()
            val friendly = when {
                msg.contains("download", ignoreCase = true) ||
                    msg.contains("module", ignoreCase = true) ||
                    msg.contains("unavailable", ignoreCase = true) ->
                    "Google Play Services está descargando el módulo de recorte IA. Intenta de nuevo en unos segundos con conexión."
                else -> "No se pudo quitar el fondo: ${msg.ifBlank { "error inesperado" }}"
            }
            Result.failure(IllegalStateException(friendly, t))
        } finally {
            runCatching { segmenter.close() }
            if (scaledBitmap !== sourceBitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }

    /**
     * Converts selected handwritten [strokes] into editable Spanish text using on-device
     * ML Kit Digital Ink Recognition (`es`). Downloads the Spanish ink model automatically
     * on first use if not yet present on device.
     */
    suspend fun recognizeHandwritingToSpanishText(
        strokes: List<StrokeObject>,
        pageWidthPx: Float,
        pageHeightPx: Float,
        onStatus: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val validStrokes = strokes.filter { it.points.isNotEmpty() }
        if (validStrokes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Selecciona al menos un trazo manuscrito"))
        }
        currentCoroutineContext().ensureActive()

        val modelId = DigitalInkRecognitionModelIdentifier.fromLanguageTag("es")
            ?: return@withContext Result.failure(
                IllegalStateException("No se encontró el identificador de idioma español")
            )
        val model = DigitalInkRecognitionModel.builder(modelId).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        try {
            val isDownloaded = remoteModelManager.isModelDownloaded(model).await()
            currentCoroutineContext().ensureActive()
            if (!isDownloaded) {
                onStatus("Descargando modelo de escritura en español…")
                val conditions = DownloadConditions.Builder().build()
                remoteModelManager.download(model, conditions).await()
                currentCoroutineContext().ensureActive()
            }

            onStatus("Reconociendo escritura a mano…")
            val w = pageWidthPx.coerceAtLeast(600f)
            val h = pageHeightPx.coerceAtLeast(600f)

            // Sort strokes roughly in reading order (top-to-bottom bands, then left-to-right)
            val ordered = validStrokes.sortedWith(
                compareBy<StrokeObject> { (it.y * 12f).toInt() }.thenBy { it.x }
            )

            val inkBuilder = Ink.builder()
            var syntheticTimeMs = 0L
            for (stroke in ordered) {
                val strokeBuilder = Ink.Stroke.builder()
                for (pt in stroke.points) {
                    strokeBuilder.addPoint(Ink.Point.create(pt.x * w, pt.y * h, syntheticTimeMs))
                    syntheticTimeMs += 12L
                }
                inkBuilder.addStroke(strokeBuilder.build())
                syntheticTimeMs += 140L
            }

            val recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            )
            try {
                val recognitionResult = recognizer.recognize(inkBuilder.build()).await()
                currentCoroutineContext().ensureActive()
                val recognized = recognitionResult.candidates
                    .firstOrNull()
                    ?.text
                    ?.trim()
                    .orEmpty()
                if (recognized.isBlank()) {
                    Result.failure(IllegalStateException("No se reconoció texto legible en los trazos"))
                } else {
                    Result.success(recognized)
                }
            } finally {
                runCatching { recognizer.close() }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val msg = t.localizedMessage.orEmpty()
            val friendly = if (msg.contains("network", ignoreCase = true) || msg.contains("download", ignoreCase = true)) {
                "Se requiere conexión una sola vez para descargar el modelo de escritura en español."
            } else {
                "No se pudo convertir a texto: ${msg.ifBlank { "error de reconocimiento" }}"
            }
            Result.failure(IllegalStateException(friendly, t))
        }
    }

    private fun scaleDownForMl(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest.toFloat()
        val m = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}

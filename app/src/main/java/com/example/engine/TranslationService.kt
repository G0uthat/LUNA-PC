package com.example.engine

import android.graphics.Bitmap
import android.util.Base64
import android.util.LruCache
import com.example.data.model.AiProviderType
import com.example.data.model.OcrBlockBox
import com.example.data.model.ReaderPreferences
import com.example.data.model.TranslationEngine
import com.example.data.model.WikipediaSummary
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

object TranslationService {

    private val httpClient: OkHttpClient by lazy {
        LumaHttpClient.shared.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Bounded thread-safe in-memory LRU caches for instant repeated dictionary/translation lookups
    private val translationCache = LruCache<String, String>(200)
    private val wikipediaCache = LruCache<String, WikipediaSummary>(100)

    private fun translationCacheKey(word: String, prefs: ReaderPreferences): String {
        val norm = word.trim().lowercase(Locale.ROOT)
        val engineTag = if (prefs.translationEngine == TranslationEngine.AI_CUSTOM_API && prefs.aiApiKey.isNotBlank()) {
            "ai:${prefs.aiProvider.name}:${prefs.aiModel.trim()}"
        } else {
            "google"
        }
        return "$engineTag|$norm"
    }

    private fun wikiCacheKey(word: String): String =
        cleanWord(word).lowercase(Locale.ROOT)

    fun getCachedTranslation(word: String, prefs: ReaderPreferences): String? {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return null
        return translationCache.get(translationCacheKey(cleaned, prefs))
    }

    fun getCachedWikipediaSummary(word: String): WikipediaSummary? {
        val key = wikiCacheKey(word)
        if (key.isEmpty()) return null
        return wikipediaCache.get(key)
    }

    /**
     * Fetches instant Spanish summary from Wikipedia REST API (with in-memory LRU cache).
     */
    suspend fun fetchWikipediaSummary(word: String): Result<WikipediaSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineContext.ensureActive()
                val cleaned = cleanWord(word)
                require(cleaned.isNotEmpty()) { "Palabra vacía" }
                val cacheKey = cleaned.lowercase(Locale.ROOT)
                wikipediaCache.get(cacheKey)?.let { return@runCatching it }

                val encoded = URLEncoder.encode(cleaned, "UTF-8").replace("+", "%20")
                val url = "https://es.wikipedia.org/api/rest_v1/page/summary/$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "FolioReaderAndroid/1.0 (Minimalist E-Reader)")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Sin artículo exacto en Wikipedia para \"$cleaned\"")
                    }
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val title = json.optString("title", cleaned)
                    val description = json.optString("description", "")
                    val extract = json.optString("extract", "")
                    val pageUrl = json.optJSONObject("content_urls")
                        ?.optJSONObject("mobile")
                        ?.optString("page", "https://es.wikipedia.org/wiki/$encoded")
                        ?: "https://es.wikipedia.org/wiki/$encoded"

                    if (extract.isBlank()) {
                        throw IllegalStateException("No se encontró un resumen disponible para \"$cleaned\"")
                    }
                    WikipediaSummary(
                        title = title,
                        description = description,
                        extract = extract,
                        pageUrl = pageUrl
                    ).also { summary ->
                        wikipediaCache.put(cacheKey, summary)
                    }
                }
            }
        }

    /**
     * Translates a single word or short phrase into Spanish for the contextual popover.
     * Checks in-memory [translationCache] first; on miss uses AI API or Google Traductor (+ ML Kit fallback).
     */
    suspend fun translateWordToSpanish(
        word: String,
        contextSnippet: String,
        prefs: ReaderPreferences,
        onStatus: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Texto vacío"))

        val cacheKey = translationCacheKey(cleaned, prefs)
        translationCache.get(cacheKey)?.let { cached ->
            return@withContext Result.success(cached)
        }

        if (prefs.translationEngine == TranslationEngine.AI_CUSTOM_API && prefs.aiApiKey.isNotBlank()) {
            onStatus("Consultando IA (${prefs.aiProvider.displayName})…")
            val aiResult = runCatching {
                val isPhrase = cleaned.contains(' ')
                val promptText = if (isPhrase) {
                    """
                        Traduce al español de forma natural, fiel y fluida el siguiente fragmento: "$cleaned".
                        Devuelve únicamente la traducción limpia, sin comillas ni markdown.
                    """.trimIndent()
                } else {
                    """
                        Traduce al español de forma concisa y natural el término "$cleaned" (contexto: "${contextSnippet.take(180)}").
                        Devuelve primero la traducción directa y, si ayuda, una breve aclaración de 1 línea. Solo texto limpio, sin markdown.
                    """.trimIndent()
                }
                translateWithCustomAi(
                    prompt = promptText,
                    prefs = prefs,
                    pageBitmap = null
                )
            }
            val aiText = aiResult.getOrNull()
            if (aiResult.isSuccess && !aiText.isNullOrBlank()) {
                translationCache.put(cacheKey, aiText)
                return@withContext aiResult
            }
        }

        // Default or fallback: Google Translate (fast online endpoint, then on-device ML Kit Translate)
        coroutineContext.ensureActive()
        onStatus("Traduciendo con Google Traductor…")
        val online = runCatching { googleTranslateHttp(cleaned) }
        val onlineText = online.getOrNull()
        if (online.isSuccess && !onlineText.isNullOrBlank()) {
            translationCache.put(cacheKey, onlineText)
            return@withContext online
        }

        coroutineContext.ensureActive()
        runCatching {
            onStatus("Preparando traductor local EN → ES…")
            mlKitTranslateEnToEs(cleaned).also { localText ->
                if (localText.isNotBlank()) translationCache.put(cacheKey, localText)
            }
        }
    }

    /**
     * Translates all OCR speech bubbles on a Manhwa / PDF page into Spanish.
     * Supports both Google Traductor (zero API key) and user's own AI API key (Gemini or OpenAI-compatible).
     */
    suspend fun translateManhwaBlocks(
        blocks: List<OcrBlockBox>,
        pageBitmap: Bitmap?,
        prefs: ReaderPreferences,
        onProgress: (String) -> Unit
    ): Result<List<OcrBlockBox>> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext Result.success(emptyList())

        if (prefs.translationEngine == TranslationEngine.AI_CUSTOM_API && prefs.aiApiKey.isNotBlank()) {
            onProgress("Traduciendo globos con ${prefs.aiProvider.displayName}…")
            val aiRes = runCatching {
                val numbered = blocks.joinToString("\n") { "[${it.id}] ${it.originalText}" }
                val prompt = """
                    Eres traductor profesional de manhwa y cómics al español natural.
                    Traduce cada globo numerado manteniendo el tono dramático y conciso para que quepa en el globo de texto.
                    Responde ÚNICAMENTE con un JSON válido con el formato {"translations":[{"id":0,"es":"..."}]}.
                    Globos detectados:
                    $numbered
                """.trimIndent()

                val raw = translateWithCustomAi(prompt, prefs, pageBitmap)
                val map = parseBatchJsonTranslations(raw)
                blocks.map { block ->
                    block.copy(translatedText = map[block.id] ?: block.translatedText ?: block.originalText)
                }
            }
            if (aiRes.isSuccess) return@withContext aiRes
        }

        // Google Traductor mode (zero API key)
        runCatching {
            onProgress("Traduciendo ${blocks.size} globos con Google Traductor…")
            val translatedBlocks = ArrayList<OcrBlockBox>(blocks.size)
            for ((idx, block) in blocks.withIndex()) {
                onProgress("Traduciendo globo ${idx + 1} de ${blocks.size}…")
                val translated = runCatching { googleTranslateHttp(block.originalText) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching { mlKitTranslateEnToEs(block.originalText) }.getOrNull()
                    ?: block.originalText
                translatedBlocks.add(block.copy(translatedText = translated))
            }
            translatedBlocks
        }
    }

    private suspend fun mlKitTranslateEnToEs(text: String): String = withTimeout(45_000L) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    private fun googleTranslateHttp(text: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Error HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val root = JSONArray(body)
            val segments = root.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i)
                val part = seg?.optString(0).orEmpty()
                sb.append(part)
            }
            return sb.toString().trim()
        }
    }

    private suspend fun translateWithCustomAi(
        prompt: String,
        prefs: ReaderPreferences,
        pageBitmap: Bitmap?
    ): String = withTimeout(55_000L) {
        val apiKey = prefs.aiApiKey.trim()
        require(apiKey.isNotEmpty()) { "Configura tu API key primero en Ajustes de Traducción." }
        val model = prefs.aiModel.trim().ifEmpty { prefs.aiProvider.defaultModel }
        val baseUrl = prefs.aiBaseUrl.trim().trimEnd('/').ifEmpty { prefs.aiProvider.defaultBaseUrl }

        when (prefs.aiProvider) {
            AiProviderType.GEMINI -> callGeminiRest(baseUrl, model, apiKey, prompt, pageBitmap)
            AiProviderType.OPENAI_COMPATIBLE -> callOpenAiCompatible(baseUrl, model, apiKey, prompt)
        }
    }

    private fun callGeminiRest(
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        pageBitmap: Bitmap?
    ): String {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", prompt))
        if (pageBitmap != null) {
            val base64 = bitmapToBase64Jpeg(pageBitmap)
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64)
                )
            )
        }
        val payload = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", parts))
        )

        val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini API (${response.code}): ${body.take(180)}")
            }
            val json = JSONObject(body)
            return json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?: error("Respuesta vacía de Gemini API")
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String
    ): String {
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
        )
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.2)

        val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("API IA (${response.code}): ${body.take(180)}")
            }
            val json = JSONObject(body)
            return json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?: error("Respuesta vacía de la API IA")
        }
    }

    private fun parseBatchJsonTranslations(raw: String): Map<Int, String> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyMap()
        val jsonStr = raw.substring(start, end + 1)
        val obj = JSONObject(jsonStr)
        val arr = obj.optJSONArray("translations") ?: return emptyMap()
        val map = mutableMapOf<Int, String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optInt("id", -1)
            val es = item.optString("es", "").trim()
            if (id >= 0 && es.isNotEmpty()) {
                map[id] = es
            }
        }
        return map
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val maxDim = 960f
        val scale = minOf(1f, maxDim / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun cleanWord(word: String): String =
        word.trim().trim { !it.isLetterOrDigit() && it != '-' && it != '\'' }

    fun buildTemplatedUrl(template: String, word: String): String {
        val encoded = URLEncoder.encode(cleanWord(word), "UTF-8").replace("+", "%20")
        return template
            .replace("{palabra}", encoded, ignoreCase = true)
            .replace("{word}", encoded, ignoreCase = true)
    }
}

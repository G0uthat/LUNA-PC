package com.example.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DictionarySourceEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FolioRepository
import com.example.data.local.HighlightNoteEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.DocumentFormat
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.ExportFormatOption
import com.example.data.model.OcrPageResult
import com.example.data.model.ParsedDocument
import com.example.data.model.ReaderPreferences
import com.example.data.model.SearchMatch
import com.example.data.model.SelectedWordContext
import com.example.data.model.WikipediaSummary
import com.example.engine.DocumentParser
import com.example.engine.DynamicIslandEventManager
import com.example.engine.MergedDocumentExporter
import com.example.engine.OcrEngine
import com.example.engine.TranslationService
import com.example.ui.components.LumaActionAnimType
import com.example.ui.components.LumaFeedbackEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val document: DocumentEntity? = null,
    val parsed: ParsedDocument? = null,
    val currentUnitIndex: Int = 0,
    val currentSheetIndex: Int = 0,
    val totalSheetCount: Int = 1,
    val activeSheetLocationKey: String? = null,
    val jumpToChapterTrigger: Int = 0,
    val isDockExpanded: Boolean = false,
    val isDrawingMode: Boolean = false,
    val isPanZoomWhileDrawing: Boolean = false,
    val viewAsOriginalFormat: Boolean = false,
    val activeTool: DrawingTool = DrawingTool.PEN,
    val activeColorArgb: Long = 0xFF1D5BFFL,
    val activeWidthNorm: Float = 0.008f,
    val activeAlpha: Float = 1f,
    val activeShapeFilled: Boolean = false,
    val customDrawingColors: List<Long> = emptyList(),
    val selectedWord: SelectedWordContext? = null,
    val instantTranslation: String? = null,
    val isTranslatingWord: Boolean = false,
    val wordTranslationStatus: String = "",
    val wikiSummary: WikipediaSummary? = null,
    val wikiError: String? = null,
    val isWikiLoading: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val showTranslatedManhwaOverlay: Boolean = false,
    val isProcessingOcrOrTranslation: Boolean = false,
    val ocrTranslationStatus: String = "",
    val isExporting: Boolean = false,
    val feedbackEvent: LumaFeedbackEvent? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class ReaderViewModel(
    application: Application,
    private val documentId: Long,
    private val repository: FolioRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPreferences()
    )

    val highlights: StateFlow<List<HighlightNoteEntity>> = repository.observeHighlights(documentId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeBookmarks(documentId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val drawingsByLocation: StateFlow<Map<String, List<DrawingStroke>>> =
        repository.observeDrawingsMap(documentId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val ocrByPage: StateFlow<Map<Int, OcrPageResult>> = repository.observeOcrPagesMap(documentId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val dictionarySources: StateFlow<List<DictionarySourceEntity>> =
        repository.dictionarySources.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Bounded in-memory window (max 3 pages) of rendered PDF bitmaps for on-demand OCR/AI translation
    private val pageBitmaps = LruCache<Int, Bitmap>(3)
    private var ocrTranslationJob: Job? = null

    // Undo / Redo stacks per locationKey
    private val undoStacks = mutableMapOf<String, ArrayDeque<List<DrawingStroke>>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<List<DrawingStroke>>>()
    private var wordLookupJob: Job? = null

    init {
        loadDocument()
    }

    override fun onCleared() {
        super.onCleared()
        ocrTranslationJob?.cancel()
        wordLookupJob?.cancel()
        pageBitmaps.evictAll()
    }

    fun flushReadingStateAsync() {
        val state = _uiState.value
        val doc = state.document ?: return
        val parsed = state.parsed
        val total = when {
            parsed == null -> doc.totalUnits.coerceAtLeast(1)
            parsed.format == DocumentFormat.PDF -> parsed.pdfPageCount.coerceAtLeast(1)
            parsed.format == DocumentFormat.PPTX -> parsed.slides.size.coerceAtLeast(1)
            else -> parsed.chapters.size.coerceAtLeast(1)
        }
        val unit = state.currentUnitIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        val progress = if (total <= 1) 1f else ((unit + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching { repository.updateProgress(doc.id, unit, progress) }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val doc = repository.getDocumentById(documentId)
                    ?: error("Documento no encontrado en la biblioteca.")
                val file = File(doc.filePath)
                if (!file.exists()) error("El archivo local ya no existe en el dispositivo.")
                val format = DocumentFormat.fromExtension(doc.format) ?: DocumentFormat.EPUB

                val initialOriginalView = when (format) {
                    DocumentFormat.EPUB -> false
                    DocumentFormat.PDF, DocumentFormat.PPTX -> true
                    DocumentFormat.DOCX, DocumentFormat.TXT, DocumentFormat.FB2 -> preferences.value.useStandardPageView
                }

                var hasDeliveredInitial = false
                fun publishParsed(parsed: ParsedDocument, isInitial: Boolean) {
                    val totalUnits = when (format) {
                        DocumentFormat.PDF -> parsed.pdfPageCount
                        DocumentFormat.PPTX -> parsed.slides.size.coerceAtLeast(1)
                        else -> parsed.chapters.size
                    }
                    val targetUnit = if (isInitial) {
                        doc.currentUnit.coerceIn(0, (totalUnits - 1).coerceAtLeast(0))
                    } else {
                        _uiState.value.currentUnitIndex.coerceIn(0, (totalUnits - 1).coerceAtLeast(0))
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            document = doc,
                            parsed = parsed,
                            currentUnitIndex = targetUnit,
                            viewAsOriginalFormat = if (isInitial) initialOriginalView else it.viewAsOriginalFormat
                        )
                    }
                    if (isInitial) refreshUndoRedoFlags()
                }

                val fullParsed = DocumentParser.parseProgressive(
                    context = getApplication(),
                    file = file,
                    format = format,
                    priorityUnitIndex = doc.currentUnit,
                    onPartialReady = { partial ->
                        if (!hasDeliveredInitial) {
                            hasDeliveredInitial = true
                            publishParsed(partial, isInitial = true)
                        }
                    }
                )
                publishParsed(fullParsed, isInitial = !hasDeliveredInitial)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "No se pudo abrir el libro.")
                }
            }
        }
    }

    fun currentLocationKey(): String {
        val state = _uiState.value
        val parsed = state.parsed ?: return "unit-0"
        return if (parsed.format == DocumentFormat.PDF && state.viewAsOriginalFormat) {
            "pdf-page-${state.currentUnitIndex}"
        } else {
            state.activeSheetLocationKey
                ?: parsed.chapters.getOrNull(state.currentUnitIndex)?.cfiPrefix
                ?: "epubcfi(/6/2!)"
        }
    }

    fun onFluidSheetChanged(sheetIndex: Int, totalSheets: Int, locationKey: String, chapterIndex: Int) {
        val safeTotal = totalSheets.coerceAtLeast(1)
        val clampedSheet = sheetIndex.coerceIn(0, safeTotal - 1)
        val progress = if (safeTotal <= 1) 1f else (clampedSheet + 1).toFloat() / safeTotal.toFloat()
        _uiState.update {
            it.copy(
                currentUnitIndex = chapterIndex,
                currentSheetIndex = clampedSheet,
                totalSheetCount = safeTotal,
                activeSheetLocationKey = locationKey
            )
        }
        refreshUndoRedoFlags()
        viewModelScope.launch {
            repository.updateProgress(documentId, chapterIndex, progress)
        }
    }

    fun onUnitChanged(newIndex: Int, isExplicitJump: Boolean = false) {
        val state = _uiState.value
        val parsed = state.parsed ?: return
        val total = if (parsed.format == DocumentFormat.PDF) parsed.pdfPageCount else parsed.chapters.size
        val clamped = newIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (clamped == state.currentUnitIndex && !isExplicitJump) return

        val progress = if (total <= 1) 1f else (clamped + 1).toFloat() / total.toFloat()
        _uiState.update {
            it.copy(
                currentUnitIndex = clamped,
                selectedWord = null,
                jumpToChapterTrigger = if (isExplicitJump) it.jumpToChapterTrigger + 1 else it.jumpToChapterTrigger
            )
        }
        refreshUndoRedoFlags()

        viewModelScope.launch {
            repository.updateProgress(documentId, clamped, progress)
        }
    }

    fun toggleDock(expanded: Boolean? = null) {
        _uiState.update { it.copy(isDockExpanded = expanded ?: !it.isDockExpanded, selectedWord = null) }
    }

    fun toggleDrawingMode(enabled: Boolean? = null) {
        _uiState.update {
            val next = enabled ?: !it.isDrawingMode
            it.copy(
                isDrawingMode = next,
                isPanZoomWhileDrawing = if (!next) false else it.isPanZoomWhileDrawing,
                isDockExpanded = false,
                selectedWord = null
            )
        }
        refreshUndoRedoFlags()
    }

    fun togglePanZoomWhileDrawing() {
        _uiState.update { it.copy(isPanZoomWhileDrawing = !it.isPanZoomWhileDrawing) }
    }

    fun applySignaturePreset() {
        _uiState.update {
            it.copy(
                isPanZoomWhileDrawing = false,
                activeTool = DrawingTool.PEN,
                activeColorArgb = 0xFF141B29L,
                activeWidthNorm = 0.0042f,
                activeAlpha = 1f,
                activeShapeFilled = false
            )
        }
    }

    fun setViewAsOriginalFormat(original: Boolean) {
        val format = _uiState.value.parsed?.format ?: return
        if (format == DocumentFormat.EPUB) return
        _uiState.update {
            it.copy(
                viewAsOriginalFormat = original,
                isPanZoomWhileDrawing = false,
                selectedWord = null
            )
        }
        if (format == DocumentFormat.DOCX || format == DocumentFormat.TXT || format == DocumentFormat.FB2) {
            updatePreferences { it.copy(useStandardPageView = original) }
        }
        refreshUndoRedoFlags()
    }

    fun toggleStandardPageViewForCurrentDoc() {
        setViewAsOriginalFormat(!_uiState.value.viewAsOriginalFormat)
    }

    fun setDrawingTool(tool: DrawingTool) {
        _uiState.update { current ->
            val nextAlpha = when {
                tool == DrawingTool.HIGHLIGHTER && current.activeTool != DrawingTool.HIGHLIGHTER -> 0.36f
                tool != DrawingTool.HIGHLIGHTER && current.activeTool == DrawingTool.HIGHLIGHTER && current.activeAlpha <= 0.42f -> 1f
                else -> current.activeAlpha
            }
            current.copy(
                activeTool = tool,
                activeAlpha = nextAlpha,
                isPanZoomWhileDrawing = false
            )
        }
    }

    fun setDrawingColor(argb: Long) {
        _uiState.update { it.copy(activeColorArgb = argb, isPanZoomWhileDrawing = false) }
    }

    fun addCustomDrawingColor(argb: Long) {
        _uiState.update { current ->
            val updated = (listOf(argb) + current.customDrawingColors.filterNot { it == argb }).take(8)
            current.copy(
                activeColorArgb = argb,
                customDrawingColors = updated,
                isPanZoomWhileDrawing = false
            )
        }
    }

    fun setDrawingWidth(widthNorm: Float) {
        _uiState.update { it.copy(activeWidthNorm = widthNorm) }
    }

    fun setDrawingAlpha(alpha: Float) {
        _uiState.update { it.copy(activeAlpha = alpha.coerceIn(0.15f, 1f)) }
    }

    fun toggleDrawingShapeFilled() {
        _uiState.update { it.copy(activeShapeFilled = !it.activeShapeFilled) }
    }

    fun addStroke(locationKey: String, stroke: DrawingStroke) {
        val current = drawingsByLocation.value[locationKey].orEmpty()
        pushUndoSnapshot(locationKey, current)
        val updated = current + stroke
        viewModelScope.launch {
            repository.saveStrokes(documentId, locationKey, updated)
        }
        refreshUndoRedoFlags()
    }

    fun eraseStrokes(locationKey: String, idsToRemove: Set<String>) {
        if (idsToRemove.isEmpty()) return
        val current = drawingsByLocation.value[locationKey].orEmpty()
        val updated = current.filterNot { it.id in idsToRemove }
        if (updated.size == current.size) return
        pushUndoSnapshot(locationKey, current)
        viewModelScope.launch {
            repository.saveStrokes(documentId, locationKey, updated)
        }
        refreshUndoRedoFlags()
    }

    fun clearCurrentPageDrawings() {
        val loc = currentLocationKey()
        val current = drawingsByLocation.value[loc].orEmpty()
        if (current.isEmpty()) return
        pushUndoSnapshot(loc, current)
        viewModelScope.launch {
            repository.saveStrokes(documentId, loc, emptyList())
        }
        refreshUndoRedoFlags()
    }

    fun undoDrawing() {
        val loc = currentLocationKey()
        val stack = undoStacks[loc] ?: return
        if (stack.isEmpty()) return
        val current = drawingsByLocation.value[loc].orEmpty()
        redoStacks.getOrPut(loc) { ArrayDeque() }.addLast(current)
        val previous = stack.removeLast()
        viewModelScope.launch {
            repository.saveStrokes(documentId, loc, previous)
        }
        refreshUndoRedoFlags()
    }

    fun redoDrawing() {
        val loc = currentLocationKey()
        val stack = redoStacks[loc] ?: return
        if (stack.isEmpty()) return
        val current = drawingsByLocation.value[loc].orEmpty()
        undoStacks.getOrPut(loc) { ArrayDeque() }.addLast(current)
        val next = stack.removeLast()
        viewModelScope.launch {
            repository.saveStrokes(documentId, loc, next)
        }
        refreshUndoRedoFlags()
    }

    private fun pushUndoSnapshot(loc: String, snapshot: List<DrawingStroke>) {
        val stack = undoStacks.getOrPut(loc) { ArrayDeque() }
        stack.addLast(snapshot)
        if (stack.size > 30) stack.removeFirst()
        redoStacks[loc]?.clear()
    }

    private fun refreshUndoRedoFlags() {
        val loc = currentLocationKey()
        _uiState.update {
            it.copy(
                canUndo = !undoStacks[loc].isNullOrEmpty(),
                canRedo = !redoStacks[loc].isNullOrEmpty()
            )
        }
    }

    // Bookmarks
    fun toggleBookmarkCurrentPage() {
        val loc = currentLocationKey()
        val existing = bookmarks.value.firstOrNull { it.locationKey == loc }
        viewModelScope.launch {
            if (existing != null) {
                repository.deleteBookmarkByLocation(documentId, loc)
                emitFeedback(LumaActionAnimType.BOOKMARK_REMOVED)
            } else {
                val parsed = _uiState.value.parsed
                val unit = _uiState.value.currentUnitIndex
                val label = when {
                    parsed?.format == DocumentFormat.PDF -> "Página ${unit + 1}"
                    parsed?.format == DocumentFormat.PPTX -> "Diapositiva ${unit + 1}"
                    loc.startsWith("std-page-") -> "Hoja ${_uiState.value.currentSheetIndex + 1}"
                    else -> parsed?.chapters?.getOrNull(unit)?.title ?: "Sección ${unit + 1}"
                }
                repository.addBookmark(
                    BookmarkEntity(
                        documentId = documentId,
                        locationKey = loc,
                        unitIndex = unit,
                        label = label
                    )
                )
                emitFeedback(LumaActionAnimType.BOOKMARK_ADDED)
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
            emitFeedback(LumaActionAnimType.DELETED)
        }
    }

    // Highlights & Notes
    fun addHighlightFromSelection(colorArgb: Long, noteText: String = "") {
        val sel = _uiState.value.selectedWord ?: return
        val unit = _uiState.value.currentUnitIndex
        viewModelScope.launch {
            repository.addHighlight(
                HighlightNoteEntity(
                    documentId = documentId,
                    locationKey = sel.locationKey,
                    chapterIndex = unit,
                    blockIndex = sel.blockIndex,
                    startChar = sel.startChar,
                    endChar = sel.endChar,
                    quotedText = sel.word,
                    colorArgb = colorArgb,
                    noteText = noteText
                )
            )
            _uiState.update { it.copy(selectedWord = null) }
            if (noteText.isNotBlank()) {
                emitFeedback(LumaActionAnimType.SAVED)
            }
        }
    }

    fun updateHighlightNote(id: Long, noteText: String, colorArgb: Long) {
        viewModelScope.launch {
            repository.updateHighlightNote(id, noteText, colorArgb)
            emitFeedback(LumaActionAnimType.SAVED)
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch {
            repository.deleteHighlight(id)
            emitFeedback(LumaActionAnimType.DELETED)
        }
    }

    // Word / Phrase / Sentence Selection -> Instant Cached or Debounced EN/Auto -> ES Translation + Wikipedia ES
    fun onWordSelected(context: SelectedWordContext) {
        wordLookupJob?.cancel()
        val prefs = preferences.value
        val wikiTerm = resolveWikiLookupTerm(context)
        val cachedTrans = TranslationService.getCachedTranslation(context.word, prefs)
        val cachedWiki = if (wikiTerm.isNotBlank()) TranslationService.getCachedWikipediaSummary(wikiTerm) else null

        _uiState.update {
            it.copy(
                selectedWord = context,
                isDockExpanded = false,
                instantTranslation = cachedTrans,
                isTranslatingWord = cachedTrans == null,
                wordTranslationStatus = if (cachedTrans == null) "Traduciendo al español…" else "",
                wikiSummary = cachedWiki,
                wikiError = null,
                isWikiLoading = wikiTerm.isNotBlank() && cachedWiki == null
            )
        }

        if (cachedTrans != null && (wikiTerm.isBlank() || cachedWiki != null)) {
            return
        }

        wordLookupJob = viewModelScope.launch {
            // Brief coalesce delay avoids spamming HTTP calls if user taps across words rapidly
            delay(80L)
            if (cachedTrans == null) {
                launch {
                    val transRes = TranslationService.translateWordToSpanish(
                        word = context.word,
                        contextSnippet = context.snippet,
                        prefs = prefs,
                        onStatus = { status ->
                            _uiState.update { it.copy(wordTranslationStatus = status) }
                        }
                    )
                    _uiState.update {
                        it.copy(
                            isTranslatingWord = false,
                            instantTranslation = transRes.getOrElse { err ->
                                "No se pudo traducir (${err.localizedMessage ?: "sin conexión"})"
                            }
                        )
                    }
                }
            }

            if (wikiTerm.isNotBlank() && cachedWiki == null) {
                launch {
                    val wikiRes = TranslationService.fetchWikipediaSummary(wikiTerm)
                    _uiState.update {
                        it.copy(
                            isWikiLoading = false,
                            wikiSummary = wikiRes.getOrNull(),
                            wikiError = wikiRes.exceptionOrNull()?.localizedMessage
                        )
                    }
                }
            }
        }
    }

    /**
     * Expands or shrinks the active text selection (e.g., Palabra <-> Oración <-> Párrafo)
     * without closing the popover, debouncing network lookups while returning cache hits instantly.
     */
    fun updateSelection(updated: SelectedWordContext) {
        val prev = _uiState.value.selectedWord
        if (prev != null && prev.startChar == updated.startChar && prev.endChar == updated.endChar && prev.word == updated.word) {
            return
        }
        wordLookupJob?.cancel()
        val prefs = preferences.value
        val cachedTrans = TranslationService.getCachedTranslation(updated.word, prefs)
        _uiState.update {
            it.copy(
                selectedWord = updated,
                instantTranslation = cachedTrans,
                isTranslatingWord = cachedTrans == null,
                wordTranslationStatus = if (cachedTrans == null) "Traduciendo selección…" else ""
            )
        }
        if (cachedTrans != null) return

        wordLookupJob = viewModelScope.launch {
            delay(180L)
            val transRes = TranslationService.translateWordToSpanish(
                word = updated.word,
                contextSnippet = updated.snippet,
                prefs = prefs,
                onStatus = { status ->
                    _uiState.update { it.copy(wordTranslationStatus = status) }
                }
            )
            _uiState.update {
                it.copy(
                    isTranslatingWord = false,
                    instantTranslation = transRes.getOrElse { err ->
                        "No se pudo traducir (${err.localizedMessage ?: "sin conexión"})"
                    }
                )
            }
        }
    }

    private fun resolveWikiLookupTerm(context: SelectedWordContext): String {
        val trimmed = context.word.trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 3) return trimmed
        // For full sentences, look up the originally tapped anchor word if available
        if (context.fullBlockText.isNotBlank()) {
            val relStart = (context.wordStartChar - context.blockCharOffset).coerceIn(0, context.fullBlockText.length)
            val relEnd = (context.wordEndChar - context.blockCharOffset).coerceIn(relStart, context.fullBlockText.length)
            val anchorWord = context.fullBlockText.substring(relStart, relEnd).trim()
            if (anchorWord.isNotBlank()) return anchorWord
        }
        return words.firstOrNull().orEmpty()
    }

    fun dismissWordPopover() {
        wordLookupJob?.cancel()
        _uiState.update { it.copy(selectedWord = null) }
    }

    // PDF / Manhwa On-Demand OCR & Full-Page Translation
    // Stores only a bounded 3-page window and NEVER launches ML Kit OCR during scrolling.
    fun onPdfPageBitmapReady(pageIndex: Int, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            pageBitmaps.put(pageIndex, bitmap)
        }
    }

    fun toggleOrRunManhwaPageTranslation() {
        val pageIndex = _uiState.value.currentUnitIndex
        val existing = ocrByPage.value[pageIndex]

        // If already translated and persisted in Room ocr_page_cache, simply toggle overlay visibility
        if (existing != null && existing.isTranslated) {
            _uiState.update { it.copy(showTranslatedManhwaOverlay = !it.showTranslatedManhwaOverlay) }
            return
        }

        val bitmap = pageBitmaps.get(pageIndex)?.takeIf { !it.isRecycled }
        if (bitmap == null) {
            emitFeedback(LumaActionAnimType.ERROR, "Espera a que cargue la página PDF")
            return
        }

        // Single-flight: cancel any previous OCR/translation job before launching the new page
        ocrTranslationJob?.cancel()
        ocrTranslationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingOcrOrTranslation = true,
                    ocrTranslationStatus = "Detectando globos de texto con OCR…"
                )
            }
            try {
                // Re-check Room ocr_page_cache first; only run ML Kit OCR on-demand on cache miss
                val ocr = existing ?: OcrEngine.recognizePage(bitmap, pageIndex).also {
                    repository.saveOcrPage(documentId, it)
                }
                if (ocr.blocks.isEmpty()) {
                    emitFeedback(LumaActionAnimType.ERROR, "Sin texto detectado en esta página")
                    return@launch
                }

                val translatedRes = TranslationService.translateManhwaBlocks(
                    blocks = ocr.blocks,
                    pageBitmap = bitmap,
                    prefs = preferences.value,
                    onProgress = { msg ->
                        _uiState.update { it.copy(ocrTranslationStatus = msg) }
                    }
                )

                val translatedBlocks = translatedRes.getOrThrow()
                val updatedOcr = ocr.copy(blocks = translatedBlocks, isTranslated = true)
                repository.saveOcrPage(documentId, updatedOcr)
                _uiState.update { it.copy(showTranslatedManhwaOverlay = true) }
                emitFeedback(LumaActionAnimType.TRANSLATED)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                emitFeedback(LumaActionAnimType.ERROR, e.localizedMessage ?: "Revisa tu conexión")
            } finally {
                _uiState.update { it.copy(isProcessingOcrOrTranslation = false) }
            }
        }
    }

    // In-Book Text Search (works across EPUB/DOCX/TXT/FB2 + PDF text + OCR cached pages!)
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(searchMatches = emptyList()) }
            return
        }
        val parsed = _uiState.value.parsed ?: return
        viewModelScope.launch {
            val matches = withContext(Dispatchers.Default) {
                val found = mutableListOf<SearchMatch>()
                if (parsed.format == DocumentFormat.PDF) {
                    val ocrMap = ocrByPage.value
                    for (p in 0 until parsed.pdfPageCount) {
                        val nativeText = parsed.pdfPageTexts.getOrNull(p).orEmpty()
                        val ocrText = ocrMap[p]?.blocks?.joinToString(" ") { it.originalText }.orEmpty()
                        val combined = if (nativeText.isNotBlank()) nativeText else ocrText
                        val idx = combined.indexOf(trimmed, ignoreCase = true)
                        if (idx >= 0) {
                            val start = (idx - 40).coerceAtLeast(0)
                            val end = (idx + trimmed.length + 70).coerceAtMost(combined.length)
                            found.add(
                                SearchMatch(
                                    targetIndex = p,
                                    chapterTitle = "Página ${p + 1}",
                                    snippet = "…${combined.substring(start, end).trim()}…"
                                )
                            )
                        }
                    }
                } else {
                    for (chapter in parsed.chapters) {
                        for (block in chapter.blocks) {
                            val idx = block.text.indexOf(trimmed, ignoreCase = true)
                            if (idx >= 0) {
                                val start = (idx - 35).coerceAtLeast(0)
                                val end = (idx + trimmed.length + 75).coerceAtMost(block.text.length)
                                found.add(
                                    SearchMatch(
                                        targetIndex = chapter.index,
                                        chapterTitle = chapter.title,
                                        snippet = "…${block.text.substring(start, end).trim()}…",
                                        blockIndex = block.index
                                    )
                                )
                                if (found.size >= 40) break
                            }
                        }
                        if (found.size >= 40) break
                    }
                }
                found
            }
            _uiState.update { it.copy(searchMatches = matches) }
        }
    }

    // Merged Export (Save to SAF Uri OR Export to FileProvider cache to share)
    fun exportMergedToUri(uri: Uri, option: ExportFormatOption, onFinished: () -> Unit) {
        val doc = _uiState.value.document ?: return
        val parsed = _uiState.value.parsed ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val drawings = repository.getDrawingsMapOnce(documentId)
                val hl = repository.getHighlightsOnce(documentId)
                val ocr = repository.getOcrPagesMapOnce(documentId)
                val res = MergedDocumentExporter.exportToUri(
                    context = getApplication(),
                    document = doc,
                    parsed = parsed,
                    drawingsByLocation = drawings,
                    highlights = hl,
                    ocrByPage = ocr,
                    formatOption = option,
                    destinationUri = uri,
                    standardPageFormat = preferences.value.standardPageFormat
                )
                if (res.isSuccess) {
                    emitFeedback(LumaActionAnimType.EXPORTED)
                    onFinished()
                } else {
                    emitFeedback(LumaActionAnimType.ERROR, res.exceptionOrNull()?.localizedMessage ?: "Error al exportar")
                }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun exportMergedForShare(option: ExportFormatOption, onFileReady: (File, String) -> Unit) {
        val doc = _uiState.value.document ?: return
        val parsed = _uiState.value.parsed ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val drawings = repository.getDrawingsMapOnce(documentId)
                val hl = repository.getHighlightsOnce(documentId)
                val ocr = repository.getOcrPagesMapOnce(documentId)
                val res = MergedDocumentExporter.exportToInternalCache(
                    context = getApplication(),
                    document = doc,
                    parsed = parsed,
                    drawingsByLocation = drawings,
                    highlights = hl,
                    ocrByPage = ocr,
                    formatOption = option,
                    standardPageFormat = preferences.value.standardPageFormat
                )
                val file = res.getOrNull()
                if (file != null) {
                    val mime = MergedDocumentExporter.resolveOutputMimeType(doc, option)
                    emitFeedback(LumaActionAnimType.EXPORTED)
                    onFileReady(file, mime)
                } else {
                    emitFeedback(LumaActionAnimType.ERROR, res.exceptionOrNull()?.localizedMessage ?: "Error al exportar")
                }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { preferencesRepository.updatePreferences(transform) }
    }

    fun addDictionarySource(name: String, urlTemplate: String) {
        viewModelScope.launch {
            repository.addDictionarySource(name, urlTemplate)
            emitFeedback(LumaActionAnimType.SAVED)
        }
    }

    fun toggleDictionarySource(source: DictionarySourceEntity) {
        viewModelScope.launch { repository.toggleDictionarySource(source) }
    }

    fun deleteDictionarySource(id: Long) {
        viewModelScope.launch {
            repository.deleteDictionarySource(id)
            emitFeedback(LumaActionAnimType.DELETED)
        }
    }

    fun clearFeedbackEvent() {
        _uiState.update { it.copy(feedbackEvent = null) }
    }

    private fun emitFeedback(type: LumaActionAnimType, errorDetail: String? = null) {
        if (type == LumaActionAnimType.ERROR) {
            DynamicIslandEventManager.postCriticalError(
                title = "Error en lectura",
                detail = errorDetail ?: "No se pudo completar la operación"
            )
            return
        }
        _uiState.update { it.copy(feedbackEvent = LumaFeedbackEvent(type = type, errorDetail = errorDetail)) }
    }

    class Factory(
        private val application: Application,
        private val documentId: Long,
        private val repository: FolioRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(application, documentId, repository, preferencesRepository) as T
        }
    }
}

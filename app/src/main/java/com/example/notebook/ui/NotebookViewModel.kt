package com.example.notebook.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.engine.CriticalEventKind
import com.example.engine.DynamicIslandEventManager
import com.example.notebook.data.NotebookAssetStore
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageEntity
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.data.NotebookPreferencesStore
import com.example.notebook.data.NotebookRepository
import com.example.notebook.engine.NotebookExportOptions
import com.example.notebook.engine.NotebookExportProgress
import com.example.notebook.engine.NotebookExporter
import com.example.notebook.engine.NotebookMlTools
import com.example.notebook.engine.NotebookStudyHelpers
import com.example.notebook.model.BuiltInStickerKind
import com.example.notebook.model.ConnectorObject
import com.example.notebook.model.ImageObject
import com.example.notebook.model.NbPoint
import com.example.notebook.model.NotebookAlignAction
import com.example.notebook.model.NotebookCommand
import com.example.notebook.model.NotebookEraserMode
import com.example.notebook.model.NotebookJsonCodec
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookLayerInfo
import com.example.notebook.model.NotebookObject
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.NotebookQuickStyle
import com.example.notebook.model.NotebookShapeKind
import com.example.notebook.model.NotebookStarterTemplate
import com.example.notebook.model.NotebookTextSpan
import com.example.notebook.model.NotebookTool
import com.example.notebook.model.StickerObject
import com.example.notebook.model.StrokeObject
import com.example.notebook.model.TextAlignMode
import com.example.notebook.model.TextObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot

data class NotebookStudioUiState(
    val notebook: NotebookEntity? = null,
    val pages: List<NotebookPageEntity> = emptyList(),
    val currentPageIndex: Int = 0,
    val contentByPageId: Map<Long, NotebookPageContent> = emptyMap(),
    val activeTool: NotebookTool = NotebookTool.PEN,
    val activeShapeKind: NotebookShapeKind = NotebookShapeKind.ARROW,
    val activeStickerKind: BuiltInStickerKind = BuiltInStickerKind.CHECK,
    val activeColorArgb: Long = 0xFF1D5BFFL,
    val activeHighlighterColorArgb: Long = 0xFFFDE047L,
    val activeStrokeWidthNorm: Float = 0.0042f,
    val activeAlpha: Float = 1f,
    val activeStabilizerLevel: Float = 0.28f,
    val eraserMode: NotebookEraserMode = NotebookEraserMode.STROKE,
    val stylusOnlyMode: Boolean = false,
    val lockAspectRatio: Boolean = false,
    val isEyedropperActive: Boolean = false,
    val activeLayerId: String = NotebookLayerIds.INK,
    val activeShapeFillEnabled: Boolean = false,
    val activeShapeDashed: Boolean = false,
    val activeShapeShadow: Boolean = false,
    val savedColors: List<Long> = emptyList(),
    val recentColors: List<Long> = emptyList(),
    val favoriteFontIds: List<String> = emptyList(),
    val recentFontIds: List<String> = emptyList(),
    val customQuickStyles: List<NotebookQuickStyle> = emptyList(),
    val myStickerPaths: List<String> = emptyList(),
    val selectedObjectIds: Set<String> = emptySet(),
    val copiedObjects: List<NotebookObject> = emptyList(),
    val pendingConnectorFromId: String? = null,
    // Preserved across configuration changes / screen rotation inside ViewModel
    val zoomScale: Float = 1f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val exportProgress: NotebookExportProgress? = null,
    val loadedBitmaps: Map<String, Bitmap> = emptyMap()
) {
    val currentPage: NotebookPageEntity?
        get() = pages.getOrNull(currentPageIndex)

    val currentContent: NotebookPageContent
        get() = currentPage?.let { contentByPageId[it.id] } ?: NotebookPageContent()

    val selectedObjectId: String?
        get() = selectedObjectIds.singleOrNull()

    val selectedObjects: List<NotebookObject>
        get() = if (selectedObjectIds.isEmpty()) emptyList() else currentContent.objects.filter { it.id in selectedObjectIds }

    val selectedObject: NotebookObject?
        get() = selectedObjectId?.let { sid -> currentContent.findById(sid) }
}

/**
 * Isolated ViewModel for the Study Notebook Studio.
 * Instantiated strictly lazily when opening a Notebook (zero cold-start impact).
 *
 * Key capabilities:
 * - UNLIMITED Command-Pattern Undo/Redo per page (single & multi-object commands, layers, pixel-eraser splits).
 * - DataStore persistence for saved/recent colors (max 12), stylus-only mode, stabilizer, eraser mode, aspect lock.
 * - Rotation-safe page index, zoom, pan, active tool, and active layer.
 * - Cross-page object clipboard, grouping/ungrouping, alignment/distribution, flip, and per-object opacity.
 * - Debounced atomic auto-save (~800 ms) + immediate ON_STOP / exit flush via Room @Transaction.
 */
class NotebookViewModel(
    application: Application,
    private val notebookId: Long,
    private val repository: NotebookRepository
) : AndroidViewModel(application) {

    private val prefsStore by lazy { NotebookPreferencesStore(application) }

    private val _uiState = MutableStateFlow(NotebookStudioUiState())
    val uiState: StateFlow<NotebookStudioUiState> = _uiState.asStateFlow()

    private val undoStacks = HashMap<Long, ArrayDeque<NotebookCommand>>()
    private val redoStacks = HashMap<Long, ArrayDeque<NotebookCommand>>()

    // Continuous gesture tracking so an entire single- or multi-object drag/resize/rotate commits as ONE command
    private var continuousTransformBefore: List<NotebookObject> = emptyList()
    private var continuousTransformPageId: Long? = null

    private val dirtyPageIds = HashSet<Long>()
    private var saveJob: Job? = null
    private var thumbJob: Job? = null
    private var preloadJob: Job? = null
    private var exportJob: Job? = null
    private var mlJob: Job? = null

    init {
        viewModelScope.launch {
            prefsStore.prefsFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        savedColors = prefs.savedColors,
                        recentColors = prefs.recentColors,
                        stylusOnlyMode = prefs.stylusOnlyMode,
                        activeStabilizerLevel = prefs.stabilizerLevel,
                        eraserMode = prefs.eraserMode,
                        lockAspectRatio = prefs.lockAspectRatio,
                        favoriteFontIds = prefs.favoriteFontIds,
                        recentFontIds = prefs.recentFontIds,
                        customQuickStyles = prefs.customQuickStyles,
                        myStickerPaths = prefs.myStickerPaths
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeNotebook(notebookId).collect { nb ->
                _uiState.update { it.copy(notebook = nb) }
            }
        }
        viewModelScope.launch {
            repository.observePages(notebookId).collect { pages ->
                val currentMap = _uiState.value.contentByPageId
                val updatedMap = HashMap(currentMap)
                for (page in pages) {
                    if (!updatedMap.containsKey(page.id) && page.id !in dirtyPageIds) {
                        updatedMap[page.id] = withContext(Dispatchers.Default) {
                            NotebookJsonCodec.decode(page.contentJson)
                        }
                    }
                }
                _uiState.update { st ->
                    val clampedIdx = st.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    val curId = pages.getOrNull(clampedIdx)?.id
                    st.copy(
                        pages = pages,
                        currentPageIndex = clampedIdx,
                        contentByPageId = updatedMap,
                        canUndo = curId?.let { undoStacks[it]?.isNotEmpty() == true } ?: false,
                        canRedo = curId?.let { redoStacks[it]?.isNotEmpty() == true } ?: false
                    )
                }
                preloadAdjacentPages()
            }
        }
    }

    // ---- Viewport & Rotation-Preserved Zoom/Pan ----

    fun setZoomPan(scale: Float, panX: Float, panY: Float) {
        _uiState.update {
            it.copy(
                zoomScale = scale.coerceIn(0.5f, 5.0f),
                panOffsetX = panX,
                panOffsetY = panY
            )
        }
    }

    fun resetZoomPan() {
        _uiState.update { it.copy(zoomScale = 1f, panOffsetX = 0f, panOffsetY = 0f) }
    }

    // ---- Page Switching & Structure ----

    fun selectPage(index: Int, resetZoom: Boolean = true) {
        val pages = _uiState.value.pages
        if (index !in pages.indices) return
        commitContinuousTransform()
        flushPendingSaveAsync()
        val pageId = pages[index].id
        _uiState.update {
            it.copy(
                currentPageIndex = index,
                selectedObjectIds = emptySet(),
                pendingConnectorFromId = null,
                isEyedropperActive = false,
                zoomScale = if (resetZoom) 1f else it.zoomScale,
                panOffsetX = if (resetZoom) 0f else it.panOffsetX,
                panOffsetY = if (resetZoom) 0f else it.panOffsetY,
                canUndo = undoStacks[pageId]?.isNotEmpty() == true,
                canRedo = redoStacks[pageId]?.isNotEmpty() == true
            )
        }
        preloadAdjacentPages()
    }

    // ---- Tool, Color, Stabilizer, Stylus & Eyedropper Controls ----

    fun selectTool(tool: NotebookTool) {
        _uiState.update {
            it.copy(
                activeTool = tool,
                isEyedropperActive = false,
                selectedObjectIds = if (tool == NotebookTool.SELECT) it.selectedObjectIds else emptySet(),
                pendingConnectorFromId = if (tool == NotebookTool.CONNECTOR) it.pendingConnectorFromId else null
            )
        }
    }

    fun selectShapeKind(kind: NotebookShapeKind) {
        _uiState.update {
            it.copy(
                activeTool = NotebookTool.SHAPE,
                activeShapeKind = kind,
                isEyedropperActive = false,
                selectedObjectIds = emptySet(),
                pendingConnectorFromId = null
            )
        }
    }

    fun selectStickerKind(kind: BuiltInStickerKind) {
        _uiState.update {
            it.copy(
                activeTool = NotebookTool.STICKER,
                activeStickerKind = kind,
                isEyedropperActive = false,
                selectedObjectIds = emptySet(),
                pendingConnectorFromId = null
            )
        }
    }

    fun selectColor(colorArgb: Long) {
        _uiState.update { st ->
            if (st.activeTool == NotebookTool.HIGHLIGHTER) {
                st.copy(activeHighlighterColorArgb = colorArgb, isEyedropperActive = false)
            } else {
                st.copy(activeColorArgb = colorArgb, isEyedropperActive = false)
            }
        }
        viewModelScope.launch { prefsStore.recordRecentColor(colorArgb) }
    }

    fun saveColorFavorite(colorArgb: Long) {
        viewModelScope.launch {
            prefsStore.saveColor(colorArgb)
            _uiState.update { it.copy(statusMessage = "Color guardado en favoritos") }
        }
    }

    fun removeSavedColor(colorArgb: Long) {
        viewModelScope.launch { prefsStore.removeSavedColor(colorArgb) }
    }

    fun toggleEyedropper(enable: Boolean? = null) {
        _uiState.update { st ->
            val next = enable ?: !st.isEyedropperActive
            st.copy(
                isEyedropperActive = next,
                statusMessage = if (next) "Cuentagotas activo · toca cualquier punto de la hoja" else null
            )
        }
    }

    fun applyEyedroppedColor(colorArgb: Long) {
        selectColor(colorArgb)
        val hex = String.format("#%06X", colorArgb.toInt() and 0xFFFFFF)
        _uiState.update { it.copy(isEyedropperActive = false, statusMessage = "Color capturado $hex") }
    }

    fun setStrokeWidthNorm(widthNorm: Float) {
        _uiState.update { it.copy(activeStrokeWidthNorm = widthNorm.coerceIn(0.0014f, 0.022f)) }
    }

    fun setAlpha(alpha: Float) {
        _uiState.update { it.copy(activeAlpha = alpha.coerceIn(0.15f, 1f)) }
    }

    fun setStabilizerLevel(level: Float) {
        val clamped = level.coerceIn(0f, 0.85f)
        _uiState.update { it.copy(activeStabilizerLevel = clamped) }
        viewModelScope.launch { prefsStore.setStabilizerLevel(clamped) }
    }

    fun setEraserMode(mode: NotebookEraserMode) {
        _uiState.update { it.copy(eraserMode = mode) }
        viewModelScope.launch { prefsStore.setEraserMode(mode) }
    }

    fun setStylusOnlyMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                stylusOnlyMode = enabled,
                statusMessage = if (enabled) "Modo Solo lápiz activado" else "Dibujo con dedo y lápiz"
            )
        }
        viewModelScope.launch { prefsStore.setStylusOnlyMode(enabled) }
    }

    fun setLockAspectRatio(locked: Boolean) {
        _uiState.update { it.copy(lockAspectRatio = locked) }
        viewModelScope.launch { prefsStore.setLockAspectRatio(locked) }
    }

    fun toggleShapeFill() {
        _uiState.update { it.copy(activeShapeFillEnabled = !it.activeShapeFillEnabled) }
    }

    fun toggleShapeDashed() {
        _uiState.update { it.copy(activeShapeDashed = !it.activeShapeDashed) }
    }

    fun toggleShapeShadow() {
        _uiState.update { it.copy(activeShapeShadow = !it.activeShapeShadow) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // ---- Layers CRUD, Reordering, Visibility, Lock & Opacity ----

    fun selectActiveLayer(layerId: String) {
        _uiState.update { it.copy(activeLayerId = layerId) }
    }

    fun addLayer() {
        val content = _uiState.value.currentContent
        val current = content.resolvedLayers()
        val newId = "layer_${UUID.randomUUID().toString().take(8)}"
        val newLayer = NotebookLayerInfo(
            id = newId,
            name = "Capa ${current.size + 1}",
            visible = true,
            locked = false,
            opacity = 1f
        )
        executeCommand(NotebookCommand.UpdateLayers(before = current, after = current + newLayer), selectIdsAfter = _uiState.value.selectedObjectIds)
        _uiState.update { it.copy(activeLayerId = newId, statusMessage = "${newLayer.name} añadida") }
    }

    fun toggleLayerVisible(layerId: String) {
        val current = _uiState.value.currentContent.resolvedLayers()
        val updated = current.map { if (it.id == layerId) it.copy(visible = !it.visible) else it }
        executeCommand(NotebookCommand.UpdateLayers(before = current, after = updated), selectIdsAfter = emptySet())
    }

    fun toggleLayerLocked(layerId: String) {
        val current = _uiState.value.currentContent.resolvedLayers()
        val updated = current.map { if (it.id == layerId) it.copy(locked = !it.locked) else it }
        executeCommand(NotebookCommand.UpdateLayers(before = current, after = updated), selectIdsAfter = emptySet())
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        val current = _uiState.value.currentContent.resolvedLayers()
        val clamped = opacity.coerceIn(0.1f, 1f)
        val updated = current.map { if (it.id == layerId) it.copy(opacity = clamped) else it }
        executeCommand(NotebookCommand.UpdateLayers(before = current, after = updated), selectIdsAfter = _uiState.value.selectedObjectIds)
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.currentContent.resolvedLayers()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val mutable = current.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        executeCommand(NotebookCommand.UpdateLayers(before = current, after = mutable), selectIdsAfter = _uiState.value.selectedObjectIds)
    }

    fun deleteLayer(layerId: String) {
        if (layerId in NotebookLayerIds.ORDERED) return
        val content = _uiState.value.currentContent
        val current = content.resolvedLayers()
        if (current.size <= 1) return
        val remaining = current.filterNot { it.id == layerId }
        val removedObjs = content.objects.filter { it.layerId == layerId }
        if (removedObjs.isNotEmpty()) {
            executeCommand(NotebookCommand.RemoveObjects(removedObjs), selectIdsAfter = emptySet())
        }
        executeCommand(NotebookCommand.UpdateLayers(before = current, after = remaining), selectIdsAfter = emptySet())
        if (_uiState.value.activeLayerId == layerId) {
            _uiState.update { it.copy(activeLayerId = NotebookLayerIds.INK) }
        }
    }

    // ---- Notebook Metadata, Templates, Paper Background & Page Size ----

    fun renameNotebook(newTitle: String) {
        viewModelScope.launch {
            repository.renameNotebook(notebookId, newTitle)
            _uiState.update { it.copy(statusMessage = "Título actualizado") }
        }
    }

    fun updateTemplate(template: NotebookPageTemplate) {
        viewModelScope.launch {
            repository.updateNotebookTemplate(notebookId, template)
            scheduleThumbnailRefresh()
        }
    }

    fun updateNotebookPageSize(widthPx: Int, heightPx: Int) {
        viewModelScope.launch {
            repository.updateNotebookPageSize(notebookId, widthPx, heightPx)
            _uiState.update { it.copy(statusMessage = "Tamaño de hoja actualizado (${widthPx}×${heightPx})") }
            scheduleThumbnailRefresh()
        }
    }

    fun updateCurrentPageBackgroundColor(colorArgb: Long) {
        val page = _uiState.value.currentPage ?: return
        viewModelScope.launch {
            repository.updatePageBackground(
                notebookId = notebookId,
                pageId = page.id,
                backgroundType = NotebookBackgroundType.TEMPLATE,
                backgroundColor = colorArgb,
                backgroundAssetPath = ""
            )
            scheduleThumbnailRefresh()
        }
    }

    fun setCurrentPageBackgroundImage(uri: Uri) {
        val page = _uiState.value.currentPage ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val imported = repository.importAssetToNotebook(notebookId, uri)
                if (imported != null) {
                    repository.updatePageBackground(
                        notebookId = notebookId,
                        pageId = page.id,
                        backgroundType = NotebookBackgroundType.IMAGE,
                        backgroundColor = 0xFFFFFFFFL,
                        backgroundAssetPath = imported.relativeFileName
                    )
                    loadAssetIntoState(imported.relativeFileName, targetMaxDimPx = 1600)
                    scheduleThumbnailRefresh()
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun addBlankPage() {
        val currentBg = _uiState.value.currentPage?.backgroundColor ?: 0xFFFFFFFFL
        viewModelScope.launch {
            flushPendingSaveAsync()
            repository.addPage(
                notebookId = notebookId,
                backgroundType = NotebookBackgroundType.TEMPLATE,
                backgroundColor = currentBg
            )
            delay(80L)
            val lastIdx = (_uiState.value.pages.size - 1).coerceAtLeast(0)
            selectPage(lastIdx)
        }
    }

    fun addPagesFromPdfUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Importando páginas del PDF…") }
            try {
                val ctx = getApplication<Application>()
                val tmp = withContext(Dispatchers.IO) {
                    val f = File(ctx.cacheDir, "nb_add_pdf_${System.currentTimeMillis()}.pdf")
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(f).use { out -> input.copyTo(out) }
                    }
                    f
                }
                val rendered = NotebookAssetStore.renderPdfPagesToAssets(ctx, notebookId, tmp)
                runCatching { tmp.delete() }
                for (asset in rendered) {
                    repository.addPage(
                        notebookId = notebookId,
                        backgroundType = NotebookBackgroundType.PDF_PAGE,
                        backgroundColor = 0xFFFFFFFFL,
                        backgroundAssetPath = asset.relativeFileName
                    )
                }
                _uiState.update {
                    it.copy(statusMessage = if (rendered.isEmpty()) "No se pudo leer el PDF" else "${rendered.size} páginas añadidas")
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun duplicateCurrentPage() {
        val page = _uiState.value.currentPage ?: return
        viewModelScope.launch {
            flushPendingSaveAsync()
            val latestJson = NotebookJsonCodec.encode(_uiState.value.currentContent)
            repository.duplicatePage(notebookId, page.copy(contentJson = latestJson))
            selectPage((_uiState.value.currentPageIndex + 1).coerceAtLeast(0))
        }
    }

    fun deletePage(pageId: Long) {
        if (_uiState.value.pages.size <= 1) return
        viewModelScope.launch {
            repository.deletePage(notebookId, pageId)
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.movePage(notebookId, fromIndex, toIndex)
            if (_uiState.value.currentPageIndex == fromIndex) {
                _uiState.update { it.copy(currentPageIndex = toIndex) }
            }
        }
    }

    // ---- Selection, Multi-Selection, Grouping, Align/Distribute & Clipboard ----

    fun selectObject(objectId: String?) {
        if (objectId == null) {
            _uiState.update { it.copy(selectedObjectIds = emptySet()) }
            return
        }
        val content = _uiState.value.currentContent
        val target = content.findById(objectId)
        if (target == null) {
            _uiState.update { it.copy(selectedObjectIds = emptySet()) }
            return
        }
        val groupId = target.groupId
        val ids = if (groupId != null) {
            content.objects.filter { it.groupId == groupId && it.visible }.map { it.id }.toSet()
        } else {
            setOf(objectId)
        }
        _uiState.update { it.copy(selectedObjectIds = ids) }
    }

    fun selectObjects(ids: Set<String>) {
        if (ids.isEmpty()) {
            _uiState.update { it.copy(selectedObjectIds = emptySet()) }
            return
        }
        val content = _uiState.value.currentContent
        val expanded = HashSet<String>(ids)
        val groupIds = content.objects.filter { it.id in ids }.mapNotNull { it.groupId }.toSet()
        if (groupIds.isNotEmpty()) {
            content.objects.filter { it.groupId in groupIds }.forEach { expanded.add(it.id) }
        }
        _uiState.update { it.copy(selectedObjectIds = expanded) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedObjectIds = emptySet()) }
    }

    fun copySelectedObjects() {
        val selected = _uiState.value.selectedObjects
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(
                copiedObjects = selected,
                statusMessage = if (selected.size == 1) "Objeto copiado" else "${selected.size} objetos copiados"
            )
        }
    }

    fun pasteCopiedObjects() {
        val clip = _uiState.value.copiedObjects
        if (clip.isEmpty()) return
        val content = _uiState.value.currentContent
        var nextZ = content.nextZIndex()
        val groupMap = HashMap<String, String>()
        val clones = clip.map { orig ->
            val mappedGroup = orig.groupId?.let { oldG -> groupMap.getOrPut(oldG) { "grp_${UUID.randomUUID().toString().take(8)}" } }
            orig.duplicateOffset().withZIndex(nextZ++).withGroup(mappedGroup)
        }
        val newIds = clones.map { it.id }.toSet()
        executeCommand(NotebookCommand.AddObjects(clones), selectIdsAfter = newIds)
        _uiState.update { it.copy(activeTool = NotebookTool.SELECT, statusMessage = "${clones.size} pegados") }
    }

    fun groupSelected() {
        val selected = _uiState.value.selectedObjects.filterNot { it.locked }
        if (selected.size < 2) return
        val newGroupId = "grp_${UUID.randomUUID().toString().take(8)}"
        val updated = selected.map { it.withGroup(newGroupId) }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
        _uiState.update { it.copy(statusMessage = "${selected.size} objetos agrupados") }
    }

    fun ungroupSelected() {
        val selected = _uiState.value.selectedObjects.filter { it.groupId != null && !it.locked }
        if (selected.isEmpty()) return
        val updated = selected.map { it.withGroup(null) }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
        _uiState.update { it.copy(statusMessage = "Grupo separado") }
    }

    fun flipSelectedHorizontal() {
        val selected = _uiState.value.selectedObjects.filterNot { it.locked }
        if (selected.isEmpty()) return
        val updated = selected.map { it.flippedHorizontal() }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
    }

    fun flipSelectedVertical() {
        val selected = _uiState.value.selectedObjects.filterNot { it.locked }
        if (selected.isEmpty()) return
        val updated = selected.map { it.flippedVertical() }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
    }

    fun setSelectedOpacity(opacity: Float) {
        val selected = _uiState.value.selectedObjects.filterNot { it.locked }
        if (selected.isEmpty()) return
        val clamped = opacity.coerceIn(0.1f, 1f)
        val updated = selected.map { it.withOpacity(clamped) }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
    }

    fun alignSelected(action: NotebookAlignAction) {
        val selected = _uiState.value.selectedObjects.filter { !it.locked && it !is ConnectorObject }
        if (selected.isEmpty()) return

        val updated: List<NotebookObject> = if (selected.size == 1) {
            val obj = selected.first()
            val moved = when (action) {
                NotebookAlignAction.ALIGN_LEFT -> obj.movedBy(0.05f - obj.x, 0f)
                NotebookAlignAction.ALIGN_CENTER_H -> obj.movedBy(0.5f - obj.centerX, 0f)
                NotebookAlignAction.ALIGN_RIGHT -> obj.movedBy(0.95f - (obj.x + obj.width), 0f)
                NotebookAlignAction.ALIGN_TOP -> obj.movedBy(0f, 0.05f - obj.y)
                NotebookAlignAction.ALIGN_CENTER_V -> obj.movedBy(0f, 0.5f - obj.centerY)
                NotebookAlignAction.ALIGN_BOTTOM -> obj.movedBy(0f, 0.95f - (obj.y + obj.height))
                NotebookAlignAction.DISTRIBUTE_H, NotebookAlignAction.DISTRIBUTE_V -> obj
            }
            listOf(moved)
        } else {
            val minL = selected.minOf { it.x }
            val maxR = selected.maxOf { it.x + it.width }
            val minT = selected.minOf { it.y }
            val maxB = selected.maxOf { it.y + it.height }
            val midX = (minL + maxR) * 0.5f
            val midY = (minT + maxB) * 0.5f

            when (action) {
                NotebookAlignAction.ALIGN_LEFT -> selected.map { it.movedBy(minL - it.x, 0f) }
                NotebookAlignAction.ALIGN_CENTER_H -> selected.map { it.movedBy(midX - it.centerX, 0f) }
                NotebookAlignAction.ALIGN_RIGHT -> selected.map { it.movedBy(maxR - (it.x + it.width), 0f) }
                NotebookAlignAction.ALIGN_TOP -> selected.map { it.movedBy(0f, minT - it.y) }
                NotebookAlignAction.ALIGN_CENTER_V -> selected.map { it.movedBy(0f, midY - it.centerY) }
                NotebookAlignAction.ALIGN_BOTTOM -> selected.map { it.movedBy(0f, maxB - (it.y + it.height)) }
                NotebookAlignAction.DISTRIBUTE_H -> {
                    if (selected.size < 3) selected else {
                        val sorted = selected.sortedBy { it.centerX }
                        val firstC = sorted.first().centerX
                        val lastC = sorted.last().centerX
                        val step = (lastC - firstC) / (sorted.size - 1)
                        sorted.mapIndexed { idx, obj -> obj.movedBy((firstC + step * idx) - obj.centerX, 0f) }
                    }
                }
                NotebookAlignAction.DISTRIBUTE_V -> {
                    if (selected.size < 3) selected else {
                        val sorted = selected.sortedBy { it.centerY }
                        val firstC = sorted.first().centerY
                        val lastC = sorted.last().centerY
                        val step = (lastC - firstC) / (sorted.size - 1)
                        sorted.mapIndexed { idx, obj -> obj.movedBy(0f, (firstC + step * idx) - obj.centerY) }
                    }
                }
            }
        }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
    }

    // ---- Command-Based Unified Object Mutations (Unlimited Undo/Redo) ----

    fun addStroke(stroke: StrokeObject) {
        val st = _uiState.value
        val content = st.currentContent
        val targetLayer = if (stroke.isHighlighterUnderlay && !content.isLayerLocked(NotebookLayerIds.UNDERLAY)) {
            NotebookLayerIds.UNDERLAY
        } else {
            st.activeLayerId
        }
        if (content.isLayerLocked(targetLayer)) {
            _uiState.update { it.copy(statusMessage = "La capa activa está bloqueada") }
            return
        }
        val withLayerAndZ = stroke.withLayer(targetLayer).withZIndex(content.nextZIndex())
        executeCommand(NotebookCommand.AddObject(withLayerAndZ), selectIdsAfter = emptySet())
    }

    fun eraseStrokesByIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val content = _uiState.value.currentContent
        val toRemove = content.objects.filter {
            it is StrokeObject && it.id in ids && !it.locked && !content.isLayerLocked(it.layerId) && content.isLayerVisible(it.layerId)
        }
        if (toRemove.isEmpty()) return
        executeCommand(NotebookCommand.RemoveObjects(toRemove), selectIdsAfter = emptySet())
    }

    /**
     * Real Pixel Eraser (Borrador por píxel): carves out points within [radiusNorm] from hit StrokeObjects
     * and splits surviving stroke segments cleanly, preserving 100% vector quality and Undo/Redo!
     */
    fun erasePixelsAt(normX: Float, normY: Float, radiusNorm: Float) {
        val content = _uiState.value.currentContent
        val hitBefore = ArrayList<NotebookObject>()
        val replacementsAfter = ArrayList<NotebookObject>()

        for (obj in content.objects) {
            if (obj !is StrokeObject || !obj.visible || obj.locked) continue
            if (content.isLayerLocked(obj.layerId) || !content.isLayerVisible(obj.layerId)) continue
            // Quick AABB reject
            if (normX < obj.x - radiusNorm || normX > obj.x + obj.width + radiusNorm ||
                normY < obj.y - radiusNorm || normY > obj.y + obj.height + radiusNorm
            ) continue

            val segments = ArrayList<List<NbPoint>>()
            var currentSeg = ArrayList<NbPoint>()
            var hitAny = false
            for (pt in obj.points) {
                val dist = hypot((pt.x - normX).toDouble(), (pt.y - normY).toDouble()).toFloat()
                if (dist <= radiusNorm) {
                    hitAny = true
                    if (currentSeg.size >= 2) {
                        segments.add(currentSeg)
                    }
                    currentSeg = ArrayList()
                } else {
                    currentSeg.add(pt)
                }
            }
            if (hitAny) {
                if (currentSeg.size >= 2) {
                    segments.add(currentSeg)
                }
                hitBefore.add(obj)
                for (seg in segments) {
                    val b = StrokeObject.computeBounds(seg)
                    replacementsAfter.add(
                        obj.copy(
                            id = UUID.randomUUID().toString(),
                            x = b[0],
                            y = b[1],
                            width = b[2],
                            height = b[3],
                            points = seg
                        )
                    )
                }
            }
        }

        if (hitBefore.isNotEmpty()) {
            executeCommand(NotebookCommand.ReplaceObjects(hitBefore, replacementsAfter), selectIdsAfter = emptySet())
        }
    }

    fun addObject(obj: NotebookObject, selectAfter: Boolean = true) {
        val content = _uiState.value.currentContent
        val preferredLayer = if (obj.layerId == NotebookLayerIds.CONTENT && !content.isLayerLocked(NotebookLayerIds.CONTENT)) {
            NotebookLayerIds.CONTENT
        } else {
            _uiState.value.activeLayerId
        }
        val withZ = obj.withLayer(preferredLayer).withZIndex(content.nextZIndex())
        executeCommand(
            command = NotebookCommand.AddObject(withZ),
            selectIdsAfter = if (selectAfter) setOf(withZ.id) else emptySet()
        )
    }

    fun upsertObject(obj: NotebookObject, selectAfter: Boolean = true) {
        val content = _uiState.value.currentContent
        val existing = content.findById(obj.id)
        if (existing != null) {
            if (existing == obj) return
            executeCommand(
                command = NotebookCommand.ReplaceObject(before = existing, after = obj),
                selectIdsAfter = if (selectAfter) setOf(obj.id) else emptySet()
            )
        } else {
            addObject(obj, selectAfter = selectAfter)
        }
    }

    fun handleConnectorObjectTap(tappedObjectId: String) {
        val st = _uiState.value
        val fromId = st.pendingConnectorFromId
        if (fromId == null) {
            _uiState.update {
                it.copy(
                    pendingConnectorFromId = tappedObjectId,
                    statusMessage = "Origen elegido · toca el objeto destino"
                )
            }
        } else if (fromId == tappedObjectId) {
            _uiState.update {
                it.copy(
                    pendingConnectorFromId = null,
                    statusMessage = "Conector cancelado"
                )
            }
        } else {
            val connector = ConnectorObject(
                fromObjectId = fromId,
                toObjectId = tappedObjectId,
                colorArgb = st.activeColorArgb,
                strokeWidthNorm = st.activeStrokeWidthNorm.coerceAtLeast(0.0036f),
                isDashed = st.activeShapeDashed,
                isCurved = true,
                opacity = st.activeAlpha
            )
            addObject(connector, selectAfter = false)
            _uiState.update {
                it.copy(
                    pendingConnectorFromId = null,
                    statusMessage = "Conector anclado (sigue al mover)"
                )
            }
        }
    }

    /**
     * Continuous drag/resize/rotate for 1 or more objects -> commits as ONE single undo command on lift.
     */
    fun beginContinuousTransform(objects: List<NotebookObject>) {
        val page = _uiState.value.currentPage ?: return
        continuousTransformBefore = objects
        continuousTransformPageId = page.id
    }

    fun updateObjectsLiveNoUndo(updatedList: List<NotebookObject>) {
        val page = _uiState.value.currentPage ?: return
        if (updatedList.isEmpty()) return
        val byId = updatedList.associateBy { it.id }
        _uiState.update { st ->
            val current = st.contentByPageId[page.id] ?: NotebookPageContent()
            val nextContent = current.copy(
                objects = current.objects.map { byId[it.id] ?: it },
                revision = current.revision + 1
            )
            val map = HashMap(st.contentByPageId)
            map[page.id] = nextContent
            st.copy(contentByPageId = map)
        }
    }

    fun commitContinuousTransform() {
        val beforeList = continuousTransformBefore
        val pageId = continuousTransformPageId
        continuousTransformBefore = emptyList()
        continuousTransformPageId = null
        if (beforeList.isEmpty() || pageId == null) return

        val content = _uiState.value.contentByPageId[pageId] ?: return
        val afterList = beforeList.mapNotNull { content.findById(it.id) }
        if (afterList.size != beforeList.size || afterList == beforeList) return

        val cmd = if (beforeList.size == 1) {
            NotebookCommand.ReplaceObject(beforeList.first(), afterList.first())
        } else {
            NotebookCommand.ReplaceObjects(beforeList, afterList)
        }
        val undo = undoStacks.getOrPut(pageId) { ArrayDeque() }
        undo.addLast(cmd)
        redoStacks[pageId]?.clear()

        synchronized(dirtyPageIds) { dirtyPageIds.add(pageId) }
        _uiState.update { it.copy(canUndo = undo.isNotEmpty(), canRedo = false) }
        scheduleDebouncedSave()
        scheduleThumbnailRefresh()
    }

    fun toggleLockSelected() {
        val selected = _uiState.value.selectedObjects
        if (selected.isEmpty()) return
        val targetLock = selected.any { !it.locked }
        val updated = selected.map { it.withLocked(targetLock) }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
        _uiState.update {
            it.copy(statusMessage = if (targetLock) "Bloqueado" else "Desbloqueado")
        }
    }

    fun duplicateSelectedObject() {
        val selected = _uiState.value.selectedObjects
        if (selected.isEmpty()) return
        var nextZ = _uiState.value.currentContent.nextZIndex()
        val copies = selected.map { it.duplicateOffset().withZIndex(nextZ++) }
        executeCommand(NotebookCommand.AddObjects(copies), selectIdsAfter = copies.map { it.id }.toSet())
    }

    fun bringSelectedToFront() {
        val selected = _uiState.value.selectedObjects
        if (selected.isEmpty()) return
        var maxZ = _uiState.value.currentContent.nextZIndex()
        val updated = selected.sortedBy { it.zIndex }.map { it.withZIndex(maxZ++) }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
    }

    fun sendSelectedToBack() {
        val selected = _uiState.value.selectedObjects
        if (selected.isEmpty()) return
        var minZ = (_uiState.value.currentContent.objects.minOfOrNull { it.zIndex } ?: 1) - selected.size
        val updated = selected.sortedBy { it.zIndex }.map { it.withZIndex(minZ++) }
        executeCommand(NotebookCommand.ReplaceObjects(selected, updated), selectIdsAfter = updated.map { it.id }.toSet())
    }

    fun deleteSelectedObject() {
        val selected = _uiState.value.selectedObjects
        if (selected.isEmpty()) return
        val unlocked = selected.filterNot { it.locked }
        if (unlocked.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Desbloquea el objeto antes de eliminarlo") }
            return
        }
        val deletedIds = unlocked.map { it.id }.toSet()
        val attachedConnectors = _uiState.value.currentContent.objects.filter {
            it is ConnectorObject && (it.fromObjectId in deletedIds || it.toObjectId in deletedIds)
        }
        executeCommand(NotebookCommand.RemoveObjects(unlocked + attachedConnectors), selectIdsAfter = emptySet())
    }

    fun clearCurrentPage() {
        val objs = _uiState.value.currentContent.objects
        if (objs.isEmpty()) return
        executeCommand(NotebookCommand.ClearPage(objs), selectIdsAfter = emptySet())
    }

    fun importImageObjectFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val imported = repository.importAssetToNotebook(notebookId, uri)
                if (imported != null) {
                    val nb = _uiState.value.notebook
                    val pageAspect = if (nb != null) {
                        nb.pageHeightPx.toFloat() / nb.pageWidthPx.coerceAtLeast(1)
                    } else 1.414f
                    val imgAspect = imported.heightPx.toFloat() / imported.widthPx.coerceAtLeast(1)
                    val normW = 0.44f
                    val normH = (normW * imgAspect / pageAspect).coerceIn(0.10f, 0.62f)
                    val imgObj = ImageObject(
                        x = (0.5f - normW * 0.5f).coerceIn(0.05f, 0.5f),
                        y = (0.45f - normH * 0.5f).coerceIn(0.08f, 0.5f),
                        width = normW,
                        height = normH,
                        relativeAssetPath = imported.relativeFileName,
                        cornerRadiusDp = 10f
                    )
                    loadAssetIntoState(imported.relativeFileName, targetMaxDimPx = 1280)
                    addObject(imgObj, selectAfter = true)
                    _uiState.update { it.copy(activeTool = NotebookTool.SELECT) }
                } else {
                    _uiState.update { it.copy(statusMessage = "No se pudo importar la imagen") }
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    // ---- Phase 3 Study Tools: Fonts, Quick Styles, User PNG Stickers, Clipboard Image & Starter Templates ----

    fun toggleFavoriteFont(fontId: String) {
        val canonical = NotebookFonts.canonicalId(fontId)
        viewModelScope.launch { prefsStore.toggleFavoriteFont(canonical) }
    }

    fun recordRecentFont(fontId: String) {
        val canonical = NotebookFonts.canonicalId(fontId)
        viewModelScope.launch { prefsStore.recordRecentFont(canonical) }
    }

    fun saveQuickStyle(style: NotebookQuickStyle) {
        viewModelScope.launch {
            prefsStore.saveQuickStyle(style)
            _uiState.update { it.copy(statusMessage = "Estilo guardado: ${style.name}") }
        }
    }

    fun deleteQuickStyle(styleId: String) {
        viewModelScope.launch { prefsStore.deleteQuickStyle(styleId) }
    }

    fun addMyStickerFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val absPath = NotebookAssetStore.importUserStickerUri(getApplication(), uri)
                if (absPath != null) {
                    prefsStore.addMyStickerPath(absPath)
                    loadAssetIntoState(absPath, targetMaxDimPx = 768)
                    val sticker = StickerObject(
                        layerId = _uiState.value.activeLayerId,
                        x = 0.40f,
                        y = 0.38f,
                        width = 0.18f,
                        height = 0.14f,
                        customAssetPath = absPath,
                        opacity = _uiState.value.activeAlpha
                    )
                    addObject(sticker, selectAfter = true)
                    _uiState.update { it.copy(activeTool = NotebookTool.SELECT, statusMessage = "Sticker PNG guardado en Mis stickers") }
                } else {
                    _uiState.update { it.copy(statusMessage = "No se pudo importar el sticker PNG") }
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun placeCustomSticker(absolutePath: String) {
        viewModelScope.launch {
            val file = File(absolutePath)
            if (!file.exists()) {
                prefsStore.removeMyStickerPath(absolutePath)
                _uiState.update { it.copy(statusMessage = "El archivo del sticker ya no existe") }
                return@launch
            }
            loadAssetIntoState(absolutePath, targetMaxDimPx = 768)
            val sticker = StickerObject(
                layerId = _uiState.value.activeLayerId,
                x = 0.40f,
                y = 0.38f,
                width = 0.18f,
                height = 0.14f,
                customAssetPath = absolutePath,
                opacity = _uiState.value.activeAlpha
            )
            addObject(sticker, selectAfter = true)
            _uiState.update { it.copy(activeTool = NotebookTool.SELECT) }
        }
    }

    fun pasteImageFromClipboard() {
        val ctx = getApplication<Application>()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            _uiState.update { it.copy(statusMessage = "El portapapeles está vacío") }
            return
        }
        var imageUri: Uri? = null
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i)?.uri ?: continue
            val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull().orEmpty()
            if (mime.startsWith("image/") || uri.toString().lowercase().let {
                    it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".webp")
                }
            ) {
                imageUri = uri
                break
            }
        }
        if (imageUri != null) {
            importImageObjectFromUri(imageUri)
            _uiState.update { it.copy(statusMessage = "Imagen pegada desde el portapapeles") }
        } else {
            _uiState.update { it.copy(statusMessage = "No hay imagen en el portapapeles") }
        }
    }

    fun applyStarterTemplate(template: NotebookStarterTemplate) {
        val content = _uiState.value.currentContent
        val objs = NotebookStudyHelpers.buildStarterTemplateObjects(template, startZ = content.nextZIndex())
        if (objs.isEmpty()) return
        executeCommand(NotebookCommand.AddObjects(objs), selectIdsAfter = emptySet())
        _uiState.update {
            it.copy(
                activeTool = NotebookTool.SELECT,
                statusMessage = "Plantilla aplicada: ${template.label}"
            )
        }
    }

    fun undo() {
        commitContinuousTransform()
        val page = _uiState.value.currentPage ?: return
        val undoStack = undoStacks[page.id] ?: return
        if (undoStack.isEmpty()) return
        val cmd = undoStack.removeLast()
        val redoStack = redoStacks.getOrPut(page.id) { ArrayDeque() }
        redoStack.addLast(cmd)

        val current = _uiState.value.currentContent
        val reverted = cmd.revert(current)
        applyContentState(page.id, reverted, selectedIds = emptySet())
    }

    fun redo() {
        commitContinuousTransform()
        val page = _uiState.value.currentPage ?: return
        val redoStack = redoStacks[page.id] ?: return
        if (redoStack.isEmpty()) return
        val cmd = redoStack.removeLast()
        val undoStack = undoStacks.getOrPut(page.id) { ArrayDeque() }
        undoStack.addLast(cmd)

        val current = _uiState.value.currentContent
        val reapplied = cmd.apply(current)
        applyContentState(page.id, reapplied, selectedIds = emptySet())
    }

    // ---- Phase 3 Optional ML Kit Tools (Strictly Lazy, On-Device) ----

    /**
     * Removes background of [imageObject] using on-device ML Kit Subject Segmentation,
     * saves a transparent alpha WebP cutout, and swaps the object via undoable Command.
     */
    fun removeBackgroundFromImage(imageObject: ImageObject) {
        if (mlJob?.isActive == true) return
        val ctx = getApplication<Application>()
        mlJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Quitando fondo con IA…") }
            DynamicIslandEventManager.postOperationStatus(
                kind = CriticalEventKind.AI_TASK,
                badgeLabel = "RECORTE IA",
                title = "Quitando fondo…",
                subtitle = "Procesando sujeto en el dispositivo",
                accentColorArgb = 0xFF7C3AEDL,
                durationMs = 8000L,
                dedupeKey = "nb_ml_task"
            )
            try {
                val srcBitmap = _uiState.value.loadedBitmaps[imageObject.relativeAssetPath]
                    ?: NotebookAssetStore.loadBitmap(ctx, notebookId, imageObject.relativeAssetPath, 1600)
                if (srcBitmap == null) {
                    _uiState.update { it.copy(statusMessage = "No se encontró la imagen original") }
                    return@launch
                }
                val result = NotebookMlTools.removeImageBackground(ctx, srcBitmap)
                result.fold(
                    onSuccess = { cutoutBmp ->
                        val saved = NotebookAssetStore.saveTransparentBitmapToNotebook(ctx, notebookId, cutoutBmp)
                        if (saved != null) {
                            loadAssetIntoState(saved.relativeFileName, targetMaxDimPx = 1280)
                            val updated = imageObject.copy(relativeAssetPath = saved.relativeFileName)
                            upsertObject(updated, selectAfter = true)
                            _uiState.update { it.copy(statusMessage = "Fondo eliminado (PNG/WebP transparente)") }
                            DynamicIslandEventManager.postOperationStatus(
                                kind = CriticalEventKind.EXPORT_DONE,
                                badgeLabel = "RECORTE IA",
                                title = "Fondo eliminado",
                                subtitle = "Transparencia aplicada (puedes deshacer)",
                                accentColorArgb = 0xFF10B981L,
                                durationMs = 3400L,
                                dedupeKey = "nb_ml_task"
                            )
                        } else {
                            _uiState.update { it.copy(statusMessage = "No se pudo guardar el recorte") }
                        }
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "No se pudo quitar el fondo"
                        _uiState.update { it.copy(statusMessage = msg) }
                        DynamicIslandEventManager.postOperationStatus(
                            kind = CriticalEventKind.CRITICAL_ERROR,
                            badgeLabel = "RECORTE IA",
                            title = "No se pudo recortar",
                            subtitle = msg,
                            accentColorArgb = 0xFFEF4444L,
                            durationMs = 4600L,
                            dedupeKey = "nb_ml_task"
                        )
                    }
                )
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    /**
     * Converts selected handwritten [StrokeObject]s into an editable Spanish [TextObject]
     * using on-device ML Kit Digital Ink Recognition (100% undoable!).
     */
    fun convertSelectedStrokesToText() {
        if (mlJob?.isActive == true) return
        val selectedStrokes = _uiState.value.selectedObjects.filterIsInstance<StrokeObject>().filterNot { it.locked }
        if (selectedStrokes.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Selecciona trazos manuscritos primero") }
            return
        }
        val nb = _uiState.value.notebook
        val pageW = (nb?.pageWidthPx ?: 1240).toFloat()
        val pageH = (nb?.pageHeightPx ?: 1754).toFloat()

        mlJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Convirtiendo escritura a texto…") }
            DynamicIslandEventManager.postOperationStatus(
                kind = CriticalEventKind.AI_TASK,
                badgeLabel = "MANUSCRITO A TEXTO",
                title = "Reconociendo escritura…",
                subtitle = "Procesando ${selectedStrokes.size} trazos en español",
                accentColorArgb = 0xFF1D5BFFL,
                durationMs = 9000L,
                dedupeKey = "nb_ml_task"
            )
            try {
                val result = NotebookMlTools.recognizeHandwritingToSpanishText(
                    strokes = selectedStrokes,
                    pageWidthPx = pageW,
                    pageHeightPx = pageH,
                    onStatus = { status ->
                        _uiState.update { it.copy(statusMessage = status) }
                        DynamicIslandEventManager.postOperationStatus(
                            kind = CriticalEventKind.AI_TASK,
                            badgeLabel = "MANUSCRITO A TEXTO",
                            title = status,
                            subtitle = "Idioma: Español",
                            accentColorArgb = 0xFF1D5BFFL,
                            durationMs = 9000L,
                            dedupeKey = "nb_ml_task"
                        )
                    }
                )
                result.fold(
                    onSuccess = { recognizedText ->
                        val minX = selectedStrokes.minOf { it.x }.coerceIn(0.03f, 0.85f)
                        val minY = selectedStrokes.minOf { it.y }.coerceIn(0.03f, 0.85f)
                        val maxX = selectedStrokes.maxOf { it.x + it.width }
                        val maxY = selectedStrokes.maxOf { it.y + it.height }
                        val boxW = (maxX - minX + 0.04f).coerceIn(0.24f, 0.90f)
                        val boxH = (maxY - minY + 0.03f).coerceIn(0.08f, 0.65f)
                        val primaryInk = selectedStrokes.first().colorArgb

                        val textObj = TextObject(
                            layerId = selectedStrokes.first().layerId,
                            zIndex = _uiState.value.currentContent.nextZIndex(),
                            x = minX.coerceAtMost(1f - boxW - 0.02f).coerceAtLeast(0.02f),
                            y = minY.coerceAtMost(1f - boxH - 0.02f).coerceAtLeast(0.02f),
                            width = boxW,
                            height = boxH,
                            spans = listOf(
                                NotebookTextSpan(
                                    text = recognizedText,
                                    colorArgb = primaryInk
                                )
                            ),
                            fontId = NotebookFonts.DEFAULT_BODY_ID,
                            fontSizeNorm = 0.022f,
                            alignment = TextAlignMode.START,
                            fillColorArgb = null,
                            borderColorArgb = null
                        )
                        executeCommand(
                            command = NotebookCommand.ReplaceObjects(
                                beforeList = selectedStrokes,
                                afterList = listOf(textObj)
                            ),
                            selectIdsAfter = setOf(textObj.id)
                        )
                        _uiState.update {
                            it.copy(
                                activeTool = NotebookTool.SELECT,
                                statusMessage = "Convertido: \"${recognizedText.take(32)}\""
                            )
                        }
                        DynamicIslandEventManager.postOperationStatus(
                            kind = CriticalEventKind.EXPORT_DONE,
                            badgeLabel = "MANUSCRITO A TEXTO",
                            title = "Texto reconocido",
                            subtitle = recognizedText.take(48),
                            accentColorArgb = 0xFF10B981L,
                            durationMs = 3600L,
                            dedupeKey = "nb_ml_task"
                        )
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "No se reconoció texto"
                        _uiState.update { it.copy(statusMessage = msg) }
                        DynamicIslandEventManager.postOperationStatus(
                            kind = CriticalEventKind.CRITICAL_ERROR,
                            badgeLabel = "MANUSCRITO A TEXTO",
                            title = "Sin resultado",
                            subtitle = msg,
                            accentColorArgb = 0xFFEF4444L,
                            durationMs = 4600L,
                            dedupeKey = "nb_ml_task"
                        )
                    }
                )
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    // ---- Phase 4: Fast, Cancellable Off-Main-Thread Export with Progress & Dynamic Island ----

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update {
            it.copy(
                isBusy = false,
                exportProgress = null,
                statusMessage = "Exportación cancelada"
            )
        }
        DynamicIslandEventManager.postOperationStatus(
            kind = CriticalEventKind.CRITICAL_ERROR,
            badgeLabel = "EXPORTACIÓN",
            title = "Exportación cancelada",
            subtitle = "No se guardaron cambios pendientes",
            accentColorArgb = 0xFFF59E0BL,
            durationMs = 2800L,
            dedupeKey = "nb_export_status"
        )
    }

    private fun reportExportProgress(progress: NotebookExportProgress) {
        _uiState.update { it.copy(exportProgress = progress) }
        val pct = (progress.fraction * 100f).toInt().coerceIn(0, 100)
        DynamicIslandEventManager.postOperationStatus(
            kind = CriticalEventKind.EXPORT_PROGRESS,
            badgeLabel = "EXPORTANDO · $pct%",
            title = progress.message,
            subtitle = _uiState.value.notebook?.title ?: "Cuaderno Luma",
            accentColorArgb = 0xFF1D5BFFL,
            durationMs = 10_000L,
            dedupeKey = "nb_export_status"
        )
    }

    fun exportToUri(uri: Uri, options: NotebookExportOptions) {
        val nb = _uiState.value.notebook ?: return
        val pages = _uiState.value.pages
        val curPage = _uiState.value.currentPage ?: return
        val map = _uiState.value.contentByPageId

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    exportProgress = NotebookExportProgress(0.02f, "Iniciando exportación…")
                )
            }
            try {
                val ok = NotebookExporter.exportToUri(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    currentPage = curPage,
                    contentByPageId = map,
                    options = options,
                    destinationUri = uri,
                    onProgress = ::reportExportProgress
                )
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = if (ok) "${options.format.label} guardado correctamente" else "No se pudo guardar el archivo"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = if (ok) CriticalEventKind.EXPORT_DONE else CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = if (ok) "EXPORTACIÓN LISTA" else "ERROR AL EXPORTAR",
                    title = if (ok) "${options.format.label} guardado" else "No se pudo exportar",
                    subtitle = nb.title,
                    accentColorArgb = if (ok) 0xFF10B981L else 0xFFEF4444L,
                    durationMs = 4000L,
                    dedupeKey = "nb_export_status"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = t.localizedMessage ?: "Error al exportar"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = "ERROR AL EXPORTAR",
                    title = "Falló la exportación",
                    subtitle = t.localizedMessage ?: "Intenta nuevamente",
                    accentColorArgb = 0xFFEF4444L,
                    durationMs = 4500L,
                    dedupeKey = "nb_export_status"
                )
            }
        }
    }

    fun saveToDeviceDirect(
        options: NotebookExportOptions,
        onNeedSafFallback: (suggestedName: String, mimeType: String) -> Unit
    ) {
        val nb = _uiState.value.notebook ?: return
        val pages = _uiState.value.pages
        val curPage = _uiState.value.currentPage ?: return
        val map = _uiState.value.contentByPageId

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            onNeedSafFallback(
                NotebookExporter.suggestedFileName(nb.title, curPage.pageIndex + 1, options),
                options.format.mimeType
            )
            return
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    exportProgress = NotebookExportProgress(0.02f, "Guardando en el dispositivo…")
                )
            }
            try {
                val savedUri = NotebookExporter.saveToDeviceMediaStore(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    currentPage = curPage,
                    contentByPageId = map,
                    options = options,
                    onProgress = ::reportExportProgress
                )
                val ok = savedUri != null
                val destLabel = if (options.format.mimeType.startsWith("image/")) "Imágenes/Luma" else "Descargas/Luma"
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = if (ok) "Guardado en $destLabel" else "No se pudo guardar"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = if (ok) CriticalEventKind.EXPORT_DONE else CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = if (ok) "GUARDADO" else "ERROR",
                    title = if (ok) "${options.format.label} (${options.scale.label}) guardado" else "No se pudo guardar",
                    subtitle = if (ok) "Carpeta $destLabel" else nb.title,
                    accentColorArgb = if (ok) 0xFF10B981L else 0xFFEF4444L,
                    durationMs = 4200L,
                    dedupeKey = "nb_export_status"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = t.localizedMessage ?: "Error al guardar"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = "ERROR AL GUARDAR",
                    title = "No se pudo guardar",
                    subtitle = t.localizedMessage ?: "Revisa el espacio disponible",
                    accentColorArgb = 0xFFEF4444L,
                    durationMs = 4400L,
                    dedupeKey = "nb_export_status"
                )
            }
        }
    }

    fun shareExport(
        options: NotebookExportOptions,
        onIntentReady: (android.content.Intent) -> Unit
    ) {
        val nb = _uiState.value.notebook ?: return
        val pages = _uiState.value.pages
        val curPage = _uiState.value.currentPage ?: return
        val map = _uiState.value.contentByPageId

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    exportProgress = NotebookExportProgress(0.02f, "Preparando para compartir…")
                )
            }
            try {
                val outFile = NotebookExporter.exportToCacheFile(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    currentPage = curPage,
                    contentByPageId = map,
                    options = options,
                    onProgress = ::reportExportProgress
                )
                val shareIntent = NotebookExporter.buildShareIntent(
                    context = getApplication(),
                    file = outFile,
                    mimeType = options.format.mimeType,
                    title = "Compartir ${nb.title}"
                )
                _uiState.update { it.copy(isBusy = false, exportProgress = null) }
                DynamicIslandEventManager.postOperationStatus(
                    kind = CriticalEventKind.EXPORT_DONE,
                    badgeLabel = "LISTO PARA COMPARTIR",
                    title = "${options.format.label} (${options.scale.label}) listo",
                    subtitle = nb.title,
                    accentColorArgb = 0xFF10B981L,
                    durationMs = 3400L,
                    dedupeKey = "nb_export_status"
                )
                onIntentReady(shareIntent)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = t.localizedMessage ?: "Error al compartir"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = "ERROR AL COMPARTIR",
                    title = "No se pudo compartir",
                    subtitle = t.localizedMessage ?: "Intenta nuevamente",
                    accentColorArgb = 0xFFEF4444L,
                    durationMs = 4200L,
                    dedupeKey = "nb_export_status"
                )
            }
        }
    }

    fun exportPdfToLumaLibrary(
        options: NotebookExportOptions,
        onImportPdf: suspend (File, String) -> Long?
    ) {
        val nb = _uiState.value.notebook ?: return
        val pages = _uiState.value.pages
        val curPage = _uiState.value.currentPage ?: return
        val map = _uiState.value.contentByPageId

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    exportProgress = NotebookExportProgress(0.02f, "Exportando PDF para tu biblioteca…")
                )
            }
            try {
                val pdfFile = NotebookExporter.exportToCacheFile(
                    context = getApplication(),
                    notebook = nb,
                    pages = pages,
                    currentPage = curPage,
                    contentByPageId = map,
                    options = options,
                    onProgress = ::reportExportProgress
                )
                reportExportProgress(NotebookExportProgress(0.96f, "Importando a tu biblioteca de Luma…"))
                val docId = onImportPdf(pdfFile, nb.title)
                val ok = docId != null
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = if (ok) "Añadido a tu biblioteca de Luma" else "No se pudo importar a la biblioteca"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = if (ok) CriticalEventKind.EXPORT_DONE else CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = if (ok) "BIBLIOTECA DE LUMA" else "ERROR",
                    title = if (ok) "PDF añadido a tu biblioteca" else "No se pudo añadir",
                    subtitle = nb.title,
                    accentColorArgb = if (ok) 0xFF10B981L else 0xFFEF4444L,
                    durationMs = 4400L,
                    dedupeKey = "nb_export_status"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportProgress = null,
                        statusMessage = t.localizedMessage ?: "Error al importar PDF"
                    )
                }
                DynamicIslandEventManager.postOperationStatus(
                    kind = CriticalEventKind.CRITICAL_ERROR,
                    badgeLabel = "BIBLIOTECA DE LUMA",
                    title = "No se pudo añadir PDF",
                    subtitle = t.localizedMessage ?: "Intenta nuevamente",
                    accentColorArgb = 0xFFEF4444L,
                    durationMs = 4400L,
                    dedupeKey = "nb_export_status"
                )
            }
        }
    }

    // ---- Internal Command Execution, Debounced Atomic Auto-Save & Preloading ----

    private fun executeCommand(
        command: NotebookCommand,
        selectIdsAfter: Set<String>
    ) {
        val page = _uiState.value.currentPage ?: return
        val before = _uiState.value.currentContent
        val after = command.apply(before)
        if (after == before) return

        val undo = undoStacks.getOrPut(page.id) { ArrayDeque() }
        undo.addLast(command)
        redoStacks[page.id]?.clear()

        applyContentState(page.id, after, selectedIds = selectIdsAfter)
    }

    private fun applyContentState(pageId: Long, newContent: NotebookPageContent, selectedIds: Set<String>) {
        synchronized(dirtyPageIds) { dirtyPageIds.add(pageId) }
        _uiState.update { st ->
            val updated = HashMap(st.contentByPageId)
            updated[pageId] = newContent
            st.copy(
                contentByPageId = updated,
                selectedObjectIds = selectedIds,
                canUndo = undoStacks[pageId]?.isNotEmpty() == true,
                canRedo = redoStacks[pageId]?.isNotEmpty() == true
            )
        }
        scheduleDebouncedSave()
        scheduleThumbnailRefresh()
    }

    private fun scheduleDebouncedSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            persistDirtyPages()
        }
    }

    fun flushPendingSaveAsync() {
        commitContinuousTransform()
        saveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            persistDirtyPages()
        }
    }

    private suspend fun persistDirtyPages() {
        val snapshotIds = synchronized(dirtyPageIds) {
            val copy = dirtyPageIds.toList()
            dirtyPageIds.clear()
            copy
        }
        if (snapshotIds.isEmpty()) return
        val contentMap = _uiState.value.contentByPageId
        for (pid in snapshotIds) {
            val content = contentMap[pid] ?: continue
            val json = NotebookJsonCodec.encode(content)
            repository.savePageContent(
                notebookId = notebookId,
                pageId = pid,
                contentJson = json,
                schemaVersion = NotebookPageContent.CURRENT_SCHEMA_VERSION
            )
        }
    }

    private fun scheduleThumbnailRefresh() {
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1200L)
            val st = _uiState.value
            val nb = st.notebook ?: return@launch
            val page = st.currentPage ?: return@launch
            val content = st.currentContent
            val path = NotebookExporter.generatePageThumbnailWebp(getApplication(), nb, page, content)
            if (path != null) {
                repository.savePageThumbnail(
                    notebookId = notebookId,
                    pageId = page.id,
                    isFirstPage = page.pageIndex == 0,
                    thumbnailAbsPath = path
                )
            }
        }
    }

    private fun preloadAdjacentPages() {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            val st = _uiState.value
            val pages = st.pages
            if (pages.isEmpty()) return@launch
            val cur = st.currentPageIndex
            val window = listOf(cur, cur + 1, cur - 1).filter { it in pages.indices }
            val loaded = HashMap(st.loadedBitmaps)
            var changed = false

            for (idx in window) {
                val p = pages[idx]
                if (p.backgroundAssetPath.isNotBlank() && !loaded.containsKey(p.backgroundAssetPath)) {
                    NotebookAssetStore.loadBitmap(
                        context = getApplication(),
                        notebookId = notebookId,
                        relativeOrAbsolutePath = p.backgroundAssetPath,
                        maxDimensionPx = 1600
                    )?.let {
                        loaded[p.backgroundAssetPath] = it
                        changed = true
                    }
                }
                val content = st.contentByPageId[p.id] ?: NotebookJsonCodec.decode(p.contentJson)
                for (obj in content.objects) {
                    val relPath = when (obj) {
                        is ImageObject -> obj.relativeAssetPath
                        is StickerObject -> obj.customAssetPath.orEmpty()
                        else -> ""
                    }
                    if (relPath.isNotBlank() && !loaded.containsKey(relPath)) {
                        NotebookAssetStore.loadBitmap(
                            context = getApplication(),
                            notebookId = notebookId,
                            relativeOrAbsolutePath = relPath,
                            maxDimensionPx = 1280
                        )?.let {
                            loaded[relPath] = it
                            changed = true
                        }
                    }
                }
            }
            if (changed) {
                _uiState.update { it.copy(loadedBitmaps = loaded) }
            }
        }
    }

    private suspend fun loadAssetIntoState(relativePath: String, targetMaxDimPx: Int) {
        val bmp = NotebookAssetStore.loadBitmap(
            context = getApplication(),
            notebookId = notebookId,
            relativeOrAbsolutePath = relativePath,
            maxDimensionPx = targetMaxDimPx
        ) ?: return
        _uiState.update { st ->
            st.copy(loadedBitmaps = st.loadedBitmaps + (relativePath to bmp))
        }
    }

    override fun onCleared() {
        super.onCleared()
        commitContinuousTransform()
        val pendingIds = synchronized(dirtyPageIds) {
            val copy = dirtyPageIds.toList()
            dirtyPageIds.clear()
            copy
        }
        if (pendingIds.isNotEmpty()) {
            val map = _uiState.value.contentByPageId
            val repo = repository
            val nbId = notebookId
            CoroutineScope(Dispatchers.IO).launch {
                for (pid in pendingIds) {
                    val c = map[pid] ?: continue
                    repo.savePageContent(
                        notebookId = nbId,
                        pageId = pid,
                        contentJson = NotebookJsonCodec.encode(c),
                        schemaVersion = NotebookPageContent.CURRENT_SCHEMA_VERSION
                    )
                }
            }
        }
    }

    companion object {
        private const val AUTO_SAVE_DEBOUNCE_MS = 800L
    }

    class Factory(
        private val application: Application,
        private val notebookId: Long,
        private val repository: NotebookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotebookViewModel(application, notebookId, repository) as T
        }
    }
}

package com.example.notebook.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lazy-initialized repository for Study Notebooks.
 * Keeps all Room DAO calls and asset file operations cleanly off the main thread.
 */
class NotebookRepository(
    private val appContext: Context,
    private val dao: NotebookDao
) {
    val notebookSummaries: Flow<List<NotebookSummaryItem>> = dao.observeNotebookSummaries()

    fun observeNotebook(notebookId: Long): Flow<NotebookEntity?> = dao.observeNotebook(notebookId)

    fun observePages(notebookId: Long): Flow<List<NotebookPageEntity>> = dao.observePages(notebookId)

    suspend fun getNotebook(notebookId: Long): NotebookEntity? = withContext(Dispatchers.IO) {
        dao.getNotebook(notebookId)
    }

    suspend fun getPagesOnce(notebookId: Long): List<NotebookPageEntity> = withContext(Dispatchers.IO) {
        dao.getPagesOnce(notebookId)
    }

    suspend fun createNotebook(
        title: String,
        template: NotebookPageTemplate = NotebookPageTemplate.BLANK,
        pageWidthPx: Int = NotebookEntity.DEFAULT_PAGE_WIDTH_PX,
        pageHeightPx: Int = NotebookEntity.DEFAULT_PAGE_HEIGHT_PX,
        backgroundColor: Long = 0xFFFFFFFFL
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val notebookId = dao.insertNotebook(
            NotebookEntity(
                title = title.trim().ifEmpty { "Cuaderno sin título" },
                createdAt = now,
                updatedAt = now,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx,
                pageTemplate = template.name
            )
        )
        dao.insertPage(
            NotebookPageEntity(
                notebookId = notebookId,
                pageIndex = 0,
                backgroundType = NotebookBackgroundType.TEMPLATE.name,
                backgroundColor = backgroundColor
            )
        )
        notebookId
    }

    /**
     * Creates a new Study Notebook pre-populated with each page of a PDF as a locked high-res background.
     */
    suspend fun createNotebookFromPdf(
        title: String,
        pdfFile: File
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val notebookId = dao.insertNotebook(
            NotebookEntity(
                title = title.trim().ifEmpty { pdfFile.nameWithoutExtension.ifBlank { "Apuntes PDF" } },
                createdAt = now,
                updatedAt = now,
                pageWidthPx = NotebookEntity.DEFAULT_PAGE_WIDTH_PX,
                pageHeightPx = NotebookEntity.DEFAULT_PAGE_HEIGHT_PX,
                pageTemplate = NotebookPageTemplate.BLANK.name
            )
        )
        val rendered = NotebookAssetStore.renderPdfPagesToAssets(appContext, notebookId, pdfFile)
        if (rendered.isEmpty()) {
            dao.insertPage(
                NotebookPageEntity(
                    notebookId = notebookId,
                    pageIndex = 0,
                    backgroundType = NotebookBackgroundType.TEMPLATE.name,
                    backgroundColor = 0xFFFFFFFFL
                )
            )
        } else {
            val first = rendered.first()
            val coverAbs = NotebookAssetStore.resolveAssetFile(appContext, notebookId, first.relativeFileName).absolutePath
            // Align notebook canvas dimensions to the PDF's native aspect ratio
            dao.insertNotebook(
                NotebookEntity(
                    id = notebookId,
                    title = title.trim().ifEmpty { pdfFile.nameWithoutExtension.ifBlank { "Apuntes PDF" } },
                    createdAt = now,
                    updatedAt = now,
                    coverImagePath = coverAbs,
                    pageWidthPx = first.widthPx.coerceIn(720, 2400),
                    pageHeightPx = first.heightPx.coerceIn(720, 2800),
                    pageTemplate = NotebookPageTemplate.BLANK.name
                )
            )
            val pages = rendered.mapIndexed { idx, asset ->
                NotebookPageEntity(
                    notebookId = notebookId,
                    pageIndex = idx,
                    backgroundType = NotebookBackgroundType.PDF_PAGE.name,
                    backgroundColor = 0xFFFFFFFFL,
                    backgroundAssetPath = asset.relativeFileName,
                    thumbnailPath = if (idx == 0) coverAbs else ""
                )
            }
            dao.insertPages(pages)
        }
        notebookId
    }

    /**
     * Creates a new Study Notebook using an imported photo/diagram/slide image as the page 1 background.
     */
    suspend fun createNotebookFromImageUri(
        title: String,
        imageUri: Uri
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val notebookId = dao.insertNotebook(
            NotebookEntity(
                title = title.trim().ifEmpty { "Apunte con imagen" },
                createdAt = now,
                updatedAt = now,
                pageWidthPx = NotebookEntity.DEFAULT_PAGE_WIDTH_PX,
                pageHeightPx = NotebookEntity.DEFAULT_PAGE_HEIGHT_PX,
                pageTemplate = NotebookPageTemplate.BLANK.name
            )
        )
        val imported = NotebookAssetStore.importImageUri(appContext, notebookId, imageUri)
        if (imported != null) {
            val absPath = NotebookAssetStore.resolveAssetFile(appContext, notebookId, imported.relativeFileName).absolutePath
            val pageW = imported.widthPx.coerceIn(900, 2048)
            val pageH = ((pageW.toFloat() * imported.heightPx) / imported.widthPx.coerceAtLeast(1))
                .toInt().coerceIn(720, 2600)
            dao.insertNotebook(
                NotebookEntity(
                    id = notebookId,
                    title = title.trim().ifEmpty { "Apunte con imagen" },
                    createdAt = now,
                    updatedAt = now,
                    coverImagePath = absPath,
                    pageWidthPx = pageW,
                    pageHeightPx = pageH,
                    pageTemplate = NotebookPageTemplate.BLANK.name
                )
            )
            dao.insertPage(
                NotebookPageEntity(
                    notebookId = notebookId,
                    pageIndex = 0,
                    backgroundType = NotebookBackgroundType.IMAGE.name,
                    backgroundColor = 0xFFFFFFFFL,
                    backgroundAssetPath = imported.relativeFileName,
                    thumbnailPath = absPath
                )
            )
        } else {
            dao.insertPage(
                NotebookPageEntity(
                    notebookId = notebookId,
                    pageIndex = 0,
                    backgroundType = NotebookBackgroundType.TEMPLATE.name,
                    backgroundColor = 0xFFFFFFFFL
                )
            )
        }
        notebookId
    }

    /**
     * Creates a multi-page Study Notebook from multiple shared images (ACTION_SEND_MULTIPLE).
     */
    suspend fun createNotebookFromImageUris(
        title: String,
        imageUris: List<Uri>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        if (imageUris.size <= 1) {
            val single = imageUris.firstOrNull()
            return@withContext if (single != null) createNotebookFromImageUri(title, single)
            else createNotebook(title)
        }
        val now = System.currentTimeMillis()
        val safeTitle = title.trim().ifEmpty { "Apunte con imágenes" }
        val notebookId = dao.insertNotebook(
            NotebookEntity(
                title = safeTitle,
                createdAt = now,
                updatedAt = now,
                pageWidthPx = NotebookEntity.DEFAULT_PAGE_WIDTH_PX,
                pageHeightPx = NotebookEntity.DEFAULT_PAGE_HEIGHT_PX,
                pageTemplate = NotebookPageTemplate.BLANK.name
            )
        )
        val pages = ArrayList<NotebookPageEntity>()
        var coverAbs = ""
        var pageW = NotebookEntity.DEFAULT_PAGE_WIDTH_PX
        var pageH = NotebookEntity.DEFAULT_PAGE_HEIGHT_PX

        imageUris.forEachIndexed { idx, uri ->
            onProgress(idx + 1, imageUris.size)
            val imported = NotebookAssetStore.importImageUri(appContext, notebookId, uri)
            if (imported != null) {
                val abs = NotebookAssetStore.resolveAssetFile(appContext, notebookId, imported.relativeFileName).absolutePath
                if (pages.isEmpty()) {
                    coverAbs = abs
                    pageW = imported.widthPx.coerceIn(900, 2048)
                    pageH = ((pageW.toFloat() * imported.heightPx) / imported.widthPx.coerceAtLeast(1))
                        .toInt().coerceIn(720, 2600)
                }
                pages.add(
                    NotebookPageEntity(
                        notebookId = notebookId,
                        pageIndex = pages.size,
                        backgroundType = NotebookBackgroundType.IMAGE.name,
                        backgroundColor = 0xFFFFFFFFL,
                        backgroundAssetPath = imported.relativeFileName,
                        thumbnailPath = abs
                    )
                )
            }
        }
        if (pages.isNotEmpty()) {
            dao.insertNotebook(
                NotebookEntity(
                    id = notebookId,
                    title = safeTitle,
                    createdAt = now,
                    updatedAt = now,
                    coverImagePath = coverAbs,
                    pageWidthPx = pageW,
                    pageHeightPx = pageH,
                    pageTemplate = NotebookPageTemplate.BLANK.name
                )
            )
            dao.insertPages(pages)
        } else {
            dao.insertPage(
                NotebookPageEntity(
                    notebookId = notebookId,
                    pageIndex = 0,
                    backgroundType = NotebookBackgroundType.TEMPLATE.name,
                    backgroundColor = 0xFFFFFFFFL
                )
            )
        }
        notebookId
    }

    suspend fun addPage(
        notebookId: Long,
        backgroundType: NotebookBackgroundType = NotebookBackgroundType.TEMPLATE,
        backgroundColor: Long = 0xFFFFFFFFL,
        backgroundAssetPath: String = ""
    ): NotebookPageEntity = withContext(Dispatchers.IO) {
        val existing = dao.getPagesOnce(notebookId)
        val nextIndex = existing.size
        val page = NotebookPageEntity(
            notebookId = notebookId,
            pageIndex = nextIndex,
            backgroundType = backgroundType.name,
            backgroundColor = backgroundColor,
            backgroundAssetPath = backgroundAssetPath
        )
        val id = dao.insertPage(page)
        dao.touchNotebook(notebookId)
        page.copy(id = id)
    }

    suspend fun duplicatePage(notebookId: Long, sourcePage: NotebookPageEntity): NotebookPageEntity =
        withContext(Dispatchers.IO) {
            val existing = dao.getPagesOnce(notebookId)
            val insertAt = (sourcePage.pageIndex + 1).coerceIn(0, existing.size)
            for (i in existing.lastIndex downTo insertAt) {
                dao.updatePageIndex(existing[i].id, i + 1)
            }
            val copy = sourcePage.copy(id = 0, pageIndex = insertAt, thumbnailPath = "")
            val newId = dao.insertPage(copy)
            dao.touchNotebook(notebookId)
            copy.copy(id = newId)
        }

    suspend fun deletePage(notebookId: Long, pageId: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getPagesOnce(notebookId)
        if (existing.size <= 1) return@withContext
        dao.deletePage(pageId)
        val remaining = dao.getPagesOnce(notebookId)
        remaining.forEachIndexed { idx, p ->
            if (p.pageIndex != idx) {
                dao.updatePageIndex(p.id, idx)
            }
        }
        dao.touchNotebook(notebookId)
    }

    suspend fun movePage(notebookId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val pages = dao.getPagesOnce(notebookId).toMutableList()
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) return@withContext
        val moved = pages.removeAt(fromIndex)
        pages.add(toIndex, moved)
        pages.forEachIndexed { idx, p ->
            if (p.pageIndex != idx) {
                dao.updatePageIndex(p.id, idx)
            }
        }
        dao.touchNotebook(notebookId)
    }

    suspend fun savePageContent(
        notebookId: Long,
        pageId: Long,
        contentJson: String,
        schemaVersion: Int = NotebookPageEntity.CURRENT_CONTENT_SCHEMA_VERSION
    ) = withContext(Dispatchers.IO) {
        dao.savePageContentAtomic(notebookId, pageId, contentJson, schemaVersion)
    }

    suspend fun savePageThumbnail(notebookId: Long, pageId: Long, isFirstPage: Boolean, thumbnailAbsPath: String) =
        withContext(Dispatchers.IO) {
            dao.updatePageThumbnail(pageId, thumbnailAbsPath)
            dao.touchNotebook(notebookId, coverImagePath = if (isFirstPage) thumbnailAbsPath else "")
        }

    suspend fun updatePageBackground(
        notebookId: Long,
        pageId: Long,
        backgroundType: NotebookBackgroundType,
        backgroundColor: Long,
        backgroundAssetPath: String
    ) = withContext(Dispatchers.IO) {
        dao.updatePageBackground(pageId, backgroundType.name, backgroundColor, backgroundAssetPath)
        dao.touchNotebook(notebookId)
    }

    suspend fun updateNotebookTemplate(notebookId: Long, template: NotebookPageTemplate) = withContext(Dispatchers.IO) {
        dao.updateNotebookTemplate(notebookId, template.name)
    }

    suspend fun updateNotebookPageSize(notebookId: Long, widthPx: Int, heightPx: Int) = withContext(Dispatchers.IO) {
        dao.updateNotebookPageSize(
            notebookId = notebookId,
            widthPx = widthPx.coerceIn(600, 2400),
            heightPx = heightPx.coerceIn(600, 2600)
        )
    }

    suspend fun renameNotebook(notebookId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return@withContext
        dao.renameNotebook(notebookId, clean)
    }

    suspend fun deleteNotebook(notebookId: Long) = withContext(Dispatchers.IO) {
        dao.deleteNotebook(notebookId)
        NotebookAssetStore.deleteNotebookFiles(appContext, notebookId)
    }

    suspend fun importAssetToNotebook(notebookId: Long, uri: Uri): ImportedAssetResult? =
        NotebookAssetStore.importImageUri(appContext, notebookId, uri)
}

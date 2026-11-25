package com.example.data.local

import com.example.data.model.DrawingStroke
import com.example.data.model.OcrPageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FolioRepository(private val dao: FolioDao) {

    val documents: Flow<List<LibraryDocumentItem>> = dao.observeLibraryDocuments()
    val playlists: Flow<List<PlaylistEntity>> = dao.observeAllPlaylists()
    val playlistRefs: Flow<List<PlaylistDocumentCrossRef>> = dao.observeAllPlaylistRefs()
    val dictionarySources: Flow<List<DictionarySourceEntity>> = dao.observeDictionarySources()

    suspend fun ensureDefaultDictionarySources() {
        if (dao.getDictionarySourceCount() == 0) {
            dao.insertDictionarySource(
                DictionarySourceEntity(
                    name = "Wikipedia",
                    urlTemplate = "https://es.wikipedia.org/wiki/{palabra}",
                    isWikipediaApi = true,
                    isEnabled = true,
                    sortOrder = 0
                )
            )
            dao.insertDictionarySource(
                DictionarySourceEntity(
                    name = "RAE",
                    urlTemplate = "https://dle.rae.es/{palabra}",
                    isWikipediaApi = false,
                    isEnabled = true,
                    sortOrder = 1
                )
            )
            dao.insertDictionarySource(
                DictionarySourceEntity(
                    name = "WordReference",
                    urlTemplate = "https://www.wordreference.com/definicion/{palabra}",
                    isWikipediaApi = false,
                    isEnabled = true,
                    sortOrder = 2
                )
            )
        }
    }

    suspend fun getDocumentsForCoverCheck(): List<DocumentCoverCheckItem> =
        dao.getDocumentsForCoverCheck()

    suspend fun getDocumentSynopsis(id: Long): String =
        dao.getDocumentSynopsis(id).orEmpty()

    suspend fun getDocumentCount(): Int = dao.getDocumentCount()

    suspend fun getDocumentById(id: Long): DocumentEntity? = dao.getDocumentById(id)

    suspend fun insertDocument(doc: DocumentEntity): Long = dao.insertDocument(doc)

    suspend fun updateDocumentCover(id: Long, coverPath: String) =
        dao.updateDocumentCover(id, coverPath)

    suspend fun updateDocumentMetadata(
        id: Long,
        title: String,
        author: String,
        genre: String,
        publisherYear: String,
        synopsis: String,
        badgeTag: String
    ) = dao.updateDocumentMetadata(id, title, author, genre, publisherYear, synopsis, badgeTag)

    suspend fun updateProgress(id: Long, unit: Int, progress: Float) =
        dao.updateReadingProgress(id, unit, progress)

    suspend fun deleteDocument(id: Long) = dao.deleteDocumentById(id)

    // Playlists (Spotify-style book sagas / collections)
    suspend fun getPlaylistCount(): Int = dao.getPlaylistCount()

    suspend fun createPlaylist(name: String, description: String, accentColorHex: Long): Long =
        dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim(),
                accentColorHex = accentColorHex
            )
        )

    suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    suspend fun addBookToPlaylist(playlistId: Long, documentId: Long) =
        dao.addDocumentToPlaylist(PlaylistDocumentCrossRef(playlistId, documentId))

    suspend fun removeBookFromPlaylist(playlistId: Long, documentId: Long) =
        dao.removeDocumentFromPlaylist(playlistId, documentId)

    // Highlights & Notes
    fun observeHighlights(documentId: Long): Flow<List<HighlightNoteEntity>> =
        dao.observeHighlights(documentId)

    suspend fun getHighlightsOnce(documentId: Long): List<HighlightNoteEntity> =
        dao.getHighlightsOnce(documentId)

    suspend fun addHighlight(highlight: HighlightNoteEntity): Long = dao.insertHighlight(highlight)

    suspend fun updateHighlightNote(id: Long, noteText: String, colorArgb: Long) =
        dao.updateHighlightNote(id, noteText, colorArgb)

    suspend fun deleteHighlight(id: Long) = dao.deleteHighlight(id)

    // Bookmarks
    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>> =
        dao.observeBookmarks(documentId)

    suspend fun addBookmark(bookmark: BookmarkEntity): Long = dao.insertBookmark(bookmark)

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun deleteBookmarkByLocation(documentId: Long, locationKey: String) =
        dao.deleteBookmarkByLocation(documentId, locationKey)

    // Drawings
    fun observeDrawingsMap(documentId: Long): Flow<Map<String, List<DrawingStroke>>> =
        dao.observeDrawingLayers(documentId).map { list ->
            list.associate { it.locationKey to JsonConverters.jsonToStrokes(it.strokesJson) }
        }

    suspend fun getDrawingsMapOnce(documentId: Long): Map<String, List<DrawingStroke>> =
        dao.getDrawingLayersOnce(documentId)
            .associate { it.locationKey to JsonConverters.jsonToStrokes(it.strokesJson) }

    suspend fun saveStrokes(documentId: Long, locationKey: String, strokes: List<DrawingStroke>) {
        if (strokes.isEmpty()) {
            dao.deleteDrawingLayer(documentId, locationKey)
        } else {
            dao.upsertDrawingLayer(
                DrawingLayerEntity(
                    documentId = documentId,
                    locationKey = locationKey,
                    strokesJson = JsonConverters.strokesToJson(strokes)
                )
            )
        }
    }

    // OCR Cache
    fun observeOcrPagesMap(documentId: Long): Flow<Map<Int, OcrPageResult>> =
        dao.observeOcrPages(documentId).map { entities ->
            entities.mapNotNull { entity ->
                JsonConverters.jsonToOcrPage(entity.ocrJson)?.let { entity.pageIndex to it }
            }.toMap()
        }

    suspend fun getOcrPagesMapOnce(documentId: Long): Map<Int, OcrPageResult> =
        dao.getOcrPagesOnce(documentId).mapNotNull { entity ->
            JsonConverters.jsonToOcrPage(entity.ocrJson)?.let { entity.pageIndex to it }
        }.toMap()

    suspend fun saveOcrPage(documentId: Long, result: OcrPageResult) {
        dao.upsertOcrPage(
            OcrPageCacheEntity(
                documentId = documentId,
                pageIndex = result.pageIndex,
                ocrJson = JsonConverters.ocrPageToJson(result)
            )
        )
    }

    // Dictionary Sources
    suspend fun addDictionarySource(name: String, urlTemplate: String) {
        val count = dao.getDictionarySourceCount()
        dao.insertDictionarySource(
            DictionarySourceEntity(
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                isWikipediaApi = false,
                isEnabled = true,
                sortOrder = count
            )
        )
    }

    suspend fun toggleDictionarySource(source: DictionarySourceEntity) {
        dao.updateDictionarySource(source.copy(isEnabled = !source.isEnabled))
    }

    suspend fun deleteDictionarySource(id: Long) = dao.deleteDictionarySource(id)
}

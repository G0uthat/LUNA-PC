package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolioDao {
    // Documents — lightweight projection for Library listing (omits heavy synopsis & unused columns)
    @Query(
        "SELECT id, title, author, format, filePath, coverImagePath, progressPercent, " +
            "totalWords, coverColorHex, badgeTag, genre, publisherYear, lastOpenedAt " +
            "FROM documents ORDER BY lastOpenedAt DESC"
    )
    fun observeLibraryDocuments(): Flow<List<LibraryDocumentItem>>

    // Minimal projection for background cover integrity check
    @Query(
        "SELECT id, title, author, format, filePath, coverImagePath, coverColorHex " +
            "FROM documents ORDER BY lastOpenedAt DESC"
    )
    suspend fun getDocumentsForCoverCheck(): List<DocumentCoverCheckItem>

    @Query("SELECT synopsis FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentSynopsis(id: Long): String?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int

    @Query(
        "SELECT id, title, author, format, filePath, coverImagePath, totalUnits, currentUnit, " +
            "progressPercent, totalWords, coverColorHex, badgeTag, genre, publisherYear, " +
            "synopsis, lastOpenedAt, addedAt FROM documents WHERE id = :id LIMIT 1"
    )
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("UPDATE documents SET coverImagePath = :coverPath WHERE id = :id")
    suspend fun updateDocumentCover(id: Long, coverPath: String)

    @Query(
        "UPDATE documents SET title = :title, author = :author, genre = :genre, " +
            "publisherYear = :publisherYear, synopsis = :synopsis, badgeTag = :badgeTag WHERE id = :id"
    )
    suspend fun updateDocumentMetadata(
        id: Long,
        title: String,
        author: String,
        genre: String,
        publisherYear: String,
        synopsis: String,
        badgeTag: String
    )

    @Query("UPDATE documents SET currentUnit = :unit, progressPercent = :progress, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, unit: Int, progress: Float, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    // Playlists (Spotify-style book collections / sagas)
    @Query("SELECT id, name, description, accentColorHex, createdAt FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId, documentId, addedAt FROM playlist_documents")
    fun observeAllPlaylistRefs(): Flow<List<PlaylistDocumentCrossRef>>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDocumentToPlaylist(ref: PlaylistDocumentCrossRef)

    @Query("DELETE FROM playlist_documents WHERE playlistId = :playlistId AND documentId = :documentId")
    suspend fun removeDocumentFromPlaylist(playlistId: Long, documentId: Long)

    // Highlights & Notes
    @Query(
        "SELECT id, documentId, locationKey, chapterIndex, blockIndex, startChar, endChar, " +
            "quotedText, colorArgb, noteText, createdAt FROM highlights_notes " +
            "WHERE documentId = :documentId ORDER BY chapterIndex ASC, blockIndex ASC, startChar ASC"
    )
    fun observeHighlights(documentId: Long): Flow<List<HighlightNoteEntity>>

    @Query(
        "SELECT id, documentId, locationKey, chapterIndex, blockIndex, startChar, endChar, " +
            "quotedText, colorArgb, noteText, createdAt FROM highlights_notes " +
            "WHERE documentId = :documentId"
    )
    suspend fun getHighlightsOnce(documentId: Long): List<HighlightNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightNoteEntity): Long

    @Query("UPDATE highlights_notes SET noteText = :noteText, colorArgb = :colorArgb WHERE id = :id")
    suspend fun updateHighlightNote(id: Long, noteText: String, colorArgb: Long)

    @Query("DELETE FROM highlights_notes WHERE id = :id")
    suspend fun deleteHighlight(id: Long)

    // Bookmarks
    @Query(
        "SELECT id, documentId, locationKey, unitIndex, label, createdAt FROM bookmarks " +
            "WHERE documentId = :documentId ORDER BY unitIndex ASC, createdAt DESC"
    )
    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND locationKey = :locationKey")
    suspend fun deleteBookmarkByLocation(documentId: Long, locationKey: String)

    // Drawing Layers
    @Query("SELECT documentId, locationKey, strokesJson, updatedAt FROM drawing_layers WHERE documentId = :documentId")
    fun observeDrawingLayers(documentId: Long): Flow<List<DrawingLayerEntity>>

    @Query("SELECT documentId, locationKey, strokesJson, updatedAt FROM drawing_layers WHERE documentId = :documentId")
    suspend fun getDrawingLayersOnce(documentId: Long): List<DrawingLayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrawingLayer(layer: DrawingLayerEntity)

    @Query("DELETE FROM drawing_layers WHERE documentId = :documentId AND locationKey = :locationKey")
    suspend fun deleteDrawingLayer(documentId: Long, locationKey: String)

    // OCR Cache
    @Query("SELECT documentId, pageIndex, ocrJson, updatedAt FROM ocr_page_cache WHERE documentId = :documentId")
    fun observeOcrPages(documentId: Long): Flow<List<OcrPageCacheEntity>>

    @Query("SELECT documentId, pageIndex, ocrJson, updatedAt FROM ocr_page_cache WHERE documentId = :documentId")
    suspend fun getOcrPagesOnce(documentId: Long): List<OcrPageCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrPage(entity: OcrPageCacheEntity)

    @Query("SELECT COALESCE(SUM(LENGTH(ocrJson)), 0) FROM ocr_page_cache")
    suspend fun getOcrCacheBytes(): Long

    @Query("SELECT COUNT(*) FROM ocr_page_cache")
    suspend fun getOcrCacheCount(): Int

    @Query("DELETE FROM ocr_page_cache")
    suspend fun clearAllOcrCache()

    // Dictionary Sources
    @Query("SELECT id, name, urlTemplate, isWikipediaApi, isEnabled, sortOrder FROM dictionary_sources ORDER BY sortOrder ASC, id ASC")
    fun observeDictionarySources(): Flow<List<DictionarySourceEntity>>

    @Query("SELECT COUNT(*) FROM dictionary_sources")
    suspend fun getDictionarySourceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDictionarySource(source: DictionarySourceEntity): Long

    @Update
    suspend fun updateDictionarySource(source: DictionarySourceEntity)

    @Query("DELETE FROM dictionary_sources WHERE id = :id")
    suspend fun deleteDictionarySource(id: Long)
}

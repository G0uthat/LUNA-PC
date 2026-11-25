package com.example.notebook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {

    @Query(
        """
        SELECT
            n.id,
            n.title,
            n.createdAt,
            n.updatedAt,
            n.coverImagePath,
            n.pageWidthPx,
            n.pageHeightPx,
            n.pageTemplate,
            (SELECT COUNT(*) FROM notebook_pages p WHERE p.notebookId = n.id) AS pageCount,
            COALESCE(
                (SELECT p.thumbnailPath FROM notebook_pages p WHERE p.notebookId = n.id ORDER BY p.pageIndex ASC LIMIT 1),
                ''
            ) AS firstPageThumbnailPath
        FROM notebooks n
        ORDER BY n.updatedAt DESC
        """
    )
    fun observeNotebookSummaries(): Flow<List<NotebookSummaryItem>>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId LIMIT 1")
    fun observeNotebook(notebookId: Long): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId LIMIT 1")
    suspend fun getNotebook(notebookId: Long): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Query("UPDATE notebooks SET title = :title, updatedAt = :updatedAt WHERE id = :notebookId")
    suspend fun renameNotebook(notebookId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notebooks SET updatedAt = :updatedAt, coverImagePath = COALESCE(NULLIF(:coverImagePath, ''), coverImagePath) WHERE id = :notebookId")
    suspend fun touchNotebook(
        notebookId: Long,
        coverImagePath: String = "",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE notebooks SET pageTemplate = :template, updatedAt = :updatedAt WHERE id = :notebookId")
    suspend fun updateNotebookTemplate(
        notebookId: Long,
        template: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE notebooks SET pageWidthPx = :widthPx, pageHeightPx = :heightPx, updatedAt = :updatedAt WHERE id = :notebookId")
    suspend fun updateNotebookPageSize(
        notebookId: Long,
        widthPx: Int,
        heightPx: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteNotebook(notebookId: Long)

    // Pages
    @Query("SELECT * FROM notebook_pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    fun observePages(notebookId: Long): Flow<List<NotebookPageEntity>>

    @Query("SELECT * FROM notebook_pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    suspend fun getPagesOnce(notebookId: Long): List<NotebookPageEntity>

    @Query("SELECT * FROM notebook_pages WHERE id = :pageId LIMIT 1")
    suspend fun getPage(pageId: Long): NotebookPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: NotebookPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<NotebookPageEntity>): List<Long>

    @Update
    suspend fun updatePage(page: NotebookPageEntity)

    @Query("UPDATE notebook_pages SET contentJson = :contentJson, schemaVersion = :schemaVersion WHERE id = :pageId")
    suspend fun updatePageContent(
        pageId: Long,
        contentJson: String,
        schemaVersion: Int = NotebookPageEntity.CURRENT_CONTENT_SCHEMA_VERSION
    )

    @androidx.room.Transaction
    suspend fun savePageContentAtomic(
        notebookId: Long,
        pageId: Long,
        contentJson: String,
        schemaVersion: Int = NotebookPageEntity.CURRENT_CONTENT_SCHEMA_VERSION
    ) {
        updatePageContent(pageId, contentJson, schemaVersion)
        touchNotebook(notebookId)
    }

    @Query("UPDATE notebook_pages SET thumbnailPath = :thumbnailPath WHERE id = :pageId")
    suspend fun updatePageThumbnail(pageId: Long, thumbnailPath: String)

    @Query(
        """
        UPDATE notebook_pages
        SET backgroundType = :backgroundType,
            backgroundColor = :backgroundColor,
            backgroundAssetPath = :backgroundAssetPath
        WHERE id = :pageId
        """
    )
    suspend fun updatePageBackground(
        pageId: Long,
        backgroundType: String,
        backgroundColor: Long,
        backgroundAssetPath: String
    )

    @Query("UPDATE notebook_pages SET pageIndex = :newIndex WHERE id = :pageId")
    suspend fun updatePageIndex(pageId: Long, newIndex: Int)

    @Query("DELETE FROM notebook_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)
}

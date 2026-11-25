package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["lastOpenedAt"]),
        Index(value = ["format"]),
        Index(value = ["addedAt"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val format: String, // EPUB, PDF, DOCX, PPTX, TXT, FB2
    val filePath: String,
    val coverImagePath: String = "",
    val totalUnits: Int, // chapters, slides, or PDF pages
    val currentUnit: Int = 0,
    val progressPercent: Float = 0f,
    val totalWords: Int = 0,
    val coverColorHex: Long = 0xFF2B64F5,
    val badgeTag: String = "",
    val genre: String = "",
    val publisherYear: String = "",
    val synopsis: String = "",
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Lightweight Room projection for LibraryScreen cards, Continue Reading hero, search & series.
 * Intentionally omits heavy text columns like `synopsis` (loaded on-demand only when opening
 * book options / metadata editor) and avoids `SELECT *`.
 */
@Immutable
data class LibraryDocumentItem(
    val id: Long,
    val title: String,
    val author: String,
    val format: String,
    val filePath: String,
    val coverImagePath: String,
    val progressPercent: Float,
    val totalWords: Int,
    val coverColorHex: Long,
    val badgeTag: String,
    val genre: String,
    val publisherYear: String,
    val lastOpenedAt: Long
)

/**
 * Minimal Room projection used solely during background cover verification.
 */
data class DocumentCoverCheckItem(
    val id: Long,
    val title: String,
    val author: String,
    val format: String,
    val filePath: String,
    val coverImagePath: String,
    val coverColorHex: Long
)

@Immutable
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["createdAt"])]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val accentColorHex: Long = 0xFF2B64F5,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_documents",
    primaryKeys = ["playlistId", "documentId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("documentId")]
)
data class PlaylistDocumentCrossRef(
    val playlistId: Long,
    val documentId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "highlights_notes",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId"),
        Index("locationKey"),
        Index(value = ["documentId", "chapterIndex", "blockIndex", "startChar"])
    ]
)
data class HighlightNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val locationKey: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val quotedText: String,
    val colorArgb: Long,
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId"),
        Index(value = ["documentId", "unitIndex", "createdAt"]),
        Index(value = ["documentId", "locationKey"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val locationKey: String,
    val unitIndex: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drawing_layers",
    primaryKeys = ["documentId", "locationKey"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DrawingLayerEntity(
    val documentId: Long,
    val locationKey: String,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ocr_page_cache",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class OcrPageCacheEntity(
    val documentId: Long,
    val pageIndex: Int,
    val ocrJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "dictionary_sources",
    indices = [Index(value = ["sortOrder", "id"])]
)
data class DictionarySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val urlTemplate: String,
    val isWikipediaApi: Boolean = false,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0
)

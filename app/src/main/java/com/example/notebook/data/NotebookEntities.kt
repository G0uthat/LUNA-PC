package com.example.notebook.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Isolated Room entity for Study Notebooks.
 * Kept strictly independent from ReaderScreen's DrawingStroke / DrawingLayerEntity.
 */
@Immutable
@Entity(
    tableName = "notebooks",
    indices = [Index(value = ["updatedAt"])]
)
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val coverImagePath: String = "",
    val pageWidthPx: Int = DEFAULT_PAGE_WIDTH_PX,
    val pageHeightPx: Int = DEFAULT_PAGE_HEIGHT_PX,
    val pageTemplate: String = NotebookPageTemplate.BLANK.name
) {
    companion object {
        // High-res A4 portrait canvas coordinates (1:1.414) for crisp handwriting & exports
        const val DEFAULT_PAGE_WIDTH_PX = 1480
        const val DEFAULT_PAGE_HEIGHT_PX = 2092
        const val LANDSCAPE_PAGE_WIDTH_PX = 1920
        const val LANDSCAPE_PAGE_HEIGHT_PX = 1080
    }
}

/**
 * Isolated Room entity for individual Notebook pages.
 * Binary images/PNGs are NEVER stored in SQLite; they live in `filesDir/notebooks/{notebookId}/assets/`
 * and `contentJson` stores strictly relative asset file names.
 */
@Immutable
@Entity(
    tableName = "notebook_pages",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["notebookId"]),
        Index(value = ["notebookId", "pageIndex"])
    ]
)
data class NotebookPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val pageIndex: Int,
    val backgroundType: String = NotebookBackgroundType.TEMPLATE.name,
    val backgroundColor: Long = 0xFFFFFFFFL,
    val backgroundAssetPath: String = "",
    val contentJson: String = EMPTY_CONTENT_JSON,
    val schemaVersion: Int = CURRENT_CONTENT_SCHEMA_VERSION,
    val thumbnailPath: String = ""
) {
    companion object {
        const val CURRENT_CONTENT_SCHEMA_VERSION = 2
        const val EMPTY_CONTENT_JSON = """{"schemaVersion":2,"objects":[]}"""
    }
}

/**
 * Lightweight projection for Library's "Cuadernos" grid (avoids reading heavy page contentJson).
 */
@Immutable
data class NotebookSummaryItem(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverImagePath: String,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val pageTemplate: String,
    val pageCount: Int,
    val firstPageThumbnailPath: String
)

enum class NotebookPageTemplate(val label: String, val subtitle: String) {
    BLANK("En blanco", "Hoja limpia para diagramas y apuntes libres"),
    GRID("Cuadrícula", "Ideal para esquemas, tablas y estructuras"),
    DOTS("Puntos", "Guía discreta estilo bullet journal"),
    RULED("Rayado", "Líneas suaves para apuntes y resúmenes"),
    CORNELL("Cornell", "Columna de palabras clave + notas + resumen");

    companion object {
        fun fromName(raw: String?): NotebookPageTemplate =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BLANK
    }
}

enum class NotebookBackgroundType {
    TEMPLATE,
    IMAGE,
    PDF_PAGE;

    companion object {
        fun fromName(raw: String?): NotebookBackgroundType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TEMPLATE
    }
}

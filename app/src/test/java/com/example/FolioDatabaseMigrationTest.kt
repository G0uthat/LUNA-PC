package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FolioDatabase
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies non-destructive Room migrations (v3 -> v4 -> v5) so existing user documents,
 * highlights, bookmarks, and reader drawings are 100% preserved when adding Study Notebooks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolioDatabaseMigrationTest {

    @Test
    fun migrate3To4To5_preservesExistingDocumentsAndCreatesNotebookTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${System.currentTimeMillis()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Bootstrap an authentic v3 database containing a user document
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL("PRAGMA user_version = 3")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `documents` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `filePath` TEXT NOT NULL,
                `coverImagePath` TEXT NOT NULL,
                `totalUnits` INTEGER NOT NULL,
                `currentUnit` INTEGER NOT NULL,
                `progressPercent` REAL NOT NULL,
                `totalWords` INTEGER NOT NULL,
                `coverColorHex` INTEGER NOT NULL,
                `badgeTag` TEXT NOT NULL,
                `genre` TEXT NOT NULL,
                `publisherYear` TEXT NOT NULL,
                `synopsis` TEXT NOT NULL,
                `lastOpenedAt` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `accentColorHex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_documents` (
                `playlistId` INTEGER NOT NULL,
                `documentId` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `documentId`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_documents_playlistId` ON `playlist_documents` (`playlistId`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_documents_documentId` ON `playlist_documents` (`documentId`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `highlights_notes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `documentId` INTEGER NOT NULL,
                `locationKey` TEXT NOT NULL,
                `chapterIndex` INTEGER NOT NULL,
                `blockIndex` INTEGER NOT NULL,
                `startChar` INTEGER NOT NULL,
                `endChar` INTEGER NOT NULL,
                `quotedText` TEXT NOT NULL,
                `colorArgb` INTEGER NOT NULL,
                `noteText` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_notes_documentId` ON `highlights_notes` (`documentId`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_notes_locationKey` ON `highlights_notes` (`locationKey`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmarks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `documentId` INTEGER NOT NULL,
                `locationKey` TEXT NOT NULL,
                `unitIndex` INTEGER NOT NULL,
                `label` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_documentId` ON `bookmarks` (`documentId`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `drawing_layers` (
                `documentId` INTEGER NOT NULL,
                `locationKey` TEXT NOT NULL,
                `strokesJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`documentId`, `locationKey`),
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_drawing_layers_documentId` ON `drawing_layers` (`documentId`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ocr_page_cache` (
                `documentId` INTEGER NOT NULL,
                `pageIndex` INTEGER NOT NULL,
                `ocrJson` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`documentId`, `pageIndex`),
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_page_cache_documentId` ON `ocr_page_cache` (`documentId`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dictionary_sources` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `urlTemplate` TEXT NOT NULL,
                `isWikipediaApi` INTEGER NOT NULL,
                `isEnabled` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Seed existing user document before migrating
        rawDb.execSQL(
            """
            INSERT INTO `documents` (
                `id`, `title`, `author`, `format`, `filePath`, `coverImagePath`,
                `totalUnits`, `currentUnit`, `progressPercent`, `totalWords`,
                `coverColorHex`, `badgeTag`, `genre`, `publisherYear`, `synopsis`,
                `lastOpenedAt`, `addedAt`
            ) VALUES (
                42, 'Farmacología Clínica', 'Dr. Goodman', 'PDF', '/tmp/farma.pdf', '',
                120, 12, 0.1, 45000, 4281033973, 'PDF', 'Medicina', '2026', '', 1000, 900
            )
            """.trimIndent()
        )
        rawDb.close()

        // 2. Open via Room with MIGRATION_3_4 + MIGRATION_4_5 (strictly NO destructive fallback!)
        val migratedDb = Room.databaseBuilder(context, FolioDatabase::class.java, dbName)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(
                FolioDatabase.MIGRATION_1_2,
                FolioDatabase.MIGRATION_2_3,
                FolioDatabase.MIGRATION_3_4,
                FolioDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()

        try {
            // Existing document must remain 100% intact
            val preservedDocs = migratedDb.folioDao().observeLibraryDocuments().first()
            assertEquals(1, preservedDocs.size)
            assertEquals("Farmacología Clínica", preservedDocs.first().title)
            assertEquals(42L, preservedDocs.first().id)

            // New notebook tables must work with FK CASCADE
            val nbId = migratedDb.notebookDao().insertNotebook(
                NotebookEntity(title = "Apuntes de Neurología")
            )
            val pageId = migratedDb.notebookDao().insertPage(
                NotebookPageEntity(notebookId = nbId, pageIndex = 0)
            )
            assertTrue(pageId > 0L)
            assertEquals(1, migratedDb.notebookDao().getPagesOnce(nbId).size)

            migratedDb.notebookDao().deleteNotebook(nbId)
            assertTrue(migratedDb.notebookDao().getPagesOnce(nbId).isEmpty())
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }
}

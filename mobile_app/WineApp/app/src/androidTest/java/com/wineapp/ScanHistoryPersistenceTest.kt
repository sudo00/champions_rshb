package com.wineapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.wineapp.data.local.*
import com.wineapp.data.repository.ScanHistoryRepositoryImpl
import com.wineapp.domain.model.*
import com.wineapp.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScanHistoryPersistenceTest {
    @Test fun galleryPhotoSurvivesCacheRemovalAndConfirmationUpdatesWithoutDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val badges = object : BadgeRepository {
            override suspend fun awardForScan(scanId: String, territoryId: String?) = emptyList<EarnedBadge>()
            override fun getBadges(): Flow<List<UserBadgeEntity>> = flowOf(emptyList())
            override fun getTotalPoints(): Flow<Int> = flowOf(0)
            override val freshBadges = MutableSharedFlow<List<EarnedBadge>>()
        }
        val repository = ScanHistoryRepositoryImpl(context, db.scanHistoryDao(), badges)
        val source = File.createTempFile("history_test_", ".jpg", context.cacheDir)
        source.writeBytes(byteArrayOf(1, 2, 3))
        var savedPath: String? = null
        try {
            val wine = Wine("a", "Первый", null, null, null, null, null, null, null,
                null, null, null, null, null, winery = null)
            val result = ScanResult(wine, .8f, scanId = "test-scan")
            val snapshot = requireNotNull(result.historySnapshot(source.path, 100))
            repository.saveScan(snapshot).getOrThrow()
            val saved = repository.getSavedScanById(snapshot.id).getOrThrow()
            savedPath = saved.labelPhotoPath
            assertEquals("candidates_unverified", saved.recognitionStatus)
            assertNotEquals(source.path, savedPath)
            source.delete()
            assertArrayEquals(byteArrayOf(1, 2, 3), File(requireNotNull(savedPath)).readBytes())
            db.scanHistoryDao().insertConversation(listOf(ScanConversationEntity("message", snapshot.id, "user", "text", 1)))
            repository.saveScan(snapshot.copy(wine = wine.copy(id = "b"),
                recognitionStatus = "user_confirmed", scannedAt = 200)).getOrThrow()
            val updated = repository.getSavedScanById(snapshot.id).getOrThrow()
            assertEquals("b", updated.wine.id)
            assertEquals("user_confirmed", updated.recognitionStatus)
            assertEquals(100L, updated.scannedAt)
            assertEquals(savedPath, updated.labelPhotoPath)
            assertEquals(1, updated.conversation.size)
            assertEquals(1, repository.getSavedScans().first().size)
        } finally {
            source.delete()
            savedPath?.let { File(it).delete() }
            db.close()
        }
    }

    @Test fun migrationPreservesExistingRowsAndMarksThemLegacy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE scan_history (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO scan_history(id) VALUES ('existing')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_6_7.migrate(db)
            db.query("SELECT id, recognitionStatus FROM scan_history").use {
                assertTrue(it.moveToFirst())
                assertEquals("existing", it.getString(0))
                assertEquals("legacy", it.getString(1))
                assertFalse(it.moveToNext())
            }
        } finally { helper.close() }
    }
}

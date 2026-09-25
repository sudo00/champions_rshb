package com.wineapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wineapp.data.local.converter.Converters

@Database(
    entities = [
        WineHistoryEntity::class,
        ScanHistoryEntity::class,
        ScanConversationEntity::class,
        FavoriteEntity::class,
        CellarEntity::class,
        UserBadgeEntity::class,
        PointsEntry::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_history ADD COLUMN recognitionStatus TEXT NOT NULL DEFAULT 'legacy'")
            }
        }
    }
    abstract fun wineDao(): WineDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun cellarDao(): CellarDao
    abstract fun gameDao(): GameDao
}

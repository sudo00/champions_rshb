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
        FavoriteEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wineDao(): WineDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun favoriteDao(): FavoriteDao
}
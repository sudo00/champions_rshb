package com.wineapp.data.repository

import android.content.Context
import android.util.Log
import com.wineapp.data.local.ScanHistoryDao
import com.wineapp.data.local.ScanHistoryEntity
import com.wineapp.data.local.ScanConversationEntity
import com.wineapp.data.local.TerritoryRegistry
import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.ScanHistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class ScanHistoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanHistoryDao: ScanHistoryDao,
    private val badgeRepository: com.wineapp.domain.repository.BadgeRepository
) : ScanHistoryRepository {

    override suspend fun saveScan(scan: SavedScan): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val previous = scanHistoryDao.getScanById(scan.id)
                val savedPhotoPath = previous?.labelPhotoPath?.takeIf { File(it).isFile }
                    ?: scan.labelPhotoPath?.let { copyPhotoToInternal(it) }
                val confirmed = scan.recognitionStatus in setOf("legacy", "user_confirmed", "score_confirmed")
                val territoryId = if (confirmed) TerritoryRegistry.normalize(scan.wine.region) else null

                val entity = ScanHistoryEntity(
                    id = scan.id,
                    wineId = scan.wine.id,
                    labelPhotoPath = savedPhotoPath,
                    confidence = scan.confidence,
                    wineName = scan.wine.name,
                    vintage = scan.wine.vintage,
                    rating = scan.wine.rating,
                    reviewsCount = scan.wine.reviewsCount,
                    price = scan.wine.price,
                    currency = scan.wine.currency,
                    region = scan.wine.region,
                    country = scan.wine.country,
                    variety = scan.wine.variety,
                    style = scan.wine.style,
                    alcoholPercentage = scan.wine.alcoholPercentage,
                    imageUrl = scan.wine.imageUrl,
                    description = scan.wine.description,
                    foodPairing = scan.wine.foodPairing.joinToString(","),
                    winery = scan.wine.winery,
                    scannedAt = previous?.scannedAt ?: scan.scannedAt,
                    territoryId = territoryId,
                    recognitionStatus = scan.recognitionStatus
                )
                scanHistoryDao.insertScan(entity)

                // «Винный путь»: очки и бейджи за скан (идемпотентно, ошибки не роняют сохранение).
                try {
                    if (confirmed && previous?.recognitionStatus !in setOf("legacy", "user_confirmed", "score_confirmed"))
                        badgeRepository.awardForScan(scan.id, territoryId)
                } catch (e: Exception) {
                    Log.e("ScanHistoryRepo", "Badge award failed", e)
                }

                // Виджет показывает недавние сканы — обновляем его после сохранения.
                try {
                    com.wineapp.presentation.widget.WidgetUpdater.updateAll(context)
                } catch (e: Exception) {
                    Log.e("ScanHistoryRepo", "Widget update failed", e)
                }

                if (scan.conversation.isNotEmpty()) {
                    val conversationEntities = scan.conversation.map { msg ->
                        ScanConversationEntity(
                            id = UUID.randomUUID().toString(),
                            scanHistoryId = scan.id,
                            role = msg.role,
                            content = msg.content,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    scanHistoryDao.insertConversation(conversationEntities)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("ScanHistoryRepo", "Save scan failed", e)
                Result.failure(e)
            }
        }
    }

    override fun getSavedScans(): Flow<List<SavedScan>> {
        return scanHistoryDao.getAllScans().map { entities ->
            entities.map { entity ->
                SavedScan(
                    id = entity.id,
                    wine = Wine(
                        id = entity.wineId,
                        name = entity.wineName,
                        vintage = entity.vintage,
                        rating = entity.rating,
                        reviewsCount = entity.reviewsCount,
                        price = entity.price,
                        currency = entity.currency,
                        region = entity.region,
                        country = entity.country,
                        variety = entity.variety,
                        style = entity.style,
                        alcoholPercentage = entity.alcoholPercentage,
                        imageUrl = entity.imageUrl,
                        description = entity.description,
                        foodPairing = entity.foodPairing.split(",").filter { it.isNotBlank() },
                        winery = entity.winery
                    ),
                    labelPhotoPath = entity.labelPhotoPath,
                    confidence = entity.confidence,
                    conversation = emptyList(),
                    recognitionStatus = entity.recognitionStatus,
                    scannedAt = entity.scannedAt
                )
            }
        }
    }

    override suspend fun getRecentScans(limit: Int): Result<List<SavedScan>> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = scanHistoryDao.getRecentScans(limit)
                Result.success(entities.map { entity -> toSavedScan(entity) })
            } catch (e: Exception) {
                Log.e("ScanHistoryRepo", "Get recent scans failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getSavedScanById(id: String): Result<SavedScan> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = scanHistoryDao.getScanById(id)
                    ?: return@withContext Result.failure(Exception("Сканирование не найдено"))

                val conversation = scanHistoryDao.getConversation(id)
                    .map { SommelierMessage(role = it.role, content = it.content) }

                val scan = SavedScan(
                    id = entity.id,
                    wine = Wine(
                        id = entity.wineId,
                        name = entity.wineName,
                        vintage = entity.vintage,
                        rating = entity.rating,
                        reviewsCount = entity.reviewsCount,
                        price = entity.price,
                        currency = entity.currency,
                        region = entity.region,
                        country = entity.country,
                        variety = entity.variety,
                        style = entity.style,
                        alcoholPercentage = entity.alcoholPercentage,
                        imageUrl = entity.imageUrl,
                        description = entity.description,
                        foodPairing = entity.foodPairing.split(",").filter { it.isNotBlank() },
                        winery = entity.winery
                    ),
                    labelPhotoPath = entity.labelPhotoPath,
                    confidence = entity.confidence,
                    conversation = conversation,
                    recognitionStatus = entity.recognitionStatus,
                    scannedAt = entity.scannedAt
                )

                Result.success(scan)
            } catch (e: Exception) {
                Log.e("ScanHistoryRepo", "Get scan by id failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteScan(id: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = scanHistoryDao.getScanById(id)
                entity?.labelPhotoPath?.let { path ->
                    File(path).delete()
                }
                scanHistoryDao.deleteScanById(id)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("ScanHistoryRepo", "Delete scan failed", e)
                Result.failure(e)
            }
        }
    }

    private fun toSavedScan(entity: ScanHistoryEntity): SavedScan {
        return SavedScan(
            id = entity.id,
            wine = Wine(
                id = entity.wineId,
                name = entity.wineName,
                vintage = entity.vintage,
                rating = entity.rating,
                reviewsCount = entity.reviewsCount,
                price = entity.price,
                currency = entity.currency,
                region = entity.region,
                country = entity.country,
                variety = entity.variety,
                style = entity.style,
                alcoholPercentage = entity.alcoholPercentage,
                imageUrl = entity.imageUrl,
                description = entity.description,
                foodPairing = entity.foodPairing.split(",").filter { it.isNotBlank() },
                winery = entity.winery
            ),
            labelPhotoPath = entity.labelPhotoPath,
            confidence = entity.confidence,
            conversation = emptyList(),
            recognitionStatus = entity.recognitionStatus,
            scannedAt = entity.scannedAt
        )
    }

    private fun copyPhotoToInternal(sourcePath: String): String {
        val photosDir = File(context.filesDir, "scan_photos")
        if (!photosDir.exists()) photosDir.mkdirs()

        val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        val sourceFile = File(sourcePath)

        require(sourceFile.isFile) { "Scan photo is missing" }
        sourceFile.copyTo(destFile, overwrite = true)

        return destFile.absolutePath
    }
}

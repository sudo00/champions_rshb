package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.file.ImageOrientationHelper
import com.wineapp.data.local.WineDao
import com.wineapp.data.local.WineHistoryEntity
import com.wineapp.data.remote.dto.ScanRequest
import com.wineapp.data.remote.dto.ScanStatusResponse
import com.wineapp.data.remote.mapper.ScanMapper
import com.wineapp.data.remote.mapper.SearchMapper
import com.wineapp.data.remote.mapper.WineMapper
import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

const val POLLING_TIMEOUT_MS = 500L
const val MAX_ATTEMPTS = 60

class WineRepositoryImpl @javax.inject.Inject constructor(
    private val apiService: com.wineapp.data.remote.ApiService,
    private val wineDao: WineDao
) : WineRepository {

    private var currentScanId: String? = null

    override suspend fun scanWineLabel(imagePath: String): Result<ScanResult> {
        return withContext(Dispatchers.IO) {
            try {
                val base64Image = convertImageToBase64(imagePath)
                val request = ScanRequest(base64Image)
                val accepted = apiService.scanLabel(request)
                if (!accepted.success) {
                    return@withContext Result.failure(Exception(accepted.error ?: "Ошибка сканирования"))
                }
                val scanId = accepted.scanId ?: return@withContext Result.failure(Exception("scanId не получен"))
                currentScanId = scanId
                val statusResponse = pollScanStatus(scanId)
                val result = ScanMapper.toDomain(statusResponse)
                result.wine?.let { saveToHistory(it) }
                Result.success(result)
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Scan failed", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun pollScanStatus(scanId: String): ScanStatusResponse {
        var status = "pending"
        var attempts = 0
        var lastResponse: ScanStatusResponse? = null
        val maxAttempts = MAX_ATTEMPTS
        while (status == "pending" || status == "processing") {
            if (attempts >= maxAttempts) {
                throw Exception("Тайм-аут сканирования")
            }
            delay(POLLING_TIMEOUT_MS)
            val response = apiService.getScanStatus(scanId)
            lastResponse = response
            status = response.status
            attempts++
        }
        if (status == "failed") {
            throw Exception(lastResponse?.error ?: "Распознавание не удалось")
        }
        return lastResponse ?: throw Exception("Сканирование не найдено")
    }

    override suspend fun searchWines(query: String, page: Int, pageSize: Int): Result<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchWines(query, page, pageSize)
                Result.success(SearchMapper.toDomain(response))
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Search failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getWineById(id: String): Result<Wine> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getWineDetail(id)
                response.wine?.let { Result.success(WineMapper.toDomain(it)) }
                    ?: Result.failure(Exception("Вино не найдено"))
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Get wine detail failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun saveToHistory(wine: Wine): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = WineHistoryEntity(
                    id = wine.id,
                    name = wine.name,
                    vintage = wine.vintage,
                    rating = wine.rating,
                    reviewsCount = wine.reviewsCount,
                    price = wine.price,
                    currency = wine.currency,
                    region = wine.region,
                    country = wine.country,
                    variety = wine.variety,
                    style = wine.style,
                    alcoholPercentage = wine.alcoholPercentage,
                    imageUrl = wine.imageUrl,
                    description = wine.description,
                    foodPairing = wine.foodPairing.joinToString(","),
                    winery = wine.winery
                )
                wineDao.insert(entity)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Save to history failed", e)
                Result.failure(e)
            }
        }
    }

    override fun getHistory() = wineDao.getAll().map { entities ->
        entities.map { entity ->
            Wine(
                id = entity.id,
                name = entity.name,
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
            )
        }
    }

    override suspend fun clearHistory(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                wineDao.clearAll()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun convertImageToBase64(imagePath: String): String {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return ""

            // Нормализация EXIF-ориентации: BitmapFactory игнорирует EXIF,
            // а compress срезает его — без этого на бэк уходило повёрнутое фото.
            // Угол берётся из EXIF каждого файла, фиксированного поворота нет.
            val jpegBytes = ImageOrientationHelper.encodeNormalizedJpeg(imagePath, quality = 90)
                ?: return ""
            android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("WineRepositoryImpl", "convertImageToBase64 failed", e)
            ""
        }
    }
}



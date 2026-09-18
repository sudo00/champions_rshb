package com.wineapp.data.repository

import android.util.Log
import com.wineapp.data.local.WineDao
import com.wineapp.data.local.WineHistoryEntity
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.data.remote.ApiService
import com.wineapp.data.remote.mapper.ScanMapper
import com.wineapp.data.remote.mapper.SearchMapper
import com.wineapp.data.remote.mapper.WineMapper
import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WineRepositoryImpl @javax.inject.Inject constructor(
    private val apiService: ApiService,
    private val wineDao: WineDao
) : WineRepository {

    override suspend fun scanWineLabel(imagePath: String): Result<ScanResult> {
        return withContext(Dispatchers.IO) {
            try {
                // TODO: Convert imagePath to base64
                val base64Image = convertImageToBase64(imagePath)
                val request = com.wineapp.data.remote.dto.ScanRequest(base64Image)
                val response = apiService.scanLabel(request)
                if (response.success) {
                    val result = ScanMapper.toDomain(response)
                    result.wine?.let { saveToHistory(it) }
                    Result.success(result)
                } else {
                    Result.failure(Exception(response.error ?: "Ошибка сканирования"))
                }
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Scan failed, using mock data", e)
                val mockResult = MockDataProvider.mockScanResult()
                mockResult.wine?.let { saveToHistory(it) }
                Result.success(mockResult)
            }
        }
    }

    override suspend fun searchWines(query: String, page: Int, pageSize: Int): Result<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchWines(query, page, pageSize)
                Result.success(SearchMapper.toDomain(response))
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Search failed, using mock data", e)
                Result.success(MockDataProvider.mockSearchResult(query, page))
            }
        }
    }

    override suspend fun getWineById(id: String): Result<Wine> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getWineDetail(id)
                response.wine?.let { Result.success(WineMapper.toDomain(it)) }
                    ?: Result.failure(Exception("Wine not found"))
            } catch (e: Exception) {
                Log.e("WineRepositoryImpl", "Get wine detail failed, using mock data", e)
                Result.success(MockDataProvider.mockWineDetail(id))
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
                    foodPairing = wine.foodPairing.joinToString(","), // TODO: Use proper JSON
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
        // TODO: Implement actual image to base64 conversion
        return "base64_placeholder"
    }
}
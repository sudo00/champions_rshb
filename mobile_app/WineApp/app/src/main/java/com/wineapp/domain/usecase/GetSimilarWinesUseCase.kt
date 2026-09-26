package com.wineapp.domain.usecase

import android.util.Log
import com.wineapp.domain.model.Wine
import javax.inject.Inject

/**
 * Спайк похожих вин: текстовый поиск по сорту/стилю, исключая текущее вино.
 * Если выдача нерелевантна — секция в UI скрывается (similar пуст).
 */
class GetSimilarWinesUseCase @Inject constructor(
    private val searchWinesUseCase: SearchWinesUseCase
) {
    suspend operator fun invoke(wine: Wine, limit: Int = 4): Result<List<Wine>> {
        val query = listOfNotNull(wine.variety, wine.style).joinToString(" ").trim()
        if (query.isEmpty()) return Result.success(emptyList())
        return try {
            val result = searchWinesUseCase(query, page = 1, pageSize = limit + 1).getOrNull()
            val similar = result?.wines
                ?.filter { it.id != wine.id }
                ?.take(limit)
                .orEmpty()
            Log.i("GetSimilarWines", "query='$query' found=${similar.size}")
            Result.success(similar)
        } catch (e: Exception) {
            Log.e("GetSimilarWines", "Similar search failed", e)
            Result.success(emptyList())
        }
    }
}

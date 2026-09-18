package com.wineapp.domain.usecase

import android.util.Log
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchWinesUseCase @javax.inject.Inject constructor(
    private val repository: WineRepository
) {

    suspend operator fun invoke(query: String, page: Int = 1, pageSize: Int = 20): Result<SearchResult> {
        Log.i("SearchWinesUseCase", "Searching wines: $query, page: $page")
        // TODO: Implement search with pagination
        // TODO: Add debouncing and caching
        return withContext(Dispatchers.IO) {
            repository.searchWines(query, page, pageSize)
        }
    }
}
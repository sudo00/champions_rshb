package com.wineapp.domain.usecase

import android.util.Log
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveToHistoryUseCase @javax.inject.Inject constructor(
    private val repository: WineRepository
) {

    suspend operator fun invoke(wine: Wine): Result<Unit> {
        Log.i("SaveToHistoryUseCase", "Saving wine to history: ${wine.name}")
        // TODO: Implement save to local history
        return withContext(Dispatchers.IO) {
            repository.saveToHistory(wine)
        }
    }
}
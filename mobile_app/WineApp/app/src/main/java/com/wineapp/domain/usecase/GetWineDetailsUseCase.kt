package com.wineapp.domain.usecase

import android.util.Log
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetWineDetailsUseCase @javax.inject.Inject constructor(
    private val repository: WineRepository
) {

    suspend operator fun invoke(wineId: String): Result<Wine> {
        Log.i("GetWineDetailsUseCase", "Fetching wine details for: $wineId")
        // TODO: Implement wine details fetching
        // TODO: Check local cache first, then remote
        return withContext(Dispatchers.IO) {
            repository.getWineById(wineId)
        }
    }
}
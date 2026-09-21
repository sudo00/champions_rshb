package com.wineapp.domain.usecase

import android.util.Log
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.flow.Flow

class GetHistoryUseCase @javax.inject.Inject constructor(
    private val repository: WineRepository
) {

    operator fun invoke(): Flow<List<Wine>> {
        Log.i("GetHistoryUseCase", "Getting scan history")
        // TODO: Implement history retrieval with Flow
        return repository.getHistory()
    }
}
package com.wineapp.domain.usecase

import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSavedScansUseCase @Inject constructor(
    private val repository: ScanHistoryRepository
) {
    operator fun invoke(): Flow<List<SavedScan>> {
        return repository.getSavedScans()
    }
}

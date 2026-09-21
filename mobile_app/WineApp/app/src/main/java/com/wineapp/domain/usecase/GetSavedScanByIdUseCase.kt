package com.wineapp.domain.usecase

import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.repository.ScanHistoryRepository
import javax.inject.Inject

class GetSavedScanByIdUseCase @Inject constructor(
    private val repository: ScanHistoryRepository
) {
    suspend operator fun invoke(id: String): Result<SavedScan> {
        return repository.getSavedScanById(id)
    }
}

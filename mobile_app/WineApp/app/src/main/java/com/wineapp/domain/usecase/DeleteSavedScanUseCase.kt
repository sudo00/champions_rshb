package com.wineapp.domain.usecase

import com.wineapp.domain.repository.ScanHistoryRepository
import javax.inject.Inject

class DeleteSavedScanUseCase @Inject constructor(
    private val repository: ScanHistoryRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.deleteScan(id)
    }
}

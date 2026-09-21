package com.wineapp.domain.usecase

import com.wineapp.domain.model.SavedScan
import com.wineapp.domain.repository.ScanHistoryRepository
import javax.inject.Inject

class SaveScanUseCase @Inject constructor(
    private val repository: ScanHistoryRepository
) {
    suspend operator fun invoke(scan: SavedScan): Result<Unit> {
        return repository.saveScan(scan)
    }
}

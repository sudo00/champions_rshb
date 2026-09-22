package com.wineapp.domain.usecase

import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanWineUseCase @javax.inject.Inject constructor(
    private val repository: WineRepository
) {

    suspend operator fun invoke(imagePath: String): Result<ScanResult> {
        return withContext(Dispatchers.IO) {
            repository.scanWineLabel(imagePath)
        }
    }
}

package com.wineapp.domain.usecase

import com.wineapp.domain.repository.CellarRepository
import javax.inject.Inject

class SetCellarStatusUseCase @Inject constructor(
    private val repository: CellarRepository
) {
    suspend operator fun invoke(wineId: String, status: String): Result<Unit> {
        return repository.setStatus(wineId, status)
    }
}

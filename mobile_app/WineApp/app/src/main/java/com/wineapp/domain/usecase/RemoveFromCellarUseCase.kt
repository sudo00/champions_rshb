package com.wineapp.domain.usecase

import com.wineapp.domain.repository.CellarRepository
import javax.inject.Inject

class RemoveFromCellarUseCase @Inject constructor(
    private val repository: CellarRepository
) {
    suspend operator fun invoke(wineId: String): Result<Unit> {
        return repository.removeWine(wineId)
    }
}

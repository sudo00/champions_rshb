package com.wineapp.domain.usecase

import com.wineapp.domain.repository.CellarRepository
import javax.inject.Inject

class AddToCellarUseCase @Inject constructor(
    private val repository: CellarRepository
) {
    suspend operator fun invoke(wineId: String, delta: Int = 1): Result<Unit> {
        return repository.addWine(wineId, delta)
    }
}

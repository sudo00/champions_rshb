package com.wineapp.domain.usecase

import com.wineapp.domain.repository.CellarRepository
import javax.inject.Inject

class SetCellarQuantityUseCase @Inject constructor(
    private val repository: CellarRepository
) {
    suspend operator fun invoke(wineId: String, quantity: Int): Result<Unit> {
        return repository.setQuantity(wineId, quantity)
    }
}

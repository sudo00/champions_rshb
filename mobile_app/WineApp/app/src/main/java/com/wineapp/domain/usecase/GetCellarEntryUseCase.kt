package com.wineapp.domain.usecase

import com.wineapp.data.local.CellarEntity
import com.wineapp.domain.repository.CellarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCellarEntryUseCase @Inject constructor(
    private val repository: CellarRepository
) {
    operator fun invoke(wineId: String): Flow<CellarEntity?> {
        return repository.getEntry(wineId)
    }
}

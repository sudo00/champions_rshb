package com.wineapp.domain.usecase

import com.wineapp.data.local.CellarEntity
import com.wineapp.domain.model.CellarItem
import com.wineapp.domain.repository.CellarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetCellarUseCase @Inject constructor(
    private val cellarRepository: CellarRepository,
    private val getWineDetailsUseCase: GetWineDetailsUseCase
) {
    operator fun invoke(): Flow<List<CellarItem>> {
        return cellarRepository.getAllEntries().map { entities ->
            entities.mapNotNull { entity -> toCellarItem(entity) }
        }
    }

    private suspend fun toCellarItem(entity: CellarEntity): CellarItem? {
        val wine = getWineDetailsUseCase(entity.wineId).getOrNull() ?: return null
        return CellarItem(
            wine = wine,
            quantity = entity.quantity,
            status = entity.status,
            updatedAt = entity.updatedAt
        )
    }
}

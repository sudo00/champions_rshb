package com.wineapp.domain.usecase

import com.wineapp.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    operator fun invoke(wineId: String): Flow<Boolean> {
        return repository.isFavorite(wineId)
    }
}

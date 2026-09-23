package com.wineapp.domain.usecase

import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    operator fun invoke(wineId: String, kind: String = FavoriteKind.LIKED): Flow<Boolean> {
        return repository.isFavorite(wineId, kind)
    }
}

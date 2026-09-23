package com.wineapp.domain.usecase

import com.wineapp.domain.repository.FavoriteRepository
import javax.inject.Inject

class SetFavoriteKindUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    suspend operator fun invoke(wineId: String, kind: String): Result<Unit> {
        return repository.setKind(wineId, kind)
    }
}

package com.wineapp.domain.usecase

import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.repository.FavoriteRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    suspend operator fun invoke(
        wineId: String,
        isFavorite: Boolean,
        kind: String = FavoriteKind.LIKED
    ): Result<Unit> {
        return if (isFavorite) {
            repository.removeFavorite(wineId)
        } else {
            repository.addFavorite(wineId, kind)
        }
    }
}

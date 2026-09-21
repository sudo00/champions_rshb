package com.wineapp.domain.usecase

import com.wineapp.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val repository: FavoriteRepository
) {
    operator fun invoke(): Flow<List<String>> {
        return repository.getAllFavoriteIds()
    }
}

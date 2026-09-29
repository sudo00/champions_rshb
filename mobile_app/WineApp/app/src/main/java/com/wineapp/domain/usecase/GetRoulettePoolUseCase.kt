package com.wineapp.domain.usecase

import com.wineapp.domain.model.RouletteSource
import com.wineapp.domain.model.Wine
import com.wineapp.domain.repository.CellarRepository
import com.wineapp.domain.repository.FavoriteRepository
import com.wineapp.domain.repository.WineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Пул вин для барабана рулетки.
 *
 * Источники хранят только id, поэтому детали вина догружаются в два прохода:
 * сначала мгновенно из локальной истории сканов, затем параллельно из сети для
 * недостающих. Сеть не определяет состав пула — только наполняет его полями.
 */
class GetRoulettePoolUseCase @Inject constructor(
    private val cellarRepository: CellarRepository,
    private val favoriteRepository: FavoriteRepository,
    private val wineRepository: WineRepository,
    private val getWineDetailsUseCase: GetWineDetailsUseCase
) {

    suspend operator fun invoke(source: RouletteSource): Result<List<Wine>> {
        return try {
            val ids = sourceIds(source).distinct()
            if (ids.isEmpty()) {
                Result.success(emptyList())
            } else {
                Result.success(resolveWines(ids))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Статус наличия в коллекции не важен — берём все записи пользователя. */
    private suspend fun sourceIds(source: RouletteSource): List<String> = when (source) {
        RouletteSource.COLLECTION -> cellarRepository.getAllEntries().first().map { it.wineId }
        RouletteSource.FAVORITES -> favoriteRepository.getAllFavorites().first().map { it.wineId }
    }

    private suspend fun resolveWines(ids: List<String>): List<Wine> {
        val local = wineRepository.getHistory().first().associateBy { it.id }
        return coroutineScope {
            ids.map { id -> async { local[id] ?: getWineDetailsUseCase(id).getOrNull() } }
                .awaitAll()
                .filterNotNull()
                .distinctBy { it.id }
        }
    }
}

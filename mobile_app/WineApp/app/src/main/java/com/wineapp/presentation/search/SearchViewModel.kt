package com.wineapp.presentation.search

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.SearchWinesUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchWinesUseCase: SearchWinesUseCase,
    getFavoritesUseCase: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : BaseViewModel<SearchState, SearchIntent>() {
    private var currentQuery = ""
    private var currentPage = 1

    /** ID вин в «Понравилось» — для сердечек на карточках. */
    val likedIds: StateFlow<Set<String>> = getFavoritesUseCase()
        .map { list -> list.filter { it.kind == FavoriteKind.LIKED }.map { it.wineId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    override fun getInitialState(): SearchState = SearchState.Idle()

    override fun reduce(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> handleSearch(intent.query)
            is SearchIntent.LoadMore -> handleLoadMore()
            is SearchIntent.ClearSearch -> handleClear()
            is SearchIntent.SelectWine -> { /* Navigation */ }
            is SearchIntent.RecentSearchSelected -> handleSearch(intent.query)
            is SearchIntent.ToggleFavorite -> handleToggleFavorite(intent.wineId)
        }
    }

    private fun handleSearch(query: String) {
        val trimmed = query.trim()
        currentQuery = trimmed
        currentPage = 1
        updateState(SearchState.Loading(trimmed))
        viewModelScope.launch {
            val result = searchWinesUseCase(trimmed, 1, 20)
            result.onSuccess { searchResult ->
                if (searchResult.wines.isEmpty()) {
                    updateState(SearchState.Empty(trimmed))
                } else {
                    updateState(SearchState.Success(searchResult, trimmed))
                }
            }.onFailure { error ->
                Log.e("SearchViewModel", "Search failed", error)
                updateState(SearchState.Error(error.message ?: "Ошибка поиска"))
            }
        }
    }

    private fun handleLoadMore() {
        val state = _state.value
        if (state is SearchState.Success && state.result.hasMore) {
            currentPage++
            updateState(SearchState.Loading(currentQuery, true))
            viewModelScope.launch {
                val result = searchWinesUseCase(currentQuery, currentPage, 20)
                result.onSuccess { searchResult ->
                    val current = (_state.value as? SearchState.Success)?.result ?: searchResult
                    val combined = SearchResult(
                        wines = current.wines + searchResult.wines,
                        totalCount = searchResult.totalCount,
                        page = searchResult.page,
                        hasMore = searchResult.hasMore
                    )
                    updateState(SearchState.Success(combined, currentQuery))
                }.onFailure { error ->
                    Log.e("SearchViewModel", "Load more failed", error)
                    currentPage--
                }
            }
        }
    }

    private fun handleClear() {
        currentQuery = ""
        currentPage = 1
        updateState(SearchState.Idle())
    }

    private fun handleToggleFavorite(wineId: String) {
        viewModelScope.launch {
            val isFavorite = wineId in likedIds.value
            toggleFavoriteUseCase(wineId, isFavorite, FavoriteKind.LIKED)
                .onFailure { error ->
                    Log.e("SearchViewModel", "Toggle favorite failed", error)
                }
        }
    }
}
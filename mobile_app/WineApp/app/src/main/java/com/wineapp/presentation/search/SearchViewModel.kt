package com.wineapp.presentation.search

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.usecase.SearchWinesUseCase
import com.wineapp.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchWinesUseCase: SearchWinesUseCase
) : BaseViewModel<SearchState, SearchIntent>() {
    private var currentQuery = ""
    private var currentPage = 1

    override fun getInitialState(): SearchState = SearchState.Idle()

    override fun reduce(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> handleSearch(intent.query)
            is SearchIntent.LoadMore -> handleLoadMore()
            is SearchIntent.ClearSearch -> handleClear()
            is SearchIntent.SelectWine -> { /* Navigation */ }
            is SearchIntent.RecentSearchSelected -> handleSearch(intent.query)
        }
    }

    private fun handleSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            updateState(SearchState.Idle())
            return
        }
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
}